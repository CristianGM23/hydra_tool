"""Unit tests for the V3 runner's pure logic (no Maven, no network). Run: python scripts/archify/v3/hydra_archify_v3.py selftest"""
import argparse
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
        v3._JAVA_CACHE.clear()
        v3._OVERRIDES.clear()
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


class ClosureFingerprintTests(Sandbox):
    """The invalidation mechanism itself: relevant changes invalidate, unrelated ones do not, uncertainty stays conservative."""

    def setUp(self):
        super().setUp()
        self.touch("src/test/java/a/b/CTests.java", "package a.b;\nimport a.b.c.Svc;\nclass CTests { Svc s; }\n")
        self.touch("src/main/java/a/b/c/Svc.java", "package a.b.c;\nimport a.b.d.Dep;\npublic class Svc { Dep d; }\n")
        self.touch("src/main/java/a/b/d/Dep.java", "package a.b.d;\npublic class Dep { }\n")
        self.touch("src/main/java/a/b/Unrelated.java", "package a.b;\npublic class Unrelated { }\n")
        self.task_inputs = ["src/test/java/a/b/CTests.java", "src/main/java/a/b/c/Svc.java", "src/main/java/a/b/Unrelated.java"]

    def t(self, **kw):
        return self.task(inputs=kw.pop("inputs", self.task_inputs), **kw)

    def fp2(self, task):
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        return v3.fingerprint_v2(task)

    def fp1(self, task):
        v3._SHA_CACHE.clear()
        return v3.fingerprint(task)["sha256"]

    def test_unreachable_declared_input_is_reported_and_does_not_invalidate(self):
        t = self.t()
        before1, before2 = self.fp1(t), self.fp2(t)
        self.assertTrue(before2["bounded"])
        self.assertEqual(before2["unreachableDeclared"], ["src/main/java/a/b/Unrelated.java"])
        self.touch("src/main/java/a/b/Unrelated.java", "package a.b;\npublic class Unrelated { int changed; }\n")
        self.assertNotEqual(before1, self.fp1(t), "v1 hashes every declared input")
        self.assertEqual(before2["sha256"], self.fp2(t)["sha256"], "v2 ignores a production file the test cannot reach")

    def test_a_change_anywhere_in_the_transitive_closure_invalidates(self):
        t = self.t()
        before = self.fp2(t)["sha256"]
        self.touch("src/main/java/a/b/d/Dep.java", "package a.b.d;\npublic class Dep { int x; }\n")  # reached only through Svc
        self.assertNotEqual(before, self.fp2(t)["sha256"])
        self.touch("src/test/java/a/b/CTests.java", "package a.b;\nimport a.b.c.Svc;\nclass CTests { Svc s; int y; }\n")
        self.assertNotEqual(before, self.fp2(t)["sha256"])

    def test_same_package_wildcard_and_qualified_references_are_followed(self):
        self.touch("src/main/java/a/b/Helper.java", "package a.b;\npublic class Helper { }\n")
        self.touch("src/main/java/a/b/w/One.java", "package a.b.w;\npublic class One { }\n")
        self.touch("src/main/java/a/b/q/Named.java", "package a.b.q;\npublic class Named { }\n")
        self.touch("src/test/java/a/b/CTests.java", "package a.b;\nimport a.b.w.*;\nclass CTests { Helper h; String n = \"a.b.q.Named\"; }\n")
        closure = self.fp2(self.t(inputs=["src/test/java/a/b/CTests.java"]))
        self.assertTrue(closure["bounded"])
        idx = v3.java_index()
        reached, _, _ = idx.closure(["src/test/java/a/b/CTests.java"])
        self.assertTrue({"src/main/java/a/b/Helper.java", "src/main/java/a/b/w/One.java", "src/main/java/a/b/q/Named.java"} <= reached)
        self.assertNotIn("src/main/java/a/b/Unrelated.java", reached)

    def test_spring_context_tests_stay_unbounded_and_equal_to_v1(self):
        self.touch("src/test/java/a/b/CTests.java", "package a.b;\nimport a.b.c.Svc;\n@SpringBootTest\nclass CTests { Svc s; }\n")
        t = self.t()
        result = self.fp2(t)
        self.assertFalse(result["bounded"])
        self.assertIn("Spring", result["reasons"][0])
        self.assertEqual(result["sha256"], self.fp1(t))
        self.touch("src/main/java/a/b/Unrelated.java", "package a.b;\npublic class Unrelated { int z; }\n")
        self.assertNotEqual(result["sha256"], self.fp2(t)["sha256"], "the whole application is reachable from a Spring test")

    def test_globs_non_maven_kinds_and_the_declared_policy_stay_conservative(self):
        self.assertFalse(self.fp2(self.t(inputGlobs=["src/main/java/**/*.java"]))["bounded"])
        self.assertFalse(self.fp2({"id": "p", "kind": "playwright", "specs": ["e2e/x.spec.ts"], "inputs": []})["bounded"])
        self.assertFalse(self.fp2(self.t(fingerprintPolicy="declared"))["bounded"])
        missing = self.fp2(self.task(selectors=["a.b.MissingTests"], inputs=[]))
        self.assertFalse(missing["bounded"], "an unreadable test source cannot be bounded")

    def test_resource_loading_pulls_in_every_resource(self):
        self.touch("src/test/resources/fixtures/f.txt", "one")
        self.touch("src/test/java/a/b/CTests.java", "package a.b;\nclass CTests { Object o = CTests.class.getResourceAsStream(\"/fixtures/f.txt\"); }\n")
        t = self.t(inputs=["src/test/java/a/b/CTests.java"])
        before = self.fp2(t)["sha256"]
        self.touch("src/test/resources/fixtures/f.txt", "two")
        self.assertNotEqual(before, self.fp2(t)["sha256"])

    def record(self, t):
        src = self.tmp / "build"
        write_xml(src, "a.b.CTests", [("t", None)])
        info = v3.capture_reports(src, "r1", "2026-10-19T08:00:00.000Z")["a.b.CTests"]
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        return {"verdict": "PASS", "kind": "maven", "evidence": {"a.b.CTests": info}, "finishedAt": "2026-10-19T09:00:00.000Z", **v3.fp_fields(t)}

    def test_classify_reuses_only_when_the_reachable_closure_is_unchanged(self):
        t = self.t()
        state = {"tasks": {"t.one": self.record(t)}}
        self.assertEqual(v3.classify(t, state, set(), False)[0], "REUSE")
        self.touch("src/main/java/a/b/Unrelated.java", "package a.b;\npublic class Unrelated { int z; }\n")
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        action, why = v3.classify(t, state, set(), False)
        self.assertEqual(action, "REUSE", why)
        self.assertIn("closure-v2", why)
        self.assertEqual(v3.classify(t, state, set(), False, strict=True)[0], "RUN", "--strict-fingerprint falls back to every declared input")
        self.assertEqual(v3.classify(t, state, {"*"}, False)[0], "RUN", "a full revalidation can always be forced")
        self.assertEqual(v3.classify(t, state, {"t.one"}, False)[0], "RUN")
        self.touch("src/main/java/a/b/d/Dep.java", "package a.b.d;\npublic class Dep { int x; }\n")
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        action, why = v3.classify(t, state, set(), False)
        self.assertEqual(action, "RUN")
        self.assertIn("src/main/java/a/b/d/Dep.java", why, "the reason names the reachable file that changed")

    def test_a_record_without_closure_data_is_never_narrowed(self):
        t = self.t()
        rec = self.record(t)
        for key in ("fingerprintV2", "closureHashes", "closureSummary"):
            rec.pop(key)
        state = {"tasks": {"t.one": rec}}
        self.touch("src/main/java/a/b/Unrelated.java", "package a.b;\npublic class Unrelated { int z; }\n")
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        self.assertEqual(v3.classify(t, state, set(), False)[0], "RUN", "legacy evidence keeps the v1 behaviour until it is attested")

    def test_attestation_binds_evidence_to_the_reconstructed_baseline(self):
        t = self.t()
        rec = self.record(t)
        for key in ("fingerprintV2", "closureHashes", "closureSummary"):
            rec.pop(key)  # evidence recorded before the closure scheme existed
        # the change set after the evidence: one production file the test cannot reach, one it can
        baseline_unrelated = (self.tmp / "src/main/java/a/b/Unrelated.java").read_bytes()
        baseline_dep = (self.tmp / "src/main/java/a/b/d/Dep.java").read_bytes()
        self.touch("src/main/java/a/b/Unrelated.java", "package a.b;\npublic class Unrelated { int z; }\n")
        self.touch("src/main/java/a/b/d/Dep.java", "package a.b.d;\npublic class Dep { int x; }\n")
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        self.assertEqual(v3.classify(t, {"tasks": {"t.one": rec}}, set(), False)[0], "RUN")
        # reconstruct the baseline: exactly the files the caller declares, with the content they had
        v3._OVERRIDES.update({"src/main/java/a/b/Unrelated.java": baseline_unrelated, "src/main/java/a/b/d/Dep.java": baseline_dep})
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        self.assertEqual(v3.fingerprint(t)["sha256"], rec["fingerprint"], "the reconstructed baseline reproduces the stored fingerprint")
        v2 = v3.fingerprint_v2(t)
        v3._OVERRIDES.clear()
        rec.update({"fingerprintV2": v2["sha256"], "closureHashes": v2["hashes"]})
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        action, why = v3.classify(t, {"tasks": {"t.one": rec}}, set(), False)
        self.assertEqual(action, "RUN", "Dep changed and is reachable, so attested evidence is still invalidated")
        self.assertIn("src/main/java/a/b/d/Dep.java", why)
        (self.tmp / "src/main/java/a/b/d/Dep.java").write_bytes(baseline_dep)  # bytes, not text: write_text would translate line endings on Windows
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        self.assertEqual(v3.classify(t, {"tasks": {"t.one": rec}}, set(), False)[0], "REUSE", "only the unreachable change remains")

    def test_a_baseline_that_does_not_reproduce_the_fingerprint_is_not_attested(self):
        t = self.t()
        rec = self.record(t)
        self.touch("src/main/java/a/b/c/Svc.java", "package a.b.c;\nimport a.b.d.Dep;\npublic class Svc { Dep d; int drifted; }\n")  # changed and NOT declared as part of the change set
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        self.assertNotEqual(v3.fingerprint(t)["sha256"], rec["fingerprint"], "an undeclared change breaks the attestation precondition")

    def test_currency_lists_only_evidence_verified_by_content(self):
        t = self.t()
        m = {"tasks": [t]}
        state = {"tasks": {"t.one": self.record(t)}}
        self.assertEqual(list(v3.write_currency(m, state)["ids"]), ["java:a.b.CTests"])
        self.assertEqual(v3.write_currency(m, state)["ids"]["java:a.b.CTests"]["scheme"], "fingerprint-v1")
        self.touch("src/main/java/a/b/Unrelated.java", "package a.b;\npublic class Unrelated { int z; }\n")
        v3._SHA_CACHE.clear()
        self.assertEqual(v3.write_currency(m, state)["ids"]["java:a.b.CTests"]["scheme"], "closure-v2", "kept current because the test cannot reach the change")
        self.touch("src/main/java/a/b/d/Dep.java", "package a.b.d;\npublic class Dep { int x; }\n")
        v3._SHA_CACHE.clear()
        self.assertEqual(v3.write_currency(m, state)["ids"], {}, "a reachable change removes the claim: the timestamp rule keeps it stale")
        state["tasks"]["t.one"]["verdict"] = "FAIL"
        self.assertEqual(v3.write_currency(m, state)["ids"], {}, "a failing record is never current")

    def test_rerun_keeps_the_history_of_the_superseded_evidence(self):
        old = {"verdict": "PASS", "fingerprint": "f1", "fingerprintV2": "g1", "runId": "r1", "finishedAt": "t1", "evidence": {}}
        entry = v3._history_entry(old)
        self.assertEqual(entry, {"verdict": "PASS", "fingerprint": "f1", "fingerprintV2": "g1", "runId": "r1", "finishedAt": "t1"})


