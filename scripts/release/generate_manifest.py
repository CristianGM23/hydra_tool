#!/usr/bin/env python3
from __future__ import annotations

"""Genera el manifiesto SHA-256 reproducible usando el inventario canónico de fuentes de HYDRA."""

import hashlib
import json
from pathlib import Path

from source_inventory import source_inventory

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "MANIFEST_SHA256.txt"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    version = json.loads((ROOT / "config/hydra-version.json").read_text(encoding="utf-8"))["version"]
    inventory = source_inventory(ROOT, version)
    # El manifiesto no puede incluir su propio digest. Todos los demás ficheros
    # proceden exactamente de la misma allowlist que utiliza package_source.py.
    inventory.pop("MANIFEST_SHA256.txt", None)
    MANIFEST.write_text(
        "".join(f"{sha256(path)}  {relative}\n" for relative, path in inventory.items()),
        encoding="utf-8",
    )
    print(f"Generated {MANIFEST.name}: {len(inventory)} canonical source files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
