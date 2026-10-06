import type { CSSProperties } from 'react';
import type { HydraConnectionTerminator, HydraConnectionVisualData, HydraConnectionRouteStyle } from '../designer-ui/HydraConnectionVisual';
import { HYDRA_CONNECTION_TERMINATORS } from '../designer-ui/HydraConnectionVisual';
import type { EdgeMapping, EditorBorderStyle, EditorNodeLabelPosition, NodeMapping } from './types';

export interface EditorNodeNotationStyle {
    fillColor:string;
    strokeColor:string;
    strokeWidth:number;
    borderStyle:EditorBorderStyle;
    borderRadius:number;
    opacity:number;
    labelColor:string;
    labelBackgroundColor:string;
    labelBackgroundTransparent:boolean;
    labelPosition:EditorNodeLabelPosition;
    labelFontSize:number;
    labelFontWeight:number;
    labelPadding:number;
    labelVisible:boolean;
    svgPath:string;
}

export type EditorEdgeLinePattern='SOLID'|'DASHED'|'DOTTED'|'LONG_DASH'|'DASH_DOT'|'NONE';

export interface EditorEdgeNotationStyle {
    strokeColor:string;
    strokeWidth:number;
    strokeOpacity:number;
    strokeBorderColor:string;
    strokeBorderWidth:number;
    borderStyle:EditorEdgeLinePattern;
    labelColor:string;
    labelBackgroundColor:string;
    labelBackgroundTransparent:boolean;
    labelFontSize:number;
    labelFontWeight:number;
    labelPadding:number;
    labelVisible:boolean;
    sourceDecoration:HydraConnectionTerminator;
    targetDecoration:HydraConnectionTerminator;
    sourceDecorationFillColor:string;
    sourceDecorationBorderColor:string;
    sourceDecorationBorderWidth:number;
    sourceDecorationSize:number;
    targetDecorationFillColor:string;
    targetDecorationBorderColor:string;
    targetDecorationBorderWidth:number;
    targetDecorationSize:number;
}

export const DEFAULT_NODE_NOTATION_STYLE:EditorNodeNotationStyle={
    fillColor:'#FFFFFF',strokeColor:'#475569',strokeWidth:2,borderStyle:'SOLID',borderRadius:12,opacity:1,
    labelColor:'#0F172A',labelBackgroundColor:'#FFFFFF',labelBackgroundTransparent:false,labelPosition:'CENTER',labelFontSize:14,labelFontWeight:600,
    labelPadding:4,labelVisible:true,svgPath:''
};
export const DEFAULT_EDGE_NOTATION_STYLE:EditorEdgeNotationStyle={
    strokeColor:'#334155',strokeWidth:2,strokeOpacity:1,strokeBorderColor:'#FFFFFF',strokeBorderWidth:0,borderStyle:'SOLID',labelColor:'#334155',labelBackgroundColor:'#FFFFFF',labelBackgroundTransparent:false,
    labelFontSize:12,labelFontWeight:600,labelPadding:3,labelVisible:true,sourceDecoration:'NONE',targetDecoration:'ARROW_CLOSED',
    sourceDecorationFillColor:'#FFFFFF',sourceDecorationBorderColor:'#334155',sourceDecorationBorderWidth:1.5,sourceDecorationSize:1,
    targetDecorationFillColor:'#334155',targetDecorationBorderColor:'#334155',targetDecorationBorderWidth:1.5,targetDecorationSize:1
};

export function resolveNodeNotationStyle(styles:Record<string,string>|undefined):EditorNodeNotationStyle{
    const value=styles??{};
    return {
        fillColor:color(value.fillColor,DEFAULT_NODE_NOTATION_STYLE.fillColor),strokeColor:color(value.strokeColor,DEFAULT_NODE_NOTATION_STYLE.strokeColor),
        strokeWidth:positive(value.strokeWidth,DEFAULT_NODE_NOTATION_STYLE.strokeWidth),borderStyle:border(value.borderStyle,DEFAULT_NODE_NOTATION_STYLE.borderStyle),
        borderRadius:nonNegative(value.borderRadius,DEFAULT_NODE_NOTATION_STYLE.borderRadius),opacity:range(value.opacity,DEFAULT_NODE_NOTATION_STYLE.opacity,0.1,1),
        labelColor:color(value.labelColor,DEFAULT_NODE_NOTATION_STYLE.labelColor),labelBackgroundColor:color(value.labelBackgroundColor,DEFAULT_NODE_NOTATION_STYLE.labelBackgroundColor),
        labelBackgroundTransparent:booleanValue(value.labelBackgroundTransparent,false),labelPosition:labelPosition(value.labelPosition),labelFontSize:positive(value.labelFontSize,DEFAULT_NODE_NOTATION_STYLE.labelFontSize),
        labelFontWeight:positive(value.labelFontWeight,DEFAULT_NODE_NOTATION_STYLE.labelFontWeight),labelPadding:nonNegative(value.labelPadding,DEFAULT_NODE_NOTATION_STYLE.labelPadding),
        labelVisible:booleanValue(value.labelVisible,true),svgPath:(value.svgPath??'').trim()
    };
}

