/**
 * Define las propiedades de texto reutilizables por nodos y relaciones.
 */
export interface VisualTextStyle {
    textColor: string;
    fontFamily: string;
    fontSize: number;
    fontWeight: number;
}

/**
 * Define la personalización visual de una figura.
 */
export interface VisualNodeStyle extends VisualTextStyle {
    fillColor: string;
    headerColor: string;
    strokeColor: string;
    strokeWidth: number;
    borderRadius: number;
}

/**
 * Define la personalización visual de una relación.
 */
export interface VisualEdgeStyle extends VisualTextStyle {
    strokeColor: string;
    strokeWidth: number;
    lineStyle: 'AUTO' | 'SOLID' | 'DASHED' | 'DOTTED';
}
