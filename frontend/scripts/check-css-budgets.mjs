import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const config=JSON.parse(fs.readFileSync(path.join(root,'config/size-exceptions.json'),'utf8'));
const now=new Date('2026-08-20T00:00:00Z');
const exceptions=new Map(config.exceptions.map(item=>[item.path,item]));
const css=[];
const walk=dir=>{for(const entry of fs.readdirSync(dir,{withFileTypes:true})){const full=path.join(dir,entry.name);if(entry.isDirectory())walk(full);else if(entry.name.endsWith('.css'))css.push(full);}};
walk(path.join(root,'frontend/src'));
let ok=true;let literals=0;let importants=0;
for(const full of css){
  const text=fs.readFileSync(full,'utf8');
  const rel=path.relative(root,full).replaceAll('\\','/');
  const lines=text.length===0?0:text.split(/\r?\n/).length-(text.endsWith('\n')?1:0);
  literals+=(text.match(/#[0-9a-fA-F]{3,8}\b/g)||[]).length;
  importants+=(text.match(/!important\b/g)||[]).length;
  if(lines>config.cssLineBudget){
    const exception=exceptions.get(rel);
    const valid=exception&&exception.owner&&exception.rationale&&Number.isInteger(exception.maxLines)&&lines<=exception.maxLines&&new Date(exception.expiresOn+'T23:59:59Z')>=now;
    console.log(`${valid?'EXCEPTION':'FAIL'} - ${rel}: ${lines} lines`);
    if(!valid)ok=false;
  }
}
for(const [rel,exception] of exceptions){
  if(!fs.existsSync(path.join(root,rel))){console.log(`FAIL - stale CSS exception: ${rel}`);ok=false;}
  if(!exception.owner||!exception.rationale||!exception.expiresOn||!Number.isInteger(exception.maxLines)){console.log(`FAIL - incomplete CSS exception: ${rel}`);ok=false;}
}
const literalBudget=Number.isInteger(config.maxLiteralColors)?config.maxLiteralColors:799;
const importantBudget=Number.isInteger(config.maxImportantDeclarations)?config.maxImportantDeclarations:263;
console.log(`${literals<=literalBudget?'PASS':'FAIL'} - literal CSS colors ${literals}/${literalBudget}`);if(literals>literalBudget)ok=false;
console.log(`${importants<=importantBudget?'PASS':'FAIL'} - !important declarations ${importants}/${importantBudget}`);if(importants>importantBudget)ok=false;
console.log(`CSS_BUDGETS=${ok?'PASS':'FAIL'}`);process.exit(ok?0:1);
