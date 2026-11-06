#!/usr/bin/env node
/* Validates semantic test-evidence records (fragments authored by analysts, or the merged catalog).
   Usage: node validate-fragment.js <fragment.json> [--discovery <discovery.json>]   (exit 1 on errors)
   Record schema (all text fields are {es,en}, both languages required, <= 380 chars each):
     id            must exist in the discovery (java:/vitest:/pw:/nodetest:/script:/gate:...)
     name          short display name
     type          UNIT|INTEGRATION|BEHAVIOR|CONTRACT|ARCHITECTURE|RUNTIME|END_TO_END|SECURITY|COMPATIBILITY|QUALIFICATION|RELEASE_GATE|STATIC_VERIFICATION|SOURCE_INTEGRITY|UI|OTHER
     typeNote      optional technical qualifier shown only in TECHNICAL mode (e.g. "API contract test")
     layer         definition|behavior|delivery|platform|external|quality   (primary layer)      layers2: other layers touched
     caps          capability ids (HM.caps)       procs: [{id, stages:[node ids], match:"direct"|"indirect"}]  (only where the evidence supports it)
     prod          production source files exercised (existing repo paths, <= 10)
     pre           preconditions (text) or null
     executes      WHAT IT EXECUTES (technical, real execution path)         asserts   WHAT IT ASSERTS (technical)
     behavior      COMPORTAMIENTO VERIFICADO / BEHAVIOR VERIFIED (plain functional language)
     rule          REGLA FUNCIONAL COMPROBADA / FUNCTIONAL RULE CHECKED or null
     scenario      ESCENARIO CUBIERTO / SCENARIO COVERED      result   RESULTADO DEMOSTRADO / RESULT DEMONSTRATED
     expected      EXPECTED RESULT (what a passing run means)
     guarantee     GARANTÍA APORTADA / GUARANTEE PROVIDED (conservative)      limits   LIMITACIONES / WHAT THIS TEST DOES NOT PROVE (mandatory)
     strength      STRONG|MEDIUM|LIMITED      strengthWhy  one short sentence      external: "none"|"mocked"|"real"
     cases         up to 8 notable cases [{n:<method/case name>, scenario:{es,en}, result:{es,en}}] (when relevant)
     related       ids of related tests      src: [{path,line}] assertion / setup lines      ambiguous: null | {es,en}
     setup         optional {es,en} mocks / fixtures / arrangement     confidence: "ANALYZED"|"SKIMMED"
     families:     id "family:<slug>" + members:[discovery ids] groups many near-identical historical scripts into ONE record (use only for scripts whose individual meaning is the same kind of check)
     gates only:   checks (what part of HYDRA), realExecuted, notExecuted ({es,en} each) */
