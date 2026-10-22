#!/usr/bin/env python3
"""HYDRA Archify V3 - incremental, resumable, non-exclusive certification runner ("megascript V3").

Why V3 exists (verified against the repository, see docs/architecture/archify/knowledge/tests/v3/AUDITORIA_CONTRASTE.md):
  * pom.xml binds maven-clean-plugin:clean to the `initialize` phase, so EVERY Maven invocation deletes build/hydra-maven/surefire-reports.
    V2 therefore ran the whole suite (`mvnw.cmd -B test`, no -Dtest) once per task (35 times). V3 runs ONE focused Maven invocation per
    batch (all pending selectors together) and copies the Surefire XML into an immutable evidence store *immediately*.
  * Archify's execution state must not depend on the volatile build directory: discover-tests.js reads this store
    (target/archify-v3/evidence/evidence-index.json) and keeps, per test class, the newest evidence together with the time its run started.
  * Reuse is fingerprint based (test + production inputs + pom.xml + .mvn config + toolchain), never "PASS because it passed once".

Properties: reusable (manifest driven), resumable (atomic state file, fingerprints), non-exclusive (a failing/compile-broken task never
blocks the others; V3-owned tests with compile errors are quarantined for the rerun and restored), real tests only (nothing is promoted by
this script: fragments are merged only for tasks whose real execution passed, and STRONG claims are mechanically audited),
independent logs per task, automatic Archify sync through the official pipeline with an idempotence check.

Only the Python standard library is used. Never runs git commit/push, network tools or anything outside the allowlist.
Usage: python scripts/archify/v3/hydra_archify_v3.py <command> [options]   (see --help)"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
KN = ROOT / "docs" / "architecture" / "archify" / "knowledge"
KT = KN / "tests"
V3DOC = KT / "v3"
OUT = ROOT / "target" / "archify-v3"
EVID = OUT / "evidence"
STORE = EVID / "surefire"
INDEX = EVID / "evidence-index.json"
STATE = OUT / "state.json"
LOCK = OUT / ".lock"
MANIFEST = V3DOC / "MANIFEST_CERTIFICACION_V3.json"
EXTRA_INPUTS = ["pom.xml", ".mvn/maven.config", ".mvn/wrapper/maven-wrapper.properties", "mvnw.cmd"]
ALLOWED_EXE = {"python", "node", "npm", "npx", "mvnw.cmd", "mvnw", "pwsh", "powershell"}
FORBIDDEN_ARGS = {"push", "publish", "-DskipTests", "-Dmaven.test.skip=true", "--update-snapshot", "-u", "deploy", "install"}
KINDS = {"maven", "vitest", "playwright", "node", "python"}
STATUSES = {"ACTIVE", "PLANNED", "BLOCKED"}
SIX_FILES = [KT / "test-catalog.json", KT / "test-execution-state.json", KT / "test-evidence-matrix.json", KT / "test-gaps.json",
             ROOT / "docs/architecture/archify/hydra-tests-data.js", KN / "capabilities-cache.json"]
MAVEN_CMD_LIMIT = 6000  # Windows command line limit is 8191 characters


# ------------------------------------------------------------------ utilities
def now_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def iso_from_ts(ts: float) -> str:
    return dt.datetime.fromtimestamp(ts, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


_SHA_CACHE: dict = {}
_GLOB_CACHE: dict = {}


def sha256_file(p: Path, fresh: bool = False) -> str:
    """SHA-256 of a file; memoised per process by (path, mtime, size) so fingerprinting hundreds of tasks that share inputs stays cheap."""
    key = str(p)  # inputs are not modified while a runner process is alive (Maven/Playwright never write sources)
    if not fresh and key in _SHA_CACHE:
        return _SHA_CACHE[key]
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    digest = h.hexdigest()
    _SHA_CACHE[key] = digest
    return digest


def write_json_atomic(p: Path, obj) -> None:
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(p.suffix + ".tmp")
    tmp.write_text(json.dumps(obj, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    os.replace(tmp, p)


def read_json(p: Path, default=None):
    try:
        return json.loads(p.read_text(encoding="utf-8-sig"))
    except FileNotFoundError:
        return default


def set_readonly(p: Path) -> None:
    try:
        os.chmod(p, stat.S_IREAD)
    except OSError:
        pass


def rel(p: Path) -> str:
    try:
        return p.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return str(p)


def inside_repo(p: Path) -> bool:
    try:
        p.resolve().relative_to(ROOT)
        return True
    except ValueError:
        return False


def log(msg: str) -> None:
    print(f"[v3 {dt.datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


# ------------------------------------------------------------------ manifest
def load_manifest(path: Path | None = None) -> dict:
    m = json.loads((path or MANIFEST).read_text(encoding="utf-8-sig"))
    problems = validate_manifest(m)
    if problems:
        raise SystemExit("manifest invalid:\n  " + "\n  ".join(problems))
    return m


def validate_manifest(m: dict) -> list[str]:
    errs: list[str] = []
    if m.get("schemaVersion") != 3:
        errs.append("schemaVersion must be 3")
    seen = set()
    for t in m.get("tasks", []):
        tid = t.get("id", "?")
        if not re.fullmatch(r"[a-z0-9][a-z0-9_.-]{2,80}", tid or ""):
            errs.append(f"{tid}: bad id")
        if tid in seen:
            errs.append(f"{tid}: duplicate id")
        seen.add(tid)
        if t.get("kind") not in KINDS:
            errs.append(f"{tid}: kind must be one of {sorted(KINDS)}")
        if t.get("status", "ACTIVE") not in STATUSES:
            errs.append(f"{tid}: bad status")
        if t.get("status") == "BLOCKED" and not t.get("blockedReason"):
            errs.append(f"{tid}: BLOCKED requires blockedReason")
        if t.get("kind") == "maven":
            if not t.get("selectors"):
                errs.append(f"{tid}: maven task needs selectors")
            for s in t.get("selectors", []):
                if not re.fullmatch(r"[A-Za-z_][\w.]*(#[A-Za-z_]\w*)?", s):
                    errs.append(f"{tid}: bad selector {s!r} (use FQN or FQN#method)")
        elif t.get("kind") == "playwright":
            if not t.get("specs"):
                errs.append(f"{tid}: playwright task needs specs (paths relative to frontend/)")
            for s in t.get("specs", []):
                if Path(s).is_absolute() or ".." in Path(s).parts or not s.endswith((".spec.ts", ".spec.js")):
                    errs.append(f"{tid}: bad spec {s!r}")
        else:
            argv = t.get("argv") or []
            if not argv:
                errs.append(f"{tid}: {t.get('kind')} task needs argv")
            else:
                errs += [f"{tid}: {e}" for e in check_argv(argv)]
        for i in t.get("inputs", []):
            if Path(i).is_absolute() or ".." in Path(i).parts:
                errs.append(f"{tid}: input outside repo: {i}")
        if t.get("status", "ACTIVE") == "ACTIVE" and not t.get("scope") and not t.get("importedFromV2") and not t.get("regressionOnly"):
            errs.append(f"{tid}: ACTIVE task needs scope [{{processId,stageId}}]")
    return errs


def check_argv(argv: list[str]) -> list[str]:
    errs = []
    exe = Path(argv[0]).name.lower()
    if exe.replace(".exe", "") not in ALLOWED_EXE:
        errs.append(f"executable not allowed: {argv[0]}")
    for a in argv[1:]:
        if a in FORBIDDEN_ARGS or a.startswith("-DskipTests"):
            errs.append(f"forbidden argument: {a}")
    return errs


def fqn_to_path(fqn: str) -> str:
    return "src/test/java/" + fqn.split("#")[0].replace(".", "/") + ".java"


def selector_class(sel: str) -> str:
    return sel.split("#")[0]


def surefire_pattern(sel: str) -> str:
    cls, _, meth = sel.partition("#")
    return cls.replace(".", "/") + (("#" + meth) if meth else "")


# ------------------------------------------------------------------ fingerprint
def task_files(task: dict) -> list[str]:
    files = list(task.get("inputs", [])) + EXTRA_INPUTS
    if task.get("kind") == "maven":
        files += [fqn_to_path(selector_class(s)) for s in task.get("selectors", [])]
    if task.get("kind") == "vitest":
        files += ["frontend/package-lock.json", "frontend/vitest.config.ts"]
    if task.get("kind") == "playwright":
        files += ["frontend/package-lock.json", "frontend/" + task.get("config", "playwright.real.config.ts"), "frontend/e2e/real-backend.mjs", "frontend/e2e/real-web.mjs"]
        files += ["frontend/" + s for s in task.get("specs", [])]
    for pattern in task.get("inputGlobs", []):
        if pattern not in _GLOB_CACHE:
            _GLOB_CACHE[pattern] = [rel(p) for p in sorted(ROOT.glob(pattern)) if p.is_file()]
        files += _GLOB_CACHE[pattern]
    out, seen = [], set()
    for f in files:
        if f not in seen:
            seen.add(f)
            out.append(f)
    return sorted(out)


def fingerprint(task: dict) -> dict:
    h = hashlib.sha256()
    missing, count = [], 0
    for f in task_files(task):
        p = ROOT / f
        if not p.is_file():
            if f in EXTRA_INPUTS and f != "pom.xml":
                continue  # optional global inputs
            missing.append(f)
            h.update(f"MISSING:{f}\n".encode())
            continue
        h.update(f"{f}:{sha256_file(p)}\n".encode())
        count += 1
    for name in sorted(task.get("envFingerprint", [])):
        h.update(f"ENV:{name}={hashlib.sha256(os.environ.get(name, '').encode()).hexdigest()}\n".encode())
    h.update(f"JAVA_HOME={os.environ.get('JAVA_HOME', '')}\nkind={task.get('kind')}\n".encode())
    return {"sha256": h.hexdigest(), "files": count, "missing": missing}


# ------------------------------------------------------------------ state
def load_state() -> dict:
    s = read_json(STATE, None)
    if s is None:
        s = {"schemaVersion": 3, "createdAt": now_iso(), "tasks": {}, "runs": [], "merged": {}, "quarantine": []}
    return s


def save_state(s: dict) -> None:
    s["updatedAt"] = now_iso()
    if STATE.exists():
        shutil.copy2(STATE, STATE.with_suffix(".json.bak"))
    write_json_atomic(STATE, s)


# ------------------------------------------------------------------ surefire parsing / evidence store
def parse_surefire(xml_path: Path) -> dict:
    root = ET.parse(xml_path).getroot()
    name = root.get("name", "")
    cases = []
    for tc in root.iter("testcase"):
        status, msg = "PASS", None
        for child in tc:
            if child.tag in ("failure", "error"):
                status, msg = "FAIL", ((child.get("message") or "")[:300])
            elif child.tag == "skipped" and status != "FAIL":
                status = "SKIPPED"
        cases.append({"n": tc.get("name", ""), "s": status, "t": float(tc.get("time") or 0), **({"m": msg} if msg else {})})
    so = root.find("system-out")
    return {"suite": name, "outer": name.split("$")[0], "cases": cases, "timestamp": root.get("timestamp"),
            "stdout": (so.text or "")[:20000] if so is not None else ""}


def group_by_outer(xml_files: list[Path]) -> dict:
    g: dict[str, list[Path]] = {}
    for f in xml_files:
        outer = re.sub(r"^TEST-|\.xml$", "", f.name).split("$")[0]
        g.setdefault(outer, []).append(f)
    return g


def load_index() -> dict:
    return read_json(INDEX, {"schemaVersion": 1, "classes": {}})


def capture_reports(src_dir: Path, run_id: str, started_at: str, finished_override: dict | None = None, only: set | None = None) -> dict:
    """Copy Surefire XML into the immutable store (one directory per run) and update the evidence index. Returns {outer: info}."""
    xmls = sorted(src_dir.glob("TEST-*.xml"))
    dest = STORE / run_id
    dest.mkdir(parents=True, exist_ok=True)
    index = load_index()
    captured = {}
    for outer, files in group_by_outer(xmls).items():
        if only is not None and outer not in only:
            continue
        names, hashes, newest = [], {}, 0.0
        for f in files:
            target = dest / f.name
            if not target.exists():
                shutil.copy2(f, target)
                set_readonly(target)
            names.append(f.name)
            hashes[f.name] = sha256_file(target)
            newest = max(newest, f.stat().st_mtime)
        fin = (finished_override or {}).get(outer) or iso_from_ts(newest)
        info = {"dir": rel(dest), "files": names, "sha256": hashes, "runId": run_id, "startedAt": started_at, "finishedAt": fin}
        prev = index["classes"].get(outer)
        if prev is None or Date_ms(fin) >= Date_ms(prev.get("finishedAt", "1970-01-01T00:00:00Z")):
            index["classes"][outer] = info
        captured[outer] = info
    index["updatedAt"] = now_iso()
    write_json_atomic(INDEX, index)
    return captured


def Date_ms(iso: str) -> float:
    return dt.datetime.fromisoformat(iso.replace("Z", "+00:00")).timestamp() * 1000


def class_result(outer: str, run_dir: Path, wanted_methods: set | None = None) -> dict:
    files = sorted(run_dir.glob(f"TEST-{outer}.xml")) + sorted(run_dir.glob(f"TEST-{outer}$*.xml"))
    if not files:
        return {"found": False}
    cases = []
    for f in files:
        cases += parse_surefire(f)["cases"]
    if wanted_methods:
        cases = [c for c in cases if c["n"].split("(")[0] in wanted_methods]
    p = sum(1 for c in cases if c["s"] == "PASS")
    fl = sum(1 for c in cases if c["s"] == "FAIL")
    sk = sum(1 for c in cases if c["s"] == "SKIPPED")
    return {"found": True, "cases": len(cases), "pass": p, "fail": fl, "skipped": sk, "problems": [c for c in cases if c["s"] != "PASS"][:10]}


def selector_verdict(sel: str, run_dir: Path) -> dict:
    cls, _, meth = sel.partition("#")
    r = class_result(cls, run_dir, {meth} if meth else None)
    if not r["found"] or r["cases"] == 0:
        return {"selector": sel, "verdict": "NOT_EXECUTED", **r}
    if r["fail"]:
        return {"selector": sel, "verdict": "FAIL", **r}
    if r["pass"] == 0:
        return {"selector": sel, "verdict": "SKIPPED", **r}
    return {"selector": sel, "verdict": "PASS", **r}


def task_verdict(per_sel: list[dict]) -> str:
    vs = {x["verdict"] for x in per_sel}
    if "FAIL" in vs:
        return "FAIL"
    if "NOT_EXECUTED" in vs:
        return "NOT_EXECUTED"
    if vs == {"SKIPPED"}:
        return "SKIPPED"
    return "PASS"


# ------------------------------------------------------------------ process control
def other_maven_running() -> list[str]:
    try:
        if os.name == "nt":
            out = subprocess.run(["powershell", "-NoProfile", "-Command",
                                  "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'mvnw|maven-wrapper|surefire|plexus-classworlds' -and $_.CommandLine -notmatch 'Get-CimInstance' } | ForEach-Object { $_.ProcessId }"],
                                 capture_output=True, text=True, timeout=60).stdout
            return [x for x in out.split() if x.isdigit() and int(x) != os.getpid()]
        out = subprocess.run(["pgrep", "-f", "mvnw|surefire"], capture_output=True, text=True).stdout
        return [x for x in out.split() if x.isdigit() and int(x) != os.getpid()]
    except Exception:  # noqa: BLE001
        return []


class Lock:
    def __enter__(self):
        OUT.mkdir(parents=True, exist_ok=True)
        if LOCK.exists():
            try:
                pid = int(LOCK.read_text().split()[0])
                if pid_alive(pid):
                    raise SystemExit(f"another V3 run is active (pid {pid}); remove {rel(LOCK)} only if it is stale")
            except (ValueError, IndexError):
                pass
        LOCK.write_text(f"{os.getpid()} {now_iso()}\n")
        return self

    def __exit__(self, *a):
        try:
            LOCK.unlink()
        except FileNotFoundError:
            pass


def pid_alive(pid: int) -> bool:
    if os.name == "nt":
        out = subprocess.run(["tasklist", "/FI", f"PID eq {pid}"], capture_output=True, text=True).stdout
        return str(pid) in out
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def run_logged(argv: list[str], cwd: Path, log_path: Path, timeout: int, env: dict | None = None) -> tuple[int, float]:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    t0 = time.time()
    e = dict(os.environ)
    e.update(env or {})
    with open(log_path, "w", encoding="utf-8", errors="replace") as lf:
        lf.write("$ " + " ".join(argv) + f"\n# cwd={rel(cwd)} started={now_iso()}\n")
        lf.flush()
        proc = subprocess.Popen(argv, cwd=str(cwd), stdout=lf, stderr=subprocess.STDOUT, env=e, shell=False)
        try:
            rc = proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            if os.name == "nt":
                subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"], capture_output=True)
            else:
                proc.kill()
            rc = 124
            lf.write(f"\n# TIMEOUT after {timeout}s\n")
    return rc, time.time() - t0


# ------------------------------------------------------------------ planning
def classify(task: dict, state: dict, force: set, retry_failed: bool) -> tuple[str, str]:
    """-> (action, reason): RUN | REUSE | BLOCKED | PLANNED | SKIP."""
    st = task.get("status", "ACTIVE")
    if st == "BLOCKED":
        return "BLOCKED", task.get("blockedReason", "")
    if st == "PLANNED":
        return "PLANNED", "not implemented yet"
    miss = missing_requirements(task)
    if miss:
        return "BLOCKED", "missing prerequisite: " + ", ".join(miss)
    fp = fingerprint(task)
    if fp["missing"]:
        return "BLOCKED", "input files missing: " + ", ".join(fp["missing"][:3])
    rec = state["tasks"].get(task["id"], {})
    if task["id"] in force or "*" in force:
        return "RUN", "forced"
    if rec.get("verdict") == "PASS" and rec.get("fingerprint") == fp["sha256"] and evidence_intact(rec):
        return "REUSE", "same fingerprint, immutable evidence intact"
    if rec.get("verdict") in ("FAIL", "COMPILE_ERROR", "NOT_EXECUTED") and rec.get("fingerprint") == fp["sha256"] and not retry_failed:
        return "SKIP", f"previous {rec['verdict']} with unchanged inputs (use --retry-failed)"
    if rec.get("fingerprint") and rec.get("fingerprint") != fp["sha256"] and rec.get("verdict"):
        return "RUN", "inputs changed since " + rec.get("finishedAt", "?")
    return "RUN", "no previous evidence"


def missing_requirements(task: dict) -> list[str]:
    miss = []
    req = task.get("requires", {})
    for tool in req.get("tools", []):
        if shutil.which(tool) is None:
            miss.append("tool:" + tool)
    for env in req.get("env", []):
        if not os.environ.get(env):
            miss.append("env:" + env)
    return miss


def evidence_intact(rec: dict) -> bool:
    for cls, info in (rec.get("evidence") or {}).items():
        for name, h in (info.get("sha256") or {}).items():
            p = ROOT / info["dir"] / name
            if not p.is_file() or sha256_file(p, fresh=True) != h:
                return False
    return bool(rec.get("evidence")) or rec.get("kind") != "maven"


def stale_task_ids(m: dict) -> set:
    """Tasks whose test class/spec/file Archify currently reports as STALE_EVIDENCE (production changed after the evidence started)."""
    ex = (read_json(KT / "test-execution-state.json", {"tests": {}}) or {}).get("tests", {})
    out = set()
    for t in m["tasks"]:
        ids = []
        if t.get("kind") == "maven":
            ids = ["java:" + selector_class(s) for s in t.get("selectors", [])]
        elif t.get("kind") == "playwright":
            ids = ["pw:e2e/" + s.split("e2e/", 1)[-1] for s in t.get("specs", [])]
        elif t.get("kind") == "vitest":
            ids = ["vitest:" + a for a in t.get("argv", []) if a.endswith((".test.ts", ".test.tsx"))]
        if any(ex.get(i, {}).get("s") == "STALE_EVIDENCE" for i in ids):
            out.add(t["id"])
    return out


def build_plan(m: dict, state: dict, only: set | None, force: set, retry_failed: bool) -> list[dict]:
    plan = []
    for t in sorted(m["tasks"], key=lambda x: (x.get("priority", 5), x["id"])):
        if only and t["id"] not in only:
            continue
        action, reason = classify(t, state, force, retry_failed)
        plan.append({"id": t["id"], "kind": t["kind"], "action": action, "reason": reason, "priority": t.get("priority", 5)})
    return plan


# ------------------------------------------------------------------ maven execution
def maven_batches(tasks: list[dict]) -> list[list[dict]]:
    """Group maven tasks into as few invocations as the Windows command-line limit allows (normally exactly one)."""
    batches, cur, size = [], [], 0
    for t in tasks:
        s = sum(len(surefire_pattern(x)) + 1 for x in t["selectors"])
        if cur and size + s > MAVEN_CMD_LIMIT:
            batches.append(cur)
            cur, size = [], 0
        cur.append(t)
        size += s
    if cur:
        batches.append(cur)
    return batches


def maven_argv(selectors: list[str], extra: list[str] | None = None) -> list[str]:
    pats = sorted({surefire_pattern(s) for s in selectors})
    exe = str(ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw"))
    return [exe, "-B", "test", "-Dtest=" + ",".join(pats), "-Dsurefire.failIfNoSpecifiedTests=false", "-DfailIfNoTests=false"] + (extra or [])


COMPILE_ERR = re.compile(r"\[ERROR\]\s+(?:/|[A-Za-z]:)?[^\s]*?[/\\]src[/\\](test|main)[/\\]java[/\\]([^\s:]+?\.java):\[(\d+),(\d+)\]\s*(.*)")


def parse_compile_errors(console: str) -> list[dict]:
    errs = []
    for m in COMPILE_ERR.finditer(console):
        errs.append({"scope": m.group(1), "file": f"src/{m.group(1)}/java/" + m.group(2).replace("\\", "/"), "line": int(m.group(3)), "msg": m.group(5)[:200]})
    return errs


def task_log_excerpt(console: str, cls_names: list[str]) -> str:
    lines = console.splitlines()
    out = []
    for i, ln in enumerate(lines):
        if any(c in ln for c in cls_names):
            out += lines[max(0, i - 1): i + 6]
    return "\n".join(dict.fromkeys(out))[:60000]


def run_maven_batch(tasks: list[dict], run_id: str, run_dir: Path, timeout: int, allow_quarantine: bool, state: dict, quarantined_out: list, all_tasks: list | None = None) -> dict:
    selectors = [s for t in tasks for s in t["selectors"]]
    argv = maven_argv(selectors)
    (run_dir / "logs").mkdir(parents=True, exist_ok=True)
    console_path = run_dir / "logs" / f"maven-batch-{len(list((run_dir / 'logs').glob('maven-batch-*.log'))) + 1}.log"
    if other_maven_running():
        raise SystemExit("another Maven/Surefire process is running (race on the shared build directory); wait for it to finish")
    started = now_iso()
    log(f"maven batch: {len(tasks)} tasks, {len(selectors)} selectors -> {rel(console_path)}")
    rc, secs = run_logged(argv, ROOT, console_path, timeout)
    console = console_path.read_text(encoding="utf-8", errors="replace")
    comp = parse_compile_errors(console) if "COMPILATION ERROR" in console else []
    result = {"argv": argv, "rc": rc, "seconds": round(secs, 1), "console": rel(console_path), "startedAt": started, "compileErrors": comp, "quarantined": []}
    if comp and allow_quarantine:
        owned = {}
        for t in (all_tasks or tasks):  # a file already broken in an earlier batch of the same run must stay quarantinable in the next ones
            if t.get("quarantinable", True) and t.get("kind") == "maven":
                for sel in t["selectors"]:
                    owned[fqn_to_path(selector_class(sel))] = t["id"]
        bad_files = {e["file"] for e in comp}
        foreign = [f for f in bad_files if f not in owned]
        if foreign:
            result["abort"] = "compile errors in files not owned by V3 tasks (never touched): " + ", ".join(sorted(foreign)[:5])
        else:
            qdir = OUT / "quarantine" / run_id
            qdir.mkdir(parents=True, exist_ok=True)
            moved = []
            try:
                for f in sorted(bad_files):
                    src, dst = ROOT / f, qdir / f.replace("/", "__")
                    shutil.copy2(src, dst)
                    moved.append({"file": f, "task": owned[f], "copy": rel(dst)})
                state["quarantine"].append({"runId": run_id, "files": moved, "restored": False})
                save_state(state)
                for mv in moved:
                    os.remove(ROOT / mv["file"])
                quarantined_out.extend(moved)
                remaining = [t for t in tasks if t["id"] not in {mv["task"] for mv in moved}]
                if remaining:
                    sel2 = [s for t in remaining for s in t["selectors"]]
                    argv2 = maven_argv(sel2)
                    cp2 = run_dir / "logs" / "maven-batch-retry-without-quarantined.log"
                    log(f"retrying {len(remaining)} tasks without {len(moved)} quarantined test file(s)")
                    rc, secs2 = run_logged(argv2, ROOT, cp2, timeout)
                    result.update({"retryArgv": argv2, "retryRc": rc, "retrySeconds": round(secs2, 1), "retryConsole": rel(cp2)})
                    console += "\n" + cp2.read_text(encoding="utf-8", errors="replace")
            finally:
                for mv in moved:
                    dst = ROOT / mv["file"]
                    if not dst.exists():
                        shutil.copy2(ROOT / mv["copy"], dst)
                for q in state["quarantine"]:
                    if q["runId"] == run_id:
                        q["restored"] = True
                save_state(state)
            result["quarantined"] = moved
    reports = ROOT / "build" / "hydra-maven" / "surefire-reports"
    captured = capture_reports(reports, run_id, started)  # immutable copies + evidence index, immediately after Maven finished
    result["xmlCaptured"] = sum(len(v["files"]) for v in captured.values())
    result["captured"] = captured
    result["console_text"] = console
    return result


# ------------------------------------------------------------------ Playwright against the REAL backend
E2E_DIR = lambda: OUT / "e2e"  # noqa: E731
E2E_WEB_PORT, E2E_API_PORT = 4183, 18081


def port_free(port: int) -> bool:
    import socket
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", port)) != 0


def kill_e2e_leftovers() -> None:
    """Stops only the backend/web processes this runner's Playwright config launched (identified by their argfile / script names)."""
    if os.name != "nt":
        return
    ps = ("Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'archify-v3.e2e.java.argfile' -or $_.CommandLine -match 'e2e.real-web.mjs' -or $_.CommandLine -match 'e2e.real-backend.mjs' } "
          "| ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }")
    subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True)


