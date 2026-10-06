import { HydraButton } from '../design-system/HydraButton';
import { fieldGuidance, shouldEnrichFieldHelp } from '../design-system/HydraFieldGuidance';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { EdgeStyleEditor } from '../visual-style/EdgeStyleEditor';
import { HydraAdaptiveSelect, type HydraSelectOption } from '../components/HydraAdaptiveSelect';
import { ResizableInfoTooltip } from '../designer-ui/ResizableInfoTooltip';
import { NodeStyleEditor } from '../visual-style/NodeStyleEditor';
import { DEFAULT_CLASS_NODE_STYLE, DEFAULT_DATA_TYPE_NODE_STYLE, DEFAULT_EDGE_STYLE, DEFAULT_ENUM_NODE_STYLE, resolveEdgeStyle, resolveNodeStyle } from '../visual-style/visualStyleUtils';
import type { VisualEdgeStyle, VisualNodeStyle } from '../visual-style/visualStyleTypes';
import { MetamodelConnectionGeometryEditor } from './MetamodelConnectionGeometryEditor';
import { MetamodelNodeSizeEditor } from './MetamodelNodeSizeEditor';
import { createUniqueEnumLiteral, findEnumLiteralError } from './enumUtils';
import { createMetaAttribute, createMetaModelId, createMetaOperation, createMetaParameter, createMetaRelation, externalType, manyMultiplicity, oneMultiplicity, primitiveType } from './metaModelFactory';
import { removeRelation, updateAttribute as changeAttribute, updateClass as changeClass, updateDataType as changeDataType, updateEnum as changeEnum, updateOperation as changeOperation, updateParameter as changeParameter, updateRelation as changeRelation } from './metaModelOperations';
import { collectIncomingReferences, updateReferenceEndpoint } from './referenceEndpoints';
import { classifierName, typeReferenceFromKey, typeReferenceKey, typeReferenceName } from './metaModelSelectors';
import type { MetaAttribute, MetaClass, MetaDataType, MetaEnum, MetaEnumLiteral, MetaModel, MetaOperation, MetaParameter, MetaPrimitiveType, MetaRelation, MetaRelationKind, MetamodelDiagramDocument, MetamodelSelection, Multiplicity } from './types';

interface MetamodelPropertyPanelProps {
    draft: MetaModel;
    diagram: MetamodelDiagramDocument;
    selection: MetamodelSelection;
    onChange: (model: MetaModel) => void;
    onDiagramChange: (diagram: MetamodelDiagramDocument) => void;
    onSelect: (selection: MetamodelSelection) => void;
    onValidationError: (message: string) => void;
}

const PRIMITIVES: MetaPrimitiveType[] = ['STRING', 'BOOLEAN', 'BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL', 'BIG_INTEGER', 'CHARACTER', 'DATE', 'BINARY', 'OBJECT'];

const CLASS_KIND_OPTIONS: HydraSelectOption[] = [
    { value:'CLASS', label:'Class', description:'Concrete type that can be instantiated in models.', category:'Instantiable types' },
    { value:'ABSTRACT_CLASS', label:'Abstract class', description:'Shared type that defines structure but cannot be instantiated directly.', category:'Shared structure' },
    { value:'INTERFACE', label:'Interface', description:'Behavioral contract implemented by classes; it does not define model attributes.', category:'Behavioral contracts' }
];
const OPERATION_BEHAVIOR_OPTIONS: HydraSelectOption[] = [
    { value:'UNSPECIFIED', label:'Unspecified', description:'Keeps the operation signature without declaring query or command semantics.', category:'Draft' },
    { value:'QUERY', label:'Query', description:'Reads model state and returns a value without changing the model.', category:'Read-only behavior' },
    { value:'COMMAND', label:'Command', description:'May modify model state and participates in transactions and undo.', category:'Model-changing behavior' }
];
const RELATION_KIND_OPTIONS: HydraSelectOption[] = [
    { value:'REFERENCE', label:'Reference', description:'Navigable semantic link between model elements.', category:'Structural links' },
    { value:'CONTAINMENT', label:'Containment', description:'Ownership relation that controls lifecycle and model hierarchy.', category:'Structural links' },
    { value:'AGGREGATION', label:'Aggregation', description:'Whole-part relation without exclusive lifecycle ownership.', category:'Structural links' },
    { value:'DEPENDENCY', label:'Dependency', description:'Non-structural usage or dependency between types.', category:'Behavioral links' },
    { value:'INHERITANCE', label:'Inheritance', description:'Specialization of one class by another class.', category:'Type hierarchy' },
    { value:'IMPLEMENTATION', label:'Interface implementation', description:'Declares that a class implements an interface contract.', category:'Type hierarchy' }
];
const LOWER_BOUND_OPTIONS: HydraSelectOption[] = [
    { value:'0', label:'Optional (0)', description:'The feature may have no value.', category:'Optional' },
    { value:'1', label:'Required (1)', description:'At least one value is required.', category:'Required' }
];
const UPPER_BOUND_OPTIONS: HydraSelectOption[] = [
    { value:'1', label:'Single value (1)', description:'The feature stores at most one value.', category:'Single-valued' },
    { value:'*', label:'Many values (*)', description:'The feature may store an unbounded collection of values.', category:'Multi-valued' }
];

