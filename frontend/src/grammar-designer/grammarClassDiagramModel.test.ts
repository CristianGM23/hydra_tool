import { describe, expect, it } from 'vitest';
import { buildGrammarDiagramView, DEFAULT_GRAMMAR_DIAGRAM_FILTERS, diagramStatusAllowsNavigation, grammarClassNodeSize } from './grammarClassDiagramModel';
import type { GrammarClassDiagramProjection } from './types';

const projection:GrammarClassDiagramProjection={
    grammarId:'g',draftRevision:1,sourceHash:'h',baseSourceHash:'h',status:'COMPLETE',projectionHash:'p',metadata:{},warnings:[],
    inferredMetamodel:{schemaVersion:'2.1',id:'m',name:'M',classes:[],enums:[],dataTypes:[],relations:[],annotations:{}},
    nodes:[
        {id:'A',semanticId:'A',name:'A',kind:'CLASS',root:true,confidence:.9,diagnosticCount:0,source:{documentId:'main',range:{start:{line:0,column:0},end:{line:0,column:1}},grammarElementId:'rA',ruleName:'A'},metadata:{},features:[{id:'a-name',name:'name',type:'STRING',multiplicity:'[1]',kind:'ATTRIBUTE',diagnosticCount:0,source:{documentId:'main',range:{start:{line:0,column:3},end:{line:0,column:7}},grammarElementId:'a',ruleName:'A'}}]},
        {id:'B',semanticId:'B',name:'B',kind:'CLASS',root:false,confidence:.8,diagnosticCount:1,source:{documentId:'main',range:{start:{line:1,column:0},end:{line:1,column:1}},grammarElementId:'rB',ruleName:'B'},metadata:{},features:[]},
        {id:'E',semanticId:'E',name:'E',kind:'ENUM',root:false,confidence:.8,diagnosticCount:0,source:{documentId:'main',range:{start:{line:2,column:0},end:{line:2,column:1}},grammarElementId:'rE',ruleName:'E'},metadata:{},features:[]}
    ],
    edges:[
        {id:'ab',sourceId:'A',targetId:'B',kind:'CONTAINMENT',label:'children',sourceMultiplicity:'',targetMultiplicity:'[0..*]',diagnosticCount:0,source:{documentId:'main',range:{start:{line:0,column:2},end:{line:0,column:6}},grammarElementId:'ab',ruleName:'A'},metadata:{}},
        {id:'ba',sourceId:'B',targetId:'A',kind:'REFERENCE',label:'parent',sourceMultiplicity:'',targetMultiplicity:'[0..1]',diagnosticCount:1,source:{documentId:'main',range:{start:{line:1,column:2},end:{line:1,column:6}},grammarElementId:'ba',ruleName:'B'},metadata:{}}
    ]
};

describe('grammar class diagram view model',()=>{
    it('lays out the inferred metamodel deterministically without overlaps in this graph',()=>{
        const first=buildGrammarDiagramView(projection);const second=buildGrammarDiagramView(projection);
        expect([...first.placements.entries()]).toEqual([...second.placements.entries()]);
        expect(first.nodes).toHaveLength(3);expect(first.edges).toHaveLength(2);
        const a=first.placements.get('A')!;const b=first.placements.get('B')!;
        expect(a.x+a.width<=b.x||b.x+b.width<=a.x||a.y+a.height<=b.y||b.y+b.height<=a.y).toBe(true);
    });
    it('filters relation focus and classifiers without changing the source projection',()=>{
        const view=buildGrammarDiagramView(projection,{...DEFAULT_GRAMMAR_DIAGRAM_FILTERS,focus:'REFERENCES',showEnums:false});
        expect(view.nodes.map(item=>item.id)).toEqual(['A','B']);
        expect(view.edges.map(item=>item.id)).toEqual(['ba']);
        expect(projection.nodes).toHaveLength(3);
    });

    it('grows class nodes with feature count and long attribute/type labels so rows are never clipped',()=>{
        const base=projection.nodes[0];
        const compact=grammarClassNodeSize(base,base.features);
        const many=Array.from({length:12},(_,index)=>({...base.features[0],id:`f-${index}`,name:`veryLongAttributeName${index}`,type:'ExtremelyLongQualifiedTypeName',multiplicity:'[0..*]'}));
        const expanded=grammarClassNodeSize({...base,name:'A very long inferred classifier name'},many);
        expect(expanded.width).toBeGreaterThanOrEqual(compact.width);
        expect(expanded.height).toBeGreaterThan(compact.height);
        expect(expanded.width).toBeGreaterThanOrEqual(260);
        expect(expanded.height).toBeGreaterThanOrEqual(96);
    });
    it('allows navigation only for usable projections',()=>{
        expect(diagramStatusAllowsNavigation(projection)).toBe(true);
        expect(diagramStatusAllowsNavigation({...projection,status:'FAILED',nodes:[]})).toBe(false);
    });
});
