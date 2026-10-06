import { HydraButton } from '../design-system/HydraButton';
import type { MetaAttribute, MetaModel, MetaRelation } from '../metamodel-designer/types';
import type { BinaryExpressionOperator, ExpressionNode, ExpressionValueType, UnaryExpressionOperator } from '../validation/types';
import { createValidationDesignerId, defaultLiteralValue, expressionTypeForAttribute, relationLabel } from './validationDesignerFactory';
import { attributesForContext } from './validationDesignerSelectors';
import { HydraCheckbox, HydraTooltip, HydraSelect } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { HelpedField } from './ValidationFieldHelp';

type GuidedSource = 'ATTRIBUTE' | 'RELATION_COUNT' | 'CLASS_COUNT';
type GuidedOperator = BinaryExpressionOperator | Extract<UnaryExpressionOperator, 'IS_EMPTY' | 'IS_NOT_EMPTY' | 'IS_NULL' | 'IS_NOT_NULL'>;
type JoinOperator = 'AND' | 'OR';

interface GuidedClause {
    id: string;
    source: GuidedSource;
    attributeId: string;
    relationId: string;
    classId: string;
    operator: GuidedOperator;
    value: unknown;
    negated: boolean;
}

interface GuidedConditionBuilderProps {
    root: ExpressionNode;
    metamodel: MetaModel;
    contextClassId?: string;
    onChange: (root: ExpressionNode) => void;
}

const COMPARISON_OPERATORS: BinaryExpressionOperator[] = [
    'EQUALS', 'NOT_EQUALS', 'GREATER_THAN', 'GREATER_OR_EQUAL', 'LESS_THAN', 'LESS_OR_EQUAL'
];

