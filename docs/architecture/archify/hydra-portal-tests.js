/* HYDRA architecture portal — TESTS & EVIDENCE area (PRUEBAS Y EVIDENCIAS).
   Data: hydra-tests-data.js (window.HYDRA_TESTS, generated from knowledge/tests/*). Host: hydra-portal-app.js (window.HydraPortalCtx).
   Functional evidence coverage - which behaviours / process stages have mapped automated evidence - NOT line coverage, and never a quality score. */
(function(){
  "use strict";
  var D = window.HYDRA_TESTS || null;
  function C(){ return window.HydraPortalCtx; }
  var ST = ["PASS", "FAIL", "SKIPPED", "STALE_EVIDENCE", "NOT_EXECUTED", "DISCOVERED"];
  var GLYPH = { PASS: "✓", FAIL: "✕", SKIPPED: "↷", STALE_EVIDENCE: "⚠", NOT_EXECUTED: "◌", DISCOVERED: "○" };
  var EVG = { ok: "✓", partial: "◐", stale: "⚠", none: "○" };
  var STRENGTHS = ["STRONG", "MEDIUM", "LIMITED"];
  var L = {
    es: {
      title: "PRUEBAS Y EVIDENCIAS", intro: "Evidencia funcional automatizada de HYDRA: qué comportamientos están respaldados por pruebas y hasta dónde llega esa evidencia. No es cobertura de código ni una puntuación de calidad: el número de pruebas no equivale a calidad.",
      noData: "No hay datos de pruebas (hydra-tests-data.js).",
      st: { PASS: "Correcta", FAIL: "Con fallo", SKIPPED: "Omitida", STALE_EVIDENCE: "Evidencia obsoleta", NOT_EXECUTED: "No ejecutada", DISCOVERED: "Descubierta" },
      stHelp: { PASS: "Una ejecución real registrada terminó bien", FAIL: "Una ejecución real registrada falló", SKIPPED: "La ejecución la omitió a propósito", STALE_EVIDENCE: "Pasó antes, pero el código relevante cambió después sin una nueva ejecución", NOT_EXECUTED: "Existe, pero quedó fuera de la última ejecución registrada", DISCOVERED: "Existe; no hay evidencia fiable de ejecución asociada" },
      ev: { ok: "Evidencia automatizada disponible", partial: "Evidencia automatizada parcial", stale: "Evidencia obsoleta", none: "Sin evidencia automatizada directa mapeada" },
      evNote: "La ausencia de evidencia mapeada no significa que la funcionalidad sea incorrecta: solo que todavía no se ha asociado una prueba automatizada directa.",
      str: { STRONG: "Fuerte", MEDIUM: "Media", LIMITED: "Limitada", NONE_MAPPED: "Sin evidencia mapeada" },
      strHelp: { STRONG: "ejecución de extremo a extremo, integración o runtime real que respalda el comportamiento", MEDIUM: "pruebas de componente/comportamiento/interfaz que verifican partes importantes", LIMITED: "pruebas unitarias, estáticas o de esquema que respaldan solo una parte", NONE_MAPPED: "no hay evidencia automatizada directa mapeada" },
      type: { UNIT: "Unitaria", INTEGRATION: "Integración", BEHAVIOR: "Comportamiento", CONTRACT: "Contrato", ARCHITECTURE: "Arquitectura", RUNTIME: "Runtime", END_TO_END: "Extremo a extremo", SECURITY: "Seguridad", COMPATIBILITY: "Compatibilidad", QUALIFICATION: "Calificación", RELEASE_GATE: "Puerta de release", STATIC_VERIFICATION: "Verificación estática", SOURCE_INTEGRITY: "Integridad de fuentes", UI: "Interfaz", OTHER: "Otra" },
      views: { layers: "Por capas", matrix: "Matriz de evidencia", gates: "Calificación y release" },
      tests: "pruebas", catalogued: "catalogadas", capsWith: "capacidades con evidencia", procsWith: "procesos con evidencia", stagesWith: "etapas con evidencia", gapsT: "áreas sin evidencia directa",
      filters: "Filtros", fStatus: "Estado", fStrength: "Fuerza", fType: "Tipo", fLayer: "Capa", fCap: "Capacidad", all: "Todos", search: "Buscar prueba…", clear: "Limpiar filtros", shown: "mostradas",
      layer: "Capa", cap: "Capacidad", proc: "Proceso", stage: "Etapa", cross: "Transversales", noProc: "Sin proceso asociado",
      layerRep: "Qué representa esta capa", layerCaps: "Capacidades que contiene", behaviorsT: "Comportamientos verificados", procsEv: "Procesos con evidencia", typesT: "Tipos de prueba", latest: "Resumen del último resultado", strengthT: "Fuerza de la evidencia", partial: "Zonas verificadas parcialmente o sin verificar",
      pickSel: "Selecciona una capa, capacidad, proceso o etapa en el árbol.", pickTest: "Selecciona una prueba para ver su detalle.", noTests: "No hay pruebas que coincidan.", more: "Mostrar más",
      // detail
      d: { user: { what: "¿Qué demuestra?", part: "¿Qué parte de HYDRA respalda?", step: "¿Qué etapa comprueba?", happens: "¿Qué ocurre en el escenario probado?", result: "¿Qué resultado se confirma?", reach: "¿Hasta dónde llega esta evidencia?", last: "Última ejecución" },
        beh: "COMPORTAMIENTO VERIFICADO", rule: "REGLA FUNCIONAL COMPROBADA", scen: "ESCENARIO CUBIERTO", inputs: "ENTRADAS / PRECONDICIONES", res: "RESULTADO DEMOSTRADO", stg: "ETAPA DEL PROCESO RESPALDADA", var: "VARIANTES / RAMAS CUBIERTAS", gua: "GARANTÍA APORTADA POR ESTA EVIDENCIA", lim: "LIMITACIONES / QUÉ NO DEMUESTRA ESTA PRUEBA", exp: "RESULTADO ESPERADO", strn: "Fuerza de la evidencia", ext: "Sistemas externos",
        extV: { none: "sin dependencia externa", mocked: "simulados (mocks): no se verifica la integración real", real: "ejecutados de verdad" },
        ttype: "TIPO TÉCNICO", fw: "FRAMEWORK", cls: "PRUEBA (CLASE / FICHERO)", cases: "CASOS", setup: "PREPARACIÓN / MOCKS / FIXTURES", exe: "QUÉ EJECUTA", asr: "QUÉ AFIRMA (ASERCIONES)", prod: "CÓDIGO DE PRODUCCIÓN EJERCITADO", comps: "COMPONENTES RELACIONADOS", rel: "PRUEBAS RELACIONADAS", src: "EVIDENCIA EN EL CÓDIGO", exe2: "ÚLTIMA EJECUCIÓN", amb: "SIGNIFICADO PENDIENTE DE REVISIÓN",
        checks: "QUÉ PARTE DE HYDRA COMPRUEBA", real: "QUÉ SE EJECUTÓ DE VERDAD", notx: "QUÉ NO SE EJECUTÓ / CERTIFICÓ", gate: "PUERTA DE CALIFICACIÓN",
        when: "Cuándo", source: "Fuente", gran: "Granularidad", dur: "Duración", cnt: "Casos", ctx: "Entorno", why: "Motivo", members: "Scripts agrupados" },
      stageSec: "Evidencia de pruebas", stageOpen: "Ver la evidencia en «Pruebas y evidencias»", stageNone: "Todavía no hay una prueba automatizada directa asociada a esta etapa.", stageUser: { ok: "Esta etapa está respaldada por pruebas automatizadas.", partial: "Esta etapa tiene evidencia automatizada parcial.", stale: "La evidencia de esta etapa es anterior a cambios recientes del código.", none: "Esta parte todavía no dispone de evidencia automatizada directa." },
      capSec: "Evidencia / Pruebas", capUser: { has: "Esta funcionalidad está respaldada por pruebas automatizadas.", cover: "Las pruebas cubren la ejecución normal y determinados casos de error o límite.", none: "Esta funcionalidad todavía no dispone de evidencia automatizada directa.", lastOk: "La última ejecución registrada fue correcta.", lastFail: "La última ejecución registrada detectó un fallo en alguna prueba.", stale: "Parte de la evidencia es anterior a cambios recientes del código." },
      openArea: "Abrir en Pruebas y evidencias", procCoverage: "Cobertura por proceso (etapas con evidencia)", stagesOf: "etapas", verifiedStages: "verificadas", unmapped: "sin evidencia directa",
      gatesTitle: "Calificación y puertas de release", gatesIntro: "Cada puerta se explica en lenguaje llano: qué comprueba, qué demuestra un PASS y qué no se ejecutó ni se certifica. Un PASS no certifica lo que la propia puerta declara que no ejecutó.",
      matrixTitle: "Matriz de evidencia funcional (capa × capacidad × proceso)", matrixIntro: "Evidencia funcional, no cobertura de líneas. «Verificadas» = etapas con prueba directa correcta; «parciales» = evidencia indirecta, obsoleta o no correcta.",
      stages: "Etapas", okS: "verificadas", partS: "parciales", noneS: "sin evidencia", e2e: "Extremo a extremo", yes: "sí", no: "no", state: "Estado de la evidencia", evState: "Evidencia", validated: "Estado del repositorio validado", evidenceFrom: "Evidencia de ejecución", total: "Total",
      lastRes: { ok: "correcta", bad: "con fallo", mix: "mixta", none: "sin ejecución registrada" }, lastExec: "Último resultado", unmappedCaps: "Capacidades sin evidencia directa", e2eNo: "Capacidades sin evidencia de extremo a extremo", unitOnly: "Capacidades con solo evidencia unitaria/estática"
    },
    en: {
      title: "TESTS & EVIDENCE", intro: "Automated functional evidence for HYDRA: which behaviours are backed by tests and how far that evidence goes. This is not code coverage and not a quality score: the number of tests does not equal quality.",
      noData: "No test data (hydra-tests-data.js).",
      st: { PASS: "Pass", FAIL: "Fail", SKIPPED: "Skipped", STALE_EVIDENCE: "Stale evidence", NOT_EXECUTED: "Not executed", DISCOVERED: "Discovered" },
      stHelp: { PASS: "A real recorded execution succeeded", FAIL: "A real recorded execution failed", SKIPPED: "The execution intentionally omitted it", STALE_EVIDENCE: "It passed earlier, but relevant code changed afterwards without a new run", NOT_EXECUTED: "It exists but was outside the latest recorded execution", DISCOVERED: "It exists; no reliable execution evidence is associated" },
      ev: { ok: "Automated evidence available", partial: "Partial automated evidence", stale: "Stale evidence", none: "No direct automated evidence mapped" },
      evNote: "Missing mapped evidence does not mean the functionality is wrong: it only means no direct automated test has been mapped yet.",
      str: { STRONG: "Strong", MEDIUM: "Medium", LIMITED: "Limited", NONE_MAPPED: "None mapped" },
      strHelp: { STRONG: "end-to-end, integration or real runtime execution backs the behaviour", MEDIUM: "component / behaviour / interface tests verify important parts", LIMITED: "unit, static or schema evidence backs only part of it", NONE_MAPPED: "no direct automated evidence is mapped" },
      type: { UNIT: "Unit", INTEGRATION: "Integration", BEHAVIOR: "Behaviour", CONTRACT: "Contract", ARCHITECTURE: "Architecture", RUNTIME: "Runtime", END_TO_END: "End-to-end", SECURITY: "Security", COMPATIBILITY: "Compatibility", QUALIFICATION: "Qualification", RELEASE_GATE: "Release gate", STATIC_VERIFICATION: "Static verification", SOURCE_INTEGRITY: "Source integrity", UI: "UI", OTHER: "Other" },
      views: { layers: "By layers", matrix: "Evidence matrix", gates: "Qualification & release" },
      tests: "tests", catalogued: "catalogued", capsWith: "capabilities with evidence", procsWith: "processes with evidence", stagesWith: "stages with evidence", gapsT: "areas without direct evidence",
      filters: "Filters", fStatus: "Status", fStrength: "Strength", fType: "Type", fLayer: "Layer", fCap: "Capability", all: "All", search: "Search test…", clear: "Clear filters", shown: "shown",
      layer: "Layer", cap: "Capability", proc: "Process", stage: "Stage", cross: "Cross-cutting", noProc: "Not tied to a process",
      layerRep: "What this layer represents", layerCaps: "Capabilities it contains", behaviorsT: "Behaviours verified", procsEv: "Processes with evidence", typesT: "Test types", latest: "Latest result summary", strengthT: "Evidence strength", partial: "Partially verified or unverified areas",
      pickSel: "Select a layer, capability, process or stage in the tree.", pickTest: "Select a test to see its detail.", noTests: "No tests match.", more: "Show more",
      d: { user: { what: "What does this show?", part: "Which part of HYDRA does it support?", step: "Which step does it check?", happens: "What happens in the tested scenario?", result: "What result is confirmed?", reach: "How far does this evidence go?", last: "Latest run" },
        beh: "BEHAVIOR VERIFIED", rule: "FUNCTIONAL RULE CHECKED", scen: "SCENARIO COVERED", inputs: "INPUTS / PRECONDITIONS", res: "RESULT DEMONSTRATED", stg: "PROCESS STAGE SUPPORTED", var: "VARIANTS / BRANCHES COVERED", gua: "GUARANTEE PROVIDED BY THIS EVIDENCE", lim: "LIMITATIONS / WHAT THIS TEST DOES NOT PROVE", exp: "EXPECTED RESULT", strn: "Evidence strength", ext: "External systems",
        extV: { none: "no external dependency", mocked: "simulated (mocks): the real integration is not verified", real: "really executed" },
        ttype: "TECHNICAL TEST TYPE", fw: "FRAMEWORK", cls: "TEST (CLASS / FILE)", cases: "CASES", setup: "SETUP / MOCKS / FIXTURES", exe: "WHAT IT EXECUTES", asr: "WHAT IT ASSERTS", prod: "PRODUCTION CODE EXERCISED", comps: "RELATED COMPONENTS", rel: "RELATED TESTS", src: "SOURCE EVIDENCE", exe2: "LATEST EXECUTION", amb: "MEANING STILL TO BE REVIEWED",
        checks: "WHAT PART OF HYDRA IT CHECKS", real: "WHAT WAS REALLY EXECUTED", notx: "WHAT WAS NOT EXECUTED / CERTIFIED", gate: "QUALIFICATION GATE",
        when: "When", source: "Source", gran: "Granularity", dur: "Duration", cnt: "Cases", ctx: "Environment", why: "Reason", members: "Grouped scripts" },
      stageSec: "Test evidence", stageOpen: "View the evidence in “Tests & evidence”", stageNone: "No direct automated test is associated with this stage yet.", stageUser: { ok: "This step is backed by automated tests.", partial: "This step has partial automated evidence.", stale: "The evidence for this step predates recent code changes.", none: "This part does not yet have direct automated evidence." },
      capSec: "Evidence / Tests", capUser: { has: "This functionality is backed by automated tests.", cover: "The tests cover normal execution and certain error or edge cases.", none: "This functionality does not yet have direct automated evidence.", lastOk: "The latest recorded run was successful.", lastFail: "The latest recorded run detected a failure in some test.", stale: "Part of the evidence predates recent code changes." },
      openArea: "Open in Tests & evidence", procCoverage: "Coverage by process (stages with evidence)", stagesOf: "stages", verifiedStages: "verified", unmapped: "without direct evidence",
      gatesTitle: "Qualification and release gates", gatesIntro: "Each gate is explained in plain language: what it checks, what a PASS demonstrates and what was not executed or certified. A PASS does not certify what the gate itself declares it did not execute.",
      matrixTitle: "Functional evidence matrix (layer × capability × process)", matrixIntro: "Functional evidence, not line coverage. “Verified” = stages with a passing direct test; “partial” = indirect, stale or non-passing evidence.",
      stages: "Stages", okS: "verified", partS: "partial", noneS: "no evidence", e2e: "End-to-end", yes: "yes", no: "no", state: "Evidence state", evState: "Evidence", validated: "Validated repository state", evidenceFrom: "Execution evidence", total: "Total",
      lastRes: { ok: "passed", bad: "with a failure", mix: "mixed", none: "no recorded run" }, lastExec: "Latest result", unmappedCaps: "Capabilities without direct evidence", e2eNo: "Capabilities without end-to-end evidence", unitOnly: "Capabilities with unit/static evidence only"
    }
  };
  function u(k){ var p = k.split("."), n = L[C().state.lang]; for (var i = 0; i < p.length; i++) n = n && n[p[i]]; return n == null ? k : n; }
  function nT(n){ var es = C().state.lang === "es"; return n + " " + (n === 1 ? (es ? "prueba" : "test") : u("tests")); }
  function esc(s){ return C().esc(s); }
  function tx(t){ return C().tx(t); }
  function fld(label, html){ return html ? '<div class="fld"><b>' + esc(label) + "</b><div>" + html + "</div></div>" : ""; }
  function par(t){ var s = typeof t === "string" ? t : tx(t); return s ? "<p>" + esc(s) + "</p>" : ""; }

  // ------------------------------------------------------------------ state (persisted through the host: state.tst)
  var F = { status: {}, strength: {}, type: "", layer: "", cap: "", q: "" }, more = {};
  function S(){ var s = C().state; if (!s.tst) s.tst = { sel: { t: "layer", layer: null }, test: null, view: "layers" }; return s.tst; }
  function st(id){ return (D.exec[id] || { s: "DISCOVERED" }); }
  function stat(id){ return st(id).s; }
  function T_(id){ var r = D.tests[id]; if (!r || !r.user || !C().isUser()) return r; return Object.assign({}, r, r.user); } // USER mode: plain-language rewrites override the functional texts
  function matches(id){
    var t = T_(id), s = stat(id); if (!t) return false;
    var anyS = Object.keys(F.status).some(function(k){ return F.status[k]; }); if (anyS && !F.status[s]) return false;
    var anyT = Object.keys(F.strength).some(function(k){ return F.strength[k]; }); if (anyT && !F.strength[t.strength]) return false;
    if (F.type && t.type !== F.type) return false;
    if (F.layer && t.layer !== F.layer && (t.layers2 || []).indexOf(F.layer) < 0) return false;
    if (F.cap && (t.caps || []).indexOf(F.cap) < 0) return false;
    if (F.q) { var q = F.q.toLowerCase(), h = (tx(t.name) + " " + tx(t.behavior) + " " + (t.path || "") + " " + t.id).toLowerCase(); if (h.indexOf(q) < 0) return false; }
    return true;
  }
  function filtering(){ return F.type || F.layer || F.cap || F.q || Object.keys(F.status).some(function(k){ return F.status[k]; }) || Object.keys(F.strength).some(function(k){ return F.strength[k]; }); }
  function ids(){ return Object.keys(D.tests); }
  function count(list, f){ var o = {}; list.forEach(function(i){ var k = f(i); o[k] = (o[k] || 0) + 1; }); return o; }
  function layerName(id){ var l = D.layers.filter(function(x){ return x.id === id; })[0]; return l ? tx(l) : id; }
  function capName(id){ return C().capNameL(id); }
  function procTitle(pid){ var p = C().procById(pid); return p ? C().pTitle(p) : pid; }
  function stageTitle(pid, sid){ var p = C().procById(pid); if (!p) return sid; var n = p.nodes.filter(function(x){ return x.id === sid; })[0]; return n ? C().nodeTitle(n) : sid; }
  function badge(s){ return '<span class="tb tb-' + s + '" title="' + esc(u("stHelp." + s)) + '"><span aria-hidden="true">' + GLYPH[s] + "</span> " + esc(u("st." + s)) + "</span>"; }
  function sbadge(s){ return s ? '<span class="sb sb-' + s + '" title="' + esc(u("strHelp." + s)) + '">' + esc(u("str." + s)) + "</span>" : ""; }
  function chips(counts, order, label){ return order.filter(function(k){ return counts[k]; }).map(function(k){ return label(k, counts[k]); }).join(" "); }
  function statusChips(c){ return chips(c, ST, function(k, n){ return '<span class="tb tb-' + k + '"><span aria-hidden="true">' + GLYPH[k] + "</span> " + n + " " + esc(u("st." + k)) + "</span>"; }); }
  function lastResult(list){ var c = count(list, stat); if (!list.length) return "none"; if (c.FAIL) return "bad"; var ok = (c.PASS || 0), tot = list.length; if (ok === tot) return "ok"; if ((c.STALE_EVIDENCE || 0) + ok === tot && ok + (c.STALE_EVIDENCE || 0) > 0) return "mix"; return "mix"; }

  // ------------------------------------------------------------------ selection -> test list
  function testsFor(sel){
    var all = ids().filter(function(i){ return D.tests[i].kind !== "gate"; }), m = D.matrix;
    if (sel.t === "layer") return all.filter(function(i){ return T_(i).layer === sel.layer; });
    if (sel.t === "cap") return all.filter(function(i){ return (T_(i).caps || []).indexOf(sel.cap) >= 0; });
    if (sel.t === "np") return all.filter(function(i){ var t = T_(i); return (t.caps || []).indexOf(sel.cap) >= 0 && !(t.procs || []).length; });
    if (sel.t === "cross") return all.filter(function(i){ var t = T_(i); return t.layer === sel.layer && !(t.caps || []).length; });
    if (sel.t === "proc") { var P = m.procs[sel.proc]; var s = {}; Object.keys(P.stages).forEach(function(k){ (P.stages[k].d || []).concat(P.stages[k].i || []).forEach(function(i){ s[i] = 1; }); }); ((P.processLevel || {}).d || []).concat((P.processLevel || {}).i || []).forEach(function(i){ s[i] = 1; }); return Object.keys(s); }
    if (sel.t === "stage") { var X = m.procs[sel.proc].stages[sel.stage] || {}; return (X.d || []).concat(X.i || []); }
    return [];
  }
  function sevOrder(i){ var t = T_(i); return -({ STRONG: 3, MEDIUM: 2, LIMITED: 1 }[t.strength] || 0) * 10 + ST.indexOf(stat(i)); }

  // ------------------------------------------------------------------ tree
  function nodeCounts(list){ var f = list.filter(matches); return { n: f.length, c: count(f, stat) }; }
  function treeRow(cls, key, label, sub, glyph, active, depth){ return '<button type="button" class="tr tr-d' + depth + (active ? " active" : "") + '" data-tst-sel="' + esc(key) + '"><span class="tr-g" aria-hidden="true">' + (glyph || "") + '</span><span class="tr-l">' + label + '</span><span class="tr-s">' + (sub || "") + "</span></button>"; }
  function tree(){
    var sel = S().sel, h = "", m = D.matrix, open = S().open || (S().open = {});
    D.layers.forEach(function(Ly){
      var lm = m.layers.filter(function(x){ return x.id === Ly.id; })[0], layerTests = ids().filter(function(i){ return D.tests[i].kind !== "gate" && T_(i).layer === Ly.id; }), nc = nodeCounts(layerTests);
      if (filtering() && !nc.n) return;
      var lk = "layer:" + Ly.id, lopen = open[lk] !== false;
      h += '<div class="tr-grp">' + '<button type="button" class="tr-tog" data-tst-tog="' + lk + '" aria-expanded="' + lopen + '">' + (lopen ? "▾" : "▸") + "</button>" + treeRow("", lk, "<b>" + esc(tx(Ly)) + "</b>", esc(nT(nc.n)), "", sel.t === "layer" && sel.layer === Ly.id, 0) + "</div>";
      if (!lopen) return;
      (lm ? lm.caps : []).forEach(function(c){
        var cm = m.caps[c], ctests = testsFor({ t: "cap", cap: c }), cn = nodeCounts(ctests); if (filtering() && !cn.n) return;
        var ck = "cap:" + c, copen = !!open[ck];
        h += '<div class="tr-grp tr-i1"><button type="button" class="tr-tog" data-tst-tog="' + ck + '" aria-expanded="' + copen + '">' + (copen ? "▾" : "▸") + "</button>" + treeRow("", ck, esc(capName(c)), cn.n || "", cm.endToEnd ? "" : "", sel.t === "cap" && sel.cap === c, 1) + "</div>";
        if (!copen) return;
        cm.processes.concat(cm.crossProcesses.filter(function(p){ return m.procs[p] && (m.procs[p].ok + m.procs[p].partial + m.procs[p].stale) > 0; })).forEach(function(pid){
          var P = m.procs[pid], pk = "proc:" + pid, popen = !!open[pk], pt = nodeCounts(testsFor({ t: "proc", proc: pid })); if (filtering() && !pt.n) return;
          var pg = P.ok === P.stagesTotal && P.stagesTotal ? EVG.ok : (P.ok + P.partial + P.stale) ? EVG.partial : EVG.none;
          h += '<div class="tr-grp tr-i2"><button type="button" class="tr-tog" data-tst-tog="' + pk + '" aria-expanded="' + popen + '">' + (popen ? "▾" : "▸") + "</button>" + treeRow("", pk, esc(procTitle(pid)), P.ok + "/" + P.stagesTotal, pg, sel.t === "proc" && sel.proc === pid, 2) + "</div>";
          if (!popen) return;
          Object.keys(P.stages).forEach(function(sid){ var X = P.stages[sid]; if (filtering() && !testsFor({ t: "stage", proc: pid, stage: sid }).filter(matches).length) return; h += treeRow("", "stage:" + pid + ":" + sid, esc(stageTitle(pid, sid)), (X.d || []).length ? (X.d || []).length : "", EVG[X.s], sel.t === "stage" && sel.proc === pid && sel.stage === sid, 3); });
        });
        var np = nodeCounts(testsFor({ t: "np", cap: c })); if (np.n) h += treeRow("", "np:" + c, esc(u("noProc")), np.n, "", sel.t === "np" && sel.cap === c, 2);
      });
      var cr = nodeCounts(testsFor({ t: "cross", layer: Ly.id })); if (cr.n) h += treeRow("", "cross:" + Ly.id, esc(u("cross")), cr.n, "", sel.t === "cross" && sel.layer === Ly.id, 1);
    });
    return h || '<p class="empty">' + esc(u("noTests")) + "</p>";
  }

  // ------------------------------------------------------------------ center panel
  function distinct(list, fn, max){ var seen = {}, out = []; list.forEach(function(i){ var s = fn(i); if (s && !seen[s]) { seen[s] = 1; out.push(s); } }); return out.slice(0, max || 6); }
  function bestFirst(list){ return list.slice().sort(function(a, b){ return sevOrder(a) - sevOrder(b); }); }
  function summaryBlock(list){
    var c = count(list, stat), sc = count(list, function(i){ return T_(i).strength; }), tc = count(list, function(i){ return T_(i).type; }), h = "";
    h += '<div class="ev-sum"><div>' + statusChips(c) + "</div><div>" + STRENGTHS.filter(function(k){ return sc[k]; }).map(function(k){ return sbadge(k) + " <span class=\"dim\">" + sc[k] + "</span>"; }).join(" ") + "</div>" + (C().isTech() ? '<div class="dim">' + Object.keys(tc).map(function(k){ return esc(u("type." + k)) + " " + tc[k]; }).join(" · ") + "</div>" : "") + "</div>";
    return h;
  }
  function aggregate(list){
    var best = bestFirst(list), h = "";
    var beh = distinct(best, function(i){ return tx(T_(i).behavior); }, 5), scn = distinct(best, function(i){ return tx(T_(i).scenario); }, 4), lim = distinct(best, function(i){ return tx(T_(i).limits); }, 3);
    if (C().isUser()) { h += fld(u("d.user.what"), beh.length ? "<ul>" + beh.slice(0, 3).map(function(s){ return "<li>" + esc(s) + "</li>"; }).join("") + "</ul>" : ""); h += fld(u("d.user.reach"), lim.length ? "<ul>" + lim.slice(0, 2).map(function(s){ return "<li>" + esc(s) + "</li>"; }).join("") + "</ul>" : ""); return h; }
    h += fld(u("behaviorsT"), beh.length ? "<ul>" + beh.map(function(s){ return "<li>" + esc(s) + "</li>"; }).join("") + "</ul>" : "");
    h += fld(u("d.scen"), scn.length ? "<ul>" + scn.map(function(s){ return "<li>" + esc(s) + "</li>"; }).join("") + "</ul>" : "");
    h += fld(u("d.lim"), lim.length ? "<ul>" + lim.map(function(s){ return "<li>" + esc(s) + "</li>"; }).join("") + "</ul>" : "");
    return h;
  }
  function testCard(id){
    var t = T_(id), s = stat(id), act = S().test === id;
    return '<button type="button" class="tcard2' + (act ? " active" : "") + '" data-tst-test="' + esc(id) + '"><span class="tc2-h">' + badge(s) + " " + sbadge(t.strength) + (C().isTech() ? ' <span class="kind-tag">' + esc(u("type." + t.type)) + "</span>" : "") + '</span><span class="tc2-n">' + esc(C().isTech() && t.kind === "suite" ? tx(t.name) + "  ·  " + (t.path || "").split("/").pop() : tx(t.name)) + '</span><span class="tc2-b dim">' + esc(tx(t.behavior)) + "</span></button>";
  }
  function testList(list, key){
    var f = bestFirst(list.filter(matches)), lim = (more[key] || 40), h = '<div class="dim tl-count">' + f.length + " / " + list.length + " " + esc(u("shown")) + "</div>";
    if (!f.length) return h + '<p class="empty">' + esc(u("noTests")) + "</p>";
    h += '<div class="tgrid2">' + f.slice(0, lim).map(testCard).join("") + "</div>";
    if (f.length > lim) h += '<button type="button" class="ghost-btn small" data-tst-more="' + esc(key) + '">' + esc(u("more")) + " (" + (f.length - lim) + ")</button>";
    return h;
  }
  function stageRows(pid){
    var P = D.matrix.procs[pid], h = '<table class="tbl stg-tbl"><tbody>';
    Object.keys(P.stages).forEach(function(sid){ var X = P.stages[sid], dn = (X.d || []).length, inn = (X.i || []).length;
      h += '<tr><td><button type="button" class="link" data-tst-sel="stage:' + esc(pid) + ":" + esc(sid) + '"><span aria-hidden="true">' + EVG[X.s] + "</span> " + esc(stageTitle(pid, sid)) + "</button></td><td>" + esc(u("ev." + X.s)) + "</td><td>" + (dn ? esc(nT(dn)) : "") + (inn ? ' <span class="dim">+' + inn + " ~</span>" : "") + "</td><td>" + (X.best && X.best !== "NONE_MAPPED" ? sbadge(X.best) : "") + "</td></tr>"; });
    return h + "</tbody></table>";
  }
  function center(){
    var sel = S().sel, m = D.matrix, h = "", list = [], title = "", key = JSON.stringify(sel);
    if (sel.t === "layer") {
      var Ly = D.layers.filter(function(x){ return x.id === sel.layer; })[0]; if (!Ly) return '<p class="empty">' + esc(u("pickSel")) + "</p>";
      list = testsFor(sel); var lm = m.layers.filter(function(x){ return x.id === sel.layer; })[0];
      h += "<h3>" + esc(tx(Ly)) + "</h3>" + (Ly.note ? '<p class="dim">' + esc(Ly.note) + "</p>" : "");
      h += fld(u("layerCaps"), lm && lm.caps.length ? '<div class="chips">' + lm.caps.map(function(c){ return '<button type="button" class="chip chip-btn" data-tst-sel="cap:' + c + '">' + esc(capName(c)) + " · " + m.caps[c].tests.length + "</button>"; }).join("") + "</div>" : "");
      h += summaryBlock(list) + aggregate(list);
      var procsEv = []; (lm ? lm.caps : []).forEach(function(c){ m.caps[c].processes.forEach(function(p){ if (m.procs[p].ok + m.procs[p].partial + m.procs[p].stale) procsEv.push(p); }); });
      h += fld(u("procsEv"), procsEv.length ? '<div class="chips">' + procsEv.slice(0, 40).map(function(p){ return '<button type="button" class="chip chip-btn" data-tst-sel="proc:' + p + '">' + esc(procTitle(p)) + " " + m.procs[p].ok + "/" + m.procs[p].stagesTotal + "</button>"; }).join("") + "</div>" : "");
      var unv = []; (lm ? lm.caps : []).forEach(function(c){ if (D.gaps.capabilitiesWithoutEvidence.indexOf(c) >= 0) unv.push(capName(c) + " — " + u("ev.none")); else if (D.gaps.capabilitiesUnitOnly.indexOf(c) >= 0) unv.push(capName(c) + " — " + u("str.LIMITED")); else if (D.gaps.capabilitiesWithoutEndToEnd.indexOf(c) >= 0) unv.push(capName(c) + " — " + u("e2eNo")); });
      h += fld(u("partial"), unv.length ? "<ul>" + unv.map(function(s){ return "<li>" + esc(s) + "</li>"; }).join("") + "</ul>" : "") + '<p class="dim">' + esc(u("evNote")) + "</p>";
    } else if (sel.t === "cap" || sel.t === "np") {
      list = testsFor(sel); var cm = m.caps[sel.cap];
      h += "<h3>" + esc(capName(sel.cap)) + (sel.t === "np" ? " — " + esc(u("noProc")) : "") + "</h3>" + summaryBlock(list) + aggregate(list);
      if (sel.t === "cap") h += fld(u("procCoverage"), '<table class="tbl"><tbody>' + cm.processes.map(function(p){ var P = m.procs[p]; return '<tr><td><button type="button" class="link" data-tst-sel="proc:' + p + '">' + esc(procTitle(p)) + "</button></td><td><span class=\"evbar\" title=\"" + P.ok + " / " + P.partial + " / " + P.none + "\"><i class=\"ok\" style=\"flex:" + P.ok + "\"></i><i class=\"pa\" style=\"flex:" + (P.partial + P.stale) + "\"></i><i class=\"no\" style=\"flex:" + P.none + "\"></i></span></td><td>" + P.ok + " / " + P.stagesTotal + " " + esc(u("verifiedStages")) + "</td></tr>"; }).join("") + "</tbody></table>");
    } else if (sel.t === "cross") {
      list = testsFor(sel); h += "<h3>" + esc(layerName(sel.layer)) + " — " + esc(u("cross")) + "</h3>" + summaryBlock(list) + aggregate(list);
    } else if (sel.t === "proc") {
      var P0 = m.procs[sel.proc]; list = testsFor(sel);
      h += "<h3>" + esc(procTitle(sel.proc)) + "</h3>" + '<p class="dim">' + esc(capName(P0.cap || "")) + " · " + P0.ok + " / " + P0.stagesTotal + " " + esc(u("verifiedStages")) + "</p>" + summaryBlock(list) + stageRows(sel.proc) + aggregate(list);
    } else if (sel.t === "stage") {
      var X = (m.procs[sel.proc].stages[sel.stage] || {}); list = testsFor(sel);
      h += "<h3>" + esc(stageTitle(sel.proc, sel.stage)) + '</h3><p class="dim">' + esc(procTitle(sel.proc)) + '</p><p><span class="evi evi-' + X.s + '"><span aria-hidden="true">' + EVG[X.s] + "</span> " + esc(u("ev." + X.s)) + "</span> " + (X.best && X.best !== "NONE_MAPPED" ? sbadge(X.best) : "") + "</p>" + (X.s === "none" ? '<p class="dim">' + esc(u("stageNone")) + " " + esc(u("evNote")) + "</p>" : "") + summaryBlock(list) + aggregate(list);
    }
    return h + '<h4 class="tl-h">' + esc(u("tests")) + "</h4>" + testList(list, key);
  }

  // ------------------------------------------------------------------ detail (audience-aware)
  function execBlock(id){
    var e = st(id), t = T_(id), h = "";
    h += '<p>' + badge(e.s) + (e.was ? ' <span class="dim">(' + esc(e.was) + ")</span>" : "") + "</p>";
    if (e.staleBecause) h += '<p class="dim">' + esc(u("d.why")) + ": " + esc(e.staleBecause) + "</p>";
    if (C().isTech()) {
      h += '<ul class="kv">' + (e.ts ? "<li><b>" + esc(u("d.when")) + ":</b> " + esc(e.ts.replace("T", " ").slice(0, 19)) + " UTC</li>" : "") + (e.src ? "<li><b>" + esc(u("d.source")) + ':</b> <span class="mono">' + esc(e.src) + "</span></li>" : "") + (e.granularity ? "<li><b>" + esc(u("d.gran")) + ":</b> " + esc(e.granularity) + "</li>" : "") + (e.durMs ? "<li><b>" + esc(u("d.dur")) + ":</b> " + (e.durMs / 1000).toFixed(1) + " s</li>" : "") +
        (e.cases ? "<li><b>" + esc(u("d.cnt")) + ":</b> " + e.cases.n + " (✓" + e.cases.pass + " ✕" + e.cases.fail + " ↷" + e.cases.skipped + ")</li>" : "") + (D.context && D.context.java ? "<li><b>" + esc(u("d.ctx")) + ":</b> " + esc([D.context.os, "JDK " + D.context.java, D.context.surefire && "surefire " + D.context.surefire].filter(Boolean).join(" · ")) + "</li>" : "") + "</ul>";
      if (e.note) h += '<p class="dim">' + esc(e.note) + "</p>";
      if (e.problems && e.problems.length) h += "<ul>" + e.problems.map(function(p){ return '<li class="mono">' + esc(p.n + (p.m ? " — " + p.m : "")) + "</li>"; }).join("") + "</ul>";
    } else if (e.problems && e.problems.length && C().isFunc()) h += "<ul>" + e.problems.slice(0, 3).map(function(p){ return "<li>" + esc(p.n) + "</li>"; }).join("") + "</ul>";
    return h;
  }
  function stagesHtml(t, withIndirect){
    return (t.procs || []).filter(function(p){ return withIndirect || p.match === "direct"; }).map(function(p){
      var stages = (p.stages || []).map(function(s){ return '<button type="button" class="link" data-tst-sel="stage:' + esc(p.id) + ":" + esc(s) + '">' + esc(stageTitle(p.id, s)) + "</button>"; }).join(", ");
      return "<li><button type=\"button\" class=\"link\" data-tst-sel=\"proc:" + esc(p.id) + '">' + esc(procTitle(p.id)) + "</button>" + (stages ? " → " + stages : "") + (p.match === "indirect" ? ' <span class="dim">(~)</span>' : "") + "</li>";
    }).join("");
  }
  function detail(){
    var id = S().test; if (!id || !T_(id)) return '<p class="empty">' + esc(u("pickTest")) + "</p>";
    var t = T_(id), e = st(id), U = C().isUser(), Fn = C().isFunc(), h = "";
    h += '<div class="insp-head"><div class="insp-kicker">' + esc(layerName(t.layer)) + (t.kind === "gate" ? " · " + esc(u("d.gate")) : "") + "</div><h3>" + esc(tx(t.name)) + '</h3><div class="insp-badges">' + badge(e.s) + " " + sbadge(t.strength) + (C().isTech() ? ' <span class="kind-tag">' + esc(u("type." + t.type)) + (t.typeNote ? " · " + esc(tx(t.typeNote)) : "") + "</span>" : "") + "</div></div><div class=\"insp-body\">";
    var capsH = (t.caps || []).map(function(c){ return '<button type="button" class="link" data-tst-sel="cap:' + c + '">' + esc(capName(c)) + "</button>"; }).join(", ");
    if (U) {
      h += fld(u("d.user.what"), par(t.behavior)) + fld(u("d.user.part"), (capsH || esc(layerName(t.layer)))) + fld(u("d.user.step"), (t.procs || []).filter(function(p){ return p.match === "direct"; }).length ? "<ul>" + stagesHtml(t, false) + "</ul>" : "") +
        fld(u("d.user.happens"), par(t.scenario)) + fld(u("d.user.result"), par(t.result)) + fld(u("d.user.reach"), par(t.limits)) + fld(u("d.user.last"), '<p>' + badge(e.s) + "</p>");
      return h + "</div>";
    }
    if (t.kind === "gate") h += fld(u("d.checks"), par(t.checks));
    h += fld(u("d.beh"), par(t.behavior)) + fld(u("d.rule"), par(t.rule)) + fld(u("d.scen"), par(t.scenario)) + fld(u("d.inputs"), par(t.pre)) + fld(u("d.res"), par(t.result));
    var stg = stagesHtml(t, true); h += fld(u("d.stg"), stg ? "<ul>" + stg + "</ul>" : "");
    if ((t.cases || []).length) h += fld(u("d.var"), "<ul>" + t.cases.map(function(c){ return "<li>" + (C().isTech() ? '<code class="mono">' + esc(c.n) + "</code> — " : "") + esc(tx(c.scenario)) + ' <span class="dim">→ ' + esc(tx(c.result)) + "</span></li>"; }).join("") + "</ul>");
    h += fld(u("d.gua"), par(t.guarantee)) + fld(u("d.lim"), par(t.limits));
    if (t.kind === "gate") h += fld(u("d.real"), par(t.realExecuted)) + fld(u("d.notx"), par(t.notExecuted));
    h += fld(u("d.strn"), sbadge(t.strength) + " " + esc(u("strHelp." + t.strength)) + (t.strengthWhy ? '<br><span class="dim">' + esc(tx(t.strengthWhy)) + "</span>" : "")) + fld(u("d.ext"), esc(u("d.extV." + (t.external || "none"))));
    if (t.ambiguous) h += fld(u("d.amb"), par(t.ambiguous));
    if (Fn) { h += fld(u("d.user.last"), execBlock(id)); return h + "</div>"; }
    // TECHNICAL
    h += fld(u("d.ttype"), '<span class="kind-tag">' + esc(u("type." + t.type)) + "</span>" + (t.typeNote ? " " + esc(tx(t.typeNote)) : "")) + fld(u("d.fw"), '<span class="mono">' + esc(t.framework) + "</span>") + fld(u("d.cls"), '<span class="mono">' + esc(t.path || "") + "</span>" + (t.kind === "family" ? "<ul class=\"mono\">" + (t.members || []).slice(0, 30).map(function(m){ return "<li>" + esc(m.replace(/^script:/, "")) + "</li>"; }).join("") + "</ul>" : ""));
    h += fld(u("d.setup"), par(t.setup)) + fld(u("d.exe"), par(t.executes)) + fld(u("d.asr"), par(t.asserts));
    if (t.script) h += fld(u("d.source"), '<span class="mono">' + esc(t.script) + "</span>" + (t.command ? '<br><span class="mono dim">' + esc(t.command) + "</span>" : ""));
    if ((t.prod || []).length) h += fld(u("d.prod"), '<ul class="mono">' + t.prod.map(function(p){ return "<li>" + esc(p) + "</li>"; }).join("") + "</ul>");
    if ((t.prodComponents || []).length) h += fld(u("d.comps"), t.prodComponents.slice(0, 10).map(function(pc){ var a = pc.split(":"); return '<button type="button" class="link" data-go-comp="' + esc(a[0] + ":" + a[1]) + '">' + esc(a[1]) + "</button>"; }).join(" · "));
    if ((t.related || []).length) h += fld(u("d.rel"), t.related.filter(function(r){ return T_(r); }).slice(0, 8).map(function(r){ return '<button type="button" class="link" data-tst-test="' + esc(r) + '">' + esc(tx(T_(r).name)) + "</button>"; }).join(" · "));
    if ((t.src || []).length) h += fld(u("d.src"), t.src.map(function(s){ return '<div class="evidence-row">' + esc(s.path + (s.line ? ":" + s.line : "")) + "</div>"; }).join(""));
    h += fld(u("d.exe2"), execBlock(id));
    return h + "</div>";
  }

  // ------------------------------------------------------------------ views: matrix, gates, overview
  function overviewCards(){
    var all = ids().filter(function(i){ return D.tests[i].kind !== "gate"; }), gates = ids().length - all.length, c = count(ids(), stat), m = D.matrix;
    var capsW = Object.keys(m.caps).filter(function(k){ return m.caps[k].tests.length; }).length, pr = Object.keys(m.procs).filter(function(p){ return m.procs[p].ok + m.procs[p].partial + m.procs[p].stale > 0; }).length, stg = 0, stgOk = 0, stgAll = 0;
    Object.keys(m.procs).forEach(function(p){ stgAll += m.procs[p].stagesTotal; stgOk += m.procs[p].ok; stg += m.procs[p].ok + m.procs[p].partial + m.procs[p].stale; });
    function card(n, l, cls){ return '<div class="ovc' + (cls ? " " + cls : "") + '"><b>' + n + "</b><span>" + esc(l) + "</span></div>"; }
    var h = '<div class="ov-cards">' + card(ids().length, u("tests") + " " + u("catalogued")) + ST.map(function(k){ return card(c[k] || 0, u("st." + k), "st-" + k); }).join("") + card(capsW + " / " + Object.keys(m.caps).length, u("capsWith")) + card(pr + " / " + Object.keys(m.procs).length, u("procsWith")) + card(stg + " / " + stgAll, u("stagesWith")) + card(stgAll - stgOk, u("gapsT")) + "</div>";
    h += '<p class="dim ev-foot">' + esc(u("intro")) + " " + esc(u("validated")) + ": <span class=\"mono\">" + esc((D.state.head || "").slice(0, 8)) + " · " + esc(D.state.branch || "") + "</span> · " + esc(u("evidenceFrom")) + ": " + esc((D.context && D.context.os) || "") + (D.context && D.context.java ? " · JDK " + esc(D.context.java) : "") + "</p>";
    return h;
  }
  function matrixView(){
    var m = D.matrix, h = "<h3>" + esc(u("matrixTitle")) + '</h3><p class="dim">' + esc(u("matrixIntro")) + "</p>";
    h += '<div class="tbl-wrap"><table class="tbl mx"><thead><tr><th>' + esc(u("layer")) + "</th><th>" + esc(u("cap")) + "</th><th>" + esc(u("tests")) + "</th><th>" + esc(u("fStatus")) + "</th><th>" + esc(u("strengthT")) + "</th><th>" + esc(u("stages")) + "</th><th>" + esc(u("e2e")) + "</th></tr></thead><tbody>";
    m.layers.forEach(function(Ly){ Ly.caps.forEach(function(c, i){ var cm = m.caps[c];
      h += "<tr>" + (i === 0 ? '<td rowspan="' + Ly.caps.length + '"><b>' + esc(layerName(Ly.id)) + "</b></td>" : "") + '<td><button type="button" class="link" data-tst-sel="cap:' + c + '" data-tst-view="layers">' + esc(capName(c)) + "</button></td><td>" + cm.tests.length + "</td><td>" + statusChips(cm.status) + "</td><td>" + (cm.tests.length ? STRENGTHS.filter(function(k){ return cm.strength[k]; }).map(function(k){ return sbadge(k) + " " + cm.strength[k]; }).join(" ") : sbadge("NONE_MAPPED")) + '</td><td><span class="evbar"><i class="ok" style="flex:' + cm.stagesOk + '"></i><i class="pa" style="flex:' + cm.stagesPartial + '"></i><i class="no" style="flex:' + Math.max(0, cm.stagesTotal - cm.stagesOk - cm.stagesPartial) + '"></i></span> <span class="dim">' + cm.stagesOk + " " + esc(u("okS")) + " · " + cm.stagesPartial + " " + esc(u("partS")) + " · " + Math.max(0, cm.stagesTotal - cm.stagesOk - cm.stagesPartial) + " " + esc(u("noneS")) + "</span></td><td>" + esc(cm.endToEnd ? u("yes") : u("no")) + "</td></tr>"; }); });
    return h + "</tbody></table></div>";
  }
  function gatesView(){
    var gates = ids().filter(function(i){ return D.tests[i].kind === "gate"; }), h = "<h3>" + esc(u("gatesTitle")) + '</h3><p class="dim">' + esc(u("gatesIntro")) + "</p>";
    var groups = {}; gates.forEach(function(i){ var k = i.split(":")[1]; (groups[k] = groups[k] || []).push(i); });
    var names = { readiness: "Release readiness (release-readiness.json)", release: "Release qualification", rc1: "RC1 qualification", rc2: "RC2 qualification" };
    Object.keys(groups).forEach(function(k){ var g = groups[k].filter(matches); h += "<h4>" + esc(names[k] || k) + " — " + statusChips(count(groups[k], stat)) + "</h4>"; h += '<div class="tgrid2">' + bestFirst(g).map(testCard).join("") + "</div>"; });
    return h;
  }
  function filterBar(){
    var h = '<div class="flt-row"><b>' + esc(u("filters")) + ":</b> ";
    h += ST.map(function(k){ return '<button type="button" class="flt" data-tst-fs="' + k + '" aria-pressed="' + !!F.status[k] + '" title="' + esc(u("stHelp." + k)) + '"><span aria-hidden="true">' + GLYPH[k] + "</span> " + esc(u("st." + k)) + "</button>"; }).join("");
    h += ' <span class="sep2"></span>' + STRENGTHS.map(function(k){ return '<button type="button" class="flt" data-tst-ft="' + k + '" aria-pressed="' + !!F.strength[k] + '" title="' + esc(u("strHelp." + k)) + '">' + esc(u("str." + k)) + "</button>"; }).join("");
    var types = {}; ids().forEach(function(i){ types[D.tests[i].type] = 1; });
    h += ' <label class="flt-sel">' + esc(u("fType")) + ' <select class="sel" data-tst-fsel="type"><option value="">' + esc(u("all")) + "</option>" + Object.keys(types).sort().map(function(k){ return '<option value="' + k + '"' + (F.type === k ? " selected" : "") + ">" + esc(u("type." + k)) + "</option>"; }).join("") + "</select></label>";
    h += ' <label class="flt-sel">' + esc(u("fLayer")) + ' <select class="sel" data-tst-fsel="layer"><option value="">' + esc(u("all")) + "</option>" + D.layers.map(function(Ly){ return '<option value="' + Ly.id + '"' + (F.layer === Ly.id ? " selected" : "") + ">" + esc(tx(Ly)) + "</option>"; }).join("") + "</select></label>";
    h += ' <label class="flt-sel">' + esc(u("fCap")) + ' <select class="sel" data-tst-fsel="cap"><option value="">' + esc(u("all")) + "</option>" + Object.keys(D.matrix.caps).map(function(c){ return '<option value="' + c + '"' + (F.cap === c ? " selected" : "") + ">" + esc(capName(c)) + "</option>"; }).join("") + "</select></label>";
    h += ' <input type="search" class="flt-q" id="tst-q" placeholder="' + esc(u("search")) + '" value="' + esc(F.q) + '">' + (filtering() ? ' <button type="button" class="ghost-btn small" data-tst-clear="1">' + esc(u("clear")) + "</button>" : "") + "</div>";
    return h;
  }

  // ------------------------------------------------------------------ section render
  function render(root){
    var Cx = C(); if (!D) { root.innerHTML = '<p class="empty">' + esc(u("noData")) + "</p>"; return; }
    var view = S().view || "layers";
    var h = '<div class="tst-wrap"><div class="tst-head"><h2>' + esc(u("title")) + '</h2><div class="seg" id="tst-views">' + ["layers", "matrix", "gates"].map(function(v){ return '<button type="button" data-tst-view="' + v + '" aria-pressed="' + (v === view) + '">' + esc(u("views." + v)) + "</button>"; }).join("") + "</div></div>" + overviewCards() + filterBar();
    if (view === "layers") h += '<div class="tst-cols"><nav class="tst-tree" id="tst-tree" aria-label="' + esc(u("layer") + " → " + u("cap") + " → " + u("proc") + " → " + u("stage")) + '">' + tree() + '</nav><section class="tst-center" id="tst-center">' + center() + '</section><aside class="tst-detail insp" id="tst-detail">' + detail() + "</aside></div>";
    else h += '<div class="tst-cols tst-cols2"><section class="tst-center tst-wide">' + (view === "matrix" ? matrixView() : gatesView()) + '</section><aside class="tst-detail insp" id="tst-detail">' + detail() + "</aside></div>";
    root.innerHTML = h + "</div>";
    root._tstRoot = true;
  }
  function refresh(){ var root = document.getElementById("view-tests"); if (!root) return; var q = document.activeElement && document.activeElement.id === "tst-q", pos = q ? document.activeElement.selectionStart : 0, ctr = root.querySelector(".tst-center"), sc = ctr ? ctr.scrollTop : 0; render(root); if (q) { var i = document.getElementById("tst-q"); if (i) { i.focus(); i.setSelectionRange(pos, pos); } } var c2 = root.querySelector(".tst-center"); if (c2) c2.scrollTop = sc; }

  // ------------------------------------------------------------------ selection plumbing
  function parseKey(k){ var p = k.split(":"); if (p[0] === "layer") return { t: "layer", layer: p[1] }; if (p[0] === "cap") return { t: "cap", cap: p[1] }; if (p[0] === "np") return { t: "np", cap: p[1] }; if (p[0] === "cross") return { t: "cross", layer: p[1] }; if (p[0] === "proc") return { t: "proc", proc: p[1] }; if (p[0] === "stage") return { t: "stage", proc: p[1], stage: p.slice(2).join(":") }; return { t: "layer", layer: null }; }
  function expandFor(sel){ var o = S().open || (S().open = {}), m = D.matrix; if (sel.t === "proc" || sel.t === "stage") { var cap = m.procs[sel.proc] && m.procs[sel.proc].cap; if (cap) { o["layer:" + m.caps[cap].layer] = true; o["cap:" + cap] = true; } o["proc:" + sel.proc] = true; } if (sel.t === "cap" || sel.t === "np") { o["layer:" + m.caps[sel.cap].layer] = true; o["cap:" + sel.cap] = true; } }
  function select(sel){ S().sel = sel; S().view = "layers"; expandFor(sel); more = {}; }
  function openAt(key, testId){ if (!D) return; var s = S(); select(parseKey(key)); if (testId) s.test = testId; C().state.section = "tests"; C().renderAll(); window.scrollTo(0, 0); }
  function wire(){
    document.addEventListener("click", function(e){
      var t = e.target, b;
      if ((b = t.closest("[data-tst-sel]")) && b.closest("#view-tests")) { if (b.dataset.tstView) S().view = b.dataset.tstView; select(parseKey(b.dataset.tstSel)); C().save && C().save(); refresh(); C().urlSync && C().urlSync(); return; }
      if ((b = t.closest("[data-tst-tog]"))) { var o = S().open || (S().open = {}), k = b.dataset.tstTog; o[k] = k.indexOf("layer:") === 0 ? o[k] === false : !o[k]; refresh(); return; }
      if ((b = t.closest("[data-tst-test]"))) { S().test = b.dataset.tstTest; refresh(); C().urlSync && C().urlSync(); return; }
      if ((b = t.closest("[data-tst-view]")) && b.closest("#view-tests")) { S().view = b.dataset.tstView; refresh(); return; }
      if ((b = t.closest("[data-tst-fs]"))) { F.status[b.dataset.tstFs] = !F.status[b.dataset.tstFs]; refresh(); return; }
      if ((b = t.closest("[data-tst-ft]"))) { F.strength[b.dataset.tstFt] = !F.strength[b.dataset.tstFt]; refresh(); return; }
      if ((b = t.closest("[data-tst-more]"))) { more[b.dataset.tstMore] = (more[b.dataset.tstMore] || 40) + 80; refresh(); return; }
      if (t.closest("[data-tst-clear]")) { F = { status: {}, strength: {}, type: "", layer: "", cap: "", q: "" }; refresh(); return; }
      if ((b = t.closest("[data-tests-go]"))) { var p = b.dataset.testsGo; openAt(p); return; }
    });
    document.addEventListener("change", function(e){ var s = e.target.closest && e.target.closest("[data-tst-fsel]"); if (s) { F[s.dataset.tstFsel] = s.value; refresh(); } });
    document.addEventListener("input", function(e){ if (e.target.id === "tst-q") { F.q = e.target.value; refresh(); } });
  }
  if (document.readyState !== "loading") wire(); else document.addEventListener("DOMContentLoaded", wire);

  // ------------------------------------------------------------------ hooks used by the host (capability tab, process stages)
  function stageState(procId, stageId){ if (!D) return null; var P = D.matrix.procs[procId]; if (!P || !P.stages[stageId]) return null; return P.stages[stageId].s; }
  function stageTitleAttr(s){ return u("ev." + s); }
  function capabilityTab(capId){
    if (!D) return '<p class="empty">' + esc(u("noData")) + "</p>";
    var m = D.matrix, cm = m.caps[capId]; if (!cm) return "";
    var list = cm.tests.filter(function(i){ return D.tests[i].kind !== "gate"; }), c = count(list, stat), h = '<div class="ov-grid">';
    var last = lastResult(list), btn = '<p><button type="button" class="ghost-btn" data-tests-go="cap:' + capId + '">' + esc(u("openArea")) + " →</button></p>";
    if (C().isUser()) {
      h += '<div class="lvl-card span-all"><div class="lvl-tag">' + esc(u("capSec")) + "</div>";
      if (!list.length) h += "<p class=\"tagline\">" + esc(u("capUser.none")) + "</p>";
      else h += '<p class="tagline">' + esc(u("capUser.has")) + "</p><p>" + esc(u("capUser.cover")) + "</p><p>" + esc(last === "ok" ? u("capUser.lastOk") : last === "bad" ? u("capUser.lastFail") : "") + (c.STALE_EVIDENCE ? " " + esc(u("capUser.stale")) : "") + "</p>" + aggregateInline(list, 3);
      h += '<p class="dim">' + esc(u("evNote")) + "</p>" + btn + "</div>";
    } else {
      h += '<div class="lvl-card span-all"><div class="lvl-tag">' + esc(u("capSec")) + "</div>" + '<div class="ev-sum"><div>' + statusChips(c) + "</div><div>" + STRENGTHS.filter(function(k){ return cm.strength[k]; }).map(function(k){ return sbadge(k) + ' <span class="dim">' + cm.strength[k] + "</span>"; }).join(" ") + "</div></div>" +
        "<p>" + cm.stagesOk + " / " + cm.stagesTotal + " " + esc(u("verifiedStages")) + " · " + esc(u("e2e")) + ": " + esc(cm.endToEnd ? u("yes") : u("no")) + '</p>' + aggregate(list) + btn + "</div>";
      h += '<div class="lvl-card span-all"><h4>' + esc(u("procCoverage")) + '</h4><table class="tbl"><tbody>' + cm.processes.map(function(p){ var P = m.procs[p]; return '<tr><td><button type="button" class="link" data-tests-go="proc:' + p + '">' + esc(procTitle(p)) + '</button></td><td><span class="evbar"><i class="ok" style="flex:' + P.ok + '"></i><i class="pa" style="flex:' + (P.partial + P.stale) + '"></i><i class="no" style="flex:' + P.none + '"></i></span></td><td>' + P.ok + " / " + P.stagesTotal + "</td></tr>"; }).join("") + "</tbody></table></div>";
      if (C().isTech()) h += '<div class="lvl-card span-all"><h4>' + esc(u("tests")) + "</h4><div class=\"tbl-wrap\"><table class=\"tbl\"><tbody>" + bestFirst(list).map(function(i){ var t = T_(i); return '<tr><td>' + badge(stat(i)) + "</td><td>" + sbadge(t.strength) + '</td><td><span class="kind-tag">' + esc(u("type." + t.type)) + '</span></td><td><button type="button" class="link" data-tests-go="cap:' + capId + '" data-tst-test="' + esc(i) + '">' + esc(tx(t.name)) + '</button></td><td class="mono dim">' + esc(t.path || "") + "</td></tr>"; }).join("") + "</tbody></table></div></div>";
    }
    return h + "</div>";
  }
  function aggregateInline(list, n){ var beh = distinct(bestFirst(list), function(i){ return tx(T_(i).behavior); }, n); return beh.length ? "<ul>" + beh.map(function(s){ return "<li>" + esc(s) + "</li>"; }).join("") + "</ul>" : ""; }
  function stageInspector(procId, stageId){
    if (!D) return ""; var P = D.matrix.procs[procId], X = P && P.stages[stageId]; if (!X) return "";
    var all = (X.d || []).concat(X.i || []).filter(function(i){ return T_(i); }), best = bestFirst(all), h = '<div class="fld ev-stage"><b>' + esc(u("stageSec")) + "</b><div>";
    h += '<p><span class="evi evi-' + X.s + '"><span aria-hidden="true">' + EVG[X.s] + "</span> " + esc(u("ev." + X.s)) + "</span> " + (X.best && X.best !== "NONE_MAPPED" && !C().isUser() ? sbadge(X.best) : "") + "</p>";
    if (C().isUser()) h += "<p>" + esc(u("stageUser." + X.s)) + "</p>";
    else if (!all.length) h += '<p class="dim">' + esc(u("stageNone")) + " " + esc(u("evNote")) + "</p>";
    else {
      var beh = distinct(best, function(i){ return tx(T_(i).behavior); }, 3), lim = distinct(best, function(i){ return tx(T_(i).limits); }, 2);
      if (!C().isTech()) h += fld(u("d.beh"), "<ul>" + beh.map(function(s){ return "<li>" + esc(s) + "</li>"; }).join("") + "</ul>") + fld(u("d.lim"), "<ul>" + lim.map(function(s){ return "<li>" + esc(s) + "</li>"; }).join("") + "</ul>");
      h += fld(u("tests"), "<ul class=\"ev-tl\">" + best.slice(0, C().isTech() ? 8 : 4).map(function(i){ var t = T_(i); return "<li>" + badge(stat(i)) + ' <button type="button" class="link" data-tests-go="stage:' + procId + ":" + stageId + '" data-tst-test="' + esc(i) + '">' + esc(tx(t.name)) + "</button>" + (C().isTech() ? ' <span class="kind-tag">' + esc(u("type." + t.type)) + '</span> <span class="mono dim">' + esc((t.path || "").split("/").pop()) + "</span>" : "") + "</li>"; }).join("") + "</ul>");
    }
    h += '<p><button type="button" class="ghost-btn small" data-tests-go="stage:' + procId + ":" + stageId + '">' + esc(u("stageOpen")) + " →</button></p></div></div>";
    return h;
  }
  function urlParams(){ var s = C().state.tst; if (!s) return {}; var k = s.sel; var key = k.t === "layer" ? "layer:" + (k.layer || "") : k.t === "cap" || k.t === "np" ? k.t + ":" + k.cap : k.t === "cross" ? "cross:" + k.layer : k.t === "proc" ? "proc:" + k.proc : k.t === "stage" ? "stage:" + k.proc + ":" + k.stage : ""; var o = { tn: key }; if (s.test) o.tt = s.test; if (s.view && s.view !== "layers") o.tv = s.view; return o; }
  function restore(p){ if (!D) return; var s = S(); if (p.tv && ["layers", "matrix", "gates"].indexOf(p.tv) >= 0) s.view = p.tv; if (p.tn) { try { select(parseKey(p.tn)); } catch (e) {} } if (p.tt && D.tests[p.tt]) s.test = p.tt; }
  function defaults(){ if (!D) return; var s = S(); if (!s.sel || (s.sel.t === "layer" && !s.sel.layer)) { var first = D.layers.filter(function(l){ return D.matrix.layers.some(function(x){ return x.id === l.id && x.tests; }); })[0]; s.sel = { t: "layer", layer: first ? first.id : D.layers[0].id }; } }
  window.HydraTestsUI = { render: function(root){ defaults(); render(root); }, capabilityTab: capabilityTab, stageState: stageState, stageTitle: stageTitleAttr, stageInspector: stageInspector, urlParams: urlParams, restore: restore, openAt: openAt, data: function(){ return D; }, glyph: EVG, legend: function(){ return [["ok", u("ev.ok")], ["partial", u("ev.partial")], ["stale", u("ev.stale")], ["none", u("ev.none")]]; } };
})();
