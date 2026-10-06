import { HydraButton } from '../design-system/HydraButton';
import { useState, type ReactNode } from 'react';
import type { MetaModel } from '../metamodel-designer/types';
import type {
    BinaryExpressionOperator,
    CollectionExpressionOperator,
    ExpressionDefinition,
    ExpressionNode,
    ExpressionValueKind,
    UnaryExpressionOperator
} from '../validation/types';
import { createNodeForKind, createValidationDesignerId, defaultLiteralValue } from './validationDesignerFactory';
import { FieldLabel, HelpedField } from './ValidationFieldHelp';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { allAttributes, attributesForContext } from './validationDesignerSelectors';
import { useHydraLanguage } from '../i18n';
import { GuidedConditionBuilder } from './GuidedConditionBuilder';
import './condition-authoring.css';

interface ExpressionBuilderProps {
    expression: ExpressionDefinition;
    metamodel: MetaModel;
    onChange: (expression: ExpressionDefinition) => void;
}

const nodeKinds: ExpressionNode['kind'][] = [
    'LITERAL', 'SELF', 'MODEL', 'VARIABLE', 'CLASS_INSTANCES', 'ATTRIBUTE_ACCESS', 'RELATION_NAVIGATION',
    'OPERATION_CALL', 'UNARY', 'BINARY', 'COLLECTION', 'CONDITIONAL', 'FUNCTION'
];
const unaryOperators: UnaryExpressionOperator[] = ['NOT', 'IS_NULL', 'IS_NOT_NULL', 'IS_EMPTY', 'IS_NOT_EMPTY', 'NEGATE', 'SIZE', 'TO_UPPER', 'TO_LOWER'];
const binaryOperators: BinaryExpressionOperator[] = [
    'AND', 'OR', 'EQUALS', 'NOT_EQUALS', 'GREATER_THAN', 'GREATER_OR_EQUAL', 'LESS_THAN', 'LESS_OR_EQUAL',
    'ADD', 'SUBTRACT', 'MULTIPLY', 'DIVIDE', 'CONTAINS', 'STARTS_WITH', 'ENDS_WITH', 'MATCHES'
];
const collectionOperators: CollectionExpressionOperator[] = ['EXISTS', 'FOR_ALL', 'NONE', 'IS_UNIQUE', 'SELECT', 'COUNT', 'SUM', 'MIN', 'MAX'];
const literalKinds: ExpressionValueKind[] = ['BOOLEAN', 'INTEGER', 'DECIMAL', 'STRING', 'DATE', 'DATETIME', 'ENUM', 'NULL'];

function nodeKindDescription(kind: ExpressionNode['kind']): string {
    const descriptions: Record<ExpressionNode['kind'], string> = {
        LITERAL:'Uses a fixed boolean, number, text, date, enum or null value.', SELF:'Reads the current context element.', MODEL:'Reads the complete model document.', VARIABLE:'Reads a named runtime or collection variable.',
        CLASS_INSTANCES:'Returns all instances of one metamodel class.', ATTRIBUTE_ACCESS:'Reads an attribute from another expression.', RELATION_NAVIGATION:'Navigates a metamodel relation.', OPERATION_CALL:'Invokes a side-effect-free query operation.',
        UNARY:'Applies one-value logic, null, text or size operations.', BINARY:'Compares or combines two expressions.', COLLECTION:'Evaluates, filters or aggregates a collection.', CONDITIONAL:'Selects between two values using a condition.',
        FUNCTION:'Calls an advanced function registered by the expression runtime.'
    };
    return descriptions[kind];
}

function nodeKindCategory(kind: ExpressionNode['kind']): string {
    if (['LITERAL','SELF','MODEL','VARIABLE','CLASS_INSTANCES'].includes(kind)) return 'Values and context';
    if (['ATTRIBUTE_ACCESS','RELATION_NAVIGATION','OPERATION_CALL'].includes(kind)) return 'Model navigation';
    if (['UNARY','BINARY','COLLECTION','CONDITIONAL'].includes(kind)) return 'Logic and collections';
    return 'Advanced';
}