export function GuidedConditionBuilder({ root, metamodel, contextClassId, onChange }: GuidedConditionBuilderProps) {
    const { t } = useHydraLanguage();
    const attributes = guidedAttributes(metamodel, contextClassId);
    const relations = guidedRelations(metamodel, contextClassId);
    const parsed = parseGuidedRoot(root, metamodel, contextClassId);
    const emptyDefault = root.kind === 'LITERAL' && root.valueType.kind === 'BOOLEAN' && root.value === true;
    const clauses = parsed?.clauses ?? [];
    const join = parsed?.join ?? 'AND';
    const contextClass = metamodel.classes.find(item => item.id === contextClassId);

    function commit(updated: GuidedClause[], nextJoin: JoinOperator = join): void {
        onChange(buildRoot(updated, nextJoin, metamodel));
    }

    function addClause(source: GuidedSource): void {
        commit([...clauses, createClause(source, metamodel, contextClassId)]);
    }

    if (parsed === null && !emptyDefault) {
        return (
            <div className="validation-guided-unsupported">
                <div>
                    <strong>{t("This condition uses advanced expression elements.")}</strong>
                    <span>{t("Keep it intact and switch to Advanced to edit it, or replace it with one of the guided templates below.")}</span>
                </div>
                <div className="validation-guided-template-row">
                    {attributes.length > 0 && <HydraButton type="button" onClick={() => commit([createClause('ATTRIBUTE', metamodel, contextClassId)])}>{t("Replace with attribute condition")}</HydraButton>}
                    {relations.length > 0 && <HydraButton type="button" onClick={() => commit([createClause('RELATION_COUNT', metamodel, contextClassId)])}>{t("Replace with relation condition")}</HydraButton>}
                    {metamodel.classes.length > 0 && <HydraButton type="button" onClick={() => commit([createClause('CLASS_COUNT', metamodel, contextClassId)])}>{t("Replace with model-wide count")}</HydraButton>}
                </div>
            </div>
        );
    }

    return (
        <div className="validation-guided-builder">
            <div className="validation-guided-intro">
                <div>
                    <strong>{t("Describe the valid state step by step")}</strong>
                    <span>{t("Choose what to check, the comparison and the expected value. HYDRA builds the expression automatically.")}</span>
                </div>
                <HydraTooltip label={t("Information about guided conditions")} title={t("Guided conditions")}>
                    <p>{t("Each row is one condition that must be true for the model to be valid. Use “All conditions” for AND or “Any condition” for OR.")}</p>
                    <p>{t("Switch to Advanced only for nested collections, custom functions, query operations or complex formulas.")}</p>
                </HydraTooltip>
            </div>
            <div className="validation-guided-context-summary" role="status">
                <span>{t('Selected class')}</span>
                <strong>{contextClass?.name ?? t('No class selected')}</strong>
                <small>{contextClass === undefined ? t('Select a context class in the rule before defining field conditions.') : `${attributes.length} ${t('attribute(s)')} · ${relations.length} ${t('relation(s)')} ${t('loaded automatically')}`}</small>
            </div>

            {clauses.length === 0 ? (
                <div className="validation-guided-empty">
                    <strong>{t("What do you want to check?")}</strong>
                    <span>{t("Choose one of the common checks below. HYDRA will create the expression and then let you choose the field, comparison and expected value in plain form controls.")}</span>
                    {(attributes.length > 0 || relations.length > 0 || metamodel.classes.length > 0) ? <div className="validation-guided-template-grid">
                        {attributes.length > 0 && <HydraButton type="button" onClick={() => addClause('ATTRIBUTE')}><strong>{t("Check a field value")}</strong><span>{t("Choose an attribute and say what value it must have, exceed, contain or avoid.")}</span></HydraButton>}
                        {relations.length > 0 && <HydraButton type="button" onClick={() => addClause('RELATION_COUNT')}><strong>{t("Check a relation")}</strong><span>{t("Define how many linked elements are required, allowed or expected.")}</span></HydraButton>}
                        {metamodel.classes.length > 0 && <HydraButton type="button" onClick={() => addClause('CLASS_COUNT')}><strong>{t("Check the complete model")}</strong><span>{t("Define how many elements of a selected type must exist in the model.")}</span></HydraButton>}
                    </div> : <div className="validation-guided-context-needed" role="status"><strong>{t("No model structure is available for a guided condition yet.")}</strong><span>{t("Go back to Context and select a class, attribute or relation, or load the metamodel associated with these validations. HYDRA will then offer the available fields here automatically.")}</span></div>}
                </div>
            ) : (
                <>
                    {clauses.length > 1 && (
                        <div className="validation-guided-join">
                            <span>{t("The rule is valid when")}</span>
                            <HydraSelect ariaLabel="Condition combination" value={join} options={[{value:'AND',label:t('All conditions are satisfied'),description:t('Every configured condition must be true.')},{value:'OR',label:t('Any condition is satisfied'),description:t('At least one configured condition must be true.')}]} onChange={value => commit(clauses, value as JoinOperator)} />
                        </div>
                    )}
                    <div className="validation-guided-clause-list">
                        {clauses.map((clause, index) => (
                            <GuidedClauseEditor
                                key={clause.id}
                                clause={clause}
                                index={index}
                                metamodel={metamodel}
                                contextClassId={contextClassId}
                                onChange={updated => commit(clauses.map(item => item.id === clause.id ? updated : item))}
                                onRemove={() => commit(clauses.filter(item => item.id !== clause.id))}
                            />
                        ))}
                    </div>
                    <div className="validation-guided-add-row">
                        <span>{t("Add another check:")}</span>
                        {attributes.length > 0 && <HydraButton type="button" onClick={() => addClause('ATTRIBUTE')}>{t("+ Field value")}</HydraButton>}
                        {relations.length > 0 && <HydraButton type="button" onClick={() => addClause('RELATION_COUNT')}>{t("+ Relation")}</HydraButton>}
                        {metamodel.classes.length > 0 && <HydraButton type="button" onClick={() => addClause('CLASS_COUNT')}>{t("+ Complete model")}</HydraButton>}
                    </div>
                    <div className="validation-guided-summary"><strong>{t("In plain language:")}</strong><span>{summary(clauses, join, metamodel, t)}</span></div>
                </>
            )}
        </div>
    );
}

