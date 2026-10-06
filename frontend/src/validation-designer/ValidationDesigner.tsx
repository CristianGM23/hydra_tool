import { HydraButton } from '../design-system/HydraButton';
import { explainInputError } from '../api/inputErrorExplanation';
import { useEffect, useMemo, useRef, useState } from 'react';
import { DesignerCommandBar, DesignerCommandGroup } from '../designer-ui/DesignerCommandBar';
import { HydraDiagnosticList } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { MetaModel } from '../metamodel-designer/types';
import { checkActionModel, createActionModel, loadActionModel, replaceActionModel } from '../validation/actionApi';
import { checkExpressionModel, createExpressionModel, loadExpressionModel, replaceExpressionModel } from '../validation/api';
import { isMissingValidationResource } from '../validation/http';
import { checkOperationBehaviorModel, createOperationBehaviorModel, loadOperationBehaviorModel, replaceOperationBehaviorModel } from '../validation/operationBehaviorApi';
import type {
    ActionModel,
    ActionModelCheckReport,
    ExpressionDefinition,
    ExpressionModel,
    ExpressionModelCheckReport,
    OperationBehaviorModel,
    OperationBehaviorSaveResponse,
    ValidationModel,
    ValidationModelCheckReport,
    ValidationRule
} from '../validation/types';
import { checkValidationModel, createValidationModel, loadValidationModel, replaceValidationModel } from '../validation/validationApi';
import { LayerInterchangeDialog } from '../layer-io';
import type { LayerImportRequest } from '../layer-io';
import { analyzeQuickFixImport, exportQuickFixLayer, importQuickFixLayer, QUICK_FIX_INTERCHANGE_FORMATS, type QuickFixInterchangeFormat } from '../layer-io/quickFixInterchange';
import { analyzeValidationImport, exportValidationLayer, importValidationLayer, VALIDATION_INTERCHANGE_FORMATS, type ValidationInterchangeFormat } from '../layer-io/validationInterchange';
import { OperationBindingDesigner } from './OperationBindingDesigner';
import { QuickFixDesigner } from './QuickFixDesigner';
import { ValidationRuleEditor } from './ValidationRuleEditor';
import { ValidationRuleManager } from './ValidationRuleManager';
import { ValidationStructureTree } from './ValidationStructureTree';
import type { ValidationStructureSelection } from './ValidationStructureTree';
import {
    cloneExpressionWithFreshIds,
    createDefaultExpression,
    createDefaultRuleSet,
    createDefaultValidationRule,
    createEmptyActionModel,
    createEmptyExpressionModel,
    createEmptyValidationModel,
    createUniqueRuleCode,
    createValidationDesignerId
} from './validationDesignerFactory';
import { inferExpressionDependencies, totalDiagnosticCount } from './validationDesignerSelectors';
import { verifiedRuleIdsFromSignatures, verifiedRuleSignaturesFromReports } from './validationVerification';
import { findValidationDesignerBlockingError } from './validationDesignerValidation';
import './validation-designer.css';
import { useHydraOverwriteConfirmation } from '../hooks/useHydraOverwriteConfirmation';
import { validationOverwriteIdentities } from './validationOverwriteIdentity';
import type { HydraPersistedIdentity } from '../hooks/hydraOverwritePolicy';

interface ValidationDesignerProps {
    metamodel: MetaModel;
    initialWorkspace?: 'RULES' | 'QUICK_FIXES';
    persisted: boolean;
    onError: (message: string) => void;
    onMessage: (message: string) => void;
}

type ValidationWorkspace = 'RULES' | 'QUICK_FIXES' | 'OPERATIONS' | 'STRUCTURE' | 'DIAGNOSTICS';

export function ValidationDesigner(props: ValidationDesignerProps) {
    return <ValidationDesignerSession key={`${props.metamodel.id}:${props.persisted}`} {...props} />;
}

