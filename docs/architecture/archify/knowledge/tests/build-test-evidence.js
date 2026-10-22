#!/usr/bin/env node
/* Builds the HYDRA test-evidence knowledge store (deterministic; no LLM, no test execution).
   Usage: node build-test-evidence.js [--discovery <file>] [--fragments <dir>[,<dir>...]] [--check]
   Reads   : discovery (discover-tests.js: sources + latest local execution evidence) + semantic fragments (authored from the test sources)
             + the existing test-catalog.json (incremental: records whose source hash is unchanged keep their semantic fields).
   Writes  : knowledge/tests/test-catalog.json            normalized stable records (semantics + canonical ids; NO execution status)
             knowledge/tests/test-execution-state.json    latest reliably known state per test (PASS/FAIL/SKIPPED/NOT_EXECUTED/STALE_EVIDENCE/DISCOVERED)
             knowledge/tests/test-evidence-matrix.json    LAYER -> CAPABILITY -> PROCESS -> STAGE -> tests, aggregated (derived)
             knowledge/tests/test-gaps.json                missing / weak / stale / ambiguous evidence (derived)
             hydra-tests-data.js                          the same data for the single-file portal (window.HYDRA_TESTS)
             capabilities-cache.json                      adds compact testEvidence / processStageEvidence per capability (no duplication of test text)
   Status rules (never PASS merely because a test exists):
     PASS = a real recorded execution succeeded;  FAIL = a real recorded execution failed;  SKIPPED = every case was skipped by the run;
     NOT_EXECUTED = test exists but is outside the latest recorded execution;  DISCOVERED = no reliable execution evidence is associated;
     STALE_EVIDENCE = it passed, but the test or the production files it exercises changed after that execution started. */
"use strict";
const fs = require("fs"), path = require("path"), cp = require("child_process");
const ROOT = path.resolve(__dirname, "..", "..", "..", "..", "..");
const { HM, dir: ARCH } = require("./portal-model-loader.js")();
const validate = require("./validate-fragment.js");
const argv = process.argv.slice(2), opt = (k) => (argv.includes(k) ? argv[argv.indexOf(k) + 1] : null);
const readJson = (p) => JSON.parse(fs.readFileSync(p, "utf8"));
const writeJson = (p, o, pretty) => fs.writeFileSync(p, JSON.stringify(o, null, pretty ? 1 : 0) + "\n", "utf8");
const T = (t) => (t && (t.es || t)) || "";
const git = (a) => { try { return cp.execFileSync("git", a, { cwd: ROOT, encoding: "utf8", maxBuffer: 256 * 1024 * 1024 }).trim(); } catch (e) { return ""; } };

// ------------------------------------------------------------------ inputs
let discoveryFile = opt("--discovery");
if (!discoveryFile) { discoveryFile = path.join(require("os").tmpdir(), "hydra-discovery-" + process.pid + ".json"); cp.execFileSync(process.execPath, [path.join(__dirname, "discover-tests.js"), "--out", discoveryFile], { stdio: "ignore" }); }
const disc = readJson(discoveryFile);
const catalogPath = path.join(__dirname, "test-catalog.json"), prev = new Map(); if (fs.existsSync(catalogPath)) readJson(catalogPath).tests.forEach((t) => prev.set(t.id, t));
const ufr = new Map(); (opt("--userfrags") || "").split(",").filter(Boolean).forEach((d) => { for (const f of fs.readdirSync(d).filter((n) => n.endsWith(".json"))) readJson(path.join(d, f)).forEach((r) => ufr.set(r.id, r)); });
const frags = new Map(); (opt("--fragments") || "").split(",").filter(Boolean).forEach((d) => { for (const f of fs.readdirSync(d).filter((n) => n.endsWith(".json"))) readJson(path.join(d, f)).forEach((r) => frags.set(r.id, r)); });