export function resolveEdgeNotationStyle(styles:Record<string,string>|undefined):EditorEdgeNotationStyle{
    const value=styles??{};
    const borderStyle=edgePattern(value.borderStyle);
    const strokeColor=color(value.strokeColor,DEFAULT_EDGE_NOTATION_STYLE.strokeColor);
    const sourceDecoration=decoration(value.sourceDecoration,'NONE');
    const targetDecoration=decoration(value.targetDecoration,'ARROW_CLOSED');
    return {
        strokeColor,strokeWidth:positive(value.strokeWidth,DEFAULT_EDGE_NOTATION_STYLE.strokeWidth),strokeOpacity:range(value.strokeOpacity,1,.1,1),
        strokeBorderColor:color(value.strokeBorderColor,DEFAULT_EDGE_NOTATION_STYLE.strokeBorderColor),strokeBorderWidth:range(value.strokeBorderWidth,0,0,8),borderStyle,
        labelColor:color(value.labelColor,DEFAULT_EDGE_NOTATION_STYLE.labelColor),labelBackgroundColor:color(value.labelBackgroundColor,DEFAULT_EDGE_NOTATION_STYLE.labelBackgroundColor),
        labelBackgroundTransparent:booleanValue(value.labelBackgroundTransparent,false),labelFontSize:positive(value.labelFontSize,DEFAULT_EDGE_NOTATION_STYLE.labelFontSize),labelFontWeight:positive(value.labelFontWeight,DEFAULT_EDGE_NOTATION_STYLE.labelFontWeight),
        labelPadding:nonNegative(value.labelPadding,DEFAULT_EDGE_NOTATION_STYLE.labelPadding),labelVisible:booleanValue(value.labelVisible,true),
        sourceDecoration,targetDecoration,
        sourceDecorationFillColor:color(value.sourceDecorationFillColor,markerDefaultFill(sourceDecoration,strokeColor)),
        sourceDecorationBorderColor:color(value.sourceDecorationBorderColor,strokeColor),
        sourceDecorationBorderWidth:range(value.sourceDecorationBorderWidth,DEFAULT_EDGE_NOTATION_STYLE.sourceDecorationBorderWidth,0,8),
        sourceDecorationSize:range(value.sourceDecorationSize,1,.5,2.5),
        targetDecorationFillColor:color(value.targetDecorationFillColor,markerDefaultFill(targetDecoration,strokeColor)),
        targetDecorationBorderColor:color(value.targetDecorationBorderColor,strokeColor),
        targetDecorationBorderWidth:range(value.targetDecorationBorderWidth,DEFAULT_EDGE_NOTATION_STYLE.targetDecorationBorderWidth,0,8),
        targetDecorationSize:range(value.targetDecorationSize,1,.5,2.5)
    };
}


/** Converts editor-definition edge styles to the shared neutral preview format. */
export function editorEdgeStyleToHydraVisual(mapping: Pick<EdgeMapping,'lineType'>, styles:Record<string,string>): HydraConnectionVisualData {
    const resolved=resolveEdgeNotationStyle(styles);
    return {
        routeStyle:edgeRoute(styles.routeStyle,mapping.lineType),lineStyle:resolved.borderStyle,lineColor:resolved.strokeColor,lineWidth:resolved.strokeWidth,
        lineOpacity:resolved.strokeOpacity,lineBorderColor:resolved.strokeBorderColor,lineBorderWidth:resolved.strokeBorderWidth,
        sourceTerminator:resolved.sourceDecoration,targetTerminator:resolved.targetDecoration,
        sourceTerminatorFillColor:resolved.sourceDecorationFillColor,sourceTerminatorBorderColor:resolved.sourceDecorationBorderColor,
        sourceTerminatorBorderWidth:resolved.sourceDecorationBorderWidth,sourceTerminatorSize:resolved.sourceDecorationSize,
        targetTerminatorFillColor:resolved.targetDecorationFillColor,targetTerminatorBorderColor:resolved.targetDecorationBorderColor,
        targetTerminatorBorderWidth:resolved.targetDecorationBorderWidth,targetTerminatorSize:resolved.targetDecorationSize
    };
}

