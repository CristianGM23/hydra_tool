import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const source = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8');
const compact = (value: string) => value.replace(/\s+/g, '');

describe('textual model editor UX contract', () => {
    it('uses the full project-workbench width and owns vertical scrolling', () => {
        const css = compact(source('src/textual-model-editor/textual-model-editor.css'));
        expect(css).toContain('.textual-model-editor{box-sizing:border-box;display:flex;width:100%;max-width:none;min-width:0;height:100%;min-height:0;flex:11auto;');
        expect(css).toContain('overflow-y:auto');
        expect(css).toContain('scrollbar-gutter:stable');
        expect(css).toContain('.textual-workbench{box-sizing:border-box;display:grid;width:100%;min-width:0;');
    });

    it('navigates textual diagnostics back to the exact editor range', () => {
        const editor = source('src/textual-model-editor/TextualModelEditor.tsx');
        const problems = source('src/textual-model-editor/TextualModelProblems.tsx');
        expect(editor).toContain('focusRange={focusProblem?.range}');
        expect(editor).toContain('onNavigate={setFocusProblem}');
        expect(problems).toContain("t('Why')");
        expect(problems).toContain("t('Recommended solution')");
        expect(problems).toContain("t('Consequence')");
        expect(problems).toContain("t('Technical details')");
    });

    it('previews the last valid semantic model through the complete export perspective catalog', () => {
        const editor = source('src/textual-model-editor/TextualModelEditor.tsx');
        const api = source('src/model-codegen/api.ts');
        expect(editor).toContain("t('View model perspectives')");
        expect(editor).toContain('session.lastValidSemanticModel === null');
        expect(editor).toContain('<LazyModelCodeGenerationDialog');
        expect(editor).toContain('defaultTargetId="xmi"');
        expect(editor).toContain('listTextualSessionCodeGenerationTargets(session.id)');
        expect(editor).toContain('previewTextualSessionCode(session.id');
        expect(editor).toContain('generateTextualSessionCode(session.id');
        expect(api).toContain('/perspectives/targets');
        expect(api).toContain('/preview');
    });


    it('keeps the textual editor module resilient when optional surfaces fail to load', () => {
        const editor = source('src/textual-model-editor/TextualModelEditor.tsx');
        const boundary = source('src/design-system/HydraErrorBoundary.tsx');
        expect(editor).toContain('LazyGrammarMonacoEditor');
        expect(editor).toContain('renderEditorFallback()');
        expect(editor).toContain("HydraErrorBoundary scope={t('Textual editor surface')}");
        expect(boundary).toContain('fallback?: ReactNode');
        expect(boundary).toContain('hydra-ui-error-boundary-fallback');
    });


    it('starts grammar-backed validation automatically and never presents an unvalidated draft as clean', () => {
        const editor = source('src/textual-model-editor/TextualModelEditor.tsx');
        const problems = source('src/textual-model-editor/TextualModelProblems.tsx');
        expect(editor).toContain('void createTextualSession(definition.id,[sourceRef.current])');
        expect(editor).toContain("session?.draftValid === true");
        expect(editor).toContain('etagRef.current');
        expect(editor).toContain('}, [drainParseQueue, source, sessionId]);');
        expect(editor).not.toContain('[source, sessionId, etag]');
        expect(editor).toContain('pending={session===null||validationPending}');
        expect(problems).toContain("t('Textual validation pending')");
    });

    it('serializes live parsing and exposes deterministic grammar quick fixes', () => {
        const editor = source('src/textual-model-editor/TextualModelEditor.tsx');
        const problems = source('src/textual-model-editor/TextualModelProblems.tsx');
        expect(editor).toContain('parseBusyRef');
        expect(editor).toContain('queuedSourceRef');
        expect(editor).toContain('drainParseQueue');
        expect(editor).toContain('sessionMatchesSource');
        expect(editor).toContain('applySyntaxQuickFix');
        expect(problems).toContain('quickFixLabel');
        expect(problems).toContain("t('Expected by the grammar')");
    });


    it('provides Ctrl+Space completion from the active grammar in Monaco and the safe editor', () => {
        const editor = source('src/textual-model-editor/TextualModelEditor.tsx');
        const monaco = source('src/grammar-designer/GrammarMonacoEditor.tsx');
        const completion = source('src/textual-model-editor/textualModelCompletion.ts');
        expect(editor).toContain('textualModelCompletions(grammarModel,text,cursorOffset)');
        expect(editor).toContain('completionProvider={completionProvider}');
        expect(editor).toContain('neutralCompletionAvailable={grammarModel!==null}');
        expect(monaco).toContain('registerCompletionItemProvider');
        expect(monaco).toContain("event.code==='Space'");
        expect(monaco).toContain('Ctrl+Space');
        expect(monaco).toContain('grammar-safe-completion');
        expect(completion).toContain('parseGrammarSyntax(model)');
        expect(completion).toContain("case 'ZERO_OR_MORE'");
        expect(completion).toContain("case 'CHOICE'");
    });

    it('exposes negotiated language services and the generated provider project', () => {
        const editor = source('src/textual-model-editor/TextualModelEditor.tsx');
        const services = source('src/textual-model-editor/TextualLanguageServicesPanel.tsx');
        expect(editor).toContain('const pinned={...definition,compilationSnapshotId:snapshot.id};');
        expect(editor).toContain('checkTextualRuntime(pinned)');
        expect(editor).toContain('<TextualLanguageServicesPanel');
        expect(services).toContain('COMPLETION');
        expect(services).toContain('HOVER');
        expect(services).toContain('GO_TO_DEFINITION');
        expect(services).toContain('FIND_REFERENCES');
        expect(services).toContain('RENAME');
        expect(services).toContain("t('Download generated language project')");
    });
});
