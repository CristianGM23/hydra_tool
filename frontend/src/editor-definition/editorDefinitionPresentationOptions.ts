/**
 * Opciones visuales de alto nivel utilizadas por la definición de editores.
 *
 * Se mantienen fuera de los componentes React para que puedan reutilizarse sin
 * acoplar la lógica de presentación al ciclo de renderizado.
 */
export const REPRESENTATION_KIND_OPTIONS = [
    { value: 'DIAGRAM', label: 'Diagram', description: 'Graphical nodes and connections on a canvas.' },
    { value: 'TREE', label: 'Tree', description: 'Hierarchical navigation through contained or related elements.' },
    { value: 'TABLE', label: 'Table', description: 'Rows and columns for comparing many model elements.' },
    { value: 'FORM', label: 'Form', description: 'Field-based editing of one model element.' },
    { value: 'MATRIX', label: 'Matrix', description: 'Two-dimensional comparison or relationship view.' },
    { value: 'DASHBOARD', label: 'Dashboard', description: 'Combined read-only or interactive summary widgets.' }
] as const;
export const NODE_SHAPE_OPTIONS = [
    { value: 'RECTANGLE', label: 'Rectangle', description: 'Standard rectangular card.' },
    { value: 'ROUNDED_RECTANGLE', label: 'Rounded rectangle', description: 'Rectangular card with rounded corners.' },
    { value: 'ELLIPSE', label: 'Ellipse', description: 'Oval node suited to states or events.' },
    { value: 'DIAMOND', label: 'Diamond', description: 'Decision-oriented diamond node.' },
    { value: 'TRIANGLE', label: 'Triangle', description: 'Three-sided marker for starts, warnings or hierarchy.' },
    { value: 'PENTAGON', label: 'Pentagon', description: 'Five-sided geometric node.' },
    { value: 'HEXAGON', label: 'Hexagon', description: 'Six-sided node for specialized concepts.' },
    { value: 'OCTAGON', label: 'Octagon', description: 'Eight-sided node for stops or guarded concepts.' },
    { value: 'PARALLELOGRAM', label: 'Parallelogram', description: 'Slanted figure for input and output concepts.' },
    { value: 'TRAPEZOID', label: 'Trapezoid', description: 'Trapezoidal process or stage node.' },
    { value: 'CYLINDER', label: 'Cylinder', description: 'Database or persistent store figure.' },
    { value: 'DOCUMENT', label: 'Document', description: 'Document-shaped node with a folded lower contour.' },
    { value: 'CLOUD', label: 'Cloud', description: 'Cloud-shaped node for external or distributed services.' },
    { value: 'STAR', label: 'Star', description: 'Star-shaped node for achievements or highlighted elements.' },
    { value: 'CROSS', label: 'Cross', description: 'Cross-shaped node for junctions or special markers.' },
    { value: 'SVG_PATH', label: 'Custom SVG shape', description: 'Shape supplied by a portable SVG path.' },
    { value: 'PROJECT_IMAGE', label: 'Project image', description: 'SVG, PNG, JPEG, GIF or WebP image stored in the active HYDRA project.' },
] as const;
export const EDGE_LINE_OPTIONS = [
    { value: 'BEZIER', label: 'Curved line', description: 'Smooth curved connection.' },
    { value: 'STRAIGHT', label: 'Straight line', description: 'Direct segment between source and target.' },
    { value: 'STEP', label: 'Stepped line', description: 'Right-angle connection with sharp turns.' },
    { value: 'SMOOTH_STEP', label: 'Smooth stepped line', description: 'Right-angle connection with rounded turns.' },
    { value: 'ORTHOGONAL', label: 'Orthogonal route', description: 'Obstacle-aware horizontal and vertical routing.' }
] as const;
