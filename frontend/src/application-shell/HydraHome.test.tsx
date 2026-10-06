import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HydraHome } from './HydraHome';
import * as grammarApi from '../grammar-designer/api';
import * as projectApi from '../project-explorer/projectApi';

vi.mock('../grammar-designer/api', () => ({ listGrammars: vi.fn() }));
vi.mock('../project-explorer/projectApi', () => ({
    getProjectSession: vi.fn(),
    listRecentProjects: vi.fn(),
    getProjectCreationCatalog: vi.fn(),
}));
vi.mock('../capability-center/CapabilityReadinessView', () => ({ CapabilityReadinessView: ({ contextId, contextType }: { contextId: string; contextType: string }) => <div>{contextType}:{contextId}</div> }));

describe('HYDRA Home', () => {
    beforeEach(() => {
        vi.mocked(grammarApi.listGrammars).mockResolvedValue([]);
        vi.mocked(projectApi.getProjectSession).mockResolvedValue({ active:false, project:null, directory:'', openedAt:null, restoredTabs:[] });
        vi.mocked(projectApi.listRecentProjects).mockResolvedValue([]);
        vi.mocked(projectApi.getProjectCreationCatalog).mockResolvedValue({
            presets:[
                { id:'GRAPHICAL_LANGUAGE', label:'Graphical language', description:'', capabilities:['METAMODEL_DESIGN','VISUAL_EDITOR'] },
                { id:'TEXTUAL_DSL', label:'Textual language', description:'', capabilities:['GRAMMAR_DESIGN','TEXTUAL_EDITOR'] },
                { id:'MODELING_VALIDATION', label:'Models and quality rules', description:'', capabilities:['METAMODEL_DESIGN','MODEL_EDITING','VALIDATIONS'] },
                { id:'M2M_TRANSFORMATION', label:'Model transformation', description:'', capabilities:['TRANSFORMATION_M2M'] },
                { id:'COMPLETE_EDITOR', label:'Complete modeling editor', description:'', capabilities:['EDITOR_DEFINITIONS'] },
                { id:'COMPLETE_MDE', label:'Complete modeling project', description:'', capabilities:['METAMODEL_DESIGN','GRAMMAR_DESIGN'] },
            ], capabilities:[]
        });
    });

    it('keeps the card dashboard and exposes high-level project actions', async () => {
        render(<HydraHome metamodels={[]} selectedMetamodelId="" onMetamodelChange={() => undefined} onNavigate={vi.fn()} />);
        expect(screen.getByRole('heading', { name: 'Start or continue a HYDRA project' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'New project' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Open project' })).toBeInTheDocument();
        expect(await screen.findByRole('button', { name: /Textual language/i })).toBeInTheDocument();
        expect(screen.queryByText(/toolchain/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/artifact/i)).not.toBeInTheDocument();
    });

    it('shows only high-level areas enabled by the active project', async () => {
        vi.mocked(projectApi.getProjectSession).mockResolvedValue({
            active:true,
            directory:'C:\\Projects\\Library',
            openedAt:'2026-09-26T09:00:00Z',
            restoredTabs:[],
            project:{
                schemaVersion:'1.1', id:'library', name:'Library', description:'', createdAt:'2026-09-26T09:00:00Z', updatedAt:'2026-09-26T09:00:00Z', storageMode:'LOCAL_DIRECTORY',
                enabledCapabilities:['GRAMMAR_DESIGN','TEXTUAL_MODELING','VALIDATIONS','QUICK_FIXES'], createdFromPreset:'TEXTUAL_DSL', experienceMode:'GUIDED',
                primaryArtifact:{ kind:'GRAMMAR', id:'library', relativePath:'grammars/library.xtext' }
            }
        });
        render(<HydraHome metamodels={[]} selectedMetamodelId="" onMetamodelChange={() => undefined} onNavigate={vi.fn()} />);
        expect(await screen.findByRole('heading', { name: 'Library' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Design the language/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Create and edit models/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Check and correct/i })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Save, connect and share/i })).not.toBeInTheDocument();
        expect(screen.getByText('library.xtext')).toBeInTheDocument();
    });

    it('combines metamodel and grammar contexts with explicit name, id and type', async () => {
        vi.mocked(grammarApi.listGrammars).mockResolvedValue([{ id: 'orders.dsl', name: 'Orders language', technology: 'LANGIUM', documentCount: 1, toolchainProfileId: 'langium-4.3-browser', etag: 'e1' }]);
        const change = vi.fn();
        const metamodels=[{ id: 'people', fileName: 'People.ecore', rootPackageCount: 1 }];
        const {rerender}=render(<HydraHome metamodels={metamodels} selectedMetamodelId="people" onMetamodelChange={change} onNavigate={() => undefined} />);
        const select = screen.getByRole('combobox', { name: 'Home semantic context' });
        expect(select).toHaveValue('METAMODEL:people');
        expect(await screen.findByRole('option', { name: 'Orders language · orders.dsl · Grammar' })).toBeInTheDocument();
        expect(screen.getByRole('option', { name: 'People · people · Metamodel' })).toBeInTheDocument();
        fireEvent.change(select, { target: { value: 'GRAMMAR:orders.dsl' } });
        expect(change).toHaveBeenCalledWith('GRAMMAR:orders.dsl');
        rerender(<HydraHome metamodels={metamodels} selectedMetamodelId="GRAMMAR:orders.dsl" onMetamodelChange={change} onNavigate={() => undefined} />);
        expect(screen.getByRole('combobox', { name: 'Home semantic context' })).toHaveValue('GRAMMAR:orders.dsl');
        expect(await screen.findByText('GRAMMAR:orders.dsl')).toBeInTheDocument();
    });
});