function ValidationDesignerSession({ metamodel, initialWorkspace = 'RULES', persisted, onError, onMessage }: ValidationDesignerProps) {
    const { t, language } = useHydraLanguage();
    const defaults = useMemo(() => createDefaults(metamodel.id), [metamodel.id]);
    const [expressionModel, setExpressionModel] = useState<ExpressionModel>(defaults.expressionModel);
    const [actionModel, setActionModel] = useState<ActionModel>(defaults.actionModel);
    const [validationModel, setValidationModel] = useState<ValidationModel>(defaults.validationModel);
    const [behaviorModel, setBehaviorModel] = useState<OperationBehaviorModel>(defaults.behaviorModel);
    const [expressionEtag, setExpressionEtag] = useState('');
    const [actionEtag, setActionEtag] = useState('');
    const [validationEtag, setValidationEtag] = useState('');
    const [behaviorEtag, setBehaviorEtag] = useState('');
    const [expressionReport, setExpressionReport] = useState<ExpressionModelCheckReport | null>(null);
    const [actionReport, setActionReport] = useState<ActionModelCheckReport | null>(null);
    const [validationReport, setValidationReport] = useState<ValidationModelCheckReport | null>(null);
    const [behaviorReport, setBehaviorReport] = useState<OperationBehaviorSaveResponse['validation'] | null>(null);
    const [verifiedRuleSignatures, setVerifiedRuleSignatures] = useState<Readonly<Record<string, string>>>({});
    const [selectedRuleId, setSelectedRuleId] = useState('');
    const [selectedActionId, setSelectedActionId] = useState('');
    const [workspace, setWorkspace] = useState<ValidationWorkspace>(initialWorkspace);
    const [layerIo, setLayerIo] = useState<{ layer: 'VALIDATIONS' | 'QUICK_FIXES'; mode: 'IMPORT' | 'EXPORT' } | null>(null);
    const [structureTabOpen, setStructureTabOpen] = useState(true);
    const [structureCollapsed, setStructureCollapsed] = useState(false);
    const [structurePropertiesOpen, setStructurePropertiesOpen] = useState(true);
    const [structurePropertiesCollapsed, setStructurePropertiesCollapsed] = useState(false);
    const [structureFilter, setStructureFilter] = useState('');
    const [structureSelection, setStructureSelection] = useState<ValidationStructureSelection>(null);
    const [loading, setLoading] = useState(persisted);
    const [expressionDirty, setExpressionDirty] = useState(false);
    const [actionDirty, setActionDirty] = useState(false);
    const [validationDirty, setValidationDirty] = useState(false);
    const [behaviorDirty, setBehaviorDirty] = useState(false);
    const [persistedValidationIdentities, setPersistedValidationIdentities] = useState<HydraPersistedIdentity[]>([]);
    const onErrorRef = useRef(onError);
    const onMessageRef = useRef(onMessage);
    useEffect(() => { onErrorRef.current = onError; onMessageRef.current = onMessage; }, [onError, onMessage]);
    const hasOperations = metamodel.classes.some(metaClass => metaClass.operations.length > 0);
    const currentValidationIdentities = useMemo(
        () => validationOverwriteIdentities(validationModel, expressionModel, actionModel, behaviorModel),
        [actionModel, behaviorModel, expressionModel, validationModel]
    );
    const { confirmOverwrite, overwriteDialogs } = useHydraOverwriteConfirmation({
        current: currentValidationIdentities,
        persisted: persistedValidationIdentities,
        includeSemanticSignature: true
    });

    useEffect(() => {
        let active = true;
        const empty = createDefaults(metamodel.id);
        if (!persisted) return () => { active = false; };

        void Promise.all([
            loadExpressionOrDefault(metamodel.id),
            loadActionOrDefault(metamodel.id, empty.expressionModel.id),
            loadValidationOrDefault(metamodel.id, empty.expressionModel.id, empty.actionModel.id),
            hasOperations ? loadBehaviorOrDefault(metamodel.id) : Promise.resolve({ model: empty.behaviorModel, etag: '' })
        ]).then(([expressions, actions, validations, behaviors]) => {
            if (!active) return;
            setExpressionModel(expressions.model);
            setActionModel({ ...actions.model, expressionModelId: expressions.model.id });
            setValidationModel({ ...validations.model, expressionModelId: expressions.model.id, actionModelId: actions.model.id });
            setBehaviorModel(behaviors.model);
            setExpressionEtag(expressions.etag);
            setActionEtag(actions.etag);
            setValidationEtag(validations.etag);
            setBehaviorEtag(behaviors.etag);
            setPersistedValidationIdentities(validationOverwriteIdentities(validations.model, expressions.model, actions.model, behaviors.model));
            setSelectedRuleId(validations.model.rules[0]?.id ?? '');
            setSelectedActionId(actions.model.actions[0]?.id ?? '');
            setVerifiedRuleSignatures({});
            onMessageRef.current('Validation definitions loaded');
        }).catch(error => {
            if (!active) return;
            onErrorRef.current(error instanceof Error ? error.message : 'Could not load validation definitions');
        }).finally(() => {
            if (active) setLoading(false);
        });
        return () => { active = false; };
    }, [hasOperations, metamodel.id, persisted]);

    const selectedRule = validationModel.rules.find(rule => rule.id === selectedRuleId) ?? null;
    const selectedExpression = expressionModel.expressions.find(expression => expression.id === selectedRule?.conditionExpressionId) ?? null;
    const designerBlockingError = useMemo(
        () => findValidationDesignerBlockingError(expressionModel, actionModel, validationModel),
        [actionModel, expressionModel, validationModel]
    );
    const verifiedRuleIds = useMemo(() => verifiedRuleIdsFromSignatures(
        validationModel, expressionModel, actionModel, behaviorModel, verifiedRuleSignatures
    ), [actionModel, behaviorModel, expressionModel, validationModel, verifiedRuleSignatures]);
    const baseDiagnosticCount = totalDiagnosticCount({ expression: expressionReport, action: actionReport, validation: validationReport });
    const diagnosticCount = addBehaviorDiagnostics(baseDiagnosticCount, behaviorReport);

    function updateExpressionModel(model: ExpressionModel): void { setExpressionModel(model); setExpressionDirty(true); }
    function updateActionModel(model: ActionModel): void {
        const remainingActionIds = new Set(model.actions.map(action => action.id));
        setActionModel(model);
        setValidationModel(current => ({
            ...current,
            rules: current.rules.map(rule => ({ ...rule, quickFixIds: rule.quickFixIds.filter(id => remainingActionIds.has(id)) }))
        }));
        setActionDirty(true);
        setValidationDirty(true);
    }
    function updateValidationModel(model: ValidationModel): void { setValidationModel(model); setValidationDirty(true); }
    function updateBehaviorModel(model: OperationBehaviorModel): void { setBehaviorModel(model); setBehaviorDirty(true); }

    function createRule(ruleSetId?: string): void {
        const contextClassId = metamodel.classes[0]?.id;
        const expression = createDefaultExpression(contextClassId);
        const baseRule = createDefaultValidationRule(metamodel, expression);
        const rule = {
            ...baseRule,
            name: t('New validation'),
            messageTemplate: t('The validation condition is not satisfied.'),
            code: createUniqueRuleCode('NEW_VALIDATION', validationModel.rules.map(item => item.code)),
            dependencies: inferExpressionDependencies(expression)
        };
        let ruleSets = validationModel.ruleSets;
        let targetRuleSetId = ruleSetId;
        if (ruleSets.length === 0) {
            const newRuleSet = createDefaultRuleSet(0);
            ruleSets = [newRuleSet];
            targetRuleSetId = newRuleSet.id;
        }
        ruleSets = ruleSets.map(item => item.id === targetRuleSetId ? { ...item, ruleIds: [...item.ruleIds, rule.id] } : item);
        updateExpressionModel({ ...expressionModel, expressions: [...expressionModel.expressions, expression] });
        updateValidationModel({ ...validationModel, rules: [...validationModel.rules, rule], ruleSets });
        setSelectedRuleId(rule.id);
        setWorkspace('RULES');
    }

    function duplicateRule(ruleId: string): void {
        const source = validationModel.rules.find(item => item.id === ruleId);
        if (source === undefined) return;
        const sourceExpression = expressionModel.expressions.find(item => item.id === source.conditionExpressionId);
        if (sourceExpression === undefined) return;
        const expression = cloneExpressionWithFreshIds(sourceExpression);
        const rule: ValidationRule = {
            ...structuredClone(source),
            id: createValidationDesignerId('rule'),
            name: `${source.name} copy`,
            code: createUniqueRuleCode(`${source.code}_COPY`, validationModel.rules.map(item => item.code)),
            conditionExpressionId: expression.id
        };
        const sourceRuleSet = validationModel.ruleSets.find(item => item.ruleIds.includes(source.id));
        const ruleSets = validationModel.ruleSets.map(item => item.id === sourceRuleSet?.id ? { ...item, ruleIds: [...item.ruleIds, rule.id] } : item);
        updateExpressionModel({ ...expressionModel, expressions: [...expressionModel.expressions, expression] });
        updateValidationModel({ ...validationModel, rules: [...validationModel.rules, rule], ruleSets });
        setSelectedRuleId(rule.id);
    }

    function changeRule(rule: ValidationRule): void {
        updateValidationModel({ ...validationModel, rules: validationModel.rules.map(item => item.id === rule.id ? rule : item) });
    }

    function changeExpression(expression: ExpressionDefinition): void {
        updateExpressionModel({ ...expressionModel, expressions: expressionModel.expressions.map(item => item.id === expression.id ? expression : item) });
        if (selectedRule !== null && selectedRule.conditionExpressionId === expression.id) {
            setValidationModel(current => ({
                ...current,
                rules: current.rules.map(rule => rule.id === selectedRule.id ? { ...rule, dependencies: inferExpressionDependencies(expression) } : rule)
            }));
            setValidationDirty(true);
        }
    }

    function deleteRule(ruleId: string): void {
        const rule = validationModel.rules.find(item => item.id === ruleId);
        if (rule === undefined) return;
        updateValidationModel({
            ...validationModel,
            rules: validationModel.rules.filter(item => item.id !== ruleId),
            ruleSets: validationModel.ruleSets.map(item => ({ ...item, ruleIds: item.ruleIds.filter(id => id !== ruleId) }))
        });
        updateExpressionModel({ ...expressionModel, expressions: expressionModel.expressions.filter(item => item.id !== rule.conditionExpressionId) });
        setSelectedRuleId(validationModel.rules.find(item => item.id !== ruleId)?.id ?? '');
    }

    function moveRuleToGroup(ruleId: string, ruleSetId: string): void {
        updateValidationModel({
            ...validationModel,
            ruleSets: validationModel.ruleSets.map(item => ({
                ...item,
                ruleIds: item.id === ruleSetId
                    ? [...item.ruleIds.filter(id => id !== ruleId), ruleId]
                    : item.ruleIds.filter(id => id !== ruleId)
            }))
        });
    }

    async function saveAll(): Promise<boolean> {
        if (!persisted) {
            onError('Save the metamodel before saving validations.');
            return false;
        }
        if (designerBlockingError !== '') {
            onError(validationBlockingActionableMessage(designerBlockingError, language));
            return false;
        }
        if (!await confirmOverwrite()) return false;
        setLoading(true);
        let nextExpressionModel = expressionModel;
        let nextActionModel: ActionModel;
        let nextValidationModel: ValidationModel;
        let nextBehaviorModel = behaviorModel;
        let nextExpressionReport = expressionReport;
        let nextActionReport = actionReport;
        let nextValidationReport = validationReport;
        let nextBehaviorReport = behaviorReport;
        let saveStage: ValidationSaveStage = 'EXPRESSIONS';
        try {
            if (expressionDirty || expressionEtag === '') {
                const result = expressionEtag === ''
                    ? await createExpressionModel(expressionModel)
                    : await replaceExpressionModel(expressionModel, expressionEtag);
                nextExpressionModel = result.expressionModel;
                nextExpressionReport = result.checkReport;
                setExpressionModel(result.expressionModel);
                setExpressionReport(result.checkReport);
                setExpressionEtag(result.etag);
                setExpressionDirty(false);
            }

            saveStage = 'QUICK_FIXES';
            const actionDocument = { ...actionModel, expressionModelId: nextExpressionModel.id };
            if (actionDirty || actionEtag === '') {
                const result = actionEtag === ''
                    ? await createActionModel(actionDocument)
                    : await replaceActionModel(actionDocument, actionEtag);
                nextActionModel = result.actionModel;
                nextActionReport = result.checkReport;
                setActionModel(result.actionModel);
                setActionReport(result.checkReport);
                setActionEtag(result.etag);
                setActionDirty(false);
            } else {
                nextActionModel = actionDocument;
            }

            saveStage = 'OPERATIONS';
            if (hasOperations && (behaviorDirty || behaviorEtag === '')) {
                const result = behaviorEtag === ''
                    ? await createOperationBehaviorModel(behaviorModel)
                    : await replaceOperationBehaviorModel(behaviorModel, behaviorEtag);
                nextBehaviorModel = result.behaviorModel;
                nextBehaviorReport = result.validation;
                setBehaviorModel(result.behaviorModel);
                setBehaviorReport(result.validation);
                setBehaviorEtag(result.etag);
                setBehaviorDirty(false);
            }

            saveStage = 'VALIDATIONS';
            const validationDocument = {
                ...validationModel,
                expressionModelId: nextExpressionModel.id,
                actionModelId: nextActionModel.id
            };
            if (validationDirty || validationEtag === '') {
                const result = validationEtag === ''
                    ? await createValidationModel(validationDocument)
                    : await replaceValidationModel(validationDocument, validationEtag);
                nextValidationModel = result.validationModel;
                nextValidationReport = result.checkReport;
                setValidationModel(result.validationModel);
                setValidationReport(result.checkReport);
                setValidationEtag(result.etag);
                setValidationDirty(false);
            } else {
                nextValidationModel = validationDocument;
            }

            setExpressionModel(nextExpressionModel);
            setActionModel(nextActionModel);
            setValidationModel(nextValidationModel);
            setBehaviorModel(nextBehaviorModel);
            setPersistedValidationIdentities(validationOverwriteIdentities(nextValidationModel, nextExpressionModel, nextActionModel, nextBehaviorModel));
            const counts = addBehaviorDiagnostics(totalDiagnosticCount({ expression: nextExpressionReport, action: nextActionReport, validation: nextValidationReport }), nextBehaviorReport);
            onMessage(`Validations saved. ${counts.errors} error(s), ${counts.warnings} warning(s).`);
            return true;
        } catch (error) {
            onError(validationSaveActionableMessage(error, saveStage, language));
            return false;
        } finally {
            setLoading(false);
        }
    }

    const dirty = expressionDirty || actionDirty || validationDirty || (hasOperations && behaviorDirty);
    const needsInitialSave = expressionEtag === '' || actionEtag === '' || validationEtag === '' || (hasOperations && behaviorEtag === '');

    async function checkDefinitions(): Promise<void> {
        if (!persisted) {
            onError('Save the metamodel before checking validations.');
            return;
        }
        if (expressionEtag === '' || actionEtag === '' || validationEtag === '' || (hasOperations && behaviorEtag === '') || dirty) {
            onError('Save the validation definitions before running the backend checks.');
            return;
        }
        setLoading(true);
        try {
            const [expressions, actions, validations, behaviors] = await Promise.all([
                checkExpressionModel(metamodel.id),
                checkActionModel(metamodel.id),
                checkValidationModel(metamodel.id),
                hasOperations ? checkOperationBehaviorModel(metamodel.id) : Promise.resolve(null)
            ]);
            setExpressionReport(expressions);
            setActionReport(actions);
            setValidationReport(validations);
            setBehaviorReport(behaviors);
            setVerifiedRuleSignatures(verifiedRuleSignaturesFromReports(validationModel, expressionModel, actionModel, behaviorModel, validations, expressions, actions, behaviors));
            const counts = addBehaviorDiagnostics(totalDiagnosticCount({ expression: expressions, action: actions, validation: validations }), behaviors);
            setWorkspace('DIAGNOSTICS');
            onMessage(`Definitions checked. ${counts.errors} error(s), ${counts.warnings} warning(s).`);
        } catch (error) {
            onError(error instanceof Error ? error.message : 'Could not check validation definitions');
        } finally {
            setLoading(false);
        }
    }


    function selectStructureItem(selection: Exclude<ValidationStructureSelection, null>): void {
        setStructureSelection(selection);
        if (selection.kind === 'RULE') setSelectedRuleId(selection.id);
        if (selection.kind === 'ACTION') setSelectedActionId(selection.id);
        if (selection.kind === 'RULE_SET') {
            const firstRuleId = validationModel.ruleSets.find(item => item.id === selection.id)?.ruleIds[0];
            if (firstRuleId !== undefined) setSelectedRuleId(firstRuleId);
        }
        setStructurePropertiesOpen(true);
        setStructurePropertiesCollapsed(false);
    }

    function structureProperties() {
        if (structureSelection?.kind === 'ACTION') return <QuickFixDesigner actionModel={actionModel} expressionModel={expressionModel} metamodel={metamodel} onChange={updateActionModel} onSelectAction={setSelectedActionId} onFinishGuided={() => saveAll()} selectedActionId={selectedActionId} />;
        if (structureSelection?.kind === 'OPERATION') return <OperationBindingDesigner actionModel={actionModel} behaviorModel={behaviorModel} expressionModel={expressionModel} metamodel={metamodel} onChange={updateBehaviorModel} />;
        if ((structureSelection?.kind === 'RULE' || structureSelection?.kind === 'RULE_SET') && selectedRule !== null) return <ValidationRuleEditor actionModel={actionModel} expression={selectedExpression} metamodel={metamodel} onChangeExpression={changeExpression} onChangeRule={changeRule} onDelete={deleteRule} onMoveToGroup={moveRuleToGroup} onFinishGuided={() => saveAll()} allDefinitionsReady={designerBlockingError === ''} rule={selectedRule} validationModel={validationModel} />;
        if (structureSelection?.kind === 'EXPRESSION') {
            const expression = expressionModel.expressions.find(item => item.id === structureSelection.id);
            return <section className="validation-structure-summary"><h3>{expression?.name || 'Expression'}</h3><p>This reusable expression is not owned by a validation rule. Its HYDRA ID is <code>{structureSelection.id}</code>.</p><HydraButton type="button" onClick={() => setWorkspace('RULES')}>Open rules workspace</HydraButton></section>;
        }
        return <section className="validation-structure-summary"><h3>Properties</h3><p>Select a rule, quick fix, expression or operation binding from the tree.</p></section>;
    }

    function importValidations(request: LayerImportRequest<ValidationInterchangeFormat>) {
        const imported = importValidationLayer(request.content, request.fileName, request.format, metamodel, { expressions: expressionModel, validations: validationModel });
        const currentRuleExpressionIds = new Set(validationModel.rules.map(rule => rule.conditionExpressionId));
        const preservedExpressions = request.strategy === 'REPLACE'
            ? expressionModel.expressions.filter(expression => !currentRuleExpressionIds.has(expression.id))
            : expressionModel.expressions;
        const nextExpressionModel: ExpressionModel = {
            ...expressionModel,
            expressions: mergeLayerItemsById(preservedExpressions, imported.expressions),
            annotations: { ...expressionModel.annotations, 'hydra.interchange.validation.format': request.format }
        };
        const nextValidationModel: ValidationModel = {
            ...validationModel,
            rules: request.strategy === 'REPLACE' ? imported.rules : mergeLayerItemsById(validationModel.rules, imported.rules),
            ruleSets: request.strategy === 'REPLACE' ? imported.ruleSets : mergeLayerItemsById(validationModel.ruleSets, imported.ruleSets),
            importedSources: request.strategy === 'REPLACE' ? [imported.source] : [...validationModel.importedSources, imported.source],
            annotations: { ...validationModel.annotations, 'hydra.interchange.validation.format': request.format }
        };
        updateExpressionModel(nextExpressionModel);
        updateValidationModel(nextValidationModel);
        setSelectedRuleId(imported.rules[0]?.id ?? nextValidationModel.rules[0]?.id ?? '');
        setWorkspace('RULES');
        onMessage(`Imported ${imported.rules.length} validation rule(s) from ${request.fileName}`);
        const partial = imported.rules.filter(rule => rule.conversionStatus !== 'FULL').length;
        const unresolved = imported.rules.filter(rule => rule.context.kind === 'CLASS' && (rule.context.classId ?? '').trim() === '').length;
        return { summary: `Imported ${imported.rules.length} validation rule(s)`, details: [`${imported.expressions.length} editable expression(s) created`, `${imported.ruleSets.length} group(s) imported`, `Strategy: ${request.strategy === 'MERGE' ? 'merge' : 'replace'}`], warnings: imported.warnings, metrics: [{ id: 'imported', label: 'Validations imported', value: imported.rules.length, tone: 'GOOD' as const }, { id: 'partial', label: 'Erroneous / partial validations', value: partial, tone: partial > 0 ? 'WARNING' as const : 'GOOD' as const }, { id: 'unresolved', label: 'Unresolved contexts', value: unresolved, tone: unresolved > 0 ? 'WARNING' as const : 'GOOD' as const }, { id: 'warnings', label: 'Warnings', value: imported.warnings.length, tone: imported.warnings.length > 0 ? 'WARNING' as const : 'GOOD' as const }] };
    }

    function importQuickFixes(request: LayerImportRequest<QuickFixInterchangeFormat>) {
        const imported = importQuickFixLayer(request.content, request.fileName, request.format, metamodel);
        const next: ActionModel = {
            ...actionModel,
            actions: request.strategy === 'REPLACE' ? imported.actions : mergeLayerItemsById(actionModel.actions, imported.actions),
            annotations: { ...actionModel.annotations, 'hydra.interchange.quickfix.format': request.format }
        };
        const validActionIds = new Set(next.actions.map(action => action.id));
        const unresolvedLinks: string[] = [];
        const linkByRule = new Map<string, string[]>();
        for (const link of imported.links) {
            const rule = validationModel.rules.find(item => item.name === link.validationRuleName || item.code === link.validationRuleName || item.name.toLowerCase() === link.validationRuleName.toLowerCase());
            if (rule === undefined) unresolvedLinks.push(`Quick fix ${link.actionId}: validation ${link.validationRuleName} is not present in the current validation layer.`);
            else linkByRule.set(rule.id, [...(linkByRule.get(rule.id) ?? []), link.actionId]);
        }
        const nextValidations: ValidationModel = {
            ...validationModel,
            rules: validationModel.rules.map(rule => ({ ...rule, quickFixIds: [...new Set([...rule.quickFixIds.filter(id => validActionIds.has(id)), ...(linkByRule.get(rule.id) ?? [])])] }))
        };
        updateActionModel(next);
        if (imported.links.length > 0 || request.strategy === 'REPLACE') updateValidationModel(nextValidations);
        setSelectedActionId(imported.actions[0]?.id ?? next.actions[0]?.id ?? '');
        setWorkspace('QUICK_FIXES');
        onMessage(`Imported ${imported.actions.length} quick fix(es) from ${request.fileName}`);
        const emptyFixes = imported.actions.filter(action => action.operations.length === 0).length;
        return { summary: `Imported ${imported.actions.length} quick fix(es)`, details: [`${imported.actions.reduce((sum, action) => sum + action.operations.length, 0)} declarative operation(s) mapped`, `${imported.links.length - unresolvedLinks.length} validation association(s) restored`, `Strategy: ${request.strategy === 'MERGE' ? 'merge/upsert' : 'replace'}`], warnings: [...imported.warnings, ...unresolvedLinks], metrics: [{ id: 'imported', label: 'Quick fixes imported', value: imported.actions.length, tone: 'GOOD' as const }, { id: 'empty', label: 'Erroneous / empty fixes', value: emptyFixes, tone: emptyFixes > 0 ? 'WARNING' as const : 'GOOD' as const }, { id: 'links', label: 'Validation links restored', value: imported.links.length - unresolvedLinks.length, tone: 'NEUTRAL' as const }, { id: 'warnings', label: 'Warnings', value: imported.warnings.length + unresolvedLinks.length, tone: imported.warnings.length + unresolvedLinks.length > 0 ? 'WARNING' as const : 'GOOD' as const }] };
    }

    return (
        <section className="validation-designer">
            <DesignerCommandBar className="validation-designer-toolbar hydra-unified-workbench-toolbar" title="Validation and quick-fix designer" description={<>Define constraints and corrections over {metamodel.name || metamodel.id} without writing code.</>} status={<>{dirty && <span className="validation-unsaved-state">Unsaved changes</span>}{!persisted && <span className="validation-unsaved-state">Save metamodel first</span>}</>} actions={<><DesignerCommandGroup label="Interchange">{workspace === 'RULES' && <><HydraButton type="button" disabled={!persisted} onClick={() => setLayerIo({ layer: 'VALIDATIONS', mode: 'IMPORT' })}>Import validations</HydraButton><HydraButton type="button" disabled={!persisted} onClick={() => setLayerIo({ layer: 'VALIDATIONS', mode: 'EXPORT' })}>Export validations</HydraButton></>}{workspace === 'QUICK_FIXES' && <><HydraButton type="button" disabled={!persisted} onClick={() => setLayerIo({ layer: 'QUICK_FIXES', mode: 'IMPORT' })}>Import quick fixes</HydraButton><HydraButton type="button" disabled={!persisted} onClick={() => setLayerIo({ layer: 'QUICK_FIXES', mode: 'EXPORT' })}>Export quick fixes</HydraButton></>}</DesignerCommandGroup><HydraButton className="validation-save-button" disabled={loading || !persisted || (!dirty && !needsInitialSave)} type="button" onClick={() => void saveAll()}>{loading ? 'Working…' : 'Save validations'}</HydraButton><DesignerCommandGroup label="Quality"><HydraButton disabled={loading || !persisted || dirty || needsInitialSave} type="button" onClick={() => void checkDefinitions()}>Check definitions</HydraButton></DesignerCommandGroup></>} viewsLabel="Workspace" viewsSummary={workspace} views={<nav aria-label="Validation designer sections"><HydraButton className={workspace === 'RULES' ? 'active' : ''} type="button" onClick={() => setWorkspace('RULES')}>Rules</HydraButton><HydraButton className={workspace === 'QUICK_FIXES' ? 'active' : ''} type="button" onClick={() => setWorkspace('QUICK_FIXES')}>Quick fixes</HydraButton>{hasOperations && <HydraButton className={workspace === 'OPERATIONS' ? 'active' : ''} type="button" onClick={() => setWorkspace('OPERATIONS')}>Operations</HydraButton>}{structureTabOpen ? <span className={`designer-structure-tab-entry${workspace === 'STRUCTURE' ? ' active' : ''}`}><HydraButton className={workspace === 'STRUCTURE' ? 'active' : ''} type="button" onClick={() => setWorkspace('STRUCTURE')}>Structure tree</HydraButton><HydraButton aria-label="Close validation structure tree" className="designer-structure-tab-close hydra-window-close" type="button" onClick={() => { setStructureTabOpen(false); if (workspace === 'STRUCTURE') setWorkspace('RULES'); }}>×</HydraButton></span> : <HydraButton type="button" onClick={() => { setStructureTabOpen(true); setWorkspace('STRUCTURE'); }}>Structure tree</HydraButton>}<HydraButton className={workspace === 'DIAGNOSTICS' ? 'active' : ''} type="button" onClick={() => setWorkspace('DIAGNOSTICS')}>Diagnostics <span>{diagnosticCount.errors + diagnosticCount.warnings + diagnosticCount.information}</span></HydraButton></nav>} />
            {layerIo?.layer === 'VALIDATIONS' && <LayerInterchangeDialog
                mode={layerIo.mode}
                layerName="Validations"
                description="Import OCL, EVL, ATL or HYDRA validation files independently from the metamodel. Imported rules are adapted to the selected metamodel and remain fully editable in Rules, Guided and Advanced modes."
                formats={VALIDATION_INTERCHANGE_FORMATS}
                defaultFormat={(validationModel.annotations['hydra.interchange.validation.format'] as ValidationInterchangeFormat | undefined) ?? 'OCL'}
                currentMetamodel={metamodel}
                onClose={() => setLayerIo(null)}
                onAnalyzeImport={request => analyzeValidationImport(request.content, request.fileName, request.format, metamodel, { expressions: expressionModel, validations: validationModel })}
                onImport={importValidations}
                onExport={format => exportValidationLayer(format, metamodel, expressionModel, validationModel)}
            />}
            {layerIo?.layer === 'QUICK_FIXES' && <LayerInterchangeDialog
                mode={layerIo.mode}
                layerName="Quick fixes"
                description="Import EVL fix blocks, EOL repair operations or HYDRA actions independently. Supported statements are mapped to editable declarative quick-fix operations against the selected metamodel."
                formats={QUICK_FIX_INTERCHANGE_FORMATS}
                defaultFormat={(actionModel.annotations['hydra.interchange.quickfix.format'] as QuickFixInterchangeFormat | undefined) ?? 'EVL'}
                currentMetamodel={metamodel}
                onClose={() => setLayerIo(null)}
                onAnalyzeImport={request => analyzeQuickFixImport(request.content, request.fileName, request.format, metamodel)}
                onImport={importQuickFixes}
                onExport={format => exportQuickFixLayer(format, metamodel, actionModel)}
            />}

            {!persisted ? (
                <section className="validation-designer-blocked validation-empty-state">
                    <strong>Save the metamodel before designing validations</strong>
                    <span>Validation rules, expressions and quick fixes are linked to the saved structural identifiers.</span>
                </section>
            ) : workspace === 'RULES' ? (
                <div className="validation-rules-workspace">
                    <ValidationRuleManager
                        model={validationModel}
                        onChange={updateValidationModel}
                        onCreateRule={createRule}
                        onDuplicateRule={duplicateRule}
                        onSelectRule={setSelectedRuleId}
                        report={validationReport}
                        selectedRuleId={selectedRuleId}
                    />
                    <ValidationRuleEditor
                        actionModel={actionModel}
                        expression={selectedExpression}
                        metamodel={metamodel}
                        onChangeExpression={changeExpression}
                        onChangeRule={changeRule}
                        onDelete={deleteRule}
                        onMoveToGroup={moveRuleToGroup}
                        onFinishGuided={() => saveAll()}
                        allDefinitionsReady={designerBlockingError === ''}
                        rule={selectedRule}
                        validationModel={validationModel}
                    />
                </div>
            ) : workspace === 'STRUCTURE' ? (
                <div className={`designer-structure-workspace designer-structure-direct-layout validation-structure-workspace${structureCollapsed ? ' collapsed' : ''}`}>
                    <section className="designer-structure-tree-pane"><header><div><h3>Validation structure</h3><p>Rules, expressions, quick fixes, operations and their links.</p></div><HydraButton type="button" onClick={() => setStructureCollapsed(value => !value)}>{structureCollapsed ? 'Expand tree' : 'Collapse tree'}</HydraButton></header>{!structureCollapsed && <><input aria-label="Filter validation structure" placeholder="Filter structure..." value={structureFilter} onChange={event => setStructureFilter(event.target.value)} /><ValidationStructureTree metamodel={metamodel} validations={validationModel} expressions={expressionModel} actions={actionModel} behaviors={behaviorModel} verifiedRuleIds={verifiedRuleIds} selection={structureSelection} filter={structureFilter} onSelect={selectStructureItem} /></>}</section>
                    {structurePropertiesOpen ? <aside className={`designer-structure-properties-pane${structurePropertiesCollapsed ? ' collapsed' : ''}`}><header><div><h3>Properties</h3><p>Edits update the validation definitions.</p></div><div><HydraButton type="button" onClick={() => setStructurePropertiesCollapsed(value => !value)}>{structurePropertiesCollapsed ? 'Expand' : 'Collapse'}</HydraButton><HydraButton aria-label="Close validation properties" className="hydra-form-close-button hydra-window-close" type="button" onClick={() => setStructurePropertiesOpen(false)}>×</HydraButton></div></header>{!structurePropertiesCollapsed && structureProperties()}</aside> : <HydraButton className="designer-structure-properties-reopen" type="button" onClick={() => { setStructurePropertiesOpen(true); setStructurePropertiesCollapsed(false); }}>Open properties</HydraButton>}
                </div>
            ) : workspace === 'QUICK_FIXES' ? (
                <QuickFixDesigner
                    actionModel={actionModel}
                    expressionModel={expressionModel}
                    metamodel={metamodel}
                    onChange={updateActionModel}
                    onSelectAction={setSelectedActionId}
                    onFinishGuided={() => saveAll()}
                    selectedActionId={selectedActionId}
                />
            ) : workspace === 'OPERATIONS' ? (
                <OperationBindingDesigner actionModel={actionModel} behaviorModel={behaviorModel} expressionModel={expressionModel} metamodel={metamodel} onChange={updateBehaviorModel} />
            ) : (
                <DiagnosticsPanel action={actionReport} behavior={behaviorReport} expression={expressionReport} validation={validationReport} onSelectElement={elementId => {
                    if (validationModel.rules.some(rule => rule.id === elementId)) selectStructureItem({ kind: 'RULE', id: elementId });
                    else if (actionModel.actions.some(action => action.id === elementId)) selectStructureItem({ kind: 'ACTION', id: elementId });
                    else if (expressionModel.expressions.some(expression => expression.id === elementId)) {
                        const owner = validationModel.rules.find(rule => rule.conditionExpressionId === elementId);
                        selectStructureItem(owner === undefined ? { kind: 'EXPRESSION', id: elementId } : { kind: 'RULE', id: owner.id });
                    } else if (behaviorModel.bindings.some(binding => binding.operationId === elementId)) selectStructureItem({ kind: 'OPERATION', id: elementId });
                    else return;
                    setStructureTabOpen(true); setWorkspace('STRUCTURE');
                }} />
            )}
            {overwriteDialogs}
        </section>
    );
}

