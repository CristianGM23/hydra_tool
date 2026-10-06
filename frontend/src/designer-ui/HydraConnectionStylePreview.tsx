import type { CSSProperties } from 'react';
import { normalizeHydraConnectionVisual } from './HydraConnectionVisual';
import type { HydraConnectionVisualData } from './HydraConnectionVisual';
import { HydraConnectionTerminatorShape } from './HydraConnectionTerminatorShape';
import { useHydraLanguage } from '../i18n/useHydraLanguage';

interface Props {
    value: Partial<HydraConnectionVisualData>;
    label?: string;
    className?: string;
}

/** Lightweight preview shared by static and conditional connection editors. */
export function HydraConnectionStylePreview({ value, label = 'Connection preview', className = '' }: Props) {
    const { t } = useHydraLanguage();
    const visual = normalizeHydraConnectionVisual(value);
    const x1 = 42, y1 = 48, x2 = 278, y2 = 48;
    const path = previewPath(visual.routeStyle, x1, y1, x2, y2);
    const dash = dashArray(visual.lineStyle);
    const lineColor = visual.lineStyle === 'NONE' ? 'transparent' : visual.lineColor || '#334155';
    const lineWidth = visual.lineWidth > 0 ? visual.lineWidth : 2;
    const borderWidth = Math.max(0, visual.lineBorderWidth);
    const common: CSSProperties = { fill:'none', opacity:visual.lineOpacity, strokeDasharray:dash, strokeLinecap:visual.lineStyle === 'DOTTED' ? 'round' : undefined };

    return <figure className={`hydra-connection-style-preview${className === '' ? '' : ` ${className}`}`} aria-label={t(label)}>
        <svg viewBox="0 0 320 96" role="img" aria-label={t(label)}>
            <path d={path} style={{ ...common, stroke:visual.lineBorderColor || '#FFFFFF', strokeWidth:lineWidth + borderWidth * 2 }} vectorEffect="non-scaling-stroke" />
            <path d={path} style={{ ...common, stroke:lineColor, strokeWidth:lineWidth }} vectorEffect="non-scaling-stroke" />
            <HydraConnectionTerminatorShape type={visual.sourceTerminator} x={x1} y={y1} rotation={180} lineColor={lineColor} fillColor={visual.sourceTerminatorFillColor} borderColor={visual.sourceTerminatorBorderColor} borderWidth={visual.sourceTerminatorBorderWidth || Math.max(1, lineWidth * .55)} size={visual.sourceTerminatorSize} selected={false} side="source" />
            <HydraConnectionTerminatorShape type={visual.targetTerminator} x={x2} y={y2} rotation={0} lineColor={lineColor} fillColor={visual.targetTerminatorFillColor} borderColor={visual.targetTerminatorBorderColor} borderWidth={visual.targetTerminatorBorderWidth || Math.max(1, lineWidth * .55)} size={visual.targetTerminatorSize} selected={false} side="target" />
            <circle cx={x1} cy={y1} r="3" className="hydra-connection-preview-anchor" />
            <circle cx={x2} cy={y2} r="3" className="hydra-connection-preview-anchor" />
        </svg>
        <figcaption><span>{t('Source')}</span><strong>{t(label)}</strong><span>{t('Target')}</span></figcaption>
    </figure>;
}

function previewPath(route: 'ORTHOGONAL'|'SMOOTH'|'STRAIGHT', x1:number, y1:number, x2:number, y2:number):string {
    if (route === 'STRAIGHT') return `M ${x1} ${y1} L ${x2} ${y2}`;
    if (route === 'SMOOTH') return `M ${x1} ${y1} C ${x1 + 70} 14 ${x2 - 70} 82 ${x2} ${y2}`;
    const middle = Math.round((x1 + x2) / 2);
    return `M ${x1} ${y1} H ${middle} V 28 H ${x2} V ${y2}`;
}

function dashArray(style: string):string|undefined {
    if(style==='DASHED')return '9 6';
    if(style==='DOTTED')return '2 5';
    if(style==='LONG_DASH')return '16 7';
    if(style==='DASH_DOT')return '12 5 2 5';
    if(style==='NONE')return '0 10000';
    return undefined;
}