function GuidedClauseEditor({ clause, index, metamodel, contextClassId, onChange, onRemove }: {
    clause: GuidedClause;
    index: number;
    metamodel: MetaModel;
    contextClassId?: string;
    onChange: (clause: GuidedClause) => void;
    onRemove: () => void;
}) {
    const { t } = useHydraLanguage();
    const attributes = guidedAttributes(metamodel, contextClassId);
    const relations = guidedRelations(metamodel, contextClassId);
    const selectedAttribute = attributes.find(item => item.attribute.id === clause.attributeId)?.attribute;
    const operators = clause.source === 'ATTRIBUTE' ? operatorsForAttribute(selectedAttribute) : COMPARISON_OPERATORS;
    const showValue = !['IS_EMPTY', 'IS_NOT_EMPTY', 'IS_NULL', 'IS_NOT_NULL'].includes(clause.operator);

    function changeSource(source: GuidedSource): void {
        onChange(createClause(source, metamodel, contextClassId, clause.id));
    }

    function changeAttribute(attributeId: string): void {
        const attribute = attributes.find(item => item.attribute.id === attributeId)?.attribute;
        const nextOperator = operatorsForAttribute(attribute).includes(clause.operator) ? clause.operator : 'EQUALS';
        onChange({ ...clause, attributeId, operator: nextOperator, value: defaultForAttribute(attribute) });
    }

    return (
        <article className="validation-guided-clause">
            <header>
                <span className="validation-guided-number">{index + 1}</span>
                <div><strong>{t("Condition")} {index + 1}</strong><small>{t('Configure this check with the same field pattern used in the rest of HYDRA.')}</small></div>
                <HydraButton type="button" onClick={onRemove}>{t("Remove")}</HydraButton>
            </header>
            <div className="validation-guided-fields">
                <HelpedField label="Check" targetId={`guided-${clause.id}-source`} required help="Choose whether this condition checks an attribute value, a relation cardinality or the number of elements in the complete model." example="Attribute value">
                    <HydraSelect id={`guided-${clause.id}-source`} ariaLabel={`${t('Condition')} ${index + 1} ${t('source')}`} value={clause.source} options={[{value:'ATTRIBUTE',label:t('Attribute value'),description:t('Compare one attribute of the selected context.')}, ...(relations.length > 0 ? [{value:'RELATION_COUNT',label:t('Relation cardinality'),description:t('Check how many elements are linked by a relation.')}] : []), {value:'CLASS_COUNT',label:t('Elements in the model'),description:t('Check how many instances of a class exist in the complete model.')}]} onChange={value => changeSource(value as GuidedSource)} />
                </HelpedField>

                {clause.source === 'ATTRIBUTE' && <HelpedField label="Attribute" targetId={`guided-${clause.id}-attribute`} required help="Choose an attribute of the selected class. HYDRA loads inherited attributes too and shows each attribute together with its type." example="age · Integer" options={attributes.map(item => ({ value:item.attribute.id,label:`${item.attribute.name} · ${t(attributeTypeLabel(item.attribute, metamodel))}`,description:`${item.owner.name}.${item.attribute.name} · ${t('Type')}: ${t(attributeTypeLabel(item.attribute, metamodel))} · ${t(item.attribute.multiplicity.lowerBound > 0 ? 'Required' : 'Optional')}` }))}>
                    <HydraSelect id={`guided-${clause.id}-attribute`} ariaLabel={`${t('Condition')} ${index + 1} ${t('attribute')}`} value={clause.attributeId} placeholder="Select attribute" options={attributes.map(item => ({ value:item.attribute.id,label:`${item.attribute.name} · ${t(attributeTypeLabel(item.attribute, metamodel))}`,description:`${item.owner.name}.${item.attribute.name} · ${t('Type')}: ${t(attributeTypeLabel(item.attribute, metamodel))} · ${t(item.attribute.multiplicity.lowerBound > 0 ? 'Required' : 'Optional')}` ,category:item.owner.name }))} onChange={changeAttribute} />
                </HelpedField>}
                {clause.source === 'RELATION_COUNT' && <HelpedField label="Relation" targetId={`guided-${clause.id}-relation`} required help="Choose a relation available from the selected class. Only relations that can be navigated from the current context are offered." example="Company.employees">
                    <HydraSelect id={`guided-${clause.id}-relation`} ariaLabel={`${t('Condition')} ${index + 1} ${t('relation')}`} value={clause.relationId} placeholder="Select relation" options={relations.map(item => ({ value:item.id,label:relationLabel(item,metamodel),description:t('Relation available from the selected validation context.') }))} onChange={value => onChange({ ...clause, relationId:value })} />
                </HelpedField>}
                {clause.source === 'CLASS_COUNT' && <HelpedField label="Class" targetId={`guided-${clause.id}-class`} required help="Choose the class whose instances will be counted in the complete model." example="Person">
                    <HydraSelect id={`guided-${clause.id}-class`} ariaLabel={`${t('Condition')} ${index + 1} ${t('class')}`} value={clause.classId} placeholder="Select class" options={metamodel.classes.map(item => ({ value:item.id,label:item.name,description:t('Metamodel class counted by this condition.') }))} onChange={value => onChange({ ...clause, classId:value })} />
                </HelpedField>}

                <HelpedField label="Must be" targetId={`guided-${clause.id}-operator`} required help="Choose how the selected value must compare with the expected value. HYDRA only offers operators compatible with the selected attribute type." example="Greater than or equal to" options={operators.map(operator => ({ value:operator,label:t(operatorText(operator)),description:t('Comparison applied by this condition.') }))}>
                    <HydraSelect id={`guided-${clause.id}-operator`} ariaLabel={`${t('Condition')} ${index + 1} ${t('operator')}`} value={clause.operator} options={operators.map(operator => ({ value:operator,label:t(operatorText(operator)),description:t('Comparison applied by this condition.') }))} onChange={value => onChange({ ...clause, operator:value as GuidedOperator })} />
                </HelpedField>
                {showValue && <HelpedField label="Expected value" targetId={`guided-${clause.id}-expected`} required help="Enter or choose the value that makes this condition valid. The editor adapts to the selected attribute type." example={selectedAttribute === undefined ? '1' : `${defaultForAttribute(selectedAttribute)}`}>
                    <GuidedValueEditor id={`guided-${clause.id}-expected`} clause={clause} attribute={selectedAttribute} metamodel={metamodel} onChange={value => onChange({ ...clause, value })} />
                </HelpedField>}
                <HelpedField className="validation-guided-negation-field" label="Negate condition" targetId={`guided-${clause.id}-negated`} help="Invert the complete condition only when the valid state is the opposite of the condition configured above." example="Not this condition">
                    <HydraCheckbox id={`guided-${clause.id}-negated`} label="NOT this condition" description="Invert the result of this complete condition." checked={clause.negated} onChange={event => onChange({ ...clause, negated: event.target.checked })} />
                </HelpedField>
            </div>
            <p className="validation-guided-operator-hint">{t(showValue ? 'This operator compares with an expected value.' : 'This operator checks the selected value directly, so no right-hand expression is required.')}</p>
        </article>
    );
}