type ValidationSaveStage = 'EXPRESSIONS' | 'QUICK_FIXES' | 'OPERATIONS' | 'VALIDATIONS';

function validationSaveActionableMessage(error: unknown, stage: ValidationSaveStage, language: 'en' | 'es'): string {
    const base = explainInputError(error, 'Could not save validation definitions', 'validation designer');
    const es = language === 'es';
    const stageField: Record<ValidationSaveStage, string> = es ? {
        EXPRESSIONS: 'Paso 4 · Condición > Documento de expresiones',
        QUICK_FIXES: 'Paso 5 · Correcciones rápidas > Documento de acciones',
        OPERATIONS: 'Operaciones > Comportamientos',
        VALIDATIONS: 'Paso 6 · Revisar > Documento de validaciones'
    } : {
        EXPRESSIONS: 'Step 4 · Condition > Expression document',
        QUICK_FIXES: 'Step 5 · Quick fixes > Action document',
        OPERATIONS: 'Operations > Behaviors',
        VALIDATIONS: 'Step 6 · Review > Validation document'
    };
    const fieldLabel = es ? 'Campo' : 'Field';
    const fieldPattern = es ? /^Campo:\s*(.*)$/m : /^Field:\s*(.*)$/m;
    const match = base.match(fieldPattern);
    if (match !== null) {
        const nativeField = match[1].trim();
        const detailed = nativeField === '' || nativeField.toLowerCase() === 'id'
            ? stageField[stage]
            : `${stageField[stage]} > ${nativeField}`;
        return base.replace(fieldPattern, `${fieldLabel}: ${detailed}`);
    }
    const lines = base.split('\n');
    lines.splice(Math.min(1, lines.length), 0, `${fieldLabel}: ${stageField[stage]}`);
    return lines.join('\n');
}

