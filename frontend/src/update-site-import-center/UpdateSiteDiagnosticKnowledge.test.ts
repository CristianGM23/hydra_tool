import { describe, expect, it } from 'vitest';
import { explainDiagnostic, explainMissingDependency, explainSubject } from './UpdateSiteDiagnosticKnowledge';

describe('UpdateSiteDiagnosticKnowledge', () => {
    it('explains the high-level role and impact of missing Eclipse dependencies', () => {
        const explanation = explainMissingDependency({
            sourceId: 'library.editor',
            targetId: 'org.eclipse.emf.ecore',
            versionRange: '[2.30,3.0)',
            optional: false,
            resolved: false,
            origin: 'META-INF/MANIFEST.MF'
        });

        expect(explanation.category).toBe('EMF metamodel');
        expect(explanation.purpose).toMatch(/packages, classes, attributes, references/i);
        expect(explanation.impact).toMatch(/required/i);
        expect(explanation.recommendation).toMatch(/original \.ecore/i);
    });

    it('extracts known files from free-form diagnostics without hiding technical detail', () => {
        const explanation = explainDiagnostic('Could not locate plugin.xml for library.editor');

        expect(explanation.subject).toBe('plugin.xml');
        expect(explanation.category).toBe('Eclipse extension registry');
        expect(explanation.purpose).toMatch(/editors, commands, handlers, menus/i);
    });

    it('keeps a safe generic explanation for unknown domain-specific resources', () => {
        const explanation = explainSubject('custom-domain.rules');

        expect(explanation.category).toBe('Unclassified recovery issue');
        expect(explanation.recommendation).toMatch(/dedicated adapter/i);
    });
});
