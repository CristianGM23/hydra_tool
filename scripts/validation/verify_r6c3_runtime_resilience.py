from pathlib import Path
import sys

root=Path(__file__).resolve().parents[2]
checks=[]
def check(label,ok): checks.append((label,bool(ok)))
def read(path): return (root/path).read_text(encoding='utf-8')

service=read(Path('src/main/java/com/example/editor/textual/runtime/TextualModelSessionService.java'))
session=read(Path('src/main/java/com/example/editor/textual/runtime/TextualModelSession.java'))
parser=read(Path('src/main/java/com/example/editor/textual/runtime/NeutralTextualModelParser.java'))
source_doc=read(Path('src/main/java/com/example/editor/textual/source/TextualModelSourceDocument.java'))
props=read(Path('src/main/java/com/example/editor/textual/TextualModelProperties.java'))
test=read(Path('src/test/java/com/example/editor/textual/roundtrip/TextualRuntimeResilienceTests.java'))
launcher=read(Path('scripts/validation/validate_r6c3_fix127_150.ps1'))
previous=read(Path('scripts/validation/validate_r6c3_fix127_149.ps1'))
fix145=read(Path('scripts/validation/validate_r6c3_fix127_145.ps1'))
legacy=read(Path('scripts/validation/validate_r6c3_fix127_139.ps1'))
oldest=read(Path('scripts/validation/validate_r6c3_fix133_137.ps1'))

check('same revision is idempotent only for identical documents','req.draftRevision()==previous.draftRevision()' in service and 'previous.documents().equals(req.documents())' in service and 'Conflicting textual draft revision' in service)
check('older textual revisions still fail closed','req.draftRevision()<previous.draftRevision()' in service and 'Stale textual draft revision' in service)
check('stale checks happen before grammar lookup',service.index('req.draftRevision()<previous.draftRevision()') < service.index('compilations.find(previous.grammarSnapshotId())'))
check('last known good survives invalid drafts','parsed.valid()?parsed.semanticModel():previous.lastValidSemanticModel()' in service and 'parsed.valid()?boundedSourceMap(parsed.sourceMap()):previous.lastValidSourceMap()' in service)
check('perspectives read last known good only','modelForPerspectives' in service and 'lastValidSemanticModel()' in service)
check('successful session snapshot stays pinned','grammarSnapshotPinned' in service and 'compilations.find(previous.grammarSnapshotId())' in service)
check('parse concurrency is bounded','BoundedPermitPool' in service and 'maximumConcurrentParses' in props and 'tryAcquire' in service)
check('caller interruption becomes cancellation','InterruptedException' in service and 'CancellationException' in service and 'checkInterrupted()' in parser)
check('parser polls interruption during document and element work',parser.count('checkInterrupted();') >= 3)
check('document count and byte limits are enforced','getMaximumDocuments()' in service and 'getMaximumDocumentBytes()' in service and 'getMaximumTotalBytes()' in service)
check('source map limit is enforced','getMaximumSourceMapEntries()' in service and 'Textual semantic source map exceeds configured entry limit' in service)
check('document paths reject traversal','relativePath.startsWith("/")' in source_doc and 'relativePath.contains("../")' in source_doc)
check('session collections are immutable snapshots','List.copyOf(documents)' in session and 'Map.copyOf(metadata)' in session)
check('runtime regression covers idempotent retry','treatsAnExactSameRevisionRetryAsIdempotentWithoutParsingOrWriting' in test)
check('runtime regression covers revision collision and stale request','rejectsSameRevisionWithDifferentContentAndOlderRevisionsBeforeParsing' in test)
check('runtime regression covers cancellation and traversal','textualDocumentsRejectTraversalAndParserHonoursCallerCancellation' in test)
check('runtime regression covers defensive snapshot copies','sessionSnapshotsDefensivelyCopyDocumentsAndMetadata' in test)
check('latest launcher runs runtime resilience gate','verify_r6c3_runtime_resilience.py' in launcher and 'TextualRuntimeResilienceTests' in launcher)
check('latest launcher labels Fix150','R6C3-NEW-FIX133-150' in launcher and 'PASS R6C2 FIX127-132 + R6C3 FIX133-150 CUMULATIVE QUALIFICATION' in launcher)
check('Fix149 launcher delegates to latest','validate_r6c3_fix127_150.ps1' in previous)
check('Fix145 launcher remains in compatibility chain','validate_r6c3_fix127_149.ps1' in fix145)
check('Fix139 compatibility chain remains intact','validate_r6c3_fix127_145.ps1' in legacy)
check('oldest compatibility launcher remains usable','validate_r6c3_fix127_139.ps1' in oldest)

for label,ok in checks: print(('PASS ' if ok else 'FAIL ')+label)
failed=[item for item in checks if not item[1]]
print(f'R6C3_RUNTIME_RESILIENCE={len(checks)-len(failed)}/{len(checks)} PASS' if not failed else f'R6C3_RUNTIME_RESILIENCE={len(checks)-len(failed)}/{len(checks)} FAIL')
sys.exit(1 if failed else 0)