function validationBlockingActionableMessage(message: string, language: 'en' | 'es'): string {
    const es = language === 'es';
    const lower = message.toLowerCase();
    let field = es ? 'Definición de validación' : 'Validation definition';
    let how = es ? 'Corrige el campo indicado en el paso donde se define y vuelve a revisar el asistente. HYDRA no permitirá guardar hasta que la comprobación quede en verde.' : 'Correct the indicated field in the step where it is defined and review the guided workflow again. HYDRA will not save until the check is green.';

    if (lower.includes('validation') && lower.includes('stable identifier')) field = es ? 'Paso 1 · Identidad > ID técnico de la validación' : 'Step 1 · Identity > Validation technical ID';
    else if (lower.includes('problem code') || lower.includes('stable problem code')) field = es ? 'Paso 1 · Identidad > Código estable del problema' : 'Step 1 · Identity > Stable problem code';
    else if (lower.includes('failure message')) field = es ? 'Paso 3 · Diagnóstico y disparadores > Mensaje de fallo' : 'Step 3 · Diagnostics and triggers > Failure message';
    else if (lower.includes('condition expression')) field = es ? 'Paso 4 · Condición > Expresión' : 'Step 4 · Condition > Expression';
    else if (lower.includes('context class')) field = es ? 'Paso 2 · Contexto > Clase' : 'Step 2 · Context > Class';
    else if (lower.includes('context feature')) field = es ? 'Paso 2 · Contexto > Atributo o relación' : 'Step 2 · Context > Attribute or relation';
    else if (lower.includes('quick-fix') || lower.includes('quick fix')) field = es ? 'Paso 5 · Correcciones rápidas' : 'Step 5 · Quick fixes';
    else if (lower.includes('expression document')) field = es ? 'Paso 4 · Condición > Documento de expresiones > ID técnico' : 'Step 4 · Condition > Expression document > Technical ID';
    else if (lower.includes('quick-fix document')) field = es ? 'Paso 5 · Correcciones rápidas > Documento de acciones > ID técnico' : 'Step 5 · Quick fixes > Action document > Technical ID';
    else if (lower.includes('validation document')) field = es ? 'Paso 6 · Revisar > Documento de validaciones > ID técnico' : 'Step 6 · Review > Validation document > Technical ID';
    else if (lower.includes('expression')) field = es ? 'Paso 4 · Condición > Expresión' : 'Step 4 · Condition > Expression';

    if (lower.includes('stable identifier') || lower.includes('document id')) {
        how = es
            ? 'Usa un identificador estable: empieza por una letra o un número y utiliza únicamente los caracteres admitidos por el campo. No cambies un ID ya referenciado; si el valor procede de una selección, vuelve a elegir el elemento desde su desplegable.'
            : 'Use a stable identifier that starts with a letter or number and contains only characters accepted by the field. Do not rename an ID that is already referenced; if it comes from a selection, choose the element again from its selector.';
    } else if (lower.includes('must select') || lower.includes('reference')) {
        how = es
            ? 'Vuelve al paso indicado y selecciona la referencia desde el desplegable correspondiente. Comprueba que el elemento dependiente exista y que el metamodelo esté guardado.'
            : 'Return to the indicated step and select the reference from its selector. Verify that the referenced element exists and the metamodel is saved.';
    }

    const what = es ? 'La validación no está lista para guardarse.' : 'The validation is not ready to be saved.';
    const why = es ? `La comprobación bloqueante ha detectado lo siguiente: ${message}` : `The blocking check detected the following problem: ${message}`;
    const checks = es
        ? 'Revisa los checks rojos del proceso guiado. Cada paso debe estar correcto antes de habilitar «Paso siguiente» y el paso 6 debe confirmar que todas las definiciones están listas para guardar.'
        : 'Review the red checks in the guided workflow. Every step must be valid before Next step is enabled, and step 6 must confirm that all definitions are ready to save.';
    return `${es ? 'Qué ha ocurrido' : 'What went wrong'}: ${what}\n${es ? 'Campo' : 'Field'}: ${field}\n${es ? 'Por qué' : 'Why'}: ${why}\n${es ? 'Cómo solucionarlo' : 'How to fix it'}: ${how}\n${es ? 'Comprobaciones' : 'Possible checks'}: ${checks}`;
}

