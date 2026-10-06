import {
    Position,
    getBezierPath,
    getSmoothStepPath,
    getStraightPath
} from '@xyflow/react';
import type { EdgeProps } from '@xyflow/react';
import { Children, isValidElement, type CSSProperties, type ReactNode } from 'react';
import { normalizeHydraConnectionVisual } from './HydraConnectionVisual';
import { HydraConnectionTerminatorShape } from './HydraConnectionTerminatorShape';
import './hydra-directed-edge.css';

const TARGET_ROTATION: Record<Position, number> = {
    [Position.Left]: 0,
    [Position.Right]: 180,
    [Position.Top]: 90,
    [Position.Bottom]: -90
};
const SOURCE_ROTATION: Record<Position, number> = {
    [Position.Left]: 180,
    [Position.Right]: 0,
    [Position.Top]: -90,
    [Position.Bottom]: 90
};

/** Shared high-fidelity edge used by Events, Event Flow and Gamification. */
export function HydraDirectedEdge(props: EdgeProps) {
    const sourcePosition = props.sourcePosition ?? Position.Right;
    const targetPosition = props.targetPosition ?? Position.Left;
    const coordinates = safeCoordinates(props);
    const visual = normalizeHydraConnectionVisual(props.data);
    const pathResult = visual.routeStyle === 'STRAIGHT'
        ? getStraightPath(coordinates)
        : visual.routeStyle === 'SMOOTH'
            ? getBezierPath({ ...coordinates, sourcePosition, targetPosition })
            : getSmoothStepPath({ ...coordinates, sourcePosition, targetPosition, borderRadius: 12, offset: 24 });
    const [path, labelX, labelY] = pathResult;
    const selectedColor = '#1d4ed8';
    const inheritedColor = typeof props.style?.stroke === 'string' ? props.style.stroke : '#284b70';
    const color = visual.lineStyle === 'NONE' ? 'transparent' : props.selected ? selectedColor : visual.lineColor || inheritedColor;
    const inheritedWidth = typeof props.style?.strokeWidth === 'number' ? props.style.strokeWidth : Number(props.style?.strokeWidth ?? 0);
    const configuredWidth = visual.lineWidth > 0 ? visual.lineWidth : Number.isFinite(inheritedWidth) ? inheritedWidth : 0;
    const strokeWidth = Math.max(props.selected ? 3.4 : .5, configuredWidth || 3);
    const dash = dashArray(visual.lineStyle);
    const borderWidth = visual.lineBorderWidth;
    const borderColor = visual.lineBorderColor || 'rgba(255,255,255,.96)';
    const pathStyle: CSSProperties = {
        ...props.style, stroke: color, strokeWidth, fill: 'none', vectorEffect: 'non-scaling-stroke', opacity:visual.lineOpacity,
        strokeDasharray: dash, strokeLinecap: visual.lineStyle === 'DOTTED' ? 'round' : undefined
    };
    const haloStyle: CSSProperties = {
        stroke: visual.lineStyle === 'NONE' ? 'transparent' : borderColor,
        strokeWidth: strokeWidth + Math.max(borderWidth * 2, props.selected ? 4.5 : 0), fill: 'none', vectorEffect: 'non-scaling-stroke', opacity:visual.lineOpacity,
        strokeDasharray: dash, strokeLinecap: visual.lineStyle === 'DOTTED' ? 'round' : undefined
    };

    return <g className={`hydra-directed-edge${props.selected ? ' selected' : ''}`} data-hydra-directed-edge={props.id}>
        <path aria-hidden="true" className="hydra-directed-edge-halo" d={path} style={haloStyle} />
        <path aria-hidden="true" className="react-flow__edge-path hydra-directed-edge-visible-path" d={path} data-hydra-directed-edge-path={props.id} style={pathStyle} />
        <path aria-hidden="true" className="react-flow__edge-interaction hydra-directed-edge-interaction" d={path} fill="none" stroke="transparent" strokeWidth={Math.max(28, props.interactionWidth ?? 0)} />
        <HydraConnectionTerminatorShape
            type={visual.sourceTerminator} x={coordinates.sourceX} y={coordinates.sourceY} rotation={SOURCE_ROTATION[sourcePosition]}
            lineColor={color} fillColor={visual.sourceTerminatorFillColor} borderColor={visual.sourceTerminatorBorderColor}
            borderWidth={visual.sourceTerminatorBorderWidth || Math.max(1.25, strokeWidth * .55)} size={visual.sourceTerminatorSize} selected={props.selected === true} side="source"
        />
        <HydraConnectionTerminatorShape
            type={visual.targetTerminator} x={coordinates.targetX} y={coordinates.targetY} rotation={TARGET_ROTATION[targetPosition]} edgeId={props.id}
            lineColor={color} fillColor={visual.targetTerminatorFillColor} borderColor={visual.targetTerminatorBorderColor}
            borderWidth={visual.targetTerminatorBorderWidth || Math.max(1.25, strokeWidth * .55)} size={visual.targetTerminatorSize} selected={props.selected === true} side="target"
        />
        {visual.connectionNote !== '' && <title>{visual.connectionNote}</title>}
        {hasVisibleLabel(props.label) && <HydraDirectedEdgeLabel
            backgroundBorderRadius={props.labelBgBorderRadius} backgroundPadding={props.labelBgPadding} backgroundStyle={props.labelBgStyle}
            label={props.label} showBackground={props.labelShowBg ?? true} style={props.labelStyle} x={labelX} y={labelY}
        />}
    </g>;
}

