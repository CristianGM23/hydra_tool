#!/usr/bin/env node
/* V3 fase 2: aplica mapeos honestos REMAP_EXISTING / DERIVED_INDIVIDUAL de tests ya PASS a nuevas etapas, sin crear infraestructura paralela:
   cada mapeo viaja como fragmento semántico de la tarea dueña del test en el manifiesto V3 (tarea V3 con fragmento → se edita su registro;
   tarea V2 sin fragmento → fragments/remap-<tarea>.json clonado del catálogo; test sin tarea → tarea nueva `v3.remap-<clase>` que adopta su evidencia).
   Uso: node apply-remaps.js <remaps.json> [--apply]    (sin --apply: informe en seco). Nunca toca la evidencia ni el estado de ejecución. */
"use strict";
const fs = require("fs"), path = require("path");
const KT = path.resolve(__dirname, "..");
const ROOT = path.resolve(KT, "..", "..", "..", "..", "..") + path.sep;
const V3 = path.join(__dirname);
const apply = process.argv.includes("--apply");
const remaps = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const catalog = new Map(JSON.parse(fs.readFileSync(path.join(KT, "test-catalog.json"), "utf8")).tests.map((t) => [t.id, t]));
const manifestPath = path.join(V3, "MANIFEST_CERTIFICACION_V3.json"), manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8").replace(/^﻿/, ""));
const DROP = ["kind", "framework", "path", "sourceHash", "lines", "caseNames", "displayNames", "caseCount", "traits", "prodRefs", "prodComponents", "status", "execution", "executed"];
const byTest = new Map(); remaps.forEach((r) => { (byTest.get(r.test) || byTest.set(r.test, []).get(r.test)).push(r); });
const kebab = (s) => s.replace(/([a-z0-9])([A-Z])/g, "$1-$2").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
const readFrag = (f) => JSON.parse(fs.readFileSync(path.join(V3, f), "utf8").replace(/^﻿/, ""));
const report = { owned_v3_fragment: [], owned_v2: [], orphan_task: [], skipped: [] };
const pending = { fragments: {}, tasks: [] };
for (const [testId, rows] of byTest) {
  const rec = catalog.get(testId); if (!rec) { report.skipped.push([testId, "not in catalog"]); continue; }
  const fqn = testId.replace(/^java:/, "");
  const owner = testId.startsWith("java:") ? manifest.tasks.find((t) => t.kind === "maven" && (t.selectors || []).some((s) => s.split("#")[0] === fqn)) : null;
  const patchRecord = (record) => {
    rows.forEach((r) => {
      let p = (record.procs = record.procs || []).find((x) => x.id === r.proc && x.match === "direct");
      if (!p) { p = { id: r.proc, stages: [], match: "direct" }; record.procs.push(p); }
      if (!p.stages.includes(r.stage)) p.stages.push(r.stage);
      record.procs.forEach((x) => { if (x.match === "indirect" && x.id === r.proc) x.stages = (x.stages || []).filter((s) => s !== r.stage); });
      record.src = record.src || [];
      if (r.assertion && !record.src.some((s) => s.path === r.assertion.path && s.line === r.assertion.line)) record.src.push({ path: r.assertion.path, line: r.assertion.line });
    });
    record.procs = record.procs.filter((x) => (x.stages || []).length || x.match === "direct");
    if (record.src.length > 12) record.src = record.src.slice(0, 12);
    return record;
  };
  if (owner && owner.fragment) {
    const file = owner.fragment, frag = readFrag(file); const r0 = frag.find((x) => x.id === testId);
    if (!r0) { report.skipped.push([testId, "owner fragment lacks the record"]); continue; }
    patchRecord(r0); pending.fragments[file] = frag; report.owned_v3_fragment.push([testId, rows.length]);
  } else {
    const clone = {}; Object.entries(rec).forEach(([k, v]) => { if (!DROP.includes(k)) clone[k] = JSON.parse(JSON.stringify(v)); });
    patchRecord(clone);
    const taskId = owner ? owner.id : "v3.remap-" + kebab(fqn.split(".").pop()).slice(0, 60);
    const file = "fragments/remap-" + taskId.replace(/^v[23]\./, "") + ".json";
    (pending.fragments[file] = pending.fragments[file] || []).push(clone);
    if (owner) { report.owned_v2.push([testId, owner.id, rows.length]); pending.tasks.push({ update: owner.id, fragment: file }); }
    else {
      const testFile = "src/test/java/" + fqn.replace(/\./g, "/") + ".java";
      pending.tasks.push({ create: { id: taskId, kind: "maven", selectors: [fqn], inputs: [testFile, ...(rec.prod || []).filter((p) => fs.existsSync(ROOT + p))], scope: rows.map((r) => ({ processId: r.proc, stageId: r.stage })), oracle: "Reutiliza la evidencia PASS ya capturada del test " + fqn.split(".").pop() + " y le añade el mapeo honesto de etapas verificado aserción a aserción.", expected: "Surefire: la clase sin Failures/Errors; el fragmento solo añade mapeos de etapas.", fragment: file, remapOnly: true, quarantinable: false, priority: 8, status: "ACTIVE" } });
      report.orphan_task.push([testId, taskId, rows.length]);
    }
  }
}
console.log(JSON.stringify({ tests: byTest.size, rows: remaps.length, ownedV3: report.owned_v3_fragment.length, ownedV2: report.owned_v2.length, orphanTasks: report.orphan_task.length, skipped: report.skipped }, null, 1));
if (!apply) process.exit(0);
const seenTask = new Set();
Object.entries(pending.fragments).forEach(([file, frag]) => { fs.mkdirSync(path.dirname(path.join(V3, file)), { recursive: true }); fs.writeFileSync(path.join(V3, file), JSON.stringify(frag, null, 2), "utf8"); });
pending.tasks.forEach((t) => {
  if (t.update) { const task = manifest.tasks.find((x) => x.id === t.update); if (task) { task.fragment = t.fragment; task.remapFragment = true; } }
  else if (t.create && !seenTask.has(t.create.id)) { seenTask.add(t.create.id); manifest.tasks = manifest.tasks.filter((x) => x.id !== t.create.id).concat([t.create]); }
});
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 1) + "\n", "utf8");
console.log("applied: fragments", Object.keys(pending.fragments).length, "tasks touched", pending.tasks.length);
