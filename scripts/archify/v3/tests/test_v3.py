"""Unit tests for the V3 runner's pure logic (no Maven, no network). Run: python scripts/archify/v3/hydra_archify_v3.py selftest"""
import importlib.util
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("v3", Path(__file__).resolve().parents[1] / "hydra_archify_v3.py")
v3 = importlib.util.module_from_spec(SPEC)
sys.modules["v3"] = v3
SPEC.loader.exec_module(v3)

XML = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="{name}" tests="{n}" errors="0" skipped="{s}" failures="{f}" timestamp="2026-10-19T08:00:00">
{cases}
<system-out>hello</system-out></testsuite>"""


def case(name, kind=None):
    inner = {"fail": '<failure message="boom"/>', "skip": "<skipped/>", None: ""}[kind]
    return f'<testcase name="{name}" classname="x" time="0.01">{inner}</testcase>'


def write_xml(d: Path, fqn: str, cases):
    d.mkdir(parents=True, exist_ok=True)
    f = sum(1 for c in cases if c[1] == "fail")
    s = sum(1 for c in cases if c[1] == "skip")
    (d / f"TEST-{fqn}.xml").write_text(XML.format(name=fqn, n=len(cases), s=s, f=f, cases="\n".join(case(*c) for c in cases)), encoding="utf-8")


class Sandbox(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.saved = {k: getattr(v3, k) for k in ("ROOT", "OUT", "EVID", "STORE", "INDEX", "STATE", "LOCK")}
        v3.ROOT = self.tmp
        v3._SHA_CACHE.clear()
        v3._GLOB_CACHE.clear()
        v3.OUT = self.tmp / "target" / "archify-v3"
        v3.EVID = v3.OUT / "evidence"
        v3.STORE = v3.EVID / "surefire"
        v3.INDEX = v3.EVID / "evidence-index.json"
        v3.STATE = v3.OUT / "state.json"
        v3.LOCK = v3.OUT / ".lock"
        (self.tmp / "pom.xml").write_text("<project/>")

    def tearDown(self):
        for k, val in self.saved.items():
            setattr(v3, k, val)

    def task(self, **kw):
        t = {"id": "t.one", "kind": "maven", "selectors": ["a.b.CTests"], "inputs": ["src/main/java/a/b/C.java"], "scope": [{"processId": "p", "stageId": "s"}]}
        t.update(kw)
        return t

    def touch(self, rel, text="x"):
        p = self.tmp / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text)
        v3._SHA_CACHE.clear()  # the runner memoises hashes per process; tests mutate files
        v3._GLOB_CACHE.clear()


class ManifestTests(Sandbox):
    def test_valid(self):
        self.assertEqual(v3.validate_manifest({"schemaVersion": 3, "tasks": [self.task()]}), [])

    def test_rules(self):
        m = {"schemaVersion": 3, "tasks": [self.task(id="Bad Id"), self.task(id="t.two", status="BLOCKED"), self.task(id="t.three", selectors=["a b"]),
                                           self.task(id="t.one"), self.task(id="t.one"), self.task(id="t.four", scope=[]), self.task(id="t.five", inputs=["../x"])]}
        e = "\n".join(v3.validate_manifest(m))
        for frag in ("bad id", "BLOCKED requires blockedReason", "bad selector", "duplicate id", "needs scope", "outside repo"):
            self.assertIn(frag, e)

    def test_argv_allowlist(self):
        self.assertTrue(v3.check_argv(["curl", "http://x"]))
        self.assertTrue(v3.check_argv(["mvnw.cmd", "-DskipTests"]))
        self.assertTrue(v3.check_argv(["npm", "publish"]))
        self.assertEqual(v3.check_argv(["node", "script.js"]), [])


class BatchTests(Sandbox):
    def test_pattern_and_single_batch(self):
        self.assertEqual(v3.surefire_pattern("a.b.CTests#m"), "a/b/CTests#m")
        tasks = [self.task(id=f"t.{i}", selectors=[f"pkg.sub.Class{i}Tests"]) for i in range(60)]
        self.assertEqual(len(v3.maven_batches(tasks)), 1, "identical maven context must be ONE invocation")
        argv = v3.maven_argv([s for t in tasks for s in t["selectors"]])
        self.assertTrue(any(a.startswith("-Dtest=") for a in argv))
        self.assertFalse(any(a in ("-DskipTests",) for a in argv))

    def test_split_only_over_command_limit(self):
        tasks = [self.task(id=f"t.{i}", selectors=["very.long.package.name." + "x" * 150 + f"{i}Tests"]) for i in range(80)]
        self.assertGreater(len(v3.maven_batches(tasks)), 1)


class EvidenceTests(Sandbox):
    def test_verdicts(self):
        d = v3.STORE / "run1"
        write_xml(d, "a.b.PassTests", [("ok1", None), ("ok2", None)])
        write_xml(d, "a.b.FailTests", [("ok", None), ("bad", "fail")])
        write_xml(d, "a.b.SkipTests", [("s", "skip")])
        self.assertEqual(v3.selector_verdict("a.b.PassTests", d)["verdict"], "PASS")
        self.assertEqual(v3.selector_verdict("a.b.FailTests", d)["verdict"], "FAIL")
        self.assertEqual(v3.selector_verdict("a.b.SkipTests", d)["verdict"], "SKIPPED")
        self.assertEqual(v3.selector_verdict("a.b.Missing", d)["verdict"], "NOT_EXECUTED")
        self.assertEqual(v3.selector_verdict("a.b.PassTests#ok2", d)["cases"], 1)
        self.assertEqual(v3.selector_verdict("a.b.PassTests#nope", d)["verdict"], "NOT_EXECUTED")
        per = [v3.selector_verdict(s, d) for s in ("a.b.PassTests", "a.b.FailTests")]
        self.assertEqual(v3.task_verdict(per), "FAIL")
        self.assertEqual(v3.task_verdict([v3.selector_verdict("a.b.PassTests", d), v3.selector_verdict("a.b.Missing", d)]), "NOT_EXECUTED")

    def test_capture_is_immutable_and_newest_wins(self):
        src = self.tmp / "build"
        write_xml(src, "a.b.CTests", [("t", None)])
        v3.capture_reports(src, "r1", "2026-10-19T08:00:00.000Z")
        idx = v3.load_index()["classes"]["a.b.CTests"]
        self.assertEqual(idx["runId"], "r1")
        self.assertFalse(os.access(v3.STORE / "r1" / "TEST-a.b.CTests.xml", os.W_OK), "stored XML must be read-only")
        os.utime(src / "TEST-a.b.CTests.xml", (4102444800, 4102444800))  # far future => newer run
        v3.capture_reports(src, "r2", "2099-01-01T00:00:00.000Z")
        self.assertEqual(v3.load_index()["classes"]["a.b.CTests"]["runId"], "r2")
        self.assertTrue((v3.STORE / "r1" / "TEST-a.b.CTests.xml").exists(), "older evidence is never deleted")

    def test_evidence_tamper_detected(self):
        src = self.tmp / "build"
        write_xml(src, "a.b.CTests", [("t", None)])
        info = v3.capture_reports(src, "r1", "2026-10-19T08:00:00.000Z")["a.b.CTests"]
        rec = {"verdict": "PASS", "kind": "maven", "evidence": {"a.b.CTests": info}}
        self.assertTrue(v3.evidence_intact(rec))
        p = v3.ROOT / info["dir"] / info["files"][0]
        os.chmod(p, 0o666)
        p.write_text(p.read_text() + "<!-- tampered -->")
        self.assertFalse(v3.evidence_intact(rec))


class PlanningTests(Sandbox):
    def test_reuse_vs_run_vs_blocked(self):
        self.touch("src/main/java/a/b/C.java")
        self.touch("src/test/java/a/b/CTests.java")
        t = self.task()
        state = {"tasks": {}}
        self.assertEqual(v3.classify(t, state, set(), False)[0], "RUN")
        src = self.tmp / "build"
        write_xml(src, "a.b.CTests", [("t", None)])
        info = v3.capture_reports(src, "r1", "2026-10-19T08:00:00.000Z")["a.b.CTests"]
        state["tasks"]["t.one"] = {"verdict": "PASS", "kind": "maven", "fingerprint": v3.fingerprint(t)["sha256"], "evidence": {"a.b.CTests": info}}
        self.assertEqual(v3.classify(t, state, set(), False)[0], "REUSE")
        self.assertEqual(v3.classify(t, state, {"t.one"}, False)[0], "RUN", "--force")
        self.touch("src/main/java/a/b/C.java", "changed production code")
        action, why = v3.classify(t, state, set(), False)
        self.assertEqual(action, "RUN")
        self.assertIn("inputs changed", why)

    def test_pom_change_invalidates(self):
        self.touch("src/main/java/a/b/C.java")
        self.touch("src/test/java/a/b/CTests.java")
        t = self.task()
        a = v3.fingerprint(t)["sha256"]
        (self.tmp / "pom.xml").write_text("<project><dep/></project>")
        v3._SHA_CACHE.clear()
        self.assertNotEqual(a, v3.fingerprint(t)["sha256"], "pom.xml is part of every fingerprint (V2 inputs omitted it)")

    def test_blocked_and_planned_never_run(self):
        self.assertEqual(v3.classify(self.task(status="BLOCKED", blockedReason="needs OAuth"), {"tasks": {}}, set(), False), ("BLOCKED", "needs OAuth"))
        self.assertEqual(v3.classify(self.task(status="PLANNED"), {"tasks": {}}, set(), False)[0], "PLANNED")
        self.assertEqual(v3.classify(self.task(requires={"env": ["V3_TEST_MISSING_ENV"]}), {"tasks": {}}, set(), False)[0], "BLOCKED")

    def test_failed_not_repeated_without_changes(self):
        self.touch("src/main/java/a/b/C.java")
        self.touch("src/test/java/a/b/CTests.java")
        t = self.task()
        state = {"tasks": {"t.one": {"verdict": "FAIL", "fingerprint": v3.fingerprint(t)["sha256"]}}}
        self.assertEqual(v3.classify(t, state, set(), False)[0], "SKIP")
        self.assertEqual(v3.classify(t, state, set(), True)[0], "RUN")


    def test_compile_error_is_never_reused_and_resumes_after_the_fix(self):
        self.touch("src/main/java/a/b/C.java")
        self.touch("src/test/java/a/b/CTests.java")
        t = self.task()
        state = {"tasks": {"t.one": {"verdict": "COMPILE_ERROR", "fingerprint": v3.fingerprint(t)["sha256"]}}}
        self.assertEqual(v3.classify(t, state, set(), False)[0], "SKIP", "not PASS, not reused, held until the inputs change")
        self.touch("src/test/java/a/b/CTests.java", "fixed test source")
        action, why = v3.classify(t, state, set(), False)
        self.assertEqual(action, "RUN", "resumes automatically once the quarantined test is corrected")
        self.assertIn("inputs changed", why)
        self.assertTrue(v3.fragment_gate([{"id": "x", "strength": "STRONG"}], {}, "COMPILE_ERROR"), "no fragment (hence no STRONG) from a quarantined task")

class CompileAndGateTests(Sandbox):
    def test_parse_compile_errors(self):
        out = "[ERROR] COMPILATION ERROR :\n[ERROR] /C:/x/hydra/src/test/java/com/example/FooTests.java:[12,5] cannot find symbol\n[ERROR] /C:/x/hydra/src/main/java/com/example/Bar.java:[3,1] boom\n"
        e = v3.parse_compile_errors(out)
        self.assertEqual([x["file"] for x in e], ["src/test/java/com/example/FooTests.java", "src/main/java/com/example/Bar.java"])

    def test_fragment_gate_never_promotes_without_basis(self):
        strong = [{"id": "x", "strength": "STRONG", "type": "INTEGRATION", "external": "none", "strengthWhy": "real chain", "procs": [{"id": "p", "match": "direct", "stages": ["s"]}]}]
        self.assertEqual(v3.fragment_gate(strong, {}, "PASS"), [])
        self.assertTrue(v3.fragment_gate(strong, {}, "FAIL"), "fragments of failing tasks are withheld")
        self.assertTrue(v3.fragment_gate([dict(strong[0], type="UNIT")], {}, "PASS"))
        self.assertTrue(v3.fragment_gate([dict(strong[0], external="mocked")], {}, "PASS"))
        self.assertTrue(v3.fragment_gate([dict(strong[0], procs=[{"id": "p", "match": "indirect", "stages": ["s"]}])], {}, "PASS"))
        self.assertTrue(v3.fragment_gate([dict(strong[0], strengthWhy="")], {}, "PASS"))

    def test_norm_generated(self):
        self.assertEqual(v3.norm_generated('{"generatedAt": "2026-01-01T00:00:00Z", "a": 1}'), '{"generatedAt":"NORM", "a": 1}')


if __name__ == "__main__":
    unittest.main()
