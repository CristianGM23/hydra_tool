#!/usr/bin/env node
/* V3: instantánea del árbol de producción (src/main, frontend/src, recursos, configuración) para trazabilidad de cambios entre fases.
   Uso: node tree-snapshot.js --out <fichero.json>            guarda hashes SHA-256 por fichero
        node tree-snapshot.js --diff <fichero.json>           lista añadidos / modificados / borrados desde esa instantánea (solo lectura) */
"use strict";
const fs = require("fs"), path = require("path"), crypto = require("crypto");
const ROOT = path.resolve(__dirname, "..", "..", "..", "..", "..", "..") + path.sep;
const ROOTS = ["src/main", "frontend/src", "frontend/e2e", "pom.xml", ".mvn", "scripts/archify", "docs/architecture/archify/knowledge/tests"];
const SKIP = /(^|\/)(node_modules|dist|target|build|\.git|test-results|__pycache__)(\/|$)|\.fragments_pending|discovery-current\.json$/;
const walk = (rel, out) => { const abs = ROOT + rel; if (!fs.existsSync(abs)) return; const st = fs.statSync(abs); if (st.isFile()) { out.push(rel); return; } for (const e of fs.readdirSync(abs, { withFileTypes: true })) { const r = rel + "/" + e.name; if (SKIP.test(r)) continue; e.isDirectory() ? walk(r, out) : out.push(r); } };
const files = []; ROOTS.forEach((r) => walk(r, files));
const hash = (rel) => crypto.createHash("sha256").update(fs.readFileSync(ROOT + rel)).digest("hex");
const args = process.argv.slice(2), opt = (k) => (args.includes(k) ? args[args.indexOf(k) + 1] : null);
if (opt("--out")) { const o = {}; files.forEach((f) => { o[f] = hash(f); }); fs.mkdirSync(path.dirname(path.resolve(opt("--out"))), { recursive: true }); fs.writeFileSync(opt("--out"), JSON.stringify({ takenAt: new Date().toISOString(), files: o }, null, 1)); console.log("snapshot", files.length, "files ->", opt("--out")); }
else if (opt("--diff")) {
  const base = JSON.parse(fs.readFileSync(opt("--diff"), "utf8")).files, now = {}; files.forEach((f) => { now[f] = hash(f); });
  const added = files.filter((f) => !(f in base)), changed = files.filter((f) => f in base && base[f] !== now[f]), removed = Object.keys(base).filter((f) => !(f in now));
  console.log(JSON.stringify({ added, changed, removed }, null, 1));
} else console.log("usage: --out <file> | --diff <file>");
