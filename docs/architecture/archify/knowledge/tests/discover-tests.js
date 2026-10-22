#!/usr/bin/env node
/* HYDRA test discovery + execution-evidence extraction (deterministic, no LLM, no test execution).
   Usage: node docs/architecture/archify/knowledge/tests/discover-tests.js [--out <file>] [--changed]
   Produces a "discovery" JSON: for every test suite/file/gate the static facts (path, framework, case names, referenced production
   files, source hash) and the LATEST RELIABLY KNOWN execution evidence found in local repository artifacts:
     * build/hydra-maven/surefire-reports/TEST-*.xml      -> per-case JUnit results (real execution evidence)
     * target/release/release-readiness.json              -> 73 gate steps incl. the full Vitest run, Maven verify, a11y, build ...
     * target/release/{release,rc1,rc2}-qualification.json -> qualification gates (may be CARRY_FORWARD evidence)
   Nothing here decides what a test MEANS: semantic fields are authored from the test sources (see README.md).
   --changed compares source hashes with the existing test-catalog.json and lists new / modified / deleted tests only. */
"use strict";
const fs = require("fs"), path = require("path"), crypto = require("crypto"), cp = require("child_process");
const ROOT = path.resolve(__dirname, "..", "..", "..", "..", "..");
const args = process.argv.slice(2), outFile = args.includes("--out") ? args[args.indexOf("--out") + 1] : null, changedOnly = args.includes("--changed");
const rel = (p) => path.relative(ROOT, p).split(path.sep).join("/");
const read = (p) => fs.readFileSync(p, "utf8");
const sha = (s) => crypto.createHash("sha256").update(s).digest("hex").slice(0, 16);
const exists = fs.existsSync;
function walk(dir, filt, out = []) { if (!exists(dir)) return out; for (const e of fs.readdirSync(dir, { withFileTypes: true })) { const p = path.join(dir, e.name); if (e.isDirectory()) { if (e.name === "node_modules" || e.name === "dist" || e.name === ".git") continue; if (e.name === "target" && path.relative(ROOT, p) === "target") continue; walk(p, filt, out); } else if (filt(p)) out.push(p); } return out; }
const git = (a) => { try { return cp.execFileSync("git", a, { cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 }).trim(); } catch (e) { return ""; } };

// ---------------------------------------------------------------- repository state (dynamic)
const state = { head: git(["rev-parse", "HEAD"]), branch: git(["rev-parse", "--abbrev-ref", "HEAD"]), headCommitTime: git(["log", "-1", "--format=%cI"]),
  staged: git(["diff", "--cached", "--name-only"]).split("\n").filter(Boolean).length, unstaged: git(["diff", "--name-only"]).split("\n").filter(Boolean).length, untracked: git(["ls-files", "--others", "--exclude-standard"]).split("\n").filter(Boolean).length };

// ---------------------------------------------------------------- production index (class name -> path) for correlation
const mainJava = walk(path.join(ROOT, "src/main/java"), (p) => p.endsWith(".java"));
const classToMain = {}; mainJava.forEach((p) => { classToMain[path.basename(p, ".java")] = classToMain[path.basename(p, ".java")] || rel(p); });
const feSrc = path.join(ROOT, "frontend/src");

