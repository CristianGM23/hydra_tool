import { describe, expect, it } from 'vitest';
import { normalizeEditorCompilationResult, normalizeEditorSessionView } from './editorRuntimeNormalization';
import type { EditorDefinitionCompilationResult, EditorSessionView } from './types';

const sparseCompilation = {
    plan:{schemaVersion:'1.0',id:'compiled',sourceEditorDefinitionId:'editor',sourceEtag:'etag',metamodelId:'mm',name:'Editor',compiledAt:'2026-09-04T00:00:00Z',representationsById:{main:{id:'main',name:'Main',kind:'DIAGRAM',editable:true}},formsById:undefined,formsByTargetClassId:undefined,capabilityManifest:undefined,runtimePolicy:undefined,annotations:undefined},
    checkReport:{editorDefinitionId:'editor'},cacheHit:false
} as unknown as EditorDefinitionCompilationResult;

const sparseSession = {
    session:{schemaVersion:'1.0',id:'session',metamodelId:'mm',editorId:'editor',sourceEditorEtag:'etag',activeRepresentationId:'main',model:{schemaVersion:'1.0',id:'model',metamodelId:'mm'},latestValidationReport:null,revision:0,createdAt:'',updatedAt:''},
    etag:'etag',representationId:'main',representationName:'Main',representationKind:'DIAGRAM',editable:true,selectedForm:null,validationReport:null,undoAvailable:false,redoAvailable:false
} as unknown as EditorSessionView;

describe('editor runtime transport normalization',()=>{
    it('normalizes sparse compiled plans before the model editor renders them',()=>{
        const normalized=normalizeEditorCompilationResult(sparseCompilation);
        expect(normalized.plan.representationsById.main).toMatchObject({rootClassIds:[],nodeMappingsByClassId:{},edgeMappingsByRelationId:{},toolsById:{},paletteGroups:[],layers:[],filters:[],annotations:{}});
        expect(normalized.plan.formsById).toEqual({});
        expect(normalized.plan.capabilityManifest.capabilities).toEqual(['MODEL_EDITING']);
        expect(normalized.checkReport.diagnostics).toEqual([]);
    });

    it('normalizes sparse session projections so empty runtime collections never crash React',()=>{
        const normalized=normalizeEditorSessionView(sparseSession);
        expect(normalized.session.openRepresentationIds).toEqual([]);
        expect(normalized.session.model.rootElementIds).toEqual([]);
        expect(normalized.session.model.elements).toEqual([]);
        expect(normalized.nodes).toEqual([]);
        expect(normalized.edges).toEqual([]);
        expect(normalized.paletteGroups).toEqual([]);
        expect(normalized.representations).toEqual([]);
    });
});
