from pathlib import Path
import sys
root=Path(__file__).resolve().parents[2]
checks=[]
def check(label, ok):
    checks.append((label,bool(ok)))

def read(path): return (root/path).read_text(encoding='utf-8')
comp=read(Path('src/main/java/com/example/editor/metamodel/roundtrip/MetamodelSemanticComparator.java'))
svc=read(Path('src/main/java/com/example/editor/metamodel/roundtrip/TextualMetamodelRoundTripService.java'))
xtext=read(Path('src/main/java/com/example/editor/metamodel/export/XtextMetamodelExporter.java'))
lang=read(Path('src/main/java/com/example/editor/metamodel/export/LangiumMetamodelExporter.java'))
imp=read(Path('src/main/java/com/example/editor/metamodel/importing/support/GeneratedGrammarImportSupport.java'))
test=read(Path('src/test/java/com/example/editor/metamodel/roundtrip/TextualMetamodelSemanticRoundTripTests.java'))
parity=read(Path('src/main/java/com/example/editor/metamodel/roundtrip/TextualProviderParityService.java'))
capability=read(Path('src/main/java/com/example/editor/metamodel/roundtrip/TextualSemanticCapabilityAnalyzer.java'))
cumulative=read(Path('scripts/validation/validate_r6c3_fix127_150.ps1'))
compatibility=read(Path('scripts/validation/validate_r6c3_fix133_137.ps1'))
check('neutral semantic comparator exists', 'class MetamodelSemanticComparator' in comp and 'classSignatures' in comp and 'relationSignatures' in comp)
check('ids are not semantic identity', 'item.id()' not in comp.split('private Map<String,String> classSignatures')[1].split('private String attributeSignature')[0])
check('round-trip service supports Xtext and Langium only', 'case XTEXT -> xtext' in svc and 'case LANGIUM -> langium' in svc)
check('lossy round-trip fails closed', 'requireLossless' in svc and 'IllegalStateException' in svc)
check('Xtext no longer injects anonymous ID', 'else {\n                result.append("ID ");' not in xtext)
check('name=ID remains conditional in Xtext', 'if(nameAttribute != null)' in xtext and 'name=ID' in xtext)
check('name=ID remains conditional in Langium', 'if(nameAttribute != null)' in lang and 'name=ID' in lang)
check('semantic root marker emitted by both exporters', 'HYDRA_SEMANTIC_ROOT' in xtext and 'HYDRA_SEMANTIC_ROOT' in lang)
check('semantic root marker restored after transport stripping', 'applySemanticRoot' in imp and 'SEMANTIC_ROOT' in imp)
check('transport rules remain explicitly marked', 'HYDRA_TRANSPORT_RULE' in xtext and 'HYDRA_TRANSPORT_RULE' in lang)
check('regression covers containment vs reference fidelity', 'MetaRelationKind.CONTAINMENT' in test and 'MetaRelationKind.REFERENCE' in test)
check('regression covers cross-provider equivalence', 'compare(xtext.reimportedMetamodel(), langium.reimportedMetamodel())' in test)
check('provider parity service compares reconstructed semantic models', 'compare(xtext.reimportedMetamodel(), langium.reimportedMetamodel())' in parity)
check('native scoping never creates a synthetic name', 'REFERENCE_SCOPE_REQUIRES_EXTERNAL_PROVIDER' in capability and 'synthetic attribute' in capability)
check('cumulative gate inlines inherited R6C2 qualification', 'Invoke-R6C2InheritedQualification' in cumulative and 'verify_r6c2_fix127_132_delivery_readiness.py' in cumulative)
check('cumulative gate labels inherited and new phases', 'R6C2-INHERITED-FIX127-132' in cumulative and 'R6C3-NEW-FIX133-150' in cumulative)
check('cumulative gate only declares final pass after both phases', 'PASS R6C2 FIX127-132 + R6C3 FIX133-150 CUMULATIVE QUALIFICATION' in cumulative and cumulative.index('R6C2-INHERITED-FIX127-132') < cumulative.index('R6C3-NEW-FIX133-150'))
check('cumulative gate has package-integrity preflight', 'Assert-RequiredPath' in cumulative and '[PACKAGE-INTEGRITY]' in cumulative)
check('cumulative gate does not depend on inherited R6C2 launcher', "& (Join-Path $root 'scripts\validation\validate_r6c2_fix127_132.ps1')" not in cumulative)
check('legacy R6C3 launcher remains in compatibility chain', 'validate_r6c3_fix127_139.ps1' in compatibility and 'validate_r6c3_fix127_145.ps1' in read(Path('scripts/validation/validate_r6c3_fix127_139.ps1')) and 'validate_r6c3_fix127_149.ps1' in read(Path('scripts/validation/validate_r6c3_fix127_145.ps1')) and 'validate_r6c3_fix127_150.ps1' in read(Path('scripts/validation/validate_r6c3_fix127_149.ps1')))
check('backend documentation gate runs before expensive inherited checks', cumulative.index("r6c2-backend-docs") < cumulative.index("r6c2-openapi-contract"))
roundtrip_doc_files = [
    Path('src/main/java/com/example/editor/metamodel/export/GrammarGenerationSupport.java'),
    Path('src/main/java/com/example/editor/metamodel/importing/support/GeneratedGrammarImportSupport.java'),
    Path('src/main/java/com/example/editor/metamodel/roundtrip/MetamodelSemanticComparator.java'),
    Path('src/main/java/com/example/editor/metamodel/roundtrip/SemanticEquivalenceReport.java'),
    Path('src/main/java/com/example/editor/metamodel/roundtrip/SemanticFidelity.java'),
    Path('src/main/java/com/example/editor/metamodel/roundtrip/TextualMetamodelRoundTripService.java'),
    Path('src/main/java/com/example/editor/metamodel/roundtrip/TextualProviderParityReport.java'),
    Path('src/main/java/com/example/editor/metamodel/roundtrip/TextualProviderParityService.java'),
    Path('src/main/java/com/example/editor/metamodel/roundtrip/TextualSemanticCapabilityAnalyzer.java'),
    Path('src/main/java/com/example/editor/metamodel/roundtrip/TextualSemanticCapabilityReport.java'),
]
check('R6C3 production methods keep Spanish documentation contract', all(
    all(token in read(path) for token in ('Descripción:', 'Entrada', 'Salida:')) for path in roundtrip_doc_files
))
for label,ok in checks: print(('PASS ' if ok else 'FAIL ')+label)
failed=[x for x in checks if not x[1]]
print(f'R6C3_SEMANTIC_ROUNDTRIP={len(checks)-len(failed)}/{len(checks)} PASS' if not failed else f'R6C3_SEMANTIC_ROUNDTRIP={len(checks)-len(failed)}/{len(checks)} FAIL')
sys.exit(1 if failed else 0)
