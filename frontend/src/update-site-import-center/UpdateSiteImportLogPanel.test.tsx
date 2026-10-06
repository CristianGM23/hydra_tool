import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { UpdateSiteImportLogPanel } from './UpdateSiteImportLogPanel';
import type { UpdateSiteDependency, UpdateSiteFileOutcome, UpdateSiteGrammarInspection,
    UpdateSiteImportLogEntry } from './types';

const logs: UpdateSiteImportLogEntry[] = [
    { id: 'grammar', stage: 'GRAMMAR_ANALYSIS', severity: 'WARNING', status: 'PARTIAL', title: 'Xtext grammar Library',
        message: 'Possible direct left recursion was detected.', sourcePath: 'language/Library.xtext', artifactKind: 'XTEXT_GRAMMAR',
        purpose: 'Defines textual syntax and semantic mappings.', recoveredAs: '3 parser rules', nextAction: 'Review rule Element.',
        confidence: 'DERIVED', details: ['Element starts with itself.'] },
    { id: 'bundle', stage: 'BUNDLES', severity: 'SUCCESS', status: 'SUPPORTING', title: 'Bundle classification',
        message: 'One model plug-in was found.', sourcePath: 'plugins/model.jar', artifactKind: 'OSGI_BUNDLE',
        purpose: 'Groups generated resources.', recoveredAs: 'Model plug-in', nextAction: '', confidence: 'RECOVERED', details: [] }
];
const files: UpdateSiteFileOutcome[] = [
    { sourcePath: 'icons/book.png', artifactKind: 'IMAGE', purpose: 'Visual icon used by an Eclipse editor.', status: 'SUPPORTING',
        recoveredItems: 0, recoveredAs: '', explanation: 'Useful to preserve visual identity.', nextAction: 'Associate it with a recovered palette tool.', relatedElements: [] },
    { sourcePath: 'about.html', artifactKind: 'OTHER', purpose: 'Human-facing plug-in documentation.', status: 'NOT_RECOVERABLE',
        recoveredItems: 0, recoveredAs: '', explanation: 'It does not define metamodel structure.', nextAction: 'Keep it as documentation.', relatedElements: [] }
];

const missingDependencies: UpdateSiteDependency[] = [
    { sourceId: 'library.editor', targetId: 'org.eclipse.emf.ecore', versionRange: '[2.30,3.0)',
        optional: false, resolved: false, origin: 'plugins/library.editor.jar!/META-INF/MANIFEST.MF' }
];

const grammars: UpdateSiteGrammarInspection[] = [{ sourcePath: 'language/Library.xtext', language: 'Xtext', grammarName: 'Library',
    parserRules: 3, terminalRules: 1, imports: 1, assignments: 4, crossReferences: 1, actions: 0,
    duplicateRules: [], possibleLeftRecursions: ['Element'], inferredEntryRule: 'Model', entryInferenceMethod: 'XTEXT_FIRST_PARSER_RULE', entryInferenceConfidence: 0.98, recursiveRules: ['Element'], automaticResolutionRules: ['Element'], diagnostics: ['Direct left recursion was detected in Element.'] }];

describe('UpdateSiteImportLogPanel', () => {
    it('filters deep logs while keeping high-level explanations for unrecovered files and grammar risks', () => {
        render(<UpdateSiteImportLogPanel logs={logs} files={files} grammars={grammars}
            missingDependencies={missingDependencies}
            diagnostics={['Could not locate plugin.xml for library.editor']} />);

        expect(screen.getByText('Human-facing plug-in documentation.')).toBeInTheDocument();
        expect(screen.getByText(/keep it as documentation/i)).toBeInTheDocument();
        expect(screen.getByText(/left-recursive rules/i)).toBeInTheDocument();
        expect(screen.getByText(/inferred entry rule/i)).toBeInTheDocument();
        expect(screen.getByText(/automatic rewrite available/i)).toBeInTheDocument();
        expect(screen.getByText(/defines packages, classes, attributes, references/i)).toBeInTheDocument();
        expect(screen.getByText(/registers editors, commands, handlers, menus/i)).toBeInTheDocument();
        expect(screen.getAllByText('What it is for').length).toBeGreaterThanOrEqual(2);

        fireEvent.change(screen.getByLabelText('Search'), { target: { value: 'classification' } });
        expect(screen.getByText('Bundle classification')).toBeInTheDocument();
        expect(screen.queryByText('Xtext grammar Library')).not.toBeInTheDocument();

        fireEvent.change(screen.getByLabelText('Search'), { target: { value: '' } });
        fireEvent.change(screen.getByLabelText('Severity'), { target: { value: 'WARNING' } });
        expect(screen.getByText('Xtext grammar Library')).toBeInTheDocument();
        expect(screen.queryByText('Bundle classification')).not.toBeInTheDocument();
    });
});
