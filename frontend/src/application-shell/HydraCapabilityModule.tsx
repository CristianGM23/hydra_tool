import { lazy } from 'react';
import { HydraErrorBoundary } from '../design-system';
import type { HydraModuleId } from './types';

const LazyGrammarDesigner = lazy(() => import('../grammar-designer/GrammarDesigner').then(module => ({ default: module.GrammarDesigner })));
const LazyTextualModelEditor = lazy(() => import('../textual-model-editor/TextualModelEditor'));
const LazyMetamodelDesigner = lazy(() => import('../metamodel-designer/MetamodelDesigner').then(module => ({ default: module.MetamodelDesigner })));
const LazyTransformationDesigner = lazy(() => import('../transformation-designer/TransformationDesigner').then(module => ({ default: module.TransformationDesigner })));
const LazyIndependentCapabilityWorkbench = lazy(() => import('./IndependentCapabilityWorkbench').then(module => ({ default: module.IndependentCapabilityWorkbench })));
const LazyCapabilityReadinessView = lazy(() => import('../capability-center/CapabilityReadinessView').then(module => ({ default: module.CapabilityReadinessView })));
const LazyNativeCompatibilityCenter = lazy(() => import('../compatibility-center/NativeCompatibilityCenter').then(module => ({ default: module.NativeCompatibilityCenter })));
const LazyRoundTripCenter = lazy(() => import('../roundtrip-center/RoundTripCenter').then(module => ({ default: module.RoundTripCenter })));
const LazyStandaloneDistributionCenter = lazy(() => import('../distribution-center/StandaloneDistributionCenter').then(module => ({ default: module.StandaloneDistributionCenter })));
const LazyGeneratedProjectCenter = lazy(() => import('../generated-project-center/GeneratedProjectCenter').then(module => ({ default: module.GeneratedProjectCenter })));
const LazyArtifactRegistryCenter = lazy(() => import('../artifact-registry-center/ArtifactRegistryCenter').then(module => ({ default: module.ArtifactRegistryCenter })));
const LazyRepositoryConnectionsCenter = lazy(() => import('../repository-connections/RepositoryConnectionsCenter').then(module => ({ default: module.RepositoryConnectionsCenter })));
const LazyRepresentableImportCenter = lazy(() => import('../representable-import-center/RepresentableImportCenter').then(module => ({ default: module.RepresentableImportCenter })));
const LazyPublicationCenter = lazy(() => import('../publication-center/PublicationCenter').then(module => ({ default: module.PublicationCenter })));
const LazyUpdateSiteImportCenter = lazy(() => import('../update-site-import-center/UpdateSiteImportCenter').then(module => ({ default: module.UpdateSiteImportCenter })));

interface HydraCapabilityModuleProps {
    activeModule: HydraModuleId;
    metamodelId: string;
    onMetamodelSaved: (metamodelId: string) => void;
    onUpdateSiteImported: (metamodelId: string) => void;
    onNavigate: (module: HydraModuleId) => void;
    onError: (message: string) => void;
}

/** Resuelve capacidades lazy independientes para mantener App como orquestador y no como registro de módulos. */
export function HydraCapabilityModule({ activeModule, metamodelId, onMetamodelSaved, onUpdateSiteImported, onNavigate, onError }: HydraCapabilityModuleProps) {
    if (activeModule === 'GRAMMARS') return <HydraErrorBoundary scope="Grammar designer"><LazyGrammarDesigner /></HydraErrorBoundary>;
    if (activeModule === 'TEXTUAL_MODELS') return <HydraErrorBoundary scope="Textual model editor"><LazyTextualModelEditor /></HydraErrorBoundary>;
    if (activeModule === 'TRANSFORMATIONS') return <HydraErrorBoundary scope="Transformation designer"><LazyTransformationDesigner /></HydraErrorBoundary>;
    if (activeModule === 'METAMODEL') return <HydraErrorBoundary scope="Metamodel workbench" resetKey={metamodelId} onError={exception => onError(`Metamodel workbench interface error: ${exception.message}`)}><LazyMetamodelDesigner initialSection="STRUCTURE" sectionLocked onSaved={onMetamodelSaved} /></HydraErrorBoundary>;
    if (['VALIDATIONS', 'QUICK_FIXES', 'EVENTS', 'EVENT_FLOWS', 'GAMIFICATION', 'EDITORS', 'INTEGRATIONS'].includes(activeModule)) return <LazyIndependentCapabilityWorkbench key={activeModule} metamodelId={metamodelId} moduleId={activeModule} />;
    if (activeModule === 'CAPABILITIES') return metamodelId.startsWith('GRAMMAR:')
        ? <LazyCapabilityReadinessView contextId={metamodelId.slice('GRAMMAR:'.length)} contextType="GRAMMAR" onNavigate={onNavigate} />
        : <LazyCapabilityReadinessView contextId={metamodelId} contextType="METAMODEL" onNavigate={onNavigate} />;
    if (activeModule === 'COMPATIBILITY') return <LazyNativeCompatibilityCenter />;
    if (activeModule === 'ROUND_TRIP') return <LazyRoundTripCenter metamodelId={metamodelId} />;
    if (activeModule === 'DISTRIBUTION') return <LazyStandaloneDistributionCenter />;
    if (activeModule === 'GENERATED_PROJECTS') return <LazyGeneratedProjectCenter />;
    if (activeModule === 'ARTIFACTS') return <LazyArtifactRegistryCenter />;
    if (activeModule === 'REPOSITORIES') return <LazyRepositoryConnectionsCenter />;
    if (activeModule === 'REPRESENTABLE_IMPORTS') return <LazyRepresentableImportCenter metamodelId={metamodelId} />;
    if (activeModule === 'UPDATE_SITE_IMPORTS') return <LazyUpdateSiteImportCenter onImported={onUpdateSiteImported} />;
    return <LazyPublicationCenter />;
}