function GuidedValueEditor({ id, clause, attribute, metamodel, onChange }: { id: string; clause: GuidedClause; attribute?: MetaAttribute; metamodel: MetaModel; onChange: (value: unknown) => void }) {
    const { t } = useHydraLanguage();
    if (clause.source !== 'ATTRIBUTE' || attribute === undefined) return <input id={id} aria-label="Expected value" type="number" min="0" value={Number(clause.value ?? 0)} onChange={event => onChange(Number(event.target.value))} />;
    const type = expressionTypeForAttribute(attribute);
    if (type.kind === 'BOOLEAN') return <HydraSelect id={id} ariaLabel="Expected value" value={String(clause.value)} options={[{value:'true',label:t('True'),description:t('The attribute must evaluate to true.')},{value:'false',label:t('False'),description:t('The attribute must evaluate to false.')}]} onChange={value => onChange(value === 'true')} />;
    if (type.kind === 'ENUM') {
        const enumeration = metamodel.enums.find(item => item.id === type.enumId);
        return <HydraSelect id={id} ariaLabel="Expected value" value={String(clause.value ?? '')} placeholder="Select value" options={(enumeration?.literals ?? []).map(item => ({value:item.name,label:item.name,description:t('Enumeration literal defined by the metamodel.')}))} onChange={onChange} />;
    }
    const inputType = type.kind === 'INTEGER' || type.kind === 'DECIMAL' ? 'number' : type.kind === 'DATE' ? 'date' : type.kind === 'DATETIME' ? 'datetime-local' : 'text';
    return <input id={id} aria-label="Expected value" type={inputType} value={String(clause.value ?? '')} onChange={event => onChange(type.kind === 'INTEGER' || type.kind === 'DECIMAL' ? Number(event.target.value) : event.target.value)} />;
}

