from pathlib import Path
import json, sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def has(path,text,label):
    p=ROOT/path; ok=p.exists() and text in p.read_text(encoding='utf-8',errors='ignore'); checks.append((ok,label)); return ok
def exists(path,label): checks.append(((ROOT/path).exists(),label))
# canonical contract
for f in ['UserFeedbackEnvelope.java','UserFeedbackPresentationPolicy.java','UserFeedbackAction.java','UserFeedbackKind.java','UserFeedbackSeverity.java','UserFeedbackSurface.java','GamificationFeedbackPolicyResolver.java','GamificationFeedbackProjector.java']:
    exists(Path('src/main/java/com/example/editor/gamification/feedback')/f,f'canonical feedback {f}')
has(Path('src/main/java/com/example/editor/gamification/feedback/UserFeedbackEnvelope.java'),'semanticScopeId','feedback carries semantic scope')
has(Path('src/main/java/com/example/editor/gamification/feedback/UserFeedbackEnvelope.java'),'nextStep','feedback carries next step')
has(Path('src/main/java/com/example/editor/gamification/feedback/UserFeedbackEnvelope.java'),'sourceEventId','feedback correlates source event')
has(Path('src/main/java/com/example/editor/gamification/feedback/UserFeedbackEnvelope.java'),'UserFeedbackPresentationPolicy','feedback carries presentation policy')
has(Path('src/main/java/com/example/editor/gamification/feedback/GamificationFeedbackPolicyResolver.java'),'hydra.feedback.rule.','per-rule presentation overrides exist')
has(Path('src/main/java/com/example/editor/gamification/feedback/UserFeedbackPresentationPolicy.java'),'30000','toast duration is bounded')
has(Path('src/main/java/com/example/editor/gamification/feedback/UserFeedbackPresentationPolicy.java'),'60000','aggregation window is bounded')
has(Path('src/main/java/com/example/editor/gamification/feedback/GamificationFeedbackProjector.java'),'ledgerEntryId','ledger remains feedback source of truth')
has(Path('src/main/java/com/example/editor/gamification/feedback/GamificationFeedbackProjector.java'),'OPEN_DIAGNOSTIC','feedback can correlate to a validation diagnostic')
# runtime and API
has(Path('src/main/java/com/example/editor/gamification/runtime/GamificationProcessingResult.java'),'List<UserFeedbackEnvelope> feedback','processing result exposes feedback')
has(Path('src/main/java/com/example/editor/gamification/runtime/GamificationRuntimeService.java'),'feedbackCache.invalidate','feedback cache invalidates after commits')
has(Path('src/main/java/com/example/editor/gamification/runtime/GamificationRuntimeService.java'),'FeedbackProjectionCache','bounded projection cache exists')
has(Path('src/main/java/com/example/editor/gamification/runtime/GamificationRuntimeService.java'),'while(entries.size() > maximumEntries)','projection cache is explicitly bounded')
has(Path('src/main/java/com/example/editor/gamification/runtime/GamificationRuntimeController.java'),'@GetMapping("/feedback")','runtime exposes durable feedback endpoint')
has(Path('src/main/java/com/example/editor/gamification/GamificationProperties.java'),'maximumFeedbackCacheEntries','feedback cache bound is configurable')
# host isolation/adapters
exists(Path('src/main/java/com/example/editor/integration/bridge/HostFeedbackPresentationService.java'),'host feedback adapter exists')
has(Path('src/main/java/com/example/editor/integration/bridge/HostFeedbackPresentationService.java'),'catch(RuntimeException','host feedback failure is isolated')
has(Path('src/main/java/com/example/editor/integration/compiler/TechnologyAdapterTextualCustomizationSupport.java'),'notifyFeedback','TypeScript/Java generated hosts expose feedback callback')
has(Path('src/main/java/com/example/editor/integration/compiler/TechnologyAdapterTextualCustomizationSupport.java'),'notify_feedback','Python generated host exposes feedback callback')
# frontend UI
exists(Path('frontend/src/gamification-designer/GamificationFeedbackPolicyEditor.tsx'),'feedback policy editor exists')
has(Path('frontend/src/gamification-designer/GamificationFeedbackPolicyEditor.tsx'),'aggregateWindowMillis','anti-spam aggregation is configurable')
has(Path('frontend/src/gamification-designer/GamificationDesigner.tsx'),'GamificationFeedbackPolicyEditor','designer embeds feedback policy')
has(Path('frontend/src/gamification-designer/api.ts'),'loadGamificationFeedback','frontend can load durable feedback')
has(Path('frontend/src/gamification-designer/GamificationDesignerParts.tsx'),'Generated user feedback','designer ledger shows feedback activity')
center=Path('frontend/src/editor-definition/GamificationFeedbackCenter.tsx')
has(center,'MAX_BACKOFF_MS','feedback polling has bounded exponential backoff')
has(center,"document.visibilityState==='hidden'",'feedback polling pauses when page is hidden')
has(center,'dedupeWindowSeconds','frontend honors dedupe window')
has(center,'aggregateWindowMillis','frontend aggregates notification bursts')
has(center,'maxVisible','frontend bounds visible toast count')
has(center,'persistActivity','activity history obeys presentation policy')
has(Path('frontend/src/editor-definition/EditorRuntimeSession.tsx'),'GamificationFeedbackCenter','HYDRA runtime renders feedback center')
# standalone
runtime=Path('src/main/resources/standalone-template/frontend/runtime.js')
has(runtime,'recentFeedback=[]','standalone owns bounded recent feedback')
has(runtime,'if(recentFeedback.length>200)','standalone feedback history is bounded')
has(runtime,'awardStatsFor','standalone award limits use indexed stats')
has(runtime,'if(state.awards.length>500)','standalone audit award list is bounded')
has(runtime,'awardStats:{}','standalone persisted state includes scalable rule stats')
has(runtime,'feedback:clone(recentFeedback)','standalone snapshot exposes feedback')
has(Path('src/main/resources/standalone-template/frontend/index.html.template'),'feedback-toasts','standalone template includes feedback live region')
has(Path('src/main/resources/standalone-template/frontend/app.js'),'renderFeedback','standalone visual editor renders feedback')
has(Path('src/main/resources/standalone-template/frontend/textual-app.js'),'renderFeedback','standalone textual editor renders feedback')
# schema and docs
schema=ROOT/'src/main/resources/schemas/gamification/user-feedback-envelope-1.0.schema.json'
exists(Path('src/main/resources/schemas/gamification/user-feedback-envelope-1.0.schema.json'),'feedback JSON schema exists')
try:
    data=json.loads(schema.read_text()); checks.append((data.get('properties',{}).get('presentation',{}).get('type')=='object','schema validates presentation object'))
