#!/usr/bin/env node
/* HYDRA portal content checker (no dependencies).
   Usage:  node docs/architecture/archify/knowledge/portal-check.js [--all] [--json] [file.js ...]
   With no file arguments (or --all) it loads hydra-portal-model.js + every hydra-model-*.js next to the portal.
   Exit code 1 when any ERROR is found.  Checks: bilingual text, requiredness values, cross references,
   graph sanity (start/end/reachability), USER-view implementation leakage, completeness matrix. */
"use strict";
const fs = require("fs"), path = require("path");
const root = path.resolve(__dirname, "..");
global.window = global;
global.document = undefined;
const args = process.argv.slice(2);
const asJson = args.includes("--json");
let files = args.filter(a => !a.startsWith("--"));
const load = f => { const code = fs.readFileSync(f, "utf8"); (0, eval)(code); };
load(path.join(root, "hydra-portal-model.js"));
// content files are always loaded first; lens files (audience projections) are applied afterwards
const modelFiles = fs.readdirSync(root).filter(f => /^hydra-model-.*\.js$/.test(f)).sort().map(f => path.join(root, f));
const lensArgs = files.filter(f => /hydra-lens-/.test(f));
const lensFiles = lensArgs.length ? lensArgs.map(f => path.resolve(f)) : fs.readdirSync(root).filter(f => /^hydra-lens-.*\.js$/.test(f)).sort().map(f => path.join(root, f));
const extFiles = fs.readdirSync(root).filter(f => /^hydra-ext-.*\.js$/.test(f)).sort().map(f => path.join(root, f));
const techArgs = files.filter(f => /hydra-(tech|ext)-/.test(f));
const techFiles = fs.readdirSync(root).filter(f => /^hydra-tech-.*\.js$/.test(f)).sort().map(f => path.join(root, f));
files = modelFiles.concat(extFiles, lensFiles, techFiles);
const loadedErrs = [];
files.forEach(f => { try { load(path.resolve(f)); } catch (e) { loadedErrs.push(`LOAD ${f}: ${e.message}`); } });
const HM = global.HM;
const errs = loadedErrs.slice(), warns = [];
const E = (w, m) => errs.push(`${w}: ${m}`);
const W = (w, m) => warns.push(`${w}: ${m}`);

const REQ = ["MANDATORY", "OPTIONAL", "CONDITIONAL", "ALTERNATIVE", "REPEATABLE", "DERIVED"];
const NODE_T = ["start", "end", "step", "decision", "artifact"];
const ACTORS = ["user", "auto", "ext", undefined, ""];
const EDGE_K = ["next", "opt", "cond", "alt", "loop", "event"];
const REL_T = ["required", "optional", "conditional", "alternative", "event", "producer", "external"];
const DEF_K = ["wizard", "form", "canvas", "flow", "text", "import", "property", "generated", "direct", "api"];
const MAP_REL = ["contains", "ref", "isa", "gen"];
const MAP_K = ["concept", "variant", "derived", "external"];
const capIds = Object.keys(HM.caps);
const isT = x => x && typeof x === "object" && typeof x.es === "string" && typeof x.en === "string" && x.es.trim() && x.en.trim();
const tOf = x => x ? (x.es || "") + " | " + (x.en || "") : "";
function needT(w, x, optional) { if (x == null && optional) return; if (!isT(x)) E(w, "bilingual text T(es,en) missing/empty: " + JSON.stringify(x)); }
function userText(w, x) {
  if (!x) return;
  if (Array.isArray(x)) return x.forEach(i => userText(w, i));
  if (x.es !== undefined) { const l = HM.leak(x.es) || HM.leak(x.en); if (l) E(w, `USER-visible text leaks implementation vocabulary "${l}": ${x.es.slice(0, 80)}`); }
}
const allElems = {};
capIds.forEach(c => (HM.caps[c].elements || []).forEach(e => { allElems[e.id] = c; }));
const procIds = new Set(HM.procs.map(p => p.id));