function nodeClassDescription(kind: string): string {
    if (kind === 'CLASS') return 'Concrete class available in the current metamodel.';
    if (kind === 'ABSTRACT_CLASS') return 'Abstract class available in the current metamodel.';
    return 'Interface available in the current metamodel.';
}

function operatorDescription(operator: string): string {
    if (['AND','OR','NOT'].includes(operator)) return 'Boolean logic operation.';
    if (operator.includes('NULL')) return 'Null-safety check.';
    if (['EQUALS','NOT_EQUALS','GREATER_THAN','GREATER_OR_EQUAL','LESS_THAN','LESS_OR_EQUAL'].includes(operator)) return 'Comparison between values.';
    if (['ADD','SUBTRACT','MULTIPLY','DIVIDE','NEGATE','SUM','MIN','MAX'].includes(operator)) return 'Numeric operation.';
    if (['CONTAINS','STARTS_WITH','ENDS_WITH','MATCHES','TO_UPPER','TO_LOWER'].includes(operator)) return 'Text operation.';
    return 'Collection or value operation.';
}

function operatorCategory(operator: string): string {
    if (['AND','OR','NOT'].includes(operator)) return 'Boolean logic';
    if (operator.includes('NULL') || operator.includes('EMPTY')) return 'Null and emptiness';
    if (['EQUALS','NOT_EQUALS','GREATER_THAN','GREATER_OR_EQUAL','LESS_THAN','LESS_OR_EQUAL'].includes(operator)) return 'Comparisons';
    if (['ADD','SUBTRACT','MULTIPLY','DIVIDE','NEGATE','SUM','MIN','MAX','SIZE','COUNT'].includes(operator)) return 'Numbers and size';
    if (['CONTAINS','STARTS_WITH','ENDS_WITH','MATCHES','TO_UPPER','TO_LOWER'].includes(operator)) return 'Text';
    return 'Collections';
}

const LITERAL_KIND_DESCRIPTIONS: Partial<Record<ExpressionValueKind, string>> = {
    BOOLEAN:'True or false.', INTEGER:'Whole number.', DECIMAL:'Number with an optional fractional part.', STRING:'Text value.', DATE:'Calendar date without time.', DATETIME:'Date and time.', ENUM:'Literal from an enum type.', NULL:'Explicit absence of a value.'
};


