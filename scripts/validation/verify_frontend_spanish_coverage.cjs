#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const Module = require('module');

const ROOT = path.resolve(__dirname, '..', '..');
const FRONT = path.join(ROOT, 'frontend');
const SRC = path.join(FRONT, 'src');
let ts;
try {
  ts = require(path.join(FRONT, 'node_modules', 'typescript'));
} catch {
  try { ts = require('typescript'); } catch {
    const { execFileSync } = require('child_process');
    const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
    const globalRoot = execFileSync(npm, ['root', '-g'], { encoding:'utf8' }).trim();
    ts = require(path.join(globalRoot, 'typescript'));
  }
}

// Tiny TS loader used only for the dependency-free i18n catalogue. It lets this
// gate execute the real translateUiText implementation even when frontend
// node_modules cannot be installed in the delivery container.
const cache = new Map();
function loadTs(file) {
  file = path.resolve(file);
  if (cache.has(file)) return cache.get(file).exports;
  const source = fs.readFileSync(file, 'utf8');
  const code = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true }
  }).outputText;
  const mod = new Module(file, module);
  cache.set(file, mod);
  mod.filename = file;
  mod.paths = Module._nodeModulePaths(path.dirname(file));
  mod.require = (request) => {
    if (request.startsWith('.')) {
      const candidate = path.resolve(path.dirname(file), request);
      for (const suffix of ['.ts', '.tsx', '/index.ts']) {
        if (fs.existsSync(candidate + suffix)) return loadTs(candidate + suffix);
      }
    }
    return require(request);
  };
  mod._compile(code, file);
  return mod.exports;
}
const { translateUiText } = loadTs(path.join(SRC, 'i18n', 'translationCatalog.ts'));

const VISIBLE_PROPERTIES = new Set([
  'label','title','help','description','placeholder','aria-label','alt','introduction','finalNote','message','heading','subtitle','emptyText',
  'confirmLabel','cancelLabel','closeLabel','maximizeLabel','restoreLabel','advancedLabel','guidedLabel','finishLabel','downloadLabel','ariaLabel','emptyMessage','emptySelectionLabel','optionHelpTitle','purpose','recommendation','rationale','tooltip','hint','caption','summary','successMessage','errorMessage','warningMessage','outcome','callbackNote','idHelp','where','detail','capability','result','steps','context','scope'
]);
const RUNTIME_SETTERS = new Set(['setError','setMessage','setStatus','setNotice','setWarning','setProblem','onError','onMessage','onNotice','onWarning']);
const TECHNICAL_RUNTIME = new Set(['READY','BLOCKED','PASS','FAIL','ERROR','WARNING','INFO']);

function propertyName(node) { return ts.isIdentifier(node) || ts.isStringLiteral(node) ? node.text : ''; }
function lineOf(sf,node) { return sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1; }
function normalize(value) { return value.replace(/\s+/g,' ').trim(); }
function looksHuman(value) {
  const t=normalize(value);
  // Keep this aligned with HydraProductionSpanishCoverage.test.ts: technical
  // values are excluded only through its reviewed allow-list, never because
  // they happen to be uppercase or look like an identifier.
  return t !== '' && t.length <= 800 && /[A-Za-z]/.test(t);
}

// Reuse the allow-list reviewed by the production Vitest contract.
const productionTest = fs.readFileSync(path.join(SRC,'i18n','HydraProductionSpanishCoverage.test.ts'),'utf8');
const testSf = ts.createSourceFile('coverage.ts', productionTest, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
const intentionally = new Set();
(function visitAllow(node){
  if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.name.text==='INTENTIONALLY_UNTRANSLATED' && node.initializer) {
    const collect=(n)=>{ if(ts.isStringLiteralLike(n)) intentionally.add(n.text); else ts.forEachChild(n,collect); }; collect(node.initializer);
  }
  ts.forEachChild(node,visitAllow);
})(testSf);

