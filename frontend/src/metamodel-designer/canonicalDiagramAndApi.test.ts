import { afterEach, describe, expect, it, vi } from 'vitest';
import { buildDependencyRelationId, buildInheritanceRelationId, buildMetamodelDiagramDocument, buildMetamodelEdges, buildMetamodelNodes, chooseConnectionHandles, createEmptyMetamodelDiagram, formatMultiplicity, isDependencyRelationId, parseInheritanceRelationId } from './diagram';
import { createMetaModel, deleteMetamodel, getMetamodelExportUrl, loadMetaModel, replaceMetaModel, saveMetamodelDiagram } from './api';
import { canonicalDiagramFixture, canonicalMetaModelFixture } from './test-fixtures/canonicalMetaModelFixture';

afterEach(() => vi.restoreAllMocks());

describe('canonical diagram projection', () => {
    it('creates one visual node per classifier', () => expect(buildMetamodelNodes(canonicalMetaModelFixture(),canonicalDiagramFixture(),[])).toHaveLength(6));
    it('creates every visual edge from MetaModel.relations', () => expect(buildMetamodelEdges(canonicalMetaModelFixture(),canonicalDiagramFixture())).toHaveLength(4));
    it('keeps relation stable ids as edge ids', () => expect(buildMetamodelEdges(canonicalMetaModelFixture(),canonicalDiagramFixture()).map(e=>e.id)).toContain('customer-orders'));
    it('stores null upper bounds as an unbounded label', () => expect(formatMultiplicity(0,null)).toBe('[0..*]'));
    it('formats exact cardinalities compactly', () => expect(formatMultiplicity(1,1)).toBe('[1]'));
    it('creates empty diagrams with the canonical id', () => expect(createEmptyMetamodelDiagram('shop')).toEqual({metamodelId:'shop',nodes:[],edges:[]}));
    it('round trips inheritance visual ids', () => expect(parseInheritanceRelationId(buildInheritanceRelationId('child','parent'))).toEqual({sourceClassId:'child',targetClassId:'parent'}));
    it('rejects malformed inheritance ids', () => expect(parseInheritanceRelationId('relation:a:b')).toBeNull());
    it('recognizes dependency visual ids', () => expect(isDependencyRelationId(buildDependencyRelationId('r1'))).toBe(true));
    it('chooses horizontal handles for nodes on the same row', () => { const nodes=buildMetamodelNodes(canonicalMetaModelFixture(),canonicalDiagramFixture(),[]); expect(chooseConnectionHandles(nodes,'root','customer')).toEqual({sourceHandle:'right-source',targetHandle:'left-target'}); });
    it('chooses loop handles for self relations', () => { const nodes=buildMetamodelNodes(canonicalMetaModelFixture(),canonicalDiagramFixture(),[]); expect(chooseConnectionHandles(nodes,'root','root')).toEqual({sourceHandle:'right-source',targetHandle:'bottom-target'}); });
    it('persists visual nodes and global relations', () => { const nodes=buildMetamodelNodes(canonicalMetaModelFixture(),canonicalDiagramFixture(),[]); const edges=buildMetamodelEdges(canonicalMetaModelFixture(),canonicalDiagramFixture(),nodes); const d=buildMetamodelDiagramDocument('shop',nodes,edges); expect(d.nodes).toHaveLength(6); expect(d.edges).toHaveLength(4); });
});

describe('canonical API contract', () => {
    function response(body: unknown, etag=''): Response { return new Response(JSON.stringify(body),{status:200,headers:{'Content-Type':'application/json','ETag':etag}}); }
    const saveBody=()=>({metamodel:canonicalMetaModelFixture(),validation:{issues:[]},projections:[],etag:'body-etag'});
    it('loads MetaModel 2.0 and its ETag', async () => { vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response(canonicalMetaModelFixture(),'v1'))); await expect(loadMetaModel('shop')).resolves.toEqual({metamodel:canonicalMetaModelFixture(),etag:'v1'}); });
    it('rejects noncanonical schema versions on load', async () => { vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response({...canonicalMetaModelFixture(),schemaVersion:'1.0'}))); await expect(loadMetaModel('shop')).rejects.toThrow('Unsupported metamodel schemaVersion'); });
    it('creates through the canonical collection endpoint', async () => { const fetchMock=vi.fn().mockResolvedValue(response(saveBody(),'v2')); vi.stubGlobal('fetch',fetchMock); expect((await createMetaModel(canonicalMetaModelFixture())).etag).toBe('v2'); expect(fetchMock).toHaveBeenCalledWith('/api/canonical-metamodels',expect.objectContaining({method:'POST'})); });
    it('replaces with If-Match concurrency control', async () => { const fetchMock=vi.fn().mockResolvedValue(response(saveBody(),'v3')); vi.stubGlobal('fetch',fetchMock); await replaceMetaModel(canonicalMetaModelFixture(),'v2'); const init=fetchMock.mock.calls[0]?.[1] as RequestInit; expect(new Headers(init.headers).get('If-Match')).toBe('v2'); });
    it('saves the diagram below the canonical resource', async () => { const d=canonicalDiagramFixture(); const fetchMock=vi.fn().mockResolvedValue(response(d)); vi.stubGlobal('fetch',fetchMock); await saveMetamodelDiagram('shop',d); expect(fetchMock.mock.calls[0][0]).toBe('/api/canonical-metamodels/shop/diagram'); });
    it('deletes lifecycle artifacts with explicit options and ETag', async () => { const fetchMock=vi.fn().mockResolvedValue(response({metamodelId:'shop',deletedFiles:[]})); vi.stubGlobal('fetch',fetchMock); await deleteMetamodel('shop',true,false,'v3'); expect(fetchMock.mock.calls[0][0]).toContain('/api/canonical-metamodels/shop/lifecycle?'); const init=fetchMock.mock.calls[0]?.[1] as RequestInit; expect(init.method).toBe('DELETE'); expect(new Headers(init.headers).get('If-Match')).toBe('v3'); });
    it('keeps export URLs independent from the former designer endpoint', () => expect(getMetamodelExportUrl('shop','markdown')).toBe('/api/metamodels/shop/exports/markdown'));
});
