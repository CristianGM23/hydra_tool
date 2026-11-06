"""Evidence currency and provenance must not depend on file timestamps or on the machine (DEF-ARCH-001, publication blockers B1/B2).

Run: python scripts/archify/v3/tests/test_evidence_provenance.py   (the tools of this checkout; also part of `hydra_archify_v3.py selftest`)
     HYDRA_V3_RUNNER=<other hydra_archify_v3.py> HYDRA_DISCOVER_TESTS=<other discover-tests.js> python ...   (the same tests against other versions)

Currency (`audit_evidence` and `classify` together, they must agree): (1) same content with a newer timestamp stays current; (2) modified content
with an unchanged timestamp and (3) with a newer one is not current; (4) tampered immutable evidence is rejected; (5) a modified dependency
invalidates the tasks that can reach it; (6) another JAVA_HOME or declared environment is never reused; (7) a record without a content fingerprint
is never accepted; (8) CRLF/LF only through the approved eol-canonical rule (EOL_NEUTRAL_SUFFIXES).
Provenance (`discover-tests.js` on a throw-away Git repository): content-identical evidence is cited from the immutable store, not from the local
build dir; host paths copied from reports are machine-independent; report times come from the provenance record bound to the report sha256; and
the discovery is identical after every file timestamp changes."""
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

RUNNER = Path(os.environ.get("HYDRA_V3_RUNNER") or Path(__file__).resolve().parents[1] / "hydra_archify_v3.py")
DISCOVER = Path(os.environ.get("HYDRA_DISCOVER_TESTS") or Path(__file__).resolve().parents[4] / "docs/architecture/archify/knowledge/tests/discover-tests.js")
NODE = os.environ.get("HYDRA_NODE") or shutil.which("node")
SPEC = importlib.util.spec_from_file_location("v3_currency", RUNNER)
v3 = importlib.util.module_from_spec(SPEC)
sys.modules["v3_currency"] = v3
SPEC.loader.exec_module(v3)

TEST_SRC = "src/test/java/a/b/CTests.java"
PROD_SRC = "src/main/java/a/b/C.java"
UNREACHABLE_SRC = "src/main/java/a/b/Unrelated.java"
CONFIG = "config/c.properties"
STARTED = "2026-10-28T11:49:10.000Z"
OLD = 1761652000  # 2025-10-28, before STARTED
NEW = 1793260800  # 2026-10-29T08:00:00Z, after STARTED

XML = ('<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="a.b.CTests" tests="1" errors="0" skipped="0" failures="0" timestamp="2026-10-28T11:50:00">\n'
       '<testcase name="works" classname="a.b.CTests" time="0.01"/>\n</testsuite>')