def npm_exe(name: str) -> str:
    return shutil.which(name + ".cmd") or shutil.which(name) or name


def e2e_prepare(steps: list[str], run_dir: Path, timeout: int) -> str | None:
    """-> None when ready, else the reason. Never touches product sources."""
    E2E_DIR().mkdir(parents=True, exist_ok=True)
    logs = run_dir / "logs"
    if "maven-classpath" in steps:
        if other_maven_running():
            return "another Maven/Surefire process is running; wait for it to finish"
        cp = E2E_DIR() / "classpath.txt"
        argv = [str(ROOT / "mvnw.cmd"), "-B", "-q", "compile", "dependency:build-classpath", f"-Dmdep.outputFile={cp}"]
        rc, secs = run_logged(argv, ROOT, logs / "e2e-prepare-maven.log", timeout)
        if rc or not cp.is_file():
            return f"maven compile/dependency:build-classpath failed (rc={rc}); see {rel(logs / 'e2e-prepare-maven.log')}"
    if "frontend-dist" in steps:
        dist = ROOT / "frontend" / "dist" / "index.html"
        src_newest = max((p.stat().st_mtime for p in (ROOT / "frontend" / "src").rglob("*") if p.is_file()), default=0)
        if not dist.is_file() or dist.stat().st_mtime < src_newest:
            rc, _ = run_logged([npm_exe("npm"), "run", "build"], ROOT / "frontend", logs / "e2e-prepare-frontend.log", timeout)
            if rc or not dist.is_file():
                return f"frontend build failed (rc={rc}); see {rel(logs / 'e2e-prepare-frontend.log')}"
    return None