capIds.forEach(id => {
  const c = HM.caps[id], w = "cap:" + id;
  needT(w + ".name", c.name);
  if (!HM.groups[c.group]) E(w, "invalid group " + c.group);
  const u = c.user || {};
  ["tagline", "what", "why", "when"].forEach(k => { needT(w + ".user." + k, u[k]); userText(w + ".user." + k, u[k]); });
  ["canDefine", "needs", "produces", "mandatory", "optional", "conditional", "next"].forEach(k => (u[k] || []).forEach((t, i) => { needT(`${w}.user.${k}[${i}]`, t); userText(`${w}.user.${k}[${i}]`, t); }));
  (u.interacts || []).forEach((i, n) => { if (!HM.caps[i.cap] && !/^(persistence|audit|security)$/.test(i.cap)) W(w, "user.interacts unknown cap " + i.cap); });
  const f = c.func || {};
  (f.lifecycle || []).concat(f.semantics || [], f.cross || []).forEach((t, i) => needT(w + ".func[" + i + "]", t));
  (f.states || []).forEach(s => { needT(w + ".func.state.n", s.n); needT(w + ".func.state.d", s.d); });
  const t = c.tech || {}; needT(w + ".tech.runtime", t.runtime, true);
  (t.evidence || []).forEach(ev => { if (!ev.path) E(w, "tech.evidence without path"); });
  const ids = new Set();
  (c.elements || []).forEach(e => {
    const we = w + ".el:" + e.id;
    if (!e.id) E(w, "element without id"); else if (ids.has(e.id)) E(we, "duplicate element id"); ids.add(e.id);
    if (!REQ.includes(e.req)) E(we, "invalid req " + e.req);
    needT(we + ".n", e.n); needT(we + ".mean", e.mean); needT(we + ".purpose", e.purpose); needT(we + ".when", e.when, true);
    [e.n, e.mean, e.purpose, e.when].forEach(x => userText(we, x));
    (e.variants || []).forEach((v, i) => { needT(`${we}.variants[${i}].n`, v.n); needT(`${we}.variants[${i}].d`, v.d); if (v.req && !REQ.includes(v.req)) E(we, "variant req " + v.req); userText(we + ".variant", v.n); userText(we + ".variant", v.d); });
    (e.props || []).forEach((p, i) => { needT(`${we}.props[${i}].n`, p.n); needT(`${we}.props[${i}].d`, p.d); if (!REQ.includes(p.req)) E(we, `props[${i}] invalid req ${p.req}`); userText(we + ".prop", p.n); userText(we + ".prop", p.d); });
    if (e.io) { needT(we + ".io.i", e.io.i, true); needT(we + ".io.o", e.io.o, true); }
    if (e.effect) needT(we + ".effect", e.effect);
  });
  const m = c.map || { nodes: [], edges: [] }, nid = new Set();
  (m.nodes || []).forEach(n => {
    if (nid.has(n.id)) E(w + ".map", "duplicate node " + n.id); nid.add(n.id);
    needT(`${w}.map.${n.id}.n`, n.n); userText(w + ".map.n", n.n); if (n.d) { needT(w + ".map.d", n.d); userText(w + ".map.d", n.d); }
    if (!MAP_K.includes(n.k || "concept")) E(w + ".map", "node kind " + n.k);
    if (n.req && !REQ.includes(n.req)) E(w + ".map", "node req " + n.req);
    if (n.elem && !allElems[n.elem]) E(w + ".map", `node ${n.id} elem "${n.elem}" does not exist`);
  });
  (m.edges || []).forEach(e => {
    if (!nid.has(e.a) || !nid.has(e.b)) E(w + ".map", `edge ${e.a}->${e.b} refers to missing node`);
    if (!MAP_REL.includes(e.rel)) E(w + ".map", "edge rel " + e.rel);
    if (e.req && !REQ.includes(e.req)) E(w + ".map", "edge req " + e.req);
    if (e.l) { needT(w + ".map.edge.l", e.l); userText(w + ".map.edge", e.l); }
  });
  if ((m.nodes || []).length && !m.nodes.every(n => (m.edges || []).some(e => e.a === n.id || e.b === n.id))) W(w + ".map", "isolated node(s): " + m.nodes.filter(n => !(m.edges || []).some(e => e.a === n.id || e.b === n.id)).map(n => n.id).join(","));
  const dids = new Set();
  (c.define || []).forEach(d => {
    const wd = w + ".define:" + d.id; if (dids.has(d.id)) E(wd, "duplicate"); dids.add(d.id);
    if (!DEF_K.includes(d.kind)) E(wd, "invalid kind " + d.kind);
    if (d.req && !REQ.includes(d.req)) E(wd, "invalid req " + d.req);
    needT(wd + ".n", d.n); needT(wd + ".does", d.does); needT(wd + ".func", d.func, true); needT(wd + ".tech", d.tech, true);
    userText(wd, d.n); userText(wd, d.does);
    if (d.proc && !procIds.has(d.proc)) E(wd, `proc "${d.proc}" not registered`);
    (d.elems || []).forEach(x => { if (!allElems[x]) E(wd, `elem "${x}" does not exist`); });
  });
  (c.rel || []).forEach((r, i) => {
    const wr = `${w}.rel[${i}]`;
    if (!HM.caps[r.to] && !HM.overview.nodes.some(n => n.id === r.to)) E(wr, "unknown capability " + r.to);
    if (!REL_T.includes(r.type)) E(wr, "invalid type " + r.type);
    needT(wr + ".u", r.u); needT(wr + ".f", r.f, true); userText(wr, r.u);
  });
});