// ------------------------------------------------------------------ change times (git history + dirty working tree) for staleness
const changed = {}; // path -> ms of last change after 2026-10-01
git(["log", "--since=2026-10-01", "--name-only", "--pretty=format:@%ct"]).split("\n").reduce((ct, l) => { if (l.startsWith("@")) return +l.slice(1) * 1000; if (l && !changed[l]) changed[l] = ct; return ct; }, 0);
git(["status", "--porcelain", "-uall"]).split("\n").filter(Boolean).forEach((l) => { const f = l.slice(3).replace(/^"|"$/g, "").split(" -> ").pop(); try { changed[f] = Math.max(changed[f] || 0, fs.statSync(path.join(ROOT, f)).mtimeMs); } catch (e) {} });
const fileChanged = (f) => changed[f] || 0;
const rootsChangedSince = (ms, roots) => { let n = 0, newest = 0; for (const [f, t] of Object.entries(changed)) if (t > ms && roots.some((r) => f.startsWith(r))) { n++; newest = Math.max(newest, t); } return { n, newest }; };

// ------------------------------------------------------------------ gate timing (steps run sequentially: start = report time - sum(seconds from the step to the end))
const gatesByKind = {}; disc.gates.forEach((g) => { const k = g.id.split(":")[1]; (gatesByKind[k] = gatesByKind[k] || []).push(g); });
Object.values(gatesByKind).forEach((arr) => { let rem = 0; for (let i = arr.length - 1; i >= 0; i--) { rem += +arr[i].seconds || 0; arr[i].endMs = Date.parse(arr[i].evidenceTime) - (arr.slice(i + 1).reduce((a, g) => a + (+g.seconds || 0), 0)) * 1000; arr[i].startMs = arr[i].endMs - (+arr[i].seconds || 0) * 1000; } });
const gateByName = (kind, re) => (gatesByKind[kind] || []).find((g) => re.test(g.name));
const gVitest = gateByName("readiness", /Vitest full suite/), gPw = gateByName("readiness", /Playwright/), gMaven = gateByName("readiness", /Maven full verify/);

// ------------------------------------------------------------------ records = deterministic facts + semantic fields
const gateScripts = new Set(disc.gates.map((g) => g.script).filter(Boolean));
prev.forEach((r, id) => { if (id.startsWith("family:") && !frags.has(id)) frags.set(id, r); }); // family records persist across incremental builds
const familyMembers = new Set(); frags.forEach((f) => (f.members || []).forEach((m) => familyMembers.add(m)));
const SEM = ["name", "type", "typeNote", "layer", "layers2", "caps", "procs", "prod", "pre", "setup", "executes", "asserts", "behavior", "rule", "scenario", "result", "expected", "guarantee", "limits", "strength", "strengthWhy", "external", "cases", "related", "src", "ambiguous", "confidence", "members", "checks", "realExecuted", "notExecuted", "user"];
const techBySrc = {}; Object.keys(HM.tech.caps).forEach((c) => HM.tech.caps[c].comps.forEach((x) => (x.src || []).forEach((s) => { (techBySrc[s.path] = techBySrc[s.path] || []).push(c + ":" + x.id); })));
const records = [], pending = [];
function compose(base) {
  const f = frags.get(base.id), old = prev.get(base.id), rec = { ...base };
  if (f) SEM.forEach((k) => { if (f[k] !== undefined) rec[k] = f[k]; });
  else if (old && old.sourceHash === base.sourceHash && old.behavior) SEM.forEach((k) => { if (old[k] !== undefined) rec[k] = old[k]; });
  else { rec.analysis = "PENDING"; pending.push(base.id); }
  if (ufr.has(base.id)) rec.user = ufr.get(base.id).user;
  if (rec.prod) rec.prodComponents = [...new Set(rec.prod.flatMap((p) => techBySrc[p] || []))];
  records.push(rec);
}
disc.suites.forEach((s) => {
  if (s.kind === "script" && (gateScripts.has(s.path) || familyMembers.has(s.id))) return; // folded into a gate / family record
  const { executed, traits, prodNewest, prodRefs, cases, ...base } = s; base.caseCount = cases; base.traits = traits; base.prodRefs = prodRefs; compose(base);
});
disc.gates.forEach((g) => compose({ id: g.id, kind: "gate", framework: g.framework, path: g.script || g.source, source: g.source, gateName: g.name, facet: g.facet, required: g.required, script: g.script || null, command: g.command, sourceHash: g.script && fs.existsSync(path.join(ROOT, g.script)) ? require("crypto").createHash("sha256").update(fs.readFileSync(path.join(ROOT, g.script), "utf8")).digest("hex").slice(0, 16) : "gate:" + g.name }));
frags.forEach((f, id) => { if (id.startsWith("family:")) compose({ id, kind: "family", framework: "script-family", path: (f.members && f.members[0]) || id, sourceHash: (f.members || []).join("|").length + ":" + (f.members || []).length, caseCount: (f.members || []).length }); });

