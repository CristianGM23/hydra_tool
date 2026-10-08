#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
def read(rel):
    p=ROOT/rel
    return p.read_text(encoding='utf-8') if p.exists() else ''
checks=[]
def check(name,ok):
    checks.append((name,bool(ok)));print(('PASS' if ok else 'FAIL')+' '+name)
base='src/main/java/com/example/editor/textual/interaction/'
required=[
'TextualInteractionCapability.java','TextualInteractionExecutionMode.java','TextualInteractionCapabilityState.java',
'TextualInteractionRequest.java','TextualCompletionItem.java','TextualNavigationLocation.java','TextualHoverInfo.java',
'TextualRenameRequest.java','TextualRenamePlan.java','TextualDocumentSymbol.java','TextualFoldingRange.java',
'TextualSemanticToken.java','TextualDependencyInvalidationPlan.java','TextualSnapshotCompatibilityRequest.java',
'TextualSnapshotCompatibilityReport.java','TextualWorkspaceIndex.java','TextualLanguageFeatureService.java','TextualInteractionFacade.java']
for name in required: check('exists '+name,(ROOT/base/name).exists())
service=read(base+'TextualLanguageFeatureService.java');workspace=read(base+'TextualWorkspaceIndex.java');facade=read(base+'TextualInteractionFacade.java');controller=read('src/main/java/com/example/editor/textual/web/TextualModelSessionController.java');test=read('src/test/java/com/example/editor/textual/interaction/TextualLanguageFeatureServiceTests.java');api=read('config/api-contract.json');client=read('frontend/src/api/generated/hydraApiClient.ts');docs=read('docs/grammar/R6C4_TEXTUAL_INTERACTION_PARITY.md')
# Fix151
check('Fix151 capability matrix is explicit','HYDRA_NEUTRAL' in service and 'PROVIDER_NATIVE_EXTERNAL' in read(base+'TextualInteractionExecutionMode.java') and 'nativeHost' in service)
# Fix152
check('Fix152 one immutable workspace index feeds all features','class TextualWorkspaceIndex' in workspace and 'TextualSemanticSourceMap indexedSourceMap' in workspace and 'Map.copyOf' in workspace)
# Fix153
check('Fix153 completion is bounded and semantic','completion(TextualWorkspaceIndex' in service and 'MAX_COMPLETIONS' in service and 'REFERENCE' in service and 'TYPE' in service)
# Fix154
check('Fix154 hover uses source map','hover(TextualWorkspaceIndex' in service and 'workspace.at' in service and 'TextualHoverInfo' in service)
# Fix155
check('Fix155 definition resolves target identity','definition(TextualWorkspaceIndex' in service and 'targetSemanticId' in service and 'definitionOf' in service)
# Fix156
check('Fix156 references are indexed and bounded','references(TextualWorkspaceIndex' in service and 'targetElementId' in service and 'MAX_REFERENCES' in service)
# Fix157
check('Fix157 rename is preview-first and validates stable ids','TextualRenamePlan rename' in service and 'StableIdentifierPolicy.isValidElementId' in service and 'previewOnly' in service and 'GrammarTextEdit' in service)
# Fix158
check('Fix158 formatting is deterministic and non-mutating','format(TextualWorkspaceIndex' in service and 'formatText' in service and 'stripTrailing' in service and "replace(\"\\r\\n\",\"\\n\")" in service)
# Fix159
check('Fix159 document symbols derive from semantic model','documentSymbols' in service and 'MODEL_ELEMENT' in service and 'MAX_SYMBOLS' in service)
# Fix160
check('Fix160 folding derives from semantic spans','folding(TextualWorkspaceIndex' in service and 'TextualFoldingRange' in service and 'end().line()>entry.range().start().line()' in service)
# Fix161
check('Fix161 semantic tokens are neutral and bounded','semanticTokens' in service and 'MAX_TOKENS' in service and 'reference' in service and 'property' in service)
# Fix162
check('Fix162 invalidation computes transitive cross-document closure','invalidation(TextualWorkspaceIndex' in service and 'ArrayDeque' in service and 'reverse.computeIfAbsent' in service and 'fullReparseRequired' in read(base+'TextualDependencyInvalidationPlan.java'))
# Fix163
check('Fix163 snapshot compatibility fails closed','snapshotCompatibility' in service and 'MIGRATION_REQUIRED' in service and 'REPARSE_SAFE' in service and 'EXACT' in service)
# Fix164
check('Fix164 operations are bounded cancellable and pinned to a coherent last-known-good revision','checkInterrupted()' in service and 'CancellationException' in service and 'lastValidSemanticModel' in facade and 'lastParsedRevision()!=session.draftRevision()' in facade and 'session.draftValid()' in facade)
# Fix165
endpoints=['interactions/capabilities','interactions/completion','interactions/hover','interactions/definition','interactions/references','interactions/rename/preview','interactions/format','interactions/symbols','interactions/folding','interactions/semantic-tokens','interactions/invalidation','interactions/snapshot-compatibility']
check('Fix165 REST exposes all interaction capabilities',all(x in controller for x in endpoints))
check('Fix165 OpenAPI publishes all interaction routes',all(x in api for x in endpoints))
check('Fix165 generated TypeScript client includes interaction operations','interactions' in client and 'TextualModelSessionController_completion_get' in client and 'TextualModelSessionController_renamePreview_post' in client)
check('Fix165 regression tests cover every major capability',all(token in test for token in ['completionReusesCanonicalTypesFeaturesAndElementIds','hoverUsesSmallestSemanticSourceMapEntry','definitionAndReferencesCrossDocuments','renameIsPreviewFirstAndCoversDeclarationAndReferences','formatSymbolsFoldingAndTokensShareOneIndex','invalidationIsTransitiveAcrossCrossDocumentReferences','snapshotCompatibilityDistinguishesExactReparseAndMigration','operationsAreBoundedAndDeterministic']))
check('Fix165 documentation exists and names all fixes',all(f'Fix{i}' in docs for i in range(151,166)))
check('no interaction code mutates session repository directly','repository.' not in service and 'repository.' not in facade)
check('rename never applies edits directly','replace(' not in service.split('TextualRenamePlan rename',1)[1].split('List<GrammarTextEdit>format',1)[0])
check('interaction API is last-known-good only','No last-known-good semantic model' in facade)

