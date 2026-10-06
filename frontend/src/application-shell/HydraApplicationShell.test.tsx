import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listGrammars } from '../grammar-designer/api';
import { HydraApplicationShell } from './HydraApplicationShell';

vi.mock('../grammar-designer/api',()=>({listGrammars:vi.fn()}));

describe('HYDRA application shell', () => {
    beforeEach(()=>{vi.mocked(listGrammars).mockResolvedValue([]);});

    it('shows modular navigation and preserves a direct Home route', () => {
        const navigate = vi.fn();
        render(<HydraApplicationShell activeModule="EVENTS" metamodels={[]} selectedMetamodelId="" onMetamodelChange={() => undefined} onNavigate={navigate} onOpenSettings={() => undefined}><p>Event workspace</p></HydraApplicationShell>);
        expect(screen.getByRole('navigation', { name: 'Primary application commands' })).toBeInTheDocument();
        expect(screen.getByRole('complementary', { name: 'HYDRA modules' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Event architecture' })).toBeInTheDocument();
        expect(screen.getAllByText('Define event contracts, payloads, bindings and their visual architecture.')).toHaveLength(2);
        expect(screen.getByRole('button', { name: '6. Events' })).toHaveAttribute('title', 'Define event contracts, payloads, bindings and their visual architecture.');
        expect(screen.getByRole('button', { name: '5. Quick fixes' })).toHaveAttribute('title', 'Define safe, declarative and reversible repairs that users can apply to validation problems.');
        fireEvent.click(screen.getByRole('button', { name: 'Home' }));
        expect(navigate).toHaveBeenCalledWith('HOME');
    });

    it('does not force a metamodel selector on independent operational modules', () => {
        render(<HydraApplicationShell activeModule="COMPATIBILITY" metamodels={[]} selectedMetamodelId="" onMetamodelChange={() => undefined} onNavigate={() => undefined} onOpenSettings={() => undefined}><span data-testid="operational-content">Operational content</span></HydraApplicationShell>);
        expect(screen.queryByRole('combobox', { name: 'Active metamodel' })).not.toBeInTheDocument();
        expect(screen.getByTestId('operational-content')).toHaveTextContent('Operational content');
    });

    it('offers grammar-first contexts in portable semantic-layer modules', async () => {
        const change=vi.fn();
        vi.mocked(listGrammars).mockResolvedValue([{id:'orders.dsl',name:'Orders',technology:'LANGIUM',documentCount:1,toolchainProfileId:'langium-default',etag:'g1'}]);
        render(<HydraApplicationShell activeModule="VALIDATIONS" metamodels={[{id:'people',fileName:'People.ecore',rootPackageCount:1}]} selectedMetamodelId="people" onMetamodelChange={change} onNavigate={() => undefined} onOpenSettings={() => undefined}><span>Validation workspace</span></HydraApplicationShell>);
        const selector=screen.getByRole('combobox',{name:'Active semantic context'});
        expect(await screen.findByRole('option',{name:/Orders.*orders\.dsl/})).toBeInTheDocument();
        fireEvent.change(selector,{target:{value:'GRAMMAR:orders.dsl'}});
        expect(change).toHaveBeenCalledWith('GRAMMAR:orders.dsl');
    });

    it('keeps metamodel-only modules restricted to real metamodel contexts', async () => {
        vi.mocked(listGrammars).mockResolvedValue([{id:'orders.dsl',name:'Orders',technology:'LANGIUM',documentCount:1,toolchainProfileId:'langium-default',etag:'g1'}]);
        render(<HydraApplicationShell activeModule="MODEL" metamodels={[{id:'people',fileName:'People.ecore',rootPackageCount:1}]} selectedMetamodelId="people" onMetamodelChange={() => undefined} onNavigate={() => undefined} onOpenSettings={() => undefined}><span>Model workspace</span></HydraApplicationShell>);
        expect(screen.getByRole('combobox',{name:'Active metamodel'})).toBeInTheDocument();
        expect(screen.queryByRole('option',{name:/Orders/})).not.toBeInTheDocument();
    });
});