// ---------------------------------------------------------------- Java tests
const testJava = walk(path.join(ROOT, "src/test/java"), (p) => p.endsWith(".java"));
const surefireDir = path.join(ROOT, "build/hydra-maven/surefire-reports");
// Immutable evidence store (archify V3 runner): the latest captured XML per test class survives later Maven runs (the pom cleans
// build/hydra-maven at "initialize"). Per class the NEWEST evidence wins (store vs volatile build dir); the store also records when that run started.
const storeIndexFile = path.join(ROOT, "target/archify-v3/evidence/evidence-index.json");
let storeIndex = null; try { if (exists(storeIndexFile)) storeIndex = JSON.parse(read(storeIndexFile)); } catch (e) { storeIndex = null; }
const sure = {}; // outer FQN -> { cases:[], ts, time, mtime, start, from }
let surefireNewest = 0, surefireOldest = 0, storeClasses = 0;
const cand = {}; // outer FQN -> best candidate { files:[abs], mtime, start, from, run }
const offer = (outer, c) => { if (!cand[outer] || c.mtime > cand[outer].mtime || (c.mtime === cand[outer].mtime && c.from === "store")) cand[outer] = c; };
if (exists(surefireDir)) {
  const byOuter = {};
  for (const f of fs.readdirSync(surefireDir).filter((n) => /^TEST-.*\.xml$/.test(n))) {
    const full = path.join(surefireDir, f), st = fs.statSync(full), outer = f.replace(/^TEST-/, "").replace(/\.xml$/, "").split("$")[0];
    surefireNewest = Math.max(surefireNewest, st.mtimeMs); surefireOldest = surefireOldest ? Math.min(surefireOldest, st.mtimeMs) : st.mtimeMs;
    const o = byOuter[outer] = byOuter[outer] || { files: [], mtime: 0 }; o.files.push(full); o.mtime = Math.max(o.mtime, st.mtimeMs);
  }
  Object.entries(byOuter).forEach(([outer, o]) => offer(outer, { files: o.files, mtime: o.mtime, start: null, from: "build" }));
}
if (storeIndex && storeIndex.classes) {
  for (const [outer, e] of Object.entries(storeIndex.classes)) {
    const files = (e.files || []).map((n) => path.join(ROOT, e.dir || "target/archify-v3/evidence/surefire", n)).filter(exists); if (!files.length) continue;
    offer(outer, { files, mtime: Date.parse(e.finishedAt) || 0, start: e.startedAt || null, from: "store", run: e.runId }); storeClasses++;
    surefireNewest = Math.max(surefireNewest, Date.parse(e.finishedAt) || 0);
  }
}
for (const [outer, c] of Object.entries(cand)) {
  const rec = sure[outer] = { cases: [], ts: null, files: c.files.length, mtime: c.mtime, start: c.start, from: c.from, run: c.run };
  for (const full of c.files) {
    const x = read(full), head = x.match(/<testsuite [^>]*>/)[0]; rec.ts = rec.ts || (head.match(/ timestamp="([^"]+)"/) || [])[1];
    const parts = x.split("<testcase ").slice(1);
    for (const part of parts) {
      const nm = (part.match(/^name="([^"]*)"/) || [])[1], t = +((part.match(/ time="([^"]*)"/) || [])[1] || 0);
      const body = part.slice(0, part.indexOf("</testcase>") >= 0 ? part.indexOf("</testcase>") : part.indexOf("/>") + 2);
      const status = /<failure/.test(body) ? "FAIL" : /<error/.test(body) ? "FAIL" : /<skipped/.test(body) ? "SKIPPED" : "PASS";
      const msg = status === "FAIL" ? ((body.match(/message="([^"]{0,300})/) || [])[1] || "").replace(/&#10;|\n/g, " ").trim() : null;
      rec.cases.push({ n: (nm || "").replace(/&quot;/g, '"').replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&"), s: status, t, ...(msg ? { m: msg } : {}) });
    }
  }
}
const javaSuites = testJava.map((p) => {
  const src = read(p), pkg = (src.match(/^package\s+([\w.]+);/m) || [])[1] || "", cls = path.basename(p, ".java"), fqn = pkg + "." + cls;
  const methods = []; const re = /@(?:Test|ParameterizedTest|RepeatedTest|TestFactory)\b[\s\S]{0,300}?\b(?:void|[\w<>\[\]]+)\s+(\w+)\s*\(/g; let m;
  while ((m = re.exec(src))) methods.push(m[1]);
  const display = []; const dre = /@DisplayName\("([^"]{1,160})"\)/g; while ((m = dre.exec(src))) display.push(m[1]);
  const prodRefs = new Set(); for (const mm of src.matchAll(/\b([A-Z][A-Za-z0-9]{3,})\b/g)) { const c = mm[1]; if (classToMain[c] && c !== cls.replace(/Tests?$/, "")) prodRefs.add(classToMain[c]); }
  const own = classToMain[cls.replace(/Tests?$|IT$/, "")]; if (own) prodRefs.add(own);
  const ex = sure[fqn];
  const mock = /org\.mockito|@Mock|mock\(|@MockBean|MockMvc|@WebMvcTest/.test(src), spring = /@SpringBootTest|@WebMvcTest|MockMvc|@AutoConfigureMockMvc/.test(src), tmp = /@TempDir|Files\.createTemp/.test(src), proc = /ProcessBuilder|HydraExternalProcessRunner|ExternalToolProcessRunner/.test(src);
  return { id: "java:" + fqn, kind: "suite", framework: "junit5", path: rel(p), sourceHash: sha(src), lines: src.split("\n").length, cases: methods.length, caseNames: [...new Set(methods)].slice(0, 80), displayNames: display.slice(0, 30), prodRefs: [...prodRefs].slice(0, 24),
    traits: { mocks: mock, springContext: spring, tempFiles: tmp, externalProcess: proc }, executed: ex ? { cases: ex.cases.length, pass: ex.cases.filter((c) => c.s === "PASS").length, fail: ex.cases.filter((c) => c.s === "FAIL").length, skipped: ex.cases.filter((c) => c.s === "SKIPPED").length, time: +ex.cases.reduce((a, c) => a + c.t, 0).toFixed(3), ts: new Date(ex.mtime).toISOString(), start: ex.start || null, from: ex.from, run: ex.run || null, tsAttr: ex.ts, problems: ex.cases.filter((c) => c.s !== "PASS").slice(0, 12) } : null };
});

// ---------------------------------------------------------------- frontend tests (Vitest / node:test / Playwright)
const feTests = walk(path.join(ROOT, "frontend"), (p) => /\.(test|spec)\.(ts|tsx|js|jsx|mjs)$/.test(p) && !/node_modules/.test(p));
const frontendSuites = feTests.map((p) => {
  const src = read(p), r = rel(p), titles = []; let m; const re = /\b(?:it|test)(?:\.each\([^)]*\))?(?:\.(?:only|skip|concurrent))?\(\s*(['"`])((?:\\.|(?!\1).){1,200})\1/g; while ((m = re.exec(src))) titles.push(m[2]);
  const describe = []; const dre = /\bdescribe(?:\.each\([^)]*\))?\(\s*(['"`])((?:\\.|(?!\1).){1,160})\1/g; while ((m = dre.exec(src))) describe.push(m[2]);
  const prodRefs = new Set(); for (const mm of src.matchAll(/from\s+['"](\.{1,2}\/[^'"]+)['"]/g)) { const base = path.resolve(path.dirname(p), mm[1]); for (const ext of ["", ".ts", ".tsx", ".js", ".mjs", "/index.ts", "/index.tsx"]) { if (exists(base + ext) && fs.statSync(base + ext).isFile() && !/\.test\./.test(base + ext)) { prodRefs.add(rel(base + ext)); break; } } }
  const fw = /@playwright\/test/.test(src) ? "playwright" : /node:test/.test(src) ? "node-test" : "vitest";
  return { id: (fw === "playwright" ? "pw:" : fw === "node-test" ? "nodetest:" : "vitest:") + r.replace(/^frontend\//, ""), kind: "suite", framework: fw, path: r, sourceHash: sha(src), lines: src.split("\n").length, cases: titles.length, caseNames: titles.slice(0, 60), describes: describe.slice(0, 20), prodRefs: [...prodRefs].slice(0, 20),
    traits: { mocks: /vi\.mock|vi\.fn|jest\.mock/.test(src), fetchMocked: /fetch.*mock|mockFetch|vi\.stubGlobal\(['"]fetch/.test(src), domRender: /@testing-library|render\(/.test(src), network: /http:\/\/|fetch\(/.test(src) }, executed: null };
});

// Real-backend Playwright evidence captured by the V3 runner (immutable store): per spec, newest run wins.
const feStore = (storeIndex && storeIndex.suites) || {};
frontendSuites.forEach((s) => {
  const e = feStore[s.id]; if (!e || !Array.isArray(e.cases) || !e.cases.length) return;
  const pass = e.cases.filter((c) => c.s === "PASS").length, fail = e.cases.filter((c) => c.s === "FAIL").length, skipped = e.cases.filter((c) => c.s === "SKIPPED").length;
  s.executed = { cases: e.cases.length, pass, fail, skipped, time: +e.cases.reduce((a, c) => a + (c.t || 0), 0).toFixed(3), ts: e.finishedAt, start: e.startedAt || null, from: "store", run: e.runId || null, tsAttr: e.finishedAt, problems: e.cases.filter((c) => c.s !== "PASS").slice(0, 12) };
});

// ---------------------------------------------------------------- reference-project / script tests
const scriptCandidates = [
  ...walk(path.join(ROOT, "scripts"), (p) => /\.(py|mjs|ps1|java|sh)$/.test(p) && /(verify|validate|qualify|e2e|smoke|stress|harness|parity|benchmark|audit|check|test)/i.test(path.basename(p))),
  ...walk(path.join(ROOT, "reference-projects"), (p) => /\.test\.(mjs|js)$/.test(p)), ...walk(path.join(ROOT, "frontend/scripts"), (p) => /\.(test\.mjs|mjs)$/.test(p) && /test|check|bundle|budget/i.test(path.basename(p)) && !/\.test\./.test(path.basename(p)) === false)
].filter((v, i, a) => a.indexOf(v) === i);
const scriptSuites = scriptCandidates.map((p) => {
  const src = read(p), r = rel(p), ext = path.extname(p).slice(1);
  const fw = ext === "py" ? "python-script" : ext === "ps1" ? "powershell-script" : ext === "java" ? "java-harness" : /\.test\.m?js$/.test(p) ? "node-test" : "node-script";
  const checks = (src.match(/\bcheck\(|\brequire\(|assert|PASS/g) || []).length;
  return { id: "script:" + r, kind: "script", framework: fw, path: r, sourceHash: sha(src), lines: src.split("\n").length, cases: checks, caseNames: [], prodRefs: [], traits: {}, executed: null };
});

// ---------------------------------------------------------------- gates (qualification / readiness) with real recorded outcomes
const gates = [];
function gateFile(name, relPath, kind) {
  const p = path.join(ROOT, relPath); if (!exists(p)) return;
  const j = JSON.parse(read(p)), mt = fs.statSync(p).mtime.toISOString(), carry = JSON.stringify(j).includes("CARRY_FORWARD");
  (j.steps || []).forEach((s, i) => gates.push({ id: "gate:" + kind + ":" + String(i).padStart(2, "0") + ":" + String(s.name).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 70), kind: "gate", framework: "qualification-stage", source: relPath, name: s.name, facet: s.facet || null, passed: !!s.passed, required: s.required !== false, seconds: s.seconds, reused: !!s.reused, skipped: !!s.skipped, carryForward: !!(s.reused || /CARRY_FORWARD/.test(String(s.outputTail || ""))), command: Array.isArray(s.command) ? s.command.slice(-3).join(" ").replace(/\\/g, "/").slice(0, 200) : null, outputTail: String(s.outputTail || "").replace(/\x1b\[[0-9;]*m/g, "").slice(-700), evidenceTime: mt, evidenceMode: j.evidenceMode || null, reportStatus: j.status, reportFingerprint: (j.fingerprint || "").slice(0, 16) }));
}
gateFile("readiness", "target/release/release-readiness.json", "readiness"); gateFile("release", "target/release/release-qualification.json", "release"); gateFile("rc1", "target/release/rc1-qualification.json", "rc1"); gateFile("rc2", "target/release/rc2-qualification.json", "rc2");

// ---------------------------------------------------------------- readiness step -> script mapping (verify_final_tree table)
const stepScripts = {};
try { const vf = read(path.join(ROOT, "scripts/validation/verify_final_tree.py")); for (const m of vf.matchAll(/\(\s*'([^']+)'\s*,\s*\[([^\]]*)\]\s*,\s*'([a-z0-9]+)'/g)) { const sc = (m[2].match(/'(scripts\/[^']+|frontend\/[^']+|[^']+\.(?:py|mjs|java|ps1))'/) || [])[1]; stepScripts[m[1]] = sc || m[2].replace(/sys\.executable|,|'/g, "").trim().slice(0, 140); } } catch (e) {}
gates.forEach((g) => { if (g.kind === "gate" && stepScripts[g.name]) g.script = stepScripts[g.name]; });

// ---------------------------------------------------------------- freshness: newest change under production roots vs evidence time
function newestMtime(files) { let n = 0; for (const f of files) { try { n = Math.max(n, fs.statSync(path.join(ROOT, f)).mtimeMs); } catch (e) {} } return n; }
const out = { schemaVersion: 1, generatedAt: new Date().toISOString(), state,
  evidence: { surefire: { store: storeIndex ? { index: rel(storeIndexFile), classes: storeClasses } : null, dir: exists(surefireDir) ? rel(surefireDir) : null, suites: Object.keys(sure).length, newest: surefireNewest ? new Date(surefireNewest).toISOString() : null, start: (() => { let m = surefireOldest || 0; for (const d of ["generated-sources", "maven-status"]) { try { const s = fs.statSync(path.join(ROOT, "build/hydra-maven", d)).mtimeMs; if (s && (!m || s < m)) m = s; } catch (e) {} } return m ? new Date(m).toISOString() : null; })() },
    gates: { readiness: gates.filter((g) => g.id.startsWith("gate:readiness")).length, release: gates.filter((g) => g.id.startsWith("gate:release")).length, rc1: gates.filter((g) => g.id.startsWith("gate:rc1")).length, rc2: gates.filter((g) => g.id.startsWith("gate:rc2")).length } },
  counts: { java: javaSuites.length, frontend: frontendSuites.length, scripts: scriptSuites.length, gates: gates.length, javaCases: javaSuites.reduce((a, s) => a + s.cases, 0), frontendCases: frontendSuites.reduce((a, s) => a + s.cases, 0) },
  suites: [...javaSuites, ...frontendSuites, ...scriptSuites], gates, surefireCases: Object.fromEntries(Object.entries(sure).map(([k, v]) => [k, v.cases])) };
out.suites.forEach((s) => { if (s.prodRefs && s.prodRefs.length) s.prodNewest = new Date(newestMtime(s.prodRefs.concat([s.path]))).toISOString(); });

if (changedOnly) {
  const cat = path.join(__dirname, "test-catalog.json");
  if (!exists(cat)) { console.log("No catalog yet: all tests are new."); }
  else {
    const old = {}; JSON.parse(read(cat)).tests.forEach((t) => { old[t.id] = t; });
    const now = {}; out.suites.forEach((s) => { now[s.id] = s; });
    const added = Object.keys(now).filter((i) => !old[i]), removed = Object.keys(old).filter((i) => !now[i] && old[i].kind !== "gate"), modified = Object.keys(now).filter((i) => old[i] && old[i].sourceHash && old[i].sourceHash !== now[i].sourceHash);
    console.log(JSON.stringify({ added: added.length, modified: modified.length, removed: removed.length, addedIds: added.slice(0, 40), modifiedIds: modified.slice(0, 40), removedIds: removed.slice(0, 40) }, null, 1));
  }
  process.exit(0);
}
const target = outFile || path.join(__dirname, "discovery.last.json");
fs.writeFileSync(target, JSON.stringify(out));
console.log(JSON.stringify({ out: target, state, counts: out.counts, evidence: out.evidence }, null, 1));
