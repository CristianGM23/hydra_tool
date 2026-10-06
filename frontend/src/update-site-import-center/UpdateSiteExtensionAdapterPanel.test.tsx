import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { UpdateSiteExtensionAdapterPanel } from './UpdateSiteExtensionAdapterPanel';
import type { UpdateSiteExtensionAdapterDescriptor, UpdateSiteSemanticRecovery } from './types';

const adapters: UpdateSiteExtensionAdapterDescriptor[] = [{
    id: 'custom-palettes', label: 'Custom palette extension', extensionPointTokens: ['palette'],
    elementNames: ['palette', 'tool'], semanticKind: 'FORM', neutralTarget: 'HYDRA palette/tool draft',
    purpose: 'Declares tool groups.', idAttributes: ['id'], implementationAttributes: ['provider'],
    confidence: 'DERIVED', enabled: true
}];
const semantic: UpdateSiteSemanticRecovery = {
    elements: [{ id: 'form:library', kind: 'FORM', label: 'Library palette', implementation: '', sourcePath: 'plugin.xml',
        extensionPoint: 'org.example.palette', confidence: 'DERIVED', representable: true,
        neutralTarget: 'HYDRA palette/tool draft', purpose: 'Declares tool groups.',
        warnings: ['Recovered through declarative extension adapter: custom-palettes.'] }],
    representableCount: 1, partialCount: 0, unrepresentableCount: 0, diagnostics: []
};

describe('UpdateSiteExtensionAdapterPanel', () => {
    it('explains server-owned adapters and marks rules used in the current analysis', () => {
        render(<UpdateSiteExtensionAdapterPanel adapters={adapters} semantic={semantic} />);
        expect(screen.getByText('Custom palette extension')).toBeInTheDocument();
        expect(screen.getByText('Used in this analysis')).toBeInTheDocument();
        expect(screen.getByText('HYDRA palette/tool draft')).toBeInTheDocument();
    });
});