// ------------------------------------------------------------------ execution state
const dsuite = new Map(disc.suites.map((s) => [s.id, s])), dgate = new Map(disc.gates.map((g) => [g.id, g]));
const exec = {}, iso = (ms) => new Date(ms).toISOString();
const ctxJava = (() => { try { const x = fs.readFileSync(path.join(ROOT, disc.evidence.surefire.dir, fs.readdirSync(path.join(ROOT, disc.evidence.surefire.dir)).find((n) => /^TEST-.*\.xml$/.test(n))), "utf8"); const p = (k) => (x.match(new RegExp('name="' + k + '" value="([^"]*)"')) || [])[1]; return { os: p("os.name"), osVersion: p("os.version"), java: p("java.version"), javaVendor: p("java.vendor"), surefire: (x.match(/ version="([^"]+)"/) || [])[1] }; } catch (e) { return {}; } })();
const changedAfter = (files, ms) => files.filter((f) => fileChanged(f) > ms);
function execFor(rec) {
  const d = dsuite.get(rec.id), g = dgate.get(rec.id), files = [rec.path, ...(rec.prod || []), ...((d && d.prodRefs) || [])].filter(Boolean);
  if (rec.kind === "family") return { s: "DISCOVERED", note: "Historical verification scripts without associated execution evidence in the local artifacts (they are not part of the latest recorded run).", members: rec.members.length };
  if (g) { // qualification / readiness gate: real recorded outcome of one step
    const base = { src: g.source.replace(/^target\/release\//, ""), granularity: "gate-step", ts: iso(g.endMs), startedAt: iso(g.startMs), durMs: Math.round((+g.seconds || 0) * 1000), required: g.required, carryForward: g.carryForward, note: g.carryForward ? "Recorded as carry-forward evidence (historical PASS reused, not executed in that run)." : undefined };
    if (g.skipped && !g.reused) return { ...base, s: "SKIPPED" };
    const kind = g.id.split(":")[1];
    if (kind !== "readiness") { // release / rc1 / rc2 reports are older than the current tree: stale if the product changed afterwards
      const r = rootsChangedSince(g.startMs, ["src/main/", "frontend/src/", "scripts/", "config/"]);
      if (g.passed && (g.carryForward || r.n > 0)) return { ...base, s: "STALE_EVIDENCE", was: "PASS", staleBecause: g.carryForward ? "carry-forward evidence + " + r.n + " product/tooling files changed since" : r.n + " product/tooling files changed after this qualification run (latest " + iso(r.newest).slice(0, 10) + ")" };
    }
    if (!g.passed) return { ...base, s: "FAIL", problems: [{ n: g.name, m: String(g.outputTail).split("\n").filter((l) => /FAIL|ERROR|Failures/.test(l)).slice(0, 3).join(" | ").slice(0, 300) }] };
    const ch = g.script ? changedAfter([g.script], g.startMs) : [];
    return ch.length ? { ...base, s: "STALE_EVIDENCE", was: "PASS", staleBecause: "gate script changed after the run: " + ch.join(", ") } : { ...base, s: "PASS" };
  }
  if (rec.framework === "junit5") {
    if (d && !d.cases && !d.executed) return { s: "DISCOVERED", note: "Support class (fixtures/helpers) without test cases: it cannot be executed on its own." };
    const e = d && d.executed; if (!e) return disc.evidence.surefire.dir ? { s: "NOT_EXECUTED", note: "Present in src/test but absent from the latest Maven surefire reports (outside the executed suite)." } : { s: "DISCOVERED" };
    const start = disc.evidence.surefire.start ? Date.parse(disc.evidence.surefire.start) : (gMaven ? gMaven.startMs : Date.parse(e.ts) - 300000), st = e.fail > 0 ? "FAIL" : e.pass === 0 && e.skipped > 0 ? "SKIPPED" : "PASS";
    const base = { src: "surefire:" + disc.evidence.surefire.dir, granularity: "case", ts: e.ts, durMs: Math.round(e.time * 1000), cases: { n: e.cases, pass: e.pass, fail: e.fail, skipped: e.skipped }, problems: e.problems && e.problems.length ? e.problems.map((p) => ({ n: p.n, s: p.s, ...(p.m ? { m: p.m } : {}) })) : undefined };
    const ch = changedAfter(files, start);
    return st === "PASS" && ch.length ? { ...base, s: "STALE_EVIDENCE", was: "PASS", staleBecause: "changed after the Maven run started: " + ch.slice(0, 3).join(", ") } : { ...base, s: st };
  }
  if (rec.framework === "vitest" || rec.framework === "playwright") {
    const g2 = rec.framework === "vitest" ? gVitest : gPw; if (!g2) return { s: "DISCOVERED" };
    const base = { src: "release-readiness.json:" + g2.name, granularity: "suite-step", ts: iso(g2.endMs), startedAt: iso(g2.startMs), durMs: null, note: rec.framework === "vitest" ? "Executed inside the full frontend run (all " + disc.counts.frontend + " files); per-file timing is not recorded." : "Executed by `npm run test:a11y:e2e` (all e2e specs)." };
    if (!g2.passed) return { ...base, s: "FAIL" };
    const ch = changedAfter(files, g2.startMs);
    return ch.length ? { ...base, s: "STALE_EVIDENCE", was: "PASS", staleBecause: "changed after the run started: " + ch.slice(0, 3).join(", ") } : { ...base, s: "PASS" };
  }
  return { s: "DISCOVERED", note: "No execution evidence for this script in the local artifacts." };
}
records.forEach((r) => { exec[r.id] = execFor(r); });

// ------------------------------------------------------------------ evidence matrix (derived)
const RANK = { STRONG: 3, MEDIUM: 2, LIMITED: 1 }, rankName = (n) => ["NONE_MAPPED", "LIMITED", "MEDIUM", "STRONG"][n];
const statusOf = (id) => (exec[id] || { s: "DISCOVERED" }).s;
const count = (ids, f) => { const o = {}; ids.forEach((i) => { const k = f(i); o[k] = (o[k] || 0) + 1; }); return o; };
const byId = new Map(records.map((r) => [r.id, r]));
const procs = {}; HM.procs.forEach((p) => { const stages = p.nodes.filter((n) => n.t !== "start" && n.t !== "end").map((n) => n.id); procs[p.id] = { cap: p.cap || null, scope: p.scope, stages: Object.fromEntries(stages.map((s) => [s, { d: [], i: [] }])), process: { d: [], i: [] } }; });
records.forEach((r) => (r.procs || []).forEach((pm) => { const P = procs[pm.id]; if (!P) return; const k = pm.match === "direct" ? "d" : "i"; if ((pm.stages || []).length) pm.stages.forEach((s) => { if (P.stages[s]) P.stages[s][k].push(r.id); }); else P.process[k].push(r.id); }));
function stageState(S) {
  const dPass = S.d.filter((i) => statusOf(i) === "PASS"), dStale = S.d.filter((i) => statusOf(i) === "STALE_EVIDENCE"), dFail = S.d.filter((i) => statusOf(i) === "FAIL");
  const iOk = S.i.filter((i) => ["PASS", "STALE_EVIDENCE"].includes(statusOf(i)));
  let s = "none"; if (S.d.length || S.i.length) s = "partial";
  if (dPass.length && !dFail.length) s = "ok"; else if (!dPass.length && dStale.length && !dFail.length) s = "stale";
  const withEv = S.d.filter((i) => ["PASS", "STALE_EVIDENCE"].includes(statusOf(i))), best = Math.max(0, ...withEv.map((i) => RANK[byId.get(i).strength] || 0), ...iOk.map((i) => Math.min(1, RANK[byId.get(i).strength] || 0)));
  return { s, best: rankName(best), fail: dFail.length || undefined };
}
const matrixProcs = {};
Object.keys(procs).forEach((pid) => {
  const P = procs[pid], stages = {}; let ok = 0, partial = 0, stale = 0, none = 0;
  Object.keys(P.stages).forEach((sid) => { const S = P.stages[sid], st = stageState(S); stages[sid] = S.d.length || S.i.length ? { s: st.s, best: st.best, d: S.d, i: S.i, ...(st.fail ? { fail: st.fail } : {}) } : { s: "none", best: "NONE_MAPPED" }; if (st.s === "ok") ok++; else if (st.s === "stale") stale++; else if (st.s === "partial") partial++; else none++; });
  const pt = stageState(P.process);
  matrixProcs[pid] = { cap: P.cap, scope: P.scope, stagesTotal: Object.keys(P.stages).length, ok, partial, stale, none, processLevel: P.process.d.length || P.process.i.length ? { d: P.process.d, i: P.process.i, s: pt.s } : undefined, stages };
});
const capIds = Object.keys(HM.caps), layers = require("./ids.js").LAYERS;
const mapCaps = {}; capIds.forEach((c) => { const ids = records.filter((r) => (r.caps || []).includes(c)).map((r) => r.id); const ps = HM.procsFor(c).filter((p) => p.cap === c || p.scope === "cross").map((p) => p.id); const own = HM.procsFor(c).filter((p) => p.cap === c).map((p) => p.id);
  mapCaps[c] = { layer: HM.caps[c].group, tests: ids, status: count(ids, statusOf), strength: count(ids, (i) => byId.get(i).strength || "LIMITED"), types: count(ids, (i) => byId.get(i).type), processes: own, crossProcesses: ps.filter((p) => !own.includes(p)),
    stagesTotal: own.reduce((a, p) => a + matrixProcs[p].stagesTotal, 0), stagesOk: own.reduce((a, p) => a + matrixProcs[p].ok, 0), stagesPartial: own.reduce((a, p) => a + matrixProcs[p].partial + matrixProcs[p].stale, 0),
    endToEnd: ids.some((i) => byId.get(i).strength === "STRONG" && ["END_TO_END", "RUNTIME", "INTEGRATION"].includes(byId.get(i).type)) }; });
const mapLayers = layers.map((L) => { const prim = records.filter((r) => r.layer === L.id).map((r) => r.id), sec = records.filter((r) => (r.layers2 || []).includes(L.id)).map((r) => r.id);
  return { id: L.id, caps: capIds.filter((c) => HM.caps[c].group === L.id), tests: prim.length, testsSecondary: sec.length, status: count(prim, statusOf), strength: count(prim, (i) => byId.get(i).strength || "LIMITED"), types: count(prim, (i) => byId.get(i).type), noCapTests: prim.filter((i) => !(byId.get(i).caps || []).length) }; });
const matrix = { schemaVersion: 1, generatedAt: new Date().toISOString(), note: "Functional evidence coverage (which behaviours/stages have mapped automated evidence), NOT line/branch code coverage. Derived: never edit by hand.", layers: mapLayers, caps: mapCaps, procs: matrixProcs,
  rules: { ok: "at least one DIRECT test with PASS and no failing direct test", stale: "direct tests exist but their PASS evidence is stale", partial: "only indirect evidence, or direct tests that are not (yet) PASS, or a failing direct test", none: "no mapped test", strength: "best strength among direct tests with PASS/STALE evidence (indirect evidence is capped at LIMITED); NONE_MAPPED if nothing is mapped" } };

// ------------------------------------------------------------------ gaps (derived)
const gaps = { schemaVersion: 1, generatedAt: matrix.generatedAt, note: "Absence of mapped automated evidence does NOT mean the behaviour is incorrect; it only means no direct evidence has been mapped yet." };
gaps.capabilitiesWithoutEvidence = capIds.filter((c) => !mapCaps[c].tests.length);
gaps.capabilitiesWithoutEndToEnd = capIds.filter((c) => !mapCaps[c].endToEnd);
gaps.capabilitiesUnitOnly = capIds.filter((c) => mapCaps[c].tests.length && !mapCaps[c].tests.some((i) => ["STRONG", "MEDIUM"].includes(byId.get(i).strength)));
gaps.processesWithoutEvidence = Object.keys(matrixProcs).filter((p) => matrixProcs[p].ok + matrixProcs[p].partial + matrixProcs[p].stale === 0 && !matrixProcs[p].processLevel).map((p) => ({ id: p, cap: matrixProcs[p].cap, scope: matrixProcs[p].scope }));
gaps.stagesWithoutDirectEvidence = {}; Object.keys(matrixProcs).forEach((p) => { const list = Object.keys(matrixProcs[p].stages).filter((s) => !matrixProcs[p].stages[s].d || !matrixProcs[p].stages[s].d.length); if (list.length && list.length < matrixProcs[p].stagesTotal) gaps.stagesWithoutDirectEvidence[p] = list; });
gaps.weakEvidenceStages = []; Object.keys(matrixProcs).forEach((p) => Object.keys(matrixProcs[p].stages).forEach((s) => { const x = matrixProcs[p].stages[s]; if (x.s !== "none" && (x.best === "LIMITED" || x.s === "partial")) gaps.weakEvidenceStages.push({ process: p, stage: s, state: x.s, best: x.best }); }));
gaps.staleEvidence = records.filter((r) => statusOf(r.id) === "STALE_EVIDENCE").map((r) => ({ id: r.id, why: exec[r.id].staleBecause }));
gaps.failing = records.filter((r) => statusOf(r.id) === "FAIL").map((r) => ({ id: r.id, problems: exec[r.id].problems }));
gaps.notExecuted = records.filter((r) => statusOf(r.id) === "NOT_EXECUTED").map((r) => r.id);
gaps.discoveredWithoutEvidence = records.filter((r) => statusOf(r.id) === "DISCOVERED").map((r) => r.id);
gaps.externalNotReal = records.filter((r) => r.external === "mocked").map((r) => r.id);
gaps.ambiguous = records.filter((r) => r.ambiguous).map((r) => ({ id: r.id, why: r.ambiguous }));
gaps.unanalyzed = pending;
gaps.gatesNotExecutedParts = records.filter((r) => r.kind === "gate" && r.notExecuted).map((r) => ({ id: r.id, notExecuted: r.notExecuted }));

// ------------------------------------------------------------------ write
const stateInfo = disc.state;
const execution = { schemaVersion: 1, generatedAt: matrix.generatedAt, validatedState: { head: stateInfo.head, branch: stateInfo.branch, headCommitTime: stateInfo.headCommitTime, workingTree: { staged: stateInfo.staged, unstaged: stateInfo.unstaged, untracked: stateInfo.untracked }, basis: "Execution evidence carries its own timestamps; repository state is inferred from them (the reports record no commit)." },
  sources: [{ id: "surefire", path: disc.evidence.surefire.dir, newest: disc.evidence.surefire.newest, granularity: "per test case" }, { id: "release-readiness", path: "target/release/release-readiness.json", granularity: "per gate step", steps: disc.evidence.gates.readiness }, { id: "release/rc1/rc2 qualification", path: "target/release/{release,rc1,rc2}-qualification.json", granularity: "per qualification stage", steps: disc.evidence.gates.release + disc.evidence.gates.rc1 + disc.evidence.gates.rc2 }],
  context: { ...ctxJava, os: ctxJava.os || "Windows", note: "Toolchain versions are taken from the surefire report; frontend Node/Vitest versions are not recorded in the reports (Vitest 4.1.10 appears in the step log)." }, tests: exec };
const catalog = { schemaVersion: 1, generatedAt: matrix.generatedAt, purpose: "Normalized semantic records of every catalogued test/suite/gate. Execution status lives in test-execution-state.json; aggregations in test-evidence-matrix.json.", state: { head: stateInfo.head, branch: stateInfo.branch }, layers, counts: { suites: records.length }, tests: records };
const vr = validate(records.filter((r) => !r.analysis), disc);
if (argv.includes("--check")) { vr.errors.slice(0, 40).forEach((e) => console.log("ERROR", e)); console.log("check:", records.length, "records,", vr.errors.length, "errors,", vr.warns.length, "warnings,", pending.length, "pending"); process.exit(vr.errors.length ? 1 : 0); }
writeJson(catalogPath, catalog); writeJson(path.join(__dirname, "test-execution-state.json"), execution); writeJson(path.join(__dirname, "test-evidence-matrix.json"), matrix); writeJson(path.join(__dirname, "test-gaps.json"), gaps);

// portal data (single file; the portal opens from file:// and cannot fetch JSON)
const slim = (r) => { const o = { ...r }; delete o.sourceHash; delete o.lines; delete o.caseNames; delete o.displayNames; delete o.describes; delete o.prodRefs; delete o.traits; Object.keys(o).forEach((k) => { if (o[k] == null || (Array.isArray(o[k]) && !o[k].length)) delete o[k]; }); return o; };
const portal = { generatedAt: matrix.generatedAt, state: execution.validatedState, context: execution.context, layers, tests: Object.fromEntries(records.map((r) => [r.id, slim(r)])), exec, matrix: { layers: mapLayers, caps: mapCaps, procs: matrixProcs }, gaps };
fs.writeFileSync(path.join(ARCH, "hydra-tests-data.js"), "/* GENERATED by knowledge/tests/build-test-evidence.js - do not edit. Test evidence for the HYDRA portal (window.HYDRA_TESTS). */\nwindow.HYDRA_TESTS = " + JSON.stringify(portal) + ";\n", "utf8");

// bitácora integration: compact references only (no duplicated test text)
const cachePath = path.join(ARCH, "knowledge", "capabilities-cache.json");
if (fs.existsSync(cachePath)) {
  const cache = readJson(cachePath);
  capIds.forEach((c) => { const m = mapCaps[c], cc = cache.capabilities[c]; if (!cc) return; const psev = {}; m.processes.forEach((p) => { const o = {}; Object.keys(matrixProcs[p].stages).forEach((s) => { const x = matrixProcs[p].stages[s]; if (x.s !== "none") o[s] = x.s; }); if (Object.keys(o).length) psev[p] = o; });
    cc.testEvidence = { evidenceSummary: { tests: m.tests.length, byStatus: m.status, byStrength: m.strength, byType: m.types, endToEnd: m.endToEnd, stagesTotal: m.stagesTotal, stagesWithEvidence: m.stagesOk + m.stagesPartial, stagesDirectlyVerified: m.stagesOk }, evidenceIds: m.tests.filter((i) => byId.get(i).strength === "STRONG").slice(0, 12), failingIds: m.tests.filter((i) => statusOf(i) === "FAIL"), store: "knowledge/tests/" };
    cc.processStageEvidence = psev; });
  cache.testEvidence = { generatedAt: matrix.generatedAt, store: "knowledge/tests/", note: "Per-capability testEvidence / processStageEvidence are compact references into knowledge/tests/*; regenerate with build-test-evidence.js after regenerating this cache." };
  writeJson(cachePath, cache, true);
}

// bitácora: baseline + gaps (idempotent; entries with id prefix "gap-t" are owned by this tool)
(function(){
  const kdir = path.join(ARCH, "knowledge"), bp = path.join(kdir, "baseline.json"), gpth = path.join(kdir, "gaps.json"), failing = gaps.failing.map((f) => f.id);
  const sc = count(records.map((r) => r.id), statusOf);
  if (fs.existsSync(bp)) { const bl = readJson(bp); bl.testEvidence = { generatedAt: matrix.generatedAt, store: "knowledge/tests/", repositoryState: { head: stateInfo.head, branch: stateInfo.branch, unstaged: stateInfo.unstaged, untracked: stateInfo.untracked }, executionEvidence: { surefire: disc.evidence.surefire, gates: disc.evidence.gates }, counts: { records: records.length, byStatus: sc }, note: "Test evidence is tracked incrementally: run knowledge/tests/discover-tests.js --changed, analyse new/modified tests, then build-test-evidence.js." }; writeJson(bp, bl, true); }
  if (fs.existsSync(gpth)) {
    const g = readJson(gpth); g.gaps = (g.gaps || []).filter((x) => !String(x.id).startsWith("gap-t"));
    const mk = (id, sev, title, detail) => g.gaps.push({ id, kind: "TEST_EVIDENCE", severity: sev, status: "OPEN", title, detail, ref: "knowledge/tests/test-gaps.json" });
    if (failing.length) mk("gap-t001", "high", "Failing tests in the latest recorded execution", "Failing: " + failing.join(", ") + ". Recorded in test-execution-state.json; the behaviour affected is described in each record (limits/behavior).");
    mk("gap-t002", "medium", gaps.capabilitiesWithoutEndToEnd.length + " capabilities have no end-to-end / integration-level evidence mapped", gaps.capabilitiesWithoutEndToEnd.join(", ") + ". Their evidence is component/unit level (see evidence strength per capability).");
    mk("gap-t003", "medium", gaps.processesWithoutEvidence.length + " functional processes have no mapped automated evidence", "Mostly cross-capability workflows and authoring micro-processes; absence of mapped evidence does not imply incorrect behaviour.");
    mk("gap-t004", "medium", gaps.staleEvidence.length + " qualification gates carry stale or carry-forward evidence", "Release/RC1/RC2 qualification reports predate recent product changes (or reuse historical PASS); re-run the qualification to refresh them.");
    mk("gap-t005", "low", "Frontend per-file results are recorded only at suite level", "The full Vitest run is recorded as a single gate step; per-file/per-case frontend results are not stored by the local tooling.");
    mk("gap-t006", "low", gaps.discoveredWithoutEvidence.length + " verification scripts have no execution evidence in the local artifacts", "Historical per-fix verifiers, native/e2e scripts and PowerShell wrappers are not part of the latest recorded run; native installer/OS evidence is opt-in (generated-editor workflow).");
    if (gaps.ambiguous.length) mk("gap-t007", "low", gaps.ambiguous.length + " tests whose meaning still needs review", gaps.ambiguous.map((a) => a.id).join(", "));
    g.testEvidenceSummary = { generatedAt: matrix.generatedAt, byStatus: sc, store: "knowledge/tests/" }; writeJson(gpth, g, true);
  }
})();
console.log(JSON.stringify({ records: records.length, pending: pending.length, validate: { errors: vr.errors.length, warnings: vr.warns.length }, status: count(records.map((r) => r.id), statusOf), kinds: count(records.map((r) => r.id), (i) => byId.get(i).kind), size: { catalog: fs.statSync(catalogPath).size, portal: fs.statSync(path.join(ARCH, "hydra-tests-data.js")).size } }, null, 1));
