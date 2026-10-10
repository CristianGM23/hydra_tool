#!/usr/bin/env python3
"""Hotfix independiente para Fix175/Fix176: corregir test de rutas 8.3 de Windows.

Ejecutar desde la raiz de HYDRA:
  python C:\\ruta\\a\\HYDRA_R6C4_BD2_WINDOWS_PATH_FIX175_FIX176.py

Modifica EXCLUSIVAMENTE verify_bd_block2.py y su entrada en MANIFEST_SHA256.txt.
Conserva el resto de los hashes, el hotfix PWA y la documentacion Archify.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

REL = 'scripts/validation/verify_bd_block2.py'
ORIGINAL = b"self.assertEqual(builder.checked_root(self.root, 'trusted-editor'), self.editor)"
CORRECTED = b"self.assertEqual(builder.checked_root(self.root, 'trusted-editor'), self.root.resolve() / 'trusted-editor')"
KNOWN_ORIGINAL_SHA = '4c22300aaa1922fef1d80bedfb4ad3fdc5c546c3b1a546d6c6be54855d925b9f'


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def atomic_write(path: Path, payload: bytes) -> None:
    with tempfile.NamedTemporaryFile(mode='wb', dir=path.parent, prefix='.bd2-shortpath-', delete=False) as f:
        tmp = Path(f.name)
        f.write(payload)
        f.flush()
        os.fsync(f.fileno())
    try:
        os.replace(tmp, path)
    finally:
        if tmp.exists():
            tmp.unlink()


def main() -> int:
    root = Path.cwd()
    source = root / REL
    manifest = root / 'MANIFEST_SHA256.txt'
    if not source.is_file() or not manifest.is_file():
        print('ERROR: Ejecuta este script desde la raiz de HYDRA (con MANIFEST_SHA256.txt).', file=sys.stderr)
        return 2
    old_source = source.read_bytes()
    old_manifest = manifest.read_bytes()
    if b'\x00' in old_source:
        print('ERROR: El test es binario o no coincide con el formato esperado.', file=sys.stderr)
        return 2
    pattern = re.compile(rb'(?m)^([0-9a-f]{64})(  scripts/validation/verify_bd_block2\.py)(\r?\n|\Z)')
    matches = list(pattern.finditer(old_manifest))
    if len(matches) != 1:
        print('ERROR: El manifiesto no contiene exactamente una entrada del test BD2.', file=sys.stderr)
        return 2
    stored_sha = matches[0].group(1).decode('ascii')
    old_sha = digest(old_source)
    if CORRECTED in old_source and ORIGINAL not in old_source:
        # Si se aplica el overlay Fix176 DESPUES del hotfix Fix175,
        # su manifiesto antiguo puede reemplazar la entrada ya corregida.
        expected_corrected_sha = '6db120048df1efde382b202c72b5ad95749996997abdd573eeee33bf1565093b'
        if old_sha != expected_corrected_sha:
            print('ERROR: Test corregido pero su contenido no es el esperado; no se modifica.', file=sys.stderr)
            return 2
        if stored_sha == old_sha:
            print('PASS: El test BD2 ya estaba corregido y su SHA-256 coincide con el manifiesto.')
            return 0
        if stored_sha != KNOWN_ORIGINAL_SHA:
            print('ERROR: El manifiesto difiere de los estados reconocidos; no se sobrescribe.', file=sys.stderr)
            return 2
        new_source = old_source
        print('INFO: Reparando solo manifiesto tras superposicion de Fix176.')
    elif old_source.count(ORIGINAL) == 1 and stored_sha == KNOWN_ORIGINAL_SHA and old_sha == stored_sha:
        new_source = old_source.replace(ORIGINAL, CORRECTED)
    else:
        print('ERROR: Test BD2 o manifiesto no reconocido; se preservan los cambios.', file=sys.stderr)
        return 2
    new_sha = digest(new_source).encode('ascii')
    new_manifest = old_manifest[:matches[0].start(1)] + new_sha + old_manifest[matches[0].end(1):]
    if not re.search(rb'(?m)^' + new_sha + rb'  scripts/validation/verify_bd_block2\.py(?:\r?\n|\Z)', new_manifest):
        print('ERROR: Validacion previa del manifiesto fallida.', file=sys.stderr)
        return 2
    try:
        atomic_write(source, new_source)
        atomic_write(manifest, new_manifest)
    except OSError as error:
        # Se intenta revertir ambos archivos en caso de error a mitad de operacion.
        try:
            atomic_write(source, old_source)
            atomic_write(manifest, old_manifest)
        except OSError:
            print('ATENCION: no se pudo completar la reversión; comprueba ambos archivos.', file=sys.stderr)
        print(f'ERROR: No se pudo aplicar el hotfix: {error}', file=sys.stderr)
        return 2
    print('PASS: Corregido test_secure_root_works para rutas 8.3 de Windows.')
    print('PASS: Actualizado exclusivamente su SHA-256 en MANIFEST_SHA256.txt.')
    print('PRESERVE: codigo de produccion, tests PWA, Archify y otros hashes del manifiesto.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
