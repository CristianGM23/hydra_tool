#!/usr/bin/env python3
"""Comprueba límites globales de tamaño real sin excepciones por archivo.

Reproduce StructuralArchitectureGuardTests.productionSourceFilesDoNotHideComplexityInGiantLines
para fallar antes de ejecutar Maven completo. No modifica las líneas ni los límites.
"""
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "config/hydra-logical-size-baseline.properties"


def baseline() -> dict[str, int]:
    properties: dict[str, int] = {}
    for line in BASELINE.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped[0] in "#!":
            continue
        key, separator, value = stripped.partition("=")
        if not separator or key.strip().startswith("exception."):
            raise ValueError(f"Invalid or exceptional logical-size baseline: {line}")
        properties[key.strip()] = int(value.strip())
    return properties


def main() -> int:
    props = baseline()
    extensions = {
        ".java": ("default.java.maxLine", "default.java.characters"),
        ".ts": ("default.typescript.maxLine", "default.typescript.characters"),
        ".tsx": ("default.typescript.maxLine", "default.typescript.characters"),
        ".css": ("default.css.maxLine", "default.css.characters"),
    }
    failed: list[str] = []
    inspected = 0
    for source in (ROOT / "src/main/java", ROOT / "frontend/src"):
        for path in sorted(source.rglob("*")):
            if not path.is_file() or path.suffix not in extensions:
                continue
            relative = path.relative_to(ROOT).as_posix()
            if ".test." in relative:  # Iguala el filtro del guard Java.
                continue
            max_line_key, max_size_key = extensions[path.suffix]
            content = path.read_text(encoding="utf-8")
            # String.length en Java cuenta unidades UTF-16, no caracteres Unicode.
            characters = len(content.encode("utf-16-le")) // 2
            longest = max((len(line.encode("utf-16-le")) // 2 for line in re.split(r"\r\n|\r|\n", content)), default=0)
            inspected += 1
            if characters > props[max_size_key]:
                failed.append(f"{relative}: {characters} characters > {props[max_size_key]}")
            if longest > props[max_line_key]:
                failed.append(f"{relative}: longest line {longest} > {props[max_line_key]}")
    for violation in failed:
        print(f"FAIL {violation}")
    print(f"R6C4_FIX171_LOGICAL_SIZE={'FAIL' if failed else 'PASS'} files={inspected} violations={len(failed)}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