function attributeTypeLabel(attribute: MetaAttribute, metamodel: MetaModel): string {
    if (attribute.type.kind === 'PRIMITIVE') return primitiveTypeLabel(attribute.type.targetId);
    if (attribute.type.kind === 'ENUM') return metamodel.enums.find(item => item.id === attribute.type.targetId)?.name ?? 'Enumeration';
    if (attribute.type.kind === 'DATATYPE') return metamodel.dataTypes.find(item => item.id === attribute.type.targetId)?.name ?? 'Data type';
    if (attribute.type.kind === 'CLASS') return metamodel.classes.find(item => item.id === attribute.type.targetId)?.name ?? 'Class';
    return attribute.type.externalName || attribute.type.targetId || 'External type';
}
function primitiveTypeLabel(typeId: string): string {
    const labels: Record<string,string> = { STRING:'Text', BOOLEAN:'Boolean', BYTE:'Byte', SHORT:'Short integer', INTEGER:'Integer', LONG:'Long integer', FLOAT:'Decimal number', DOUBLE:'Decimal number', DECIMAL:'Decimal number', BIG_INTEGER:'Large integer', CHARACTER:'Character', DATE:'Date', BINARY:'Binary data', OBJECT:'Object' };
    return labels[typeId] ?? typeId;
}

function createClause(source: GuidedSource, metamodel: MetaModel, contextClassId?: string, id = createValidationDesignerId('guided')): GuidedClause {
    const attributes = guidedAttributes(metamodel, contextClassId);
    const relations = guidedRelations(metamodel, contextClassId);
    const attribute = attributes[0]?.attribute;
    return {
        id,
        source,
        attributeId: attribute?.id ?? '',
        relationId: relations[0]?.id ?? '',
        classId: metamodel.classes.find(item => item.id === contextClassId)?.id ?? metamodel.classes[0]?.id ?? '',
        operator: 'EQUALS',
        value: source === 'ATTRIBUTE' ? defaultForAttribute(attribute) : 1,
        negated: false
    };
}

function guidedAttributes(metamodel: MetaModel, contextClassId?: string) {
    return contextClassId === undefined || contextClassId === '' ? [] : attributesForContext(metamodel, contextClassId);
}

function hasGuidedAttribute(metamodel: MetaModel, contextClassId: string | undefined, attributeId: string): boolean {
    return guidedAttributes(metamodel, contextClassId).some(item => item.attribute.id === attributeId);
}

function hasGuidedRelation(metamodel: MetaModel, contextClassId: string | undefined, relationId: string): boolean {
    return guidedRelations(metamodel, contextClassId).some(item => item.id === relationId);
}

function guidedRelations(metamodel: MetaModel, contextClassId?: string): MetaRelation[] {
    if (contextClassId === undefined || contextClassId === '') return [];
    const navigableOwnerIds = new Set<string>();
    const visit = (classId: string): void => {
        if (navigableOwnerIds.has(classId)) return;
        navigableOwnerIds.add(classId);
        metamodel.relations
            .filter(item => item.kind === 'INHERITANCE' && item.sourceTypeId === classId)
            .forEach(item => visit(item.targetTypeId));
    };
    visit(contextClassId);
    return metamodel.relations.filter(item => item.kind !== 'INHERITANCE' && item.kind !== 'IMPLEMENTATION' && navigableOwnerIds.has(item.sourceTypeId));
}

function defaultForAttribute(attribute?: MetaAttribute): unknown {
    return attribute === undefined ? '' : defaultLiteralValue(expressionTypeForAttribute(attribute));
}

function buildRoot(clauses: GuidedClause[], join: JoinOperator, metamodel: MetaModel): ExpressionNode {
    if (clauses.length === 0) return booleanLiteral(true);
    const nodes = clauses.map(clause => buildClause(clause, metamodel));
    return nodes.slice(1).reduce<ExpressionNode>((left, right) => ({ kind: 'BINARY', nodeId: createValidationDesignerId('node'), operator: join, left, right }), nodes[0]);
}