export function ExpressionBuilder({ expression, metamodel, onChange }: ExpressionBuilderProps) {
    const { t } = useHydraLanguage();
    const expressionNameId = `expression-${safeId(expression.id)}-name`;
    const [mode, setMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [workingState, setWorkingState] = useState(() => ({ expressionId: expression.id, sourceRoot: expression.root, root: expression.root }));
    const sourceChanged = workingState.expressionId !== expression.id || workingState.sourceRoot !== expression.root;
    const workingRoot = sourceChanged ? expression.root : workingState.root;

    if (sourceChanged) {
        setWorkingState({ expressionId: expression.id, sourceRoot: expression.root, root: expression.root });
    }

    function updateRoot(root: ExpressionNode): void {
        setWorkingState({ expressionId: expression.id, sourceRoot: expression.root, root });
        onChange({ ...expression, root });
    }

    return (
        <section className="validation-expression-builder" aria-label={t("Visual expression builder")}>
            <div className="validation-expression-heading">
                <div>
                    <h4>{t("Condition")}</h4>
                    <span>{t("Build the valid state without writing OCL, EVL or Java code. The rule fails when this expression evaluates to false.")}</span>
                </div>
            </div>
            <div className="validation-condition-mode-bar">
                <div>
                    <strong>{t("Condition builder")}</strong>
                    <span>{t("Guided mode covers the common validation patterns. Advanced mode keeps full access to the expression tree.")}</span>
                </div>
                <div className="metamodel-designer-mode-tabs" role="group" aria-label={t("Condition builder mode")}>
                    <HydraButton className={mode === 'GUIDED' ? 'active' : ''} type="button" onClick={() => setMode('GUIDED')}>{t("Guided")}</HydraButton>
                    <HydraButton className={mode === 'ADVANCED' ? 'active' : ''} type="button" onClick={() => setMode('ADVANCED')}>{t("Advanced")}</HydraButton>
                </div>
            </div>
            {mode === 'GUIDED' ? (
                <GuidedConditionBuilder root={workingRoot} metamodel={metamodel} contextClassId={expression.contextClassId} onChange={updateRoot} />
            ) : (
                <div className="validation-expression-advanced-workspace">
                    <HelpedField
                        className="validation-expression-technical-name"
                        label="Expression name"
                        targetId={expressionNameId}
                        required
                        help="Internal name used to identify this reusable condition in the designer and in quick-fix preconditions."
                        example="Person age is valid"
                    >
                        <input id={expressionNameId} required value={expression.name} onChange={event => onChange({ ...expression, root: workingRoot, name: event.target.value })} />
                    </HelpedField>
                    <ExpressionNodeEditor
                        contextClassId={expression.contextClassId}
                        depth={0}
                        metamodel={metamodel}
                        node={workingRoot}
                        onChange={updateRoot}
                    />
                </div>
            )}
        </section>
    );
}

interface ExpressionNodeEditorProps {
    node: ExpressionNode;
    metamodel: MetaModel;
    contextClassId?: string;
    depth: number;
    onChange: (node: ExpressionNode) => void;
}

export function ExpressionNodeEditor({ node, metamodel, contextClassId, depth, onChange }: ExpressionNodeEditorProps) {
    const { t } = useHydraLanguage();
    const target = 'target' in node ? node.target : undefined;
    const targetClassId = target === undefined ? undefined : expressionResultClassId(target, metamodel, contextClassId);
    const attributes = node.kind === 'ATTRIBUTE_ACCESS' && targetClassId !== undefined ? attributesForContext(metamodel, targetClassId) : allAttributes(metamodel);
    const compatibleOwnerIds = targetClassId === undefined ? null : compatibleOwnerClassIds(metamodel, targetClassId);
    const relations = node.kind === 'RELATION_NAVIGATION' && compatibleOwnerIds !== null
        ? metamodel.relations.filter(relation => relation.kind !== 'INHERITANCE' && relation.kind !== 'IMPLEMENTATION' && compatibleOwnerIds.has(relation.sourceTypeId))
        : metamodel.relations;
    const queryOperations = metamodel.classes
        .filter(owner => compatibleOwnerIds === null || compatibleOwnerIds.has(owner.id))
        .flatMap(owner => owner.operations.filter(operation => operation.behaviorKind !== 'COMMAND').map(operation => ({ owner, operation })));
    const prefix = `node-${safeId(node.nodeId)}`;
    const nodeKindOptions = nodeKinds.map(kind => ({ value:kind,label:t(nodeKindLabel(kind)),description:t(nodeKindDescription(kind)),category:t(nodeKindCategory(kind)) }));
    const operatorOptions = (operators: readonly string[]) => operators.map(operator => ({ value:operator,label:t(operatorLabel(operator)),description:t(operatorDescription(operator)),category:t(operatorCategory(operator)) }));

    function literalEditor(): ReactNode {
        if (node.kind !== 'LITERAL') return <></>;
        const valueKind = node.valueType.kind;
        const valueId = `${prefix}-literal-value`;
        if (valueKind === 'BOOLEAN') {
            return (
                <HydraAdaptiveSelect id={valueId} ariaLabel="Boolean literal" required value={String(node.value)} options={[{ value:'true',label:t('True'),description:t('The condition or value is satisfied.'),category:t('Boolean values') },{ value:'false',label:t('False'),description:t('The condition or value is not satisfied.'),category:t('Boolean values') }]} onChange={value => onChange({ ...node, value: value === 'true' })} />
            );
        }
        if (valueKind === 'NULL') return <span className="validation-expression-static" id={valueId}>null</span>;
        const inputType = valueKind === 'INTEGER' || valueKind === 'DECIMAL' ? 'number' : valueKind === 'DATE' ? 'date' : valueKind === 'DATETIME' ? 'datetime-local' : 'text';
        return (
            <input
                id={valueId}
                aria-label="Literal value"
                required
                type={inputType}
                value={node.value === null || node.value === undefined ? '' : String(node.value)}
                onChange={event => {
                    let value: unknown = event.target.value;
                    if (valueKind === 'INTEGER') value = event.target.value === '' ? 0 : Number.parseInt(event.target.value, 10);
                    if (valueKind === 'DECIMAL') value = event.target.value === '' ? 0 : Number.parseFloat(event.target.value);
                    onChange({ ...node, value });
                }}
            />
        );
    }

    return (
        <div className={`validation-expression-node kind-${node.kind.toLowerCase()}`} data-depth={depth} data-node-id={node.nodeId}>
            <div className="validation-expression-node-header">
                <HelpedField
                    className="validation-expression-kind-field"
                    label="Expression element"
                    targetId={`${prefix}-kind`}
                    required
                    help="Chooses what this part of the condition does: read a value, navigate a relation, compare values, evaluate a collection or call an advanced function."
                    example="Compare or combine for age >= 0."
                    options={nodeKindOptions}
                >
                    <HydraAdaptiveSelect id={`${prefix}-kind`} ariaLabel="Expression element" required value={node.kind} options={nodeKindOptions} onChange={kind => onChange(createNodeForKind(kind as ExpressionNode['kind'], metamodel, contextClassId))} />
                </HelpedField>
            </div>

            {node.kind === 'LITERAL' && (
                <div className="validation-expression-inline-fields">
                    <HelpedField
                        label="Type"
                        targetId={`${prefix}-literal-type`}
                        required
                        help="Defines how the fixed value is interpreted and which operators can use it."
                        example="INTEGER for 18; STRING for an email suffix."
                        options={literalKinds.map(kind => ({ value:kind,label:t(operatorLabel(kind)),description:t(LITERAL_KIND_DESCRIPTIONS[kind] ?? 'Literal value type.') }))}
                    >
                        <HydraAdaptiveSelect id={`${prefix}-literal-type`} ariaLabel="Literal type" required value={node.valueType.kind} options={literalKinds.map(kind => ({ value:kind,label:t(operatorLabel(kind)),description:t(LITERAL_KIND_DESCRIPTIONS[kind] ?? 'Literal value type.'),category:t(['INTEGER','DECIMAL'].includes(kind) ? 'Numbers' : ['DATE','DATETIME'].includes(kind) ? 'Temporal values' : ['BOOLEAN','NULL'].includes(kind) ? 'Logic and absence' : 'Text and literals') }))} onChange={valueKind => { const kind = valueKind as ExpressionValueKind; onChange({ ...node, valueType: { kind }, value: defaultLiteralValue({ kind }) }); }} />
                    </HelpedField>
                    <HelpedField
                        label="Value"
                        targetId={`${prefix}-literal-value`}
                        required={node.valueType.kind !== 'NULL'}
                        help="Fixed value used by the expression. Its editor follows the selected type."
                        example={node.valueType.kind === 'BOOLEAN' ? 'true' : node.valueType.kind === 'INTEGER' ? '18' : 'ACTIVE'}
                    >
                        {literalEditor()}
                    </HelpedField>
                </div>
            )}


            {node.kind === 'VARIABLE' && (
                <HelpedField
                    label="Variable"
                    targetId={`${prefix}-variable`}
                    required
                    help="Reads a named value supplied by an operation parameter, action parameter or collection runtime context."
                    example="prefix"
                >
                    <input id={`${prefix}-variable`} required value={node.variableName} onChange={event => onChange({ ...node, variableName: event.target.value })} />
                </HelpedField>
            )}

            {node.kind === 'OPERATION_CALL' && (
                <>
                    <HelpedField
                        label="Query operation"
                        targetId={`${prefix}-operation`}
                        required
                        help="Invokes a QUERY operation declared by a class or interface. COMMAND operations are intentionally unavailable in validation expressions."
                        example="Person.displayName(prefix)"
                        options={queryOperations.map(({ owner, operation }) => ({ value:operation.id,label:`${owner.name}.${operation.name}`,description:`${t('Query with')} ${operation.parameters.length} ${t('parameter(s).')}` }))}
                    >
                        <HydraAdaptiveSelect id={`${prefix}-operation`} ariaLabel="Query operation" required value={node.operationId} placeholder="Select query operation" options={queryOperations.map(({ owner, operation }) => ({ value:operation.id,label:`${owner.name}.${operation.name}`,description:`${t('Query with')} ${operation.parameters.length} ${t('parameter(s).')}`,category:owner.name }))} onChange={operationId => { const selected = queryOperations.find(item => item.operation.id === operationId)?.operation; onChange({ ...node, operationId, arguments: selected?.parameters.map(() => createBooleanLiteralForCollection('EXISTS')) ?? [] }); }} />
                    </HelpedField>
                    <Child label="Target" required help="Expression that returns the object on which the operation is invoked." example="Current Person element">
                        <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={node.target} onChange={target => onChange({ ...node, target })} />
                    </Child>
                    {node.arguments.map((argument, index) => {
                        const operation = queryOperations.find(item => item.operation.id === node.operationId)?.operation;
                        const parameter = operation?.parameters[index];
                        return (
                            <Child key={argument.nodeId} label={parameter?.name ?? `Argument ${index + 1}`} required help="Expression passed to the operation parameter." example={parameter?.name ?? 'value'}>
                                <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={argument} onChange={updated => onChange({ ...node, arguments: node.arguments.map((item, itemIndex) => itemIndex === index ? updated : item) })} />
                            </Child>
                        );
                    })}
                </>
            )}

            {node.kind === 'CLASS_INSTANCES' && (
                <HelpedField
                    label="Class"
                    targetId={`${prefix}-class`}
                    required
                    help="Returns every model element that is an instance of the selected class. Use it for global uniqueness and collection checks."
                    example="All Person instances"
                    options={metamodel.classes.map(item => ({ value:item.id,label:item.name,description:t(nodeClassDescription(item.kind)) }))}
                >
                    <HydraAdaptiveSelect id={`${prefix}-class`} ariaLabel="Class" required value={node.classId} placeholder="Select class" options={metamodel.classes.map(item => ({ value:item.id,label:item.name,description:t(nodeClassDescription(item.kind)),category:t(item.kind === 'CLASS' ? 'Concrete classes' : item.kind === 'ABSTRACT_CLASS' ? 'Abstract classes' : 'Interfaces') }))} onChange={classId => onChange({ ...node, classId })} />
                </HelpedField>
            )}

            {node.kind === 'ATTRIBUTE_ACCESS' && (
                <>
                    <HelpedField
                        label="Attribute"
                        targetId={`${prefix}-attribute`}
                        required
                        help="Reads an attribute from the target expression. The target must evaluate to a compatible model element."
                        example="Read Person.age from the current Person."
                        options={attributes.map(({ owner, attribute }) => ({ value:attribute.id,label:`${owner.name}.${attribute.name} · ${t(attributeTypeLabel(attribute, metamodel))}`,description:`${t('Type')}: ${t(attributeTypeLabel(attribute, metamodel))} · ${t(attribute.multiplicity.lowerBound > 0 ? 'Required' : 'Optional')} · ${t('Declared by')} ${owner.name}.` }))}
                    >
                        <HydraAdaptiveSelect id={`${prefix}-attribute`} ariaLabel="Attribute" required value={node.attributeId} placeholder="Select attribute" options={attributes.map(({ owner, attribute }) => ({ value:attribute.id,label:`${owner.name}.${attribute.name} · ${t(attributeTypeLabel(attribute, metamodel))}`,description:`${t('Type')}: ${t(attributeTypeLabel(attribute, metamodel))} · ${t(attribute.multiplicity.lowerBound > 0 ? 'Required' : 'Optional')} · ${t('Declared by')} ${owner.name}.`,category:owner.name }))} onChange={attributeId => onChange({ ...node, attributeId })} />
                    </HelpedField>
                    {targetClassId !== undefined && <div className="validation-expression-context-hint"><span>{t('Context class')}</span><strong>{metamodel.classes.find(item => item.id === targetClassId)?.name ?? targetClassId}</strong><small>{attributes.length} {t('compatible attribute(s) loaded with their types')}</small></div>}
                    <Child label="Target" required help="Expression that produces the model element whose attribute will be read." example="Current element">
                        <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={node.target} onChange={target => onChange({ ...node, target })} />
                    </Child>
                </>
            )}

            {node.kind === 'RELATION_NAVIGATION' && (
                <>
                    <HelpedField
                        label="Relation"
                        targetId={`${prefix}-relation`}
                        required
                        help="Navigates from the target element to the element or collection connected through this relation."
                        example="Company.employees"
                        options={relations.map(relation => { const source = metamodel.classes.find(item => item.id === relation.sourceTypeId)?.name ?? 'Source'; const target = metamodel.classes.find(item => item.id === relation.targetTypeId)?.name ?? 'Target'; return { value:relation.id,label:`${source}.${relation.name || relation.kind.toLowerCase()} → ${target}`,description:`${t(relationKindLabel(relation.kind))} · ${t('From')} ${source} ${t('to')} ${target}.` }; })}
                    >
                        <HydraAdaptiveSelect id={`${prefix}-relation`} ariaLabel="Relation" required value={node.relationId} placeholder="Select relation" options={relations.map(relation => { const source = metamodel.classes.find(item => item.id === relation.sourceTypeId)?.name ?? 'Source'; const target = metamodel.classes.find(item => item.id === relation.targetTypeId)?.name ?? 'Target'; return { value:relation.id,label:`${source}.${relation.name || relation.kind.toLowerCase()} → ${target}`,description:`${t(relationKindLabel(relation.kind))} · ${t('From')} ${source} ${t('to')} ${target}.`,category:source }; })} onChange={relationId => onChange({ ...node, relationId })} />
                    </HelpedField>
                    <Child label="Target" required help="Expression that produces the source element from which the relation is navigated." example="Current Company element">
                        <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={node.target} onChange={target => onChange({ ...node, target })} />
                    </Child>
                </>
            )}

            {node.kind === 'UNARY' && (
                <>
                    <HelpedField
                        label="Operator"
                        targetId={`${prefix}-unary-operator`}
                        required
                        help="Applies a single-value operation such as NOT, null checking, size or text conversion."
                        example="IS_NOT_NULL applied to Person.name."
                        options={operatorOptions(unaryOperators)}
                    >
                        <HydraAdaptiveSelect id={`${prefix}-unary-operator`} ariaLabel="Operator" required value={node.operator} options={operatorOptions(unaryOperators)} onChange={operator => onChange({ ...node, operator:operator as UnaryExpressionOperator })} />
                    </HelpedField>
                    <Child label="Operand" required help="Value on which the selected unary operator is applied." example="Read attribute Person.name">
                        <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={node.operand} onChange={operand => onChange({ ...node, operand })} />
                    </Child>
                </>
            )}

            {node.kind === 'BINARY' && (
                <>
                    <HelpedField
                        label="Operator"
                        targetId={`${prefix}-binary-operator`}
                        required
                        help="Compares or combines the left and right expressions. Both sides must have types compatible with the selected operator."
                        example="GREATER_OR_EQUAL for age >= 0."
                        options={operatorOptions(binaryOperators)}
                    >
                        <HydraAdaptiveSelect id={`${prefix}-binary-operator`} ariaLabel="Operator" required value={node.operator} options={operatorOptions(binaryOperators)} onChange={operator => onChange({ ...node, operator:operator as BinaryExpressionOperator })} />
                    </HelpedField>
                    <div className="validation-expression-branches">
                        <Child label="Left" required help="First value used by the comparison or combination." example="Read attribute Person.age">
                            <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={node.left} onChange={left => onChange({ ...node, left })} />
                        </Child>
                        <Child label="Right" required help="Second value used by the comparison or combination." example="Integer value 0">
                            <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={node.right} onChange={right => onChange({ ...node, right })} />
                        </Child>
                    </div>
                </>
            )}

            {node.kind === 'COLLECTION' && (
                <>
                    <HelpedField
                        label="Collection operation"
                        targetId={`${prefix}-collection-operator`}
                        required
                        help="Evaluates or transforms a collection: check whether items exist, filter them, count them or calculate numeric aggregates."
                        example="FOR_ALL employees have a non-empty name."
                        options={operatorOptions(collectionOperators)}
                    >
                        <HydraAdaptiveSelect id={`${prefix}-collection-operator`} ariaLabel="Collection operation" required value={node.operator} options={operatorOptions(collectionOperators)} onChange={value => { const operator = value as CollectionExpressionOperator; onChange({ ...node, operator, expression: operator === 'COUNT' ? undefined : node.expression ?? createBooleanLiteralForCollection(operator) }); }} />
                    </HelpedField>
                    <Child label="Collection" required help="Expression that returns the collection to process." example="Navigate Company.employees">
                        <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={node.source} onChange={source => onChange({ ...node, source })} />
                    </Child>
                    {node.operator !== 'COUNT' && (
                        <Child
                            label={collectionExpressionLabel(node.operator)}
                            required
                            help={node.operator === 'SUM' || node.operator === 'MIN' || node.operator === 'MAX' ? 'Expression that obtains the numeric value from each item.' : node.operator === 'SELECT' ? 'Boolean expression used to retain matching items.' : 'Boolean expression evaluated for each item.'}
                            example={node.operator === 'SUM' ? 'Read employee.salary' : 'Read employee.active equals true'}
                        >
                            <ExpressionNodeEditor
                                contextClassId={contextClassId}
                                depth={depth + 1}
                                metamodel={metamodel}
                                node={node.expression ?? createBooleanLiteralForCollection(node.operator)}
                                onChange={expression => onChange({ ...node, expression })}
                            />
                        </Child>
                    )}
                </>
            )}

            {node.kind === 'CONDITIONAL' && (
                <div className="validation-expression-branches">
                    <Child label="If" required help="Boolean condition that selects which branch is returned." example="Person.age is null">
                        <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={node.condition} onChange={condition => onChange({ ...node, condition })} />
                    </Child>
                    <Child label="Then" required help="Value returned when the condition is true." example="false">
                        <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={node.whenTrue} onChange={whenTrue => onChange({ ...node, whenTrue })} />
                    </Child>
                    <Child label="Else" required help="Value returned when the condition is false." example="Person.age >= 0">
                        <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={node.whenFalse} onChange={whenFalse => onChange({ ...node, whenFalse })} />
                    </Child>
                </div>
            )}

            {node.kind === 'FUNCTION' && (
                <>
                    <HelpedField
                        label="Function name"
                        targetId={`${prefix}-function-name`}
                        required
                        help="Name of an advanced function registered by the expression runtime. Use this only when the built-in visual operators are insufficient."
                        example="isValidEmail"
                    >
                        <input id={`${prefix}-function-name`} required value={node.functionName} onChange={event => onChange({ ...node, functionName: event.target.value })} />
                    </HelpedField>
                    <div className="validation-expression-action-row">
                        <FieldLabel
                            label="Function arguments"
                            targetId={`${prefix}-add-argument`}
                            help="Ordered expressions passed to the advanced function. The function definition determines how many are required."
                            example="isValidEmail(Person.email)"
                        />
                        <HydraButton id={`${prefix}-add-argument`} type="button" onClick={() => onChange({ ...node, arguments: [...node.arguments, createBooleanLiteralForCollection('EXISTS')] })}>{t('Add argument')}</HydraButton>
                    </div>
                    {node.arguments.map((argument, index) => (
                        <Child key={argument.nodeId} label={`${t('Argument')} ${index + 1}`} required help="Expression passed in this argument position." example="Read attribute Person.email">
                            <HydraButton className="validation-inline-delete" type="button" onClick={() => onChange({ ...node, arguments: node.arguments.filter((_, itemIndex) => itemIndex !== index) })}>{t('Remove')}</HydraButton>
                            <ExpressionNodeEditor contextClassId={contextClassId} depth={depth + 1} metamodel={metamodel} node={argument} onChange={updated => onChange({ ...node, arguments: node.arguments.map((item, itemIndex) => itemIndex === index ? updated : item) })} />
                        </Child>
                    ))}
                </>
            )}

            {(node.kind === 'SELF' || node.kind === 'MODEL') && <span className="validation-expression-static">{t(node.kind === 'SELF' ? 'Current model element' : 'Complete model')}</span>}
        </div>
    );
}

function Child({ label, children, help, example, required = false }: { label: string; children: ReactNode; help: string; example: string; required?: boolean }) {
    return (
        <section className="validation-expression-child">
            <FieldLabel label={label} targetId="" help={help} example={example} required={required} />
            <div className="validation-expression-child-body">{children}</div>
        </section>
    );
}

function nodeKindLabel(kind: ExpressionNode['kind']): string {
    const labels: Record<ExpressionNode['kind'], string> = {
        LITERAL: 'Value', SELF: 'Current element', MODEL: 'Complete model', VARIABLE: 'Operation or action parameter', CLASS_INSTANCES: 'All instances of a class',
        ATTRIBUTE_ACCESS: 'Read attribute', RELATION_NAVIGATION: 'Navigate relation', OPERATION_CALL: 'Call query operation', UNARY: 'Single-value operation',
        BINARY: 'Compare or combine', COLLECTION: 'Collection operation', CONDITIONAL: 'If / then / else', FUNCTION: 'Advanced function'
    };
    return labels[kind];
}

function operatorLabel(operator: string): string {
    return operator.toLowerCase().replaceAll('_', ' ').replace(/^./, value => value.toUpperCase());
}

function collectionExpressionLabel(operator: CollectionExpressionOperator): string {
    if (operator === 'SUM' || operator === 'MIN' || operator === 'MAX') return 'Projected value';
    if (operator === 'SELECT') return 'Filter condition';
    return 'Item condition';
}

function createBooleanLiteralForCollection(operator: CollectionExpressionOperator): ExpressionNode {
    if (operator === 'SUM' || operator === 'MIN' || operator === 'MAX') {
        return { kind: 'LITERAL', nodeId: createValidationDesignerId('node'), valueType: { kind: 'INTEGER' }, value: 0 };
    }
    return { kind: 'LITERAL', nodeId: createValidationDesignerId('node'), valueType: { kind: 'BOOLEAN' }, value: true };
}

function attributeTypeLabel(attribute: import('../metamodel-designer/types').MetaAttribute, metamodel: MetaModel): string {
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

function compatibleOwnerClassIds(metamodel: MetaModel, classId: string): Set<string> {
    const compatible = new Set<string>();
    const visit = (currentId: string): void => {
        if (compatible.has(currentId)) return;
        compatible.add(currentId);
        metamodel.relations
            .filter(relation => (relation.kind === 'INHERITANCE' || relation.kind === 'IMPLEMENTATION') && relation.sourceTypeId === currentId)
            .forEach(relation => visit(relation.targetTypeId));
    };
    visit(classId);
    return compatible;
}

function expressionResultClassId(node: ExpressionNode, metamodel: MetaModel, contextClassId?: string): string | undefined {
    if (node.kind === 'SELF') return contextClassId;
    if (node.kind === 'RELATION_NAVIGATION') return metamodel.relations.find(relation => relation.id === node.relationId)?.targetTypeId;
    if (node.kind === 'ATTRIBUTE_ACCESS') {
        const attribute = metamodel.classes.flatMap(owner => owner.attributes).find(candidate => candidate.id === node.attributeId);
        return attribute?.type.kind === 'CLASS' ? attribute.type.targetId : undefined;
    }
    if (node.kind === 'OPERATION_CALL') {
        const operation = metamodel.classes.flatMap(owner => owner.operations).find(candidate => candidate.id === node.operationId);
        return operation?.returnType?.kind === 'CLASS' ? operation.returnType.targetId : undefined;
    }
    if (node.kind === 'CONDITIONAL') {
        const whenTrue = expressionResultClassId(node.whenTrue, metamodel, contextClassId);
        const whenFalse = expressionResultClassId(node.whenFalse, metamodel, contextClassId);
        return whenTrue !== undefined && whenTrue === whenFalse ? whenTrue : undefined;
    }
    return undefined;
}

function relationKindLabel(kind: import('../metamodel-designer/types').MetaRelationKind): string {
    const labels: Record<import('../metamodel-designer/types').MetaRelationKind,string> = {
        REFERENCE:'Reference', AGGREGATION:'Aggregation', CONTAINMENT:'Containment', DEPENDENCY:'Dependency', INHERITANCE:'Inheritance', IMPLEMENTATION:'Implementation'
    };
    return labels[kind];
}

function safeId(value: string): string {
    return value.replace(/[^A-Za-z0-9_-]/g, '-');
}
