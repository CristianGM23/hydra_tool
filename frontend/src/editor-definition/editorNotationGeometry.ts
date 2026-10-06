import type { NodeMapping } from './types';

export interface GeometryPoint{x:number;y:number;}
export interface GeometryRect{x:number;y:number;width:number;height:number;}

export function shapePolygon(shape:NodeMapping['shape']):GeometryPoint[]|null{
    switch(shape){
        case 'TRIANGLE':return [{x:.5,y:0},{x:1,y:1},{x:0,y:1}];
        case 'DIAMOND':return [{x:.5,y:0},{x:1,y:.5},{x:.5,y:1},{x:0,y:.5}];
        case 'PENTAGON':return [{x:.5,y:0},{x:1,y:.38},{x:.82,y:1},{x:.18,y:1},{x:0,y:.38}];
        case 'HEXAGON':return [{x:.25,y:0},{x:.75,y:0},{x:1,y:.5},{x:.75,y:1},{x:.25,y:1},{x:0,y:.5}];
        case 'OCTAGON':return [{x:.28,y:0},{x:.72,y:0},{x:1,y:.28},{x:1,y:.72},{x:.72,y:1},{x:.28,y:1},{x:0,y:.72},{x:0,y:.28}];
        case 'PARALLELOGRAM':return [{x:.2,y:0},{x:1,y:0},{x:.8,y:1},{x:0,y:1}];
        case 'TRAPEZOID':return [{x:.18,y:0},{x:.82,y:0},{x:1,y:1},{x:0,y:1}];
        case 'STAR':return starPoints(5,.5,.48,.22);
        case 'CROSS':return [{x:.35,y:0},{x:.65,y:0},{x:.65,y:.35},{x:1,y:.35},{x:1,y:.65},{x:.65,y:.65},{x:.65,y:1},{x:.35,y:1},{x:.35,y:.65},{x:0,y:.65},{x:0,y:.35},{x:.35,y:.35}];
        default:return null;
    }
}

export function pointInsideShape(shape:NodeMapping['shape'],point:GeometryPoint,width:number,height:number,padding=0):boolean{
    const x=point.x/Math.max(1,width),y=point.y/Math.max(1,height),px=padding/Math.max(1,width),py=padding/Math.max(1,height);
    if(x<px||y<py||x>1-px||y>1-py)return false;
    if(shape==='ELLIPSE'||shape==='CLOUD'||shape==='CYLINDER'){
        const rx=Math.max(.01,.5-px),ry=Math.max(.01,.5-py);return ((x-.5)**2)/(rx**2)+((y-.5)**2)/(ry**2)<=1;
    }
    const polygon=shapePolygon(shape);return polygon===null||pointInPolygon({x,y},polygon);
}

export function constrainChildToShape(rect:GeometryRect,parentWidth:number,parentHeight:number,padding:number,shape:NodeMapping['shape']):GeometryRect{
    const topPadding=Math.max(padding,padding+24);
    const availableWidth=Math.max(24,parentWidth-padding*2);
    const availableHeight=Math.max(24,parentHeight-topPadding-padding);
    let width=Math.min(rect.width,availableWidth),height=Math.min(rect.height,availableHeight);
    let x=Math.max(padding,Math.min(rect.x,Math.max(padding,parentWidth-width-padding)));
    let y=Math.max(topPadding,Math.min(rect.y,Math.max(topPadding,parentHeight-height-padding)));
    const fits=()=>corners({x,y,width,height}).every(point=>pointInsideShape(shape,point,parentWidth,parentHeight,padding));
    if(fits())return {x,y,width,height};
    const centerX=parentWidth/2,centerY=(topPadding+parentHeight-padding)/2;
    for(let scale=1;scale>=.25;scale-=.025){
        width=Math.min(rect.width,availableWidth)*scale;height=Math.min(rect.height,availableHeight)*scale;
        x=centerX-width/2;y=centerY-height/2;
        if(fits())return {x,y,width,height};
    }
    return {x:centerX-width/2,y:centerY-height/2,width,height};
}

function corners(rect:GeometryRect):GeometryPoint[]{return [{x:rect.x,y:rect.y},{x:rect.x+rect.width,y:rect.y},{x:rect.x,y:rect.y+rect.height},{x:rect.x+rect.width,y:rect.y+rect.height}];}
function pointInPolygon(point:GeometryPoint,polygon:GeometryPoint[]):boolean{let inside=false;for(let i=0,j=polygon.length-1;i<polygon.length;j=i++){const a=polygon[i],b=polygon[j];if(((a.y>point.y)!==(b.y>point.y))&&point.x<(b.x-a.x)*(point.y-a.y)/(b.y-a.y||1e-9)+a.x)inside=!inside;}return inside;}
function starPoints(spikes:number,center:number,outer:number,inner:number):GeometryPoint[]{const result:GeometryPoint[]=[];for(let i=0;i<spikes*2;i++){const radius=i%2===0?outer:inner,angle=-Math.PI/2+i*Math.PI/spikes;result.push({x:center+Math.cos(angle)*radius,y:center+Math.sin(angle)*radius});}return result;}
