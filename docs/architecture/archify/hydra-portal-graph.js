/* HYDRA architecture portal — graph engine v2 (node-editor style).
   Layered (Sugiyama-style) layout with crossing minimisation, band folding and port-aware routing,
   rendered as an interactive SVG canvas: dotted grid, card nodes with ports and icons, semantic node families,
   pan / zoom / fit / reset / focus-selected, selection with path highlighting.
   Used by: process diagrams, authoring micro-processes, domain maps and the interactive overview.
   No dependencies. Presentation only: it knows nothing about HYDRA; content lives in hydra-model-*.js,
   audience lenses in hydra-lens-*.js and the UI in hydra-portal-app.js. */
(function(){
  "use strict";
  var SVGNS = "http://www.w3.org/2000/svg";
  var FAMILY = '-apple-system,BlinkMacSystemFont,"Segoe UI Variable","Segoe UI",Roboto,Helvetica,Arial,sans-serif';
  var _ctx = null;
  function measure(text, bold, size){
    if (!_ctx) { _ctx = document.createElement("canvas").getContext("2d"); }
    _ctx.font = (bold ? "650 " : "400 ") + (size || 13.5) + "px " + FAMILY;
    return _ctx.measureText(text).width;
  }
  // wrap into at most maxLines lines; the last line is ellipsised when the text does not fit
  function wrapLines(text, maxW, bold, size, maxLines){
    var words = String(text || "").split(/\s+/).filter(Boolean), lines = [], cur = "";
    for (var i = 0; i < words.length; i++) {
      var t = cur ? cur + " " + words[i] : words[i];
      if (cur && measure(t, bold, size) > maxW) {
        lines.push(cur); cur = words[i];
        if (maxLines && lines.length === maxLines - 1) { cur = words.slice(i).join(" "); break; }
      } else cur = t;
    }
    if (cur) lines.push(cur);
    if (maxLines && lines.length > maxLines) lines.length = maxLines;
    var last = lines.length - 1;
    if (last >= 0) {
      var full = lines[last];
      if (measure(full, bold, size) > maxW) { while (full.length > 1 && measure(full + "…", bold, size) > maxW) full = full.slice(0, -1); lines[last] = full.replace(/\s+$/, "") + "…"; }
    }
    return lines.length ? lines : [""];
  }
  function wrap(text, maxW, bold){ return wrapLines(text, maxW, bold, 13.5, 0); }
  function el(name, attrs, parent){
    var n = document.createElementNS(SVGNS, name);
    if (attrs) Object.keys(attrs).forEach(function(k){ n.setAttribute(k, attrs[k]); });
    if (parent) parent.appendChild(n);
    return n;
  }

  // ------------------------------------------------------------------ node sizing (card families)
  var CARD_W = 244, TITLE_LH = 18, SUB_LH = 15;
  function sizeNode(n){
    var f = n.shape || "step";
    if (f === "start" || f === "end") {
      var tl = wrapLines(n.label, 200, true, 13.5, 2);
      n.lines = tl; n.w = Math.round(Math.min(330, Math.max(168, Math.max.apply(null, tl.map(function(s){ return measure(s, true); })) + 86)));
      n.h = tl.length > 1 ? 66 : 54;
    } else if (f === "decision") {
      var dl = wrapLines(n.label, 116, true, 13, 3);
      n.lines = dl; n.w = 190; n.h = Math.max(116, 58 + dl.length * 17);
    } else {
      var w = f === "cap" ? 262 : (f === "variant" ? 214 : CARD_W), inner = w - 32;
      var titleLines = wrapLines(n.label, inner, true, 13.5, 2), subLines = n.sub ? wrapLines(n.sub, inner, false, 11.5, 2) : [];
      n.lines = titleLines; n.subLines = subLines; n.w = w;
      n.h = 14 + 22 + 9 + titleLines.length * TITLE_LH + (subLines.length ? 5 + subLines.length * SUB_LH : 0) + 15;
    }
    n.w = Math.round(n.w); n.h = Math.round(n.h);
  }

  function polyPath(pts, rad){
    var d = "M" + pts[0].x + "," + pts[0].y;
    for (var i = 1; i < pts.length - 1; i++) {
      var a = pts[i - 1], b = pts[i], c = pts[i + 1];
      var l1 = Math.hypot(b.x - a.x, b.y - a.y) || 1, l2 = Math.hypot(c.x - b.x, c.y - b.y) || 1, r1 = Math.min(rad, l1 / 2), r2 = Math.min(rad, l2 / 2);
      d += " L" + (b.x - (b.x - a.x) / l1 * r1) + "," + (b.y - (b.y - a.y) / l1 * r1) + " Q" + b.x + "," + b.y + " " + (b.x + (c.x - b.x) / l2 * r2) + "," + (b.y + (c.y - b.y) / l2 * r2);
    }
    return d + " L" + pts[pts.length - 1].x + "," + pts[pts.length - 1].y;
  }
  function midpoint(r){
    var p = r.pts;
    if (r.back && !r.poly) return { x: (p[0].x + p[1].x) / 2, y: r.lane };
    var segs = [], total = 0;
    for (var i = 1; i < p.length; i++) { var l = Math.abs(p[i].x - p[i - 1].x) + Math.abs(p[i].y - p[i - 1].y); segs.push(l); total += l; }
    var half = total / 2, acc = 0;
    for (var j = 0; j < segs.length; j++) {
      if (acc + segs[j] >= half) { var t = (half - acc) / (segs[j] || 1); return { x: p[j].x + (p[j + 1].x - p[j].x) * t, y: p[j].y + (p[j + 1].y - p[j].y) * t }; }
      acc += segs[j];
    }
    return p[0];
  }


  // ------------------------------------------------------------------ edge styles (pattern + weight + glyph; colour is secondary)
  var EDGE = {
    next:     { dash: "",        w: 1.9, mark: "arrow" },
    req:      { dash: "",        w: 2.3, mark: "arrow" },
    opt:      { dash: "7 5",     w: 1.7, mark: "arrow" },
    cond:     { dash: "2 5",     w: 2.4, mark: "arrow", cap: "round" },
    alt:      { dash: "",        w: 1.9, mark: "arrow", dbl: true },
    loop:     { dash: "6 4",     w: 1.9, mark: "arrow", glyph: "↻" },
    event:    { dash: "9 3 2 3", w: 1.9, mark: "arrow", glyph: "ϟ" },
    x:        { dash: "",        w: 2.8, mark: "arrow", glyph: "»" },
    prod:     { dash: "",        w: 1.9, mark: "arrow" },
    ext:      { dash: "3 4",     w: 1.6, mark: "sq" },
    contains: { dash: "",        w: 1.9, mark: "dia" },
    ref:      { dash: "",        w: 1.6, mark: "arrow" },
    isa:      { dash: "",        w: 1.6, mark: "tri" },
    gen:      { dash: "5 4",     w: 1.6, mark: "arrow" }
  };
  var EDGE_GLYPH = { opt: "opc.", cond: "?", alt: "alt", event: "evt", ext: "ext" };

  // 24x24 stroke icons
  function gearLines(){ var d = ""; for (var i = 0; i < 8; i++) { var a = i * Math.PI / 4, x1 = 12 + Math.cos(a) * 6.2, y1 = 12 + Math.sin(a) * 6.2, x2 = 12 + Math.cos(a) * 8.4, y2 = 12 + Math.sin(a) * 8.4; d += "M" + x1.toFixed(2) + " " + y1.toFixed(2) + "L" + x2.toFixed(2) + " " + y2.toFixed(2); } return d; }
  var ICONS = {
    user:     { d: "M5 20c0-3.6 3-6 7-6s7 2.4 7 6M12 4.5a3.6 3.6 0 1 1 0 7.2 3.6 3.6 0 0 1 0-7.2z" },
    auto:     { d: "M12 8.8a3.2 3.2 0 1 1 0 6.4 3.2 3.2 0 0 1 0-6.4z" + gearLines() },
    ext:      { d: "M9 3v5M15 3v5M7 8h10v3.5a5 5 0 0 1-10 0V8zM12 16.5V21" },
    play:     { d: "M8 5l11 7-11 7z", fill: true },
    stop:     { d: "M7 7h10v10H7z", fill: true },
    doc:      { d: "M7 3h7l4 4v14H7zM14 3v4h4M10 12h5M10 16h5" },
    branch:   { d: "M6 4v8a4 4 0 0 0 4 4h8M6 12h12M15 9l3 3-3 3" },
    loop:     { d: "M4 12a8 8 0 0 1 14-5M20 4v4h-4M20 12a8 8 0 0 1-14 5M4 20v-4h4" },
    cube:     { d: "M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12l8-4.5M12 12v9M12 12L4 7.5" },
    tag:      { d: "M4 4h8l8 8-8 8-8-8zM8.5 8.5h.01" },
    spark:    { d: "M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z" },
    layers:   { d: "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5M3 17.5l9 5 9-5" },
    step:     { d: "M5 12h14M13 6l6 6-6 6" },
    flag:     { d: "M6 21V4M6 4h11l-2 4 2 4H6" }
  };
  function icon(parent, kind, cx, cy, size, cls){
    var ic = ICONS[kind] || ICONS.step, s = size / 24;
    var g = el("g", { transform: "translate(" + (cx - 12 * s) + "," + (cy - 12 * s) + ") scale(" + s + ")", "class": "gv-ic" + (ic.fill ? " gv-ic-fill" : "") + (cls ? " " + cls : "") }, parent);
    el("path", { d: ic.d }, g);
    return g;
  }

  // ------------------------------------------------------------------ view
  var uid = 0;
  function GraphView(container, opts){
    this.c = container; this.opts = opts || {};
    this.id = "gv" + (++uid);
    this.sel = null; this.k = 1; this.tx = 0; this.ty = 0; this.cn = 0;
    this.nodes = []; this.edges = []; this.nodeEls = {}; this.edgeEls = [];
    container.classList.add("gv-wrap");
    var keep = [].slice.call(container.querySelectorAll(".gv-keep"));
    container.innerHTML = "";
    var svg = this.svg = el("svg", { "class": "gv-svg", role: "group", "aria-label": this.opts.label || "Diagram", tabindex: "0" }, container);
    var defs = this.defs = el("defs", null, svg);
    var id = this.id;
    function marker(mid, d, cls, w, h, rx){
      var m = el("marker", { id: id + mid, viewBox: "0 0 12 12", refX: rx, refY: 6, markerWidth: w, markerHeight: h, orient: "auto", markerUnits: "userSpaceOnUse" }, defs);
      el("path", { d: d, "class": cls }, m);
    }
    marker("-arrow", "M1.5,1.5 L11,6 L1.5,10.5 L3.8,6 z", "gv-mk", 11, 11, 10.5);
    marker("-tri", "M1,1 L11,6 L1,11 z", "gv-mk-hollow", 13, 13, 11);
    marker("-dia", "M0.5,6 L6,1.5 L11.5,6 L6,10.5 z", "gv-mk-hollow", 13, 13, 0.5);
    marker("-sq", "M2,2 H10 V10 H2 z", "gv-mk", 9, 9, 6);
    var hatch = el("pattern", { id: id + "-hatch", width: 8, height: 8, patternUnits: "userSpaceOnUse", patternTransform: "rotate(45)" }, defs);
    el("rect", { width: 8, height: 8, fill: "none" }, hatch); el("line", { x1: 0, y1: 0, x2: 0, y2: 8, "class": "gv-hatch-line" }, hatch);
    this.vp = el("g", { "class": "gv-vp" }, svg);
    this.gE = el("g", { "class": "gv-edges" }, this.vp);
    this.gN = el("g", { "class": "gv-nodes" }, this.vp);
    keep.forEach(function(k){ container.appendChild(k); });
    var self = this, L = this.opts.labels || {};
    var bar = this.bar = document.createElement("div"); bar.className = "gv-toolbar"; bar.setAttribute("role", "toolbar");
    function btn(txt, title, fn, cls){ var b = document.createElement("button"); b.type = "button"; b.className = cls || ""; b.innerHTML = txt; b.title = title; b.setAttribute("aria-label", title); b.addEventListener("click", function(e){ e.stopPropagation(); fn(); }); bar.appendChild(b); return b; }
    btn('<svg viewBox="0 0 24 24" width="15" height="15" class="gv-ic"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></svg><span>' + (L.fit || "Fit") + "</span>", L.fitT || "Fit to view", function(){ self.fit(); }, "gv-tb-text");
    btn("−", L.out || "Zoom out", function(){ self.zoomBy(0.8); });
    this.pct = document.createElement("span"); this.pct.className = "gv-pct"; this.pct.textContent = "100%"; bar.appendChild(this.pct);
    btn("+", L["in"] || "Zoom in", function(){ self.zoomBy(1.25); });
    btn('<svg viewBox="0 0 24 24" width="15" height="15" class="gv-ic"><path d="M4 12a8 8 0 1 0 3-6.2M4 4v5h5"/></svg>', L.reset || "Reset", function(){ self.reset(); });
    btn('<svg viewBox="0 0 24 24" width="15" height="15" class="gv-ic"><circle cx="12" cy="12" r="3.2"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3"/></svg><span>' + (L.focus || "Focus") + "</span>", L.focusT || "Centre on the selected node", function(){ if (self.sel) self.focus(self.sel); }, "gv-tb-text");
    container.appendChild(bar);
    this._bind();
  }
  GraphView.prototype._apply = function(){
    this.vp.setAttribute("transform", "translate(" + this.tx + "," + this.ty + ") scale(" + this.k + ")");
    var s = 24 * this.k; this.c.style.backgroundSize = s + "px " + s + "px"; this.c.style.backgroundPosition = this.tx + "px " + this.ty + "px";
    if (this.pct) this.pct.textContent = Math.round(this.k * 100) + "%";
  };
  GraphView.prototype.zoomBy = function(f, cx, cy){
    var r = this.svg.getBoundingClientRect();
    cx = cx == null ? r.width / 2 : cx; cy = cy == null ? r.height / 2 : cy;
    var nk = Math.max(0.2, Math.min(3, this.k * f)); f = nk / this.k;
    this.tx = cx - (cx - this.tx) * f; this.ty = cy - (cy - this.ty) * f; this.k = nk; this._apply();
  };
  GraphView.prototype.fit = function(){
    var r = this.svg.getBoundingClientRect(); if (!this.W || !r.width) return;
    var k = Math.min((r.width - 40) / this.W, (r.height - 40) / this.H, 1.25); k = Math.max(0.2, k);
    this.k = k; this.tx = (r.width - this.W * k) / 2; this.ty = Math.max(20, (r.height - this.H * k) / 2); this._apply();
  };
  // Initial view: the whole diagram when it stays legible (>= 0.7x); otherwise a readable zoom anchored on the entry node.
  GraphView.prototype.smartFit = function(){
    var r = this.svg.getBoundingClientRect(); if (!this.W || !r.width) return;
    var fitK = Math.min((r.width - 40) / this.W, (r.height - 40) / this.H, 1.25);
    if (fitK >= (this.opts.minFit || 0.62)) { this.fit(); return; }
    var first = this.nodes[0], minY = Infinity;
    this.nodes.forEach(function(n){ if (n.shape === "start" && first.shape !== "start") first = n; if (n.y < minY) minY = n.y; });
    this.k = 0.85; this.tx = 36 - (first ? first.x : 0) * 0.85; this.ty = Math.max(28 - minY * 0.85, (r.height - this.H * 0.85) / 2); this._apply();
  };
  GraphView.prototype.reset = function(){ this.k = 1; this.tx = 24; this.ty = 24; this._apply(); };
  GraphView.prototype.focus = function(id){
    var n = this.byId && this.byId[id]; if (!n) return;
    var r = this.svg.getBoundingClientRect(), k = Math.max(this.k, 0.85);
    this.k = k; this.tx = r.width / 2 - (n.x + n.w / 2) * k; this.ty = r.height / 2 - (n.y + n.h / 2) * k; this._apply();
  };
  GraphView.prototype._bind = function(){
    var self = this, svg = this.svg, drag = null, moved = false;
    svg.addEventListener("wheel", function(e){ e.preventDefault(); var r = svg.getBoundingClientRect(); self.zoomBy(e.deltaY < 0 ? 1.1 : 0.9, e.clientX - r.left, e.clientY - r.top); }, { passive: false });
    svg.addEventListener("pointerdown", function(e){
      if (e.target.closest && e.target.closest(".gv-node")) return;
      drag = { x: e.clientX, y: e.clientY, tx: self.tx, ty: self.ty }; moved = false; svg.setPointerCapture(e.pointerId); svg.classList.add("gv-drag");
    });
    svg.addEventListener("pointermove", function(e){
      if (!drag) return; var dx = e.clientX - drag.x, dy = e.clientY - drag.y;
      if (Math.abs(dx) + Math.abs(dy) > 3) moved = true;
      self.tx = drag.tx + dx; self.ty = drag.ty + dy; self._apply();
    });
    svg.addEventListener("pointerup", function(e){
      if (!drag) return; drag = null; svg.classList.remove("gv-drag");
      try { svg.releasePointerCapture(e.pointerId); } catch (x) {}
      if (!moved) self.select(null, true);
    });
    svg.addEventListener("keydown", function(e){
      if (e.key === "+" || e.key === "=") self.zoomBy(1.2); else if (e.key === "-") self.zoomBy(0.83);
      else if (e.key === "0") self.reset(); else if (e.key === "f" || e.key === "F") self.fit();
      else if (e.key === "Escape") self.select(null, true);
    });
  };

  // ---- layout through ELK (hydra-layout-elk.js + vendor/elk.bundled.js); the renderer only draws the returned geometry --------------
  var STRUCT_KINDS = { contains: 1, ref: 1, isa: 1, gen: 1 };
  function labelText(e){
    var st = EDGE[e.kind] || EDGE.next, lab = e.label || "", gl = st.glyph || EDGE_GLYPH[e.kind] || "";
    var txt = gl && lab ? gl + " " + lab : (lab || gl);
    if (e.kind === "next" && !lab) txt = "";
    if (STRUCT_KINDS[e.kind]) txt = lab;
    return txt;
  }
  var _elk = null;
  function getElk(){ if (!_elk && typeof ELK === "function") { try { _elk = new ELK(); } catch (x) {} } return _elk; }
  var DEBUG_LAYOUT = (function(){ try { return /[?&]layoutdebug=1/.test(location.search) || localStorage.getItem("hydra-layout-debug") === "1"; } catch (x) { return false; } })();
  function debugOn(){ return DEBUG_LAYOUT; }
  // last-resort layout when ELK could not be loaded: plain columns by longest-path rank with elbow connectors (clearly degraded, never silent)
  function fallbackLayout(nodes, edges){
    var rank = {}; nodes.forEach(function(n){ rank[n.id] = 0; });
    for (var it = 0; it < nodes.length; it++) edges.forEach(function(e){ if (e.kind !== "loop" && rank[e.b] <= rank[e.a] && rank[e.a] + 1 < nodes.length) rank[e.b] = rank[e.a] + 1; });
    var col = {}, x = 40, maxH = 0, widths = {};
    nodes.forEach(function(n){ sizeNode(n); (col[rank[n.id]] = col[rank[n.id]] || []).push(n); widths[rank[n.id]] = Math.max(widths[rank[n.id]] || 0, n.w); });
    Object.keys(col).map(Number).sort(function(a, b){ return a - b; }).forEach(function(r){ var y = 40; col[r].forEach(function(n){ n.x = x; n.y = y; y += n.h + 40; }); maxH = Math.max(maxH, y); x += widths[r] + 110; });
    var by = {}; nodes.forEach(function(n){ by[n.id] = n; });
    var routes = edges.filter(function(e){ return by[e.a] && by[e.b]; }).map(function(e){
      var a = by[e.a], b = by[e.b], s = { x: a.x + a.w, y: a.y + a.h / 2 }, t = { x: b.x, y: b.y + b.h / 2 }, mx = (s.x + t.x) / 2;
      return { e: e, pts: [s, { x: mx, y: s.y }, { x: mx, y: t.y }, t], poly: true, back: false, label: null };
    });
    return { nodes: nodes, routes: routes, width: x + 40, height: maxH + 40, cand: "fallback", metrics: null };
  }

  GraphView.prototype.setData = function(data){
    var self = this, tok = this._tok = (this._tok || 0) + 1;
    this.nodes = data.nodes.map(function(n){ var c = {}; Object.keys(n).forEach(function(k){ c[k] = n[k]; }); return c; });
    this.edges = data.edges.map(function(e){ var c = {}; Object.keys(e).forEach(function(k){ c[k] = e[k]; }); return c; });
    this.sel = null; this.nodeEls = {}; this.edgeEls = [];
    if (this._pb) { this._pb.pause(); this._pb = null; }
    this.gE.innerHTML = ""; this.gN.innerHTML = ""; if (this.gD) { this.gD.remove(); this.gD = null; }
    this.c.classList.add("gv-busy");
    this.nodes.forEach(sizeNode);
    var r0 = this.svg.getBoundingClientRect(), elk = getElk(), HL = window.HydraLayout, p;
    if (elk && HL) {
      p = HL.layout(elk, this.nodes, this.edges, { family: data.family || "process", viewport: { w: r0.width || 1260, h: r0.height || 760 },
        labelText: data.hideLabels ? function(){ return ""; } : labelText, labelSize: function(t){ return { w: measure(t, true, 11) + 16, h: 22 }; } })
        .catch(function(){ return fallbackLayout(self.nodes, self.edges); });
    } else p = Promise.resolve(fallbackLayout(this.nodes, this.edges));
    this.ready = p.then(function(L){
      if (tok !== self._tok) return self; // a newer setData superseded this one
      self.c.classList.remove("gv-busy"); self._render(L, data); return self;
    });
    return this.ready;
  };

  GraphView.prototype._render = function(L, data){
    var self = this;
    this.layoutInfo = L; this.W = L.width; this.H = L.height; this.nodes = L.nodes;
    this.byId = {}; this.nodes.forEach(function(n){ self.byId[n.id] = n; });
    L.routes.forEach(function(r){
      var st = EDGE[r.e.kind] || EDGE.next, d = polyPath(r.pts, 7);
      var g = el("g", { "class": "gv-edge gv-e-" + (r.e.kind || "next"), "data-a": r.e.a, "data-b": r.e.b }, self.gE);
      if (st.dbl) el("path", { d: d, "class": "gv-edge-halo", "stroke-width": st.w + 2.6, fill: "none" }, g);
      var path = el("path", { d: d, "class": "gv-edge-line", "stroke-width": st.dbl ? 1.5 : st.w, "stroke-dasharray": st.dash, "stroke-linecap": st.cap || "butt", fill: "none" }, g);
      if (st.mark === "dia" || st.mark === "tri") path.setAttribute("marker-start", "url(#" + self.id + "-" + st.mark + ")");
      else if (st.mark) path.setAttribute("marker-end", "url(#" + self.id + "-" + st.mark + ")");
      var lab = r.label, full = labelText(r.e), pos = null, tw = 0, shown = "";
      if (lab) { pos = { x: lab.x + lab.w / 2, y: lab.y + lab.h / 2 }; tw = lab.w; shown = lab.text; }
      else if (data.hideLabels && full) { pos = midpoint(r); shown = full.length > 34 ? full.slice(0, 33) + "…" : full; tw = measure(shown, true, 11) + 16; }
      if (pos && shown) {
        var lg = el("g", { "class": "gv-elabel" + (data.hideLabels ? " gv-lbl-hidden" : ""), transform: "translate(" + pos.x + "," + pos.y + ")" }, g);
        el("rect", { x: -tw / 2, y: -10, width: tw, height: 20, rx: 10 }, lg);
        var t = el("text", { "text-anchor": "middle", y: 4 }, lg); t.textContent = shown;
        if (full !== shown) { var tt0 = el("title", null, lg); tt0.textContent = full; }
      }
      if (r.e.card1 || r.e.card2) {
        var p0 = r.pts[0], p1 = r.pts[r.pts.length - 1];
        if (r.e.card1) { var c1 = el("text", { "class": "gv-card-n", x: p0.x + 6, y: p0.y - 7 }, g); c1.textContent = r.e.card1; }
        if (r.e.card2) { var c2 = el("text", { "class": "gv-card-n", x: p1.x - 6, y: p1.y - 7, "text-anchor": "end" }, g); c2.textContent = r.e.card2; }
      }
      self.edgeEls.push({ g: g, e: r.e, path: path });
    });
    this.nodes.forEach(function(n){ self._drawNode(n); });
    // attachment points: ports are drawn where the routed edges really start / end
    var seen = {};
    L.routes.forEach(function(r){
      [[r.pts[0], r.e.a, "out"], [r.pts[r.pts.length - 1], r.e.b, "in"]].forEach(function(q){
        var n = self.byId[q[1]], g = self.nodeEls[q[1]], k = q[1] + "|" + Math.round(q[0].x) + "|" + Math.round(q[0].y); if (!n || !g || seen[k]) return; seen[k] = 1;
        el("circle", { cx: q[0].x - n.x, cy: q[0].y - n.y, r: 4, "class": "gv-port gv-port-" + q[2] }, g);
      });
    });
    this.c.classList.add("gv-ready");
    if (debugOn()) this._debug(L);
    requestAnimationFrame(function(){ self.smartFit(); });
  };

  // development aid (?layoutdebug=1 or localStorage hydra-layout-debug=1; never on by default): boxes, bend points, ports, crossings, score
  GraphView.prototype._debug = function(L){
    var g = this.gD = el("g", { "class": "gv-debug", "pointer-events": "none" }, this.vp), HL = window.HydraLayout;
    this.nodes.forEach(function(n){ el("rect", { x: n.x, y: n.y, width: n.w, height: n.h, fill: "none", stroke: "#e0457b", "stroke-dasharray": "3 3" }, g); });
    L.routes.forEach(function(r){ r.pts.forEach(function(p, i){ el("circle", { cx: p.x, cy: p.y, r: i === 0 || i === r.pts.length - 1 ? 3.5 : 2.5, fill: i === 0 || i === r.pts.length - 1 ? "#2a7de1" : "#e0a030" }, g); }); if (r.label) el("rect", { x: r.label.x, y: r.label.y, width: r.label.w, height: r.label.h, fill: "none", stroke: "#9b59b6", "stroke-dasharray": "2 2" }, g); });
    var m = L.metrics || (HL && HL.metrics(L)), cps = HL && HL.crossingPoints ? HL.crossingPoints(L) : [];
    cps.forEach(function(p){ var x = el("g", { transform: "translate(" + p.x + "," + p.y + ")" }, g); el("path", { d: "M-6,-6L6,6M6,-6L-6,6", stroke: "#d6264a", "stroke-width": 3 }, x); });
    var box = this.c.querySelector(".gv-debugpanel"); if (!box) { box = document.createElement("div"); box.className = "gv-debugpanel gv-keep"; this.c.appendChild(box); }
    box.textContent = m ? "layout " + (L.cand || "?") + " · crossings " + m.EDGE_EDGE_CROSSINGS + " · edge×node " + m.EDGE_NODE_INTERSECTIONS + " · label×node " + m.LABEL_NODE_OVERLAPS + " · bends " + m.TOTAL_BENDS + " · score " + Math.round(m.SCORE) : "layout fallback (ELK unavailable)";
  };

  function reqDash(req){ return req === "OPTIONAL" ? "7 5" : req === "CONDITIONAL" ? "2 4" : req === "DERIVED" ? "9 3 2 3" : ""; }

  GraphView.prototype._drawNode = function(n){
    var self = this, f = n.shape || "step", w = n.w, h = n.h;
    var g = el("g", { "class": "gv-node gv-f-" + f + (n.req ? " gv-r-" + n.req : "") + (n.actor ? " gv-a-" + n.actor : ""), transform: "translate(" + n.x + "," + n.y + ")", tabindex: "0", role: "button", "data-id": n.id }, this.gN);
    g.setAttribute("aria-label", [n.label, n.kindLabel, n.capLabel, n.tag].filter(Boolean).join(" · "));
    var stripe = n.color || "var(--accent)";
    var dash = reqDash(n.req);
    var cid = this.id + "-c" + (this.cn++);

    if (f === "start" || f === "end") {
      el("rect", { x: -5, y: -5, width: w + 10, height: h + 10, rx: (h + 10) / 2, "class": "gv-halo" }, g);
      el("rect", { x: 0, y: 0, width: w, height: h, rx: h / 2, "class": "gv-card gv-terminal gv-terminal-" + f }, g);
      if (f === "end") el("rect", { x: 5, y: 5, width: w - 10, height: h - 10, rx: (h - 10) / 2, "class": "gv-terminal-ring" }, g);
      var ib = el("circle", { cx: h / 2 + 4, cy: h / 2, r: 14, "class": "gv-terminal-badge" }, g);
      icon(g, f === "start" ? "play" : "flag", h / 2 + 4, h / 2, 17, "gv-ic-on");
      var ls = n.lines || [n.label], y0 = h / 2 - (ls.length - 1) * 8.5 + 4.5;
      ls.forEach(function(l, i){ var t = el("text", { "class": "gv-title gv-title-on", x: h / 2 + 26, y: y0 + i * 17 }, g); t.textContent = l; });
    } else if (f === "decision") {
      var pts = (w / 2) + ",0 " + w + "," + (h / 2) + " " + (w / 2) + "," + h + " 0," + (h / 2);
      el("polygon", { points: (w / 2) + ",-6 " + (w + 8) + "," + (h / 2) + " " + (w / 2) + "," + (h + 6) + " -8," + (h / 2), "class": "gv-halo gv-halo-poly" }, g);
      el("polygon", { points: pts, "class": "gv-card gv-decision" }, g);
      icon(g, "branch", w / 2, 22, 15, "gv-ic-dim");
      var dl = n.lines || [n.label], dy = h / 2 - (dl.length - 1) * 8.5 + 7;
      dl.forEach(function(l, i){ var t = el("text", { "class": "gv-title gv-title-c", x: w / 2, y: dy + i * 17, "text-anchor": "middle" }, g); t.textContent = l; });
    } else {
      var rx = f === "variant" ? 22 : 14;
      el("rect", { x: -5, y: -5, width: w + 10, height: h + 10, rx: rx + 5, "class": "gv-halo" }, g);
      if (n.req === "REPEATABLE") { el("rect", { x: 7, y: -7, width: w, height: h, rx: rx, "class": "gv-card gv-stack" }, g); el("rect", { x: 3.5, y: -3.5, width: w, height: h, rx: rx, "class": "gv-card gv-stack" }, g); }
      var body;
      if (f === "artifact") {
        var fo = 16;
        body = el("path", { d: "M" + rx + ",0 H" + (w - fo) + " L" + w + "," + fo + " V" + (h - rx) + " Q" + w + "," + h + " " + (w - rx) + "," + h + " H" + rx + " Q0," + h + " 0," + (h - rx) + " V" + rx + " Q0,0 " + rx + ",0 Z", "class": "gv-card gv-artifact", "stroke-dasharray": dash }, g);
        el("path", { d: "M" + (w - fo) + ",0 V" + fo + " H" + w, "class": "gv-fold" }, g);
      } else {
        body = el("rect", { x: 0, y: 0, width: w, height: h, rx: rx, "class": "gv-card" + (f === "external" ? " gv-ext" : "") + (f === "derived" ? " gv-derived" : ""), "stroke-dasharray": f === "external" ? "6 4" : dash }, g);
      }
      if (f === "external") el("rect", { x: 1, y: 1, width: w - 2, height: h - 2, rx: rx - 1, fill: "url(#" + this.id + "-hatch)", "class": "gv-hatch", "pointer-events": "none" }, g);
      if (n.req === "ALTERNATIVE") el("rect", { x: 4.5, y: 4.5, width: w - 9, height: h - 9, rx: rx - 4, "class": "gv-inner" }, g);
      // capability accent bar (clipped to the card outline)
      var cp = el("clipPath", { id: cid }, this.defs); el("rect", { x: 0, y: 0, width: w, height: h, rx: rx }, cp);
      el("rect", { x: 0, y: 0, width: w, height: 5, fill: stripe, "clip-path": "url(#" + cid + ")", "class": "gv-bar5" }, g);
      // header: icon badge + kind/capability label + requiredness chip
      var iconKind = f === "step" ? (n.actor === "user" ? "user" : n.actor === "auto" ? "auto" : n.actor === "ext" ? "ext" : "step") :
        f === "external" ? "ext" : f === "artifact" ? "doc" : f === "concept" || f === "cap" ? (f === "cap" ? "layers" : "cube") : f === "variant" ? "tag" : f === "derived" ? "spark" : "step";
      el("circle", { cx: 27, cy: 30, r: 12, "class": "gv-badge-c gv-badge-" + (n.actor || f) }, g);
      icon(g, iconKind, 27, 30, 15);
      var head = n.capLabel || n.kindLabel || "";
      var chipW = 0;
      if (n.tag) {
        var cw = measure(n.tag, true, 9.5) * 1 + 16; chipW = cw + 6;
        el("rect", { x: w - cw - 12, y: 21, width: cw, height: 18, rx: 9, "class": "gv-chip gv-chip-" + (n.req || "x") }, g);
        var ct = el("text", { "class": "gv-chip-t", x: w - cw / 2 - 12, y: 33.4, "text-anchor": "middle" }, g); ct.textContent = n.tag;
      }
      if (head) {
        var hmax = w - 52 - 14 - chipW, ht = head; while (ht.length > 3 && measure(ht, true, 10) > hmax) ht = ht.slice(0, -2);
        if (ht !== head) ht = ht.replace(/\s+$/, "") + "…";
        var hx = el("text", { "class": "gv-kicker", x: 46, y: 34 }, g); hx.textContent = ht;
        if (ht !== head) { var htl = el("title", null, hx); htl.textContent = head; }
      }
      var ls2 = n.lines || [n.label], ty = 14 + 22 + 9 + 13;
      ls2.forEach(function(l, i){ var t = el("text", { "class": "gv-title", x: 16, y: ty + i * TITLE_LH }, g); t.textContent = l; });
      var sy = ty + (ls2.length - 1) * TITLE_LH + 5 + 11;
      (n.subLines || []).forEach(function(l, i){ var t = el("text", { "class": "gv-sub", x: 16, y: sy + i * SUB_LH }, g); t.textContent = l; });
    }
    if (n.evid) { // test-evidence indicator (glyph + text title; never colour only)
      var ex = f === "decision" ? w - 30 : w - 15, ey = f === "decision" ? h - 26 : h - 14, EVG = { ok: "✓", partial: "◐", stale: "⚠", none: "○" };
      var eb = el("g", { "class": "gv-evb gv-evb-" + n.evid, transform: "translate(" + ex + "," + ey + ")" }, g);
      el("circle", { r: 8.5 }, eb); var et = el("text", { "class": "gv-evt", y: 3.8 }, eb); et.textContent = EVG[n.evid] || "";
      var ett = el("title", null, eb); ett.textContent = (window.HydraTestsUI && window.HydraTestsUI.stageTitle(n.evid)) || n.evid;
    }    g.addEventListener("click", function(e){ e.stopPropagation(); self.select(n.id, true); });
    g.addEventListener("mouseenter", function(){ if (!self.sel) self._focus(n.id); });
    g.addEventListener("mouseleave", function(){ if (!self.sel) self._focus(null); });
    g.addEventListener("keydown", function(e){ if (e.key === "Enter" || e.key === " ") { e.preventDefault(); self.select(n.id, true); } });
    this.nodeEls[n.id] = g;
  };

  // ---- focus (hover/selection) -------------------------------------------------------------------------------------------
  GraphView.prototype._focus = function(id){
    var self = this, conn = {};
    if (id) this.edges.forEach(function(e){ if (e.a === id) conn[e.b] = 1; if (e.b === id) conn[e.a] = 1; });
    Object.keys(this.nodeEls).forEach(function(k){ var g = self.nodeEls[k]; g.classList.toggle("gv-dim", !!id && k !== id && !conn[k]); g.classList.toggle("gv-nb", !!id && !!conn[k]); });
    this.edgeEls.forEach(function(x){ x.g.classList.toggle("gv-dim", !!id && x.e.a !== id && x.e.b !== id); x.g.classList.toggle("gv-hot", !!id && (x.e.a === id || x.e.b === id)); });
  };
  GraphView.prototype.select = function(id, notify){
    if (this.sel && this.nodeEls[this.sel]) this.nodeEls[this.sel].classList.remove("gv-sel");
    this.sel = id; this._focus(id);
    if (id && this.nodeEls[id]) { this.nodeEls[id].classList.add("gv-sel"); this.gN.appendChild(this.nodeEls[id]); }
    if (notify && this.opts.onSelect) this.opts.onSelect(id);
  };

  // ---- process playback (follows the REAL rendered connection geometry) ------------------------------------------------------
  function Playback(view){
    this.v = view; this.speed = 1; this.optional = false; this.path = []; this.cur = null; this.timer = null; this.playing = false; this.busy = false;
    this.loopUsed = {}; this.choice = {}; this.pending = null; this.finished = false; this.cb = null; this.dot = null; this.visited = [];
    this.reduce = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  }
  Playback.prototype.outs = function(id){ return this.v.edges.filter(function(e){ return e.a === id; }); };
  Playback.prototype.startNode = function(){
    var v = this.v, hasIn = {}; v.edges.forEach(function(e){ if (e.kind !== "loop") hasIn[e.b] = 1; });
    var s = v.nodes.filter(function(n){ return n.shape === "start"; })[0] || v.nodes.filter(function(n){ return !hasIn[n.id]; })[0] || v.nodes[0];
    return s && s.id;
  };
  Playback.prototype._clear = function(){
    var v = this.v; Object.keys(v.nodeEls).forEach(function(k){ v.nodeEls[k].classList.remove("gv-active", "gv-done"); });
    v.edgeEls.forEach(function(x){ x.g.classList.remove("gv-visited", "gv-playing"); });
    if (this.dot && this.dot.parentNode) this.dot.parentNode.removeChild(this.dot); this.dot = null;
  };
  Playback.prototype.restart = function(){
    this.pause(); this._clear(); this.loopUsed = {}; this.choice = {}; this.pending = null; this.finished = false; this.visited = [];
    var s = this.startNode(); this.path = s ? [s] : []; this.cur = s || null; if (s) this._activate(s, null, true);
  };
  Playback.prototype._emit = function(extra){ if (this.cb) { var st = { node: this.cur, step: this.path.length, playing: this.playing, finished: this.finished, pending: this.pending ? this.pending.opts : null, speed: this.speed, optional: this.optional }; Object.keys(extra || {}).forEach(function(k){ st[k] = extra[k]; }); this.cb(st); } };
  Playback.prototype._activate = function(id, edge, silent){
    var v = this.v, prev = this.prevNode; this.cur = id;
    Object.keys(v.nodeEls).forEach(function(k){ v.nodeEls[k].classList.remove("gv-active"); });
    if (this.path.length > 1 || edge) { var pn = this.path[this.path.length - 2]; if (pn && v.nodeEls[pn]) v.nodeEls[pn].classList.add("gv-done"); }
    if (v.nodeEls[id]) v.nodeEls[id].classList.add("gv-active");
    v.select(id, false);
    var r = v.svg.getBoundingClientRect(), n = v.byId[id];
    if (n) { var sx = v.tx + (n.x + n.w / 2) * v.k, sy = v.ty + (n.y + n.h / 2) * v.k; if (sx < 60 || sy < 60 || sx > r.width - 60 || sy > r.height - 60) v.focus(id); }
    var outs = this.outs(id), end = !outs.length || (n && n.shape === "end");
    this.finished = !!end; if (end) { this.playing = false; if (this.timer) clearTimeout(this.timer); }
    var pnode = edge ? v.byId[edge.a] : null, cross = pnode && n && pnode.cap && n.cap && pnode.cap !== n.cap ? { from: pnode.cap, to: n.cap } : null;
    this._decide(); this._emit({ edge: edge, cross: cross, silent: !!silent });
  };
  Playback.prototype._decide = function(){
    var id = this.cur, outs = this.outs(id), n = this.v.byId[id];
    if (this.finished || outs.length < 2 || !(n && n.shape === "decision")) { this.pending = null; return; }
    this.pending = this.choice[id] != null ? null : { id: id, opts: outs.map(function(e, i){ return { i: i, label: e.label || "", kind: e.kind, to: e.b }; }) };
  };
  Playback.prototype.choose = function(i){ var id = this.cur; this.choice[id] = i; this.pending = null; this._emit({}); if (this.playing) this._tick(); };
  Playback.prototype._pick = function(){
    var id = this.cur, outs = this.outs(id), n = this.v.byId[id]; if (!outs.length) return null;
    if (this.choice[id] != null && outs[this.choice[id]]) { var ch = outs[this.choice[id]]; delete this.choice[id]; return ch; }
    var self = this, fwd = outs.filter(function(e){ return e.kind !== "loop"; }), prim = fwd.filter(function(e){ return e.kind === "next" || e.kind === "x" || e.kind === "alt"; }), opt = fwd.filter(function(e){ return e.kind === "opt" || e.kind === "cond" || e.kind === "event"; });
    if (n && n.shape === "decision" && outs.length > 1) return prim[0] || fwd[0] || outs[0];
    if (this.optional && opt.length) { var o = opt.filter(function(e){ return !self.loopUsed[e.a + ">" + e.b]; })[0]; if (o) { this.loopUsed[o.a + ">" + o.b] = 1; return o; } }
    return prim[0] || opt[0] || fwd[0] || outs[0];
  };
  Playback.prototype._move = function(edge, done){
    var v = this.v, x = null, self = this; v.edgeEls.forEach(function(q){ if (q.e === edge) x = q; });
    if (edge.kind === "loop") this.loopUsed[edge.a + ">" + edge.b] = 1;
    var finish = function(){ if (x) { x.g.classList.remove("gv-playing"); x.g.classList.add("gv-visited"); } if (self.dot && self.dot.parentNode) self.dot.parentNode.removeChild(self.dot); self.dot = null; self.busy = false; self.path.push(edge.b); self._activate(edge.b, edge, false); if (done) done(); };
    if (!x) { finish(); return; }
    this.busy = true; x.g.classList.add("gv-playing");
    if (this.reduce) { this.timer = setTimeout(finish, 450 / this.speed); return; }
    var len = 0; try { len = x.path.getTotalLength(); } catch (e) {}
    var dur = Math.min(2600, Math.max(420, len / (0.42 * this.speed))), t0 = null, dot = this.dot = el("circle", { r: 6.5, "class": "gv-dot" }, v.vp);
    var tick = function(ts){
      if (self.dot !== dot) return; if (t0 === null) t0 = ts; var f = Math.min(1, (ts - t0) / dur), pt = x.path.getPointAtLength(len * f);
      dot.setAttribute("cx", pt.x); dot.setAttribute("cy", pt.y);
      if (f < 1) requestAnimationFrame(tick); else finish();
    };
    requestAnimationFrame(tick);
  };
  Playback.prototype.step = function(){
    if (this.busy || this.finished || this.pending) return false;
    var e = this._pick(); if (!e) { this.finished = true; this._emit({}); return false; }
    this._move(e); return true;
  };
  Playback.prototype._tick = function(){
    var self = this; if (!this.playing) return;
    if (this.finished) { this.playing = false; this._emit({}); return; }
    if (this.pending) { this.timer = setTimeout(function(){ if (self.playing && self.pending) { self.choose(self.pending.opts[self.defaultIndex()].i); } }, 1500 / this.speed); return; }
    if (this.busy) { this.timer = setTimeout(function(){ self._tick(); }, 120); return; }
    this.step(); this.timer = setTimeout(function(){ self._tick(); }, 1100 / this.speed + (this.reduce ? 0 : 700 / this.speed));
  };
  Playback.prototype.defaultIndex = function(){ var o = this.pending ? this.pending.opts : []; for (var i = 0; i < o.length; i++) if (o[i].kind !== "loop") return i; return 0; };
  Playback.prototype.play = function(){ if (this.finished) this.restart(); if (!this.path.length) this.restart(); this.playing = true; this._emit({}); this._tick(); };
  Playback.prototype.pause = function(){ this.playing = false; if (this.timer) { clearTimeout(this.timer); this.timer = null; } this._emit({}); };
  Playback.prototype.next = function(){ this.pause(); if (this.busy) return; if (!this.path.length) this.restart(); else if (this.pending) this.choose(this.defaultIndex()); else this.step(); };
  Playback.prototype.prev = function(){
    this.pause(); if (this.busy || this.path.length < 2) return;
    var last = this.path.pop(), v = this.v, from = this.path[this.path.length - 1];
    v.edgeEls.forEach(function(q){ if (q.e.a === from && q.e.b === last) q.g.classList.remove("gv-visited"); });
    if (v.nodeEls[last]) v.nodeEls[last].classList.remove("gv-done", "gv-active"); this.finished = false; delete this.choice[from];
    this._activate(from, null, true);
  };
  Playback.prototype.setSpeed = function(s){ this.speed = s; this._emit({}); };
  Playback.prototype.setOptional = function(b){ this.optional = !!b; this._emit({}); };
  GraphView.prototype.playback = function(){ if (!this._pb) this._pb = new Playback(this); return this._pb; };
  GraphView.prototype.stopPlayback = function(){ if (this._pb) { this._pb.pause(); this._pb._clear(); } };

  // Shared CSS (theme aware through the page's custom properties)
  GraphView.CSS = [
    ".gv-wrap{position:relative;overflow:hidden;border:1px solid var(--line);border-radius:14px;min-height:380px;background-color:var(--canvas);background-image:radial-gradient(circle, var(--grid-dot) 1.1px, transparent 1.3px);background-size:24px 24px;}",
    ".gv-svg{width:100%;height:100%;display:block;cursor:grab;touch-action:none;outline:none;font-family:" + FAMILY + ";}",
    ".gv-svg.gv-drag{cursor:grabbing;}",
    ".gv-toolbar{position:absolute;top:12px;left:12px;display:flex;align-items:center;gap:2px;padding:4px;background:var(--panel);border:1px solid var(--line);border-radius:12px;box-shadow:0 4px 14px rgba(0,0,0,.10);z-index:3;}",
    ".gv-toolbar button{appearance:none;border:none;background:transparent;color:var(--ink);min-width:30px;height:30px;border-radius:8px;font:600 14px/1 " + FAMILY + ";cursor:pointer;display:inline-flex;align-items:center;justify-content:center;gap:6px;padding:0 8px;}",
    ".gv-toolbar button:hover{background:var(--chip);} .gv-toolbar button:focus-visible{outline:2px solid var(--accent);}",
    ".gv-toolbar .gv-tb-text span{font-size:12px;font-weight:600;} .gv-pct{font:600 11.5px " + FAMILY + ";color:var(--ink-dim);min-width:42px;text-align:center;}",
    ".gv-ic{fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round;} .gv-ic-fill{fill:currentColor;} .gv-ic-dim{color:var(--ink-dim);} .gv-ic-on{color:var(--accent-ink);}",
    ".gv-node .gv-ic{color:var(--ink);}",
    ".gv-card{fill:var(--card);stroke:var(--card-line);stroke-width:1.5;filter:drop-shadow(0 1px 1.5px rgba(10,20,30,.10)) drop-shadow(0 5px 12px rgba(10,20,30,.06));}",
    ".gv-r-MANDATORY .gv-card:not(.gv-stack){stroke:var(--card-line-strong);stroke-width:1.7;}",
    ".gv-r-OPTIONAL .gv-card:not(.gv-stack){stroke-width:1.7;} .gv-r-CONDITIONAL .gv-card:not(.gv-stack){stroke-width:2;} .gv-r-DERIVED .gv-card:not(.gv-stack){stroke-width:1.7;}",
    ".gv-stack{fill:var(--chip);filter:none;} .gv-inner{fill:none;stroke:var(--card-line-strong);stroke-width:1;}",
    ".gv-artifact{fill:var(--artifact);} .gv-fold{fill:none;stroke:var(--card-line-strong);stroke-width:1.3;}",
    ".gv-ext{fill:var(--card);} .gv-hatch-line{stroke:var(--ink-dim);stroke-width:1;opacity:.22;} .gv-hatch{opacity:.55;}",
    ".gv-derived{fill:var(--artifact);}",
    ".gv-decision{fill:var(--decision);stroke:var(--card-line-strong);stroke-width:1.7;stroke-linejoin:round;}",
    ".gv-f-variant .gv-card{fill:var(--chip);}",
    ".gv-terminal{stroke:none;} .gv-terminal-start{fill:var(--accent);} .gv-terminal-end{fill:var(--accent);} .gv-terminal-ring{fill:none;stroke:var(--accent-ink);stroke-width:1.3;opacity:.7;}",
    ".gv-terminal-badge{fill:rgba(255,255,255,.18);} .gv-title-on{fill:var(--accent-ink);}",
    ".gv-bar5{opacity:.9;} .gv-badge-c{fill:var(--chip);stroke:var(--card-line);stroke-width:1;}",
    ".gv-badge-user{fill:color-mix(in srgb, #4a90c9 18%, var(--card));} .gv-badge-auto{fill:color-mix(in srgb, #1f8a6f 18%, var(--card));} .gv-badge-ext{fill:color-mix(in srgb, #c9994a 22%, var(--card));}",
    ".gv-title{fill:var(--ink);font-weight:650;font-size:13.5px;pointer-events:none;letter-spacing:-.005em;} .gv-title-c{font-size:13px;}",
    ".gv-sub{fill:var(--ink-dim);font-size:11.5px;pointer-events:none;} .gv-kicker{fill:var(--ink-dim);font-size:10px;font-weight:650;letter-spacing:.05em;text-transform:uppercase;pointer-events:none;}",
    ".gv-chip{fill:var(--chip);stroke:var(--card-line);stroke-width:1;} .gv-chip-t{fill:var(--ink);font-size:9.5px;font-weight:700;letter-spacing:.04em;text-transform:uppercase;pointer-events:none;}",
    ".gv-chip-OPTIONAL{stroke-dasharray:3 2;} .gv-chip-CONDITIONAL{stroke-dasharray:1 2;stroke:var(--warn);} .gv-chip-ALTERNATIVE{stroke:#4a90c9;} .gv-chip-REPEATABLE{stroke:#8a5fc9;} .gv-chip-DERIVED{stroke-dasharray:4 2 1 2;stroke:#2aa7a0;}",
    ".gv-wrap.gv-busy .gv-vp{opacity:.35;transition:opacity .15s;} .gv-wrap.gv-ready .gv-vp{animation:gv-enter .22s ease-out;} @keyframes gv-enter{from{opacity:.25;}to{opacity:1;}} .gv-debugpanel{position:absolute;left:12px;top:12px;z-index:4;background:#1b1f23;color:#fff;font:11px/1.4 monospace;padding:5px 9px;border-radius:7px;pointer-events:none;}",
    ".gv-evb circle{fill:var(--card);stroke:var(--ink-dim);stroke-width:1.5;} .gv-evb-ok circle{stroke:var(--accent);} .gv-evb-partial circle{stroke:#c9994a;} .gv-evb-stale circle{stroke:#c25555;} .gv-evb-none circle{stroke-dasharray:2 2;opacity:.8;} .gv-evt{font:700 10px sans-serif;text-anchor:middle;fill:var(--ink);pointer-events:none;} .gv-evb-none .gv-evt{opacity:.7;}",
    ".gv-port{fill:var(--card);stroke:var(--ink-dim);stroke-width:1.5;opacity:.75;transition:r .12s,opacity .12s;} .gv-port-v{opacity:0;}",
    ".gv-node:hover .gv-port,.gv-sel .gv-port{opacity:1;r:5.5;stroke:var(--accent);} .gv-node:hover .gv-port-v,.gv-sel .gv-port-v{opacity:1;r:4.5;}",
    ".gv-halo{fill:none;stroke:var(--accent);stroke-width:0;opacity:.0;pointer-events:none;} .gv-halo-poly{stroke-linejoin:round;}",
    ".gv-node{cursor:pointer;outline:none;transition:opacity .18s;}",
    ".gv-node:hover .gv-card{stroke:var(--accent);filter:drop-shadow(0 2px 3px rgba(10,20,30,.14)) drop-shadow(0 8px 18px rgba(10,20,30,.12));}",
    ".gv-node:focus-visible .gv-halo{stroke-width:3;opacity:.45;} .gv-node:hover .gv-halo{stroke-width:3;opacity:.18;}",
    ".gv-sel .gv-halo{stroke-width:6;opacity:.28;} .gv-sel .gv-card{stroke:var(--accent);stroke-width:2.2;} .gv-nb .gv-card{stroke:color-mix(in srgb, var(--accent) 55%, var(--card-line));}",
    ".gv-dim{opacity:.36;}",
    ".gv-edge-line{stroke:var(--edge);transition:stroke .15s;} .gv-edge-halo{stroke:var(--edge);opacity:.38;} .gv-edge{transition:opacity .18s;}",
    ".gv-e-x .gv-edge-line{stroke:var(--accent);} .gv-e-event .gv-edge-line{stroke:#8a5fc9;} .gv-e-loop .gv-edge-line{stroke:var(--ink-dim);} .gv-e-cond .gv-edge-line{stroke:var(--warn);}",
    ".gv-hot .gv-edge-line{stroke:var(--accent);stroke-width:3;} .gv-hot .gv-edge-halo{stroke:var(--accent);}",
    ".gv-mk{fill:context-stroke;} .gv-mk-hollow{fill:var(--card);stroke:context-stroke;stroke-width:1.3;}",
    ".gv-elabel rect{fill:var(--card);stroke:var(--card-line);stroke-width:1;filter:drop-shadow(0 1px 2px rgba(10,20,30,.10));} .gv-elabel text{fill:var(--ink);font-size:11px;font-weight:650;pointer-events:none;}",
    ".gv-card-n{fill:var(--ink-dim);font-size:11px;font-weight:700;}",
    ".gv-edge.gv-dim{opacity:.16;} .gv-lbl-hidden{display:none;} .gv-edge.gv-hot .gv-lbl-hidden{display:inline;}",
    ".gv-active .gv-card{stroke:var(--accent);stroke-width:3.2;} .gv-active .gv-halo{stroke-width:8;opacity:.32;animation:gv-pulse 1.4s ease-in-out infinite;} .gv-done .gv-card{stroke:color-mix(in srgb, var(--accent) 60%, var(--card-line));}",
    ".gv-visited .gv-edge-line{stroke:var(--accent);} .gv-playing .gv-edge-line{stroke:var(--accent);stroke-width:3.4;stroke-dasharray:10 6;animation:gv-flow .7s linear infinite;} .gv-dot{fill:var(--accent);stroke:var(--card);stroke-width:2.5;filter:drop-shadow(0 0 6px var(--accent));pointer-events:none;}",
    "@keyframes gv-pulse{0%,100%{opacity:.18}50%{opacity:.42}} @keyframes gv-flow{to{stroke-dashoffset:-32}}",
    "@media (prefers-reduced-motion: reduce){.gv-node,.gv-edge,.gv-port{transition:none;} .gv-active .gv-halo,.gv-playing .gv-edge-line{animation:none;}}"
  ].join("\n");

  GraphView.EDGE_STYLES = EDGE; GraphView.ICONS = ICONS;
  window.HydraGraph = { GraphView: GraphView, measure: measure, wrap: wrap, wrapLines: wrapLines, SVGNS: SVGNS };
})();
