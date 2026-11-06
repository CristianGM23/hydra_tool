#!/usr/bin/env node
/* Canonical id index for the test-evidence model: layers, capabilities, processes and stages taken from the portal content model (HM).
   Usage: node ids.js [--out <dir>]   writes <dir>/index.json and <dir>/<capability>.json (compact lists used by the semantic analysts / validator). */
"use strict";
const fs = require("fs"), path = require("path");
const { HM } = require("./portal-model-loader.js")();
const T = (t) => (t && (t.es || t)) || "";
// Layers = the architecture groups already defined by the portal (HM.groups) + one cross-cutting layer for evidence that spans the whole product.
const LAYERS = Object.keys(HM.groups).map((id) => ({ id, es: HM.groups[id].es, en: HM.groups[id].en }));
LAYERS.push({ id: "quality", es: "Calidad transversal", en: "Cross-cutting quality", note: "Evidence that spans the whole product (release gates, architecture, source integrity, i18n, accessibility, performance). Added by the test-evidence model; it is not a capability group." });
function build() {
  const caps = {};
  Object.keys(HM.caps).forEach((id) => {
    const c = HM.caps[id], procs = HM.procsFor(id).map((p) => ({ id: p.id, scope: p.scope, cap: p.cap || null, n: T(p.n), nodes: p.nodes.filter((n) => n.t !== "start" && n.t !== "end").map((n) => ({ id: n.id, l: T(n.l), cap: n.cap, rt: (n.x && n.x.rt) || null, tc: (HM.tech.procs[p.id] && HM.tech.procs[p.id][n.id] && HM.tech.procs[p.id][n.id].tc) || null })) }));
    const comps = ((HM.tech.caps[id] || {}).comps || []).map((x) => ({ id: x.id, n: x.n, kind: x.kind, src: (x.src || []).slice(0, 2).map((s) => s.path) }));
    caps[id] = { id, layer: c.group, name: T(c.name) || id, elements: (c.elements || []).map((e) => ({ id: e.id, n: T(e.n) })), procs, comps };
  });
  return { layers: LAYERS, caps };
}
module.exports = build; module.exports.LAYERS = LAYERS;
if (require.main === module) {
  const out = process.argv.includes("--out") ? process.argv[process.argv.indexOf("--out") + 1] : path.join(__dirname, ".ids");
  fs.mkdirSync(out, { recursive: true });
  const b = build();
  fs.writeFileSync(path.join(out, "index.json"), JSON.stringify({ layers: b.layers, caps: Object.values(b.caps).map((c) => ({ id: c.id, layer: c.layer, name: c.name, procs: c.procs.map((p) => p.id) })) }, null, 1));
  Object.values(b.caps).forEach((c) => fs.writeFileSync(path.join(out, c.id + ".json"), JSON.stringify(c)));
  console.log("ids written to", out, Object.keys(b.caps).length, "capabilities");
}