def _walk_pw_suites(suites: list, out: list, inherited_file: str = "") -> None:
    for s in suites:
        f = s.get("file") or inherited_file
        for spec in s.get("specs", []):
            status = "PASS"
            msg = None
            dur = 0.0
            for test in spec.get("tests", []):
                st = test.get("status")
                for r in test.get("results", []):
                    dur += (r.get("duration") or 0) / 1000.0
                    if r.get("error"):
                        msg = (r["error"].get("message") or "")[:300]
                if st in ("unexpected", "flaky"):
                    status = "FAIL"
                elif st == "skipped" and status != "FAIL":
                    status = "SKIPPED"
            out.append({"file": (spec.get("file") or f).replace("\\", "/"), "n": spec.get("title", ""), "s": status, "t": round(dur, 3), **({"m": msg} if msg else {})})
        _walk_pw_suites(s.get("suites", []), out, f)


def parse_playwright_json(p: Path) -> list[dict]:
    js = json.loads(p.read_text(encoding="utf-8-sig"))
    out: list[dict] = []
    _walk_pw_suites(js.get("suites", []), out)
    return out


def _spec_match(parsed_file: str, spec: str) -> bool:
    a, b = parsed_file.replace("\\", "/").lstrip("./"), spec.replace("\\", "/")
    return a == b or b.endswith("/" + a) or a.endswith("/" + b)