"use strict";
const fs = require("fs"), path = require("path");
const { HM } = require("./portal-model-loader.js")();
const ROOT = path.resolve(__dirname, "..", "..", "..", "..", "..");
const TYPES = "UNIT INTEGRATION BEHAVIOR CONTRACT ARCHITECTURE RUNTIME END_TO_END SECURITY COMPATIBILITY QUALIFICATION RELEASE_GATE STATIC_VERIFICATION SOURCE_INTEGRITY UI OTHER".split(" ");
const LAYERS = Object.keys(HM.groups).concat(["quality"]), STRENGTH = ["STRONG", "MEDIUM", "LIMITED"], EXT = ["none", "mocked", "real"];
const TEXT = ["name", "executes", "asserts", "behavior", "scenario", "result", "expected", "guarantee", "limits"];
const PLAIN = ["name", "behavior", "rule", "scenario", "result", "expected", "guarantee", "limits", "pre"]; // plain-language fields: the word "contract" must not carry the meaning
const procNodes = {}; HM.procs.forEach((p) => { procNodes[p.id] = new Set(p.nodes.map((n) => n.id)); });
function validate(recs, disc) {
  const errors = [], warns = [], ids = disc ? new Set(disc.suites.map((s) => s.id).concat(disc.gates.map((g) => g.id))) : null, seen = new Set();
  const isTxt = (v) => v && typeof v === "object" && typeof v.es === "string" && typeof v.en === "string" && v.es.trim() && v.en.trim();
  recs.forEach((r, i) => {
    const w = (m) => errors.push(`${r.id || "#" + i}: ${m}`), wn = (m) => warns.push(`${r.id || "#" + i}: ${m}`);
    if (!r.id) { w("missing id"); return; } if (seen.has(r.id)) w("duplicate id"); seen.add(r.id);
    if (r.id.startsWith("family:")) { if (!Array.isArray(r.members) || r.members.length < 2) w("family record needs members (>=2)"); else if (ids) r.members.forEach((m) => { if (!ids.has(m)) w("family member not in discovery: " + m); }); }
    else if (ids && !ids.has(r.id)) w("id not found in discovery");
    if (!TYPES.includes(r.type)) w("bad type " + r.type);
    if (!LAYERS.includes(r.layer)) w("bad layer " + r.layer);
    (r.layers2 || []).forEach((l) => { if (!LAYERS.includes(l)) w("bad secondary layer " + l); if (l === r.layer) wn("secondary layer equals primary"); });
    (r.caps || []).forEach((c) => { if (!HM.caps[c]) w("unknown capability " + c); });
    (r.procs || []).forEach((p) => {
      if (!procNodes[p.id]) { w("unknown process " + p.id); return; }
      if (!["direct", "indirect"].includes(p.match)) w(`process ${p.id}: match must be direct|indirect`);
      (p.stages || []).forEach((s) => { if (!procNodes[p.id].has(s)) w(`process ${p.id}: unknown stage ${s}`); });
      if (p.match === "direct" && !(p.stages || []).length) wn(`process ${p.id}: direct match without stages`);
    });
    if (r.layer && r.layer !== "quality" && !(r.caps || []).length) wn("no capability mapped for a non-quality layer test");
    TEXT.forEach((k) => { if (!isTxt(r[k])) w("text field missing/invalid: " + k); else if (r[k].es.length > 380 || r[k].en.length > 380) w("text too long: " + k); });
    ["rule", "pre", "setup", "ambiguous"].forEach((k) => { if (r[k] != null && !isTxt(r[k])) w("bad optional text " + k); });
    PLAIN.forEach((k) => { const v = r[k]; if (v && /\bcontract(s|o|os)?\b|\bcontrato/i.test((v.es || "") + " " + (v.en || ""))) wn(`plain-language field "${k}" uses "contract"`); });
    ["PASS", "FAIL"].forEach((k) => { if (r.status || r.execution) w("fragments must not carry execution status"); });
    if (!STRENGTH.includes(r.strength)) w("bad strength");
    if (!EXT.includes(r.external)) w("external must be none|mocked|real");
    if (r.strength === "STRONG" && r.external === "mocked") wn("STRONG evidence with mocked external systems");
    if (r.strength === "STRONG" && r.type === "UNIT") wn("STRONG evidence for a UNIT test");
    (r.prod || []).forEach((p) => { if (!fs.existsSync(path.join(ROOT, p))) w("prod path does not exist: " + p); });
    if ((r.prod || []).length > 10) w("too many prod paths");
    (r.cases || []).forEach((c) => { if (!c.n || !isTxt(c.scenario) || !isTxt(c.result)) w("bad case " + (c.n || "?")); });
    if ((r.cases || []).length > 8) w("too many cases");
    (r.src || []).forEach((s) => { if (!s.path || !fs.existsSync(path.join(ROOT, s.path))) w("src path missing: " + (s.path || "?")); else if (s.line) { const n = fs.readFileSync(path.join(ROOT, s.path), "utf8").split("\n").length; if (s.line < 1 || s.line > n) w(`src line out of range: ${s.path}:${s.line}`); } });
    if (!(r.src || []).length) wn("no src evidence lines");
    if (r.id.startsWith("gate:")) ["checks", "realExecuted", "notExecuted"].forEach((k) => { if (!isTxt(r[k])) w("gate field missing: " + k); });
    if (r.type !== "RELEASE_GATE" && r.type !== "QUALIFICATION" && r.id.startsWith("gate:")) wn("gate record should use type RELEASE_GATE or QUALIFICATION");
  });
  return { errors, warns };
}
module.exports = validate;
if (require.main === module) {
  const f = process.argv[2], di = process.argv.indexOf("--discovery"), disc = di > 0 ? JSON.parse(fs.readFileSync(process.argv[di + 1], "utf8")) : null;
  const recs = JSON.parse(fs.readFileSync(f, "utf8")), r = validate(Array.isArray(recs) ? recs : recs.tests, disc);
  r.errors.slice(0, 60).forEach((e) => console.log("ERROR", e)); r.warns.slice(0, 40).forEach((e) => console.log("WARN ", e));
  console.log(`${(Array.isArray(recs) ? recs : recs.tests).length} records, ${r.errors.length} errors, ${r.warns.length} warnings`);
  process.exit(r.errors.length ? 1 : 0);
}
