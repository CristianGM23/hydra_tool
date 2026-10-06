import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { createEmptyEventModels } from '../event-designer/eventDesignerFactory';
import { loadEventModels, loadReservedEventTypes } from '../event-designer/api';
import type { EventTypeDefinition } from '../event-designer/types';
import { isMissingValidationResource } from '../validation/http';
import { HydraActionableErrorNotice, HydraErrorBoundary } from '../design-system';
import { loadMetaModel } from '../metamodel-designer/api';
import { loadGrammar, projectGrammarClassDiagram } from '../grammar-designer/api';
import { loadCapabilityReadiness } from '../capability-center/api';
import type { MetaModel } from '../metamodel-designer/types';
import type { HydraModuleId } from './types';
import { useHydraLanguage } from '../i18n';

interface Props { moduleId: HydraModuleId; metamodelId: string; }

const LazyValidationDesigner = lazy(() => import('../validation-designer/ValidationDesigner')
    .then(module => ({ default: module.ValidationDesigner })));
const LazyEventDesigner = lazy(() => import('../event-designer/EventDesigner')
    .then(module => ({ default: module.EventDesigner })));
const LazyGamificationDesigner = lazy(() => import('../gamification-designer/GamificationDesigner')
    .then(module => ({ default: module.GamificationDesigner })));
const LazyEventFlowDesigner = lazy(() => import('../event-designer/flow/EventFlowDesigner')
    .then(module => ({ default: module.EventFlowDesigner })));
const LazyEditorDefinitionDesigner = lazy(() => import('../editor-definition/EditorDefinitionDesigner')
    .then(module => ({ default: module.EditorDefinitionDesigner })));
const LazyIntegrationDesigner = lazy(() => import('../integration-designer/IntegrationDesigner')
    .then(module => ({ default: module.IntegrationDesigner })));


export function IndependentCapabilityWorkbench(props: Props) {
    return <IndependentCapabilityWorkbenchSession key={`${props.moduleId}:${props.metamodelId}`} {...props} />;
}