const found=[];
let parsedFiles=0; const syntaxErrors=[];
function add(file,sf,node,text,kind) {
  const value=normalize(text); if(!looksHuman(value)) return;
  found.push({file:path.relative(SRC,file).replaceAll('\\','/'),line:lineOf(sf,node),text:value,kind});
}
function collectStringBranches(expr, cb) {
  if (!expr) return;
  if (ts.isStringLiteralLike(expr) || ts.isNoSubstitutionTemplateLiteral(expr)) { cb(expr,expr.text); return; }
  if (ts.isConditionalExpression(expr)) { collectStringBranches(expr.whenTrue,cb); collectStringBranches(expr.whenFalse,cb); return; }
  if (ts.isArrayLiteralExpression(expr)) { for (const element of expr.elements) collectStringBranches(element,cb); return; }
  if (ts.isBinaryExpression(expr) && [ts.SyntaxKind.BarBarToken,ts.SyntaxKind.QuestionQuestionToken].includes(expr.operatorToken.kind)) { collectStringBranches(expr.left,cb); collectStringBranches(expr.right,cb); return; }
}
function scanFile(file) {
  const source=fs.readFileSync(file,'utf8');
  const sf=ts.createSourceFile(file,source,ts.ScriptTarget.Latest,true,file.endsWith('.tsx')?ts.ScriptKind.TSX:ts.ScriptKind.TS);
  parsedFiles++;
  for (const diagnostic of sf.parseDiagnostics) {
    const location=sf.getLineAndCharacterOfPosition(diagnostic.start ?? 0);
    syntaxErrors.push(`${path.relative(SRC,file).replaceAll('\\','/')}:${location.line+1} ${ts.flattenDiagnosticMessageText(diagnostic.messageText,' ')}`);
  }
  function visit(node) {
    if (ts.isJsxText(node)) add(file,sf,node,node.getText(sf),'jsx');
    if (ts.isJsxAttribute(node) && VISIBLE_PROPERTIES.has(propertyName(node.name)) && node.initializer) {
      const init=node.initializer;
      if (ts.isStringLiteral(init)) add(file,sf,init,init.text,'attribute');
      if (ts.isJsxExpression(init)) collectStringBranches(init.expression,(n,v)=>add(file,sf,n,v,'attribute-expression'));
    }
    if (ts.isPropertyAssignment(node) && VISIBLE_PROPERTIES.has(propertyName(node.name))) {
      collectStringBranches(node.initializer,(n,v)=>add(file,sf,n,v,'property'));
    }
    // Literal/conditional text inserted directly as a JSX child.
    if (ts.isJsxExpression(node) && (ts.isJsxElement(node.parent) || ts.isJsxFragment(node.parent))) {
      collectStringBranches(node.expression,(n,v)=>add(file,sf,n,v,'jsx-expression'));
    }
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)) {
      const name=node.expression.text;
      const arg=node.arguments[0];
      if (name==='t' && arg && ts.isStringLiteralLike(arg)) add(file,sf,arg,arg.text,'t-call');
      if (RUNTIME_SETTERS.has(name) && arg) collectStringBranches(arg,(n,v)=>add(file,sf,n,v,'runtime'));
    }
    ts.forEachChild(node,visit);
  }
  visit(sf);
}
function walk(dir) {
  for(const entry of fs.readdirSync(dir,{withFileTypes:true})) {
    const p=path.join(dir,entry.name);
    if(entry.isDirectory()) walk(p);
    else if(/\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name) && !entry.name.endsWith('.d.ts')) {
      const relativePath=path.relative(SRC,p).replaceAll('\\','/');
      if(relativePath.startsWith('i18n/') && /(?:SpanishCatalog|translationCatalog|runtimeSpanishPatterns)/.test(entry.name)) continue;
      scanFile(p);
    }
  }
}
walk(SRC);
if (syntaxErrors.length) {
  console.error(`TS_TSX_SYNTAX=${parsedFiles} files diagnostics=${syntaxErrors.length}`);
  for (const error of syntaxErrors.slice(0,100)) console.error(error);
  process.exit(1);
}
console.log(`TS_TSX_SYNTAX=${parsedFiles} files diagnostics=0`);

const missing=[];
const seen=new Set();
for(const item of found) {
  if (intentionally.has(item.text) || (item.kind==='runtime' && TECHNICAL_RUNTIME.has(item.text))) continue;
  if (translateUiText(item.text,'es') !== item.text) continue;
  const key=item.text; if(seen.has(key)) continue; seen.add(key); missing.push(item);
}
if(missing.length) {
  console.error(`FRONTEND_SPANISH_COVERAGE=FAIL missing=${missing.length}`);
  for(const m of missing) console.error(`${m.file}:${m.line} [${m.kind}] ${m.text}`);
  process.exit(1);
}
console.log(`FRONTEND_SPANISH_COVERAGE=PASS checked=${found.length} unique=${new Set(found.map(x=>x.text)).size} intentional=${intentionally.size}`);
