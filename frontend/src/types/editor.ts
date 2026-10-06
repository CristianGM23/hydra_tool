/**
 * Representa un resumen de un metamodelo.
 */
export interface MetamodelSummary {
    id: string;
    fileName: string;
    rootPackageCount: number;
}

/**
 * Representa una clase disponible en la paleta.
 */
export interface PaletteEntry {
    classId: string;
    packageName: string;
    className: string;
}

/**
 * Describe un atributo Ecore.
 */
export interface AttributeDescriptor {
    name: string;
    many: boolean;
    changeable: boolean;
    derived: boolean;
}

/**
 * Describe una referencia Ecore.
 */
export interface ReferenceDescriptor {
    name: string;
    targetClassId: string;
    containment: boolean;
    many: boolean;
}

/**
 * Describe una EClass.
 */
export interface ClassDescriptor {
    id: string;
    name: string;
    instantiable: boolean;
    superTypeIds: string[];
    attributes: AttributeDescriptor[];
    references: ReferenceDescriptor[];
}

/**
 * Describe un paquete Ecore.
 */
export interface PackageDescriptor {
    name: string;
    classes: ClassDescriptor[];
    subpackages: PackageDescriptor[];
}

/**
 * Describe un metamodelo completo.
 */
export interface MetamodelDescriptor {
    sourceUri: string;
    packages: PackageDescriptor[];
}

/**
 * Representa la información inicial del editor.
 */
export interface EditorBootstrap {
    metamodelId: string;
    fileName: string;
    palette: PaletteEntry[];
    descriptor: MetamodelDescriptor;
}

/**
 * Representa un EObject.
 */
export interface ModelElement {
    id: string;
    classId: string;
    className: string;
    containerId: string | null;
    containmentReference: string | null;
    attributes: Record<string, unknown>;
}

/**
 * Representa una referencia semántica.
 */
export interface ModelReference {
    sourceId: string;
    referenceName: string;
    targetId: string;
}

/**
 * Representa un archivo XMI almacenado.
 */
export interface SavedModelSummary {
    fileName: string;
    size: number;
}

/**
 * Representa un documento semántico.
 */
export interface ModelDocument {
    id: string;
    metamodelId: string;
    fileName: string;
    rootElementId: string;
    dirty: boolean;
    elements: ModelElement[];
    references: ModelReference[];
}

/**
 * Define las formas disponibles para los nodos.
 */
export type NodeShape = 'RECTANGLE' | 'ROUNDED_RECTANGLE' | 'ELLIPSE' | 'DIAMOND' | 'TRIANGLE' | 'PENTAGON' | 'HEXAGON' | 'OCTAGON' | 'PARALLELOGRAM' | 'TRAPEZOID' | 'CYLINDER' | 'DOCUMENT' | 'CLOUD' | 'STAR' | 'CROSS' | 'SVG_PATH';

/**
 * Define los trazados disponibles para las conexiones.
 */
export type EdgeLineType = 'BEZIER' | 'STRAIGHT' | 'STEP' | 'SMOOTH_STEP';

/**
 * Define los extremos disponibles para las conexiones.
 */
export type EdgeMarkerType = 'NONE' | 'ARROW_OPEN' | 'ARROW_CLOSED' | 'TRIANGLE_OPEN' | 'DIAMOND_OPEN' | 'DIAMOND_CLOSED' | 'CIRCLE_OPEN' | 'CIRCLE_CLOSED' | 'SQUARE_OPEN' | 'SQUARE_CLOSED';

/**
 * Describe una representación de nodo.
 */
export interface NodeNotationStyle {
    id: string;
    classId: string;
    representationName: string;
    shape: NodeShape;
    defaultWidth: number;
    defaultHeight: number;
    minWidth: number;
    minHeight: number;
    fillColor: string;
    strokeColor: string;
    strokeWidth: number;
    strokeDash: string;
    borderRadius: number;
    labelAttribute: string;
    svgViewBox: string;
    svgPath: string;
}

/**
 * Describe una representación de conexión.
 */
export interface EdgeNotationStyle {
    id: string;
    sourceClassId: string;
    referenceName: string;
    targetClassId: string;
    lineType: EdgeLineType;
    strokeColor: string;
    strokeWidth: number;
    strokeDash: string;
    startMarker: EdgeMarkerType;
    endMarker: EdgeMarkerType;
    animated: boolean;
}

/**
 * Describe una notación completa.
 */
export interface NotationDefinition {
    id: string;
    metamodelId: string;
    name: string;
    nodeStyles: NodeNotationStyle[];
    edgeStyles: EdgeNotationStyle[];
}

/**
 * Representa una notación en los selectores.
 */
export interface NotationSummary {
    id: string;
    metamodelId: string;
    name: string;
    nodeStyleCount: number;
    edgeStyleCount: number;
}

/**
 * Representa la posición, tamaño y representación de un nodo.
 */
export interface DiagramNodeView {
    elementId: string;
    nodeStyleId: string;
    x: number;
    y: number;
    width: number;
    height: number;
}

/**
 * Representa el estilo elegido para una conexión.
 */
export interface DiagramEdgeView {
    sourceId: string;
    referenceName: string;
    targetId: string;
    edgeStyleId: string;
}

/**
 * Representa el estado visual completo de un modelo.
 */
export interface DiagramDocument {
    formatVersion: number;
    modelFileName: string;
    notationId: string;
    nodes: DiagramNodeView[];
    edges: DiagramEdgeView[];
}