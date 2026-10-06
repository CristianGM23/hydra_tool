#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEST = ROOT / "frontend/src/integration-designer/integrationTechnologyRecommendations.test.ts"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL {message}")
    print(f"PASS {message}")


def main() -> int:
    source = TEST.read_text(encoding="utf-8")
    require("toContain('VALIDATION_RUNTIME','PROJECT_EXPORT')" not in source,
            "FULL capability assertions no longer pass two values to toContain")
    require("toContain('EVENT_DEFINITION_PROJECTION','PROJECT_EXPORT')" not in source,
            "projection assertions no longer pass two values to toContain")
    require("expect(value).toContain('VALIDATION_RUNTIME')" in source,
            "validation runtime remains asserted")
    require("expect(value).toContain('PROJECT_EXPORT')" in source,
            "project export remains asserted")
    require("expect(value).toContain('EVENT_DEFINITION_PROJECTION')" in source,
            "event projection remains asserted")
    require("expect(value).not.toContain('QUICK_FIX_RUNTIME')" in source,
            "partial quick fix remains excluded")
    require("expect(value).not.toContain('EVENT_PUBLICATION')" in source,
            "runtime event publication remains excluded for projection targets")
    print("FIX126_TYPED_EXPECTATIONS=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
