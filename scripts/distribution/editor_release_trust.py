#!/usr/bin/env python3
"""B4+D4: Ed25519 release authenticity, detached from immutable web ZIP bytes.

The administrator supplies external private keys and a pinned trust policy.
Neither SHA-256 nor self-signed metadata alone proves the publisher's identity.
Verification is explicit and fail-closed when trust is configured.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import sys

KEY_ID = re.compile(r'[a-z][a-z0-9_-]{0,63}\Z')
HEX = re.compile(r'[0-9a-f]{64}\Z')
ALLOWED_CHANNELS = ('stable', 'beta', 'nightly')
MAX_DOCUMENT_BYTES = 64 * 1024
DOMAIN = b'HYDRA-EDITOR-RELEASE-ED25519-V1\x00'


def require_crypto():
    try:
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
        from cryptography.hazmat.primitives.serialization import load_pem_private_key
        from cryptography.exceptions import InvalidSignature
    except ImportError as exc:
        raise ValueError('Ed25519 verification requires the optional cryptography package') from exc
    return Ed25519PublicKey, load_pem_private_key, InvalidSignature


def read_document(path: Path) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_DOCUMENT_BYTES:
        raise ValueError('Missing, symlinked, or oversized release trust document')
    value = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(value, dict):
        raise ValueError('Release trust document must be an object')
    return value


def b64(value: str, expected: int) -> bytes:
    if not isinstance(value, str):
        raise ValueError('Invalid base64 encoding')
    try:
        raw = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError('Invalid base64 encoding') from exc
    if len(raw) != expected:
        raise ValueError('Unexpected cryptographic key/signature length')
    return raw


def claim(product: str, channel: str, version: str, sha256: str) -> dict:
    if not isinstance(product, str) or not re.fullmatch(r'[a-z][a-z0-9-]{0,47}', product):
        raise ValueError('Invalid signed product identifier')
    if channel not in ALLOWED_CHANNELS:
        raise ValueError('Invalid signed channel')
    if not isinstance(version, str) or not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version):
        raise ValueError('Invalid signed release version')
    if not isinstance(sha256, str) or not HEX.fullmatch(sha256):
        raise ValueError('Invalid signed release digest')
    return {'schemaVersion': 1, 'product': product, 'channel': channel, 'version': version,
            'packageSHA256': sha256}


def payload(document: dict) -> bytes:
    return DOMAIN + json.dumps(document, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode('ascii')


def policy_keys(policy: dict, channel: str) -> tuple[dict[str, bytes], int]:
    if policy.get('schemaVersion') != 1 or policy.get('algorithm') != 'Ed25519':
        raise ValueError('Unsupported trust policy')
    channels = policy.get('channels')
    if not isinstance(channels, list) or not channels or any(c not in ALLOWED_CHANNELS for c in channels) or len(set(channels)) != len(channels):
        raise ValueError('Trust policy has invalid channels')
    if channel not in channels:
        raise ValueError('Publication channel not authorized by trust policy')
    entries = policy.get('trustedKeys')
    if not isinstance(entries, list) or not 1 <= len(entries) <= 16:
        raise ValueError('Trust policy must contain 1..16 pinned public keys')
    minimum = policy.get('minimumSignatures')
    if type(minimum) is not int or not 1 <= minimum <= len(entries):
        raise ValueError('Trust policy signature threshold invalid')
    trusted = {}
    for entry in entries:
        if not isinstance(entry, dict) or not isinstance(entry.get('id'), str) or not KEY_ID.fullmatch(entry['id']):
            raise ValueError('Invalid trusted key identifier')
        if entry['id'] in trusted:
            raise ValueError('Duplicate trusted key')
        trusted[entry['id']] = b64(entry.get('publicKey'), 32)
    return trusted, minimum


def sign_detached(private_key: Path, key_id: str, signed_claim: dict, existing: dict | None = None) -> dict:
    _, load_pem_private_key, _ = require_crypto()
    if not KEY_ID.fullmatch(key_id):
        raise ValueError('Invalid signing key identifier')
    if private_key.is_symlink() or not private_key.is_file() or private_key.stat().st_size > 16384:
        raise ValueError('Private key file unavailable or linked')
    key = load_pem_private_key(private_key.read_bytes(), password=None)
    if type(key).__name__ != 'Ed25519PrivateKey':
        # Providers can expose concrete subclasses; use signature length too.
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        if not isinstance(key, Ed25519PrivateKey):
            raise ValueError('Only Ed25519 signing is supported')
    bundle = existing or {'schemaVersion': 1, 'algorithm': 'Ed25519', 'claim': signed_claim, 'signatures': []}
    if bundle.get('schemaVersion') != 1 or bundle.get('algorithm') != 'Ed25519' or bundle.get('claim') != signed_claim:
        raise ValueError('Cannot append signature to a different release claim')
    signatures = bundle.get('signatures')
    if not isinstance(signatures, list) or len(signatures) >= 16 or any(s.get('keyId') == key_id for s in signatures if isinstance(s, dict)):
        raise ValueError('Too many signatures or duplicate key ID')
    signed = base64.b64encode(key.sign(payload(signed_claim))).decode('ascii')
    return {**bundle, 'signatures': [*signatures, {'keyId': key_id, 'signature': signed}]}


def verify_detached(bundle: dict, policy: dict, expected_claim: dict) -> dict:
    Ed25519PublicKey, _, InvalidSignature = require_crypto()
    trusted, minimum = policy_keys(policy, expected_claim['channel'])
    if bundle.get('schemaVersion') != 1 or bundle.get('algorithm') != 'Ed25519' or bundle.get('claim') != expected_claim:
        raise ValueError('Signed release identity, channel, version or digest differs')
    signatures = bundle.get('signatures')
    if not isinstance(signatures, list) or not 1 <= len(signatures) <= 16:
        raise ValueError('Signed release has no acceptable signature list')
    valid = set()
    seen = set()
    for entry in signatures:
        if not isinstance(entry, dict) or not isinstance(entry.get('keyId'), str):
            raise ValueError('Invalid signature entry')
        kid = entry['keyId']
        if kid in seen:
            raise ValueError('Duplicate signing key identifier')
        seen.add(kid)
        signature = b64(entry.get('signature'), 64)
        if kid not in trusted:
            continue
        try:
            Ed25519PublicKey.from_public_bytes(trusted[kid]).verify(signature, payload(expected_claim))
            valid.add(kid)
        except InvalidSignature:
            raise ValueError('Invalid signature from trusted key: ' + kid) from None
    if len(valid) < minimum:
        raise ValueError('Insufficient trusted valid signatures for release')
    return {'authenticityVerified': True, 'algorithm': 'Ed25519', 'validSigners': sorted(valid),
            'signatureThreshold': minimum}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('sign', 'verify'))
    parser.add_argument('--product', required=True)
    parser.add_argument('--channel', choices=ALLOWED_CHANNELS, required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--signature', type=Path, required=True)
    parser.add_argument('--private-key', type=Path)
    parser.add_argument('--key-id')
    parser.add_argument('--trust-policy', type=Path)
    args = parser.parse_args()
    try:
        from verify_web_package import verify as verify_package
        report = verify_package(args.package)
        if report['version'] != args.version:
            raise ValueError('Package version does not match requested release identity')
        signed_claim = claim(args.product, args.channel, args.version, report['packageSHA256'])
        if args.action == 'sign':
            if args.private_key is None or args.key_id is None:
                raise ValueError('Signing requires external private key and key id')
            existing = read_document(args.signature) if args.signature.exists() else None
            signed = sign_detached(args.private_key, args.key_id, signed_claim, existing)
            if args.signature.is_symlink():
                raise ValueError('Refusing to write signature through symbolic link')
            args.signature.parent.mkdir(parents=True, exist_ok=True)
            args.signature.write_text(json.dumps(signed, sort_keys=True, indent=2) + '\n', encoding='utf-8')
            print('BD4_SIGN=PASS signature=' + str(args.signature))
        else:
            if args.trust_policy is None:
                raise ValueError('Verification requires an administrator-pinned trust policy')
            result = verify_detached(read_document(args.signature), read_document(args.trust_policy), signed_claim)
            print(json.dumps(result, sort_keys=True))
        return 0
    except (ValueError, OSError, KeyError, UnicodeError, json.JSONDecodeError) as exc:
        print('BD4_RELEASE_TRUST=FAIL ' + str(exc), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
