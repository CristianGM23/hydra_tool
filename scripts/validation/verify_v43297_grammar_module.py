#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name, ok): checks.append((name,bool(ok)))
def text(rel):
    p=ROOT/rel
    return p.read_text(encoding='utf-8') if p.is_file() else ''
def exists(rel): return (ROOT/rel).is_file()
# Neutral core / providers
for rel in [
'src/main/java/com/example/editor/grammar/core/GrammarDefinitionModel.java',
'src/main/java/com/example/editor/grammar/core/GrammarSourceDocument.java',
'src/main/java/com/example/editor/grammar/core/GrammarSemanticBindingMap.java',
'src/main/java/com/example/editor/grammar/provider/GrammarProvider.java',
'src/main/java/com/example/editor/grammar/provider/GrammarProviderRegistry.java',
'src/main/java/com/example/editor/grammar/provider/xtext/XtextGrammarProvider.java',
'src/main/java/com/example/editor/grammar/provider/langium/LangiumGrammarProvider.java',
'src/main/java/com/example/editor/grammar/provider/antlr/AntlrGrammarProvider.java']:
    check('exists '+rel, exists(rel))
cap=text('src/main/java/com/example/editor/grammar/provider/GrammarProviderCapabilities.java')
check('provider capabilities distinguish LIVE/GENERATED_HOST','LIVE' in cap and 'GENERATED_HOST' in cap)
profiles=text('src/main/java/com/example/editor/grammar/application/GrammarToolchainProfileService.java')
for literal in ['2.44','4.3','4.13.2']:
    check('pinned toolchain '+literal,literal in profiles)
check('no xtext-web dependency in provider generation','org.eclipse.xtext.web' not in text('src/main/java/com/example/editor/grammar/provider/xtext/XtextGrammarProvider.java').lower())
# Analysis/snapshots/fault tolerance
check('FAST NORMAL FULL modes', all(x in text('src/main/java/com/example/editor/grammar/core/GrammarAnalysisMode.java') for x in ['FAST','NORMAL','FULL']))
coord=text('src/main/java/com/example/editor/grammar/application/GrammarAnalysisCoordinator.java')
check('stale/cancellation coordination','stale' in coord.lower() and ('cancel' in coord.lower() or 'revision' in coord.lower()))
check('bounded tracked revisions','getMaximumTrackedGrammarRevisions' in coord and 'trackRevision' in coord)
snap=text('src/main/java/com/example/editor/grammar/runtime/GrammarCompilationSnapshot.java')
check('snapshot retains runtime files','runtimeFiles' in snap)
check('snapshot retains inferred class diagram','classDiagram' in snap)
check('last known good service',exists('src/main/java/com/example/editor/grammar/runtime/LastKnownGoodGrammarService.java'))
compile=text('src/main/java/com/example/editor/grammar/application/GrammarCompilationService.java')
check('FULL compilation required','GrammarAnalysisMode.FULL' in compile)
check('atomic success promotion evidence','partialArtifactsPublished' in compile and 'Only a successful grammar snapshot can be activated' in compile)
# Diagnostics
codes=text('src/main/java/com/example/editor/grammar/diagnostic/ReferenceDiagnosticCode.java')
for code in ['REFERENCE_NOT_FOUND','REFERENCE_NOT_VISIBLE','REFERENCE_WRONG_TYPE','REFERENCE_AMBIGUOUS','REFERENCE_IMPORT_MISSING','REFERENCE_MAPPING_MISMATCH','REFERENCE_CYCLE','REFERENCE_TARGET_DELETED','REFERENCE_CASE_MISMATCH','REFERENCE_STALE']:
    check('reference diagnostic '+code,code in codes)
check('ranked reference candidates',exists('src/main/java/com/example/editor/grammar/diagnostic/ReferenceCandidate.java') and 'score' in text('src/main/java/com/example/editor/grammar/diagnostic/ReferenceCandidate.java').lower())
check('structured text edits',exists('src/main/java/com/example/editor/grammar/diagnostic/GrammarTextEdit.java'))
# Class diagram
for rel in ['src/main/java/com/example/editor/grammar/visualization/GrammarClassDiagramProjectionService.java','frontend/src/grammar-designer/GrammarClassDiagram.tsx','frontend/src/grammar-designer/grammarClassDiagramModel.ts','frontend/src/grammar-designer/grammarClassDiagramModel.test.ts']:
    check('diagram exists '+rel,exists(rel))