const REFERENCE_KINDS = new Set<MetaRelationKind>(['REFERENCE', 'CONTAINMENT', 'AGGREGATION']);

export function MetamodelPropertyPanel(props: MetamodelPropertyPanelProps) {
    let content = buildModelEditor(props);
    if (props.selection.kind === 'CLASS') content = buildClassEditor(props, props.selection.id);
    else if (props.selection.kind === 'ENUM') content = buildEnumEditor(props, props.selection.id);
    else if (props.selection.kind === 'DATA_TYPE') content = buildDataTypeEditor(props, props.selection.id);
    else if (props.selection.kind === 'ATTRIBUTE') content = buildAttributeEditor(props, props.selection.ownerClassId, props.selection.id);
    else if (props.selection.kind === 'OPERATION') content = buildOperationEditor(props, props.selection.ownerClassId, props.selection.id);
    else if (props.selection.kind === 'PARAMETER') content = buildParameterEditor(props, props.selection.ownerClassId, props.selection.operationId, props.selection.id);
    else if (props.selection.kind === 'RELATION') content = buildRelationEditor(props, props.selection.id);
    return <aside className="metamodel-property-panel"><h2>Properties</h2>{content}</aside>;
}

function buildModelEditor(props: MetamodelPropertyPanelProps) {
    return <div className="metamodel-property-content">
        <h3>MetaModel</h3>
        <label>Stable id<input disabled value={props.draft.id} /></label>
        <label>Name<input value={props.draft.name} onChange={event => props.onChange({ ...props.draft, name: event.target.value })} /></label>
        <p className="metamodel-field-help">File names, Ecore namespaces and generator options are configured as projection targets and are not part of the HYDRA semantics.</p>
    </div>;
}

function buildClassEditor(props: MetamodelPropertyPanelProps, classId: string) {
    const metaClass = props.draft.classes.find(item => item.id === classId);
    if (metaClass === undefined) return <p>Selected class was not found.</p>;
    const outgoing = props.draft.relations.filter(item => item.sourceTypeId === classId);
    const incoming = collectIncomingReferences(props.draft, classId);
    return <div className="metamodel-property-content">
        <h3>Class</h3>
        <label>Name<input value={metaClass.name} onChange={event => updateClass(props, classId, { name: event.target.value })} /></label>
        <AdaptivePropertyField label="Kind" help="Choose whether this type is instantiable, abstract or an interface contract." value={metaClass.kind} options={CLASS_KIND_OPTIONS} onChange={kind => updateClass(props, classId, { kind: kind as MetaClass['kind'] })} />
        <div className="metamodel-property-section-heading"><h3>Attributes</h3><HydraButton type="button" onClick={() => addAttribute(props, classId)}>Add</HydraButton></div>
        {metaClass.attributes.length === 0 && <p className="metamodel-empty-property">No attributes.</p>}
        {metaClass.attributes.map(attribute => <HydraButton className="metamodel-property-list-item" key={attribute.id} type="button" onClick={() => props.onSelect({ kind: 'ATTRIBUTE', id: attribute.id, ownerClassId: classId })}>{attribute.name} : {typeReferenceName(props.draft, attribute.type)}</HydraButton>)}
        <div className="metamodel-property-section-heading"><h3>Operations</h3><HydraButton type="button" onClick={() => addOperation(props, classId)}>Add operation</HydraButton></div>
        {metaClass.operations.length === 0 && <p className="metamodel-empty-property">No operations.</p>}
        {metaClass.operations.map(operation => <HydraButton className="metamodel-property-list-item" key={operation.id} type="button" onClick={() => props.onSelect({ kind: 'OPERATION', id: operation.id, ownerClassId: classId })}>{operationSignature(props.draft, operation)}</HydraButton>)}
        <div className="metamodel-property-section-heading"><h3>Outgoing relations</h3><HydraButton type="button" onClick={() => addReference(props, classId)}>Add reference</HydraButton></div>
        {outgoing.length === 0 && <p className="metamodel-empty-property">No outgoing relations.</p>}
        {outgoing.map(relation => <HydraButton className="metamodel-property-list-item" key={relation.id} type="button" onClick={() => props.onSelect({ kind: 'RELATION', id: relation.id })}>{relation.kind}: {relation.name || 'unnamed'} → {classifierName(props.draft, relation.targetTypeId)}</HydraButton>)}
        <div className="metamodel-property-section-heading"><h3>Incoming references</h3></div>
        {incoming.length === 0 && <p className="metamodel-empty-property">No incoming references.</p>}
        {incoming.map(reference => <HydraButton className="metamodel-property-list-item" key={reference.referenceId} type="button" onClick={() => props.onSelect({ kind: 'RELATION', id: reference.referenceId })}>{reference.sourceClassName}.{reference.referenceName} → {metaClass.name}</HydraButton>)}
        <MetamodelNodeSizeEditor elementType={classKindLabel(metaClass)} height={readNodeSize(props, classId, 240, 130).height} minimumHeight={110} minimumWidth={190} width={readNodeSize(props, classId, 240, 130).width} onChange={(width, height) => updateNodeSize(props, classId, width, height)} />
        <NodeStyleEditor value={readNodeStyle(props, classId, 'CLASS')} onChange={style => updateNodeStyle(props, classId, style)} />
    </div>;
}