function buildClause(clause: GuidedClause, metamodel: MetaModel): ExpressionNode {
    let valueNode: ExpressionNode;
    let valueType: ExpressionValueType = { kind: 'INTEGER' };
    if (clause.source === 'ATTRIBUTE') {
        const attribute = metamodel.classes.flatMap(item => item.attributes).find(item => item.id === clause.attributeId);
        valueType = attribute === undefined ? { kind: 'ANY' } : expressionTypeForAttribute(attribute);
        valueNode = { kind: 'ATTRIBUTE_ACCESS', nodeId: createValidationDesignerId('node'), target: selfNode(), attributeId: clause.attributeId };
    } else if (clause.source === 'RELATION_COUNT') {
        valueNode = { kind: 'COLLECTION', nodeId: createValidationDesignerId('node'), operator: 'COUNT', source: { kind: 'RELATION_NAVIGATION', nodeId: createValidationDesignerId('node'), target: selfNode(), relationId: clause.relationId } };
    } else {
        valueNode = { kind: 'COLLECTION', nodeId: createValidationDesignerId('node'), operator: 'COUNT', source: { kind: 'CLASS_INSTANCES', nodeId: createValidationDesignerId('node'), classId: clause.classId } };
    }
    const condition: ExpressionNode = ['IS_EMPTY', 'IS_NOT_EMPTY', 'IS_NULL', 'IS_NOT_NULL'].includes(clause.operator)
        ? { kind: 'UNARY', nodeId: createValidationDesignerId('node'), operator: clause.operator as Extract<UnaryExpressionOperator, 'IS_EMPTY' | 'IS_NOT_EMPTY' | 'IS_NULL' | 'IS_NOT_NULL'>, operand: valueNode }
        : { kind: 'BINARY', nodeId: createValidationDesignerId('node'), operator: clause.operator as BinaryExpressionOperator, left: valueNode, right: { kind: 'LITERAL', nodeId: createValidationDesignerId('node'), valueType, value: clause.value } };
    return clause.negated ? { kind: 'UNARY', nodeId: createValidationDesignerId('node'), operator: 'NOT', operand: condition } : condition;
}

function parseGuidedRoot(root: ExpressionNode, metamodel: MetaModel, contextClassId?: string): { join: JoinOperator; clauses: GuidedClause[] } | null {
    const flattened = flattenJoin(root);
    const clauses = flattened.nodes.map(node => parseClause(node, metamodel, contextClassId));
    if (clauses.some(item => item === null)) return null;
    return { join: flattened.join, clauses: clauses as GuidedClause[] };
}

function flattenJoin(root: ExpressionNode): { join: JoinOperator; nodes: ExpressionNode[] } {
    if (root.kind !== 'BINARY' || (root.operator !== 'AND' && root.operator !== 'OR')) return { join: 'AND', nodes: [root] };
    const join = root.operator;
    const visit = (node: ExpressionNode): ExpressionNode[] => node.kind === 'BINARY' && node.operator === join ? [...visit(node.left), ...visit(node.right)] : [node];
    return { join, nodes: visit(root) };
}

function parseClause(node: ExpressionNode, metamodel: MetaModel, contextClassId?: string): GuidedClause | null {
    const negated = node.kind === 'UNARY' && node.operator === 'NOT';
    const candidate = negated ? node.operand : node;
    if (candidate.kind === 'UNARY' && ['IS_EMPTY', 'IS_NOT_EMPTY', 'IS_NULL', 'IS_NOT_NULL'].includes(candidate.operator)) {
        if (candidate.operand.kind === 'ATTRIBUTE_ACCESS' && candidate.operand.target.kind === 'SELF' && hasGuidedAttribute(metamodel, contextClassId, candidate.operand.attributeId)) return { id: node.nodeId, source: 'ATTRIBUTE', attributeId: candidate.operand.attributeId, relationId: '', classId: '', operator: candidate.operator as GuidedOperator, value: '', negated };
        return null;
    }
    if (candidate.kind !== 'BINARY' || !COMPARISON_OPERATORS.includes(candidate.operator) || candidate.right.kind !== 'LITERAL') return null;
    if (candidate.left.kind === 'ATTRIBUTE_ACCESS' && candidate.left.target.kind === 'SELF' && hasGuidedAttribute(metamodel, contextClassId, candidate.left.attributeId)) return { id: node.nodeId, source: 'ATTRIBUTE', attributeId: candidate.left.attributeId, relationId: '', classId: '', operator: candidate.operator, value: candidate.right.value, negated };
    if (candidate.left.kind === 'COLLECTION' && candidate.left.operator === 'COUNT') {
        if (candidate.left.source.kind === 'RELATION_NAVIGATION' && candidate.left.source.target.kind === 'SELF' && hasGuidedRelation(metamodel, contextClassId, candidate.left.source.relationId)) return { id: node.nodeId, source: 'RELATION_COUNT', attributeId: '', relationId: candidate.left.source.relationId, classId: '', operator: candidate.operator, value: candidate.right.value, negated };
        if (candidate.left.source.kind === 'CLASS_INSTANCES') return { id: node.nodeId, source: 'CLASS_COUNT', attributeId: '', relationId: '', classId: candidate.left.source.classId, operator: candidate.operator, value: candidate.right.value, negated };
    }
    return null;
}