def run_playwright_batch(tasks: list[dict], run_id: str, run_dir: Path, timeout: int, state: dict) -> dict:
    """One Playwright invocation (one real backend start) for all the given tasks; per-task verdicts from the JSON report."""
    cfg = tasks[0].get("config", "playwright.real.config.ts")
    specs = [s for t in tasks for s in t["specs"]]
    steps = sorted({p for t in tasks for p in t.get("prepare", ["maven-classpath", "frontend-dist"])})
    logs = run_dir / "logs"
    logs.mkdir(parents=True, exist_ok=True)
    results: dict[str, dict] = {}
    started = now_iso()
    why = e2e_prepare(steps, run_dir, timeout)
    if not why:
        busy = [p for p in (E2E_WEB_PORT, E2E_API_PORT) if not port_free(p)]
        if busy:
            why = f"ports already in use: {busy} (another backend/web server is running)"
    if why:
        for t in tasks:
            results[t["id"]] = {"kind": "playwright", "verdict": "NOT_EXECUTED", "reason": why, "fingerprint": fingerprint(t)["sha256"], "runId": run_id, "finishedAt": now_iso(), "startedAt": started,
                                "attempts": state["tasks"].get(t["id"], {}).get("attempts", 0) + 1}
        return results
    jsonp = run_dir / "playwright.json"
    argv = [npm_exe("npx"), "playwright", "test", "-c", cfg, *specs]
    env = {"PLAYWRIGHT_JSON_OUTPUT_NAME": str(jsonp), "HYDRA_E2E_WEB_PORT": str(E2E_WEB_PORT), "HYDRA_E2E_API_PORT": str(E2E_API_PORT), "HYDRA_E2E_OUTPUT_DIR": str(run_dir / "pw-results"), "CI": "1"}
    log(f"playwright batch: {len(tasks)} tasks, {len(specs)} specs -> {rel(logs / 'playwright-batch.log')}")
    rc, secs = run_logged(argv, ROOT / "frontend", logs / "playwright-batch.log", timeout, env)
    kill_e2e_leftovers()
    finished = now_iso()
    cases = parse_playwright_json(jsonp) if jsonp.is_file() else []
    dest = EVID / "playwright" / run_id
    info_by_spec: dict[str, dict] = {}
    if jsonp.is_file():
        dest.mkdir(parents=True, exist_ok=True)
        target = dest / "playwright.json"
        if not target.exists():
            shutil.copy2(jsonp, target)
            set_readonly(target)
        h = sha256_file(target)
        index = load_index()
        index.setdefault("suites", {})
        for t in tasks:
            for s in t["specs"]:
                mine = [c for c in cases if _spec_match(c["file"], s)]
                key = "pw:e2e/" + s.split("e2e/", 1)[-1] if "e2e/" in s else "pw:" + s
                info = {"dir": rel(dest), "files": ["playwright.json"], "sha256": {"playwright.json": h}, "runId": run_id, "startedAt": started, "finishedAt": finished, "cases": mine}
                info_by_spec[s] = info
                if mine:
                    index["suites"][key] = info
        index["updatedAt"] = now_iso()
        write_json_atomic(INDEX, index)
    for t in tasks:
        per, verdicts = [], []
        for s in t["specs"]:
            mine = info_by_spec.get(s, {}).get("cases", [])
            fl = sum(1 for c in mine if c["s"] == "FAIL")
            ps = sum(1 for c in mine if c["s"] == "PASS")
            sk = sum(1 for c in mine if c["s"] == "SKIPPED")
            v = "NOT_EXECUTED" if not mine else "FAIL" if fl else "SKIPPED" if ps == 0 else "PASS"
            verdicts.append(v)
            per.append({"selector": s, "verdict": v, "found": bool(mine), "cases": len(mine), "pass": ps, "fail": fl, "skipped": sk, "problems": [c for c in mine if c["s"] != "PASS"][:10]})
        verdict = task_verdict(per)
        tlog = logs / f"{t['id']}.log"
        lines = [f"# task {t['id']}  verdict={verdict}", f"# oracle: {t.get('oracle', '')}", f"# scope: {json.dumps(t.get('scope', []))}", f"# command: {' '.join(argv)}", f"# batch seconds={round(secs, 1)} rc={rc} console={rel(logs / 'playwright-batch.log')}", ""]
        for p in per:
            lines.append(f"spec {p['selector']}: {p['verdict']} cases={p['cases']} pass={p['pass']} fail={p['fail']} skipped={p['skipped']}")
            lines += [f"   - {x['s']} {x['n']} {x.get('m', '')}" for x in p["problems"]]
        tlog.write_text("\n".join(lines) + "\n", encoding="utf-8")
        ev = {("pw:e2e/" + s.split("e2e/", 1)[-1] if "e2e/" in s else "pw:" + s): info_by_spec[s] for s in t["specs"] if s in info_by_spec and info_by_spec[s].get("cases")}
        results[t["id"]] = {"kind": "playwright", "verdict": verdict, "fingerprint": fingerprint(t)["sha256"], "runId": run_id, "finishedAt": finished, "startedAt": started, "selectors": per,
                            "evidence": {k: {kk: vv for kk, vv in v.items() if kk != "cases"} for k, v in ev.items()}, "log": rel(tlog), "reason": "",
                            "attempts": state["tasks"].get(t["id"], {}).get("attempts", 0) + 1}
    return results