function buildAttributeEditor(props: MetamodelPropertyPanelProps, classId: string, attributeId: string) {
    const metaClass = props.draft.classes.find(item => item.id === classId);
    const attribute = metaClass?.attributes.find(item => item.id === attributeId);
    if (metaClass === undefined || attribute === undefined) return <p>Selected attribute was not found.</p>;
    return <div className="metamodel-property-content">
        <HydraButton className="metamodel-back-button" type="button" onClick={() => props.onSelect({ kind: 'CLASS', id: classId })}>Back to {metaClass.name}</HydraButton>
        <h3>Attribute</h3>
        <label>Name<input value={attribute.name} onChange={event => updateAttribute(props, classId, attributeId, { name: event.target.value })} /></label>
        <AdaptivePropertyField label="Type" help="Select a primitive, enumeration or custom datatype. Long type catalogs are grouped by semantic category." value={typeReferenceKey(attribute.type)} options={metamodelTypeOptions(props.draft, false, attribute.type)} onChange={value => updateAttribute(props, classId, attributeId, { type: typeReferenceFromKey(value) })} />
        {buildMultiplicityEditor(attribute.multiplicity, multiplicity => updateAttribute(props, classId, attributeId, { multiplicity }))}
        <label className="metamodel-checkbox"><input checked={attribute.ordered} type="checkbox" onChange={event => updateAttribute(props, classId, attributeId, { ordered: event.target.checked })} />Ordered</label>
        <label className="metamodel-checkbox"><input checked={attribute.unique} type="checkbox" onChange={event => updateAttribute(props, classId, attributeId, { unique: event.target.checked })} />Unique</label>
        <label>Default value<input value={attribute.defaultValue} onChange={event => updateAttribute(props, classId, attributeId, { defaultValue: event.target.value })} /></label>
        <HydraButton className="metamodel-delete-inline-button" type="button" onClick={() => removeAttribute(props, classId, attributeId)}>Remove attribute</HydraButton>
    </div>;
}

function buildOperationEditor(props: MetamodelPropertyPanelProps, classId: string, operationId: string) {
    const metaClass = props.draft.classes.find(item => item.id === classId);
    const operation = metaClass?.operations.find(item => item.id === operationId);
    if(metaClass === undefined || operation === undefined) return <p>Selected operation was not found.</p>;
    return <div className="metamodel-property-content">
        <HydraButton className="metamodel-back-button" type="button" onClick={() => props.onSelect({ kind: 'CLASS', id: classId })}>Back to {metaClass.name}</HydraButton>
        <h3>Operation</h3>
        <label>Name<input value={operation.name} onChange={event => updateOperation(props, classId, operationId, { name: event.target.value })} /></label>
        <AdaptivePropertyField label="Behavior" help="Declare whether the operation is read-only or may change model state." value={operation.behaviorKind} options={OPERATION_BEHAVIOR_OPTIONS} onChange={behaviorKind => updateOperation(props, classId, operationId, { behaviorKind: behaviorKind as MetaOperation['behaviorKind'] })} />
        <AdaptivePropertyField label="Return type" help="Select void, a scalar value or a metamodel type returned by this operation." value={operation.returnType === null ? 'void' : typeReferenceKey(operation.returnType)} options={[{ value:'void',label:'No return value',description:'The operation returns no value.',category:'Recommended' }, ...metamodelTypeOptions(props.draft, true, operation.returnType ?? undefined)]} onChange={value => updateOperationReturnType(props, classId, operationId, value)} />
        {operation.returnType !== null && <>{buildMultiplicityEditor(operation.returnMultiplicity ?? oneMultiplicity(), multiplicity => updateOperation(props, classId, operationId, { returnMultiplicity: multiplicity }))}<label className="metamodel-checkbox"><input checked={operation.ordered} type="checkbox" onChange={event => updateOperation(props, classId, operationId, { ordered: event.target.checked })} />Ordered result</label><label className="metamodel-checkbox"><input checked={operation.unique} type="checkbox" onChange={event => updateOperation(props, classId, operationId, { unique: event.target.checked })} />Unique result</label></>}
        <div className="metamodel-property-section-heading"><h3>Parameters</h3><HydraButton type="button" onClick={() => addParameter(props, classId, operationId)}>Add parameter</HydraButton></div>
        {operation.parameters.length === 0 && <p className="metamodel-empty-property">No parameters.</p>}
        {operation.parameters.map(parameter => <HydraButton className="metamodel-property-list-item" key={parameter.id} type="button" onClick={() => props.onSelect({ kind: 'PARAMETER', id: parameter.id, ownerClassId: classId, operationId })}>{parameter.name} : {typeReferenceName(props.draft, parameter.type)}</HydraButton>)}
        <div className="metamodel-property-section-heading"><h3>Exception types</h3><HydraButton type="button" onClick={() => addOperationException(props, classId, operationId)}>Add exception</HydraButton></div>
        {operation.exceptionTypes.length === 0 && <p className="metamodel-empty-property">No exception types.</p>}
        {operation.exceptionTypes.map((type, index) => <div className="metamodel-enum-literal-row" key={`${operation.id}-exception-${index}`}>
            <AdaptivePropertyField label={`Exception ${index + 1}`} help="Select the exception or model type declared by this operation." value={typeReferenceKey(type)} options={metamodelTypeOptions(props.draft, true, type)} onChange={value => updateOperationException(props, classId, operationId, index, typeReferenceFromKey(value))} />
            <HydraButton className="metamodel-delete-inline-button" type="button" onClick={() => removeOperationException(props, classId, operationId, index)}>Remove</HydraButton>
        </div>)}
        <HydraButton className="metamodel-delete-inline-button" type="button" onClick={() => removeOperation(props, classId, operationId)}>Remove operation</HydraButton>
    </div>;
}