function operatorsForAttribute(attribute?: MetaAttribute): GuidedOperator[] {
    if (attribute === undefined) return ['EQUALS', 'NOT_EQUALS'];
    const kind = expressionTypeForAttribute(attribute).kind;
    if (kind === 'STRING' || kind === 'ANY') return ['EQUALS', 'NOT_EQUALS', 'CONTAINS', 'STARTS_WITH', 'ENDS_WITH', 'MATCHES', 'IS_EMPTY', 'IS_NOT_EMPTY', 'IS_NULL', 'IS_NOT_NULL'];
    if (kind === 'BOOLEAN' || kind === 'ENUM' || kind === 'OBJECT') return ['EQUALS', 'NOT_EQUALS', 'IS_NULL', 'IS_NOT_NULL'];
    return [...COMPARISON_OPERATORS, 'IS_NULL', 'IS_NOT_NULL'];
}

function operatorText(operator: GuidedOperator): string {
    const labels: Record<GuidedOperator, string> = {
        AND: 'all conditions', OR: 'any condition', EQUALS: 'equal to', NOT_EQUALS: 'different from', GREATER_THAN: 'greater than', GREATER_OR_EQUAL: 'greater than or equal to', LESS_THAN: 'less than', LESS_OR_EQUAL: 'less than or equal to', ADD: 'plus', SUBTRACT: 'minus', MULTIPLY: 'multiplied by', DIVIDE: 'divided by', CONTAINS: 'contains', STARTS_WITH: 'starts with', ENDS_WITH: 'ends with', MATCHES: 'matches pattern', IS_EMPTY: 'empty', IS_NOT_EMPTY: 'not empty', IS_NULL: 'missing / null', IS_NOT_NULL: 'defined / not null'
    };
    return labels[operator];
}

function summary(clauses: GuidedClause[], join: JoinOperator, metamodel: MetaModel, t: (value: string) => string): string {
    return clauses.map(clause => clauseSummary(clause, metamodel, t)).join(` ${t(join === 'AND' ? 'AND' : 'OR')} `);
}

function clauseSummary(clause: GuidedClause, metamodel: MetaModel, t: (value: string) => string): string {
    let subject: string;
    if (clause.source === 'ATTRIBUTE') {
        const owner = metamodel.classes.find(item => item.attributes.some(attribute => attribute.id === clause.attributeId));
        const attribute = owner?.attributes.find(item => item.id === clause.attributeId);
        subject = `${owner?.name ?? t('Element')}.${attribute?.name ?? t('attribute')}`;
    } else if (clause.source === 'RELATION_COUNT') {
        const relation = metamodel.relations.find(item => item.id === clause.relationId);
        subject = `${t('number of elements in')} ${relation?.name || t('relation')}`;
    } else subject = `${t('number of')} ${metamodel.classes.find(item => item.id === clause.classId)?.name ?? t('class')} ${t('elements')}`;
    const direct = ['IS_EMPTY', 'IS_NOT_EMPTY', 'IS_NULL', 'IS_NOT_NULL'].includes(clause.operator) ? `${subject} ${t('is')} ${t(operatorText(clause.operator))}` : `${subject} ${t('is')} ${t(operatorText(clause.operator))} ${String(clause.value)}`;
    return clause.negated ? `${t('NOT')} (${direct})` : direct;
}

function selfNode(): ExpressionNode { return { kind: 'SELF', nodeId: createValidationDesignerId('node') }; }
function booleanLiteral(value: boolean): ExpressionNode { return { kind: 'LITERAL', nodeId: createValidationDesignerId('node'), valueType: { kind: 'BOOLEAN' }, value }; }