function IndependentCapabilityWorkbenchSession({ moduleId, metamodelId }: Props) {
    const { t } = useHydraLanguage();
    const [metamodel, setMetamodel] = useState<MetaModel | null>(null);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [loading, setLoading] = useState(metamodelId !== '');

    useEffect(() => {
        let active = true;
        if (metamodelId === '') return () => { active = false; };
        const grammarId = metamodelId.startsWith('GRAMMAR:') ? metamodelId.slice('GRAMMAR:'.length) : '';
        const request = grammarId === ''
            ? loadMetaModel(metamodelId).then(result => result.metamodel)
            : Promise.all([loadGrammar(grammarId), loadCapabilityReadiness('GRAMMAR', grammarId)]).then(async ([loaded, readiness]) => {
                const projection = await projectGrammarClassDiagram(loaded.grammar, 0);
                return { ...projection.inferredMetamodel, id: readiness.semanticContextId, name: loaded.grammar.name };
            });
        void request.then(result => {
            if (!active) return;
            setMetamodel(result); setError('');
        }).catch(exception => {
            if (!active) return;
            setError(exception instanceof Error ? exception.message : 'Could not load the selected semantic context');
        }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [metamodelId]);

    const moduleLabel = independentModuleLabel(moduleId);
    if (metamodelId === '') return <section className="hydra-empty-state"><h2>{t('Select a semantic context')}</h2><p>{t('Choose a metamodel or grammar before opening this capability.')}</p></section>;
    if (error !== '' && metamodel === null) return <section className="hydra-empty-state error"><h2>{t('Capability unavailable')}</h2><HydraActionableErrorNotice message={error} context={moduleErrorContext(moduleId)} /></section>;
    if (loading || metamodel === null) return <section className="hydra-empty-state"><h2>{t('Loading semantic context…')}</h2><p>{metamodelId}</p></section>;

    const callbacks = { onError: (value: string) => { setMessage(''); setError(value); }, onMessage: (value: string) => { setError(''); setMessage(value); } };
    let content = null;
    if (moduleId === 'VALIDATIONS') content = <LazyValidationDesigner initialWorkspace="RULES" metamodel={metamodel} persisted {...callbacks} />;
    if (moduleId === 'QUICK_FIXES') content = <LazyValidationDesigner initialWorkspace="QUICK_FIXES" metamodel={metamodel} persisted {...callbacks} />;
    if (moduleId === 'EVENTS') content = <LazyEventDesigner initialView="FORM" metamodel={metamodel} metamodelId={metamodel.id} persisted {...callbacks} />;
    if (moduleId === 'EVENT_FLOWS') content = <IndependentEventFlowWorkbench metamodel={metamodel} {...callbacks} />;
    if (moduleId === 'GAMIFICATION') content = <LazyGamificationDesigner metamodel={metamodel} metamodelId={metamodel.id} persisted {...callbacks} />;
    if (moduleId === 'EDITORS') content = <LazyEditorDefinitionDesigner metamodel={metamodel} persisted {...callbacks} />;
    if (moduleId === 'INTEGRATIONS') content = <LazyIntegrationDesigner metamodelId={metamodel.id} persisted {...callbacks} />;

    return <section className="hydra-independent-workbench">
        {error !== '' && <HydraActionableErrorNotice message={error} context={moduleErrorContext(moduleId)} onDismiss={() => setError('')} />}
        {message !== '' && error === '' && <div className="status-message">{message}</div>}
        <HydraErrorBoundary scope={t(moduleLabel)} resetKey={`${moduleId}:${metamodel.id}`} onError={exception => setError(exception.message)}>
            <Suspense fallback={<section className="hydra-empty-state"><h2>{t('Loading capability…')}</h2><p>{t(moduleLabel)} · {t('Only this designer chunk is being loaded.')}</p></section>}>
                {content}
            </Suspense>
        </HydraErrorBoundary>
    </section>;
}

function independentModuleLabel(moduleId: HydraModuleId): string {
    const labels: Readonly<Partial<Record<HydraModuleId, string>>> = { VALIDATIONS: 'Validations', QUICK_FIXES: 'Quick fixes', EVENTS: 'Events', EVENT_FLOWS: 'Event flows', GAMIFICATION: 'Gamification', EDITORS: 'Editors', INTEGRATIONS: 'Integrations' };
    return labels[moduleId] ?? moduleId;
}

function IndependentEventFlowWorkbench({ metamodel, onError, onMessage }: { metamodel: MetaModel; onError: (message: string) => void; onMessage: (message: string) => void }) {
    const fallback = createEmptyEventModels(metamodel.id);
    const [eventDefinitionModelId, setEventDefinitionModelId] = useState(fallback.definitions.id);
    const [customEventTypes, setCustomEventTypes] = useState<EventTypeDefinition[]>([]);
    const [reservedEventTypes, setReservedEventTypes] = useState<string[]>([]);
    const onErrorRef = useRef(onError);
    useEffect(() => { onErrorRef.current = onError; }, [onError]);

    useEffect(() => {
        let active = true;
        void Promise.allSettled([loadEventModels(metamodel.id), loadReservedEventTypes()]).then(results => {
            if (!active) return;
            const events = results[0];
            if (events.status === 'fulfilled') {
                setEventDefinitionModelId(events.value.definitions.id);
                setCustomEventTypes(events.value.definitions.eventTypes);
            } else if (!isMissingValidationResource(events.reason)) {
                onErrorRef.current(events.reason instanceof Error ? `Could not load event catalog for EventFlow: ${events.reason.message}` : 'Could not load event catalog for EventFlow');
            }
            const reserved = results[1];
            if (reserved.status === 'fulfilled') setReservedEventTypes(reserved.value);
        });
        return () => { active = false; };
    }, [metamodel.id]);

    const eventTypeIds = Array.from(new Set(['core.model.element-created', ...reservedEventTypes, ...customEventTypes.map(type => type.id)]));
    return <LazyEventFlowDesigner metamodel={metamodel} metamodelId={metamodel.id} eventDefinitionModelId={eventDefinitionModelId}
        eventTypeIds={eventTypeIds} customEventTypes={customEventTypes} persisted onError={onError} onMessage={onMessage} />;
}


function moduleErrorContext(moduleId: HydraModuleId): string {
    const values: Partial<Record<HydraModuleId,string>> = { VALIDATIONS:'validation designer', QUICK_FIXES:'quick-fix designer', EVENTS:'event designer', EVENT_FLOWS:'event flow designer', GAMIFICATION:'gamification designer', EDITORS:'editor definition', INTEGRATIONS:'integration designer', MODEL:'model editor' };
    return values[moduleId] ?? 'the current form';
}
