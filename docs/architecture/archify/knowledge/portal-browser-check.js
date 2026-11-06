#!/usr/bin/env node
/* HYDRA portal browser check. Requires playwright-core (resolved from frontend/node_modules) and a system Chrome/Edge.
   Usage: node docs/architecture/archify/knowledge/portal-browser-check.js [--shots <dir>] [--quick]
   Phases:
     A  structure: console errors, overview per level/lang, every capability tab, every domain-map node, every process node
        (clickable + inspector content), node overlap, edge crossings, USER implementation-leakage audit on RENDERED text
     B  audience projection: USER vs FUNCTIONAL vs TECHNICAL content genuinely differs (capability overview, elements, process
        node titles/inspectors, domain-map labels, overview labels); technical evidence absent from USER
     C  context persistence: capability/tab/process/node/map/overview selection survives audience and language switches
     D  canvas experience: canvas area share, inspector does not overlap the canvas, pan / zoom / fit / reset / focus work */
"use strict";
const path = require("path"), fs = require("fs");
const root = path.resolve(__dirname, "..");
const repo = path.resolve(root, "..", "..", "..");
const { chromium } = require(path.join(repo, "frontend", "node_modules", "playwright-core"));
const args = process.argv.slice(2);
const shotDir = args.includes("--shots") ? args[args.indexOf("--shots") + 1] : null;
const quick = args.includes("--quick");
const exe = ["C:/Program Files/Google/Chrome/Application/chrome.exe", "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"].find(p => fs.existsSync(p));
const url = "file:///" + path.join(root, "hydra-architecture.html").replace(/\\/g, "/");
const errors = [], res = { overview: [], procs: [], crossings: [], overlaps: [], leaks: [], audience: [], context: [], canvas: [] };
const check = (ok, msg) => { if (!ok) errors.push(msg); return ok; };

