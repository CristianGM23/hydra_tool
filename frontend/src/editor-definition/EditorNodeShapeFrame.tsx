import type { ReactNode } from 'react';
import type { EditorNodeNotationStyle } from './editorNotationStyle';
import { strokeDasharray } from './editorNotationStyle';
import type { NodeMapping } from './types';

interface Props {
    shape: NodeMapping['shape'];
    notation: EditorNodeNotationStyle;
    className?: string;
}

/**
 * Dibuja la geometría neutral de un nodo con la misma semántica visual que utiliza el editor de modelos.
 * Entrada: forma seleccionada, estilo resuelto y una clase CSS opcional para adaptar la superficie anfitriona.
 * Salida: un SVG puramente presentacional que conserva relleno, borde y geometría entre previsualización y runtime.
 */
export function EditorNodeShapeFrame({ shape, notation, className = '' }: Props) {
    if (shape === 'PROJECT_IMAGE') return null;
    const common = {
        fill: notation.fillColor,
        stroke: notation.strokeColor,
        strokeWidth: notation.strokeWidth,
        strokeDasharray: strokeDasharray(notation.borderStyle),
        vectorEffect: 'non-scaling-stroke' as const
    };
    let element: ReactNode;
    switch (shape) {
        case 'RECTANGLE': element = <rect x="2" y="2" width="96" height="96" {...common}/>; break;
        case 'ROUNDED_RECTANGLE': element = <rect x="2" y="2" width="96" height="96" rx={Math.min(48, notation.borderRadius)} {...common}/>; break;
        case 'ELLIPSE': element = <ellipse cx="50" cy="50" rx="48" ry="48" {...common}/>; break;
        case 'DIAMOND': element = <polygon points="50,2 98,50 50,98 2,50" {...common}/>; break;
        case 'TRIANGLE': element = <polygon points="50,2 98,98 2,98" {...common}/>; break;
        case 'PENTAGON': element = <polygon points="50,2 98,38 82,98 18,98 2,38" {...common}/>; break;
        case 'HEXAGON': element = <polygon points="25,2 75,2 98,50 75,98 25,98 2,50" {...common}/>; break;
        case 'OCTAGON': element = <polygon points="28,2 72,2 98,28 98,72 72,98 28,98 2,72 2,28" {...common}/>; break;
        case 'PARALLELOGRAM': element = <polygon points="20,2 98,2 80,98 2,98" {...common}/>; break;
        case 'TRAPEZOID': element = <polygon points="18,2 82,2 98,98 2,98" {...common}/>; break;
        case 'CYLINDER': element = <><path d="M 4 18 C 4 7 96 7 96 18 L 96 82 C 96 94 4 94 4 82 Z" {...common}/><path d="M 4 18 C 4 30 96 30 96 18 M 4 82 C 4 70 96 70 96 82" fill="none" stroke={notation.strokeColor} strokeWidth={notation.strokeWidth} strokeDasharray={strokeDasharray(notation.borderStyle)} vectorEffect="non-scaling-stroke"/></>; break;
        case 'DOCUMENT': element = <path d="M 4 4 H 96 V 82 C 80 70 64 94 48 82 C 32 70 20 94 4 82 Z" {...common}/>; break;
        case 'CLOUD': element = <path d="M 24 82 C 8 82 4 64 14 54 C 5 32 28 20 43 31 C 55 8 88 20 86 43 C 105 49 98 82 78 82 Z" {...common}/>; break;
        case 'STAR': element = <polygon points="50,2 61,35 96,35 68,56 79,92 50,70 21,92 32,56 4,35 39,35" {...common}/>; break;
        case 'CROSS': element = <polygon points="35,2 65,2 65,35 98,35 98,65 65,65 65,98 35,98 35,65 2,65 2,35 35,35" {...common}/>; break;
        case 'SVG_PATH': element = <path d={notation.svgPath || 'M 4 4 H 96 V 96 H 4 Z'} {...common}/>; break;
        default: element = <rect x="2" y="2" width="96" height="96" {...common}/>;
    }
    return <svg className={`editor-node-shape-frame${className === '' ? '' : ` ${className}`}`} data-shape={shape} viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">{element}</svg>;
}
