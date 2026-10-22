/* HYDRA architecture portal — content registry + schema (presentation-agnostic data).

   Content files hydra-model-*.js register into window.HM. Rendering lives in hydra-portal-app.js.
   Rules that the content MUST follow (checked by scripts/… no: by docs/architecture/archify/knowledge/portal-check.js):
     - T(es, en) is a bilingual text. Every user-visible string is a T or an array of T.
     - USER fields (cap.user, element.mean/purpose/when/variants/props, mechanism.does, node.u/need/out, edge labels)
       must NOT contain Java/TS file names, class names, DTOs, packages, API paths or line numbers.
     - FUNCTIONAL fields add semantics (cardinality, lifecycle, conditions, states, relations).
     - TECHNICAL fields (cap.tech, element.tech, node.x, mechanism.tech) hold class names, services, APIs, tests, evidence.
     - Requiredness values: MANDATORY | OPTIONAL | CONDITIONAL | ALTERNATIVE | REPEATABLE | DERIVED.

   HM.cap(id, {
     name: T, group: 'definition'|'behavior'|'delivery'|'platform'|'external',
     user: { tagline:T, what:T, why:T, when:T, canDefine:[T], needs:[T], produces:[T], mandatory:[T], optional:[T],
             conditional:[T], next:[T] },
     func: { lifecycle:[T], semantics:[T], states:[{n:T,d:T}], cross:[T] },
     tech: { runtime:T, services:[str], apis:[str], schemas:[str], tests:[str], evidence:[{path,line,end_line,label}], notes:[T] },
     elements: [ { id, n:T, kind:'concept'|'variant-set'|'derived', req, mean:T, purpose:T, when:T, card:str,
                   variants:[{n:T, d:T, req}], props:[{n:T, req, d:T}], io:{i:T,o:T}, effect:T,
                   func:{ rel:[T], lifecycle:T, cond:T }, tech:{ type:str, schema:str, evidence:[...] } } ],
     map: { nodes:[{id, elem?, n:T, k:'concept'|'variant'|'derived'|'external', req, d:T}],
            edges:[{a, b, rel:'contains'|'ref'|'isa'|'gen', card1, card2, req, l:T}] },
     define: [ { id, n:T, kind:'wizard'|'form'|'canvas'|'flow'|'text'|'import'|'property'|'generated'|'direct'|'api',
                 req, elems:[elemId], does:T, func:T, tech:T, proc:'processId' } ],
     rel: [ { to:capId, type:'required'|'optional'|'conditional'|'alternative'|'event'|'producer'|'external', dir:'out'|'in',
              u:T, f:T, t:T } ]
   })
   HM.proc({ id, scope:'cap'|'cross'|'authoring', cap, caps:[...], elems:[elemId], n:T, d:T,
     nodes:[{ id, t:'start'|'end'|'step'|'decision'|'artifact', a:'user'|'auto'|'ext', r:requiredness, cap, l:T, e:elemId,
              u:T(what+why), need:T, out:T, f:T(conditions/state), s:T(state transition), x:{rt:str, test:[str], ev:[{path,line,label}]} }],
     edges:[ [from,to,kind?,label?] ]   // kind: next|opt|cond|alt|loop|event ; cross-capability is inferred from node caps
   }) */
