#!/usr/bin/env node
/* V3 fase 2: agrega el análisis fresco de las etapas no STRONG (frags/pkg-NN.json) y verifica mecánicamente cada REMAP/DERIVED
   contra el catálogo y el estado de ejecución oficiales. Solo lectura.
   Uso: node phase2-aggregate.js <dir phase2> [--out <dir>]   (dir con frags/ y slices/) */
"use strict";
const fs = require("fs"), path = require("path");
const KT = path.resolve(__dirname, "..");
const ROOT = path.resolve(KT, "..", "..", "..", "..", "..") + path.sep;
const dir = path.resolve(process.argv[2] || path.join(ROOT, "target/archify-v3/phase2"));
const out = path.resolve((process.argv.includes("--out") ? process.argv[process.argv.indexOf("--out") + 1] : dir));
const { HM } = require(path.join(KT, "portal-model-loader.js"))();
const rd = (f) => JSON.parse(fs.readFileSync(path.join(KT, f), "utf8"));
const cat = new Map(rd("test-catalog.json").tests.map((t) => [t.id, t])), ex = rd("test-execution-state.json").tests, mx = rd("test-evidence-matrix.json");
const catOf = (pid) => { const p = HM.procs.find((x) => x.id === pid); return p ? (p.scope === "cross" ? "cross" : p.cap || "sin-capacidad") : "?"; };
const rows = [];
fs.readdirSync(path.join(dir, "frags")).filter((f) => /^pkg-\d+\.json$/.test(f)).sort().forEach((f) => JSON.parse(fs.readFileSync(path.join(dir, "frags", f), "utf8").replace(/^﻿/, "")).forEach((r) => rows.push({ ...r, pkg: f })));
const RANK = { STRONG: 3, MEDIUM: 2, LIMITED: 1 };
rows.forEach((r) => {
  const x = (mx.procs[r.proc] || { stages: {} }).stages[r.stage] || {};
  r.category = catOf(r.proc); r.bestNow = x.best || "NONE_MAPPED"; r.stateNow = x.s || "none";
  if (r.decision === "REMAP_EXISTING" || r.decision === "DERIVED_INDIVIDUAL") {
    const t = cat.get(r.test), st = (ex[r.test] || {}).s, a = r.assertion || {}; let line = null;
    try { line = fs.readFileSync(ROOT + a.path, "utf8").split("\n")[a.line - 1]; } catch (e) { /* ignorado */ }
    r.check = { catalog: !!t, strength: t && t.strength, external: t && t.external, status: st, assertionLine: line ? line.trim().slice(0, 140) : null,
      alreadyMappedDirect: !!(t && (t.procs || []).some((p) => p.id === r.proc && (p.stages || []).includes(r.stage) && p.match === "direct")) };
    r.verdict = !t ? "TEST_NOT_IN_CATALOG" : st !== "PASS" ? "TEST_NOT_PASS:" + st : t.external === "mocked" ? "MOCKED_EXTERNAL" : t.strength === "STRONG" ? "STRONG_CANDIDATE" : "TEST_NOT_STRONG:" + t.strength;
    if (!line) r.verdict = "ASSERTION_LINE_MISSING";
  }
});
const count = (f) => rows.reduce((o, r) => { const k = f(r); o[k] = (o[k] || 0) + 1; return o; }, {});
const byDecision = count((r) => r.decision), byCategory = {};
rows.forEach((r) => { const c = byCategory[r.category] = byCategory[r.category] || {}; c[r.decision] = (c[r.decision] || 0) + 1; c.total = (c.total || 0) + 1; });
const remap = rows.filter((r) => r.check), verdicts = count2(remap, (r) => r.verdict);
function count2(a, f) { return a.reduce((o, r) => { const k = f(r); o[k] = (o[k] || 0) + 1; return o; }, {}); }
const journeys = {}; rows.filter((r) => r.decision === "E2E_REAL_BACKEND").forEach((r) => { const j = journeys[r.journey] = journeys[r.journey] || { journey: r.journey, screen: r.screen, oracle: r.oracle, userSteps: r.userSteps, stages: [], categories: new Set() }; j.stages.push(r.proc + "." + r.stage); j.categories.add(r.category); });
const groups = {}; rows.filter((r) => r.decision === "NEW_BACKEND_TEST").forEach((r) => { const g = groups[r.group] = groups[r.group] || { group: r.group, stages: [] }; g.stages.push(r.proc + "." + r.stage); if (r.newTest) g.newTest = r.newTest; });
const defects = {}; rows.filter((r) => r.decision === "PRODUCTION_DEFECT").forEach((r) => { (defects[r.defect] = defects[r.defect] || []).push(r.proc + "." + r.stage); });
const summary = { total: rows.length, byDecision, byCategory, remapDerivedVerification: verdicts, journeys: Object.values(journeys).map((j) => ({ ...j, categories: [...j.categories] })).sort((a, b) => b.stages.length - a.stages.length), newBackendGroups: Object.values(groups).sort((a, b) => b.stages.length - a.stages.length), defects };
fs.mkdirSync(out, { recursive: true });
fs.writeFileSync(path.join(out, "phase2-aggregate.json"), JSON.stringify({ summary, rows }, null, 1), "utf8");
console.log(JSON.stringify({ total: summary.total, byDecision, remapDerivedVerification: verdicts, journeys: summary.journeys.length, newBackendGroups: summary.newBackendGroups.length, defects: Object.fromEntries(Object.entries(defects).map(([k, v]) => [k, v.length])) }, null, 1));
