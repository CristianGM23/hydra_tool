#!/usr/bin/env node
/* V3 step 1: deterministic inventory of the stages that are NOT yet STRONG (current official matrix), with the facts needed to analyse them.
   Usage: node inventory-709.js [--out <dir>] [--packages <n>]
   Writes <out>/inventory.json (all non-STRONG stages), <out>/slices/pkg-NN.json (work packages for the analysts: balanced by stage count, grouped by process). */
"use strict";
const fs = require("fs"), path = require("path");
const KT = path.resolve(__dirname, "..");
const { HM } = require(path.join(KT, "portal-model-loader.js"))();
const args = process.argv.slice(2), opt = (k, d) => (args.includes(k) ? args[args.indexOf(k) + 1] : d);
const OUT = path.resolve(opt("--out", path.join(KT, "..", "..", "..", "..", "..", "target", "archify-v3", "analysis")));
const N = +opt("--packages", 12);
const mx = JSON.parse(fs.readFileSync(path.join(KT, "test-evidence-matrix.json"), "utf8")), cat = JSON.parse(fs.readFileSync(path.join(KT, "test-catalog.json"), "utf8")).tests, ex = JSON.parse(fs.readFileSync(path.join(KT, "test-execution-state.json"), "utf8")).tests;
const byId = new Map(cat.map((t) => [t.id, t])); const T = (t) => (t && (t.es || t)) || "";
const NO_IMPL = new Set(["gamification_author_policy", "project_author_areas", "recovery_author_governance"]);
const EXTERNAL = /(^repos_|^toolchains_|^x_remote_|oauth|git remoto|remote git|toolchain)/i;
const rows = [];
HM.procs.forEach((p) => {
  const P = mx.procs[p.id]; if (!P) return;
  p.nodes.forEach((n) => {
    if (n.t === "start" || n.t === "end") return; const x = P.stages[n.id]; if (!x || x.best === "STRONG") return;
    const tech = (HM.tech.procs[p.id] || {})[n.id] || {};
    const tests = (ids) => (ids || []).map((i) => { const t = byId.get(i) || {}; return { id: i, st: (ex[i] || {}).s, strength: t.strength, type: t.type, fw: t.framework, external: t.external }; });
    const row = { proc: p.id, scope: p.scope, cap: p.cap || null, owner: n.cap, stage: n.id, node: n.t, actor: n.a || "", required: n.r || "", label: T(n.l), user: T(n.u).slice(0, 260), func: T(n.f).slice(0, 260), out: T(n.out).slice(0, 160), rt: (n.x && n.x.rt) || null, tc: tech.tc || null, tm: tech.tm || null,
      best: x.best, state: x.s, direct: tests(x.d), indirect: tests(x.i) };
    // mechanical classification (a hypothesis for the analysts, never a verdict)
    let cls = "BACKEND_CANDIDATE";
    if (NO_IMPL.has(p.id)) cls = "NO_BACKEND_IMPLEMENTATION";
    else if (EXTERNAL.test(p.id) || EXTERNAL.test(row.label) || p.cap === "external_repositories" || p.cap === "toolchains") cls = "EXTERNAL_OR_NATIVE";
    else if (n.t === "decision" || n.t === "artifact") cls = "DERIVED_NODE";
    else if (p.scope === "authoring" && !(row.rt || row.tc)) cls = "UI_AUTHORING";
    else if (!(row.rt || row.tc) && n.a === "user") cls = "UI_OR_USER_ACTION";
    row.classHint = cls;
    rows.push(row);
  });
});
fs.mkdirSync(path.join(OUT, "slices"), { recursive: true });
fs.writeFileSync(path.join(OUT, "inventory.json"), JSON.stringify(rows));
// work packages: group by process, balance by stage count; non-cross processes first (by capability), cross processes spread at the end
const byProc = {}; rows.forEach((r) => { (byProc[r.proc] = byProc[r.proc] || []).push(r); });
const procs = Object.keys(byProc).sort((a, b) => { const A = byProc[a][0], B = byProc[b][0]; return (A.scope === "cross") - (B.scope === "cross") || String(A.cap || A.owner).localeCompare(String(B.cap || B.owner)) || a.localeCompare(b); });
const target = Math.ceil(rows.length / N), pk = []; let cur = [], size = 0;
procs.forEach((p) => { if (size >= target && pk.length < N - 1) { pk.push(cur); cur = []; size = 0; } cur.push(p); size += byProc[p].length; }); if (cur.length) pk.push(cur);
pk.forEach((list, i) => fs.writeFileSync(path.join(OUT, "slices", "pkg-" + String(i + 1).padStart(2, "0") + ".json"), JSON.stringify(list.map((p) => ({ process: p, scope: byProc[p][0].scope, cap: byProc[p][0].cap, stages: byProc[p] })))));
const cnt = (f) => rows.reduce((o, r) => { const k = f(r); o[k] = (o[k] || 0) + 1; return o; }, {});
console.log(JSON.stringify({ nonStrong: rows.length, byBest: cnt((r) => r.best), byClassHint: cnt((r) => r.classHint), packages: pk.map((l, i) => ({ pkg: i + 1, processes: l.length, stages: l.reduce((a, p) => a + byProc[p].length, 0), first: l[0] })) }, null, 1));