function buildParameterEditor(props: MetamodelPropertyPanelProps, classId: string, operationId: string, parameterId: string) {
    const metaClass = props.draft.classes.find(item => item.id === classId);
    const operation = metaClass?.operations.find(item => item.id === operationId);
    const parameter = operation?.parameters.find(item => item.id === parameterId);
    if(metaClass === undefined || operation === undefined || parameter === undefined) return <p>Selected parameter was not found.</p>;
    return <div className="metamodel-property-content">
        <HydraButton className="metamodel-back-button" type="button" onClick={() => props.onSelect({ kind: 'OPERATION', id: operationId, ownerClassId: classId })}>Back to {operation.name}</HydraButton>
        <h3>Parameter</h3>
        <label>Name<input value={parameter.name} onChange={event => updateParameter(props, classId, operationId, parameterId, { name: event.target.value })} /></label>
        <AdaptivePropertyField label="Type" help="Select the scalar, model or external type accepted by this parameter." value={typeReferenceKey(parameter.type)} options={metamodelTypeOptions(props.draft, true, parameter.type)} onChange={value => updateParameter(props, classId, operationId, parameterId, { type: typeReferenceFromKey(value) })} />
        {buildMultiplicityEditor(parameter.multiplicity, multiplicity => updateParameter(props, classId, operationId, parameterId, { multiplicity }))}
        <label className="metamodel-checkbox"><input checked={parameter.ordered} type="checkbox" onChange={event => updateParameter(props, classId, operationId, parameterId, { ordered: event.target.checked })} />Ordered</label>
        <label className="metamodel-checkbox"><input checked={parameter.unique} type="checkbox" onChange={event => updateParameter(props, classId, operationId, parameterId, { unique: event.target.checked })} />Unique</label>
        <HydraButton className="metamodel-delete-inline-button" type="button" onClick={() => removeParameter(props, classId, operationId, parameterId)}>Remove parameter</HydraButton>
    </div>;
}

function buildRelationEditor(props: MetamodelPropertyPanelProps, relationId: string) {
    const relation = props.draft.relations.find(item => item.id === relationId);
    if (relation === undefined) return <p>Selected relation was not found.</p>;
    const referenceLike = REFERENCE_KINDS.has(relation.kind);
    return <div className="metamodel-property-content">
        <HydraButton className="metamodel-back-button" type="button" onClick={() => props.onSelect({ kind: 'CLASS', id: relation.sourceTypeId })}>Back to {classifierName(props.draft, relation.sourceTypeId)}</HydraButton>
        <h3>Relation</h3>
        <AdaptivePropertyField label="Kind" help="Select the semantic meaning of this connection." value={relation.kind} options={RELATION_KIND_OPTIONS} onChange={kind => updateRelationKind(props, relation, kind as MetaRelationKind)} />
        <label>Name<input disabled={!referenceLike && relation.kind !== 'DEPENDENCY'} value={relation.name} onChange={event => updateRelation(props, relationId, { name: event.target.value })} /></label>
        <AdaptivePropertyField label="Source" help="Class or interface from which this relation starts." value={relation.sourceTypeId} options={classOptions(props.draft)} onChange={classId => changeEndpoint(props, relation, 'SOURCE', classId)} />
        <AdaptivePropertyField label="Target" help="Class or interface reached by this relation." value={relation.targetTypeId} options={classOptions(props.draft)} onChange={classId => changeEndpoint(props, relation, 'TARGET', classId)} />
        {referenceLike && <>
            {buildMultiplicityEditor(relation.targetMultiplicity ?? manyMultiplicity(), targetMultiplicity => updateRelation(props, relationId, { targetMultiplicity }))}
            <label className="metamodel-checkbox"><input checked={relation.ordered} type="checkbox" onChange={event => updateRelation(props, relationId, { ordered: event.target.checked })} />Ordered</label>
            <label className="metamodel-checkbox"><input checked={relation.unique} type="checkbox" onChange={event => updateRelation(props, relationId, { unique: event.target.checked })} />Unique</label>
            <AdaptivePropertyField label="Opposite" help="Optional reverse relation kept synchronized with this reference." value={relation.oppositeRelationId} options={[{ value:'',label:'No opposite relation',description:'Keep this reference independent.',category:'Recommended' }, ...oppositeOptions(props.draft, relation).map(item => ({ value:item.id,label:`${classifierName(props.draft, item.sourceTypeId)}.${item.name}`,description:'Compatible reverse reference.',category:'Compatible opposites' }))]} onChange={oppositeId => updateOpposite(props, relation, oppositeId)} />
        </>}
        <MetamodelConnectionGeometryEditor diagram={props.diagram} onChange={props.onDiagramChange} relationId={relation.id} sourceClassId={relation.sourceTypeId} sourceLabel={classifierName(props.draft, relation.sourceTypeId)} targetClassId={relation.targetTypeId} targetLabel={classifierName(props.draft, relation.targetTypeId)} />
        <EdgeStyleEditor value={readEdgeStyle(props, relation.id)} onChange={style => updateEdgeStyle(props, relation.id, style)} />
        <HydraButton className="metamodel-delete-inline-button" type="button" onClick={() => { props.onChange(removeRelation(props.draft, relation.id)); props.onDiagramChange({ ...props.diagram, edges: props.diagram.edges.filter(edge => edge.relationId !== relation.id) }); props.onSelect({ kind: 'MODEL', id: props.draft.id }); }}>Remove relation</HydraButton>
    </div>;
}