# ------------------------------------------------------------------ run
def cmd_run(args) -> int:
    m = load_manifest()
    state = load_state()
    only = set(args.only.split(",")) if args.only else None
    force = set(args.force.split(",")) if args.force else set()
    if getattr(args, "include_stale", False):
        force |= stale_task_ids(m)
    plan = build_plan(m, state, only, force, args.retry_failed)
    summary = {k: sum(1 for p in plan if p["action"] == k) for k in ("RUN", "REUSE", "BLOCKED", "PLANNED", "SKIP")}
    log("plan: " + json.dumps(summary))
    if args.dry_run:
        for p in plan:
            print(f"  {p['action']:8} {p['id']:55} {p['reason']}")
        return 0
    tasks_by_id = {t["id"]: t for t in m["tasks"]}
    to_run = [tasks_by_id[p["id"]] for p in plan if p["action"] == "RUN"]
    for p in plan:  # blocked / planned tasks are recorded, never silently dropped
        if p["action"] in ("BLOCKED", "PLANNED"):
            rec = state["tasks"].setdefault(p["id"], {})
            rec.update({"kind": tasks_by_id[p["id"]]["kind"], "verdict": p["action"], "reason": p["reason"], "checkedAt": now_iso()})
    if not to_run:
        log("nothing to run: all certified tasks are reused (same fingerprint)")
        save_state(state)
        return cmd_sync(args) if not args.no_sync else 0
    run_id = dt.datetime.now(dt.timezone.utc).strftime("run-%Y%m%dT%H%M%SZ")
    run_dir = OUT / "runs" / run_id
    (run_dir / "logs").mkdir(parents=True, exist_ok=True)
    with Lock():
        state["runs"].append({"runId": run_id, "startedAt": now_iso(), "tasks": [t["id"] for t in to_run]})
        save_state(state)
        results: dict[str, dict] = {}
        maven_tasks = [t for t in to_run if t["kind"] == "maven"]
        for batch in maven_batches(maven_tasks):
            qf: list = []
            res = run_maven_batch(batch, run_id, run_dir, args.timeout, not args.no_quarantine, state, qf, m["tasks"])
            run_dir_surefire = STORE / run_id
            console = res.pop("console_text")
            started = res["startedAt"]
            captured = res["captured"]
            for t in batch:
                clsnames = sorted({selector_class(s) for s in t["selectors"]})
                per = [selector_verdict(s, run_dir_surefire) for s in t["selectors"]]
                verdict = task_verdict(per)
                qmatch = [q for q in res["quarantined"] if q["task"] == t["id"]]
                if qmatch:
                    verdict = "COMPILE_ERROR"
                elif res.get("abort") and all(p["verdict"] == "NOT_EXECUTED" for p in per):
                    verdict = "COMPILE_ERROR"
                tlog = run_dir / "logs" / f"{t['id']}.log"
                tlog.write_text(render_task_log(t, res, per, verdict, console, clsnames, qmatch), encoding="utf-8")
                fp = fingerprint(t)
                ev = {c: captured[c] for c in clsnames if c in captured}
                results[t["id"]] = {"kind": "maven", "verdict": verdict, "fingerprint": fp["sha256"], "runId": run_id, "finishedAt": now_iso(), "startedAt": started,
                                    "selectors": per, "evidence": ev, "log": rel(tlog), "reason": "; ".join(e["msg"] for e in res["compileErrors"][:2]) if verdict == "COMPILE_ERROR" else "",
                                    "attempts": state["tasks"].get(t["id"], {}).get("attempts", 0) + 1}
                log(f"  {verdict:14} {t['id']}")
        pw_tasks = [t for t in to_run if t["kind"] == "playwright"]
        for cfg in sorted({t.get("config", "playwright.real.config.ts") for t in pw_tasks}):
            batch = [t for t in pw_tasks if t.get("config", "playwright.real.config.ts") == cfg]
            res_pw = run_playwright_batch(batch, run_id, run_dir, args.timeout, state)
            for tid, r in res_pw.items():
                results[tid] = r
                log(f"  {r['verdict']:14} {tid}")
        for t in [x for x in to_run if x["kind"] not in ("maven", "playwright")]:
            results[t["id"]] = run_command_task(t, run_id, run_dir, args.timeout, state)
            log(f"  {results[t['id']]['verdict']:14} {t['id']}")
        for tid, r in results.items():
            state["tasks"][tid] = r
        state["runs"][-1].update({"finishedAt": now_iso(), "verdicts": {k: v["verdict"] for k, v in results.items()}})
        save_state(state)
    tally = {}
    for r in results.values():
        tally[r["verdict"]] = tally.get(r["verdict"], 0) + 1
    log("run finished: " + json.dumps(tally))
    rc = 0 if all(v["verdict"] in ("PASS",) for v in results.values()) else 1
    if not args.no_sync:
        cmd_sync(args)
    return rc


def render_task_log(t: dict, res: dict, per: list, verdict: str, console: str, clsnames: list, qmatch: list) -> str:
    lines = [f"# task {t['id']}  verdict={verdict}", f"# oracle: {t.get('oracle', '')}", f"# scope: {json.dumps(t.get('scope', []))}",
             f"# command: {' '.join(res.get('retryArgv') or res['argv'])}", f"# batch seconds={res['seconds']} rc={res['rc']} console={res['console']}", ""]
    for p in per:
        lines.append(f"selector {p['selector']}: {p['verdict']} cases={p.get('cases')} pass={p.get('pass')} fail={p.get('fail')} skipped={p.get('skipped')}")
        for pr in p.get("problems", []):
            lines.append(f"   - {pr['s']} {pr['n']} {pr.get('m', '')}")
    if qmatch:
        lines += ["", "# QUARANTINED for the rerun because of compile errors in its test source:"] + [f"   {q['file']} (copy {q['copy']})" for q in qmatch]
        lines += [f"   {e['file']}:{e['line']} {e['msg']}" for e in res["compileErrors"] if e["file"] in {q['file'] for q in qmatch}]
    lines += ["", "# --- console excerpt (lines mentioning the task classes) ---", task_log_excerpt(console, clsnames)]
    return "\n".join(lines) + "\n"


