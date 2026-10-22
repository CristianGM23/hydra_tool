#!/usr/bin/env node
/* V3: demuestra que ninguna etapa funcional queda fuera de la evaluación oficial y explica 970 registros vs 980 etapas.
   Solo lectura. Uso: node verify-universe.js [--json <salida>] */
"use strict";
const fs = require("fs"), path = require("path");
const KT = path.resolve(__dirname, "..");
const { HM } = require(path.join(KT, "portal-model-loader.js"))();
const rd = (f) => JSON.parse(fs.readFileSync(path.join(KT, f), "utf8"));
const mx = rd("test-evidence-matrix.json"), cat = rd("test-catalog.json").tests, ex = rd("test-execution-state.json").tests, gaps = rd("test-gaps.json");
const out = { problems: [] };
// 1) universo funcional: nodos de proceso que no son start/end
const stages = [], skipped = { start: 0, end: 0 };
HM.procs.forEach((p) => p.nodes.forEach((n) => { if (n.t === "start" || n.t === "end") { skipped[n.t]++; return; } stages.push({ proc: p.id, stage: n.id, type: n.t, scope: p.scope, cap: p.cap || null }); }));
out.processes = HM.procs.length; out.nodes = HM.procs.reduce((a, p) => a + p.nodes.length, 0); out.excludedStartEnd = skipped; out.functionalStages = stages.length;
// 2) cada etapa tiene entrada en la matriz oficial, y la matriz no contiene etapas inexistentes
const inMx = new Set();
Object.entries(mx.procs).forEach(([pid, P]) => Object.keys(P.stages).forEach((sid) => inMx.add(pid + "." + sid)));
const inModel = new Set(stages.map((s) => s.proc + "." + s.stage));
const missing = [...inModel].filter((k) => !inMx.has(k)), extra = [...inMx].filter((k) => !inModel.has(k));
out.matrixStages = inMx.size; out.stagesMissingFromMatrix = missing; out.matrixStagesNotInModel = extra;
if (missing.length) out.problems.push(missing.length + " etapas del modelo sin entrada en la matriz");
if (extra.length) out.problems.push(extra.length + " entradas de la matriz sin etapa en el modelo");
const procsMissing = HM.procs.filter((p) => !mx.procs[p.id]).map((p) => p.id); out.processesMissingFromMatrix = procsMissing;
if (procsMissing.length) out.problems.push("procesos sin matriz: " + procsMissing.join(","));
// 3) todas las etapas tienen estado de fuerza (best) y estado (s)
const noState = stages.filter((s) => { const x = (mx.procs[s.proc] || { stages: {} }).stages[s.stage]; return !x || !x.s || !("best" in x); }).map((s) => s.proc + "." + s.stage);
out.stagesWithoutState = noState; if (noState.length) out.problems.push(noState.length + " etapas sin estado");
// 4) 970 registros: unidad distinta (pruebas), no etapas
const byKind = {}; cat.forEach((t) => { const k = t.id.split(":")[0]; byKind[k] = (byKind[k] || 0) + 1; });
out.catalogRecords = cat.length; out.catalogRecordsByKind = byKind; out.executionStateRecords = Object.keys(ex).length;
if (cat.length !== Object.keys(ex).length) out.problems.push("catálogo y estado de ejecución difieren en tamaño");
const idsCat = new Set(cat.map((t) => t.id)), orphanExec = Object.keys(ex).filter((i) => !idsCat.has(i)), noExec = [...idsCat].filter((i) => !(i in ex));
out.executionWithoutRecord = orphanExec.length; out.recordsWithoutExecution = noExec.length;
// registros que mapean etapas (directo/indirecto) vs registros sin ninguna etapa
let withProcs = 0, withoutProcs = 0; const mapped = new Set(), directStages = new Set();
cat.forEach((t) => { if ((t.procs || []).length) withProcs++; else withoutProcs++; (t.procs || []).forEach((p) => (p.stages || []).forEach((s) => { mapped.add(p.id + "." + s); if (p.match === "direct") directStages.add(p.id + "." + s); })); });
out.recordsMappingStages = withProcs; out.recordsMappingNoStage = withoutProcs;
out.stagesMappedByAnyRecord = [...mapped].filter((k) => inModel.has(k)).length; out.stagesMappedDirectly = [...directStages].filter((k) => inModel.has(k)).length;
const danglingMappings = [...mapped].filter((k) => !inModel.has(k)); out.mappingsToUnknownStages = danglingMappings.length; if (danglingMappings.length) out.problems.push(danglingMappings.length + " mapeos a etapas inexistentes: " + danglingMappings.slice(0, 5).join(","));
// 5) cada etapa sin test aparece como hueco (gaps) o está explícitamente sin mapear
const best = {}; stages.forEach((s) => { const x = mx.procs[s.proc].stages[s.stage]; const b = x.best || "NONE_MAPPED"; best[b] = (best[b] || 0) + 1; });
out.strengthByStage = best; out.gapsKeys = Object.keys(gaps);
out.ok = out.problems.length === 0;
const arg = process.argv.indexOf("--json"); if (arg > 0) fs.writeFileSync(process.argv[arg + 1], JSON.stringify(out, null, 1), "utf8");
console.log(JSON.stringify(out, null, 1));
process.exit(out.ok ? 0 : 1);