function createDefaults(metamodelId: string): { expressionModel: ExpressionModel; actionModel: ActionModel; validationModel: ValidationModel; behaviorModel: OperationBehaviorModel } {
    const expressionModel = createEmptyExpressionModel(metamodelId);
    const actionModel = createEmptyActionModel(metamodelId, expressionModel.id);
    return { expressionModel, actionModel, validationModel: createEmptyValidationModel(metamodelId, expressionModel.id, actionModel.id), behaviorModel: createEmptyOperationBehaviorModel(metamodelId) };
}

function createEmptyOperationBehaviorModel(metamodelId: string): OperationBehaviorModel {
    return { schemaVersion: '1.0', metamodelId, bindings: [], annotations: {} };
}

async function loadBehaviorOrDefault(metamodelId: string): Promise<{ model: OperationBehaviorModel; etag: string }> {
    try {
        const result = await loadOperationBehaviorModel(metamodelId);
        return { model: result.behaviorModel, etag: result.etag };
    } catch (error) {
        if (isMissingValidationResource(error)) return { model: createEmptyOperationBehaviorModel(metamodelId), etag: '' };
        throw error;
    }
}

async function loadExpressionOrDefault(metamodelId: string): Promise<{ model: ExpressionModel; etag: string }> {
    try {
        const result = await loadExpressionModel(metamodelId);
        return { model: result.expressionModel, etag: result.etag };
    } catch (error) {
        if (isMissingValidationResource(error)) return { model: createEmptyExpressionModel(metamodelId), etag: '' };
        throw error;
    }
}