def capture_vitest(js: dict, src: Path, run_id: str) -> dict:
    """Per-file Vitest evidence into the immutable store (same shape as the Playwright suites) so Archify can read it."""
    dest = EVID / "vitest" / run_id
    dest.mkdir(parents=True, exist_ok=True)
    target = dest / "vitest.json"
    if not target.exists():
        shutil.copy2(src, target)
        set_readonly(target)
    h = sha256_file(target, fresh=True)
    index = load_index()
    index.setdefault("suites", {})
    started = iso_from_ts(js["startTime"] / 1000) if js.get("startTime") else now_iso()
    finished = now_iso()
    out = {}
    for f in js.get("testResults", []):
        name = str(f.get("name", "")).replace("\\", "/")
        if "/frontend/" not in name:
            continue
        relname = name.split("/frontend/", 1)[1]
        cases = []
        for a in f.get("assertionResults", []):
            st = {"passed": "PASS", "failed": "FAIL"}.get(a.get("status"), "SKIPPED")
            cases.append({"n": a.get("fullName") or a.get("title", ""), "s": st, "t": round((a.get("duration") or 0) / 1000.0, 3), **({"m": (a.get("failureMessages") or [""])[0][:300]} if st == "FAIL" else {})})
        info = {"dir": rel(dest), "files": ["vitest.json"], "sha256": {"vitest.json": h}, "runId": run_id, "startedAt": started, "finishedAt": finished, "cases": cases}
        if cases:
            index["suites"]["vitest:" + relname] = info
            out["vitest:" + relname] = {k: v for k, v in info.items() if k != "cases"}
    index["updatedAt"] = now_iso()
    write_json_atomic(INDEX, index)
    return out


def run_command_task(t: dict, run_id: str, run_dir: Path, timeout: int, state: dict) -> dict:
    cwd = ROOT / t.get("cwd", ".")
    if not inside_repo(cwd):
        return {"kind": t["kind"], "verdict": "BLOCKED", "reason": "cwd outside repo", "runId": run_id, "finishedAt": now_iso()}
    argv = [a.replace("{RUN_DIR}", str(run_dir)) for a in t["argv"]]
    resolved = shutil.which(argv[0]) or shutil.which(argv[0] + ".cmd")
    if resolved:
        argv[0] = resolved  # Windows: npx/npm/mvnw are .cmd shims that CreateProcess cannot find by bare name
    logp = run_dir / "logs" / f"{t['id']}.log"
    rc, secs = run_logged(argv, cwd, logp, t.get("timeoutSeconds", timeout), t.get("env"))
    verdict = "PASS" if rc == 0 else "FAIL"
    vitest_evidence = {}
    if t["kind"] == "vitest":
        js = read_json(run_dir / "vitest.json", None)
        if js is not None:
            verdict = "PASS" if js.get("numFailedTests", 1) == 0 and js.get("numPassedTests", 0) > 0 else "FAIL"
            vitest_evidence = capture_vitest(js, run_dir / "vitest.json", run_id)
    return {"kind": t["kind"], "verdict": verdict, "fingerprint": fingerprint(t)["sha256"], "runId": run_id, "finishedAt": now_iso(),
            "seconds": round(secs, 1), "rc": rc, "log": rel(logp), "archifyIntegration": bool(vitest_evidence), **({"evidence": vitest_evidence} if vitest_evidence else {}),
            "attempts": state["tasks"].get(t["id"], {}).get("attempts", 0) + 1}


# ------------------------------------------------------------------ strong audit (mechanical invariants only)
def audit_strong() -> dict:
    cat = {t["id"]: t for t in read_json(KT / "test-catalog.json")["tests"]}
    ex = read_json(KT / "test-execution-state.json")["tests"]
    mx = read_json(KT / "test-evidence-matrix.json")
    issues = {"strongRecordNotExecutedPass": [], "strongUnit": [], "strongStatic": [], "strongMockedExternal": [], "strongWithoutWhy": [], "strongMissingFile": [], "strongStageOnlyStale": [], "strongRecordNoCases": []}
    for tid, t in cat.items():
        if t.get("strength") != "STRONG":
            continue
        s = ex.get(tid, {}).get("s")
        if s not in ("PASS", "STALE_EVIDENCE"):
            issues["strongRecordNotExecutedPass"].append(f"{tid}:{s}")
        if t.get("type") == "UNIT":
            issues["strongUnit"].append(tid)
        if t.get("type") in ("STATIC_VERIFICATION", "SOURCE_INTEGRITY", "ARCHITECTURE"):
            issues["strongStatic"].append(tid)
        if t.get("external") == "mocked":
            issues["strongMockedExternal"].append(tid)
        if not t.get("strengthWhy"):
            issues["strongWithoutWhy"].append(tid)
        if t.get("path") and not (ROOT / t["path"]).exists():
            issues["strongMissingFile"].append(tid)
        if t.get("kind") == "suite" and t.get("framework") == "junit5" and not (ex.get(tid, {}).get("cases") or {}).get("n"):
            issues["strongRecordNoCases"].append(tid)
    strong_stages = 0
    for pid, P in mx["procs"].items():
        for sid, x in P["stages"].items():
            if x.get("best") != "STRONG":
                continue
            strong_stages += 1
            ds = [d for d in x.get("d", []) if cat.get(d, {}).get("strength") == "STRONG"]
            if ds and not any(ex.get(d, {}).get("s") == "PASS" for d in ds):
                issues["strongStageOnlyStale"].append(f"{pid}.{sid}")
    return {"strongStages": strong_stages, "strongRecords": sum(1 for t in cat.values() if t.get("strength") == "STRONG"), "issues": {k: v for k, v in issues.items()}, "issueCounts": {k: len(v) for k, v in issues.items()}}


def fragment_gate(frag: list, task: dict, verdict: str) -> list[str]:
    """Objective checks before a fragment may be merged. STRONG is never granted by the runner; it is only *allowed through* when the
    fragment's own claim is consistent with the real execution result and with the strength rubric."""
    errs = []
    if verdict != "PASS":
        errs.append(f"task verdict is {verdict}, fragments are withheld")
        return errs
    for r in frag:
        if r.get("strength") == "STRONG":
            if r.get("type") in ("UNIT", "STATIC_VERIFICATION", "SOURCE_INTEGRITY", "ARCHITECTURE", "UI"):
                errs.append(f"{r.get('id')}: STRONG not allowed for type {r.get('type')}")
            if r.get("external") == "mocked":
                errs.append(f"{r.get('id')}: STRONG with mocked external systems")
            if not r.get("strengthWhy"):
                errs.append(f"{r.get('id')}: STRONG needs strengthWhy")
            if not any(p.get("match") == "direct" and p.get("stages") for p in r.get("procs", [])):
                errs.append(f"{r.get('id')}: STRONG needs at least one direct stage mapping")
    return errs


# ------------------------------------------------------------------ sync with the official Archify pipeline
def norm_generated(text: str) -> str:
    return re.sub(r'"generatedAt":\s*"[^"]*"', '"generatedAt":"NORM"', text)


def snapshot_six() -> dict:
    return {rel(p): norm_generated(p.read_text(encoding="utf-8-sig")) if p.exists() else None for p in SIX_FILES}


