#!/usr/bin/env node
/* Validates USER-mode rewrites of test-evidence texts: node validate-user.js <file.json> [--slice <slice.json>]
   Each record: {id, user:{name, behavior, scenario, result, limits}} with {es,en}, no implementation vocabulary (HM.leak), <= 300 chars. */
"use strict";
const fs = require("fs");
const { HM } = require("./portal-model-loader.js")();
const F = ["name", "behavior", "scenario", "result", "limits"];
function validate(recs, slice) {
  const errors = [], seen = new Set(), want = slice ? new Set(slice.map((s) => s.id)) : null;
  recs.forEach((r) => {
    if (!r.id) { errors.push("missing id"); return; } if (seen.has(r.id)) errors.push(r.id + ": duplicate"); seen.add(r.id);
    if (want && !want.has(r.id)) errors.push(r.id + ": not in slice");
    if (!r.user) { errors.push(r.id + ": no user object"); return; }
    F.forEach((k) => { const v = r.user[k]; if (!v || typeof v.es !== "string" || typeof v.en !== "string" || !v.es.trim() || !v.en.trim()) { errors.push(r.id + ": user." + k + " missing es/en"); return; }
      if (v.es.length > 300 || v.en.length > 300) errors.push(r.id + ": user." + k + " too long");
      ["es", "en"].forEach((l) => { const h = HM.leak(v[l]); if (h) errors.push(r.id + ": user." + k + "." + l + ' uses implementation vocabulary "' + h + '"'); if (/`|\.java\b|\.tsx?\b|\.py\b|\w+\(\)/.test(v[l])) errors.push(r.id + ": user." + k + "." + l + " contains code-like text"); }); });
  });
  if (want) want.forEach((id) => { if (!seen.has(id)) errors.push(id + ": missing"); });
  return errors;
}
module.exports = validate;
if (require.main === module) {
  const f = process.argv[2], si = process.argv.indexOf("--slice"), recs = JSON.parse(fs.readFileSync(f, "utf8")), e = validate(recs, si > 0 ? JSON.parse(fs.readFileSync(process.argv[si + 1], "utf8")) : null);
  e.slice(0, 40).forEach((x) => console.log("ERROR", x)); console.log(recs.length + " records, " + e.length + " errors"); process.exit(e.length ? 1 : 0);
}