HM.procs.forEach(p => {
  const w = "proc:" + p.id;
  if (!["cap", "cross", "authoring"].includes(p.scope)) E(w, "invalid scope " + p.scope);
  if (p.scope !== "cross" && !HM.caps[p.cap]) E(w, "owner cap missing/unknown " + p.cap);
  if (p.scope === "cross" && (new Set((p.nodes || []).map(n => n.cap))).size < 2) E(w, "cross process touching fewer than 2 capabilities");
  needT(w + ".n", p.n); needT(w + ".d", p.d); userText(w, p.n); userText(w, p.d);
  const ids = new Set(), ns = p.nodes || [];
  ns.forEach(n => {
    const wn = `${w}.${n.id}`;
    if (ids.has(n.id)) E(wn, "duplicate node id"); ids.add(n.id);
    if (!NODE_T.includes(n.t || "step")) E(wn, "invalid t " + n.t);
    if (!ACTORS.includes(n.a)) E(wn, "invalid actor " + n.a);
    if (n.r && !REQ.includes(n.r)) E(wn, "invalid req " + n.r);
    if (!HM.caps[n.cap] && !HM.overview.nodes.some(o => o.id === n.cap)) E(wn, "unknown cap " + n.cap);
    needT(wn + ".l", n.l); if (n.u) needT(wn + ".u", n.u); needT(wn + ".need", n.need, true); needT(wn + ".out", n.out, true);
    [n.l, n.u, n.need, n.out].forEach(x => userText(wn, x));
    if (n.f) needT(wn + ".f", n.f); if (n.s) needT(wn + ".s", n.s);
    if (n.e && !allElems[n.e]) E(wn, `elem "${n.e}" does not exist`);
    if ((n.t === "step" || n.t === "decision" || !n.t) && !n.u) W(wn, "no user detail (u)");
  });
  if (!ns.some(n => n.t === "start")) E(w, "no start node"); if (!ns.some(n => n.t === "end")) E(w, "no end node");
  p.edges.forEach(e => {
    if (!ids.has(e.a) || !ids.has(e.b)) E(w, `edge ${e.a}->${e.b} refers to missing node`);
    if (!EDGE_K.includes(e.kind)) E(w, "invalid edge kind " + e.kind);
    if (e.l) { needT(w + ".edge.l", e.l); userText(w + ".edge", e.l); }
  });
  const start = ns.filter(n => n.t === "start").map(n => n.id), seen = new Set(start), q = start.slice();
  while (q.length) { const x = q.pop(); p.edges.filter(e => e.a === x).forEach(e => { if (!seen.has(e.b)) { seen.add(e.b); q.push(e.b); } }); }
  ns.forEach(n => { if (!seen.has(n.id)) E(w, `node ${n.id} unreachable from start`); });
  ns.filter(n => n.t !== "end").forEach(n => { if (!p.edges.some(e => e.a === n.id)) E(w, `node ${n.id} has no outgoing edge`); });
  ns.filter(n => n.t === "decision").forEach(n => { if (p.edges.filter(e => e.a === n.id).length < 2) E(w, `decision ${n.id} has <2 branches`); });
});