function buildEnumEditor(props: MetamodelPropertyPanelProps, enumId: string) {
    const metaEnum = props.draft.enums.find(item => item.id === enumId);
    if (metaEnum === undefined) return <p>Selected enumeration was not found.</p>;
    const validationError = findEnumLiteralError(metaEnum);
    return <div className="metamodel-property-content">
        <h3>Enumeration</h3>
        <label>Name<input value={metaEnum.name} onChange={event => updateEnum(props, enumId, { name: event.target.value })} /></label>
        <div className="metamodel-property-section-heading"><h3>Literals</h3><HydraButton type="button" onClick={() => addEnumLiteral(props, enumId)}>Add literal</HydraButton></div>
        {metaEnum.literals.map(literal => <div className="metamodel-enum-literal-row" key={literal.id}>
            <label>Name<input value={literal.name} onChange={event => updateEnumLiteral(props, enumId, literal.id, { name: event.target.value })} /></label>
            <label>Value<input step={1} type="number" value={literal.value} onChange={event => updateEnumLiteral(props, enumId, literal.id, { value: Number(event.target.value) })} /></label>
            <HydraButton className="metamodel-delete-inline-button" disabled={metaEnum.literals.length === 1} type="button" onClick={() => removeEnumLiteral(props, enumId, literal.id)}>Remove</HydraButton>
        </div>)}
        {validationError !== '' && <p className="metamodel-validation-error">{validationError}</p>}
        <MetamodelNodeSizeEditor elementType="Enumeration" height={readNodeSize(props, enumId, 210, 110).height} minimumHeight={90} minimumWidth={170} width={readNodeSize(props, enumId, 210, 110).width} onChange={(width, height) => updateNodeSize(props, enumId, width, height)} />
        <NodeStyleEditor value={readNodeStyle(props, enumId, 'ENUM')} onChange={style => updateNodeStyle(props, enumId, style)} />
    </div>;
}

function buildDataTypeEditor(props: MetamodelPropertyPanelProps, dataTypeId: string) {
    const dataType = props.draft.dataTypes.find(item => item.id === dataTypeId);
    if (dataType === undefined) return <p>Selected data type was not found.</p>;
    return <div className="metamodel-property-content">
        <h3>Data type</h3>
        <label>Name<input value={dataType.name} onChange={event => updateDataType(props, dataTypeId, { name: event.target.value })} /></label>
        <p className="metamodel-field-help">Runtime implementation and serialization options belong to the selected target configuration.</p>
        <MetamodelNodeSizeEditor elementType="Data type" height={readNodeSize(props, dataTypeId, 220, 100).height} minimumHeight={90} minimumWidth={180} width={readNodeSize(props, dataTypeId, 220, 100).width} onChange={(width, height) => updateNodeSize(props, dataTypeId, width, height)} />
        <NodeStyleEditor value={readNodeStyle(props, dataTypeId, 'DATA_TYPE')} onChange={style => updateNodeStyle(props, dataTypeId, style)} />
    </div>;
}