async function loadActionOrDefault(metamodelId: string, expressionModelId: string): Promise<{ model: ActionModel; etag: string }> {
    try {
        const result = await loadActionModel(metamodelId);
        return { model: result.actionModel, etag: result.etag };
    } catch (error) {
        if (isMissingValidationResource(error)) return { model: createEmptyActionModel(metamodelId, expressionModelId), etag: '' };
        throw error;
    }
}

async function loadValidationOrDefault(metamodelId: string, expressionModelId: string, actionModelId: string): Promise<{ model: ValidationModel; etag: string }> {
    try {
        const result = await loadValidationModel(metamodelId);
        return { model: result.validationModel, etag: result.etag };
    } catch (error) {
        if (isMissingValidationResource(error)) return { model: createEmptyValidationModel(metamodelId, expressionModelId, actionModelId), etag: '' };
        throw error;
    }
}

function DiagnosticsPanel({ expression, action, validation, behavior, onSelectElement }: { expression: ExpressionModelCheckReport | null; action: ActionModelCheckReport | null; validation: ValidationModelCheckReport | null; behavior: OperationBehaviorSaveResponse['validation'] | null; onSelectElement: (elementId: string) => void }) {
    const { t } = useHydraLanguage();
    const diagnostics = [
        ...(expression?.diagnostics ?? []).map(item => ({ source: 'Expression model', severity:item.severity, code:item.code, message:item.message, path:item.path, elementId:item.expressionId })),
        ...(expression?.expressions ?? []).flatMap(result => result.diagnostics.map(item => ({ source: `Expression ${result.expressionId}`, severity:item.severity, code:item.code, message:item.message, path:item.path, elementId:result.expressionId }))),
        ...(action?.diagnostics ?? []).map(item => ({ source: 'Quick-fix model', severity:item.severity, code:item.code, message:item.message, path:item.path, elementId:item.actionId })),
        ...(action?.actions ?? []).flatMap(result => result.diagnostics.map(item => ({ source: `Quick fix ${result.actionId}`, severity:item.severity, code:item.code, message:item.message, path:item.path, elementId:result.actionId }))),
        ...(validation?.diagnostics ?? []).map(item => ({ source: 'Validation model', severity:item.severity, code:item.code, message:item.message, path:item.path, elementId:item.ruleId })),
        ...(validation?.rules ?? []).flatMap(result => result.diagnostics.map(item => ({ source: `Validation ${result.ruleId}`, severity:item.severity, code:item.code, message:item.message, path:item.path, elementId:result.ruleId }))),
        ...(behavior?.issues ?? []).map(item => ({ source: 'Operation bindings', severity:item.severity, code:item.code, message:item.message, path:item.path, elementId:operationIdFromPath(item.path) }))
    ];
    return <section className="validation-diagnostics"><HydraDiagnosticList diagnostics={diagnostics} context="validation" title={t('Definition diagnostics')} emptyText={t('Save or check the definitions to run the backend checkers.')} onSelectElement={onSelectElement} /></section>;
}

function operationIdFromPath(path: string): string | undefined {
    const match = path.match(/(?:operation|binding)(?:s)?(?:\[[^\]]+\]|[.:/])([A-Za-z0-9_.:-]+)/i);
    return match?.[1];
}

function addBehaviorDiagnostics(base: { errors: number; warnings: number; information: number }, behavior: OperationBehaviorSaveResponse['validation'] | null): { errors: number; warnings: number; information: number } {
    const issues = behavior?.issues ?? [];
    return {
        errors: base.errors + issues.filter(item => item.severity === 'ERROR').length,
        warnings: base.warnings + issues.filter(item => item.severity === 'WARNING').length,
        information: base.information + issues.filter(item => item.severity === 'INFO').length
    };
}

function mergeLayerItemsById<T extends { id: string }>(current: readonly T[], imported: readonly T[]): T[] {
    const byId = new Map(current.map(item => [item.id, item]));
    for (const item of imported) byId.set(item.id, item);
    return [...byId.values()];
}