HM.overview.nodes.forEach(n => { userText("overview:" + n.id, n.nm.user); userText("overview:" + n.id, n.sb.user); ["user", "functional", "technical"].forEach(l => { if (n.lv.includes(l)) { needT(`overview:${n.id}.nm.${l}`, n.nm[l]); } }); });
HM.overview.edges.forEach(e => { userText(`overview:${e.a}->${e.b}`, e.l.user); if (!HM.overview.nodes.some(n => n.id === e.a) || !HM.overview.nodes.some(n => n.id === e.b)) E("overview", `edge ${e.a}->${e.b} refers to missing node`); });
// ---- audience lenses (FUNCTIONAL / TECHNICAL projections)
(HM.lensErrors || []).forEach(e => E("lens", e));
const lensStat = { caps: 0, capsTotal: 0, elements: 0, elementsTotal: 0, procs: 0, procsTotal: 0, nodes: 0, nodesTotal: 0, identical: 0 };
const same = (a, b) => a && b && tOf(a).toLowerCase().trim() === tOf(b).toLowerCase().trim();
capIds.forEach(id => {
  const c = HM.caps[id], w = "lens:cap:" + id; lensStat.capsTotal++;
  if (c.funcL && c.techL) lensStat.caps++; else E(w, "missing func/tech lens (HM.lens caps." + id + ".func/.tech)");
  if (c.funcL) { needT(w + ".func.title", c.funcL.title); needT(w + ".func.summary", c.funcL.summary); (c.funcL.inputs || []).concat(c.funcL.outputs || [], c.funcL.conditions || []).forEach(t => needT(w + ".func.list", t)); [c.funcL.title, c.funcL.summary].forEach(x => { const l = HM.leakFunc(tOf(x)); if (l) E(w, "FUNCTIONAL text leaks " + l); }); if (same(c.funcL.summary, (c.user || {}).what)) { lensStat.identical++; E(w, "func.summary identical to user.what"); } }
  if (c.techL) { needT(w + ".tech.title", c.techL.title); needT(w + ".tech.summary", c.techL.summary); }
  (c.elements || []).forEach(e => {
    lensStat.elementsTotal++; const we = w + ".el:" + e.id;
    if (e.nf && e.fm && e.tm) lensStat.elements++; else E(we, "missing lens nf/fm/tm");
    if (e.nf) { needT(we + ".nf", e.nf); needT(we + ".fm", e.fm); needT(we + ".tm", e.tm); [e.nf, e.fm].forEach(x => { const l = HM.leakFunc(tOf(x)); if (l) E(we, "FUNCTIONAL text leaks " + l); }); if (same(e.fm, e.mean)) { lensStat.identical++; E(we, "fm identical to user mean"); } }
    (e.variants || []).forEach((v, i) => { if (e.nf && !v.fd) E(we + ".variants[" + i + "]", "missing lens fd (functional variant description)"); if (v.fd && same(v.fd, v.d)) { lensStat.identical++; E(we + ".variants[" + i + "]", "fd identical to user d"); } });
  });
});
HM.procs.forEach(p => {
  const w = "lens:proc:" + p.id; lensStat.procsTotal++;
  if (p.nf && p.df && p.nt && p.dt) { lensStat.procs++; [p.nf, p.df, p.nt, p.dt].forEach(x => needT(w, x)); } else E(w, "missing process lens nf/df/nt/dt");
  p.nodes.forEach(n => {
    if (n.t === "start" || n.t === "end") return; lensStat.nodesTotal++;
    if (n.lf && n.sf && n.fio) lensStat.nodes++; else E(w + "." + n.id, "missing node lens lf/sf/fio");
    if (n.lf) { [n.lf, n.sf, n.fio].forEach(x => { needT(w + "." + n.id, x); const l = HM.leakFunc(tOf(x)); if (l) E(w + "." + n.id, "FUNCTIONAL text leaks " + l); }); if (same(n.lf, n.l)) { lensStat.identical++; E(w + "." + n.id, "lf identical to user label"); } }
  });
});
// ---- facets (named answers per variant): structure + USER leakage
const facetStat = { variantsWithFacets: 0, facets: 0 };
capIds.forEach(id => (HM.caps[id].elements || []).forEach(e => (e.variants || []).forEach((v, i) => { if (!v.facets) return; facetStat.variantsWithFacets++; v.facets.forEach(f => { facetStat.facets++; const wf = `facets:${id}.${e.id}[${i}]`; needT(wf + ".n", f.n); needT(wf + "." + tOf(f.n).slice(0, 20) + ".v", f.v); (f.a || ["user", "functional", "technical"]).forEach(a => { if (!["user", "functional", "technical"].includes(a)) E(wf, "invalid audience " + a); }); if ((f.a || ["user"]).includes("user")) { userText(wf, f.n); userText(wf, f.v); } }); })));
// ---- TECHNICAL documentation (hydra-tech-*.js): every component must explain WHAT / DOES / WHY / RESPONSIBILITY / WHERE / RECEIVES / PRODUCES, and be grounded in real files
const repoRoot = path.resolve(root, "..", "..", "..");
const fileCache = {}; const readSrc = p => { if (!(p in fileCache)) { const fp = path.join(repoRoot, p); fileCache[p] = fs.existsSync(fp) ? fs.readFileSync(fp, "utf8").split(/\r?\n/) : null; } return fileCache[p]; };
const normL = s => s.replace(/\s+/g, " ").trim();
const techStat = { caps: 0, comps: 0, groups: 0, methods: 0, excerpts: 0, tests: 0, trace: 0, nodeLinks: 0, nodesWithEvidence: 0, srcRefs: 0 };
const REP_TECH = { validation: { c: 14, x: 2, t: 4 }, transformation: { c: 10, x: 1, t: 3 }, grammar: { c: 10, x: 1, t: 3 }, events: { c: 10, x: 1, t: 3 }, gamification: { c: 8, x: 1, t: 3 }, integration: { c: 8, x: 1, t: 3 }, portability: { c: 5, x: 1, t: 2 } };
const allCompIds = {}; Object.keys(HM.tech.caps).forEach(id => HM.tech.caps[id].comps.forEach(k => { allCompIds[k.id] = id; }));
const TK = HM.TECH_KINDS;
capIds.forEach(id => {
  const td = HM.tech.caps[id], w = "tech:" + id, rep = REP_TECH[id] || { c: 3, x: 0, t: 1 };
  if (!td) { E(w, "no technical documentation (hydra-tech-*.js)"); return; }
  techStat.caps++; techStat.groups += td.groups.length;
  const gids = new Set(td.groups.map(g => g.id)), tids = new Set(td.tests.map(x => x.id)), cids = new Set();
  td.groups.forEach(g => { needT(w + ".group." + g.id + ".n", g.n); needT(w + ".group." + g.id + ".d", g.d); });
  if (td.comps.length < rep.c) E(w, `only ${td.comps.length} components documented (min ${rep.c})`);
  td.comps.forEach(k => {
    const wk = w + ".comp:" + k.id; techStat.comps++;
    if (cids.has(k.id)) E(wk, "duplicate component id"); cids.add(k.id);
    if (!TK.includes(k.kind)) E(wk, "invalid kind " + k.kind); if (!gids.has(k.g)) E(wk, "unknown group " + k.g);
    if (!k.n || typeof k.n !== "string") E(wk, "name (class/service) missing");
    ["what", "does", "why", "resp", "where", "recv", "prod", "fx", "rel"].forEach(f => needT(wk + "." + f, k[f]));
    if (!k.rat || !["proven", "observable"].includes(k.rat.k)) E(wk, "rat.k must be proven|observable"); else { needT(wk + ".rat.t", k.rat.t); if (k.rat.k === "proven" && !(k.rat.src || []).length) E(wk, "rationale marked proven without src evidence"); }
    if (!(k.src || []).length) E(wk, "no source evidence"); 
    (k.src || []).concat((k.rat && k.rat.src) || []).forEach(s => { techStat.srcRefs++; const L = readSrc(s.path); if (!L) E(wk, "source file not found: " + s.path); else if (s.line && s.line > L.length) E(wk, `line ${s.line} beyond end of ${s.path} (${L.length})`); });
    const main = (k.src || []).map(s => readSrc(s.path)).filter(Boolean);
    (k.methods || []).forEach(m => { techStat.methods++; const wm = wk + ".method:" + m.n; ["purpose", "when", "in", "out", "fx", "fail", "next"].forEach(f => needT(wm + "." + f, m[f])); if (main.length && !main.some(L => L.some(l => l.includes(m.n)))) E(wm, "method name not found in the component sources"); });
    (k.excerpts || []).forEach((x, i) => { techStat.excerpts++; const wx = wk + ".excerpt[" + i + "]"; ["what", "why", "role", "in", "out", "behavior"].forEach(f => needT(wx + "." + f, x[f])); const L = readSrc(x.path); if (!L) { E(wx, "file not found " + x.path); return; } const win = L.slice(Math.max(0, x.from - 3), x.to + 2).map(normL); const miss = (x.code || "").split("\n").map(normL).filter(l => l && !l.startsWith("// …") && !l.startsWith("// ...") && !win.includes(l)); if (miss.length) E(wx, `code does not match ${x.path}:${x.from}-${x.to} (e.g. "${miss[0].slice(0, 70)}")`); if ((x.code || "").split("\n").length > 40) E(wx, "excerpt longer than 40 lines"); });
    (k.tests || []).forEach(t => { if (!tids.has(t)) E(wk, "unknown test " + t); });
    (k.calls || []).concat(k.usedBy || []).forEach(r => { if (typeof r === "string") { if (!allCompIds[r]) E(wk, "unknown component reference " + r); } else { if (!r.n) E(wk, "external reference without name"); needT(wk + ".ref." + (r.n || "?"), r.d); } });
    (k.elems || []).forEach(e => { if (!allElems[e]) E(wk, "unknown element " + e); });
  });
  td.tests.forEach(x => { techStat.tests++; const wt = w + ".test:" + x.id; ["contract", "why", "protects"].forEach(f => needT(wt + "." + f, x[f])); const L = readSrc(x.path); if (!L) E(wt, "test file not found " + x.path); else if (x.line && x.line > L.length) E(wt, "line beyond end of file"); });
  td.trace.forEach((r, i) => { techStat.trace++; const wr = w + ".trace[" + i + "]"; ["user", "func", "role"].forEach(f => needT(wr + "." + f, r[f])); if (!allCompIds[r.comp]) E(wr, "unknown component " + r.comp); if (r.src && !readSrc(r.src.path)) E(wr, "file not found " + r.src.path); });
  const nEx = td.comps.reduce((s, k) => s + (k.excerpts || []).length, 0); if (nEx < rep.x) E(w, `only ${nEx} explained code excerpts (min ${rep.x})`);
  if (td.tests.length < rep.t) E(w, `only ${td.tests.length} explained tests (min ${rep.t})`); if (td.trace.length < (REP_TECH[id] ? 4 : 2)) E(w, `only ${td.trace.length} traceability rows`);
});
Object.keys(HM.tech.procs).forEach(pid => { const p = HM.procs.filter(x => x.id === pid)[0]; if (!p) { E("tech:proc:" + pid, "unknown process"); return; } Object.keys(HM.tech.procs[pid]).forEach(nid => { const n = p.nodes.filter(x => x.id === nid)[0], l = HM.tech.procs[pid][nid]; if (!n) { E("tech:proc:" + pid, "unknown node " + nid); return; } if (!allCompIds[l.tc]) E("tech:proc:" + pid + "." + nid, "unknown component " + l.tc); techStat.nodeLinks++; ["tw", "ti", "to", "te", "tn", "tf"].forEach(f => { if (l[f]) needT("tech:proc:" + pid + "." + nid + "." + f, l[f]); }); }); });
HM.procs.forEach(p => p.nodes.forEach(n => { if (n.x && (n.x.rt || (n.x.ev || []).length)) { techStat.nodesWithEvidence++; if (!(HM.tech.procs[p.id] || {})[n.id] && REP_TECH[p.cap]) W("tech:proc:" + p.id + "." + n.id, "node with technical evidence has no implementing-component link (tc)"); } }));
// ---- test-evidence store (knowledge/tests/*): schema, referential integrity, execution-status honesty, plain-language rules
const testStat = { tests: 0, byStatus: {}, byStrength: {}, byLayer: {}, procsWithEvidence: 0, stagesWithEvidence: 0, pending: 0 };
(function(){
  const dirT = path.join(__dirname, "tests"), need = ["test-catalog.json", "test-execution-state.json", "test-evidence-matrix.json", "test-gaps.json"];
  if (!need.every(n => fs.existsSync(path.join(dirT, n)))) { W("tests", "test-evidence store not built yet (knowledge/tests/*.json)"); return; }
  const cat = JSON.parse(fs.readFileSync(path.join(dirT, "test-catalog.json"), "utf8")), ex = JSON.parse(fs.readFileSync(path.join(dirT, "test-execution-state.json"), "utf8")), mx = JSON.parse(fs.readFileSync(path.join(dirT, "test-evidence-matrix.json"), "utf8")), gp = JSON.parse(fs.readFileSync(path.join(dirT, "test-gaps.json"), "utf8"));
  const validateRec = require("./tests/validate-fragment.js"), ids = new Set(cat.tests.map(x => x.id)), STAT = ["DISCOVERED", "PASS", "FAIL", "SKIPPED", "NOT_EXECUTED", "STALE_EVIDENCE"];
  const analysed = cat.tests.filter(x => !x.analysis); testStat.tests = cat.tests.length; testStat.pending = cat.tests.length - analysed.length;
  if (testStat.pending) E("tests", testStat.pending + " catalog record(s) still PENDING semantic analysis");
  const vr = validateRec(analysed, null); vr.errors.forEach(m => E("tests:" + m.split(":")[0], m.slice(m.indexOf(":") + 1))); vr.warns.slice(0, 40).forEach(m => W("tests:" + m.split(":")[0], m.slice(m.indexOf(":") + 1)));
  cat.tests.forEach(t => { const e = ex.tests[t.id]; testStat.byLayer[t.layer || "?"] = (testStat.byLayer[t.layer || "?"] || 0) + 1; testStat.byStrength[t.strength || "?"] = (testStat.byStrength[t.strength || "?"] || 0) + 1;
    if (!e) { E("tests:" + t.id, "no execution state"); return; } testStat.byStatus[e.s] = (testStat.byStatus[e.s] || 0) + 1;
    if (!STAT.includes(e.s)) E("tests:" + t.id, "bad status " + e.s);
    if (["PASS", "FAIL", "STALE_EVIDENCE", "SKIPPED"].includes(e.s) && !(e.src && e.ts)) E("tests:" + t.id, e.s + " without execution source/timestamp (never infer a status from existence)");
    if (e.s === "STALE_EVIDENCE" && !e.staleBecause) E("tests:" + t.id, "STALE_EVIDENCE without reason");
    if (e.s === "PASS" && t.external === "mocked" && /real external/i.test(JSON.stringify(t.guarantee || ""))) W("tests:" + t.id, "guarantee mentions real external verification for a mocked test");
    // USER-visible text must stay non-technical
    ["name", "behavior", "scenario", "result", "limits"].forEach(k => { const v = (t.user && t.user[k]) || t[k]; if (!v) return; ["es", "en"].forEach(l => { const hit = HM.leak(v[l] || ""); if (hit) E("tests:" + t.id + "." + k + "." + l, 'USER-visible text uses implementation vocabulary "' + hit + '"'); }); });
  });
  Object.keys(ex.tests).forEach(id => { if (!ids.has(id)) E("tests:exec:" + id, "execution state for unknown test"); });
  const pm = {}; HM.procs.forEach(p => { pm[p.id] = new Set(p.nodes.map(n => n.id)); });
  Object.keys(mx.procs).forEach(pid => { if (!pm[pid]) E("tests:matrix:" + pid, "unknown process"); Object.keys(mx.procs[pid].stages).forEach(s => { if (!pm[pid] || !pm[pid].has(s)) E("tests:matrix:" + pid, "unknown stage " + s); const x = mx.procs[pid].stages[s]; (x.d || []).concat(x.i || []).forEach(i => { if (!ids.has(i)) E("tests:matrix:" + pid + "." + s, "unknown test " + i); }); if (x.s === "ok" && !(x.d || []).some(i => (ex.tests[i] || {}).s === "PASS")) E("tests:matrix:" + pid + "." + s, "stage marked ok without a directly mapped PASS test"); }); if (mx.procs[pid].ok + mx.procs[pid].partial + mx.procs[pid].stale > 0) testStat.procsWithEvidence++; testStat.stagesWithEvidence += mx.procs[pid].ok + mx.procs[pid].partial + mx.procs[pid].stale; });
  Object.keys(mx.caps).forEach(c => { if (!HM.caps[c]) E("tests:matrix:" + c, "unknown capability"); });
  capIds.forEach(c => { if (!mx.caps[c]) E("tests:matrix", "capability missing in the matrix: " + c); else if (!mx.caps[c].tests.length) W("tests:" + c, "capability without any mapped test"); });
  const dataJs = path.join(root, "hydra-tests-data.js"); if (!fs.existsSync(dataJs)) E("tests", "hydra-tests-data.js missing (run knowledge/tests/build-test-evidence.js)"); else if (!fs.readFileSync(dataJs, "utf8").includes(mx.generatedAt)) E("tests", "hydra-tests-data.js is out of date with the knowledge store");
  const cache = JSON.parse(fs.readFileSync(path.join(__dirname, "capabilities-cache.json"), "utf8")); capIds.forEach(c => { if (!(cache.capabilities[c] || {}).testEvidence) E("tests:cache:" + c, "capabilities-cache.json lacks testEvidence"); });
  // quality gate: every capability is evaluated (has at least one analysed test or an explicit gap entry)
  capIds.forEach(c => { if (!mx.caps[c].tests.length && !(gp.capabilitiesWithoutEvidence || []).includes(c)) E("tests:" + c, "capability without tests and without a gap entry"); });
})();const matrix = {}, KEYS = ["user", "functional", "technical", "concepts", "variants", "purpose", "requiredness", "authoring", "microprocess", "domainmap", "internal", "graphical", "clickable", "relationships", "inputs", "outputs", "crosscap", "evidence"];
capIds.forEach(id => { const r = HM.completeness(id); matrix[id] = r.map(x => x.ok ? 1 : 0); });
const stats = { facets: facetStat, tech: techStat, lens: lensStat, tests: testStat,
  capabilities: capIds.length, elements: capIds.reduce((s, c) => s + (HM.caps[c].elements || []).length, 0),
  variants: capIds.reduce((s, c) => s + (HM.caps[c].elements || []).reduce((a, e) => a + (e.variants || []).length, 0), 0),
  mapNodes: capIds.reduce((s, c) => s + ((HM.caps[c].map || {}).nodes || []).length, 0),
  mechanisms: capIds.reduce((s, c) => s + (HM.caps[c].define || []).length, 0),
  processes: HM.procs.length, capProcesses: HM.procs.filter(p => p.scope === "cap").length,
  authoringProcesses: HM.procs.filter(p => p.scope === "authoring").length, crossProcesses: HM.procs.filter(p => p.scope === "cross").length,
  processNodes: HM.procs.reduce((s, p) => s + p.nodes.length, 0)
};
if (asJson) { console.log(JSON.stringify({ errors: errs, warnings: warns, stats, matrix, keys: KEYS }, null, 1)); }
else {
  warns.slice(0, 80).forEach(x => console.log("WARN  " + x)); if (warns.length > 80) console.log(`WARN  … ${warns.length - 80} more`);
  errs.forEach(x => console.log("ERROR " + x));
  console.log("\nSTATS " + JSON.stringify(stats));
  console.log("COMPLETENESS (" + KEYS.join(",") + ")");
  capIds.forEach(id => console.log("  " + id.padEnd(22) + matrix[id].join("") + "  " + matrix[id].reduce((a, b) => a + b, 0) + "/" + KEYS.length));
  console.log(`\n${errs.length} error(s), ${warns.length} warning(s)`);
}
process.exit(errs.length ? 1 : 0);
