#!/usr/bin/env node
/* V3 fase 3: auditoría de VIGENCIA de la evidencia que sostiene cada etapa STRONG. Solo lectura.
   Para cada test Java con evidencia PASS calcula el cierre transitivo de sus dependencias (código de producción, soportes de test y
   recursos; todo src/main si es @SpringBootTest) más pom.xml/.mvn y compara la fecha de cada fichero con el inicio de la ejecución
   conservada. Un test sin cambios posteriores en su cierre sigue vigente (no se repite); con cambios queda INVALIDADO (se reejecuta).
   Los specs Playwright y ficheros Vitest se validan con su huella en el runner (inputGlobs/inputs), no aquí.
   Uso: node audit-currency.js [--out <dir>] */
"use strict";
const fs = require("fs"), path = require("path");
const KT = path.resolve(__dirname, "..");
const ROOT = path.resolve(KT, "..", "..", "..", "..", "..") + path.sep;
const rd = (f) => JSON.parse(fs.readFileSync(path.join(KT, f), "utf8").replace(/^﻿/, ""));
const mx = rd("test-evidence-matrix.json"), cat = new Map(rd("test-catalog.json").tests.map((t) => [t.id, t])), ex = rd("test-execution-state.json").tests;
const index = JSON.parse(fs.readFileSync(ROOT + "target/archify-v3/evidence/evidence-index.json", "utf8"));
const args = process.argv.slice(2), OUT = path.resolve(args.includes("--out") ? args[args.indexOf("--out") + 1] : ROOT + "target/archify-v3/phase3");
const walk = (dir, out = []) => { if (!fs.existsSync(dir)) return out; for (const e of fs.readdirSync(dir, { withFileTypes: true })) { const f = path.join(dir, e.name); if (e.isDirectory()) walk(f, out); else out.push(f); } return out; };
const mainJava = walk(ROOT + "src/main/java").filter((f) => f.endsWith(".java")), testJava = walk(ROOT + "src/test/java").filter((f) => f.endsWith(".java")), resources = walk(ROOT + "src/main/resources");
const byName = new Map(); [...mainJava, ...testJava].forEach((f) => { const n = path.basename(f, ".java"); (byName.get(n) || byName.set(n, []).get(n)).push(f); });
const mtime = new Map(); const mt = (f) => { if (!mtime.has(f)) mtime.set(f, fs.statSync(f).mtimeMs); return mtime.get(f); };
const refsCache = new Map(), springCache = new Map();
const src = (f) => fs.readFileSync(f, "utf8");
const refs = (f) => { if (refsCache.has(f)) return refsCache.get(f); const s = src(f), own = path.basename(f, ".java"), set = new Set(); for (const m of s.matchAll(/\b([A-Z][A-Za-z0-9]{2,})\b/g)) { const list = byName.get(m[1]); if (list && m[1] !== own) list.forEach((x) => set.add(x)); } refsCache.set(f, [...set]); return refsCache.get(f); };
const closure = (start) => { const seen = new Set([start]), q = [start]; let spring = /@SpringBootTest|@AutoConfigureMockMvc/.test(src(start)); while (q.length) { const f = q.pop(); for (const r of refs(f)) if (!seen.has(r)) { seen.add(r); q.push(r); if (!spring && /@SpringBootTest/.test(src(r))) spring = true; } } return { files: seen, spring }; };
const globalFiles = [ROOT + "pom.xml", ROOT + ".mvn/maven.config", ROOT + ".mvn/wrapper/maven-wrapper.properties"].filter(fs.existsSync);
const startOf = (id) => { const outer = id.replace(/^java:/, ""), e = index.classes[outer]; if (e && e.startedAt) return Date.parse(e.startedAt); const s = ex[id]; return s && s.ts ? Date.parse(s.ts) - 300000 : null; };
const tests = new Set(); Object.entries(mx.procs).forEach(([pid, P]) => Object.values(P.stages).forEach((x) => { if (x.best !== "STRONG") return; (x.d || []).forEach((i) => { const r = cat.get(i); if (r && r.strength === "STRONG" && (ex[i] || {}).s === "PASS") tests.add(i); }); }));
const verdict = new Map(), allMain = [...mainJava.filter((f) => f.includes(path.sep + "main" + path.sep)), ...resources];
const stableAll = (() => { let m = 0, name = ""; allMain.forEach((f) => { const t = mt(f); if (t > m) { m = t; name = f; } }); return { m, name }; })();
for (const id of tests) {
  if (!id.startsWith("java:")) { verdict.set(id, { kind: id.split(":")[0], status: "RUNNER_FINGERPRINT", note: "vigencia por huella del runner (entradas de la tarea)" }); continue; }
  const outer = id.replace(/^java:/, ""), tf = testJava.find((f) => { const s = src(f); const m = s.match(/^package\s+([\w.]+);/m); return m && m[1] + "." + path.basename(f, ".java") === outer; });
  const start = startOf(id); if (!tf || !start) { verdict.set(id, { kind: "java", status: "UNKNOWN", note: "sin fichero o sin hora de inicio" }); continue; }
  const { files, spring } = closure(tf); const watched = new Set([...files, ...globalFiles]); if (spring) allMain.forEach((f) => watched.add(f));
  const changed = [...watched].filter((f) => mt(f) > start + 1000).map((f) => path.relative(ROOT, f).replace(/\\/g, "/"));
  verdict.set(id, { kind: "java", status: changed.length ? "INVALIDATED" : "VALID", spring, closureFiles: watched.size, evidenceStart: new Date(start).toISOString(), runId: (index.classes[outer] || {}).runId, changed: changed.slice(0, 8), changedCount: changed.length });
}
// impact on stages
const stages = []; let histOnly = 0;
Object.entries(mx.procs).forEach(([pid, P]) => Object.entries(P.stages).forEach(([sid, x]) => {
  if (x.best !== "STRONG") return;
  const direct = (x.d || []).filter((i) => tests.has(i)); if (!direct.length) return;
  const st = direct.map((i) => ({ id: i, ...verdict.get(i) }));
  const validStrong = st.filter((v) => v.status === "VALID" || v.status === "RUNNER_FINGERPRINT").length;
  const dates = direct.map((i) => (ex[i] || {}).ts || "").sort();
  const historical = dates.every((d) => d && d < "2026-10-19");
  if (historical) histOnly++;
  stages.push({ stage: pid + "." + sid, directStrongPass: direct.length, valid: validStrong, invalidated: st.filter((v) => v.status === "INVALIDATED").length, unknown: st.filter((v) => v.status === "UNKNOWN").length, historicalOnly: historical, withoutValidStrong: validStrong === 0 });
}));
const summary = { stagesStrongAnalysed: stages.length, historicalOnlyBeforeOct19: histOnly, testsAnalysed: tests.size, testsJava: [...verdict.values()].filter((v) => v.kind === "java").length, java: { VALID: 0, INVALIDATED: 0, UNKNOWN: 0 }, springDependent: [...verdict.values()].filter((v) => v.spring).length,
  stagesWithoutAnyValidStrong: stages.filter((s) => s.withoutValidStrong).length, stagesHistoricalAndInvalidated: stages.filter((s) => s.historicalOnly && s.withoutValidStrong).length, newestMainFile: path.relative(ROOT, stableAll.name).replace(/\\/g, "/"), newestMainMtime: new Date(stableAll.m).toISOString() };
[...verdict.values()].forEach((v) => { if (v.kind === "java") summary.java[v.status]++; });
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, "currency.json"), JSON.stringify({ summary, tests: Object.fromEntries(verdict), stages }, null, 1));
fs.writeFileSync(path.join(OUT, "currency-invalidated.txt"), [...verdict.entries()].filter(([, v]) => v.status === "INVALIDATED").map(([k]) => k.replace(/^java:/, "")).sort().join("\n"));
console.log(JSON.stringify(summary, null, 1));
