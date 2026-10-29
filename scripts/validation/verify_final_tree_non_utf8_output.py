#!/usr/bin/env python3
"""DEF-003: a readiness step whose output is not valid UTF-8 must be judged by its exit code and keep its real diagnostic.

verify_final_tree.py executes every step when imported, so the test compiles only its real `run()` function (extracted from the source with
`ast`) and drives it with child processes that write bytes which are not valid UTF-8, as some Windows tools and tests do."""
import ast
import subprocess
import sys
import time
import unittest
from pathlib import Path
import os
import re

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'scripts/validation/verify_final_tree.py'


def load_run():
    tree = ast.parse(SOURCE.read_text(encoding='utf-8'))
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'run')
    namespace = {'os': os, 're': re, 'subprocess': subprocess, 'time': time, 'Path': Path, 'ROOT': ROOT, 'steps': []}
    exec(compile(ast.Module(body=[node], type_ignores=[]), str(SOURCE), 'exec'), namespace)
    return namespace['run'], namespace['steps']


CHILD = "import sys; sys.stdout.buffer.write(b'Tests run: 1, Failures: 0\\n\\xe1\\xe9 cp1252 bytes\\n'); sys.stdout.flush(); sys.exit({code})"


class NonUtf8StepOutput(unittest.TestCase):
    def test_passing_step_with_non_utf8_output_passes(self):
        run, steps = load_run()
        self.assertTrue(run('non-utf8 passing step', [sys.executable, '-c', CHILD.format(code=0)], timeout=60))
        self.assertTrue(steps[-1]['passed'])
        self.assertIn('Tests run: 1', steps[-1]['outputTail'])

    def test_failing_step_keeps_its_real_diagnostic(self):
        run, steps = load_run()
        self.assertFalse(run('non-utf8 failing step', [sys.executable, '-c', CHILD.format(code=3)], timeout=60))
        self.assertIn('Tests run: 1', steps[-1]['outputTail'])
        self.assertNotIn('NoneType', steps[-1]['outputTail'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
