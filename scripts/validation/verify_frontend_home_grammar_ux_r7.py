from pathlib import Path
import hashlib

ROOT = Path('/mnt/data/hydra_front_fix_tree')
checks = []

def file_has(path, needle):
    text = (ROOT / path).read_text(encoding='utf-8')
    return needle in text

checks.append(("GrammarStructureTree hides DOCUMENT symbols", file_has('frontend/src/grammar-designer/GrammarStructureTree.tsx', "item.kind!=='DOCUMENT'")))
checks.append(("GrammarStructureTree shows wrapped full names", file_has('frontend/src/grammar-designer/GrammarStructureTree.tsx', "<strong title={symbol.name}>{symbol.name}</strong>")))
checks.append(("GrammarDesigner uses local diagnostics fallback", file_has('frontend/src/grammar-designer/GrammarDesigner.tsx', 'localVisibleDiagnostics')))
checks.append(("grammarLocalInspection exports diagnostics", file_has('frontend/src/grammar-designer/grammarLocalInspection.ts', 'diagnostics: GrammarDiagnostic[]')))
checks.append(("Local inspection detects unexpected top-level text", file_has('frontend/src/grammar-designer/grammarLocalInspection.ts', 'Unexpected text in the grammar')))
checks.append(("Local inspection detects unresolved references", file_has('frontend/src/grammar-designer/grammarLocalInspection.ts', 'Unresolved grammar reference')))
checks.append(("Compilation cards use optimized auto-fit width", file_has('frontend/src/grammar-designer/grammar-designer.css', 'repeat(auto-fit,minmax(158px,1fr))')))
checks.append(("Workbench left rail reduced to free more editor width", file_has('frontend/src/grammar-designer/grammar-designer.css', 'grid-template-columns:minmax(188px,240px) minmax(0,1fr)')))
all_ok = all(flag for _, flag in checks)
print('R7 static verification:', 'PASS' if all_ok else 'FAIL')
for label, flag in checks:
    print(('OK  ' if flag else 'FAIL'), label)

zip_path = Path('/mnt/data/hydra_front_fix_delivery/HYDRA_V4.32.107_FRONTEND_HOME_GRAMMAR_UX_GENERATED_CODE_CUMULATIVE_R7.zip')
if zip_path.exists():
    h = hashlib.sha256(zip_path.read_bytes()).hexdigest()
    print('ZIP SHA-256', h)
