from pathlib import Path
import sys

root=Path(__file__).resolve().parents[2]
checks=[]
def check(label,ok): checks.append((label,bool(ok)))
def read(path): return (root/path).read_text(encoding='utf-8')

comparator=read(Path('src/main/java/com/example/editor/textual/roundtrip/CanonicalModelSemanticComparator.java'))
codec=read(Path('src/main/java/com/example/editor/textual/roundtrip/GeneratedTextualInstanceCodec.java'))
service=read(Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceRoundTripService.java'))
result=read(Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceRoundTripResult.java'))
dependency=read(Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceDocumentDependency.java'))
parity=read(Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceProviderParityService.java'))
test=read(Path('src/test/java/com/example/editor/textual/roundtrip/TextualInstanceSemanticRoundTripTests.java'))
stress=read(Path('src/test/java/com/example/editor/textual/roundtrip/TextualInstanceAggressiveRoundTripTests.java'))
doc=read(Path('docs/grammar/R6C3_INSTANCE_SEMANTIC_ROUNDTRIP.md'))
launcher=read(Path('scripts/validation/validate_r6c3_fix127_150.ps1'))
previous=read(Path('scripts/validation/validate_r6c3_fix127_149.ps1'))
fix145=read(Path('scripts/validation/validate_r6c3_fix127_145.ps1'))
legacy=read(Path('scripts/validation/validate_r6c3_fix127_139.ps1'))
oldest=read(Path('scripts/validation/validate_r6c3_fix133_137.ps1'))

check('canonical instance comparator exists','class CanonicalModelSemanticComparator' in comparator and 'CanonicalModelSemanticReport' in comparator)
check('instance invariants validate containment topology','MULTIPLE_CONTAINERS' in comparator and 'CONTAINMENT_CYCLE' in comparator and 'ROOTS_DO_NOT_MATCH_CONTAINMENT' in comparator)
check('instance invariants validate attributes and cardinalities','ATTRIBUTE_BELOW_LOWER_BOUND' in comparator and 'ATTRIBUTE_TYPE_MISMATCH' in comparator and 'RELATION_ABOVE_UPPER_BOUND' in comparator)
check('relation instance ids are not semantic identity','relationTargets' in comparator and 'relation.id()' not in comparator.split('public CanonicalModelSemanticReport compare')[1].split('public CanonicalModelInvariantReport validate')[0])
check('generated codec only supports Xtext and Langium','format!=MetamodelExportFormat.XTEXT&&format!=MetamodelExportFormat.LANGIUM' in codec)
check('native identity never synthesizes name','INSTANCE_IDENTITY_REQUIRES_EXTERNAL_PROVIDER' in codec and 'INSTANCE_IDENTITY_MISMATCH' in codec)
check('deep containment is serialized recursively','writeElement(index,relation.targetElementId()' in codec and 'MetaRelationKind.CONTAINMENT' in codec)
check('containment roots remain top level','state.roots.add(root)' in codec and 'parseElement(null,null)' in codec)
check('multi-document strategies are explicit','ONE_DOCUMENT_PER_ROOT' in read(Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceDocumentStrategy.java')) and 'SINGLE_DOCUMENT' in read(Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceDocumentStrategy.java')))
check('cross-document references are measured','crossDocumentReferences' in codec)
check('cross-document dependencies are typed workspace scope','TextualInstanceDocumentDependency' in dependency and 'WORKSPACE_SCOPE' in codec and 'requiresWorkspaceIndex' in codec)
check('dependency plan is round-trip verified','INSTANCE_DOCUMENT_DEPENDENCY_MISMATCH' in service and 'decoded.dependencies()' in service)
check('forward backward references resolve after all documents','resolvePendingReferences' in codec and 'state.pending.add' in codec)
check('keyword-colliding identities fail closed','INSTANCE_ID_COLLIDES_WITH_KEYWORD' in codec and 'generatedKeywords' in codec)
check('nonportable native ids fail closed','INSTANCE_ID_NOT_PORTABLE_ID' in codec and '[A-Za-z_][A-Za-z0-9_]*' in codec)
check('generated keyword collisions fail closed','INSTANCE_CLASS_KEYWORD_COLLISION' in codec and 'INSTANCE_FEATURE_KEYWORD_COLLISION' in codec and 'validateGeneratedKeywordUniqueness' in codec)
check('generated paths are traversal safe','safePath' in codec and '.replace("..","_")' in codec)
check('source maps cover elements features relations','"MODEL_ELEMENT"' in codec and '"MODEL_FEATURE"' in codec and '"MODEL_RELATION"' in codec)
check('identity token has feature binding','"identity","true"' in codec and 'id+":"+name.id()' in codec)
check('source maps retain metamodel and grammar bindings','metamodelElementId' not in codec or 'state.ruleId' in codec)
check('binding coverage blocks false lossless','bindingCoverageComplete' in result and 'INSTANCE_BINDING_COVERAGE_INCOMPLETE' in service)
check('binding coverage enumerates all reconstructed semantics','elementBindings' in result and 'featureBindings' in result and 'relationBindings' in result and 'element.attributes().keySet()' in result)
check('round trip fails closed','requireLossless' in service and 'IllegalStateException' in service)
check('provider parity compares reconstructed instances','compare(metamodel,xtext.reconstructedModel(),langium.reconstructedModel())' in parity)
check('regression covers deep containment','Deep containment' in test and 'deepContainmentDepth' in test)
check('regression covers multi-file cycles','crossDocumentReferences' in test and 'favoriteBook' in test)
check('regression covers workspace dependency plan','WORKSPACE_SCOPE' in test and 'result.dependencies()' in test)
check('regression covers identity fail closed','INSTANCE_IDENTITY_REQUIRES_EXTERNAL_PROVIDER' in test and 'INSTANCE_IDENTITY_MISMATCH' in test)
check('regression covers keyword identity collision','INSTANCE_ID_COLLIDES_WITH_KEYWORD' in test and 'failsClosedWhenElementIdentityCollidesWithGeneratedKeyword' in test)
check('regression covers portable ids and keyword ambiguity','INSTANCE_ID_NOT_PORTABLE_ID' in test and 'INSTANCE_FEATURE_KEYWORD_COLLISION' in test)
check('nonportable-id fixture is canonically valid before provider rejection','StableIdentifierPolicy.isValidElementId(nonPortableId)' in test and 'nonPortableId="node-1"' in test and '../evil' not in test)
check('regression follows generated optional-feature syntax',"'subtitle' subtitle=STRING?" in test and '"subtitle"' in test)
check('regression covers containment cycle rejection','CONTAINMENT_CYCLE' in test)
check('aggressive campaign covers forty documents','index<40' in stress and 'hasSize(40)' in stress and 'crossDocumentReferences","40' in stress)
check('aggressive campaign checks determinism','first.documents()' in stress and 'second.documents()' in stress)
check('aggressive campaign exercises both providers and cross parity','MetamodelExportFormat.XTEXT' in stress and 'MetamodelExportFormat.LANGIUM' in stress and 'requireSemanticParity' in stress)
check('instance documentation exists','Round-trip semántico de instancias Xtext/Langium' in doc and 'Source maps y bindings' in doc)
check('latest cumulative launcher includes instance gate','verify_r6c3_instance_roundtrip.py' in launcher and 'TextualInstanceSemanticRoundTripTests' in launcher and 'TextualInstanceAggressiveRoundTripTests' in launcher)
check('latest cumulative launcher labels Fix150','R6C3-NEW-FIX133-150' in launcher and 'PASS R6C2 FIX127-132 + R6C3 FIX133-150 CUMULATIVE QUALIFICATION' in launcher)
check('Fix149 launcher delegates forward','validate_r6c3_fix127_150.ps1' in previous)
check('Fix145 launcher remains in compatibility chain','validate_r6c3_fix127_149.ps1' in fix145)
check('Fix139 compatibility chain remains intact','validate_r6c3_fix127_145.ps1' in legacy)
check('oldest compatibility launcher remains usable','validate_r6c3_fix127_139.ps1' in oldest)

production_files=[
 Path('src/main/java/com/example/editor/textual/roundtrip/CanonicalModelInvariantReport.java'),
 Path('src/main/java/com/example/editor/textual/roundtrip/CanonicalModelSemanticComparator.java'),
 Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceDocumentDependency.java'),
 Path('src/main/java/com/example/editor/textual/roundtrip/GeneratedTextualInstanceCodec.java'),
 Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceProviderParityReport.java'),
 Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceProviderParityService.java'),
 Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceRoundTripResult.java'),
 Path('src/main/java/com/example/editor/textual/roundtrip/TextualInstanceRoundTripService.java'),
]
check('instance production methods keep Spanish documentation contract',all(all(token in read(path) for token in ('Descripción:','Entrada','Salida:')) for path in production_files))

for label,ok in checks: print(('PASS ' if ok else 'FAIL ')+label)
failed=[item for item in checks if not item[1]]
print(f'R6C3_INSTANCE_ROUNDTRIP={len(checks)-len(failed)}/{len(checks)} PASS' if not failed else f'R6C3_INSTANCE_ROUNDTRIP={len(checks)-len(failed)}/{len(checks)} FAIL')
sys.exit(1 if failed else 0)
