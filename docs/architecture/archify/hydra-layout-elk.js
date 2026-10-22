/* HYDRA architecture portal — graph layout through the Eclipse Layout Kernel (ELK, elkjs 0.12.0, vendor/elk.bundled.js).
   Pipeline: graph semantics -> ELK layered (cycle breaking, layering, node ordering with LAYER_SWEEP crossing minimisation + two-sided greedy
   switch, coordinate assignment, port assignment, ORTHOGONAL edge routing, edge-label placement) -> geometry (node boxes, bend points, label boxes)
   -> quality metrics -> bounded deterministic choice among a few ELK configurations.
   Pure functions, no DOM: usable from the browser (window.HydraLayout) and from node (module.exports) for the regression checker.
   The renderer (hydra-portal-graph.js) only draws what this module returns. */
(function(root, factory){
  var api = factory();
  if (typeof module === "object" && module.exports) module.exports = api; else root.HydraLayout = api;
})(typeof self !== "undefined" ? self : this, function(){
  "use strict";

  // ------------------------------------------------------------------ ELK option sets
  var BASE = {
    "elk.algorithm": "layered",
    "elk.edgeRouting": "ORTHOGONAL",
    "elk.layered.crossingMinimization.strategy": "LAYER_SWEEP",
    "elk.layered.crossingMinimization.greedySwitch.type": "TWO_SIDED",
    "elk.layered.crossingMinimization.greedySwitchHierarchical.type": "TWO_SIDED",
    "elk.layered.thoroughness": "30",
    "elk.layered.layering.strategy": "NETWORK_SIMPLEX",
    "elk.layered.cycleBreaking.strategy": "GREEDY",
    "elk.layered.considerModelOrder.strategy": "NONE",
    "elk.layered.nodePlacement.strategy": "BRANDES_KOEPF",
    "elk.layered.nodePlacement.bk.fixedAlignment": "BALANCED",
    "elk.layered.nodePlacement.favorStraightEdges": "true",
    "elk.layered.edgeRouting.selfLoopDistribution": "EQUALLY",
    "elk.layered.unnecessaryBendpoints": "false",
    "elk.layered.mergeEdges": "false",
    "elk.layered.feedbackEdges": "true",
    "elk.separateConnectedComponents": "true",
    "elk.portAlignment.default": "DISTRIBUTED",
    "elk.edgeLabels.inline": "true",
    "elk.edgeLabels.placement": "CENTER",
    "elk.json.edgeCoords": "ROOT",
    "elk.json.shapeCoords": "ROOT",
    "elk.padding": "[top=36,left=36,bottom=36,right=36]",
    "elk.randomSeed": "1"
  };
  // spacing: deliberately generous; readability before compactness
  var SPACING = {
    "elk.spacing.nodeNode": "46",
    "elk.layered.spacing.nodeNodeBetweenLayers": "96",
    "elk.spacing.edgeNode": "30",
    "elk.layered.spacing.edgeNodeBetweenLayers": "30",
    "elk.spacing.edgeEdge": "16",
    "elk.layered.spacing.edgeEdgeBetweenLayers": "16",
    "elk.spacing.edgeLabel": "8",
    "elk.spacing.portPort": "12",
    "elk.spacing.componentComponent": "60"
  };
  function opts(){ var o = {}; for (var i = 0; i < arguments.length; i++) { var s = arguments[i]; for (var k in s) o[k] = s[k]; } return o; }

  // bounded candidate set per graph family (the best by quality score wins; ties keep the earlier candidate -> deterministic)
  var FAMILY = {
    process: [
      { id: "lr-tight", direction: "RIGHT", o: { "elk.layered.spacing.nodeNodeBetweenLayers": "60", "elk.spacing.nodeNode": "30" } },
      { id: "lr-dfs", direction: "RIGHT", o: { "elk.layered.cycleBreaking.strategy": "DEPTH_FIRST", "elk.layered.spacing.nodeNodeBetweenLayers": "60", "elk.spacing.nodeNode": "30" } },
      { id: "lr-minwidth", direction: "RIGHT", o: { "elk.layered.layering.strategy": "MIN_WIDTH", "elk.layered.spacing.nodeNodeBetweenLayers": "60", "elk.spacing.nodeNode": "30" } },
      { id: "lr-longest", direction: "RIGHT", o: { "elk.layered.layering.strategy": "LONGEST_PATH", "elk.layered.spacing.nodeNodeBetweenLayers": "60", "elk.spacing.nodeNode": "30" } },
      { id: "lr-wrap-multi", direction: "RIGHT", o: { "elk.layered.wrapping.strategy": "MULTI_EDGE", "elk.aspectRatio": "1.3", "elk.layered.wrapping.multiEdge.improveCuts": "true", "elk.layered.wrapping.multiEdge.improveWrappedEdges": "true" } }
    ],
    map: [
      { id: "lr-sweep", direction: "RIGHT", o: {} },
      { id: "td-sweep", direction: "DOWN", o: {} },
      { id: "lr-ns", direction: "RIGHT", o: { "elk.layered.nodePlacement.strategy": "NETWORK_SIMPLEX" } }
    ],
    overview: [
      { id: "lr-sweep", direction: "RIGHT", o: {} },
      { id: "td-sweep", direction: "DOWN", o: {} },
      { id: "lr-median", direction: "RIGHT", o: { "elk.layered.crossingMinimization.strategy": "MEDIAN_LAYER_SWEEP" } }
    ]
  };

  // ------------------------------------------------------------------ ELK graph construction
  var PORT_MIN = 22; // vertical room per port on a side
  function buildGraph(nodes, edges, cand, family){
    var dir = cand.direction, outSide = dir === "RIGHT" ? "EAST" : "SOUTH", inSide = dir === "RIGHT" ? "WEST" : "NORTH";
    var byId = {}; nodes.forEach(function(n){ byId[n.id] = n; });
    var outCount = {}, inCount = {};
    edges.forEach(function(e){ outCount[e.a] = (outCount[e.a] || 0) + 1; inCount[e.b] = (inCount[e.b] || 0) + 1; });
    var children = nodes.map(function(n){
      var dec = n.shape === "decision";
      // enlarge the attachment side of busy nodes so that every edge gets its own port
      var need = Math.max(dec ? 0 : outCount[n.id] || 0, dec ? 0 : inCount[n.id] || 0);
      var w = n.w, h = n.h;
      if (need > 3) { if (dir === "RIGHT") h = Math.max(h, (need + 1) * PORT_MIN); else w = Math.max(w, (need + 1) * PORT_MIN); }
      n._lw = w; n._lh = h;
      return { id: n.id, width: w, height: h, ports: [], layoutOptions: { "elk.portConstraints": dec ? "FIXED_POS" : (cand.free ? "FREE" : "FIXED_SIDE") } };
    });
    var cById = {}; children.forEach(function(c){ cById[c.id] = c; });
    // decision nodes: every edge attaches at a distinct diamond vertex when possible (ins prefer the flow-facing vertex, outs fan over the other three)
    var inOrder, outOrder;
    if (dir === "RIGHT") {
      inOrder = [["WEST", 0, .5], ["NORTH", .5, 0], ["SOUTH", .5, 1], ["WEST", .25, .25], ["WEST", .25, .75]];
      outOrder = [["EAST", 1, .5], ["SOUTH", .5, 1], ["NORTH", .5, 0], ["EAST", .75, .25], ["EAST", .75, .75]];
    } else {
      inOrder = [["NORTH", .5, 0], ["WEST", 0, .5], ["EAST", 1, .5], ["NORTH", .25, .25], ["NORTH", .75, .25]];
      outOrder = [["SOUTH", .5, 1], ["EAST", 1, .5], ["WEST", 0, .5], ["SOUTH", .25, .75], ["SOUTH", .75, .75]];
    }
    var used = {};
    function vertex(n, c, kind){
      var order = kind === "in" ? inOrder : outOrder, u = used[n.id] = used[n.id] || {}, k = 0;
      function key(q){ return q[0] + q[1] + "," + q[2]; }
      while (k < order.length - 1 && u[key(order[k])]) k++; // a vertex already taken by an edge of the other direction is skipped
      u[key(order[k])] = 1;
      var o = order[k], id = n.id + "~" + kind + k;
      if (!c.ports.some(function(p){ return p.id === id; })) c.ports.push({ id: id, x: o[1] * c.width, y: o[2] * c.height, width: 0, height: 0, layoutOptions: { "elk.port.side": o[0] } });
      return id;
    }    var elkEdges = edges.map(function(e, i){
      var a = byId[e.a], b = byId[e.b], ca = cById[e.a], cb = cById[e.b], sp, tp;
      if (a.shape === "decision") sp = vertex(a, ca, "out");
      else { sp = e.a + "~o" + i; ca.ports.push({ id: sp, width: 0, height: 0, layoutOptions: cand.free ? {} : { "elk.port.side": outSide } }); }
      if (b.shape === "decision") tp = vertex(b, cb, "in");
      else { tp = e.b + "~i" + i; cb.ports.push({ id: tp, width: 0, height: 0, layoutOptions: cand.free ? {} : { "elk.port.side": inSide } }); }
      var ed = { id: "e" + i, sources: [sp], targets: [tp] };
      if (e._lab) ed.labels = [{ id: "l" + i, text: e._lab.text, width: e._lab.w, height: e._lab.h, layoutOptions: { "elk.edgeLabels.placement": "CENTER" } }];
      return ed;
    });
    return { id: "root", layoutOptions: opts(BASE, SPACING, family === "overview" ? { "elk.spacing.nodeNode": "70", "elk.layered.spacing.nodeNodeBetweenLayers": "130" } : {}, cand.o, { "elk.direction": dir }), children: children, edges: elkEdges };
  }

  // ------------------------------------------------------------------ read ELK geometry
  function readLayout(res, nodes, edges){
    var pos = {}; (res.children || []).forEach(function(c){ pos[c.id] = c; });
    nodes.forEach(function(n){ var c = pos[n.id]; n.x = c.x; n.y = c.y; n.w = c.width; n.h = c.height; });
    var routes = [];
    (res.edges || []).forEach(function(re, i){
      var e = edges[i], sec = (re.sections || [])[0], pts = [];
      if (sec) { pts.push({ x: sec.startPoint.x, y: sec.startPoint.y }); (sec.bendPoints || []).forEach(function(b){ pts.push({ x: b.x, y: b.y }); }); pts.push({ x: sec.endPoint.x, y: sec.endPoint.y }); }
      else { var a = pos[e.a], b = pos[e.b]; pts = [{ x: a.x + a.width, y: a.y + a.height / 2 }, { x: b.x, y: b.y + b.height / 2 }]; }
      var lab = re.labels && re.labels[0] ? { x: re.labels[0].x, y: re.labels[0].y, w: re.labels[0].width, h: re.labels[0].height, text: e._lab.text } : null;
      routes.push({ e: e, pts: clean(pts), poly: true, back: !!e._back, label: lab });
    });
    var maxX = 0, maxY = 0;
    nodes.forEach(function(n){ maxX = Math.max(maxX, n.x + n.w); maxY = Math.max(maxY, n.y + n.h); });
    routes.forEach(function(r){ r.pts.forEach(function(p){ maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); }); if (r.label) { maxX = Math.max(maxX, r.label.x + r.label.w); maxY = Math.max(maxY, r.label.y + r.label.h); } });
    return { nodes: nodes, routes: routes, width: Math.max(res.width || 0, maxX + 36), height: Math.max(res.height || 0, maxY + 36) };
  }
  function clean(pts){
    var q = [pts[0]];
    for (var i = 1; i < pts.length; i++) { var l = q[q.length - 1]; if (Math.abs(l.x - pts[i].x) > 0.01 || Math.abs(l.y - pts[i].y) > 0.01) q.push(pts[i]); }
    var o = [q[0]];
    for (var j = 1; j < q.length - 1; j++) { var a = o[o.length - 1], b = q[j], c = q[j + 1]; if ((Math.abs(a.x - b.x) < 0.01 && Math.abs(b.x - c.x) < 0.01) || (Math.abs(a.y - b.y) < 0.01 && Math.abs(b.y - c.y) < 0.01)) continue; o.push(b); }
    if (q.length > 1) o.push(q[q.length - 1]);
    return o;
  }

  // ------------------------------------------------------------------ quality metrics
  function segHit(a, b, c, d){ // proper intersection of two axis-aligned (or general) segments, endpoints excluded
    var o = function(p, q, r){ return (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x); };
    var d1 = o(a, b, c), d2 = o(a, b, d), d3 = o(c, d, a), d4 = o(c, d, b);
    return d1 * d2 < -1e-6 && d3 * d4 < -1e-6;
  }
  function segRect(a, b, r){ // does the segment pass through the (open) rectangle?
    var x0 = Math.min(a.x, b.x), x1 = Math.max(a.x, b.x), y0 = Math.min(a.y, b.y), y1 = Math.max(a.y, b.y);
    if (x1 <= r.x || x0 >= r.x + r.w || y1 <= r.y || y0 >= r.y + r.h) return false;
    if (Math.abs(a.x - b.x) < 0.01 || Math.abs(a.y - b.y) < 0.01) return true; // axis-aligned and overlapping the box interior
    var c = [{ x: r.x, y: r.y }, { x: r.x + r.w, y: r.y }, { x: r.x + r.w, y: r.y + r.h }, { x: r.x, y: r.y + r.h }];
    for (var i = 0; i < 4; i++) if (segHit(a, b, c[i], c[(i + 1) % 4])) return true;
    return (a.x > r.x && a.x < r.x + r.w && a.y > r.y && a.y < r.y + r.h);
  }
  function shrink(n, d){ var f = n.shape === "decision" ? 0.5 : 1; return { x: n.x + d, y: n.y + d, w: Math.max(1, n.w - 2 * d), h: Math.max(1, n.h - 2 * d) }; }
  function rectOverlap(a, b){ return a.x < b.x + b.w - 0.5 && b.x < a.x + a.w - 0.5 && a.y < b.y + b.h - 0.5 && b.y < a.y + a.h - 0.5; }
  function diamondHit(a, b, n){ // edge segment inside the diamond polygon of a decision node (more accurate than its bbox)
    var cx = n.x + n.w / 2, cy = n.y + n.h / 2, hw = n.w / 2 - 3, hh = n.h / 2 - 3;
    var P = [{ x: cx, y: cy - hh }, { x: cx + hw, y: cy }, { x: cx, y: cy + hh }, { x: cx - hw, y: cy }];
    for (var i = 0; i < 4; i++) if (segHit(a, b, P[i], P[(i + 1) % 4])) return true;
    var inside = function(p){ return Math.abs(p.x - cx) / hw + Math.abs(p.y - cy) / hh < 0.98; };
    return inside(a) && inside(b);
  }
  function metrics(L){
    var R = L.routes, N = L.nodes, m = { EDGE_EDGE_CROSSINGS: 0, EDGE_NODE_INTERSECTIONS: 0, EDGE_LABEL_INTERSECTIONS: 0, NODE_NODE_OVERLAPS: 0, LABEL_LABEL_OVERLAPS: 0, LABEL_NODE_OVERLAPS: 0, TOTAL_BENDS: 0, MAX_BENDS_PER_EDGE: 0, TOTAL_EDGE_LENGTH: 0, LONG_BACK_EDGE_COUNT: 0, LONG_EDGE_COUNT: 0, AMBIGUOUS_COLLINEAR_OVERLAPS: 0 };
    var i, j, a, b, s, t;
    for (i = 0; i < N.length; i++) for (j = i + 1; j < N.length; j++) if (rectOverlap(N[i], N[j])) m.NODE_NODE_OVERLAPS++;
    R.forEach(function(r){
      var p = r.pts, bends = Math.max(0, p.length - 2), len = 0;
      m.TOTAL_BENDS += bends; m.MAX_BENDS_PER_EDGE = Math.max(m.MAX_BENDS_PER_EDGE, bends);
      for (s = 0; s < p.length - 1; s++) len += Math.abs(p[s + 1].x - p[s].x) + Math.abs(p[s + 1].y - p[s].y);
      r.len = len; m.TOTAL_EDGE_LENGTH += len;
      if (len > Math.max(900, L.width * 0.55)) { if (r.back) m.LONG_BACK_EDGE_COUNT++; else m.LONG_EDGE_COUNT++; }
      var hit = false;
      N.forEach(function(n){
        var rc = shrink(n, 3);
        for (s = 0; s < p.length - 1; s++) { if (n.shape === "decision" ? diamondHit(p[s], p[s + 1], n) : segRect(p[s], p[s + 1], rc)) { hit = true; } }
      });
      if (hit) m.EDGE_NODE_INTERSECTIONS++;
      if (r.label) N.forEach(function(n){ if (rectOverlap({ x: r.label.x, y: r.label.y, w: r.label.w, h: r.label.h }, n)) m.LABEL_NODE_OVERLAPS++; });
    });
    for (i = 0; i < R.length; i++) for (j = i + 1; j < R.length; j++) {
      var A = R[i].pts, B = R[j].pts, cr = 0, ov = 0;
      for (s = 0; s < A.length - 1; s++) for (t = 0; t < B.length - 1; t++) {
        a = A[s]; b = A[s + 1]; var c = B[t], d = B[t + 1];
        if (segHit(a, b, c, d)) cr++;
        else { // collinear overlap of different edges longer than a port stub
          if (Math.abs(a.x - b.x) < 0.01 && Math.abs(c.x - d.x) < 0.01 && Math.abs(a.x - c.x) < 1.5) { var o = Math.min(Math.max(a.y, b.y), Math.max(c.y, d.y)) - Math.max(Math.min(a.y, b.y), Math.min(c.y, d.y)); if (o > 6) ov++; }
          else if (Math.abs(a.y - b.y) < 0.01 && Math.abs(c.y - d.y) < 0.01 && Math.abs(a.y - c.y) < 1.5) { var o2 = Math.min(Math.max(a.x, b.x), Math.max(c.x, d.x)) - Math.max(Math.min(a.x, b.x), Math.min(c.x, d.x)); if (o2 > 6) ov++; }
        }
      }
      m.EDGE_EDGE_CROSSINGS += cr; m.AMBIGUOUS_COLLINEAR_OVERLAPS += ov;
    }
    var labs = R.filter(function(r){ return r.label; });
    for (i = 0; i < labs.length; i++) {
      var lr = labs[i].label, lrect = { x: lr.x, y: lr.y, w: lr.w, h: lr.h };
      for (j = i + 1; j < labs.length; j++) if (rectOverlap(lrect, { x: labs[j].label.x, y: labs[j].label.y, w: labs[j].label.w, h: labs[j].label.h })) m.LABEL_LABEL_OVERLAPS++;
      R.forEach(function(r2){ if (r2 === labs[i]) return; for (var q = 0; q < r2.pts.length - 1; q++) if (segRect(r2.pts[q], r2.pts[q + 1], { x: lrect.x + 1, y: lrect.y + 1, w: lrect.w - 2, h: lrect.h - 2 })) { m.EDGE_LABEL_INTERSECTIONS++; break; } });
    }
    m.AREA = Math.round(L.width * L.height);
    m.SCORE = score(m);
    return m;
  }
  // internal quality score: node/label collisions are catastrophic, crossings and ambiguous merges very expensive, bends/length modest, area almost free
  function score(m){
    return 1e6 * (m.EDGE_NODE_INTERSECTIONS + m.LABEL_NODE_OVERLAPS + m.NODE_NODE_OVERLAPS) + 2e5 * m.LABEL_LABEL_OVERLAPS + 1e5 * m.EDGE_LABEL_INTERSECTIONS +
      1e3 * m.EDGE_EDGE_CROSSINGS + 5e2 * m.AMBIGUOUS_COLLINEAR_OVERLAPS + 3 * m.TOTAL_BENDS + 1200 * m.LONG_BACK_EDGE_COUNT + 300 * (m.LONG_EDGE_COUNT || 0) + 0.02 * m.TOTAL_EDGE_LENGTH + 1e-4 * m.AREA;
  }
  // coordinates of every edge/edge crossing (debug overlay)
  function crossingPoints(L){
    var out = [], R = L.routes, i, j, s, t;
    for (i = 0; i < R.length; i++) for (j = i + 1; j < R.length; j++) {
      var A = R[i].pts, B = R[j].pts;
      for (s = 0; s < A.length - 1; s++) for (t = 0; t < B.length - 1; t++) if (segHit(A[s], A[s + 1], B[t], B[t + 1])) {
        var a = A[s], b = A[s + 1], c = B[t], d = B[t + 1];
        out.push(Math.abs(a.x - b.x) < 0.01 ? { x: a.x, y: c.y } : { x: c.x, y: a.y });
      }
    }
    return out;
  }  // scale at which the whole diagram fits a viewport; used to reject layouts that are legible only when tiny
  function fitScale(L, vw, vh){ return Math.min((vw - 40) / L.width, (vh - 40) / L.height, 1.25); }

  // ------------------------------------------------------------------ public entry
  var cache = {};
  function signature(nodes, edges, family){
    return family + "|" + nodes.map(function(n){ return n.id + ":" + n.w + "x" + n.h + ":" + (n.shape || ""); }).join(",") + "|" + edges.map(function(e){ return e.a + ">" + e.b + ":" + (e.kind || "") + ":" + (e.label || ""); }).join(",");
  }
  function clone(L){ return JSON.parse(JSON.stringify({ nodes: L.nodes.map(function(n){ return { id: n.id, x: n.x, y: n.y, w: n.w, h: n.h }; }), routes: L.routes.map(function(r){ return { i: r.i, pts: r.pts, back: r.back, label: r.label }; }), width: L.width, height: L.height, cand: L.cand, metrics: L.metrics, candidates: L.candidates })); }
  /* layout(elk, nodes, edges, {family, labelSize(text)->{w,h}, viewport:{w,h}, only: candidateId}) -> Promise<{nodes, routes, width, height, cand, metrics, candidates}>
     nodes: [{id,w,h,shape}]; edges: [{a,b,kind,label}]. Nodes are mutated (x,y,w,h); routes[i].e is the i-th input edge. */
  function layout(elk, nodes, edges, o){
    o = o || {};
    var family = o.family || "process", cands = FAMILY[family] || FAMILY.process;
    if (o.only) cands = cands.filter(function(c){ return c.id === o.only; });
    var nodeIds = {}; nodes.forEach(function(n){ nodeIds[n.id] = 1; });
    var es = edges.filter(function(e){ return nodeIds[e.a] && nodeIds[e.b] && e.a !== e.b; });
    es.forEach(function(e){
      var t = o.labelText ? o.labelText(e) : (e.label ? String(e.label) : ""); t = t ? String(t) : ""; var full = t; if (t.length > 30) t = t.slice(0, 29) + "…";
      var sz = t && o.labelSize ? o.labelSize(t) : { w: t.length * 6.6 + 16, h: 20 };
      e._lab = t ? { text: t, full: full, w: Math.ceil(sz.w), h: sz.h } : null;
    });
    var sig = signature(nodes, es, family + (o.only || ""));
    var key = sig; if (cache[key]) { var hit = clone(cache[key]); return Promise.resolve(restore(hit, nodes, es)); }
    var results = [], p = Promise.resolve();
    cands.forEach(function(cand){
      p = p.then(function(){
        var ns = nodes.map(function(n){ return { id: n.id, w: n.w, h: n.h, shape: n.shape }; });
        var g = buildGraph(ns.map(function(n, i){ n.src = nodes[i]; return n; }), es, cand, family);
        return elk.layout(g).then(function(res){
          var Ls = readLayout(res, ns, es); Ls.cand = cand.id; Ls.metrics = metrics(Ls); Ls.metrics.FIT = +fitScale(Ls, (o.viewport || { w: 1260, h: 760 }).w, (o.viewport || { w: 1260, h: 760 }).h).toFixed(3);
          Ls.metrics.SCORE_FIT = Ls.metrics.SCORE + (Ls.metrics.FIT < 0.6 ? 6e3 * (0.6 - Ls.metrics.FIT) / 0.6 : 0);
          results.push(Ls);
        }, function(){ /* a candidate may fail on exotic input: ignore it, the others decide */ });
      });
    });
    return p.then(function(){
      if (!results.length) return Promise.reject(new Error("ELK layout failed for every candidate"));
      var best = results[0]; results.forEach(function(r){ if (r.metrics.SCORE_FIT < best.metrics.SCORE_FIT - 1e-9) best = r; });
      best.candidates = results.map(function(r){ return { id: r.cand, score: Math.round(r.metrics.SCORE_FIT), crossings: r.metrics.EDGE_EDGE_CROSSINGS, edgeNode: r.metrics.EDGE_NODE_INTERSECTIONS, fit: r.metrics.FIT, bends: r.metrics.TOTAL_BENDS }; });
      best.routes.forEach(function(r, i){ r.i = i; });
      cache[key] = clone(best);
      return restore(best, nodes, es);
    });
  }
  // copy the chosen geometry onto the caller's node objects / edges
  function restore(L, nodes, es){
    var pos = {}; L.nodes.forEach(function(n){ pos[n.id] = n; });
    nodes.forEach(function(n){ var q = pos[n.id]; n.x = q.x; n.y = q.y; n.w = q.w; n.h = q.h; });
    L.routes.forEach(function(r, i){ r.e = es[i]; r.poly = true; });
    L.nodes = nodes; return L;
  }

  return { layout: layout, metrics: metrics, crossingPoints: crossingPoints, score: score, fitScale: fitScale, buildGraph: buildGraph, readLayout: readLayout, FAMILY: FAMILY, BASE: BASE, SPACING: SPACING, clearCache: function(){ cache = {}; } };
});
