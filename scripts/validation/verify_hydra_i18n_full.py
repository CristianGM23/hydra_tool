from __future__ import annotations
from pathlib import Path
import ast,csv,re,sys
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'frontend/src'
errors=[]
entry_re=re.compile(r'^\s*([\'\"])(.*)\1\s*:\s*', re.MULTILINE)

def catalog_keys(path: Path) -> list[str]:
    result=[]
    for match in entry_re.finditer(path.read_text(encoding='utf-8')):
        raw=match.group(1)+match.group(2)+match.group(1)
        try: result.append(ast.literal_eval(raw))
        except (ValueError,SyntaxError): pass
    return result


def validate_catalog_entry_separators(path: Path) -> list[str]:
    """Detect adjacent literal object entries that are missing their comma separator."""
    lines=path.read_text(encoding='utf-8').splitlines()
    failures=[]
    entry_start=re.compile(r'^\s*[\'"]')
    for index,line in enumerate(lines):
        stripped=line.strip()
        if not entry_start.match(line) or ':' not in stripped:
            continue
        next_line=''
        for candidate in lines[index+1:]:
            candidate=candidate.strip()
            if candidate and not candidate.startswith('//'):
                next_line=candidate
                break
        if next_line.startswith(("'", '"')) and not stripped.endswith(','):
            failures.append(
                f'{path.relative_to(ROOT)}:{index+1} missing comma between catalog entries'
            )
    return failures

catalog_files=list((SRC/'i18n').glob('*SpanishCatalog*.ts'))
all_keys=set()
for p in catalog_files:
    errors.extend(validate_catalog_entry_separators(p))
    keys=catalog_keys(p); all_keys.update(keys)
    seen=set(); dup=[]
    for key in keys:
        if key in seen: dup.append(key)
        seen.add(key)
    if dup: errors.append(f'{p.relative_to(ROOT)} duplicate keys: {sorted(set(dup))[:8]}')

glossary=ROOT/'docs/i18n/HYDRA_ES_GLOSSARY.csv'
if not glossary.is_file(): errors.append('missing canonical Spanish glossary')
else:
    rows=list(csv.DictReader(glossary.open(encoding='utf-8-sig')))
    for row in rows:
        key=row['english'].strip()
        if key and key not in all_keys: errors.append(f'missing catalog key: {key}')

for p in SRC.rglob('*.tsx'):
    text=p.read_text(encoding='utf-8',errors='ignore').lower()
    if 'c:\\users\\cristian' in text or 'cristian to cristian' in text: errors.append(f'personal example/path in {p.relative_to(ROOT)}')
print(f'HYDRA_I18N_FULL={"PASS" if not errors else "FAIL"}')
for e in errors: print('FAIL ::',e)
sys.exit(1 if errors else 0)
