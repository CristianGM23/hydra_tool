#!/usr/bin/env python3
"""Fix182: deterministic Windows-safe OpenAPI/client code generation tests."""
from __future__ import annotations

import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/api"))
from generated_output import write_generated


class GeneratedOutputTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="hydra-openapi-safe-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.client = self.root / "hydraApiClient.ts"

    def test_equal_bytes_do_not_modify_inode_or_mtime(self):
        self.client.write_bytes(b"hello\n")
        before = self.client.stat()
        self.assertFalse(write_generated(self.client, "hello\n"))
        after = self.client.stat()
        self.assertEqual((before.st_ino, before.st_mtime_ns), (after.st_ino, after.st_mtime_ns))

    def test_missing_output_created(self):
        self.assertTrue(write_generated(self.client, "hola\n"))
        self.assertEqual(self.client.read_bytes(), b"hola\n")

    def test_changed_output_committed_with_replace(self):
        self.client.write_bytes(b"old\n")
        self.assertTrue(write_generated(self.client, "new\n"))
        self.assertEqual(self.client.read_bytes(), b"new\n")
        self.assertFalse(list(self.root.glob(".*.tmp")))

    def test_utf8_preserved_without_platform_newline_translation(self):
        self.assertTrue(write_generated(self.client, "niño\nfrançais\n"))
        self.assertEqual(self.client.read_bytes(), "niño\nfrançais\n".encode("utf-8"))

    def test_failed_replace_preserves_original_and_cleans_staging(self):
        self.client.write_bytes(b"do not truncate")
        with mock.patch("generated_output.os.replace", side_effect=OSError(22, "Invalid argument")):
            with self.assertRaises(OSError):
                write_generated(self.client, "changed")
        self.assertEqual(self.client.read_bytes(), b"do not truncate")
        self.assertEqual(list(self.root.iterdir()), [self.client])

    def test_error_does_not_silently_certify_new_generated_content(self):
        self.client.write_bytes(b"old")
        with mock.patch("generated_output.os.replace", side_effect=PermissionError("denied")):
            with self.assertRaises(PermissionError):
                write_generated(self.client, "new")
        self.assertEqual(self.client.read_bytes(), b"old")

    def test_windows_sharing_violation_retries_and_recovers(self):
        self.client.write_bytes(b"old")
        native = os.replace
        failures = 0

        def transient(src, dest):
            nonlocal failures
            failures += 1
            if failures <= 2:
                raise PermissionError("Windows sharing violation")
            native(src, dest)

        with mock.patch("generated_output.os.replace", side_effect=transient), \
             mock.patch("generated_output._sharing_violation", return_value=True), \
             mock.patch("generated_output.time.sleep") as pause:
            self.assertTrue(write_generated(self.client, "new"))
        self.assertEqual(self.client.read_bytes(), b"new")
        self.assertEqual(failures, 3)
        self.assertEqual(pause.call_count, 2)

    def test_persistent_windows_lock_fails_closed(self):
        self.client.write_bytes(b"original")
        with mock.patch("generated_output.os.replace", side_effect=PermissionError("Windows sharing violation")), \
             mock.patch("generated_output._sharing_violation", return_value=True), \
             mock.patch("generated_output.time.sleep") as pause:
            with self.assertRaises(PermissionError):
                write_generated(self.client, "changed", retries=2)
        self.assertEqual(pause.call_count, 2)
        self.assertEqual(self.client.read_bytes(), b"original")
        self.assertEqual(list(self.root.iterdir()), [self.client])

    def test_symlink_output_refused(self):
        target = self.root / "target.txt"
        target.write_text("untouched", encoding="utf-8")
        try:
            self.client.symlink_to(target)
        except (OSError, NotImplementedError):
            self.skipTest("Filesystem does not allow symlinks")
        with self.assertRaises(ValueError):
            write_generated(self.client, "different")
        self.assertEqual(target.read_text(encoding="utf-8"), "untouched")


class RealOpenApiGeneratorTests(unittest.TestCase):
    def test_existing_generated_artifacts_are_not_rewritten(self):
        outputs = [
            ROOT / "config/api-contract.json",
            ROOT / "docs/api/hydra-openapi.yaml",
            ROOT / "frontend/src/api/generated/hydraApiClient.ts",
        ]
        before = [(file.read_bytes(), file.stat().st_mtime_ns) for file in outputs]
        proc = subprocess.run(
            [sys.executable, str(ROOT / "scripts/api/generate_openapi.py")],
            cwd=ROOT, text=True, capture_output=True, check=True,
        )
        self.assertIn("operations=577", proc.stdout)
        self.assertIn("updated=0 unchanged=3", proc.stdout)
        after = [(file.read_bytes(), file.stat().st_mtime_ns) for file in outputs]
        self.assertEqual(before, after)


if __name__ == "__main__":
    unittest.main(verbosity=2)
