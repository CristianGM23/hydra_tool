import { describe, expect, it } from 'vitest';
import { translateUiText } from './translationCatalog';

const layerCopy: Readonly<Record<string, readonly string[]>> = {
    model: [
        'Open XMI', 'New model', 'Save XMI', 'Save layout', 'Load layout', 'Download XMI'
    ],
    metamodel: [
        'Import metamodel', 'Export metamodel', 'Connection routing', 'Delete selected elements',
        'Distribute horizontally', 'Documentation and AI'
    ],
    validation: [
        'How to create a validation rule', 'How to create a quick fix', 'Execution mode',
        'Message shown when the rule fails', 'No diagnostics available'
    ],
    events: [
        'Event contracts', 'Custom event types', 'Payload fields', 'Event architecture', 'Save events'
    ],
    eventFlow: [
        'How to define an event flow', 'Create or select a flow before using the diagram.', 'Runtime trace'
    ],
    gamification: [
        'Rules and progression', 'Evidence rules', 'Gamification ledger', 'Save gamification'
    ],
    editors: [
        'New editor', 'Palette group', 'Accepted containment relations', 'Background color',
        'Border pattern', 'Label position'
    ],
    integrations: [
        'Integration profiles', 'Target project and technology', 'Preview injection',
        'Apply injection', 'Rollback required'
    ],
    roundTrip: [
        'Synchronize changes with a generated project', 'Compare changes', 'Synchronize with backup'
    ],
    distribution: [
        'PWA and desktop distribution', 'Distribution targets', 'Materialize and validate'
    ],
    verification: [
        'Native adapter verification', 'Run selected probes', 'No compatibility matrix yet',
        'Run at least one profile to record auditable evidence.'
    ],
    generatedProjects: [
        'Generated project lifecycle profile', 'Register generated project', 'No lifecycle report yet'
    ],
    artifacts: [
        'Neutral artifact registry', 'Registered artifacts', 'Dependency cycles'
    ],
    representableImports: [
        'Representable bidirectional import', 'External change candidates', 'Representability diagnostics',
        'HYDRA will separate neutral operations from partial and unsupported constructs.'
    ],
    updateSite: [
        'Eclipse update site recovery and evolution workbench', 'Actionable diagnostics',
        'Canonical layer materialization', 'Generated editor semantics', 'Compatibility observatory'
    ],
    publication: [
        'Distribution publication and updates', 'Publication channel', 'Publish release'
    ],
    projectExplorer: [
        'Hide explorer', 'Projects…', 'Send app to project', 'Load project into app',
        'Download project ZIP', 'Close project'
    ]
};

describe('HYDRA Spanish coverage across every application layer', () => {
    it('translates representative fields, actions, tooltips and information in every layer', () => {
        for (const [layer, strings] of Object.entries(layerCopy)) {
            for (const source of strings) {
                expect(translateUiText(source, 'es'), `${layer}: ${source}`).not.toBe(source);
            }
        }
    });

    it('keeps proper names, technologies and persisted identifiers unchanged', () => {
        for (const source of ['Windows', 'MongoDB', 'EventFlow', 'Ecore', 'GMF', 'people.ecore', 'sourceUri']) {
            expect(translateUiText(source, 'es')).toBe(source);
        }
    });

    it('never builds a partial translation for unknown copy', () => {
        const source = 'This future extension is not registered in the Spanish catalogue yet.';
        expect(translateUiText(source, 'es')).toBe(source);
    });
});