export function updateNodeStyle(mapping:NodeMapping,key:keyof EditorNodeNotationStyle,value:string|number|boolean):NodeMapping{
    return {...mapping,styles:{...mapping.styles,[key]:String(value)}};
}

export function nodeFrameCss(style:EditorNodeNotationStyle):CSSProperties{return {opacity:style.opacity};}
export function labelCss(style:Pick<EditorNodeNotationStyle,'labelColor'|'labelBackgroundColor'|'labelBackgroundTransparent'|'labelFontSize'|'labelFontWeight'|'labelPadding'>):CSSProperties{
    return {color:style.labelColor,backgroundColor:style.labelBackgroundTransparent?'transparent':style.labelBackgroundColor,fontSize:style.labelFontSize,fontWeight:style.labelFontWeight,padding:style.labelPadding};
}
export function strokeDasharray(style:EditorBorderStyle):string|undefined{
    if(style==='DASHED')return '9 6';if(style==='DOTTED')return '2 5';if(style==='DOUBLE')return '12 3 2 3';if(style==='NONE')return '0 10000';return undefined;
}

function markerDefaultFill(marker:HydraConnectionTerminator,stroke:string):string{return marker.endsWith('_CLOSED')||marker==='BAR'||marker==='DOUBLE_BAR'||marker.startsWith('CROW_FOOT')?stroke:'#FFFFFF';}
function color(value:string|undefined,fallback:string):string{return /^#[0-9A-Fa-f]{6}$/.test(value??'')?(value??fallback).toUpperCase():fallback;}
function positive(value:string|undefined,fallback:number):number{const parsed=Number(value);return Number.isFinite(parsed)&&parsed>0?parsed:fallback;}
function nonNegative(value:string|undefined,fallback:number):number{const parsed=Number(value);return Number.isFinite(parsed)&&parsed>=0?parsed:fallback;}
function range(value:string|number|undefined,fallback:number,min:number,max:number):number{const parsed=Number(value);return Number.isFinite(parsed)?Math.max(min,Math.min(max,parsed)):fallback;}
function booleanValue(value:string|undefined,fallback:boolean):boolean{return value===undefined?fallback:value==='true';}
function edgePattern(value:string|undefined):EditorEdgeLinePattern{return value==='DASHED'||value==='DOTTED'||value==='LONG_DASH'||value==='DASH_DOT'||value==='NONE'||value==='SOLID'?value:'SOLID';}
function border(value:string|undefined,fallback:EditorBorderStyle):EditorBorderStyle{return value==='DASHED'||value==='DOTTED'||value==='DOUBLE'||value==='NONE'||value==='SOLID'?value:fallback;}
function labelPosition(value:string|undefined):EditorNodeLabelPosition{return value==='TOP'||value==='BOTTOM'||value==='OUTSIDE_TOP'||value==='OUTSIDE_BOTTOM'||value==='CENTER'?value:'CENTER';}

function edgeRoute(value:string|undefined,lineType:EdgeMapping['lineType']):HydraConnectionRouteStyle{
    if(value==='STRAIGHT'||value==='SMOOTH'||value==='ORTHOGONAL')return value;
    if(lineType==='STRAIGHT')return 'STRAIGHT';
    if(lineType==='BEZIER')return 'SMOOTH';
    return 'ORTHOGONAL';
}
function decoration(value:string|undefined,fallback:HydraConnectionTerminator):HydraConnectionTerminator{
    if(value==='ARROW')return 'ARROW_CLOSED';if(value==='CIRCLE')return 'CIRCLE_OPEN';if(value==='DIAMOND')return 'DIAMOND_OPEN';
    return typeof value==='string'&&(HYDRA_CONNECTION_TERMINATORS as readonly string[]).includes(value)?value as HydraConnectionTerminator:fallback;
}