function updateClass(props: MetamodelPropertyPanelProps, id: string, patch: Partial<MetaClass>): void { props.onChange(changeClass(props.draft, id, value => ({ ...value, ...patch }))); }
function addAttribute(props: MetamodelPropertyPanelProps, classId: string): void { props.onChange(changeClass(props.draft, classId, value => ({ ...value, attributes: [...value.attributes, createMetaAttribute(primitiveType('STRING'))] }))); }
function updateAttribute(props: MetamodelPropertyPanelProps, classId: string, id: string, patch: Partial<MetaAttribute>): void { props.onChange(changeAttribute(props.draft, classId, id, value => ({ ...value, ...patch }))); }
function removeAttribute(props: MetamodelPropertyPanelProps, classId: string, id: string): void { props.onChange(changeClass(props.draft, classId, value => ({ ...value, attributes: value.attributes.filter(item => item.id !== id) }))); props.onSelect({ kind: 'CLASS', id: classId }); }
function addOperation(props: MetamodelPropertyPanelProps, classId: string): void {
    const operation = createMetaOperation();
    props.onChange(changeClass(props.draft, classId, value => ({ ...value, operations: [...value.operations, operation] })));
    props.onSelect({ kind: 'OPERATION', id: operation.id, ownerClassId: classId });
}
function updateOperation(props: MetamodelPropertyPanelProps, classId: string, operationId: string, patch: Partial<MetaOperation>): void { props.onChange(changeOperation(props.draft, classId, operationId, value => ({ ...value, ...patch }))); }
function removeOperation(props: MetamodelPropertyPanelProps, classId: string, operationId: string): void { props.onChange(changeClass(props.draft, classId, value => ({ ...value, operations: value.operations.filter(item => item.id !== operationId) }))); props.onSelect({ kind: 'CLASS', id: classId }); }
function addParameter(props: MetamodelPropertyPanelProps, classId: string, operationId: string): void { const parameter = createMetaParameter(); props.onChange(changeOperation(props.draft, classId, operationId, value => ({ ...value, parameters: [...value.parameters, parameter] }))); props.onSelect({ kind: 'PARAMETER', id: parameter.id, ownerClassId: classId, operationId }); }
function updateParameter(props: MetamodelPropertyPanelProps, classId: string, operationId: string, parameterId: string, patch: Partial<MetaParameter>): void { props.onChange(changeParameter(props.draft, classId, operationId, parameterId, value => ({ ...value, ...patch }))); }
function removeParameter(props: MetamodelPropertyPanelProps, classId: string, operationId: string, parameterId: string): void { props.onChange(changeOperation(props.draft, classId, operationId, value => ({ ...value, parameters: value.parameters.filter(item => item.id !== parameterId) }))); props.onSelect({ kind: 'OPERATION', id: operationId, ownerClassId: classId }); }
function updateOperationReturnType(props: MetamodelPropertyPanelProps, classId: string, operationId: string, value: string): void { updateOperation(props, classId, operationId, value === 'void' ? { returnType: null, returnMultiplicity: null } : { returnType: typeReferenceFromKey(value), returnMultiplicity: oneMultiplicity() }); }
function addOperationException(props: MetamodelPropertyPanelProps, classId: string, operationId: string): void { const defaultType = props.draft.classes.length > 0 ? typeReferenceFromKey(`class:${props.draft.classes[0].id}`) : externalType('java.lang.Exception'); updateOperation(props, classId, operationId, { exceptionTypes: [...(props.draft.classes.find(item => item.id === classId)?.operations.find(item => item.id === operationId)?.exceptionTypes ?? []), defaultType] }); }
function updateOperationException(props: MetamodelPropertyPanelProps, classId: string, operationId: string, index: number, type: MetaOperation['exceptionTypes'][number]): void { props.onChange(changeOperation(props.draft, classId, operationId, value => ({ ...value, exceptionTypes: value.exceptionTypes.map((item, itemIndex) => itemIndex === index ? type : item) }))); }
function removeOperationException(props: MetamodelPropertyPanelProps, classId: string, operationId: string, index: number): void { props.onChange(changeOperation(props.draft, classId, operationId, value => ({ ...value, exceptionTypes: value.exceptionTypes.filter((_, itemIndex) => itemIndex !== index) }))); }

function addReference(props: MetamodelPropertyPanelProps, classId: string): void {
    const target = props.draft.classes.find(item => item.id !== classId) ?? props.draft.classes.find(item => item.id === classId);
    if (target === undefined) return;
    const relation = createMetaRelation(classId, target.id, 'REFERENCE', 'newReference');
    props.onChange({ ...props.draft, relations: [...props.draft.relations, relation] });
    props.onSelect({ kind: 'RELATION', id: relation.id });
}
function updateRelation(props: MetamodelPropertyPanelProps, id: string, patch: Partial<MetaRelation>): void { props.onChange(changeRelation(props.draft, id, value => ({ ...value, ...patch }))); updateDiagramEdge(props, id, relationDiagramPatch({ ...props.draft.relations.find(item => item.id === id)!, ...patch })); }
function updateRelationKind(props: MetamodelPropertyPanelProps, relation: MetaRelation, kind: MetaRelationKind): void {
    const referenceLike = REFERENCE_KINDS.has(kind);
    updateRelation(props, relation.id, {
        kind,
        name: kind === 'INHERITANCE' || kind === 'IMPLEMENTATION' ? '' : relation.name,
        sourceMultiplicity: referenceLike ? (relation.sourceMultiplicity ?? { lowerBound: 1, upperBound: 1 }) : null,
        targetMultiplicity: referenceLike ? (relation.targetMultiplicity ?? manyMultiplicity()) : null,
        oppositeRelationId: referenceLike ? relation.oppositeRelationId : ''
    });
}
function changeEndpoint(props: MetamodelPropertyPanelProps, relation: MetaRelation, endpoint: 'SOURCE' | 'TARGET', classId: string): void {
    const result = updateReferenceEndpoint(props.draft, props.diagram, relation.id, endpoint, classId);
    if (result.valid === false) { props.onValidationError(result.error); return; }
    props.onValidationError(''); props.onChange(result.model); props.onDiagramChange(result.diagram); props.onSelect(result.selection);
}
function updateOpposite(props: MetamodelPropertyPanelProps, relation: MetaRelation, oppositeId: string): void {
    let model = changeRelation(props.draft, relation.id, value => ({ ...value, oppositeRelationId: oppositeId }));
    if (relation.oppositeRelationId !== '') model = changeRelation(model, relation.oppositeRelationId, value => ({ ...value, oppositeRelationId: value.oppositeRelationId === relation.id ? '' : value.oppositeRelationId }));
    if (oppositeId !== '') model = changeRelation(model, oppositeId, value => ({ ...value, oppositeRelationId: relation.id }));
    props.onChange(model);
}
function updateEnum(props: MetamodelPropertyPanelProps, id: string, patch: Partial<MetaEnum>): void { props.onChange(changeEnum(props.draft, id, value => ({ ...value, ...patch }))); }
function addEnumLiteral(props: MetamodelPropertyPanelProps, id: string): void { props.onChange(changeEnum(props.draft, id, value => ({ ...value, literals: [...value.literals, createUniqueEnumLiteral(value, () => createMetaModelId('literal'))] }))); }
function updateEnumLiteral(props: MetamodelPropertyPanelProps, id: string, literalId: string, patch: Partial<MetaEnumLiteral>): void { props.onChange(changeEnum(props.draft, id, value => ({ ...value, literals: value.literals.map(item => item.id === literalId ? { ...item, ...patch } : item) }))); }
function removeEnumLiteral(props: MetamodelPropertyPanelProps, id: string, literalId: string): void { props.onChange(changeEnum(props.draft, id, value => ({ ...value, literals: value.literals.filter(item => item.id !== literalId) }))); }
function updateDataType(props: MetamodelPropertyPanelProps, id: string, patch: Partial<MetaDataType>): void { props.onChange(changeDataType(props.draft, id, value => ({ ...value, ...patch }))); }