interface HydraDirectedEdgeLabelProps { x:number; y:number; label:ReactNode; style?:CSSProperties; backgroundStyle?:CSSProperties; backgroundPadding?:[number,number]; backgroundBorderRadius?:number; showBackground:boolean; }
function HydraDirectedEdgeLabel({x,y,label,style,backgroundStyle,backgroundPadding=[8,4],backgroundBorderRadius=4,showBackground}:HydraDirectedEdgeLabelProps) {
    const text=labelText(label); if(text==='')return null;
    const horizontalPadding=finitePositive(backgroundPadding[0],8),verticalPadding=finitePositive(backgroundPadding[1],4);
    const textWidth=Math.min(260,Math.max(24,[...text].length*7.2)),textHeight=16,width=textWidth+horizontalPadding*2,height=textHeight+verticalPadding*2;
    const left=x-width/2,top=y-height/2;
    const textStyle:CSSProperties={fill:'#1f2937',fontSize:12,fontWeight:600,pointerEvents:'none',userSelect:'none',...style};
    const boxStyle:CSSProperties={fill:'rgba(255,255,255,.96)',stroke:'rgba(40,75,112,.28)',strokeWidth:1,vectorEffect:'non-scaling-stroke',...backgroundStyle};
    return <g aria-hidden="true" className="hydra-directed-edge-label" data-hydra-edge-label={text}>{showBackground&&<rect height={height} rx={Math.max(0,backgroundBorderRadius)} ry={Math.max(0,backgroundBorderRadius)} style={boxStyle} width={width} x={left} y={top}/>}<text dominantBaseline="middle" style={textStyle} textAnchor="middle" x={x} y={y}>{text}</text></g>;
}
function hasVisibleLabel(label:ReactNode):boolean{return labelText(label)!=='';}
function labelText(label:ReactNode):string{if(typeof label==='string'||typeof label==='number')return String(label).trim();if(Array.isArray(label))return Children.toArray(label).map(labelText).filter(Boolean).join(' ');if(isValidElement<{children?:ReactNode}>(label))return labelText(label.props.children);return '';}
function finitePositive(value:number,fallback:number):number{return Number.isFinite(value)&&value>=0?value:fallback;}
function safeCoordinates(props:EdgeProps):{sourceX:number;sourceY:number;targetX:number;targetY:number}{const sourceX=finiteCoordinate(props.sourceX,0),sourceY=finiteCoordinate(props.sourceY,0),targetX=finiteCoordinate(props.targetX,sourceX+80),targetY=finiteCoordinate(props.targetY,sourceY);return{sourceX,sourceY,targetX,targetY};}
function finiteCoordinate(value:number,fallback:number):number{return Number.isFinite(value)?value:fallback;}
function dashArray(style:string):string|undefined{if(style==='DASHED')return '9 6';if(style==='DOTTED')return '2 5';if(style==='LONG_DASH')return '16 7';if(style==='DASH_DOT')return '12 5 2 5';if(style==='NONE')return '0 10000';return undefined;}