(async () => {
  const browser = await chromium.launch({ executablePath: exe, headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1680, height: 1000 } });
  const page = await ctx.newPage();
  page.on("pageerror", e => errors.push("pageerror: " + e.message));
  page.on("console", m => { if (m.type() === "error") errors.push("console: " + m.text()); });
  page.on("requestfailed", r => errors.push("requestfailed: " + r.url().split("/").pop()));
  await page.goto(url); await page.waitForFunction(() => window.HydraPortal && window.HM);
  const ready = () => page.evaluate(() => HydraPortal.ready());
  const set = async (s) => { await page.evaluate(s => HydraPortal.setState(s), s); await ready(); await page.waitForTimeout(25); };
  const click = async (sel) => { await page.evaluate((s) => { const n = document.querySelector(s); if (!n) throw new Error("not found " + s); n.dispatchEvent(new MouseEvent("click", { bubbles: true })); }, sel); await ready(); };
  const shot = async (name) => { if (shotDir) { fs.mkdirSync(shotDir, { recursive: true }); await page.screenshot({ path: path.join(shotDir, name + ".png") }); } };
  const text = (sel) => page.evaluate((s) => { const e = document.querySelector(s); return e ? e.innerText : ""; }, sel);
  const nodeTitles = (sel) => page.evaluate((s) => [...document.querySelectorAll(s + " .gv-node")].map(n => [...n.querySelectorAll(".gv-title")].map(t => t.textContent).join(" ")), sel);
  const toks = (s) => new Set(s.toLowerCase().replace(/[^a-záéíóúñü0-9_]+/g, " ").split(" ").filter(w => w.length > 3));
  const jac = (a, b) => { const A = toks(a), B = toks(b); let i = 0; A.forEach(x => { if (B.has(x)) i++; }); return i / Math.max(1, A.size + B.size - i); };
  const levels = ["user", "functional", "technical"], langs = quick ? ["es"] : ["es", "en"];
  const capIds = await page.evaluate(() => Object.keys(HM.caps));
  const procInfo = await page.evaluate(() => HM.procs.map(p => ({ id: p.id, scope: p.scope, cap: p.cap, nodes: p.nodes.map(n => n.id), n: p.n.es })));

  async function graphMetrics(sel) {
    return page.evaluate((s) => {
      const root = document.querySelector(s); if (!root) return null;
      const nodes = [...root.querySelectorAll(".gv-node")].map(g => { const r = g.querySelector(".gv-card").getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height }; });
      let overlaps = 0; for (let i = 0; i < nodes.length; i++) for (let j = i + 1; j < nodes.length; j++) { const a = nodes[i], b = nodes[j]; if (a.x < b.x + b.w - 2 && b.x < a.x + a.w - 2 && a.y < b.y + b.h - 2 && b.y < a.y + a.h - 2) overlaps++; }
      const paths = [...root.querySelectorAll(".gv-edge-line")].map(p => { const L = p.getTotalLength(), pts = []; for (let k = 0; k <= 30; k++) { const q = p.getPointAtLength(L * k / 30); pts.push([q.x, q.y]); } return pts; });
      const seg = (a, b, c, d) => { const o = (p, q, r) => (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0]); return o(a, b, c) * o(a, b, d) < 0 && o(c, d, a) * o(c, d, b) < 0; };
      let cross = 0; for (let i = 0; i < paths.length; i++) for (let j = i + 1; j < paths.length; j++) { let hit = false; const A = paths[i], B = paths[j]; for (let a = 0; a < A.length - 1 && !hit; a++) for (let b = 0; b < B.length - 1 && !hit; b++) if (seg(A[a], A[a + 1], B[b], B[b + 1])) hit = true; if (hit) cross++; }
      let v = root; while (v && !v._view) v = v.parentElement;
      const em = v && v._view && v._view.layoutInfo && v._view.layoutInfo.metrics;
      return { nodes: nodes.length, edges: paths.length, overlaps, crossings: cross, engine: em ? { cross: em.EDGE_EDGE_CROSSINGS, edgeNode: em.EDGE_NODE_INTERSECTIONS, labelNode: em.LABEL_NODE_OVERLAPS, labelLabel: em.LABEL_LABEL_OVERLAPS, edgeLabel: em.EDGE_LABEL_INTERSECTIONS, nodeNode: em.NODE_NODE_OVERLAPS, bends: em.TOTAL_BENDS, maxBends: em.MAX_BENDS_PER_EDGE, length: Math.round(em.TOTAL_EDGE_LENGTH), longBack: em.LONG_BACK_EDGE_COUNT, amb: em.AMBIGUOUS_COLLINEAR_OVERLAPS, fit: em.FIT, cand: v._view.layoutInfo.cand } : null };
    }, sel);
  }
  async function renderedLeaks(sel) {
    return page.evaluate((s) => {
      const out = [], root = document.querySelector(s); if (!root) return out;
      const w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT); let n;
      while ((n = w.nextNode())) { const t = n.textContent.trim(); if (!t || n.parentElement.closest("code,.mono,.evidence-row,title")) continue; const k = HM.leak(t); if (k) out.push(k + " ⇐ " + t.slice(0, 90)); }
      return out;
    }, sel);
  }

  // ======================================================= A. structure
  for (const lang of langs) for (const level of levels) {
    await set({ section: "overview", mode: "map", lang, level, ovSel: null }); await page.waitForTimeout(250); await click('#ov-host .flt[data-f="all"]'); await page.waitForTimeout(200);
    const info =await page.evaluate(() => ({ nodes: document.querySelectorAll("#ov-host .gv-node").length, edges: document.querySelectorAll("#ov-host .gv-edge").length }));
    res.overview.push({ lang, level, ...info });
    check(info.nodes >= (level === "user" ? 15 : 18), `overview ${level}/${lang}: only ${info.nodes} nodes`);
    await click("#ov-host .gv-node"); const t = await text("#ov-host .insp"); check(t.length > 40, `overview ${level}/${lang}: empty inspector`);
    await shot(`overview-${level}-${lang}`);
  }
  await set({ section: "overview", mode: "archify", level: "functional", lang: "es" }); await page.waitForTimeout(500); await shot("overview-archify"); await set({ mode: "map" });

  for (const lang of langs) for (const level of levels) for (const cap of capIds) {
    for (const tab of ["overview", "elements", "map", "define", "processes", "relationships", "implementation", "evidence"]) {
      await set({ section: "capabilities", cap, tab, lang, level, mapSel: null, proc: null, node: null, openMech: {} });
      await page.waitForTimeout(30);
      const len = await page.evaluate(() => document.getElementById("cap-panel").innerText.length);
      check(len > 30, `${cap}/${tab}/${level}/${lang}: panel empty`);
      if (tab === "map") {
        const ids = await page.evaluate(() => [...document.querySelectorAll("#cap-map .gv-node")].map(n => n.dataset.id));
        check(ids.length > 0, `${cap}: domain map has no nodes`);
        if (level === "technical" && lang === "es") {
          const gm = await graphMetrics("#cap-map .gv-canvas"); if (gm) res.crossings.push({ where: `map:${cap}`, ...gm });
          for (const id of ids) { await click(`#cap-map .gv-node[data-id="${id}"]`); check((await text("#cap-map .insp")).length > 25, `${cap}: map node ${id} empty inspector`); }
        } else if (ids[0]) await click(`#cap-map .gv-node[data-id="${ids[0]}"]`);
        if (level === "user" && lang === "es") await shot(`map-${cap}`);
      }
      if (tab === "define" && level === "user" && lang === "es") { const has = await page.evaluate(() => !!document.getElementById("def-proc")); if (has) { await page.waitForTimeout(60); const n = await page.evaluate(() => document.querySelectorAll("#def-proc .gv-node").length); check(n > 0, `${cap}: micro-process did not render`); } }
      if (level === "user") (await renderedLeaks("#cap-panel")).forEach(x => res.leaks.push(`${cap}/${tab}/${lang}: ${x}`));
    }
  }
  for (const lang of langs) for (const level of levels) {
    for (const p of procInfo) {
      if (quick && !(lang === "es" && (level === "technical" || level === "user"))) continue;
      await set({ section: "processes", proc: p.id, node: null, lang, level }); await page.waitForTimeout(60);
      const ids = await page.evaluate(() => [...document.querySelectorAll("#proc-explorer .gv-node")].map(n => n.dataset.id));
      let empty = 0;
      for (const id of ids) {
        await click(`#proc-explorer .gv-node[data-id="${id}"]`);
        if ((await text("#proc-explorer .insp")).length < 30) empty++;
        if (level === "user") (await renderedLeaks("#proc-explorer .insp")).forEach(x => res.leaks.push(`proc ${p.id}/${id}/${lang}: ${x.slice(0, 100)}`));
      }
      check(!empty, `proc ${p.id} ${level}/${lang}: ${empty} node(s) with empty inspector`);
      if (level === "user") (await renderedLeaks("#proc-explorer .gv-canvas")).forEach(x => res.leaks.push(`proc canvas ${p.id}/${lang}: ${x.slice(0, 100)}`));
      if (lang === "es") {
        const gm = await graphMetrics("#proc-explorer .gv-canvas");
        if (gm) {
          res.procs.push({ id: p.id, level, scope: p.scope, ...gm, declared: p.nodes.length }); if (gm.overlaps) res.overlaps.push(p.id);
          const en = gm.engine; if (en) { check(en.edgeNode === 0, `layout ${p.id}/${level}: ${en.edgeNode} edge-node intersection(s)`); check(en.labelNode === 0 && en.labelLabel === 0 && en.nodeNode === 0 && en.edgeLabel === 0, `layout ${p.id}/${level}: label/node overlaps`); check(en.amb === 0, `layout ${p.id}/${level}: ${en.amb} ambiguous edge merge(s)`); check(en.cross <= 8, `layout ${p.id}/${level}: ${en.cross} edge crossings (regression ceiling 8)`); }
        }
      }
      if (level === "user" && lang === "es" && /(validation_execute|validation_apply_quickfix|x_event_flow_to_reward|transformation_sync)/.test(p.id)) await shot("proc-" + p.id);
    }
  }

  // ======================================================= B. audience projection differs
  const REP = ["validation", "transformation", "events", "gamification", "grammar"];
  for (const cap of REP) {
    const panels = {};
    for (const level of levels) { await set({ section: "capabilities", cap, tab: "overview", lang: "es", level }); await page.waitForTimeout(60); panels[level] = await text("#cap-panel"); }
    const uf = jac(panels.user, panels.functional), ut = jac(panels.user, panels.technical), ft = jac(panels.functional, panels.technical);
    res.audience.push({ what: `${cap} overview`, userFunc: +uf.toFixed(2), userTech: +ut.toFixed(2), funcTech: +ft.toFixed(2) });
    check(uf < 0.45, `${cap}: USER vs FUNCTIONAL overview too similar (${uf.toFixed(2)})`); check(ut < 0.35, `${cap}: USER vs TECHNICAL overview too similar (${ut.toFixed(2)})`); check(ft < 0.45, `${cap}: FUNCTIONAL vs TECHNICAL overview too similar (${ft.toFixed(2)})`);
    check(!/\.(java|tsx?)\b/.test(panels.user), `${cap}: USER overview shows source files`); check(!/\.(java|tsx?)\b/.test(panels.functional), `${cap}: FUNCTIONAL overview shows source files`);
    const el = {};
    for (const level of levels) { await set({ section: "capabilities", cap, tab: "elements", lang: "es", level }); await page.waitForTimeout(60); el[level] = await text("#el-detail .el"); }
    const e1 = jac(el.user, el.functional), e2 = jac(el.user, el.technical);
    res.audience.push({ what: `${cap} element[0]`, userFunc: +e1.toFixed(2), userTech: +e2.toFixed(2) });
    check(e1 < 0.5, `${cap}: first element USER vs FUNCTIONAL too similar (${e1.toFixed(2)})`); check(e2 < 0.4, `${cap}: first element USER vs TECHNICAL too similar (${e2.toFixed(2)})`);
    await set({ section: "capabilities", cap, tab: "implementation", lang: "es", level: "technical" }); check(/\.java|\.ts/.test(await page.evaluate(() => document.getElementById("cap-panel").innerHTML)), `${cap}: TECHNICAL implementation lacks source evidence`);
    await set({ level: "user" }); check(!/\.java/.test(await text("#cap-panel")), `${cap}: USER implementation tab leaks evidence`);
    const pr = procInfo.find(p => p.cap === cap && p.scope === "cap");
    if (pr) {
      const tt = {};
      for (const level of levels) { await set({ section: "processes", proc: pr.id, node: null, lang: "es", level }); await page.waitForTimeout(80); tt[level] = await nodeTitles("#proc-explorer"); }
      const diff = (a, b) => a.filter((x, i) => x !== b[i]).length / Math.max(1, a.length);
      const d1 = diff(tt.user, tt.functional), d2 = diff(tt.user, tt.technical), d3 = diff(tt.functional, tt.technical);
      res.audience.push({ what: `${pr.id} node titles`, userFunc: +d1.toFixed(2), userTech: +d2.toFixed(2), funcTech: +d3.toFixed(2) });
      check(d1 >= 0.6, `${pr.id}: only ${(d1 * 100) | 0}% of node titles differ USER→FUNCTIONAL`); check(d2 >= 0.5, `${pr.id}: only ${(d2 * 100) | 0}% of node titles differ USER→TECHNICAL`);
    }
    const ml = {};
    for (const level of levels) { await set({ section: "capabilities", cap, tab: "map", lang: "es", level, mapSel: null }); await page.waitForTimeout(80); ml[level] = (await nodeTitles("#cap-map")).join("|"); }
    check(ml.user !== ml.functional, `${cap}: map labels identical USER/FUNCTIONAL`); check(ml.user !== ml.technical, `${cap}: map labels identical USER/TECHNICAL`);
  }
  const ov = {};
  for (const level of levels) { await set({ section: "overview", mode: "map", level, lang: "es", ovSel: null }); await page.waitForTimeout(150); ov[level] = (await nodeTitles("#ov-host")).join("|"); }
  check(ov.user !== ov.functional && ov.functional !== ov.technical, "overview node labels do not change with the audience");
  await set({ section: "processes", proc: "validation_execute", node: "eval", lang: "es", level: "user" }); await page.waitForTimeout(300);
  const insp = {}; for (const level of levels) { await set({ level }); await page.waitForTimeout(300); insp[level] = await text("#proc-explorer .insp"); }
  res.audience.push({ what: "validation_execute.eval inspector", userFunc: +jac(insp.user, insp.functional).toFixed(2), userTech: +jac(insp.user, insp.technical).toFixed(2), funcTech: +jac(insp.functional, insp.technical).toFixed(2) });
  check(jac(insp.user, insp.functional) < 0.4 && jac(insp.functional, insp.technical) < 0.4 && jac(insp.user, insp.technical) < 0.3, "validation_execute.eval inspector too similar across audiences");
  check(!/\.java|\.tsx?\b/.test(insp.user) && !/\.java:\d+/.test(insp.functional), "inspector leaks source references into USER/FUNCTIONAL");

  // ======================================================= C. context persistence
  async function persists(name, setup, probe) {
    await setup(); await ready(); await page.waitForTimeout(100);
    const before = await probe();
    for (const level of ["functional", "technical", "user"]) {
      await page.evaluate((l) => document.querySelector(`#level-seg button[data-level="${l}"]`).click(), level); await ready(); await page.waitForTimeout(80);
      const after = await probe(); const ok = JSON.stringify(after) === JSON.stringify(before);
      res.context.push({ name, level, ok }); check(ok, `context lost on ${name} → ${level}: ${JSON.stringify(before)} vs ${JSON.stringify(after)}`);
    }
    await page.evaluate(() => document.querySelector('#lang-seg button[data-lang="en"]').click()); await ready(); await page.waitForTimeout(80);
    check(JSON.stringify(await probe()) === JSON.stringify(before), `context lost on ${name} when switching language`);
    await page.evaluate(() => document.querySelector('#lang-seg button[data-lang="es"]').click()); await ready();
  }
  const selProbe = (sel, keys) => async () => page.evaluate(([s, k]) => { const g = document.querySelector(s + " .gv-sel"); return { st: k.map(x => HydraPortal.state[x]), sel: g ? g.dataset.id : null }; }, [sel, keys]);
  await persists("process node", async () => { await set({ section: "processes", proc: "validation_execute", node: "eval", lang: "es", level: "user" }); }, selProbe("#proc-explorer", ["section", "proc", "node"]));
  await persists("capability + tab", async () => { await set({ section: "capabilities", cap: "transformation", tab: "elements", lang: "es", level: "user" }); }, async () => page.evaluate(() => ({ st: [HydraPortal.state.section, HydraPortal.state.cap, HydraPortal.state.tab], sel: document.querySelector("#cap-tabs [aria-pressed=true]").dataset.tab })));
  await persists("domain-map node", async () => { await set({ section: "capabilities", cap: "validation", tab: "map", mapSel: "m_rule", lang: "es", level: "user" }); }, selProbe("#cap-map", ["cap", "tab", "mapSel"]));
  await persists("overview node", async () => { await set({ section: "overview", mode: "map", ovSel: "validation", lang: "es", level: "user" }); }, selProbe("#ov-host", ["section", "ovSel"]));
  await persists("micro-process open", async () => { await set({ section: "capabilities", cap: "validation", tab: "define", lang: "es", level: "user", }); }, async () => page.evaluate(() => ({ st: [HydraPortal.state.cap, HydraPortal.state.tab], sel: String(document.querySelectorAll("#def-proc .gv-node").length > 0) })));

  // ======================================================= D. canvas experience
  await set({ section: "processes", proc: "validation_execute", node: null, lang: "es", level: "user" }); await page.waitForTimeout(400);
  const lay = await page.evaluate(() => { const c = document.querySelector("#proc-explorer .gv-canvas").getBoundingClientRect(), i = document.querySelector("#proc-explorer .insp").getBoundingClientRect(); return { cw: c.width, ch: c.height, cr: c.right, ir: i.x, iw: i.width, vw: innerWidth, vh: innerHeight }; });
  const share = (lay.cw * lay.ch) / (lay.vw * lay.vh); res.canvas.push({ canvasShareOfViewport: +share.toFixed(2), canvas: [Math.round(lay.cw), Math.round(lay.ch)], inspectorWidth: Math.round(lay.iw) });
  check(share > 0.45, `process canvas uses only ${(share * 100) | 0}% of the viewport`); check(lay.cr <= lay.ir + 1, "inspector overlaps the process canvas");
  const tf = () => page.evaluate(() => document.querySelector("#proc-explorer .gv-vp").getAttribute("transform"));
  const t0 = await tf(), box = await page.$("#proc-explorer .gv-svg"), bb = await box.boundingBox();
  await page.mouse.move(bb.x + bb.width / 2, bb.y + bb.height / 2); await page.mouse.wheel(0, -400); await page.waitForTimeout(100); const t1 = await tf(); check(t1 !== t0, "wheel zoom did not change the view");
  await page.mouse.move(bb.x + 300, bb.y + 400); await page.mouse.down(); await page.mouse.move(bb.x + 420, bb.y + 460, { steps: 4 }); await page.mouse.up(); const t2 = await tf(); check(t2 !== t1, "drag pan did not change the view");
  const tb = (n) => `#proc-explorer .gv-toolbar > *:nth-child(${n})`;
  await page.click(tb(1)); await page.waitForTimeout(80); const t3 = await tf(); check(t3 !== t2, "Fit did not change the view");
  const pct0 = await page.$eval("#proc-explorer .gv-pct", e => e.textContent); await page.click(tb(4)); const pct1 = await page.$eval("#proc-explorer .gv-pct", e => e.textContent); check(pct0 !== pct1, "zoom-in button did not change the zoom");
  await page.click(tb(5)); await page.waitForTimeout(60); check((await tf()) === "translate(24,24) scale(1)", "Reset did not restore the default view");
  await click('#proc-explorer .gv-node[data-id="eval"]'); const tFocus0 = await tf(); await page.click(tb(6)); await page.waitForTimeout(80); check((await tf()) !== tFocus0, "Focus did not centre on the selected node");
  await page.click("#proc-explorer .gv-insp-toggle"); await page.waitForTimeout(80); check(await page.evaluate(() => document.querySelector("#proc-explorer .gv-layout").classList.contains("insp-collapsed")), "inspector toggle did not collapse"); await page.click("#proc-explorer .gv-insp-toggle");
  await shot("canvas-final");

  // ======================================================= E. routing quality, playback, filters, width, technical explanation
  const through = (sel) => page.evaluate((s) => {
    const root = document.querySelector(s); if (!root) return null; const svg = root.querySelector("svg"); if (!svg) return null;
    const rects = [...root.querySelectorAll(".gv-node")].map(g => { const r = g.querySelector(".gv-card").getBoundingClientRect(); return { x: r.x + 4, y: r.y + 4, r: r.right - 4, b: r.bottom - 4 }; });
    let hits = 0, edgesHit = 0;
    root.querySelectorAll(".gv-edge-line").forEach(p => {
      const L = p.getTotalLength(), m = p.getScreenCTM(); let hit = false;
      for (let k = 2; k <= 98; k++) { const q = p.getPointAtLength(L * k / 100), pt = svg.createSVGPoint(); pt.x = q.x; pt.y = q.y; const s = pt.matrixTransform(m); if (rects.some(r => s.x > r.x && s.x < r.r && s.y > r.y && s.y < r.b)) { hits++; hit = true; } }
      if (hit) edgesHit++;
    });
    return { edges: root.querySelectorAll(".gv-edge-line").length, hits, edgesHit };
  }, sel);
  res.routing = [];
  for (const p of procInfo) {
    if (quick && !/validation_execute|transformation|events_execute|x_event/.test(p.id)) continue;
    await set({ section: "processes", proc: p.id, node: null, lang: "es", level: "functional" }); await page.waitForTimeout(60);
    const m = await through("#proc-explorer .gv-canvas"); if (m) { res.routing.push({ id: p.id, ...m }); }
  }
  const edgesThroughNodes = res.routing.reduce((s, r) => s + r.edgesHit, 0);
  check(edgesThroughNodes === 0, `routing: ${edgesThroughNodes} edge(s) pass through a node (${res.routing.filter(r => r.edgesHit).slice(0, 6).map(r => r.id + ":" + r.edgesHit).join(", ")})`);

  // playback
  await set({ section: "processes", proc: "validation_execute", node: null, lang: "es", level: "functional" }); await page.waitForTimeout(300);
  const pbq = (a) => `#proc-explorer .pb-bar [data-pb="${a}"]`;
  check(!!(await page.$(pbq("play"))), "playback controls missing");
  await click(pbq("restart")); await page.waitForTimeout(100);
  const act0 = await page.evaluate(() => document.querySelector("#proc-explorer .gv-active")?.dataset.id || null); check(!!act0, "playback restart: no active node");
  const seen = new Set([act0]);
  for (let i = 0; i < 40; i++) {
    const pend = await page.evaluate(() => document.querySelectorAll("#proc-explorer .pb-branch [data-pbc]").length);
    if (pend) await click("#proc-explorer .pb-branch [data-pbc]"); else await click(pbq("next"));
    await page.waitForTimeout(pend ? 80 : 1500);
    const a = await page.evaluate(() => document.querySelector("#proc-explorer .gv-active")?.dataset.id || null); if (a) seen.add(a);
    if (await page.evaluate(() => /Fin del recorrido/.test(document.querySelector("#proc-explorer .pb-info")?.textContent || ""))) break;
  }
  check(seen.size >= 4, `playback visited only ${seen.size} node(s)`);
  const inspSync = await page.evaluate(() => { const a = document.querySelector("#proc-explorer .gv-active"), i = document.querySelector("#proc-explorer .insp"); return !!a && i.innerText.length > 30; }); check(inspSync, "playback: inspector not synchronised with the active node");
  await click(pbq("prev")); await page.waitForTimeout(100); check(await page.evaluate(() => !!document.querySelector("#proc-explorer .gv-active")), "playback: previous lost the active node");
  await page.selectOption('#proc-explorer [data-pb="speed"]', "2"); await click(pbq("restart")); await click(pbq("play")); await page.waitForTimeout(2600);
  check(await page.evaluate(() => document.querySelectorAll("#proc-explorer .gv-done,#proc-explorer .gv-visited").length > 0), "playback: auto-play made no progress"); await click(pbq("play"));
  res.playback = { visited: [...seen] };

  // relationship filters on the overview
  await set({ section: "overview", mode: "map", level: "functional", lang: "es", ovSel: null }); await page.waitForTimeout(250);
  const ec = () => page.evaluate(() => document.querySelectorAll("#ov-host .gv-edge").length);
  await click('#ov-host .flt[data-f="main"]'); await page.waitForTimeout(250); const eMain = await ec(); await click('#ov-host .flt[data-f="all"]'); await page.waitForTimeout(250); const eAll = await ec();
  check(eAll > eMain, `overview filters: All (${eAll}) not larger than Main (${eMain})`); check(eMain >= 5, `overview filters: Main shows only ${eMain} relationships`);
  await click('#ov-host .flt[data-f="event"]'); await page.waitForTimeout(250); const eEv = await ec(); check(eEv < eAll, "overview filters: Events did not reduce the graph");
  await click('#ov-host .flt[data-f="main"]');
  // focus: selecting a node dims unrelated edges
  await set({ section: "overview", mode: "map", level: "functional", ovSel: null }); await click('#ov-host .flt[data-f="all"]'); await page.waitForTimeout(250);
  await click('#ov-host .gv-node[data-id="validation"]'); await page.waitForTimeout(150);
  check(await page.evaluate(() => document.querySelectorAll("#ov-host .gv-dim").length > 0), "overview focus: unrelated edges are not dimmed");

  // workspace width and layout
  res.layout = [];
  for (const [tab, sel, minW] of [["overview", ".ov-grid", 900], ["elements", ".md", 900], ["define", ".md", 900], ["map", ".gv-layout", 900], ["relationships", ".gv-layout", 900], ["implementation", ".md,.impl-map", 900]]) {
    await set({ section: "capabilities", cap: "validation", tab, lang: "es", level: "technical", mapSel: null }); await page.waitForTimeout(120);
    const w = await page.evaluate((s) => { const n = document.querySelector("#cap-panel " + s.split(",")[0]) || document.querySelector("#cap-panel " + (s.split(",")[1] || s)); return n ? Math.round(n.getBoundingClientRect().width) : 0; }, sel);
    res.layout.push({ tab, width: w }); check(w >= minW, `workspace ${tab}: content only ${w}px wide at 1680px`);
  }
  await set({ section: "capabilities", cap: "validation", tab: "overview", level: "technical" }); await page.waitForTimeout(100);
  check(await page.evaluate(() => { const g = document.querySelector(".ov-grid"); return g && getComputedStyle(g).gridTemplateColumns.split(" ").length >= 2; }), "capability overview is not a multi-column card grid");
  await shot("cap-overview-technical"); await set({ tab: "implementation" }); await page.waitForTimeout(150); await shot("cap-implementation-technical");
  // responsive
  await page.setViewportSize({ width: 760, height: 900 }); await set({ section: "capabilities", cap: "validation", tab: "elements", level: "user" }); await page.waitForTimeout(150);
  check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 4), "responsive: horizontal page scroll at 760px"); await shot("cap-elements-narrow");
  await page.setViewportSize({ width: 1680, height: 1000 });

  // technical explanation (WHAT / WHY / ROLE) per representative capability, absent from USER
  res.techExpl = [];
  for (const cap of ["validation", "quickfix_none", "transformation", "grammar", "events", "gamification", "integration", "portability"]) {
    if (!capIds.includes(cap)) continue;
    await set({ section: "capabilities", cap, tab: "implementation", lang: "es", level: "technical" }); await page.waitForTimeout(120);
    const info = await page.evaluate(() => { const c = document.querySelector("#comp-detail .comp"); return c ? { labels: [...c.querySelectorAll(".fld > b")].map(b => b.textContent), len: c.innerText.length, drill: c.querySelectorAll("details.drill").length } : null; });
    res.techExpl.push({ cap, ...(info || {}) });
    check(info && ["QUÉ HACE", "POR QUÉ EXISTE", "RESPONSABILIDAD", "RECIBE", "PRODUCE"].every(l => info.labels.some(x => x.toUpperCase() === l)), `${cap}: technical component card lacks WHAT/WHY/RESPONSIBILITY/RECEIVES/PRODUCES (${info && info.labels.join(",")})`);
    check(info && info.len > 600, `${cap}: technical component explanation too short`);
    await set({ tab: "overview" }); await page.waitForTimeout(80);
    check(await page.evaluate(() => !!document.querySelector(".im-grid") && document.querySelectorAll(".trace tbody tr").length >= 4), `${cap}: technical overview lacks implementation map / traceability`);
    await set({ level: "user", tab: "implementation" }); await page.waitForTimeout(80); check(await page.evaluate(() => !document.querySelector(".comp") && !/\.java|\.tsx?:/.test(document.getElementById("cap-panel").innerHTML)), `${cap}: USER implementation tab exposes technical documentation`);
  }
  // technical stage inspector
  await set({ section: "processes", proc: "validation_execute", node: null, lang: "es", level: "technical" }); await page.waitForTimeout(250);
  const stageIds = await page.evaluate(() => [...document.querySelectorAll("#proc-explorer .gv-node")].map(n => n.dataset.id));
  let linked = 0; for (const id of stageIds) { await click(`#proc-explorer .gv-node[data-id="${id}"]`); if (/IMPLEMENTADA POR/i.test(await text("#proc-explorer .insp"))) linked++; }
  check(linked >= 3, `technical stage inspector: only ${linked} stage(s) show the implementing component`);
  res.techStage = { stages: stageIds.length, linked };


  // ======================================================= G. TESTS & EVIDENCE area
  const tdata = await page.evaluate(() => window.HYDRA_TESTS ? { tests: Object.keys(HYDRA_TESTS.tests).length, procs: Object.keys(HYDRA_TESTS.matrix.procs).length } : null);
  check(!!tdata && tdata.tests > 100, "tests & evidence: data missing or too small");
  if (tdata) {
    await set({ section: "tests", level: "functional", lang: "es" });
    check(await page.evaluate(() => !document.getElementById("view-tests").classList.contains("hidden") && document.querySelectorAll("#view-tests .tr").length > 3), "tests area: tree not rendered");
    check(await page.evaluate(() => getComputedStyle(document.querySelector(".tst-cols")).gridTemplateColumns.split(" ").length >= 3), "tests area: not a multi-column master/detail layout at desktop width");
    check(await page.evaluate(() => document.querySelectorAll("#view-tests .ovc").length >= 8), "tests area: overview counters missing");
    // layer -> capability -> process -> stage -> test navigation
    const nav = await page.evaluate(() => { const o = {}; const L = document.querySelector('#tst-tree [data-tst-sel^="layer:"]'); if (L) L.click(); return !!L; }); check(nav, "tests area: no layer row");
    await page.evaluate(() => { const cap = [...document.querySelectorAll('#tst-tree [data-tst-tog^="cap:"]')][0]; if (cap) cap.click(); });
    await page.evaluate(() => { const pr = [...document.querySelectorAll('#tst-tree [data-tst-tog^="proc:"]')][0]; if (pr) pr.click(); });
    check(await page.evaluate(() => document.querySelectorAll('#tst-tree [data-tst-sel^="stage:"]').length > 0 || document.querySelectorAll('#tst-tree [data-tst-sel^="proc:"]').length > 0), "tests area: tree does not expand to processes/stages");
    await page.evaluate(() => HydraTestsUI.openAt("proc:validation_execute"));
    await page.evaluate(() => { const t = document.querySelector("#tst-center .tcard2"); if (t) t.click(); });
    const det = {}; for (const level of levels) { await set({ level }); det[level] = await text("#tst-detail"); }
    check(det.technical.length > 200, "tests area: technical detail too short"); check(!/\.(java|tsx?|py|mjs)\b/.test(det.user), "tests area: USER detail shows source files"); check(jac(det.user, det.technical) < 0.5, "tests area: USER and TECHNICAL detail too similar (" + jac(det.user, det.technical).toFixed(2) + ")");
    check(/QUÉ DEMUESTRA|¿Qué demuestra/i.test(det.user), "tests area: USER detail lacks plain-language questions"); check(/COMPORTAMIENTO VERIFICADO/i.test(det.functional), "tests area: FUNCTIONAL detail lacks COMPORTAMIENTO VERIFICADO"); check(/LIMITACIONES/i.test(det.functional), "tests area: FUNCTIONAL detail lacks LIMITACIONES");
    res.testsArea = { counts: tdata, detailLen: { user: det.user.length, functional: det.functional.length, technical: det.technical.length } };
    // filters
    await set({ level: "functional" }); await page.evaluate(() => HydraTestsUI.openAt("layer:" + HYDRA_TESTS.layers[0].id));
    const n0 = await page.evaluate(() => document.querySelectorAll("#tst-center .tcard2").length);
    await click('[data-tst-fs="FAIL"]'); const n1 = await page.evaluate(() => document.querySelectorAll("#tst-center .tcard2").length); check(n1 < n0 || n0 === 0, "tests area: status filter did not narrow the list"); await click("[data-tst-clear]");
    for (const v of ["matrix", "gates"]) { await click(`#tst-views [data-tst-view="${v}"]`); check(await page.evaluate(() => document.querySelector(".tst-center").innerText.length > 200), `tests area: ${v} view empty`); }
    await shot("tests-matrix"); await click('#tst-views [data-tst-view="layers"]');
    // process stage evidence indicators and inspector
    await set({ section: "processes", proc: "validation_execute", node: null, level: "functional", lang: "es" });
    check(await page.evaluate(() => document.querySelectorAll("#proc-explorer .gv-evb").length > 0), "process diagram: no test-evidence indicators on stages");
    const stage = await page.evaluate(() => { const g = document.querySelector("#proc-explorer .gv-evb-ok, #proc-explorer .gv-evb-partial"); return g ? g.closest(".gv-node").dataset.id : null; });
    if (stage) { await click(`#proc-explorer .gv-node[data-id="${stage}"]`); const it = {}; for (const level of levels) { await set({ level }); it[level] = await text("#proc-explorer .insp"); check(/PRUEBAS|Evidencia/i.test(it[level]), `process stage inspector (${level}) lacks test evidence`); } check(!/\.(java|tsx?)\b/.test(it.user + it.functional), "process stage evidence leaks source files into USER/FUNCTIONAL"); }
    await shot("process-evidence");
    // capability evidence tab per audience
    const ct = {}; for (const level of levels) { await set({ section: "capabilities", cap: "validation", tab: "evidence", level, lang: "es" }); ct[level] = await text("#cap-panel"); }
    check(ct.user.length > 60 && !/\.(java|tsx?)\b/.test(ct.user), "capability evidence tab (USER) empty or technical"); check(/\.java|\.tsx?/.test(ct.technical), "capability evidence tab (TECHNICAL) lacks concrete tests"); check(jac(ct.user, ct.technical) < 0.4, "capability evidence tab: USER/TECHNICAL too similar");
    await shot("capability-evidence-technical");
    // EN
    await set({ section: "tests", level: "functional", lang: "en" }); check(await page.evaluate(() => /TESTS & EVIDENCE/.test(document.querySelector("#view-tests h2").textContent)), "tests area: no English title"); await set({ lang: "es" });
  }
  // ======================================================= F. ELK layout: animation follows routed geometry, selection survives relayout, debug mode is opt-in
  await set({ section: "processes", proc: "validation_execute", node: null, lang: "es", level: "functional" });
  await click(pbq("restart")); await page.waitForTimeout(80);
  await page.evaluate(() => document.querySelector('#proc-explorer .pb-bar [data-pb="next"]').dispatchEvent(new MouseEvent("click", { bubbles: true })));
  let onPath = null;
  for (let i = 0; i < 12 && onPath === null; i++) {
    await page.waitForTimeout(40);
    onPath = await page.evaluate(() => {
      const dot = document.querySelector("#proc-explorer .gv-dot"), act = document.querySelector("#proc-explorer .gv-playing .gv-edge-line"); if (!dot || !act) return null;
      const cx = +dot.getAttribute("cx"), cy = +dot.getAttribute("cy"), L = act.getTotalLength(); let best = 1e9;
      for (let k = 0; k <= 400; k++) { const q = act.getPointAtLength(L * k / 400); best = Math.min(best, Math.hypot(q.x - cx, q.y - cy)); }
      return { dist: best, len: Math.round(L) };
    });
  }
  res.animation = onPath; check(onPath === null || onPath.dist < 4, "playback: the moving token is not on the routed ELK edge geometry");
  await set({ section: "overview", mode: "map", level: "functional", lang: "es", ovSel: "validation" });
  await click('#ov-host .flt[data-f="all"]');
  check(await page.evaluate(() => { const s = document.querySelector("#ov-host .gv-sel"); return !!s && s.dataset.id === "validation"; }), "relayout on filter change lost the selected node");
  check(await page.evaluate(() => !document.querySelector(".gv-debugpanel") && !document.querySelector(".gv-debug")), "layout debug overlay visible by default");
  await page.goto(url + "?layoutdebug=1"); await page.waitForFunction(() => window.HydraPortal); await set({ section: "processes", proc: "validation_execute", node: null, lang: "es", level: "functional" });
  check(await page.evaluate(() => !!document.querySelector(".gv-debugpanel") && !!document.querySelector(".gv-debug")), "layout debug mode (?layoutdebug=1) did not render");
  await shot("layout-debug"); await page.goto(url); await page.waitForFunction(() => window.HydraPortal);  await browser.close();

  const pt = res.procs.filter(p => p.level === "technical"), eng = (k) => res.procs.reduce((s, p) => s + ((p.engine && p.engine[k]) || 0), 0);
  const sum = { errors: errors.length, leaks: res.leaks.length, overlapDiagrams: res.overlaps.length,
    layout: { diagrams: res.procs.length, engineCrossings: eng("cross"), edgeNodeIntersections: eng("edgeNode"), labelNodeOverlaps: eng("labelNode"), labelLabelOverlaps: eng("labelLabel"), edgeLabelIntersections: eng("edgeLabel"), nodeNodeOverlaps: eng("nodeNode"), bends: eng("bends"), longBackEdges: eng("longBack"), ambiguousOverlaps: eng("amb") },
    crossingsTechnicalProcesses: pt.reduce((s, p) => s + p.crossings, 0) + res.crossings.reduce((s, p) => s + p.crossings, 0), procsChecked: pt.length,
    nodesTotal: pt.reduce((s, p) => s + p.nodes, 0), worstCrossings: pt.concat(res.crossings).sort((a, b) => b.crossings - a.crossings).slice(0, 8).map(p => `${p.id || p.where}:${p.crossings}`),
    contextChecks: res.context.length, contextFailures: res.context.filter(c => !c.ok).length, audienceChecks: res.audience.length, canvas: res.canvas[0] };  fs.writeFileSync(path.join(root, "knowledge", "portal-browser-check.last.json"), JSON.stringify({ summary: sum, audience: res.audience, context: res.context, overview: res.overview, errors: errors.slice(0, 200), leaks: res.leaks.slice(0, 200), overlaps: res.overlaps, procs: res.procs, mapMetrics: res.crossings, routing: res.routing, playback: res.playback, layout: res.layout, techExpl: res.techExpl, techStage: res.techStage, animation: res.animation, testsArea: res.testsArea }, null, 1));
  console.log(JSON.stringify(sum, null, 1)); errors.slice(0, 40).forEach(e => console.log("ERR  " + e)); res.leaks.slice(0, 40).forEach(e => console.log("LEAK " + e));
  process.exit(errors.length || res.leaks.length ? 1 : 0);
})().catch(e => { console.error("FATAL", e); process.exit(2); });
