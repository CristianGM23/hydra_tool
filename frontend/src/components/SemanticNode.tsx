import type { CSSProperties, ReactNode } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { Node, NodeProps } from '@xyflow/react';

import type { NodeNotationStyle } from '../types/editor';
import type { ValidationSeverity } from '../validation/types';
import { HydraNodeResizer } from '../designer-ui/HydraNodeResizer';

export interface SemanticNodeData extends Record<string, unknown> {
    label: string;
    notation: NodeNotationStyle;
    problemSeverity?: ValidationSeverity;
}

export type SemanticFlowNode = Node<SemanticNodeData, 'semantic'>;

/**
 * Representa un EObject mediante una figura redimensionable.
 *
 * @param data contiene la etiqueta y la notación.
 * @param selected indica si el nodo está seleccionado.
 * @returns nodo gráfico redimensionable.
 */
export function SemanticNode({ data, selected }: NodeProps<SemanticFlowNode>) {
    const notation = data.notation;
    const bodyStyle: CSSProperties = {
        background: notation.fillColor,
        borderColor: notation.strokeColor,
        borderStyle: notation.strokeDash === '' ? 'solid' : 'dashed',
        borderWidth: notation.strokeWidth,
        borderRadius: notation.borderRadius,
        width: '100%',
        height: '100%'
    };

    if (notation.shape === 'ROUNDED_RECTANGLE') {
        bodyStyle.borderRadius = notation.borderRadius === 0 ? 12 : notation.borderRadius;
    } else {
        const clipPath = shapeClipPath(notation.shape);
        if (clipPath !== '') bodyStyle.clipPath = clipPath;
    }

    let body = <div className="semantic-node-body" style={bodyStyle}><span>{data.label}</span></div>;
    const neutralShape = semanticShapeElement(notation);
    if (neutralShape !== null) {
        body = (
            <div className="semantic-node-body semantic-node-svg" data-shape={notation.shape}>
                <svg aria-hidden="true" preserveAspectRatio="none" viewBox="0 0 100 100">{neutralShape}</svg>
                <span>{data.label}</span>
            </div>
        );
    }

    if (notation.shape === 'SVG_PATH' && notation.svgPath !== '') {
        const viewBox = notation.svgViewBox === '' ? '0 0 100 100' : notation.svgViewBox;
        body = (
            <div className="semantic-node-body semantic-node-svg" data-shape={notation.shape}>
                <svg aria-hidden="true" preserveAspectRatio="none" viewBox={viewBox}>
                    <path d={notation.svgPath} fill={notation.fillColor} stroke={notation.strokeColor} strokeDasharray={notation.strokeDash} strokeWidth={notation.strokeWidth} />
                </svg>
                <span>{data.label}</span>
            </div>
        );
    }

    const content = (
        <>
            <HydraNodeResizer visible={selected} minHeight={notation.minHeight} minWidth={notation.minWidth} />
            <Handle id="target" position={Position.Left} type="target" />
            {body}
            {data.problemSeverity !== undefined && <span aria-label={`${data.problemSeverity.toLowerCase()} validation problem`} className={`semantic-node-problem ${data.problemSeverity.toLowerCase()}`}>!</span>}
            <Handle id="source" position={Position.Right} type="source" />
        </>
    );

    return content;
}

/**
 * Construye la geometría SVG exacta de las formas que no se representan fielmente mediante un recorte CSS.
 *
 * @param notation contiene forma, relleno, borde y patrón visual resueltos para el elemento.
 * @returns geometría SVG presentacional o `null` cuando la forma se resuelve mejor mediante CSS.
 */
function semanticShapeElement(notation: NodeNotationStyle): ReactNode {
    const common = { fill: notation.fillColor, stroke: notation.strokeColor, strokeDasharray: notation.strokeDash, strokeWidth: notation.strokeWidth, vectorEffect: 'non-scaling-stroke' as const };
    switch (notation.shape) {
        case 'ELLIPSE': return <ellipse cx="50" cy="50" rx="48" ry="48" {...common} />;
        case 'CYLINDER': return <><path d="M 4 18 C 4 7 96 7 96 18 L 96 82 C 96 94 4 94 4 82 Z" {...common} /><path d="M 4 18 C 4 30 96 30 96 18 M 4 82 C 4 70 96 70 96 82" fill="none" stroke={notation.strokeColor} strokeDasharray={notation.strokeDash} strokeWidth={notation.strokeWidth} vectorEffect="non-scaling-stroke" /></>;
        case 'DOCUMENT': return <path d="M 4 4 H 96 V 82 C 80 70 64 94 48 82 C 32 70 20 94 4 82 Z" {...common} />;
        case 'CLOUD': return <path d="M 24 82 C 8 82 4 64 14 54 C 5 32 28 20 43 31 C 55 8 88 20 86 43 C 105 49 98 82 78 82 Z" {...common} />;
        case 'STAR': return <polygon points="50,2 61,35 96,35 68,56 79,92 50,70 21,92 32,56 4,35 39,35" {...common} />;
        case 'CROSS': return <polygon points="35,2 65,2 65,35 98,35 98,65 65,65 65,98 35,98 35,65 2,65 2,35 35,35" {...common} />;
        default: return null;
    }
}

/**
 * Devuelve un contorno CSS equivalente para las figuras geométricas neutras.
 *
 * @param shape identifica la figura declarada.
 * @returns un polygon CSS o una cadena vacía cuando no se necesita recorte.
 */
function shapeClipPath(shape: NodeNotationStyle['shape']): string {
    switch (shape) {
        case 'DIAMOND': return 'polygon(50% 0%, 100% 50%, 50% 100%, 0% 50%)';
        case 'TRIANGLE': return 'polygon(50% 0%, 100% 100%, 0% 100%)';
        case 'PENTAGON': return 'polygon(50% 0%, 100% 38%, 82% 100%, 18% 100%, 0% 38%)';
        case 'HEXAGON': return 'polygon(25% 0%, 75% 0%, 100% 50%, 75% 100%, 25% 100%, 0% 50%)';
        case 'OCTAGON': return 'polygon(28% 0%, 72% 0%, 100% 28%, 100% 72%, 72% 100%, 28% 100%, 0% 72%, 0% 28%)';
        case 'PARALLELOGRAM': return 'polygon(20% 0%, 100% 0%, 80% 100%, 0% 100%)';
        case 'TRAPEZOID': return 'polygon(18% 0%, 82% 0%, 100% 100%, 0% 100%)';
        default: return '';
    }
}