def node(*a: str) -> subprocess.CompletedProcess:
    return subprocess.run(["node", *a], cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")


def cmd_sync(args) -> int:
    state = load_state()
    if getattr(args, "restage", False):
        state["merged"] = {}  # re-merge every fragment of every PASS task (idempotent; refreshes source hashes)
    m = load_manifest()
    pending = KT / ".fragments_pending"
    pending.mkdir(exist_ok=True)
    tasks = {t["id"]: t for t in m["tasks"]}
    staged, withheld = [], []
    for tid, rec in state["tasks"].items():
        t = tasks.get(tid)
        if not t or not t.get("fragment"):
            continue
        fpath = V3DOC / t["fragment"]
        if not fpath.is_file():
            withheld.append((tid, "fragment file missing"))
            continue
        h = sha256_file(fpath)
        if state["merged"].get(tid, {}).get("sha256") == h and rec.get("verdict") == "PASS":
            continue
        frag = json.loads(fpath.read_text(encoding="utf-8-sig"))
        errs = fragment_gate(frag, t, rec.get("verdict", "NOT_RUN"))
        if errs:
            withheld.append((tid, "; ".join(errs[:2])))
            continue
        shutil.copy2(fpath, pending / f"v3-{tid}.json")
        staged.append((tid, h))
    for tid, why in withheld:
        log(f"fragment withheld for {tid}: {why}")
    log(f"sync: {len(staged)} fragment(s) staged, {len(withheld)} withheld")
    disc = KT / "discovery-current.json"
    r = node(str(KT / "discover-tests.js"), "--out", str(disc))
    if r.returncode:
        log("discover-tests failed:\n" + r.stderr[-800:])
        return 2
    for tid, _ in staged:
        r = node(str(KT / "validate-fragment.js"), str(pending / f"v3-{tid}.json"), "--discovery", str(disc))
        if r.returncode:
            log(f"fragment validation failed for {tid}:\n" + r.stdout[-1200:])
            (pending / f"v3-{tid}.json").unlink(missing_ok=True)
            withheld.append((tid, "validate-fragment failed"))
            staged = [x for x in staged if x[0] != tid]
    before = snapshot_six()
    r = node(str(KT / "build-test-evidence.js"), "--discovery", str(disc), "--fragments", str(pending), "--check")
    log("build --check: " + (r.stdout.strip().splitlines() or ["?"])[-1])
    if r.returncode:
        log(r.stdout[-1500:])
        shutil.rmtree(pending, ignore_errors=True)
        pending.mkdir(exist_ok=True)
        return 3
    r = node(str(KT / "build-test-evidence.js"), "--discovery", str(disc), "--fragments", str(pending))
    if r.returncode:
        log("build failed:\n" + r.stderr[-1000:])
        return 3
    after_first = snapshot_six()
    shutil.rmtree(pending, ignore_errors=True)
    pending.mkdir(exist_ok=True)
    r = node(str(KT / "build-test-evidence.js"), "--discovery", str(disc))
    after_second = snapshot_six()
    idem = {k: (after_first[k] == after_second[k]) for k in after_first}
    pc = node(str(KN / "portal-check.js"))
    last = [ln for ln in pc.stdout.splitlines() if "error(s)" in ln]
    log("portal-check: " + (last[-1] if last else "?"))
    log("idempotence (re-merge with nothing pending): " + ("IDENTICAL" if all(idem.values()) else "DIFFERENT " + json.dumps(idem)))
    for tid, h in staged:
        state["merged"][tid] = {"sha256": h, "mergedAt": now_iso()}
    state["lastSync"] = {"at": now_iso(), "staged": [t for t, _ in staged], "withheld": withheld, "idempotent": all(idem.values()), "portalCheck": last[-1] if last else None,
                         "changed": [k for k in before if before[k] != after_first[k]]}
    save_state(state)
    return 0 if (pc.returncode == 0 and all(idem.values())) else 4


# ------------------------------------------------------------------ other commands
def cmd_capture_baseline(args) -> int:
    src = Path(args.src) if args.src else ROOT / "build" / "hydra-maven" / "surefire-reports"
    if not inside_repo(src):
        raise SystemExit("source must be inside the repository")
    xmls = sorted(src.glob("TEST-*.xml"))
    if not xmls:
        raise SystemExit(f"no TEST-*.xml in {rel(src)}")
    run_id = args.run_id or "baseline-" + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    started = args.started or iso_from_ts(min(f.stat().st_mtime for f in xmls) - 60)
    cap = capture_reports(src, run_id, started)
    log(f"captured {len(xmls)} XML files / {len(cap)} classes into {rel(STORE / run_id)} (started {started})")
    return 0


def cmd_adopt(args) -> int:
    """Adopt already captured evidence (e.g. the V2 baseline run) for tasks whose inputs were NOT modified after that run started:
    nothing is executed; the verdict is recomputed from the immutable XML, and the task keeps its fingerprint for later reuse checks."""
    m = load_manifest()
    state = load_state()
    idx = load_index()
    run_dir = STORE / args.run_id
    if not run_dir.is_dir():
        raise SystemExit(f"unknown evidence run {args.run_id}")
    infos = [v for v in idx["classes"].values() if v["runId"] == args.run_id]
    if not infos:
        raise SystemExit("run has no classes in the evidence index")
    started = min(Date_ms(v["startedAt"]) for v in infos) / 1000
    adopted, skipped = [], []
    for t in m["tasks"]:
        if t["kind"] != "maven" or t.get("status", "ACTIVE") != "ACTIVE" or t["id"] in state["tasks"] and state["tasks"][t["id"]].get("verdict") == "PASS":
            continue
        if not (t.get("importedFromV2") or t.get("adoptBaseline")):
            continue
        newer = [f for f in task_files(t) if (ROOT / f).is_file() and (ROOT / f).stat().st_mtime > started]
        per = [selector_verdict(s, run_dir) for s in t["selectors"]]
        if newer:
            skipped.append((t["id"], "input modified after the baseline run: " + newer[0]))
            continue
        verdict = task_verdict(per)
        ev = {selector_class(s): idx["classes"][selector_class(s)] for s in t["selectors"] if selector_class(s) in idx["classes"] and idx["classes"][selector_class(s)]["runId"] == args.run_id}
        state["tasks"][t["id"]] = {"kind": "maven", "verdict": verdict, "fingerprint": fingerprint(t)["sha256"], "runId": args.run_id, "finishedAt": now_iso(), "adopted": True,
                                   "selectors": per, "evidence": ev, "reason": "adopted from immutable run " + args.run_id + " (no re-execution)", "attempts": 0}
        adopted.append((t["id"], verdict))
    save_state(state)
    log(f"adopted {len(adopted)} task(s): " + json.dumps({v: sum(1 for _, x in adopted if x == v) for v in {x for _, x in adopted}}))
    for tid, why in skipped:
        log(f"not adopted {tid}: {why}")
    return 0


def cmd_status(args) -> int:
    m = load_manifest()
    state = load_state()
    plan = build_plan(m, state, None, set(), False)
    by = {}
    for p in plan:
        by.setdefault(p["action"], []).append(p["id"])
    idx = load_index()
    print(f"manifest tasks: {len(plan)}  evidence classes: {len(idx['classes'])}  runs: {len(state['runs'])}")
    for k, v in by.items():
        print(f"  {k:8} {len(v):3}  {', '.join(v[:6])}{' ...' if len(v) > 6 else ''}")
    verdicts = {}
    for r in state["tasks"].values():
        verdicts[r.get("verdict")] = verdicts.get(r.get("verdict"), 0) + 1
    print("recorded verdicts:", json.dumps(verdicts))
    q = [x for x in state.get("quarantine", []) if not x.get("restored")]
    if q:
        print("WARNING: unrestored quarantine entries:", json.dumps(q)[:300])
    return 0


def cmd_plan(args) -> int:
    args.dry_run = True
    return cmd_run(args)


def cmd_restore_quarantine(args) -> int:
    state = load_state()
    n = 0
    for q in state.get("quarantine", []):
        for mv in q["files"]:
            dst = ROOT / mv["file"]
            if not dst.exists():
                shutil.copy2(ROOT / mv["copy"], dst)
                n += 1
        q["restored"] = True
    save_state(state)
    log(f"restored {n} file(s)")
    return 0


def cmd_audit(args) -> int:
    a = audit_strong()
    OUT.mkdir(parents=True, exist_ok=True)
    write_json_atomic(OUT / "audit-strong.json", a)
    print(json.dumps({"strongStages": a["strongStages"], "strongRecords": a["strongRecords"], "issueCounts": a["issueCounts"]}, indent=1))
    return 0


# ------------------------------------------------------------------ evidence / quarantine audit (read-only)
NON_PASS = ("FAIL", "COMPILE_ERROR", "NOT_EXECUTED", "SKIPPED", "BLOCKED", "PLANNED")


def audit_evidence() -> dict:
    """Read-only audit: quarantine hygiene, PASS <-> manifest <-> immutable Surefire XML correspondence, no coverage from non-PASS tasks."""
    m = load_manifest()
    state = load_state()
    idx = load_index()["classes"]
    cat = {t["id"]: t for t in read_json(KT / "test-catalog.json")["tests"]}
    ex = read_json(KT / "test-execution-state.json")["tests"]
    problems: list[str] = []
    warnings: list[str] = []
    tasks = m["tasks"]
    v2 = [t for t in tasks if t.get("importedFromV2")]
    v3 = [t for t in tasks if t.get("generatedFromFragment")]
    verdicts: dict[str, int] = {}
    rows = []
    for t in tasks:
        rec = state["tasks"].get(t["id"], {})
        v = rec.get("verdict", "NOT_RUN")
        verdicts[v] = verdicts.get(v, 0) + 1
        row = {"task": t["id"], "origin": "v2" if t.get("importedFromV2") else "v3", "verdict": v, "classes": []}
        for sel in (t.get("selectors", []) if t.get("kind") == "maven" else []):
            outer = selector_class(sel)
            info = (rec.get("evidence") or {}).get(outer)
            cls = {"class": outer}
            if v == "PASS":
                if not info:
                    problems.append(f"{t['id']}: PASS without evidence for {outer}")
                else:
                    cases = 0
                    for name, h in info["sha256"].items():
                        p = ROOT / info["dir"] / name
                        if not p.is_file():
                            problems.append(f"{t['id']}: XML missing {rel(p)}")
                        elif sha256_file(p, fresh=True) != h:
                            problems.append(f"{t['id']}: XML hash mismatch {rel(p)}")
                        else:
                            res = parse_surefire(p)
                            cases += len(res["cases"])
                            bad = [c for c in res["cases"] if c["s"] == "FAIL"]
                            if bad:
                                problems.append(f"{t['id']}: PASS task has failing case in {name}")
                            skipped = [c["n"] for c in res["cases"] if c["s"] == "SKIPPED"]
                            if skipped:
                                cls.setdefault("skippedCases", []).extend(skipped)
                                warnings.append(f"{t['id']}: {len(skipped)} skipped case(s), not counted as coverage: {', '.join(skipped)}")
                    cls.update({"xmlFiles": len(info["files"]), "cases": cases, "runId": info["runId"]})
                    newest = idx.get(outer)
                    if not newest or newest["runId"] != info["runId"]:
                        # A newer run re-executed the same class (shared by another task): fine when that newer evidence is intact and all-PASS; a problem otherwise.
                        newer_ok = bool(newest)
                        for nname, nh in ((newest or {}).get("sha256") or {}).items():
                            np_ = ROOT / newest["dir"] / nname
                            if not np_.is_file() or sha256_file(np_, fresh=True) != nh or any(c["s"] == "FAIL" for c in parse_surefire(np_)["cases"]):
                                newer_ok = False
                        if newer_ok:
                            warnings.append(f"{t['id']}: evidence of {outer} superseded by a newer all-PASS run ({newest['runId']}); verdict keeps its own intact evidence ({info['runId']})")
                        else:
                            problems.append(f"{t['id']}: index points to a different run ({newest and newest['runId']}) than the verdict ({info['runId']}) and the newer evidence is not intact/all-PASS")
                    srcf = ROOT / fqn_to_path(outer)
                    if srcf.exists() and Date_ms(info["startedAt"]) < srcf.stat().st_mtime * 1000 - 1000:
                        problems.append(f"{t['id']}: test source modified after evidence started (stale)")
                    crec, st = cat.get(f"java:{outer}"), ex.get(f"java:{outer}", {}).get("s")
                    cls["catalogStatus"] = st
                    if st != "PASS":
                        problems.append(f"{t['id']}: Archify execution state for {outer} is {st}, expected PASS")
                    if crec and crec.get("caseCount") not in (None, cases):
                        cls["caseCountMismatch"] = [crec.get("caseCount"), cases]
            else:
                crec = cat.get(f"java:{outer}")
                st = ex.get(f"java:{outer}", {}).get("s")
                cls["catalogStatus"] = st
                if crec and crec.get("strength") == "STRONG" and not t.get("importedFromV2"):
                    problems.append(f"{t['id']}: non-PASS task has a STRONG catalog record for {outer}")
            row["classes"].append(cls)
        rows.append(row)
    pending_quarantine = [q for q in state.get("quarantine", []) if not q.get("restored")]
    for q in state.get("quarantine", []):
        for mv in q["files"]:
            if not (ROOT / mv["file"]).exists():
                problems.append(f"quarantined file not present in the tree: {mv['file']}")
            if not (ROOT / mv["copy"]).exists():
                problems.append(f"quarantine copy missing: {mv['copy']}")
    if pending_quarantine:
        problems.append(f"{len(pending_quarantine)} quarantine entries not restored")
    compile_history = []
    for r in state["runs"]:
        for tid, v in (r.get("verdicts") or {}).items():
            if v in NON_PASS:
                compile_history.append({"run": r["runId"], "task": tid, "verdict": v, "finalVerdict": state["tasks"].get(tid, {}).get("verdict")})
    merged_not_pass = [tid for tid in state.get("merged", {}) if state["tasks"].get(tid, {}).get("verdict") != "PASS"]
    if merged_not_pass:
        problems.append("fragments merged for non-PASS tasks: " + ", ".join(merged_not_pass))
    return {"tasks": len(tasks), "v2Imported": len(v2), "v3Generated": len(v3), "verdicts": verdicts, "problems": problems, "warnings": warnings, "ok": not problems,
            "quarantineEntries": len(state.get("quarantine", [])), "unrestoredQuarantine": len(pending_quarantine),
            "nonPassHistory": compile_history, "rows": rows}


def cmd_audit_evidence(args) -> int:
    a = audit_evidence()
    OUT.mkdir(parents=True, exist_ok=True)
    write_json_atomic(OUT / "audit-evidence.json", a)
    print(json.dumps({k: a[k] for k in ("tasks", "v2Imported", "v3Generated", "verdicts", "ok", "quarantineEntries", "unrestoredQuarantine")}, indent=1))
    print(f"non-PASS history entries: {len(a['nonPassHistory'])}  problems: {len(a['problems'])}")
    for p in a["problems"][:30]:
        print("  PROBLEM", p)
    for w in a["warnings"][:30]:
        print("  WARNING", w)
    return 0 if a["ok"] else 1


def cmd_selftest(args) -> int:
    return subprocess.call([sys.executable, "-m", "unittest", "discover", "-s", str(Path(__file__).parent / "tests"), "-v"], cwd=ROOT)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="HYDRA Archify V3 certification runner")
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("run", "plan"):
        p = sub.add_parser(name)
        p.add_argument("--only"); p.add_argument("--force"); p.add_argument("--retry-failed", action="store_true"); p.add_argument("--dry-run", action="store_true")
        p.add_argument("--no-sync", action="store_true", default=False); p.add_argument("--no-quarantine", action="store_true"); p.add_argument("--timeout", type=int, default=3600)
        p.add_argument("--include-stale", action="store_true", help="also re-run tasks whose test Archify reports as STALE_EVIDENCE")
    ad = sub.add_parser("adopt"); ad.add_argument("--run-id", required=True)
    sy = sub.add_parser("sync"); sy.add_argument("--restage", action="store_true"); sub.add_parser("status"); sub.add_parser("audit-strong"); sub.add_parser("audit-evidence"); sub.add_parser("selftest"); sub.add_parser("restore-quarantine")
    cb = sub.add_parser("capture-baseline"); cb.add_argument("--src"); cb.add_argument("--run-id"); cb.add_argument("--started")
    ap.add_argument("--sandbox", action="store_true", help="isolate state/evidence/runs under target/archify-v3-sandbox (for self-tests; sync is not allowed)")
    ap.add_argument("--manifest", help="alternative manifest (default: knowledge/tests/v3/MANIFEST_CERTIFICACION_V3.json)")
    a = ap.parse_args(argv)
    global MANIFEST, OUT, EVID, STORE, INDEX, STATE, LOCK
    if a.manifest:
        MANIFEST = Path(a.manifest).resolve()
    if a.sandbox:
        OUT = ROOT / "target" / "archify-v3-sandbox"
        EVID, STATE, LOCK = OUT / "evidence", OUT / "state.json", OUT / ".lock"
        STORE, INDEX = EVID / "surefire", EVID / "evidence-index.json"
        if a.cmd == "sync":
            raise SystemExit("sync is not allowed in sandbox mode")
    fn = {"run": cmd_run, "plan": cmd_plan, "sync": cmd_sync, "status": cmd_status, "audit-strong": cmd_audit, "audit-evidence": cmd_audit_evidence, "selftest": cmd_selftest,
          "capture-baseline": cmd_capture_baseline, "restore-quarantine": cmd_restore_quarantine, "adopt": cmd_adopt}[a.cmd]
    return fn(a)


if __name__ == "__main__":
    sys.exit(main())
