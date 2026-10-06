import { cloneElement, isValidElement, type ReactElement, type ReactNode } from 'react';
import type { HydraConnectionTerminator } from './HydraConnectionVisual';

interface Props {
    type: HydraConnectionTerminator;
    x: number;
    y: number;
    rotation: number;
    lineColor: string;
    fillColor: string;
    borderColor: string;
    borderWidth: number;
    size: number;
    selected: boolean;
    side: 'source' | 'target';
    edgeId?: string;
}


type ShapeRenderer = (style:MarkerStyle) => ReactNode;
interface MarkerStyle { stroke:string; fill:string; width:number; open:boolean; }

const POLYGONS: Partial<Record<HydraConnectionTerminator,string>> = {
    TRIANGLE_OPEN:'-16,-9 1,0 -16,9', TRIANGLE_CLOSED:'-16,-9 1,0 -16,9',
    DIAMOND_OPEN:'-18,0 -9,-8 0,0 -9,8', DIAMOND_CLOSED:'-18,0 -9,-8 0,0 -9,8',
    PENTAGON_OPEN:'-17,0 -13,-8 -4,-7 0,0 -7,8', PENTAGON_CLOSED:'-17,0 -13,-8 -4,-7 0,0 -7,8',
    HEXAGON_OPEN:'-16,0 -12,-7 -4,-7 0,0 -4,7 -12,7', HEXAGON_CLOSED:'-16,0 -12,-7 -4,-7 0,0 -4,7 -12,7',
    OCTAGON_OPEN:'-17,-4 -12,-9 -5,-9 0,-4 0,4 -5,9 -12,9 -17,4', OCTAGON_CLOSED:'-17,-4 -12,-9 -5,-9 0,-4 0,4 -5,9 -12,9 -17,4',
    CROSS_OPEN:'-16,-4 -10,-4 -10,-10 -4,-10 -4,-4 2,-4 2,4 -4,4 -4,10 -10,10 -10,4 -16,4',
    CROSS_CLOSED:'-16,-4 -10,-4 -10,-10 -4,-10 -4,-4 2,-4 2,4 -4,4 -4,10 -10,10 -10,4 -16,4'
};

const RENDERERS: Partial<Record<HydraConnectionTerminator,ShapeRenderer>> = {
    ARROW_OPEN:({stroke,width})=><polyline points="-15,-8 1,0 -15,8" fill="none" stroke={stroke} strokeWidth={width} vectorEffect="non-scaling-stroke"/>,
    ARROW_CLOSED:style=><polygon points="-17,-9 1,0 -17,9 -11,0" {...svgStyle(style)}/>,
    DOUBLE_ARROW_OPEN:({stroke,width})=><g fill="none" stroke={stroke} strokeWidth={width} vectorEffect="non-scaling-stroke"><polyline points="-15,-8 1,0 -15,8"/><polyline points="-24,-8 -8,0 -24,8"/></g>,
    DOUBLE_ARROW_CLOSED:style=><g><polygon points="-17,-9 1,0 -17,9 -11,0" {...svgStyle(style)}/><polygon points="-27,-9 -9,0 -27,9 -21,0" {...svgStyle(style)}/></g>,
    CIRCLE_OPEN:style=><circle cx="-7" cy="0" r="7" {...svgStyle(style)}/>, CIRCLE_CLOSED:style=><circle cx="-7" cy="0" r="7" {...svgStyle(style)}/>,
    SQUARE_OPEN:style=><rect x="-15" y="-7" width="14" height="14" rx="1" {...svgStyle(style)}/>, SQUARE_CLOSED:style=><rect x="-15" y="-7" width="14" height="14" rx="1" {...svgStyle(style)}/>,
    BAR:({stroke,width})=><line x1="-2" y1="-9" x2="-2" y2="9" stroke={stroke} strokeWidth={width+1} vectorEffect="non-scaling-stroke"/>,
    DOUBLE_BAR:({stroke,width})=><g fill="none" stroke={stroke} strokeWidth={width} vectorEffect="non-scaling-stroke"><line x1="-3" y1="-9" x2="-3" y2="9"/><line x1="-8" y1="-9" x2="-8" y2="9"/></g>,
    CROW_FOOT:style=><CrowFoot style={style}/>,
    CROW_FOOT_OPTIONAL:style=><g><circle cx="-18" cy="0" r="4.5" fill="transparent" stroke={style.stroke} strokeWidth={style.width}/><CrowFoot style={style}/></g>,
    CROW_FOOT_REQUIRED:style=><g><line x1="-18" y1="-8" x2="-18" y2="8" stroke={style.stroke} strokeWidth={style.width}/><CrowFoot style={style}/></g>
};

/** SVG endpoint rendering isolated behind a registry so new neutral marker kinds do not change canvas orchestration. */
export function HydraConnectionTerminatorShape({type,x,y,rotation,lineColor,fillColor,borderColor,borderWidth,size,selected,side,edgeId}:Props) {
    if(type==='NONE') return null;
    const style:MarkerStyle={stroke:borderColor||lineColor,fill:fillColor||lineColor,width:borderWidth,open:type.endsWith('_OPEN')};
    const polygon=POLYGONS[type];
    const shape=polygon!==undefined?<polygon points={polygon} {...svgStyle(style)}/>:RENDERERS[type]?.(style) ?? null;
    const annotatedShape = side === 'target' && edgeId !== undefined && (type === 'ARROW_CLOSED' || type === 'ARROW_OPEN') && isValidElement(shape)
        ? cloneElement(shape as ReactElement<Record<string, unknown>>, { 'data-hydra-directed-edge-arrow': edgeId })
        : shape;
    return <g aria-hidden="true" className={`hydra-directed-edge-terminator ${side}${selected?' selected':''}`} data-hydra-terminator={`${side}:${type}`} transform={`translate(${x} ${y}) rotate(${rotation}) scale(${size})`}>{annotatedShape}</g>;
}

function svgStyle(style:MarkerStyle) { return {fill:style.open?'transparent':style.fill,stroke:style.stroke,strokeWidth:style.width,vectorEffect:'non-scaling-stroke' as const}; }
function CrowFoot({style}:{style:MarkerStyle}) { return <g fill="none" stroke={style.stroke} strokeWidth={style.width} vectorEffect="non-scaling-stroke"><line x1="0" y1="0" x2="-13" y2="-9"/><line x1="0" y1="0" x2="-13" y2="0"/><line x1="0" y1="0" x2="-13" y2="9"/></g>; }