class EolCanonicalTests(Sandbox):
    """A clean checkout (LF) recognises evidence recorded on a working copy whose sources were written with CRLF, and nothing else."""

    def setUp(self):
        super().setUp()
        self.put("src/main/java/a/b/C.java", b"package a.b;\r\npublic class C { }\r\n")
        self.put("src/test/java/a/b/CTests.java", b"package a.b;\r\nclass CTests { C c; }\r\n")

    def put(self, rel, data: bytes):
        p = self.tmp / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)  # bytes, not text: write_text would translate line endings on Windows
        v3._SHA_CACHE.clear(); v3._GLOB_CACHE.clear(); v3._JAVA_CACHE.clear()

    def to_lf(self, rel):
        self.put(rel, (self.tmp / rel).read_bytes().replace(b"\r\n", b"\n"))

    def recorded(self, t):
        src = self.tmp / "build"
        write_xml(src, "a.b.CTests", [("t", None)])
        info = v3.capture_reports(src, "r1", "2026-10-19T08:00:00.000Z")["a.b.CTests"]
        v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        return {"verdict": "PASS", "kind": "maven", "evidence": {"a.b.CTests": info}, "finishedAt": "2026-10-19T09:00:00.000Z", **v3.fp_fields(t)}

    def test_line_endings_of_source_files_fold_and_their_content_does_not(self):
        t = self.task()
        raw, canonical = v3.fingerprint(t)["sha256"], v3.fingerprint(t, canonical=True)["sha256"]
        self.to_lf("src/main/java/a/b/C.java")
        self.assertNotEqual(raw, v3.fingerprint(t)["sha256"], "the raw fingerprint still sees every byte")
        self.assertEqual(canonical, v3.fingerprint(t, canonical=True)["sha256"], "CRLF and LF are the same Java line terminator")
        self.put("src/main/java/a/b/C.java", b"package a.b;\npublic class C { int changed; }\n")
        self.assertNotEqual(canonical, v3.fingerprint(t, canonical=True)["sha256"], "a content change always invalidates")
        self.put("src/main/java/a/b/C.java", b"package a.b;\rpublic class C { }\r")
        self.assertNotEqual(canonical, v3.fingerprint(t, canonical=True)["sha256"], "only CRLF is folded, a lone CR is kept")

    def test_files_whose_bytes_may_matter_keep_their_raw_hash(self):
        t = self.task(inputs=["src/main/java/a/b/C.java", "src/main/resources/app.properties", "scripts/run.sh"])
        self.put("src/main/resources/app.properties", b"a=1\r\nb=2\r\n")
        self.put("scripts/run.sh", b"#!/bin/sh\r\necho ok\r\n")
        canonical = v3.fingerprint(t, canonical=True)["sha256"]
        self.to_lf("src/main/resources/app.properties")
        self.assertNotEqual(canonical, v3.fingerprint(t, canonical=True)["sha256"], "resources are read at runtime: their bytes stay strict")
        self.put("src/main/resources/app.properties", b"a=1\r\nb=2\r\n")
        self.to_lf("scripts/run.sh")
        self.assertNotEqual(canonical, v3.fingerprint(t, canonical=True)["sha256"], "shell scripts are line-ending sensitive")

    def test_a_clean_checkout_reuses_the_evidence_only_through_the_canonical_twin(self):
        t = self.task()
        rec = self.recorded(t)
        self.assertIn("fingerprintCanonical", rec)
        self.assertIn("fingerprintV2Canonical", rec)
        state = {"tasks": {"t.one": rec}}
        self.to_lf("src/main/java/a/b/C.java")
        self.to_lf("src/test/java/a/b/CTests.java")
        action, why = v3.classify(t, state, set(), False)
        self.assertEqual(action, "REUSE", why)
        self.assertIn("eol-canonical", why)
        self.assertEqual(v3.write_currency({"tasks": [t]}, state)["ids"]["java:a.b.CTests"]["scheme"], "fingerprint-v1+eol-canonical")
        self.assertEqual(v3.classify(t, state, set(), False, strict=True)[0], "RUN", "--strict-fingerprint demands identical bytes")
        self.assertEqual(v3.classify(t, state, {"t.one"}, False)[0], "RUN", "a revalidation can always be forced")
        info = rec["evidence"]["a.b.CTests"]
        stored = self.tmp / info["dir"] / info["files"][0]
        os.chmod(stored, 0o666)  # the store is read-only by design
        stored.write_text("tampered")
        v3._SHA_CACHE.clear()
        self.assertEqual(v3.classify(t, state, set(), False)[0], "RUN", "tampered evidence is never reused")

    def test_a_content_change_on_the_clean_checkout_is_not_reused(self):
        t = self.task()
        state = {"tasks": {"t.one": self.recorded(t)}}
        self.put("src/main/java/a/b/C.java", b"package a.b;\npublic class C { int x; }\n")
        action, why = v3.classify(t, state, set(), False)
        self.assertEqual(action, "RUN")
        self.assertIn("inputs changed", why)

    def attest(self, t, state, **kw):
        v3.save_state(state)
        saved = v3.load_manifest
        v3.load_manifest = lambda path=None: {"tasks": [t]}
        try:
            args = argparse.Namespace(**{"changed_file": None, "baseline_dir": None, "only": None, "apply": True, "report": None, **kw})
            v3.cmd_attest_eol(args)
        finally:
            v3.load_manifest = saved
            v3._SHA_CACHE.clear(); v3._JAVA_CACHE.clear()
        return v3.load_state()

    def test_legacy_evidence_gains_the_twin_only_when_its_recorded_bytes_reproduce_the_stored_fingerprint(self):
        t = self.task()
        rec = self.recorded(t)
        for key in ("fingerprintCanonical", "fingerprintV2Canonical"):
            rec.pop(key)  # evidence recorded before the eol-canonical scheme existed
        recorded_tree = self.tmp / "recorded"  # the working copy the evidence was recorded in (CRLF)
        for rel in ("src/main/java/a/b/C.java", "src/test/java/a/b/CTests.java"):
            (recorded_tree / rel).parent.mkdir(parents=True, exist_ok=True)
            (recorded_tree / rel).write_bytes((self.tmp / rel).read_bytes())
            self.to_lf(rel)  # the clean checkout of the same commit
        self.assertEqual(v3.classify(t, {"tasks": {"t.one": rec}}, set(), False)[0], "RUN", "legacy evidence is not reused until it is attested")
        changed = self.tmp / "changed.txt"
        changed.write_text("src/main/java/a/b/C.java\nsrc/test/java/a/b/CTests.java\n")
        state = self.attest(t, {"tasks": {"t.one": rec}}, changed_file=str(changed), baseline_dir=str(recorded_tree))
        attested = state["tasks"]["t.one"]
        self.assertEqual(attested["eolAttestation"]["baselineFingerprint"], rec["fingerprint"])
        self.assertIn("fingerprintV2Canonical", attested)
        self.assertEqual(v3.classify(t, state, set(), False)[0], "REUSE")

        drifted = self.tmp / "drifted"  # a "baseline" whose bytes are not the recorded ones is refused
        (drifted / "src/main/java/a/b").mkdir(parents=True)
        (drifted / "src/main/java/a/b/C.java").write_bytes(b"package a.b;\r\npublic class C { int drift; }\r\n")
        (drifted / "src/test/java/a/b").mkdir(parents=True)
        (drifted / "src/test/java/a/b/CTests.java").write_bytes((recorded_tree / "src/test/java/a/b/CTests.java").read_bytes())
        state = self.attest(t, {"tasks": {"t.one": dict(rec)}}, changed_file=str(changed), baseline_dir=str(drifted))
        self.assertNotIn("fingerprintCanonical", state["tasks"]["t.one"], "nothing is recorded when the stored fingerprint is not reproduced")
        self.assertEqual(v3.classify(t, state, set(), False)[0], "RUN")

    def test_closure_reused_evidence_gains_only_the_closure_twin(self):
        self.put("src/main/java/a/b/Unrelated.java", b"package a.b;\npublic class Unrelated { }\n")
        t = self.task(inputs=["src/main/java/a/b/C.java", "src/main/java/a/b/Unrelated.java"])
        rec = self.recorded(t)
        for key in ("fingerprintCanonical", "fingerprintV2Canonical"):
            rec.pop(key)
        recorded_tree = self.tmp / "recorded"
        for rel in ("src/main/java/a/b/C.java", "src/test/java/a/b/CTests.java"):
            (recorded_tree / rel).parent.mkdir(parents=True, exist_ok=True)
            (recorded_tree / rel).write_bytes((self.tmp / rel).read_bytes())
            self.to_lf(rel)
        self.put("src/main/java/a/b/Unrelated.java", b"package a.b;\npublic class Unrelated { int later; }\n")  # unreachable change committed after the run
        changed = self.tmp / "changed.txt"
        changed.write_text("src/main/java/a/b/C.java\nsrc/test/java/a/b/CTests.java\n")
        state = self.attest(t, {"tasks": {"t.one": rec}}, changed_file=str(changed), baseline_dir=str(recorded_tree))
        attested = state["tasks"]["t.one"]
        self.assertNotIn("fingerprintCanonical", attested, "the declared inputs changed, so v1 is not reproduced and gets no twin")
        self.assertIn("fingerprintV2Canonical", attested, "the reachable closure is reproduced byte for byte")
        action, why = v3.classify(t, state, set(), False)
        self.assertEqual(action, "REUSE", why)
        self.assertIn("closure-v2, eol-canonical", why)
        self.put("src/main/java/a/b/C.java", b"package a.b;\npublic class C { int reachable; }\n")
        self.assertEqual(v3.classify(t, state, set(), False)[0], "RUN", "a reachable content change still invalidates")