# Fix166 / Fix167
launcher168=read('scripts/validation/validate_r6c4_fix151_168.ps1')
launcher169=read('scripts/validation/validate_r6c4_fix151_169.ps1')
active_launcher=launcher169 or launcher168
launcher167=read('scripts/validation/validate_r6c4_fix151_167.ps1')
launcher166=read('scripts/validation/validate_r6c4_fix151_166.ps1')
launcher165=read('scripts/validation/validate_r6c4_fix151_165.ps1')
check('Fix167 canonical launcher exists',(ROOT/'scripts/validation/validate_r6c4_fix151_167.ps1').exists())
check('Fix167 previous launchers delegate forward','validate_r6c4_fix151_167.ps1' in launcher166 and 'validate_r6c4_fix151_167.ps1' in launcher165)
check('Fix167 qualification has no named dependency on historical R6C3 verifiers','verify_r6c3_' not in launcher167 and 'validate_r6c3_' not in launcher167)
check('Fix167/168/169 source integrity remains manifest based','verify_manifest.py' in active_launcher and 'SOURCE-INTEGRITY' in active_launcher and 'MANIFEST_SHA256.txt' in active_launcher)
required_native=['TextualMetamodelSemanticRoundTripTests','TextualGrammarImportRegressionTests','TextualInstanceSemanticRoundTripTests','TextualInstanceAggressiveRoundTripTests','TextualRuntimeResilienceTests','TextualLanguageFeatureServiceTests']
check('Fix167/168/169 reruns focused R6C3 and R6C4 native behavior',all(x in active_launcher for x in required_native))
check('Fix167/168/169 package preflight checks actual production and test surfaces','TextualWorkspaceIndex.java' in active_launcher and 'TextualLanguageFeatureServiceTests.java' in active_launcher and 'frontend\\package.json' in active_launcher)
check('Fix167 documentation records full-source recovery','Fix167' in docs and 'overlay completo' in docs)
check('Fix167 release notes record packaging hardening','R6C4 Fix167' in read('HYDRA_V4.32.107_RELEASE_NOTES.md'))
# Fix168
manifest_verifier=read('scripts/validation/verify_manifest.py')
check('Fix168 canonical launcher exists',(ROOT/'scripts/validation/validate_r6c4_fix151_168.ps1').exists())
check('Fix168 previous launcher delegates forward','validate_r6c4_fix151_168.ps1' in launcher167)
check('Fix168/169 workspace qualification allows preserved extras','verify_manifest.py' in active_launcher and "'--allow-extra'" in active_launcher and 'manifest-workspace' in active_launcher)
check('Fix168 manifest verifier keeps strict mode by default','--allow-extra' in manifest_verifier and 'blocking_extra = [] if args.allow_extra else extra' in manifest_verifier)
check('Fix168 manifest verifier still blocks missing and changed canonical files','if missing or blocking_extra or changed' in manifest_verifier and 'FAIL missing' in manifest_verifier and 'FAIL changed' in manifest_verifier)
check('Fix168 release notes record workspace-extra preservation','R6C4 Fix168' in read('HYDRA_V4.32.107_RELEASE_NOTES.md'))

# Fix169
check('Fix169 canonical launcher exists',(ROOT/'scripts/validation/validate_r6c4_fix151_169.ps1').exists())
check('Fix169 previous launcher delegates forward','validate_r6c4_fix151_169.ps1' in read('scripts/validation/validate_r6c4_fix151_168.ps1'))
check('Fix169 navigation uses relation-aware cursor resolution','navigationEntry(workspace,request)' in service and 'MODEL_RELATION' in service and 'allMatch(Character::isWhitespace)' in service)
check('Fix169 regression covers cursor immediately before cross-document reference','definitionUsesReferenceTargetWhenCursorIsImmediatelyBeforeCrossDocumentToken' in test and 'assertEquals(exact,adjacent)' in test)
check('Fix169 release notes record cross-document navigation correction','R6C4 Fix169' in read('HYDRA_V4.32.107_RELEASE_NOTES.md'))

passed=sum(ok for _,ok in checks)
print(f'R6C4_TEXTUAL_INTERACTIONS={passed}/{len(checks)} PASS' if passed==len(checks) else f'R6C4_TEXTUAL_INTERACTIONS={passed}/{len(checks)} FAIL')
if passed!=len(checks): sys.exit(1)
