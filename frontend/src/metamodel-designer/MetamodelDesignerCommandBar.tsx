import { HydraButton, HydraSelect } from '../design-system';
import { DesignerCommandBar, DesignerCommandGroup } from '../designer-ui/DesignerCommandBar';
import { useHydraLanguage } from '../i18n';
import type { HydraCreationMode } from '../design-system';
import type { CanonicalMetamodelSummary } from './types';
import type { MetamodelLayoutDirection } from './layoutTypes';
import { MetamodelLayoutToolbar } from './MetamodelLayoutToolbar';

export type MetamodelDesignerMode = 'STRUCTURE' | 'VALIDATIONS' | 'EVENTS' | 'GAMIFICATION' | 'EDITORS' | 'INTEGRATIONS';

interface Props {
    metamodels: CanonicalMetamodelSummary[];
    selectedMetamodelId: string;
    loading: boolean;
    exportDisabled: boolean;
    saveDisabled: boolean;
    persistedDraftId: string;
    designerMode: MetamodelDesignerMode;
    structureCreationMode: HydraCreationMode;
    layoutDirection: MetamodelLayoutDirection;
    focusSelected: boolean;
    hideDependencies: boolean;
    sectionLocked: boolean;
    canUndo: boolean;
    canRedo: boolean;
    onSelectMetamodel: (id: string) => void;
    onOpenLibrary: () => void;
    onOpenSelected: () => void | Promise<void>;
    onCreateNew: () => void;
    onImport: () => void;
    onExport: () => void | Promise<void>;
    onFullLayout: () => void | Promise<void>;
    onElkLayout: () => void;
    onDirectionChange: (direction: MetamodelLayoutDirection) => void;
    onFocusSelectedChange: (value: boolean) => void;
    onHideDependenciesChange: (value: boolean) => void;
    onRoute: () => void | Promise<void>;
    onUndo: () => void;
    onRedo: () => void;
    onSave: () => void | Promise<unknown>;
    onDelete: () => void | Promise<void>;
    onModeChange: (mode: MetamodelDesignerMode) => void;
}

export function MetamodelDesignerCommandBar(props: Props) {
    const { t } = useHydraLanguage();
    return <DesignerCommandBar
        className="metamodel-designer-toolbar hydra-unified-workbench-toolbar"
        title="Visual metamodel designer"
        description="Edit the technology-neutral MetaModel and generate configured projections."
        context={<div className="metamodel-saved-context"><HydraSelect className="hydra-command-context-select metamodel-context-select" ariaLabel="Existing metamodel" showFilter={false} value={props.selectedMetamodelId} placeholder="Select metamodel" options={props.metamodels.map(item => ({ value: item.id, label: item.name || item.id, description: 'Saved metamodel', category: 'Saved metamodels' }))} onChange={props.onSelectMetamodel} /><HydraButton size="small" onClick={props.onOpenLibrary}>{t('Saved metamodels')}</HydraButton></div>}
        actions={<>
            <DesignerCommandGroup label="Metamodel">
                <HydraButton disabled={props.loading} size="small" onClick={() => void props.onOpenSelected()}>Open</HydraButton>
                <HydraButton disabled={props.loading} size="small" onClick={props.onCreateNew}>New</HydraButton>
            </DesignerCommandGroup>
            <DesignerCommandGroup label="Interchange">
                <HydraButton className="metamodel-import-button" disabled={props.loading} size="small" onClick={props.onImport}>Import</HydraButton>
                <HydraButton className="metamodel-export-button" disabled={props.exportDisabled} size="small" onClick={() => void props.onExport()}>Export</HydraButton>
            </DesignerCommandGroup>
            {props.designerMode === 'STRUCTURE' && props.structureCreationMode === 'ADVANCED' && <DesignerCommandGroup label="Layout">
                <MetamodelLayoutToolbar
                    direction={props.layoutDirection}
                    disabled={props.loading}
                    focusSelected={props.focusSelected}
                    hideDependencies={props.hideDependencies}
                    onApply={() => void props.onFullLayout()}
                    onElkApply={props.onElkLayout}
                    onDirectionChange={props.onDirectionChange}
                    onFocusSelectedChange={props.onFocusSelectedChange}
                    onHideDependenciesChange={props.onHideDependenciesChange}
                    onRoute={() => void props.onRoute()}
                />
            </DesignerCommandGroup>}
            <DesignerCommandGroup label="Changes">
                <HydraButton disabled={props.loading || !props.canUndo} size="small" onClick={props.onUndo}>{t('Undo')}</HydraButton>
                <HydraButton disabled={props.loading || !props.canRedo} size="small" onClick={props.onRedo}>{t('Redo')}</HydraButton>
                <HydraButton className="metamodel-save-button" disabled={props.saveDisabled} size="small" variant="primary" onClick={() => void props.onSave()}>Save metamodel</HydraButton>
                <HydraButton className="metamodel-delete-button" disabled={props.loading || props.persistedDraftId === ''} size="small" variant="danger" onClick={() => void props.onDelete()}>Delete</HydraButton>
            </DesignerCommandGroup>
        </>}
        viewsLabel="Workspace"
        viewsSummary={props.designerMode}
        views={!props.sectionLocked ? <div className="metamodel-designer-mode-tabs" aria-label="Metamodel design sections">
            <HydraButton className={props.designerMode === 'STRUCTURE' ? 'active' : ''} size="small" variant={props.designerMode === 'STRUCTURE' ? 'primary' : 'quiet'} onClick={() => props.onModeChange('STRUCTURE')}>Structure</HydraButton>
            <HydraButton className={props.designerMode === 'VALIDATIONS' ? 'active' : ''} size="small" variant={props.designerMode === 'VALIDATIONS' ? 'primary' : 'quiet'} onClick={() => props.onModeChange('VALIDATIONS')}>Validations</HydraButton>
            <HydraButton className={props.designerMode === 'EVENTS' ? 'active' : ''} size="small" variant={props.designerMode === 'EVENTS' ? 'primary' : 'quiet'} onClick={() => props.onModeChange('EVENTS')}>Events</HydraButton>
            <HydraButton className={props.designerMode === 'GAMIFICATION' ? 'active' : ''} size="small" variant={props.designerMode === 'GAMIFICATION' ? 'primary' : 'quiet'} onClick={() => props.onModeChange('GAMIFICATION')}>Gamification</HydraButton>
            <HydraButton className={props.designerMode === 'EDITORS' ? 'active' : ''} size="small" variant={props.designerMode === 'EDITORS' ? 'primary' : 'quiet'} onClick={() => props.onModeChange('EDITORS')}>Model editors</HydraButton>
            <HydraButton className={props.designerMode === 'INTEGRATIONS' ? 'active' : ''} size="small" variant={props.designerMode === 'INTEGRATIONS' ? 'primary' : 'quiet'} onClick={() => props.onModeChange('INTEGRATIONS')}>Integrations</HydraButton>
        </div> : undefined}
    />;
}
