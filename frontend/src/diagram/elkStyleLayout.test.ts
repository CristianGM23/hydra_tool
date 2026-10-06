import { describe, expect, it } from 'vitest';
import { layoutHydraElkStyle } from './elkStyleLayout';

function overlap(a:{x:number;y:number;width:number;height:number},b:{x:number;y:number;width:number;height:number}):boolean{
    return a.x < b.x+b.width && a.x+a.width>b.x && a.y<b.y+b.height && a.y+a.height>b.y;
}



function orthogonalSegments(source:{x:number;y:number;width:number;height:number},target:{x:number;y:number;width:number;height:number}){
    const x1=source.x+source.width,y1=source.y+source.height/2,x2=target.x,y2=target.y+target.height/2,middle=(x1+x2)/2;
    return [{x1,y1,x2:middle,y2:y1},{x1:middle,y1,x2:middle,y2},{x1:middle,y1:y2,x2,y2}];
}
function segmentCrossesNode(segment:{x1:number;y1:number;x2:number;y2:number},node:{x:number;y:number;width:number;height:number},clearance=8):boolean{
    const left=node.x-clearance,right=node.x+node.width+clearance,top=node.y-clearance,bottom=node.y+node.height+clearance;
    if(Math.abs(segment.y1-segment.y2)<0.001){const min=Math.min(segment.x1,segment.x2),max=Math.max(segment.x1,segment.x2);return segment.y1>=top&&segment.y1<=bottom&&max>=left&&min<=right;}
    const min=Math.min(segment.y1,segment.y2),max=Math.max(segment.y1,segment.y2);return segment.x1>=left&&segment.x1<=right&&max>=top&&min<=bottom;
}

describe('HYDRA ELK-style layered layout',()=>{
    it('lays out variable-sized directed nodes without overlap',()=>{
        const layout=layoutHydraElkStyle([
            {id:'a',width:260,height:140},{id:'b',width:180,height:90},{id:'c',width:320,height:120},{id:'d',width:160,height:220}
        ],[{id:'ab',source:'a',target:'b'},{id:'ac',source:'a',target:'c'},{id:'bd',source:'b',target:'d'},{id:'cd',source:'c',target:'d'}]);
        const values=[...layout.values()];
        for(let i=0;i<values.length;i++)for(let j=i+1;j<values.length;j++)expect(overlap(values[i]!,values[j]!)).toBe(false);
        expect(layout.get('a')!.x).toBeLessThan(layout.get('d')!.x);
    });

    it('terminates and remains deterministic for cycles and disconnected components',()=>{
        const nodes=[{id:'a',width:100,height:80},{id:'b',width:100,height:80},{id:'c',width:100,height:80},{id:'x',width:120,height:90}];
        const edges=[{id:'ab',source:'a',target:'b'},{id:'bc',source:'b',target:'c'},{id:'ca',source:'c',target:'a'}];
        expect([...layoutHydraElkStyle(nodes,edges)]).toEqual([...layoutHydraElkStyle(nodes,edges)]);
    });

    it('keeps children inside an expanded compound parent',()=>{
        const layout=layoutHydraElkStyle([
            {id:'parent',width:180,height:100},{id:'left',width:120,height:70,parentId:'parent'},{id:'right',width:140,height:80,parentId:'parent'}
        ],[{id:'lr',source:'left',target:'right'}]);
        const parent=layout.get('parent')!, left=layout.get('left')!, right=layout.get('right')!;
        expect(parent.width).toBeGreaterThanOrEqual(Math.max(left.x+left.width,right.x+right.width)+20);
        expect(parent.height).toBeGreaterThanOrEqual(Math.max(left.y+left.height,right.y+right.height)+20);
        expect(left.parentId).toBe('parent');
    });


    it('moves unrelated nodes out of connection corridors so smooth-step edges never cross their boxes',()=>{
        const nodes=[
            {id:'source',width:180,height:90},{id:'target',width:180,height:90},
            {id:'upper',width:200,height:110},{id:'obstacle',width:220,height:130},{id:'lower',width:200,height:110},
        ];
        const edges=[
            {id:'main',source:'source',target:'target'},
            {id:'su',source:'source',target:'upper'},{id:'ut',source:'upper',target:'target'},
            {id:'sl',source:'source',target:'lower'},{id:'lt',source:'lower',target:'target'},
            {id:'uo',source:'upper',target:'obstacle'},
        ];
        const layout=layoutHydraElkStyle(nodes,edges,{nodeGap:44,layerGap:120});
        for(const edge of edges){
            const source=layout.get(edge.source)!,target=layout.get(edge.target)!;
            for(const node of nodes){
                if(node.id===edge.source||node.id===edge.target)continue;
                const obstacle=layout.get(node.id)!;
                expect(orthogonalSegments(source,target).some(segment=>segmentCrossesNode(segment,obstacle))).toBe(false);
            }
        }
    });

    it('supports top-to-bottom layout',()=>{
        const layout=layoutHydraElkStyle([{id:'a',width:100,height:80},{id:'b',width:100,height:80}],[{id:'e',source:'a',target:'b'}],{direction:'DOWN'});
        expect(layout.get('a')!.y).toBeLessThan(layout.get('b')!.y);
    });
});