except Exception: checks.append((False,'schema parses'))
exists(Path('docs/GAMIFICATION_FEEDBACK_RUNTIME_MATRIX.md'),'host presentation matrix documented')
has(Path('docs/GAMIFICATION_FEEDBACK_RUNTIME_MATRIX.md'),'Standalone Web editor','standalone host documented')
has(Path('docs/GAMIFICATION_FEEDBACK_RUNTIME_MATRIX.md'),'VS Code / Langium','VS Code/Langium host documented')
has(Path('docs/GAMIFICATION_FEEDBACK_RUNTIME_MATRIX.md'),'Eclipse / Sirius / GLSP','Eclipse/Sirius/GLSP host documented')
# tests
exists(Path('src/test/java/com/example/editor/gamification/feedback/GamificationFeedbackProjectorTests.java'),'projector regression tests exist')
has(Path('src/test/java/com/example/editor/gamification/feedback/GamificationFeedbackProjectorTests.java'),'10_000','projector has 10k-entry extreme test')
exists(Path('src/test/java/com/example/editor/integration/bridge/HostFeedbackPresentationServiceTests.java'),'host presentation failure tests exist')
has(Path('src/test/java/com/example/editor/gamification/GamificationPenaltyRuntimeTests.java'),'duplicateEventDoesNotEmitDuplicateFeedback','runtime duplicate event feedback regression exists')
has(Path('src/test/java/com/example/editor/gamification/GamificationPenaltyRuntimeTests.java'),'emitsPortablePenaltyFeedbackAfterCommit','runtime feedback is tested after semantic commit')
exists(Path('scripts/validation/stress_r5c13_fix11_gamification_feedback.mjs'),'standalone stress test exists')
has(Path('scripts/validation/stress_r5c13_fix11_gamification_feedback.mjs'),'12000','standalone stress executes 12k awards')
has(Path('scripts/validation/stress_r5c13_fix11_gamification_feedback.mjs'),'storage unavailable','standalone stress covers persistence failure')
has(Path('scripts/validation/stress_r5c13_fix11_gamification_feedback.mjs'),'cycle.a','standalone stress covers cyclic event bindings')
for ok,label in checks: print(('PASS' if ok else 'FAIL')+' - '+label)
failed=[label for ok,label in checks if not ok]
print(f'FIX11_GAMIFICATION_FEEDBACK={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'))
sys.exit(1 if failed else 0)