proj=text('src/main/java/com/example/editor/grammar/visualization/GrammarClassDiagramProjectionService.java')
check('diagram uses source navigation','GrammarDiagramSourceRef' in proj)
check('diagram has last-known-good degradation','last' in proj.lower() and ('partial' in proj.lower() or 'degrad' in proj.lower()))
diagram_ui=text('frontend/src/grammar-designer/GrammarClassDiagram.tsx')
check('diagram uses shared ELK layout','layoutHydraElkStyle' in text('frontend/src/grammar-designer/grammarClassDiagramModel.ts'))
check('diagram filters/navigates','filter' in diagram_ui.lower() and ('source' in diagram_ui.lower() or 'range' in diagram_ui.lower()))
# Textual runtime convergence
parser=text('src/main/java/com/example/editor/textual/runtime/NeutralTextualModelParser.java')
check('textual parser produces CanonicalModelDocument','CanonicalModelDocument' in parser)
recon=text('src/main/java/com/example/editor/textual/runtime/TextualCanonicalReconciliationService.java')
check('unsafe reconciliation rejected','TextualReconciliationResult.unsafe' in recon)
check('reparse/equivalence reconciliation',('parse' in recon.lower()) and ('equiv' in recon.lower() or 'semantic' in recon.lower()))
shared=text('src/main/java/com/example/editor/textual/runtime/TextualSharedLayerRuntimeService.java')
check('Validation reuse','ValidationRuntimeApplicationService' in shared)
check('EventFlow/Gamification use CanonicalEventBus','CanonicalEventBus' in shared and 'gamificationReuse' in shared and 'eventFlowReuse' in shared)
session=text('src/main/java/com/example/editor/textual/runtime/TextualModelSession.java')
check('draft + last valid + last committed separated',all(x in session for x in ['lastValidSemanticModel','lastCommittedSemanticModel','draft']))
# Standalone
stand=text('src/main/java/com/example/editor/standalone/StandaloneEditorGeneratorService.java')
for state in ['BUILD_REQUIRED','LANGUAGE_SERVER_HOST_REQUIRED','BUNDLED_BROWSER_RUNTIME']:
    check('standalone runtime state '+state,state in stand)
check('standalone uses frozen snapshot runtime','runtimeFiles' in stand and 'compilationSnapshotId' in stand)
# Security/config
props=text('src/main/resources/application.properties')
for key in ['dsl.grammars.maximum-tracked-grammar-revisions','dsl.grammars.native-maximum-heap-megabytes','dsl.grammars.native-capture']:
    check('configurable '+key,key in props)
native=text('src/main/java/com/example/editor/grammar/application/GrammarNativeToolchainService.java')
check('native execution bounded','timeout' in native.lower() and ('maximum' in native.lower() or 'limit' in native.lower()))
check('native capture protects symlinks/traversal','symbolic' in native.lower() and ('normalize' in native.lower() or 'travers' in native.lower()))
# Frontend and i18n
check('grammar designer integrated in App','LazyGrammarDesigner' in text('frontend/src/application-shell/HydraCapabilityModule.tsx'))
check('textual editor integrated in App','LazyTextualModelEditor' in text('frontend/src/application-shell/HydraCapabilityModule.tsx'))
check('grammar Spanish catalog',exists('frontend/src/i18n/grammarSpanishCatalog.ts'))
monaco=text('frontend/src/grammar-designer/GrammarMonacoEditor.tsx')
check('Monaco fallback present','textarea' in monaco.lower() and 'monaco' in monaco.lower())
# Permanent aggressive tests
for rel in ['scripts/validation/GrammarV43297StressHarness.java','scripts/validation/GrammarProviderV43297ContractHarness.java','scripts/validation/GrammarDiagramV43297StressHarness.java','src/test/java/com/example/editor/grammar/visualization/GrammarClassDiagramProjectionServiceTests.java','src/test/java/com/example/editor/grammar/application/GrammarNativeToolchainServiceTests.java']:
    check('permanent verification '+rel,exists(rel))
# No provider-specific branches in designer surface
react='\n'.join(p.read_text(encoding='utf-8') for p in (ROOT/'frontend/src/grammar-designer').glob('*.tsx'))
check('React has no provider-specific implementation branches',not re.search(r'if\s*\([^\n]*(XTEXT|LANGIUM|ANTLR)',react,re.I))
for name,ok in checks: print(('PASS' if ok else 'FAIL')+' - '+name)
failed=[n for n,o in checks if not o]
print(f'V43297_GRAMMAR_MODULE={len(checks)-len(failed)}/{len(checks)}')
if failed:
    print('FAILED: '+', '.join(failed))
    sys.exit(1)