class IsolationTests(Sandbox):
    """The runner may only start child processes in an execution root that cannot reach the real workspace storage (root/data)."""

    def setUp(self):
        super().setUp()
        self.saved_exec = (v3.EXEC_ROOT, v3.REQUIRE_ISOLATION)
        self.real = self.tmp / "workspace"
        (self.real / "data" / "audit-log").mkdir(parents=True)
        (self.real / "data" / "audit-log" / "d.jsonl").write_text("{}")
        self.box = self.tmp / "box"
        for rel_path in ("pom.xml", "mvnw.cmd", "frontend/src/a.ts"):
            p = self.box / rel_path
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("x")
        v3.ROOT = self.real
        self.saved_env = dict(os.environ)

    def tearDown(self):
        v3.EXEC_ROOT, v3.REQUIRE_ISOLATION = self.saved_exec
        os.environ.clear()
        os.environ.update(self.saved_env)
        super().tearDown()

    def link(self, link: Path, target: Path):
        link.parent.mkdir(parents=True, exist_ok=True)
        rc = os.system(f'cmd /c mklink /J "{link}" "{target}" > NUL')
        self.assertEqual(rc, 0, "could not create the junction used by the test")

    @unittest.skipUnless(os.name == "nt", "junctions are a Windows scenario")
    def test_a_separate_copy_is_accepted(self):
        self.assertEqual(v3.isolation_problems(self.real, self.box), [])

    def test_the_real_workspace_is_never_an_isolated_root(self):
        self.assertTrue(any("real workspace" in p for p in v3.isolation_problems(self.real, self.real)))

    def test_a_root_nested_in_or_around_the_workspace_is_rejected(self):
        nested = self.real / "target" / "sandbox"
        nested.mkdir(parents=True)
        for need in ("pom.xml", "mvnw.cmd"):
            (nested / need).write_text("x")
        self.assertTrue(any("inside the real workspace" in p for p in v3.isolation_problems(self.real, nested)))
        self.assertTrue(any("contains the real workspace" in p for p in v3.isolation_problems(self.real, self.tmp)))

    def test_a_directory_that_is_not_a_copy_of_the_workspace_is_rejected(self):
        (self.box / "pom.xml").unlink()
        self.assertTrue(any("pom.xml" in p for p in v3.isolation_problems(self.real, self.box)))

    @unittest.skipUnless(os.name == "nt", "junctions are a Windows scenario")
    def test_data_linked_to_the_real_storage_is_rejected(self):
        self.link(self.box / "data", self.real / "data")
        problems = v3.isolation_problems(self.real, self.box)
        self.assertTrue(any("data/" in p for p in problems), problems)

    @unittest.skipUnless(os.name == "nt", "junctions are a Windows scenario")
    def test_any_other_link_inside_the_root_is_rejected_but_the_dependency_store_is_allowed(self):
        store = self.tmp / "node_modules_store"
        store.mkdir()
        self.link(self.box / "frontend" / "node_modules", store)
        self.assertEqual(v3.isolation_problems(self.real, self.box), [], "frontend/node_modules is the declared read-only exception")
        self.link(self.box / "src" / "sneaky", self.real / "data")
        self.assertTrue(any("sneaky" in p for p in v3.isolation_problems(self.real, self.box)))

    @unittest.skipUnless(os.name == "nt", "junctions are a Windows scenario")
    def test_the_dependency_store_must_not_point_into_the_real_data(self):
        self.link(self.box / "frontend" / "node_modules", self.real / "data")
        self.assertTrue(any("points into the real data" in p for p in v3.isolation_problems(self.real, self.box)))

    def test_inherited_environment_that_redirects_storage_is_rejected(self):
        for name in [k for k in os.environ if k.upper().startswith(("HYDRA_", "DSL_", "SPRING_")) or k in v3._JVM_OPTION_VARS]:
            del os.environ[name]
        self.assertEqual(v3.unsafe_env_problems(self.real), [])
        os.environ["HYDRA_TRANSFORMATION_SYNC_SESSION_DIRECTORY"] = str(self.real / "data" / "sessions")
        self.assertTrue(any("HYDRA_TRANSFORMATION_SYNC_SESSION_DIRECTORY" in p for p in v3.unsafe_env_problems(self.real)))
        del os.environ["HYDRA_TRANSFORMATION_SYNC_SESSION_DIRECTORY"]
        os.environ["DSL_STORAGE_METAMODELS"] = str(self.real / "data" / ".." / "data" / "metamodels")
        self.assertTrue(any("DSL_STORAGE_METAMODELS" in p for p in v3.unsafe_env_problems(self.real)))
        del os.environ["DSL_STORAGE_METAMODELS"]
        os.environ["MAVEN_OPTS"] = "-Xmx1g -Duser.dir=C:\\somewhere"
        self.assertTrue(any("user.dir" in p for p in v3.unsafe_env_problems(self.real)))
        os.environ["MAVEN_OPTS"] = "-Xmx1g"
        os.environ["HYDRA_SOMETHING"] = str(self.box / "elsewhere")
        self.assertEqual(v3.unsafe_env_problems(self.real), [])

    def test_run_refuses_to_start_anything_when_isolation_is_required_and_missing(self):
        started = []
        saved = (v3.load_manifest, v3.load_state, v3.build_plan, v3.run_maven_batch, v3.save_state)
        task = self.task()
        v3.load_manifest = lambda *a, **k: {"tasks": [task]}
        v3.load_state = lambda: {"tasks": {}, "runs": [], "quarantine": []}
        v3.build_plan = lambda *a, **k: [{"id": "t.one", "kind": "maven", "action": "RUN", "reason": "x", "priority": 5}]
        v3.run_maven_batch = lambda *a, **k: started.append(a)
        v3.save_state = lambda s: None
        v3.OUT = self.real / "target" / "archify-v3"
        try:
            args = type("A", (), {"only": None, "force": None, "include_stale": False, "retry_failed": False, "dry_run": False, "no_sync": True, "no_quarantine": False, "timeout": 5})()
            v3.EXEC_ROOT, v3.REQUIRE_ISOLATION = v3.ROOT, True
            self.assertEqual(v3.cmd_run(args), 3)
            self.assertEqual(started, [], "no Maven batch may start")
            self.assertFalse((v3.OUT / "runs").exists(), "no run directory is created for a refused run")
            os.environ["HYDRA_X"] = str(self.real / "data")
            v3.EXEC_ROOT = self.box
            self.assertEqual(v3.cmd_run(args), 3, "an inherited variable pointing into data/ also refuses")
            self.assertEqual(started, [])
        finally:
            v3.load_manifest, v3.load_state, v3.build_plan, v3.run_maven_batch, v3.save_state = saved

    def test_isolated_commands_use_the_execution_root(self):
        v3.EXEC_ROOT = self.box
        self.assertTrue(v3.maven_argv(["a.b.CTests"])[0].startswith(str(self.box)))
        report = v3.isolation_report()
        self.assertTrue(report["isolated"])
        self.assertEqual(report["effectiveData"], str(self.box / "data"))
        self.assertEqual(report["realData"], str(self.real / "data"))
        self.assertTrue(report["mavenCwd"].startswith(str(self.box)) and report["playwrightCwd"].startswith(str(self.box)))

    def test_a_task_marked_isolation_blocked_is_never_planned_to_run(self):
        task = self.task(isolation="blocked", isolationReason="writes outside its working directory")
        self.touch("src/main/java/a/b/C.java", "package a.b;\npublic class C {}\n")
        action, reason = v3.classify(task, {"tasks": {}}, {"*"}, False)
        self.assertEqual(action, "BLOCKED", "even a forced run must not start it")
        self.assertIn("writes outside its working directory", reason)
        plan = v3.build_plan({"tasks": [task]}, {"tasks": {}}, None, {"*"}, False)
        self.assertEqual([p["action"] for p in plan], ["BLOCKED"])

    def test_capture_baseline_accepts_reports_from_the_isolated_root_only(self):
        reports = self.box / "build" / "hydra-maven" / "surefire-reports"
        write_xml(reports, "a.b.CTests", [("ok", None)])
        v3.OUT = self.real / "target" / "archify-v3"
        v3.EVID, v3.STORE, v3.INDEX = v3.OUT / "evidence", v3.OUT / "evidence" / "surefire", v3.OUT / "evidence" / "evidence-index.json"
        args = type("A", (), {"src": str(reports), "run_id": "run-iso", "started": "2026-10-23T00:00:00.000Z"})()
        v3.EXEC_ROOT = self.real
        with self.assertRaises(SystemExit):
            v3.cmd_capture_baseline(args)
        v3.EXEC_ROOT = self.box
        self.assertEqual(v3.cmd_capture_baseline(args), 0)
        self.assertTrue((v3.STORE / "run-iso").is_dir())
        outside = self.tmp / "elsewhere" / "surefire-reports"
        write_xml(outside, "a.b.DTests", [("ok", None)])
        with self.assertRaises(SystemExit):
            v3.cmd_capture_baseline(type("A", (), {"src": str(outside), "run_id": "run-x", "started": None})())


if __name__ == "__main__":
    unittest.main()
