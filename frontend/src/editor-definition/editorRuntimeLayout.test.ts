import { describe, expect, it } from 'vitest';
import type { Node } from '@xyflow/react';
import { arrangeRuntimeSelection, normalizeRuntimeRotation, rotateRuntimeDiagramNodes, rotationFitScale } from './editorRuntimeLayout';

const diagramNodes=[
    {elementId:'a',x:20,y:30,width:180,height:90},
    {elementId:'b',x:260,y:140,width:180,height:90,rotationDegrees:270}
];

describe('editor runtime layout helpers',()=>{
    it('normalizes persisted rotations and applies relative rotation only to the selection',()=>{
        expect(normalizeRuntimeRotation(-90)).toBe(270);
        const rotated=rotateRuntimeDiagramNodes(diagramNodes,['a','b'],90);
        expect(rotated.map(node=>node.rotationDegrees)).toEqual([90,0]);
    });

    it('keeps arbitrary rotations inside the visual node bounds',()=>{
        expect(rotationFitScale(180,90,0)).toBe(1);
        expect(rotationFitScale(180,90,90)).toBeCloseTo(.5,5);
        expect(rotationFitScale(100,100,45)).toBeLessThan(1);
    });

    it('arranges selected elements independently inside each semantic container',()=>{
        const nodes:Node<Record<string,unknown>>[]=[
            {id:'a',position:{x:10,y:20},width:100,height:50,data:{},parentId:'container-a'},
            {id:'b',position:{x:80,y:160},width:100,height:50,data:{},parentId:'container-a'},
            {id:'c',position:{x:300,y:40},width:100,height:50,data:{},parentId:'container-b'},
            {id:'d',position:{x:380,y:180},width:100,height:50,data:{},parentId:'container-b'}
        ];
        const arranged=arrangeRuntimeSelection(nodes,['a','b','c','d'],'ALIGN_LEFT');
        expect(arranged.find(node=>node.id==='a')?.position.x).toBe(arranged.find(node=>node.id==='b')?.position.x);
        expect(arranged.find(node=>node.id==='c')?.position.x).toBe(arranged.find(node=>node.id==='d')?.position.x);
        expect(arranged.find(node=>node.id==='a')?.position.x).not.toBe(arranged.find(node=>node.id==='c')?.position.x);
    });
});