function buildMultiplicityEditor(value: Multiplicity, onChange: (value: Multiplicity) => void) {
    return <div className="metamodel-bounds-grid">
        <AdaptivePropertyField label="Lower bound" help="Minimum number of values required for this feature." value={String(value.lowerBound)} options={LOWER_BOUND_OPTIONS} onChange={lower => onChange({ ...value, lowerBound: Number(lower) })} />
        <AdaptivePropertyField label="Upper bound" help="Maximum number of values accepted by this feature." value={value.upperBound === null ? '*' : String(value.upperBound)} options={UPPER_BOUND_OPTIONS} onChange={upper => onChange({ ...value, upperBound: upper === '*' ? null : Number(upper) })} />
    </div>;
}
function metamodelTypeOptions(model: MetaModel, includeClasses: boolean, current?: MetaOperation['returnType'] | MetaParameter['type']): HydraSelectOption[] {
    const options: HydraSelectOption[] = [
        ...PRIMITIVES.map(type => ({ value:`primitive:${type}`,label:readableTypeName(type),description:primitiveDescription(type),category:primitiveCategory(type) })),
        ...model.enums.map(item => ({ value:`enum:${item.id}`,label:item.name,description:'Enumeration declared in the current metamodel.',category:'Enumerations' })),
        ...model.dataTypes.map(item => ({ value:`datatype:${item.id}`,label:item.name,description:'Custom datatype declared in the current metamodel.',category:'Custom datatypes' }))
    ];
    if (includeClasses) options.push(...classOptions(model).map(item => ({ ...item, value:`class:${item.value}` })));
    if (current?.kind === 'EXTERNAL') options.push({ value:`external:${current.externalName}`,label:`External: ${current.externalName}`,description:'External type preserved by the neutral metamodel.',category:'External types' });
    return options;
}
function classOptions(model: MetaModel): HydraSelectOption[] { return model.classes.map(item => ({ value:item.id,label:item.name,description:`${item.kind.toLowerCase().replaceAll('_',' ')} declared in the current metamodel.`,category:item.kind === 'CLASS' ? 'Concrete classes' : item.kind === 'ABSTRACT_CLASS' ? 'Abstract classes' : 'Interfaces' })); }
function readableTypeName(value: string): string { return value.toLowerCase().split('_').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join(' '); }
function primitiveCategory(value: MetaPrimitiveType): string { if (['BYTE','SHORT','INTEGER','LONG','FLOAT','DOUBLE','DECIMAL','BIG_INTEGER'].includes(value)) return 'Numbers'; if (['DATE','BINARY','OBJECT'].includes(value)) return 'Special values'; return 'Basic values'; }
function primitiveDescription(value: MetaPrimitiveType): string { const descriptions: Partial<Record<MetaPrimitiveType,string>> = { STRING:'Text value.', BOOLEAN:'True or false.', BYTE:'8-bit integer.', SHORT:'16-bit integer.', INTEGER:'32-bit integer.', LONG:'64-bit integer.', FLOAT:'Single-precision number.', DOUBLE:'Double-precision number.', DECIMAL:'Arbitrary-precision decimal.', BIG_INTEGER:'Arbitrary-size integer.', CHARACTER:'Single character.', DATE:'Calendar date.', BINARY:'Binary content.', OBJECT:'Generic external object.' }; return descriptions[value] ?? 'Primitive value.'; }
function AdaptivePropertyField({ label, help, value, options, onChange }: { label:string; help:string; value:string; options:HydraSelectOption[]; onChange:(value:string)=>void }) { const { t, language } = useHydraLanguage(); const guidance = shouldEnrichFieldHelp(help) ? fieldGuidance(label, help, language, true) : null; return <div className="metamodel-adaptive-property-field"><span className="metamodel-adaptive-property-label"><span>{t(label)}</span><ResizableInfoTooltip label={`${t('Information about')} ${t(label)}`} title={t(label)}><p>{t(help)}</p>{guidance !== null && <section className="hydra-ui-field-guidance"><div><strong>{t('How to choose')}</strong><p>{guidance.choose}</p></div><div><strong>{t('What changes')}</strong><p>{guidance.impact}</p></div><div><strong>{t('Use the current context')}</strong><p>{guidance.options}</p></div></section>}</ResizableInfoTooltip></span><HydraAdaptiveSelect ariaLabel={label} value={value} options={options} onChange={onChange} /></div>; }
function operationSignature(model: MetaModel, operation: MetaOperation): string { const parameters = operation.parameters.map(parameter => `${parameter.name}: ${typeReferenceName(model, parameter.type)}`).join(', '); const result = operation.returnType === null ? 'void' : typeReferenceName(model, operation.returnType); return `${operation.name}(${parameters}) : ${result}`; }
function oppositeOptions(model: MetaModel, relation: MetaRelation): MetaRelation[] { return model.relations.filter(item => item.id !== relation.id && REFERENCE_KINDS.has(item.kind) && item.sourceTypeId === relation.targetTypeId && item.targetTypeId === relation.sourceTypeId); }
function classKindLabel(value: MetaClass): string { return value.kind === 'INTERFACE' ? 'Interface' : value.kind === 'ABSTRACT_CLASS' ? 'Abstract class' : 'Class'; }

