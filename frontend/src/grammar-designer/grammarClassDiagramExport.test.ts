import { describe, expect, it } from 'vitest';
import { buildGrammarDrawio, buildGrammarDiagramSvg, buildGrammarPlantUml } from './grammarClassDiagramExport';
import { buildGrammarDiagramView } from './grammarClassDiagramModel';
import type { GrammarClassDiagramProjection } from './types';

const source={documentId:'main',range:{start:{line:0,column:0},end:{line:0,column:1}},grammarElementId:'x',ruleName:'Model'};
const projection:GrammarClassDiagramProjection={
    grammarId:'g',draftRevision:1,sourceHash:'h',baseSourceHash:'h',status:'COMPLETE',projectionHash:'p',metadata:{},warnings:[],
    inferredMetamodel:{schemaVersion:'2.1',id:'m',name:'M',classes:[],enums:[],dataTypes:[],relations:[],annotations:{}},
    nodes:[
        {id:'Model',semanticId:'Model',name:'Model',kind:'CLASS',root:true,confidence:1,diagnosticCount:0,source,metadata:{},features:[{id:'f',name:'elements',type:'Element',multiplicity:'[0..*]',kind:'CONTAINMENT',source,diagnosticCount:0}]},
        {id:'Element',semanticId:'Element',name:'Element',kind:'CLASS',root:false,confidence:1,diagnosticCount:0,source,metadata:{},features:[{id:'name',name:'name',type:'STRING',multiplicity:'[1]',kind:'ATTRIBUTE',source,diagnosticCount:0}]}
    ],
    edges:[{id:'contains',sourceId:'Model',targetId:'Element',kind:'CONTAINMENT',label:'elements',sourceMultiplicity:'[1]',targetMultiplicity:'[0..*]',diagnosticCount:0,source,metadata:{}}]
};

describe('grammar inferred metamodel exports',()=>{
    const view=buildGrammarDiagramView(projection);
    it('exports composition semantics to PlantUML',()=>{
        const text=buildGrammarPlantUml(view);
        expect(text).toContain('*-->');
        expect(text).toContain('elements');
        expect(text).toContain('@startuml');
    });
    it('exports draw.io with UML diamond and non-collapsible class cells',()=>{
        const xml=buildGrammarDrawio(view);
        expect(xml).toContain('startArrow=diamond;startFill=1;endArrow=open');
        expect(xml).toContain('collapsible=0');
        expect(xml).toContain('Model');
    });
    it('exports standalone SVG with filled composition diamonds and arrow heads',()=>{
        const svg=buildGrammarDiagramSvg(view);
        expect(svg).toContain('id="diamond-filled"');
        expect(svg).toContain('marker-start="url(#diamond-filled)"');
        expect(svg).toContain('marker-end="url(#arrow)"');
    });
});
