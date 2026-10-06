import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const frontend=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const root=path.resolve(frontend,'..');
const src=path.join(frontend,'src');
const files=[];
function walk(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,e.name);if(e.isDirectory())walk(p);else if(/\.(tsx|css)$/.test(p))files.push(p);}}
walk(src);
const css=files.filter(f=>f.endsWith('.css')).map(f=>fs.readFileSync(f,'utf8')).join('\n');
const packageJson=JSON.parse(fs.readFileSync(path.join(frontend,'package.json'),'utf8'));
const axeTest=path.join(src,'accessibility/axeAccessibility.test.tsx');
const browserTest=path.join(frontend,'e2e/accessibility.spec.ts');
const browserSource=fs.existsSync(browserTest)?fs.readFileSync(browserTest,'utf8'):'';
const checks=[
 ['forced-colors',/@media\s*\(forced-colors:\s*active\)/.test(css)],
 ['reduced-motion',/@media\s*\(prefers-reduced-motion:\s*reduce\)/.test(css)],
 ['focus-visible',/:focus-visible/.test(css)],
 ['container-query',/@container\s+hydra-workspace/.test(css)],
 ['axe-core dependency',Boolean(packageJson.devDependencies?.['axe-core'])],
 ['axe component gate',fs.existsSync(axeTest)&&fs.readFileSync(axeTest,'utf8').includes('axe.run')],
 ['playwright dependency',Boolean(packageJson.devDependencies?.['@playwright/test'])],
 ['playwright keyboard/focus gate',browserSource.includes("keyboard.press('Tab')")&&browserSource.includes("locator(':focus')")],
 ['playwright 200/400 percent coverage',browserSource.includes("style.zoom = '2'")&&browserSource.includes('width: 320')&&browserSource.includes('width: 640')],
];
for(const [name,ok] of checks)console.log(`${ok?'PASS':'FAIL'} ${name}`);
const ok=checks.every(([,value])=>value);
console.log(`ACCESSIBILITY_GATE=${ok?'PASS':'FAIL'}`);
if(!ok)process.exit(1);