(function(){
  "use strict";
  var HM = window.HM = { caps: {}, procs: [], overview: null, groups: null, T: function(es, en){ return { es: es, en: en }; } };

  HM.cap = function(id, def){ def.id = id; HM.caps[id] = def; return def; };
  HM.proc = function(def){
    def.edges = (def.edges || []).map(function(e){
      return Array.isArray(e) ? { a: e[0], b: e[1], kind: e[2] || "next", l: e[3] || null } : e;
    });
    HM.procs.push(def); return def;
  };

  // ------------------------------------------------------------------------------------------------
  // AUDIENCE LENSES. The content files hold the canonical semantic model plus the USER projection.
  // hydra-lens-*.js add the FUNCTIONAL and TECHNICAL projections (own titles, summaries, semantics) on top of it:
  //   HM.lens({
  //     caps:  { <capId>: { func:{title,summary,inputs:[T],outputs:[T],conditions:[T]}, tech:{title,summary},
  //                         elements:{ <elemId>:{ nf:T, fm:T, tm:T, variants:[{k:'REAL_CONSTANT', fd:T, td:T}] /* same order as element.variants */ } } } },
  //     procs: { <procId>: { nf:T, df:T, nt:T, dt:T,
  //                          nodes:{ <nodeId>:{ lf:T, sf:T, fio:T, lt:T|str, st:T, tx:T } } } }
  //   })
  //   lf/sf = FUNCTIONAL title / subtitle, fio = functional inputs → outputs, lt/st/tx = TECHNICAL title / subtitle / implementation text.
  // ------------------------------------------------------------------------------------------------
  HM.lens = function(p){
    Object.keys(p.caps || {}).forEach(function(id){
      var c = HM.caps[id], l = p.caps[id]; if (!c) { (HM.lensErrors = HM.lensErrors || []).push("lens: unknown cap " + id); return; }
      if (l.func) c.funcL = l.func; if (l.tech) c.techL = l.tech;
      Object.keys(l.elements || {}).forEach(function(eid){
        var e = (c.elements || []).filter(function(x){ return x.id === eid; })[0];
        if (!e) { (HM.lensErrors = HM.lensErrors || []).push("lens: unknown element " + id + "." + eid); return; }
        var le = l.elements[eid]; e.nf = le.nf; e.fm = le.fm; e.tm = le.tm;
        (le.variants || []).forEach(function(v, i){ var tv = (e.variants || [])[i]; if (tv) { tv.k = v.k; tv.fd = v.fd; tv.td = v.td; } });
      });
    });
    Object.keys(p.procs || {}).forEach(function(pid){
      var pr = HM.procs.filter(function(x){ return x.id === pid; })[0], l = p.procs[pid];
      if (!pr) { (HM.lensErrors = HM.lensErrors || []).push("lens: unknown process " + pid); return; }
      pr.nf = l.nf; pr.df = l.df; pr.nt = l.nt; pr.dt = l.dt;
      Object.keys(l.nodes || {}).forEach(function(nid){
        var n = pr.nodes.filter(function(x){ return x.id === nid; })[0];
        if (!n) { (HM.lensErrors = HM.lensErrors || []).push("lens: unknown node " + pid + "." + nid); return; }
        var ln = l.nodes[nid]; n.lf = ln.lf; n.sf = ln.sf; n.fio = ln.fio; n.lt = ln.lt; n.st = ln.st; n.tx = ln.tx;
      });
    });
  };

  // ------------------------------------------------------------------------------------------------
  // TECHNICAL DOCUMENTATION (hydra-tech-*.js). Explains what each implementation component IS, DOES, WHY it exists, its responsibility,
  // where it participates, what it receives/produces, who calls it and what it calls, side effects, relevance, evidence, important methods,
  // explained code excerpts and explained tests. Shown only in the TECHNICAL audience.
  //   HM.techdoc({
  //     caps: { <capId>: {
  //       groups: [ { id, n:T, d:T } ],                           // responsibility groups (DEFINITION, APPLICATION, RUNTIME, ADAPTER, PERSISTENCE, API, FRONTEND ...)
  //       comps:  [ { id, n:'ClassName', kind, g, what:T, does:T, why:T, resp:T, where:T, recv:T, prod:T,
  //                   calls:[compId | {n,d:T}], usedBy:[compId | {n,d:T}], fx:T, rel:T, rat:{k:'proven'|'observable', t:T, src:[..]},
  //                   src:[{path,line,end_line,label}], methods:[{n,purpose:T,when:T,in:T,out:T,decisions:T,fx:T,fail:T,next:T,src:[..]}],
  //                   excerpts:[{path,from,to,code:"exact lines",what:T,why:T,role:T,in:T,out:T,behavior:T,related:[compId]}],
  //                   tests:[testId], elems:[elemId] } ],
  //       tests:  [ { id, n, path, line, contract:T, why:T, protects:T } ],
  //       trace:  [ { user:T, func:T, comp:compId, method:"name", src:{path,line}, role:T } ] } },
  //     procs: { <procId>: { <nodeId>: { tc:compId, tm:"method", tw:T, ti:T, to:T, te:T, tn:T, tf:T } } }   // stage → implementing component
  //   })
  //   HM.facets({ caps:{ <capId>:{ elements:{ <elemId>:{ variants:[ {i:<index>, facets:[{n:T,v:T}]} ] } } } } })  // extra named facets per variant
  //   HM.extend(capId, { elements:[..], mapNodes:[..], mapEdges:[..] })                                          // append content to a capability
  // ------------------------------------------------------------------------------------------------
  HM.TECH_KINDS = ["service", "runtime", "controller", "record", "interface", "registry", "adapter", "bridge", "schema", "config", "ui", "repository", "enum", "util", "engine", "facade"];
  HM.tech = { caps: {}, procs: {} };
  HM.techdoc = function(p){
    Object.keys(p.caps || {}).forEach(function(id){
      var s = p.caps[id], d = HM.tech.caps[id] = HM.tech.caps[id] || { groups: [], comps: [], tests: [], trace: [] };
      (s.groups || []).forEach(function(g){ if (!d.groups.some(function(x){ return x.id === g.id; })) d.groups.push(g); });
      d.comps = d.comps.concat(s.comps || []); d.tests = d.tests.concat(s.tests || []); d.trace = d.trace.concat(s.trace || []);
    });
    Object.keys(p.procs || {}).forEach(function(pid){ var t = HM.tech.procs[pid] = HM.tech.procs[pid] || {}; Object.keys(p.procs[pid]).forEach(function(nid){ t[nid] = p.procs[pid][nid]; }); });
  };
  HM.facets = function(p){
    Object.keys(p.caps || {}).forEach(function(id){
      var c = HM.caps[id]; if (!c) { (HM.lensErrors = HM.lensErrors || []).push("facets: unknown cap " + id); return; }
      Object.keys(p.caps[id].elements || {}).forEach(function(eid){
        var e = (c.elements || []).filter(function(x){ return x.id === eid; })[0];
        if (!e) { (HM.lensErrors = HM.lensErrors || []).push("facets: unknown element " + id + "." + eid); return; }
        (p.caps[id].elements[eid].variants || []).forEach(function(v){ var tv = (e.variants || [])[v.i]; if (tv) tv.facets = v.facets; else (HM.lensErrors = HM.lensErrors || []).push("facets: bad variant index " + eid + "[" + v.i + "]"); });
      });
    });
  };
  HM.extend = function(id, add){
    var c = HM.caps[id]; if (!c) { (HM.lensErrors = HM.lensErrors || []).push("extend: unknown cap " + id); return; }
    c.elements = (c.elements || []).concat(add.elements || []);
    c.map = c.map || { nodes: [], edges: [] };
    c.map.nodes = c.map.nodes.concat(add.mapNodes || []); c.map.edges = c.map.edges.concat(add.mapEdges || []);
    if (add.define) c.define = (c.define || []).concat(add.define);
  };

  HM.groups = {
    definition: { es: "Definición del lenguaje", en: "Language definition" },
    behavior:   { es: "Comportamiento y reglas", en: "Behaviour and rules" },
    delivery:   { es: "Entrega y generación", en: "Delivery and generation" },
    platform:   { es: "Plataforma y proyecto", en: "Platform and project" },
    external:   { es: "Sistemas externos", en: "External systems" }
  };

  // Decorative hue per capability group (never the only carrier of meaning).
  HM.groupColor = { definition: "#4a90c9", behavior: "#8a5fc9", delivery: "#1f8a6f", platform: "#7a8590", external: "#c9994a" };

  var T = HM.T;

  // ------------------------------------------------------------------------------------------------
  // Shared checks (used by the portal's completeness matrix and by knowledge/portal-check.js)
  // ------------------------------------------------------------------------------------------------
  var PUBLIC_VOCAB = ["EventFlow", "OAuth", "GitHub", "GitLab", "JavaScript", "TypeScript", "EMF", "Ecore", "GenModel", "PlantUML",
    "DrawIO", "OpenAPI", "QuickFix", "Quick Fix", "Quick Fixes", "MetaModel", "HYDRA", "Xtext", "Langium", "ANTLR", "Sirius", "Eclipse", "Blockly",
    "Monaco", "ReactFlow", "React Flow", "PKCE", "JSON", "JSONL", "GraphQL", "DSL", "MetaClass"];
  // Returns the offending fragment, or null. Implementation vocabulary that must not appear in USER text.
  HM.leak = function(s){
    if (!s) return null;
    var str = String(s);
    var m = str.match(/[\w./-]+\.(java|tsx?|jsx?|json|ya?ml|properties|py|ps1)\b/i); if (m) return m[0];
    m = str.match(/\bsrc\/[\w./-]+/); if (m) return m[0];
    m = str.match(/(^|[\s(])\/[a-z][\w-]*(\/[\w{}:-]+)+/); if (m) return m[0].trim();
    m = str.match(/\b(com|org)\.[a-z]+\.[a-z.]+/); if (m) return m[0];
    m = str.match(/\b[A-Za-z]+(Service|Controller|Controllers|Runtime|Engine|Registry|Descriptor|Facade|Repository|Handler|Executor|Probe|Projector|Planner|Packager|Codec|DTO|Dto|Impl|Store|Factory|Adapter|Mapper|Resolver|Observer|Publisher|Gate)\b/);
    if (m && PUBLIC_VOCAB.indexOf(m[0]) < 0) return m[0];
    m = str.match(/\b(Runtime|Controller|Impl|DTO|DTOs)\b/); if (m) return m[0];
    var camel = str.match(/\b[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]+)+\b/g) || [];
    for (var i = 0; i < camel.length; i++) { if (PUBLIC_VOCAB.indexOf(camel[i]) < 0) return camel[i]; }
    m = str.match(/\b[a-z]+[A-Z][A-Za-z0-9]+\(\)?/); if (m) return m[0];
    m = str.match(/\b[A-Z]{2,}(?:_[A-Z0-9]{2,})+\b/); if (m && PUBLIC_VOCAB.indexOf(m[0]) < 0) return m[0];
    return null;
  };

  // FUNCTIONAL text may name domain concepts and real option values but must not carry file paths, endpoints or source references.
  HM.leakFunc = function(s){
    if (!s) return null; var str = String(s), m;
    m = str.match(/[\w./-]+\.(java|tsx?|jsx?|json|ya?ml|properties|py|ps1)\b/i); if (m) return m[0];
    m = str.match(/\bsrc\/[\w./-]+/); if (m) return m[0];
    m = str.match(/(^|[\s(])\/[a-z][\w-]*(\/[\w{}:-]+)+/); if (m) return m[0].trim();
    m = str.match(/\b(com|org)\.[a-z]+\.[a-z.]+/); if (m) return m[0];
    return null;
  };
  function tl(x){ return x ? (x.es || "") + " " + (x.en || "") : ""; }
  HM.procsFor = function(capId){
    return HM.procs.filter(function(p){ return p.cap === capId || (p.caps || []).indexOf(capId) >= 0 || (p.nodes || []).some(function(n){ return n.cap === capId; }); });
  };
  // 18-dimension completeness matrix (strict definition of done)
  HM.completeness = function(capId){
    var c = HM.caps[capId]; var r = [];
    function add(k, ok){ r.push({ k: k, ok: !!ok }); }
    if (!c) return r;
    var u = c.user || {}, f = c.func || {}, t = c.tech || {}, els = c.elements || [], map = c.map || {}, def = c.define || [];
    var procs = HM.procsFor(capId), own = procs.filter(function(p){ return p.cap === capId && p.scope !== "authoring"; });
    var cross = procs.filter(function(p){ return p.scope === "cross"; });
    var micro = def.some(function(d){ return d.proc && HM.procs.some(function(p){ return p.id === d.proc; }); });
    add("user", u.what && u.why && (u.canDefine || []).length && !HM.leak(tl(u.what) + tl(u.why)));
    add("functional", (f.lifecycle || []).length && (f.semantics || []).length);
    add("technical", ((t.services || []).length || (t.evidence || []).length) && t.runtime);
    add("concepts", els.length >= 2);
    add("variants", els.some(function(e){ return (e.variants || []).length; }) || c.noVariants);
    add("purpose", els.length && els.every(function(e){ return e.purpose && e.mean; }));
    add("requiredness", els.length && els.every(function(e){ return e.req; }));
    add("authoring", def.length);
    add("microprocess", micro || c.noMicro);
    add("domainmap", (map.nodes || []).length >= 3);
    add("internal", own.length);
    add("graphical", procs.length && procs.every(function(p){ return (p.nodes || []).length >= 3; }));
    add("clickable", own.concat(cross).every(function(p){ return p.nodes.filter(function(n){ return n.u; }).length >= Math.min(3, p.nodes.length); }));
    add("relationships", (c.rel || []).length);
    add("inputs", (u.needs || []).length);
    add("outputs", (u.produces || []).length);
    add("crosscap", (c.rel || []).length && (cross.length || c.noCross));
    add("evidence", (t.evidence || []).length);
    return r;
  };

  // ------------------------------------------------------------------------------------------------
  // OVERVIEW — complete current capability inventory + typed relationships (fixed coordinates).
  // node.lv = levels where the node is drawn. node.nm / node.sb = name / subtitle per level.
  // edge.type: required | optional | conditional | alternative | event | producer | external
  // ------------------------------------------------------------------------------------------------
  var ALL = ["user", "functional", "technical"], FT = ["functional", "technical"], TECH = ["technical"];
  function N(id, group, x, y, lv, user, func, tech, subU, subF, subT){
    return { id: id, group: group, x: x, y: y, lv: lv,
      nm: { user: user, functional: func, technical: tech || func },
      sb: { user: subU || T("", ""), functional: subF || subU || T("", ""), technical: subT || subF || subU || T("", "") } };
  }
  HM.overview = {
    nodes: [
      N("ext_git", "external", 0, 0, ALL, T("Servicios Git y nube", "Git and cloud services"), T("Proveedores externos (Git, almacenamiento)", "External providers (Git, storage)"), T("Git / almacenamiento / OAuth (sistema externo)", "Git / storage / OAuth (external system)"),
        T("Fuera de HYDRA", "Outside HYDRA"), T("Límite del sistema", "System boundary")),
      N("external_repositories", "external", 0, 130, ALL, T("Repositorios remotos", "Remote repositories"), T("Proveedores remotos", "Remote providers"), T("Proveedores remotos (Git · storage · OAuth)", "Remote providers (Git · storage · OAuth)"),
        T("Git y nube", "Git and cloud"), T("Conexión y transferencia", "Connection and transfer")),
      N("recovery", "platform", 0, 260, ALL, T("Recuperar proyectos externos", "Recover external projects"), T("Recuperación externa", "External recovery"), T("Recuperación externa (descubrimiento + materialización)", "External recovery (discovery + materialization)"),
        T("Traer lo que ya existe fuera", "Bring in what exists elsewhere"), T("Inventario no destructivo", "Non-destructive inventory")),
      N("project", "platform", 0, 390, ALL, T("Proyecto", "Project"), T("Gestión de proyectos", "Project management"), T("Gestión de proyectos (árbol, catálogo, import/export)", "Project management (tree, catalog, import/export)"),
        T("El contenedor de todo", "The container for everything"), T("Árbol de archivos y capacidades activas", "File tree and active capabilities")),
      N("portability", "platform", 0, 520, ALL, T("Mover entre proyectos", "Move between projects"), T("Portabilidad", "Portability"), T("Portabilidad (paquetes de capa, fusión)", "Portability (layer packages, merge)"),
        T("Llevar capacidades a otro proyecto", "Carry capabilities to another project"), T("Empaquetar, fusionar, revertir", "Package, merge, revert")),

      N("grammar", "definition", 270, 0, ALL, T("Lenguajes textuales", "Textual languages"), T("Gramáticas", "Grammars"), T("Gramáticas (Xtext · Langium · ANTLR + servicio de lenguaje)", "Grammars (Xtext · Langium · ANTLR + language service)"),
        T("Escribir el lenguaje como texto", "Write the language as text"), T("Sintaxis, compilación, editor de texto", "Syntax, compilation, text editor")),
      N("metamodel", "definition", 270, 130, ALL, T("Metamodelos", "Metamodels"), T("Metamodelos", "Metamodels"), T("Metamodelos (canónico + EMF/Ecore)", "Metamodels (canonical + EMF/Ecore)"),
        T("Definir el lenguaje", "Define the language"), T("Tipos, relaciones, operaciones", "Types, relations, operations")),
      N("model_editor", "definition", 270, 260, ALL, T("Modelos y su editor", "Models and their editor"), T("Editor de modelos", "Model editor"), T("Editor de modelos (definición de editor + EMF dinámico)", "Model editor (editor definition + dynamic EMF)"),
        T("Crear y editar contenido", "Create and edit content"), T("Representaciones y sesiones", "Representations and sessions")),

      N("validation", "behavior", 540, 0, ALL, T("Validación y reparaciones", "Validation and fixes"), T("Validación y Quick Fixes", "Validation & Quick Fixes"), T("Validación y Quick Fixes (motor declarativo)", "Validation & Quick Fixes (declarative engine)"),
        T("Detectar y reparar errores", "Detect and repair errors"), T("Reglas, diagnósticos, reparaciones", "Rules, diagnostics, repairs")),
      N("transformation", "behavior", 540, 130, ALL, T("Transformaciones", "Transformations"), T("Transformaciones", "Transformations"), T("Transformaciones (M2M · M2T · T2M)", "Transformations (M2M · M2T · T2M)"),
        T("Convertir modelos y texto", "Convert models and text"), T("Reglas, ejecución, sincronización", "Rules, execution, synchronization")),
      N("events", "behavior", 540, 260, ALL, T("Eventos", "Events"), T("Eventos y flujos", "Events & flows"), T("Eventos + EventFlow", "Events + EventFlow"),
        T("Reaccionar a lo que ocurre", "React to what happens"), T("Tipos de evento y flujos", "Event types and flows")),
      N("gamification", "behavior", 540, 390, ALL, T("Gamificación", "Gamification"), T("Gamificación", "Gamification"), T("Gamificación (reglas, ledger, feedback)", "Gamification (rules, ledger, feedback)"),
        T("Puntos, logros y niveles", "Points, achievements and levels"), T("Progresión por eventos", "Event-driven progression")),

      N("integration", "delivery", 810, 0, ALL, T("Integración con tecnologías", "Technology integration"), T("Integración de tecnologías", "Technology integration"), T("Integración (proyección, roundtrip, paridad nativa)", "Integration (projection, roundtrip, native parity)"),
        T("Llevar el modelo a otras herramientas", "Take the model to other tools"), T("Perfiles, 9 etapas, roundtrip", "Profiles, 9 stages, roundtrip")),
      N("toolchains", "external", 810, 130, ALL, T("Herramientas instaladas", "Installed tools"), T("Toolchains", "Toolchains"), T("Toolchains (ATL · ETL · Acceleo · EGL · Xtend · npm/mvn)", "Toolchains (ATL · ETL · Acceleo · EGL · Xtend · npm/mvn)"),
        T("Motores del equipo", "Engines on the machine"), T("Sondeos reales de versión", "Real version probes")),
      N("ext_engines", "external", 1080, 130, ALL, T("Motores de terceros", "Third-party engines"), T("Motores y herramientas externas", "External engines and tools"), T("ATL · ETL · Acceleo · EGL · Xtend · npm · mvn (sistema externo)", "ATL · ETL · Acceleo · EGL · Xtend · npm · mvn (external system)"),
        T("Fuera de HYDRA", "Outside HYDRA"), T("Límite del sistema", "System boundary")),
      N("publication", "delivery", 810, 260, ALL, T("Publicación", "Publication"), T("Publicación y generación", "Publication & generation"), T("Publicación (generadores, release gates, distribución)", "Publication (generators, release gates, distribution)"),
        T("Generar y distribuir", "Generate and distribute"), T("Código, paquetes y puertas de calidad", "Code, packages and quality gates")),
      N("generated_projects", "delivery", 810, 390, ALL, T("Proyectos generados", "Generated projects"), T("Ciclo de vida de proyectos generados", "Generated-project lifecycle"), T("Generated projects (etapas GENERATE → LAUNCH)", "Generated projects (GENERATE → LAUNCH stages)"),
        T("Construir y ejecutar lo generado", "Build and run what was generated"), T("Etapas y comandos", "Stages and commands")),

      N("persistence", "platform", 270, 540, FT, T("Persistencia", "Persistence"), T("Persistencia canónica", "Canonical persistence"), T("Persistencia canónica (JSON + ETag)", "Canonical persistence (JSON + ETag)"),
        T("Guarda todo en local", "Saves everything locally"), T("Documentos JSON versionados", "Versioned JSON documents")),
      N("audit", "platform", 540, 540, FT, T("Auditoría", "Audit"), T("Auditoría", "Audit"), T("Auditoría (JSONL append-only)", "Audit (append-only JSONL)"),
        T("Registro de cambios", "Change log"), T("Efecto automático de guardar", "Automatic side effect of saving")),
      N("security", "platform", 810, 540, FT, T("Seguridad", "Security"), T("Seguridad", "Security"), T("Seguridad (hardening + sandbox de procesos)", "Security (hardening + process sandbox)"),
        T("Protege peticiones y procesos externos", "Protects requests and external processes"), T("Filtro de peticiones y aislamiento", "Request filter and isolation"))
    ],
    // [a, b, type, levels, user label, functional label, technical label, bend]
    edges: []
  };
  function E(a, b, type, lv, u, f, t, bend){ HM.overview.edges.push({ a: a, b: b, type: type, lv: lv, l: { user: u, functional: f, technical: t || f }, bend: bend || 0 }); }
  // required: the source cannot work without the target
  E("model_editor", "metamodel", "required", ALL, T("usa su lenguaje", "uses its language"), T("instancia el metamodelo", "instantiates the metamodel"), T("metamodelId (FK)", "metamodelId (FK)"));
  E("validation", "metamodel", "required", ALL, T("se escribe sobre sus tipos", "written over its types"), T("contexto = clase/atributo", "context = class/attribute"), T("ValidationRule.context", "ValidationRule.context"), -30);
  E("grammar", "metamodel", "alternative", ALL, T("otra forma de obtener el lenguaje: se deduce del texto", "another way to get the language: inferred from the text"), T("el metamodelo puede deducirse de la gramática (GRAMMAR_FIRST) o definirse aparte (METAMODEL_FIRST / LINKED)", "the metamodel can be inferred from the grammar (GRAMMAR_FIRST) or defined separately (METAMODEL_FIRST / LINKED)"), T("la compilación proyecta el diagrama de clases inferido", "compilation projects the inferred class diagram"));
  E("transformation", "metamodel", "required", ALL, T("entre dos lenguajes", "between two languages"), T("extremos origen/destino", "source/target endpoints"), T("Transformation endpoints", "Transformation endpoints"));
  E("transformation", "model_editor", "required", ALL, T("parte de modelos ya creados", "starts from existing models"), T("entrada: modelos persistidos", "input: persisted models"), T("lee documentos de modelo", "reads model documents"), 24);
  E("integration", "metamodel", "required", ALL, T("proyecta el lenguaje", "projects the language"), T("proyección tecnológica", "technology projection"), T("TechnologyIntegrationApplicationService", "TechnologyIntegrationApplicationService"), -40);
  E("publication", "model_editor", "required", ALL, T("genera a partir del modelo", "generates from the model"), T("metamodelo + modelo + destino", "metamodel + model + target"), T("ModelCodeGenerationService", "ModelCodeGenerationService"), 30);
  E("portability", "project", "required", ALL, T("empaqueta lo del proyecto", "packages the project's content"), T("capas activas del proyecto", "project's active layers"), T("CapabilityPackageArchiveService", "CapabilityPackageArchiveService"));
  // producer: the source creates artifacts consumed by the target
  E("recovery", "metamodel", "producer", ALL, T("recupera el lenguaje", "recovers the language"), T("materializa un metamodelo", "materializes a metamodel"), T("materialización en dos fases", "two-phase materialization"), -34);
  E("recovery", "project", "producer", ALL, T("incorpora al proyecto", "brings into the project"), T("preserva lo no reconciliable", "preserves unreconcilable content"), T("PROJECT_RESOURCE", "PROJECT_RESOURCE"));
  E("publication", "generated_projects", "producer", ALL, T("entrega el resultado", "delivers the result"), T("proyecto generado + informe", "generated project + report"), T("registro de proyecto generado", "generated-project registration"), 28);
  E("grammar", "model_editor", "producer", ALL, T("ofrece edición en texto", "offers text editing"), T("sesión textual sobre el modelo", "textual session over the model"), T("TextualModelSessionService", "TextualModelSessionService"), -48);
  // optional
  E("transformation", "grammar", "conditional", ALL, T("si convierte texto en modelo", "if it converts text into a model"), T("T2M parsea con una gramática compilada", "T2M parses with a compiled grammar"), T("GrammarTransformationTextParserGateway", "GrammarTransformationTextParserGateway"), -60);
  E("external_repositories", "project", "optional", ALL, T("importa/exporta el proyecto", "imports/exports the project"), T("transferencia de archivos del proyecto", "project file transfer"), T("RemoteGit / RemoteStorage", "RemoteGit / RemoteStorage"));
  E("model_editor", "validation", "optional", ALL, T("muestra errores y reparaciones", "shows errors and repairs"), T("diagnósticos + quick fixes en la sesión", "diagnostics + quick fixes in the session"), T("ValidationEngine ← sesión", "ValidationEngine ← session"), -36);
  // conditional
  E("transformation", "toolchains", "conditional", ALL, T("si usa un motor externo", "if it uses an external engine"), T("paridad nativa requiere motor verificado", "native parity requires a verified engine"), T("NativeToolchainProbeExecutor", "NativeToolchainProbeExecutor"));
  E("integration", "toolchains", "conditional", FT, T("si verifica con herramientas", "if it verifies with tools"), T("verificar proyecto exige build tools", "verifying a project needs build tools"), T("sondeos de compatibilidad", "compatibility probes"), 30);
  E("grammar", "toolchains", "conditional", FT, T("si construye la tecnología elegida", "if building the chosen technology"), T("npm/mvn necesarios para construir", "npm/mvn required to build"), T("sondeos npm/mvn", "npm/mvn probes"), -70);
  // alternative
  // event-driven
  E("model_editor", "events", "event", ALL, T("avisa de lo que haces", "reports what you do"), T("publica eventos (opt-in por sesión)", "publishes events (per-session opt-in)"), T("EditorSessionEventPublisher.publishEvents()", "EditorSessionEventPublisher.publishEvents()"));
  E("events", "gamification", "event", ALL, T("activa puntos y logros", "triggers points and achievements"), T("eventos directos y derivados de flujo", "direct and flow-derived events"), T("suscripción del runtime de gamificación", "gamification runtime subscription"));
  E("validation", "events", "event", FT, T("los resultados pueden convertirse en eventos", "results can become events"), T("diagnósticos publicados como eventos de sesión", "diagnostics published as session events"), T("eventos core.validation.*", "core.validation.* events"), -26);
  // external
  E("external_repositories", "ext_git", "external", ALL, T("se conecta con tu cuenta", "connects with your account"), T("OAuth + operaciones Git/almacenamiento", "OAuth + Git/storage operations"), T("RemoteGit / RemoteStorage / OAuth", "RemoteGit / RemoteStorage / OAuth"));
  E("toolchains", "ext_engines", "external", ALL, T("usa lo instalado en tu equipo", "uses what is installed on your machine"), T("sondeos reales de versión; nunca se simula", "real version probes; never simulated"), T("HydraExternalProcessRunner / sondeos", "HydraExternalProcessRunner / probes"));
  // platform links (functional/technical)
  E("metamodel", "persistence", "required", FT, T("", ""), T("guarda documentos canónicos", "stores canonical documents"), T("CanonicalDocumentStore", "CanonicalDocumentStore"), 70);
  E("persistence", "audit", "event", FT, T("", ""), T("cada guardado alimenta el registro", "every save feeds the log"), T("AuditCanonicalDocumentMutationObserver", "AuditCanonicalDocumentMutationObserver"));
  E("toolchains", "security", "required", FT, T("", ""), T("todo proceso externo pasa por el aislamiento", "every external process goes through isolation"), T("HydraExternalProcessRunner", "HydraExternalProcessRunner"), -40);
})();
