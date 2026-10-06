#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
api = (root / "frontend/src/remote-storage/api.ts").read_text(encoding="utf-8")
test = (root / "frontend/src/aggressive-testing/HydraAggressiveRemoteIntegrationBrowserFlow.test.ts").read_text(encoding="utf-8")
checks = {
    "remote import consumes raw response bytes": "const bytes = await response.arrayBuffer();" in api,
    "remote import constructs File from bytes": "new File([bytes], fileName" in api,
    "remote import preserves response content type": "response.headers.get('content-type')" in api and "type: contentType" in api,
    "remote import no longer nests response blob in File": "const blob = await response.blob();\n    return { file: new File([blob]" not in api,
    "aggressive browser test checks remote version": "expect(imported.version).toBe('etag:v2');" in test,
    "aggressive browser test checks binary mime": "expect(imported.file.type).toBe('application/octet-stream');" in test,
    "aggressive browser test checks exact byte length": "expect(imported.file.size).toBe(payload.byteLength);" in test,
    "aggressive browser test checks exact bytes": "expect(new Uint8Array(await imported.file.arrayBuffer())).toEqual(payload);" in test,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"[{'PASS' if ok else 'FAIL'}] {name}")
if failed:
    raise SystemExit(f"Fix76 contract failed: {len(failed)}/{len(checks)} checks failed")
print(f"Fix76 remote binary transport: {len(checks)}/{len(checks)} PASS")
