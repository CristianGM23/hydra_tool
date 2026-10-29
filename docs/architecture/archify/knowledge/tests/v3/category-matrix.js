#!/usr/bin/env node
/* V3 fase 2: matriz exacta por las 19 categorías (capacidad del proceso; "cross" = procesos transversales) sobre la matriz oficial vigente.
   Solo lectura. Uso: node category-matrix.js [--json <salida>] [--compare <snapshot.json>] [--snapshot <salida>]
   El snapshot guarda por etapa la fuerza vigente para poder comparar antes/después. */
"use strict";
const fs = require("fs"), path = require("path");
const KT = path.resolve(__dirname, "..");
const { HM } = require(path.join(KT, "portal-model-loader.js"))();
const mx = JSON.parse(fs.readFileSync(path.join(KT, "test-evidence-matrix.json"), "utf8"));
const args = process.argv.slice(2), opt = (k) => (args.includes(k) ? args[args.indexOf(k) + 1] : null);
const catOf = (p) => (p.scope === "cross" ? "cross" : p.cap || "sin-capacidad");
const cat = new Map(JSON.parse(fs.readFileSync(path.join(KT, "test-catalog.json"), "utf8")).tests.map((t) => [t.id, t])), exec = JSON.parse(fs.readFileSync(path.join(KT, "test-execution-state.json"), "utf8")).tests;
const strongPassOnly = (pid, sid) => ((((mx.procs[pid] || { stages: {} }).stages[sid]) || {}).d || []).some((i) => { const r = cat.get(i); return r && r.strength === "STRONG" && (exec[i] || {}).s === "PASS"; });
const stages = {};
HM.procs.forEach((p) => p.nodes.forEach((n) => { if (n.t === "start" || n.t === "end") return; const x = (mx.procs[p.id] || { stages: {} }).stages[n.id] || {}; stages[p.id + "." + n.id] = { cat: catOf(p), best: x.best || "NONE_MAPPED", state: x.s || "none", type: n.t, strongPass: strongPassOnly(p.id, n.id) }; }));
const summarize = (st) => { const t = {}; Object.values(st).forEach((s) => { const c = t[s.cat] = t[s.cat] || { etapas: 0, STRONG: 0, STRONG_PASS: 0, MEDIUM: 0, LIMITED: 0, NONE_MAPPED: 0 }; c.etapas++; c[s.best]++; if (s.strongPass) c.STRONG_PASS++; }); return t; };
const now = summarize(stages);
const snapFile = opt("--compare");
const before = snapFile ? JSON.parse(fs.readFileSync(snapFile, "utf8")).stages : null;
const rows = Object.keys(now).sort().map((c) => ({ categoria: c, ...now[c], noStrong: now[c].etapas - now[c].STRONG }));
const tot = rows.reduce((a, r) => { ["etapas", "STRONG", "STRONG_PASS", "MEDIUM", "LIMITED", "NONE_MAPPED", "noStrong"].forEach((k) => { a[k] = (a[k] || 0) + r[k]; }); return a; }, {});
let cmp = null;
if (before) {
  const b = summarize(before), moved = {};
  Object.keys(stages).forEach((k) => { const o = before[k] ? before[k].best : "NONE_MAPPED", n = stages[k].best; if (o !== n) { const c = stages[k].cat; (moved[c] = moved[c] || {})[o + "->" + n] = (moved[c][o + "->" + n] || 0) + 1; } });
  cmp = Object.keys(now).sort().map((c) => ({ categoria: c, antesSTRONG: (b[c] || {}).STRONG || 0, despuesSTRONG: now[c].STRONG, deltaSTRONG: now[c].STRONG - ((b[c] || {}).STRONG || 0), antesNoStrong: ((b[c] || {}).etapas || 0) - ((b[c] || {}).STRONG || 0), despuesNoStrong: now[c].etapas - now[c].STRONG, movimientos: moved[c] || {} }));
}
if (opt("--snapshot")) fs.writeFileSync(opt("--snapshot"), JSON.stringify({ takenAt: new Date().toISOString(), stages }, null, 1), "utf8");
const out = { categories: rows.length, total: tot, rows, compare: cmp };
if (opt("--json")) fs.writeFileSync(opt("--json"), JSON.stringify(out, null, 1), "utf8");
const pad = (s, n) => String(s).padEnd(n);
console.log(pad("categoria", 22) + ["etapas", "STRONG", "STRONG(PASS)", "MEDIUM", "LIMITED", "sin test", "no STRONG"].map((h) => pad(h, 10)).join(""));
rows.forEach((r) => console.log(pad(r.categoria, 22) + [r.etapas, r.STRONG, r.STRONG_PASS, r.MEDIUM, r.LIMITED, r.NONE_MAPPED, r.noStrong].map((v) => pad(v, 10)).join("")));
console.log(pad("TOTAL (" + rows.length + " categorías)", 22) + [tot.etapas, tot.STRONG, tot.STRONG_PASS, tot.MEDIUM, tot.LIMITED, tot.NONE_MAPPED, tot.noStrong].map((v) => pad(v, 10)).join(""));
if (cmp) { console.log("\ncomparación STRONG antes→después"); cmp.forEach((c) => console.log(pad(c.categoria, 22) + pad(c.antesSTRONG + " → " + c.despuesSTRONG, 12) + " (" + (c.deltaSTRONG >= 0 ? "+" : "") + c.deltaSTRONG + ")")); }