class Currency(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        names = ("ROOT", "KN", "KT", "V3DOC", "MANIFEST", "OUT", "EVID", "STORE", "INDEX", "STATE", "LOCK")
        self.saved = {k: getattr(v3, k) for k in names if hasattr(v3, k)}
        self.env = mock.patch.dict(os.environ, {"JAVA_HOME": "C:/jdk-25.0.5", "HYDRA_DECLARED": "one"})
        self.env.start()
        v3.ROOT = self.tmp
        v3.KN = self.tmp / "docs/architecture/archify/knowledge"
        v3.KT = v3.KN / "tests"
        v3.V3DOC = v3.KT / "v3"
        v3.MANIFEST = v3.V3DOC / "MANIFEST_CERTIFICACION_V3.json"
        v3.OUT = self.tmp / "target/archify-v3"
        v3.EVID = v3.OUT / "evidence"
        v3.STORE = v3.EVID / "surefire"
        v3.INDEX = v3.EVID / "evidence-index.json"
        v3.STATE = v3.OUT / "state.json"
        v3.LOCK = v3.OUT / ".lock"
        self.clear()
        self.write("pom.xml", "<project/>\n")
        self.write(PROD_SRC, "package a.b;\npublic class C {\n  int value() { return 1; }\n}\n")
        self.write(UNREACHABLE_SRC, "package a.b;\npublic class Unrelated {\n}\n")
        self.write(TEST_SRC, "package a.b;\nclass CTests {\n  C c = new C();\n  void works() {}\n}\n")
        self.write(CONFIG, "key=value\n")
        self.task = {"id": "t.one", "kind": "maven", "selectors": ["a.b.CTests"], "inputs": [PROD_SRC, UNREACHABLE_SRC, CONFIG],
                     "envFingerprint": ["HYDRA_DECLARED"], "scope": [{"processId": "p", "stageId": "s"}]}
        self.write(str(v3.MANIFEST.relative_to(self.tmp)), json.dumps({"schemaVersion": 3, "tasks": [self.task]}))
        self.write(str((v3.KT / "test-catalog.json").relative_to(self.tmp)), json.dumps({"tests": [{"id": "java:a.b.CTests", "caseCount": 1}]}))
        self.write(str((v3.KT / "test-execution-state.json").relative_to(self.tmp)), json.dumps({"tests": {"java:a.b.CTests": {"s": "PASS"}}}))
        for f in (TEST_SRC, PROD_SRC, UNREACHABLE_SRC, CONFIG, "pom.xml"):
            os.utime(self.tmp / f, (OLD, OLD))  # the recorded tree predates the evidence

    def tearDown(self):
        self.env.stop()
        for k, val in self.saved.items():
            setattr(v3, k, val)
        self.clear()
        shutil.rmtree(self.tmp, ignore_errors=True)

    # ---------------------------------------------------------------- helpers
    def clear(self):
        for cache in ("_SHA_CACHE", "_GLOB_CACHE", "_JAVA_CACHE", "_OVERRIDES"):
            if hasattr(v3, cache):
                getattr(v3, cache).clear()

    def write(self, rel, text, keep_mtime=False, binary=False):
        p = self.tmp / rel
        st = p.stat() if keep_mtime and p.exists() else None
        p.parent.mkdir(parents=True, exist_ok=True)
        if p.exists():
            os.chmod(p, 0o666)
        if binary:
            p.write_bytes(text)
        else:
            p.write_text(text, encoding="utf-8", newline="")
        if st:
            os.utime(p, (st.st_atime, st.st_mtime))
        self.clear()

    def record(self, **overrides):
        """Executes nothing: records a PASS exactly as the runner does after a Maven run (fingerprints of the current tree + captured XML)."""
        build = self.tmp / "build/hydra-maven/surefire-reports"
        build.mkdir(parents=True, exist_ok=True)
        (build / "TEST-a.b.CTests.xml").write_text(XML, encoding="utf-8")
        info = v3.capture_reports(build, "suite-1", STARTED)["a.b.CTests"]
        rec = {"kind": "maven", "verdict": "PASS", **v3.fp_fields(self.task), "runId": "suite-1", "startedAt": STARTED, "finishedAt": "2026-10-28T12:10:00.000Z",
               "evidence": {"a.b.CTests": info}}
        rec.update(overrides)
        for k in [k for k, val in rec.items() if val is None]:
            del rec[k]
        v3.STATE.parent.mkdir(parents=True, exist_ok=True)
        v3.STATE.write_text(json.dumps({"schemaVersion": 3, "tasks": {"t.one": rec}, "runs": [{"runId": "suite-1", "verdicts": {"t.one": "PASS"}}], "merged": {}, "quarantine": []}))
        self.clear()
        return rec

    def decide(self):
        """-> (audit ok, plan action). Both must agree: current <=> audit without problems <=> REUSE."""
        self.clear()
        audit = v3.audit_evidence()
        self.clear()
        action, why = v3.classify(self.task, v3.load_state(), set(), False)
        return audit["ok"], action, audit["problems"], why

    def assertCurrent(self):
        ok, action, problems, why = self.decide()
        self.assertTrue(ok, f"audit-evidence must accept current evidence: {problems}")
        self.assertEqual(action, "REUSE", why)

    def assertNotCurrent(self):
        ok, action, problems, why = self.decide()
        self.assertFalse(ok, "audit-evidence must reject evidence that is not current by content")
        self.assertNotEqual(action, "REUSE", why)

    # ---------------------------------------------------------------- guarantees
    def test_g0_freshly_recorded_evidence_is_current(self):
        self.record()
        self.assertCurrent()

    def test_g1_same_content_with_newer_timestamps_stays_current(self):
        """A clean clone or a copy gives every file a newer timestamp; the content (and so the evidence) is the same."""
        self.record()
        for f in (TEST_SRC, PROD_SRC, UNREACHABLE_SRC, CONFIG, "pom.xml"):
            os.utime(self.tmp / f, (NEW, NEW))
        self.assertCurrent()

    def test_g2_modified_test_source_with_unchanged_timestamp_is_not_current(self):
        self.record()
        self.write(TEST_SRC, "package a.b;\nclass CTests {\n  C c = new C();\n  void works() { throw new AssertionError(); }\n}\n", keep_mtime=True)
        self.assertNotCurrent()

    def test_g2_modified_production_input_with_unchanged_timestamp_is_not_current(self):
        self.record()
        self.write(PROD_SRC, "package a.b;\npublic class C {\n  int value() { return 2; }\n}\n", keep_mtime=True)
        self.assertNotCurrent()

    def test_g3_modified_test_source_with_newer_timestamp_is_not_current(self):
        self.record()
        self.write(TEST_SRC, "package a.b;\nclass CTests {\n  C c = new C();\n  void works() { int x = 1; }\n}\n")
        os.utime(self.tmp / TEST_SRC, (NEW, NEW))
        self.assertNotCurrent()

    def test_g4_tampered_evidence_is_rejected(self):
        rec = self.record()
        info = rec["evidence"]["a.b.CTests"]
        p = self.tmp / info["dir"] / info["files"][0]
        st = p.stat()
        os.chmod(p, 0o666)
        p.write_text(XML.replace('time="0.01"', 'time="0.02"'), encoding="utf-8")
        os.utime(p, (st.st_atime, st.st_mtime))
        self.assertNotCurrent()

    def test_g4_tampered_recorded_fingerprint_is_rejected(self):
        rec = self.record()
        forged = {k: ("0" * 64 if k.startswith("fingerprint") else val) for k, val in rec.items()}
        self.record(**forged)
        self.assertNotCurrent()

    def test_g5_modified_dependency_invalidates_the_task_that_reaches_it(self):
        self.record()
        self.write("pom.xml", "<project><version>2</version></project>\n", keep_mtime=True)
        self.assertNotCurrent()

    def test_g5_modified_declared_resource_invalidates_the_task(self):
        self.record()
        self.write(CONFIG, "key=other\n", keep_mtime=True)
        self.assertNotCurrent()

    def test_g5_unreachable_declared_source_keeps_the_approved_closure_v2_reuse(self):
        """Approved scheme closure-v2 (unchanged): a declared production class the test provably cannot reach does not invalidate it."""
        self.record()
        self.write(UNREACHABLE_SRC, "package a.b;\npublic class Unrelated {\n  int other;\n}\n", keep_mtime=True)
        self.assertCurrent()
        _, action, _, why = self.decide()
        self.assertIn("closure-v2", why)

    def test_g6_other_java_home_is_not_reused(self):
        self.record()
        os.environ["JAVA_HOME"] = "C:/jdk-21"
        self.assertNotCurrent()

    def test_g6_other_declared_environment_is_not_reused(self):
        self.record()
        os.environ["HYDRA_DECLARED"] = "two"
        self.assertNotCurrent()

    def test_g7_historical_record_without_fingerprints_is_not_accepted(self):
        self.record(fingerprint=None, fingerprintCanonical=None, fingerprintV2=None, fingerprintV2Canonical=None, closureHashes=None, closureSummary=None)
        self.assertNotCurrent()

    def test_g8_crlf_recorded_java_source_matches_the_lf_checkout(self):
        """Evidence recorded on a Windows working copy (CRLF) and checked in a clean LF checkout: approved eol-canonical rule for .java."""
        crlf = (self.tmp / TEST_SRC).read_bytes().replace(b"\n", b"\r\n")
        self.write(TEST_SRC, crlf, binary=True)
        self.record()
        self.write(TEST_SRC, crlf.replace(b"\r\n", b"\n"), binary=True)
        os.utime(self.tmp / TEST_SRC, (NEW, NEW))
        self.assertCurrent()

    def test_g8_crlf_change_in_a_file_outside_the_eol_rule_is_not_current(self):
        crlf = b"key=value\r\n"
        self.write(CONFIG, crlf, binary=True)
        self.record()
        self.write(CONFIG, b"key=value\n", binary=True, keep_mtime=True)
        self.assertNotCurrent()

    # ---------------------------------------------------------------- mutation sweep
    def test_mutation_every_input_byte_flip_invalidates_unless_provably_unreachable(self):
        """Flip one byte of every fingerprinted input while keeping its timestamp: each one must make the evidence not current, except the
        declared source that closure-v2 proves unreachable."""
        self.record()
        self.clear()
        inputs = v3.task_files(self.task)
        self.assertIn(TEST_SRC, inputs)
        for f in inputs:
            if not (self.tmp / f).is_file():
                continue
            with self.subTest(input=f):
                original = (self.tmp / f).read_bytes()
                self.write(f, original[:-1] + (b"#" if original[-1:] != b"#" else b"!"), keep_mtime=True, binary=True)
                try:
                    if f == UNREACHABLE_SRC:
                        self.assertCurrent()
                    else:
                        self.assertNotCurrent()
                finally:
                    self.write(f, original, keep_mtime=True, binary=True)  # each mutation is judged alone
        self.assertCurrent()

    def test_mutation_every_recorded_fingerprint_field_is_checked_with_the_tree(self):
        """Each approved scheme is accepted only when its OWN value matches: forging one field while the others are absent must fail."""
        rec = self.record()
        for field in ("fingerprint", "fingerprintCanonical", "fingerprintV2", "fingerprintV2Canonical"):
            with self.subTest(field=field):
                keep = {k: (rec[k] if k == field else None) for k in ("fingerprintCanonical", "fingerprintV2", "fingerprintV2Canonical")}
                self.record(**{**keep, "fingerprint": rec["fingerprint"] if field == "fingerprint" else "f" * 64, field: "e" * 64})
                self.assertNotCurrent()


@unittest.skipUnless(NODE and DISCOVER.is_file() and shutil.which("git"), "node, git and discover-tests.js are required")
class Provenance(unittest.TestCase):
    REPORT = "target/release/rc2-qualification.json"

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        kt = self.tmp / "docs/architecture/archify/knowledge/tests"
        kt.mkdir(parents=True)
        shutil.copy(DISCOVER, kt / "discover-tests.js")
        self.put(PROD_SRC, "package a.b;\npublic class C {\n}\n")
        self.put(TEST_SRC, "package a.b;\nclass CTests {\n  C c = new C();\n  @Test\n  void works() {}\n}\n")
        self.put(self.REPORT, json.dumps({"status": "FAIL", "fingerprint": "ab" * 32, "steps": [{
            "name": "workspace-integrity", "passed": True, "required": True, "seconds": 3, "reused": False, "skipped": False,
            "command": ["C:\\Users\\someone\\AppData\\Local\\hydra-runtimes\\venv\\Scripts\\python.exe", "scripts/validation/verify_manifest.py", "--allow-extra"],
            "outputTail": "checked C:\\Users\\someone\\work\\hydra\\frontend\\x.ts OK\n/home/someone/hydra/y OK"}]}))
        for args in (["init", "-q"], ["add", "-A"], ["-c", "user.name=t", "-c", "user.email=t@example.org", "commit", "-qm", "fixture"]):
            subprocess.run(["git", *args], cwd=self.tmp, check=True, capture_output=True)
        self.recorded = "2026-10-28T11:28:54.511Z"

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def put(self, rel, text):
        p = self.tmp / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")
        return p

    def evidence(self, build_xml=XML, store_xml=XML):
        """The same Maven run in the volatile build dir (newer timestamp) and in the immutable store (indexed as finished earlier)."""
        if build_xml is not None:
            self.put("build/hydra-maven/surefire-reports/TEST-a.b.CTests.xml", build_xml)
        store = self.put("target/archify-v3/evidence/surefire/suite-1/TEST-a.b.CTests.xml", store_xml)
        self.put("target/archify-v3/evidence/evidence-index.json", json.dumps({"schemaVersion": 1, "classes": {"a.b.CTests": {
            "dir": "target/archify-v3/evidence/surefire/suite-1", "files": ["TEST-a.b.CTests.xml"], "runId": "suite-1", "startedAt": STARTED,
            "finishedAt": "2026-10-28T12:10:00.000Z", "sha256": {"TEST-a.b.CTests.xml": hashlib.sha256(store.read_bytes()).hexdigest()}}}}))

    def provenance(self, sha=None):
        sha = sha or hashlib.sha256((self.tmp / self.REPORT).read_bytes()).hexdigest()
        self.put("target/release/evidence-provenance.json", json.dumps({"schemaVersion": 1, "reports": {self.REPORT: {"sha256": sha, "recordedAt": self.recorded}}}))

    def discover(self):
        out = Path(tempfile.mkdtemp()) / "discovery.json"  # outside the repository: it must not count as an untracked file
        r = subprocess.run([NODE, str(self.tmp / "docs/architecture/archify/knowledge/tests/discover-tests.js"), "--out", str(out)], cwd=self.tmp, capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr[-800:])
        return json.loads(out.read_text(encoding="utf-8"))

    def suite(self, d):
        return next(s for s in d["suites"] if s["id"] == "java:a.b.CTests")

    def test_identical_build_and_store_evidence_is_cited_from_the_store(self):
        self.evidence()
        d = self.discover()
        ex = self.suite(d)["executed"]
        self.assertEqual((ex["from"], ex["run"]), ("store", "suite-1"), "the portable, immutable copy must be cited")
        self.assertIsNone(d["evidence"]["surefire"]["dir"], "no class is cited from the local build dir")
        self.assertIsNone(d["evidence"]["surefire"]["start"])
        self.assertTrue(d["evidence"]["surefire"]["sample"].startswith("target/archify-v3/evidence/surefire/"))

    def test_newer_different_build_evidence_still_wins(self):
        """Control: a newer run not yet captured in the store is real evidence and stays cited from the build dir."""
        self.evidence(build_xml=XML.replace('time="0.01"', 'time="0.03"'))
        d = self.discover()
        self.assertEqual(self.suite(d)["executed"]["from"], "build")
        self.assertEqual(d["evidence"]["surefire"]["dir"], "build/hydra-maven/surefire-reports")

    def test_host_paths_from_reports_are_machine_independent(self):
        self.evidence()
        g = self.discover()["gates"][0]
        text = g["command"] + "\n" + g["outputTail"]
        self.assertNotIn("someone", text)
        self.assertNotRegex(text, r"[A-Za-z]:[\\/]")
        self.assertIn("<hydra-runtimes>/venv/Scripts/python.exe scripts/validation/verify_manifest.py", g["command"])
        self.assertIn("<home>/work/hydra/frontend/x.ts", g["outputTail"])
        self.assertIn("<home>/hydra/y", g["outputTail"])
        self.assertEqual(g.get("reportSha256"), hashlib.sha256((self.tmp / self.REPORT).read_bytes()).hexdigest(), "the untouched report stays verifiable")

    def test_working_copy_roots_outside_a_home_directory_are_portable(self):
        """A qualification run in a clone outside any home directory (e.g. C:\\h\\pc) records paths of that clone: the provenance record names that
        working copy and discovery rewrites it, like the current checkout, to <repo>."""
        report = json.loads((self.tmp / self.REPORT).read_text(encoding="utf-8"))
        report["steps"][0]["outputTail"] = "PASS maven executable=C:\\h\\qualif\\mvnw.cmd\nTOOLCHAIN_INVENTORY=C:/h/qualif/target/diagnostics/x.json\n" + str(self.tmp / "frontend" / "y.ts")
        self.put(self.REPORT, json.dumps(report))
        self.evidence()
        sha = hashlib.sha256((self.tmp / self.REPORT).read_bytes()).hexdigest()
        self.put("target/release/evidence-provenance.json", json.dumps({"schemaVersion": 1, "reports": {self.REPORT: {
            "sha256": sha, "recordedAt": self.recorded, "workingCopyRoot": "C:\\h\\qualif"}}}))
        g = self.discover()["gates"][0]
        self.assertNotRegex(g["outputTail"], r"[A-Za-z]:[\\/]")
        self.assertIn("executable=<repo>/mvnw.cmd", g["outputTail"])
        self.assertIn("TOOLCHAIN_INVENTORY=<repo>/target/diagnostics/x.json", g["outputTail"])
        self.assertIn("<repo>/frontend/y.ts", g["outputTail"])

    def test_report_time_comes_from_provenance_bound_to_its_bytes(self):
        self.evidence()
        self.provenance()
        os.utime(self.tmp / self.REPORT, (NEW, NEW))
        g = self.discover()["gates"][0]
        self.assertEqual((g["evidenceTime"], g.get("evidenceTimeSource")), (self.recorded, "provenance"))

    def test_provenance_for_other_bytes_is_ignored_and_the_fallback_is_declared(self):
        self.evidence()
        self.provenance(sha="0" * 64)
        os.utime(self.tmp / self.REPORT, (NEW, NEW))
        g = self.discover()["gates"][0]
        self.assertEqual(g.get("evidenceTimeSource"), "file-mtime")
        self.assertNotEqual(g["evidenceTime"], self.recorded)

    def test_discovery_is_identical_after_every_timestamp_changes(self):
        """A clone, a copy or an unzip rewrites timestamps; the published discovery must not change (generatedAt aside)."""
        self.evidence()
        self.provenance()
        first = self.discover()
        for p in self.tmp.rglob("*"):
            if p.is_file() and ".git" not in p.parts:
                os.utime(p, (NEW, NEW))
        second = self.discover()
        for d in (first, second):
            d.pop("generatedAt")
        self.assertEqual(first, second)


if __name__ == "__main__":
    print("runner:", RUNNER, "\ndiscover-tests:", DISCOVER)
    unittest.main(verbosity=2)