function readNodeSize(props: MetamodelPropertyPanelProps, elementId: string, fallbackWidth: number, fallbackHeight: number): { width: number; height: number } { const node = props.diagram.nodes.find(item => item.elementId === elementId); return { width: node?.width ?? fallbackWidth, height: node?.height ?? fallbackHeight }; }
function updateNodeSize(props: MetamodelPropertyPanelProps, elementId: string, width: number, height: number): void { const existing = props.diagram.nodes.find(item => item.elementId === elementId); props.onDiagramChange(existing ? { ...props.diagram, nodes: props.diagram.nodes.map(item => item.elementId === elementId ? { ...item, width, height } : item) } : { ...props.diagram, nodes: [...props.diagram.nodes, { ...createFallbackDiagramNode(props.draft, elementId, readNodeStyle(props, elementId, nodeKind(props.draft, elementId))), width, height }] }); }
function nodeKind(model: MetaModel, id: string): 'CLASS' | 'ENUM' | 'DATA_TYPE' { return model.enums.some(item => item.id === id) ? 'ENUM' : model.dataTypes.some(item => item.id === id) ? 'DATA_TYPE' : 'CLASS'; }
function readNodeStyle(props: MetamodelPropertyPanelProps, id: string, kind: 'CLASS' | 'ENUM' | 'DATA_TYPE'): VisualNodeStyle { const stored = props.diagram.nodes.find(item => item.elementId === id)?.style; return resolveNodeStyle(stored, kind === 'ENUM' ? DEFAULT_ENUM_NODE_STYLE : kind === 'DATA_TYPE' ? DEFAULT_DATA_TYPE_NODE_STYLE : DEFAULT_CLASS_NODE_STYLE); }
function updateNodeStyle(props: MetamodelPropertyPanelProps, id: string, style: VisualNodeStyle): void { const existing = props.diagram.nodes.find(item => item.elementId === id); props.onDiagramChange(existing ? { ...props.diagram, nodes: props.diagram.nodes.map(item => item.elementId === id ? { ...item, style } : item) } : { ...props.diagram, nodes: [...props.diagram.nodes, createFallbackDiagramNode(props.draft, id, style)] }); }
function createFallbackDiagramNode(model: MetaModel, id: string, style: VisualNodeStyle): MetamodelDiagramDocument['nodes'][number] { const ids = [...model.classes, ...model.enums, ...model.dataTypes].map(item => item.id); const index = Math.max(0, ids.indexOf(id)); const kind = nodeKind(model, id); return { elementId: id, x: 80 + (index % 3) * 310, y: 70 + Math.floor(index / 3) * 230, width: kind === 'ENUM' ? 210 : kind === 'DATA_TYPE' ? 220 : 240, height: kind === 'ENUM' ? 110 : kind === 'DATA_TYPE' ? 100 : 130, style }; }
function readEdgeStyle(props: MetamodelPropertyPanelProps, id: string): VisualEdgeStyle { return resolveEdgeStyle(props.diagram.edges.find(item => item.relationId === id)?.style ?? DEFAULT_EDGE_STYLE); }
function updateEdgeStyle(props: MetamodelPropertyPanelProps, id: string, style: VisualEdgeStyle): void { updateDiagramEdge(props, id, { style }); }
function relationDiagramPatch(relation: MetaRelation): Partial<MetamodelDiagramDocument['edges'][number]> { return { kind: relation.kind, sourceClassId: relation.sourceTypeId, targetClassId: relation.targetTypeId, label: relation.name }; }
function updateDiagramEdge(props: MetamodelPropertyPanelProps, id: string, patch: Partial<MetamodelDiagramDocument['edges'][number]>): void { const existing = props.diagram.edges.find(item => item.relationId === id); const base = { relationId: id, sourceHandle: 'right-25-source', targetHandle: 'left-75-target', routePoints: [] }; props.onDiagramChange({ ...props.diagram, edges: existing ? props.diagram.edges.map(item => item.relationId === id ? { ...item, ...patch } : item) : [...props.diagram.edges, { ...base, ...patch }] }); }
