package com.example.editor.integration.adapter.glsp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.RepresentationKind;
import tools.jackson.databind.json.JsonMapper;

/** Genera las pruebas de protocolo cliente-servidor de los servidores GLSP generados y el fixture que derivan de los mappings del editor. */
final class GlspProtocolTestSources {
    private final JsonMapper mapper;

    /** Descripción: Inicializa el generador de pruebas de protocolo. Entradas: mapper: serializador JSON del adaptador. Salida: deja la instancia lista para su uso. */ GlspProtocolTestSources(JsonMapper mapper){this.mapper=mapper;}

    /** Descripción: Obtiene el tipo GLSP de cada clase canónica con mapping de nodo en una representación DIAGRAM; gana el primer mapping. Entradas: editor: definición canónica. Salida: mapa ordenado classId → tipo de nodo. */ static Map<String,String> nodeTypes(EditorDefinitionModel editor){
        Map<String,String> nodeTypes=new LinkedHashMap<>();
        editor.representations().stream().filter(r->r.kind()==RepresentationKind.DIAGRAM).forEach(r->r.nodeMappings().forEach(m->nodeTypes.putIfAbsent(m.targetClassId(),m.representationName())));
        return nodeTypes;
    }
    /** Descripción: Obtiene las relaciones canónicas con mapping de arista en una representación DIAGRAM. Entradas: editor: definición canónica. Salida: mapa ordenado relationId → tipo de arista. */ static Map<String,String> edgeTypes(EditorDefinitionModel editor){
        Map<String,String> edgeTypes=new LinkedHashMap<>();
        editor.representations().stream().filter(r->r.kind()==RepresentationKind.DIAGRAM).forEach(r->r.edgeMappings().stream().filter(m->!m.relationId().isBlank()).forEach(m->edgeTypes.putIfAbsent(m.relationId(),"hydra:edge")));
        return edgeTypes;
    }

    /** Modelo canónico y GModel esperado con que la prueba de protocolo generada ejercita el servidor. */
    private record ProtocolFixture(String model,String updatedModel,List<List<String>> nodes,List<List<String>> edges,List<List<String>> updatedNodes,List<List<String>> updatedEdges){}

    /** Descripción: Construye el fixture de la prueba de protocolo: dos elementos por clase mapeada, la primera relación mapeada entre los dos primeros y tres casos que la proyección debe ocultar (clase sin mapping, relación sin mapping y relación hacia un elemento oculto). La versión editada retira el último elemento mapeado y marca el documento para que el guardado sea observable. Entradas: editor: definición canónica. Salida: modelos serializados y nodos/aristas esperados, ordenados por id. */ private ProtocolFixture protocolFixture(EditorDefinitionModel editor){
        Map<String,String> nodeTypes=nodeTypes(editor);Map<String,String> edgeTypes=edgeTypes(editor);
        String hiddenClass=unused("hydra:it:unmapped",nodeTypes.keySet());String hiddenRelation=unused("hydra:it:unmapped",edgeTypes.keySet());
        List<Map<String,String>> elements=new ArrayList<>();List<List<String>> nodes=new ArrayList<>();
        int index=0;
        for(Map.Entry<String,String> type:nodeTypes.entrySet()){
            for(String suffix:List.of("a","b")){String id="hydra-it-"+index+"-"+suffix;elements.add(ordered("id",id,"classId",type.getKey()));nodes.add(List.of(id,type.getValue()));}
            index++;
        }
        elements.add(ordered("id","hydra-it-unmapped","classId",hiddenClass));
        List<Map<String,String>> relations=new ArrayList<>();List<List<String>> edges=new ArrayList<>();
        if(!nodes.isEmpty()){
            String first=nodes.get(0).get(0);String second=nodes.get(1).get(0);
            if(!edgeTypes.isEmpty()){
                String relationId=edgeTypes.keySet().iterator().next();
                relations.add(ordered("id","hydra-it-edge","relationId",relationId,"sourceElementId",first,"targetElementId",second));
                edges.add(List.of("hydra-it-edge",first,second));
                relations.add(ordered("id","hydra-it-hidden-target","relationId",relationId,"sourceElementId",first,"targetElementId","hydra-it-unmapped"));
            }
            relations.add(ordered("id","hydra-it-unmapped-relation","relationId",hiddenRelation,"sourceElementId",first,"targetElementId",second));
        }
        String removed=nodes.isEmpty()?"":nodes.get(nodes.size()-1).get(0);
        Map<String,Object> model=new LinkedHashMap<>();model.put("elements",elements);model.put("relations",relations);
        Map<String,Object> updated=new LinkedHashMap<>();updated.put("name","hydra-it-updated");
        updated.put("elements",elements.stream().filter(element->!element.get("id").equals(removed)).toList());updated.put("relations",relations);
        List<List<String>> updatedNodes=nodes.stream().filter(node->!node.get(0).equals(removed)).toList();
        Set<String> kept=new HashSet<>();updatedNodes.forEach(node->kept.add(node.get(0)));
        List<List<String>> updatedEdges=edges.stream().filter(edge->kept.contains(edge.get(1))&&kept.contains(edge.get(2))).toList();
        return new ProtocolFixture(mapper.writeValueAsString(model),mapper.writeValueAsString(updated),byId(nodes),byId(edges),byId(updatedNodes),byId(updatedEdges));
    }
    /** Descripción: Devuelve un identificador que no colisiona con los existentes. Entradas: base: candidato; taken: identificadores ocupados. Salida: identificador libre. */ private static String unused(String base,Set<String> taken){String value=base;int suffix=1;while(taken.contains(value))value=base+"-"+suffix++;return value;}
    /** Descripción: Crea un objeto JSON con el orden de claves indicado. Entradas: pares clave/valor. Salida: mapa ordenado. */ private static Map<String,String> ordered(String... pairs){Map<String,String> value=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)value.put(pairs[i],pairs[i+1]);return value;}
    /** Descripción: Ordena filas por su primera columna en orden de unidades UTF-16, el mismo que aplican `<` en TypeScript y compareTo en Java. Entradas: rows: filas. Salida: copia ordenada. */ private static List<List<String>> byId(List<List<String>> rows){return rows.stream().sorted((a,b)->a.get(0).compareTo(b.get(0))).toList();}
    /** Descripción: Escribe filas como expresión List.of de Java. Entradas: rows: filas. Salida: expresión Java. */ private static String javaRows(List<List<String>> rows){return rows.isEmpty()?"List.<List<String>>of()":"List.of("+String.join(", ",rows.stream().map(row->"List.of("+String.join(", ",row.stream().map(GlspProtocolTestSources::javaLiteral).toList())+")").toList())+")";}

    /** Descripción: Genera la prueba de integración cliente-servidor del servidor Node: el cliente JSON-RPC oficial de GLSP se conecta por socket al HydraDiagramModule generado y recorre inicialización, sesión, carga con layout en cliente, operación HYDRA, deshacer/rehacer, guardado y cierre, comprobando el GModel proyectado y el fichero guardado. Entradas: diagramType: tipo; editor: definición visual canónica. Salida: fuente Vitest. */ String nodeProtocolTest(String diagramType, EditorDefinitionModel editor){
        ProtocolFixture fixture=protocolFixture(editor);
        return """
            import 'reflect-metadata';
            import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
            import { connect } from 'node:net';
            import { tmpdir } from 'node:os';
            import { join } from 'node:path';
            import { pathToFileURL } from 'node:url';
            import { Container } from 'inversify';
            import { BaseJsonrpcGLSPClient, ComputedBoundsAction, RequestModelAction, SaveModelAction, type Action } from '@eclipse-glsp/protocol';
            import { LogLevel, ServerModule, SocketServerLauncher, createAppModule, defaultLaunchOptions } from '@eclipse-glsp/server/node.js';
            import { SocketMessageReader, SocketMessageWriter, createMessageConnection } from 'vscode-jsonrpc/node.js';
            import { describe, expect, it } from 'vitest';
            import { HydraDiagramModule } from './hydra-diagram-module.js';

            // Fixture derived by HYDRA from the editor mappings: mapped elements become nodes, a mapped relation between visible nodes becomes
            // an edge, and unmapped classes, unmapped relations and relations to hidden elements must stay out of the GModel.
            const DIAGRAM_TYPE = %s;
            const MODEL: unknown = %s;
            const EXPECTED_NODES: string[][] = %s;
            const EXPECTED_EDGES: string[][] = %s;
            const UPDATED_MODEL: unknown = %s;
            const EXPECTED_UPDATED_NODES: string[][] = %s;
            const EXPECTED_UPDATED_EDGES: string[][] = %s;
            const CLIENT_ACTIONS = ['requestBounds', 'setModel', 'updateModel', 'setDirtyState', 'setEditMode', 'setMarkers', 'setTypeHints', 'status',
              'serverMessage', 'startProgress', 'updateProgress', 'endProgress', 'triggerNodeCreation', 'triggerEdgeCreation'];

            type GElement = { id: string; type: string; revision?: number; children?: GElement[]; sourceId?: string; targetId?: string };
            const byId = (a: string[], b: string[]) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0);
            const rootOf = (action: Action): GElement => (action as unknown as { newRoot: GElement }).newRoot;
            const nodesOf = (root: GElement) => (root.children ?? []).filter(child => child.type !== 'hydra:edge').map(child => [child.id, child.type]).sort(byId);
            const edgesOf = (root: GElement) => (root.children ?? []).filter(child => child.type === 'hydra:edge')
              .map(child => [child.id, child.sourceId ?? '', child.targetId ?? '']).sort(byId);
            const shapes = (root: GElement, out: string[] = []): string[] => {
              for(const child of root.children ?? []) { if(child.type !== 'hydra:edge') out.push(child.id); shapes(child, out); }
              return out;
            };

            async function next(received: Action[], from: number, ...kinds: string[]): Promise<{ action: Action; index: number }> {
              const deadline = Date.now() + 15_000;
              while(Date.now() < deadline) {
                const index = received.findIndex((action, position) => position >= from && kinds.includes(action.kind));
                if(index >= 0) return { action: received[index], index };
                const rejected = received.slice(from).find(action => action.kind === 'rejectRequest' || (action.kind === 'status' && (action as { severity?: string }).severity === 'ERROR'));
                if(rejected) throw new Error(`GLSP server rejected the request: ${JSON.stringify(rejected).slice(0, 800)}`);
                await new Promise(resolve => setTimeout(resolve, 25));
              }
              throw new Error(`No ${kinds.join('/')} after #${from}; received: ${received.map(action => action.kind).join(', ')}`);
            }

            describe('generated GLSP client-server integration', () => {
              it('loads, edits, undoes, redoes and saves the HYDRA source model through the GLSP protocol', async () => {
                const work = mkdtempSync(join(tmpdir(), 'hydra-glsp-it-'));
                const modelPath = join(work, 'model.hydra.json');
                writeFileSync(modelPath, JSON.stringify(MODEL), 'utf8');
                const app = new Container();
                app.load(createAppModule({ ...defaultLaunchOptions, logLevel: LogLevel.warn, fileLog: false, consoleLog: false }));
                const launcher = app.resolve(SocketServerLauncher);
                launcher.configure(new ServerModule().configureDiagramModule(new HydraDiagramModule()));
                const running = Promise.resolve(launcher.start({ port: 0, host: '127.0.0.1' }));
                running.catch(() => undefined);
                const { port } = await launcher.listening;
                const socket = connect({ port, host: '127.0.0.1' });
                await new Promise<void>((resolve, reject) => { socket.once('connect', () => resolve()); socket.once('error', reject); });
                const client = new BaseJsonrpcGLSPClient({ id: 'hydra-it', connectionProvider: createMessageConnection(new SocketMessageReader(socket), new SocketMessageWriter(socket)) });
                const clientId = 'hydra-it-session';
                const received: Action[] = [];
                const send = (action: Action) => client.sendActionMessage({ clientId, action });
                try {
                  await client.start();
                  const initialized = await client.initializeServer({ applicationId: 'hydra-it', protocolVersion: '1.0.0' });
                  expect(initialized.protocolVersion).toBeTruthy();
                  client.onActionMessage(message => {
                    received.push(message.action);
                    if(message.action.kind === 'requestBounds') {
                      const request = message.action as unknown as { newRoot: GElement; requestId: string };
                      const bounds = shapes(request.newRoot).map(elementId => ({ elementId, newSize: { width: 80, height: 40 }, newPosition: { x: 0, y: 0 } }));
                      send(ComputedBoundsAction.create(bounds, { revision: request.newRoot.revision, responseId: request.requestId }));
                    }
                  }, clientId);
                  await client.initializeClientSession({ clientSessionId: clientId, diagramType: DIAGRAM_TYPE, clientActionKinds: CLIENT_ACTIONS });

                  send(RequestModelAction.create({ options: { sourceUri: pathToFileURL(modelPath).href, diagramType: DIAGRAM_TYPE } }));
                  const loaded = await next(received, 0, 'setModel');
                  expect(rootOf(loaded.action).id).toBe('hydra-root');
                  expect(rootOf(loaded.action).type).toBe(DIAGRAM_TYPE);
                  expect(nodesOf(rootOf(loaded.action))).toEqual(EXPECTED_NODES);
                  expect(edgesOf(rootOf(loaded.action))).toEqual(EXPECTED_EDGES);

                  send({ kind: 'hydra.replaceSourceModel', isOperation: true, newState: JSON.stringify(UPDATED_MODEL) } as unknown as Action);
                  const edited = await next(received, loaded.index + 1, 'updateModel', 'setModel');
                  expect(nodesOf(rootOf(edited.action))).toEqual(EXPECTED_UPDATED_NODES);
                  expect(edgesOf(rootOf(edited.action))).toEqual(EXPECTED_UPDATED_EDGES);

                  send({ kind: 'glspUndo' } as Action);
                  const undone = await next(received, edited.index + 1, 'updateModel', 'setModel');
                  expect(nodesOf(rootOf(undone.action))).toEqual(EXPECTED_NODES);
                  expect(edgesOf(rootOf(undone.action))).toEqual(EXPECTED_EDGES);

                  send({ kind: 'glspRedo' } as Action);
                  const redone = await next(received, undone.index + 1, 'updateModel', 'setModel');
                  expect(nodesOf(rootOf(redone.action))).toEqual(EXPECTED_UPDATED_NODES);

                  send(SaveModelAction.create());
                  const saved = await next(received, redone.index + 1, 'setDirtyState');
                  expect((saved.action as unknown as { isDirty: boolean }).isDirty).toBe(false);
                  expect(JSON.parse(readFileSync(modelPath, 'utf8'))).toEqual(UPDATED_MODEL);

                  await client.disposeClientSession({ clientSessionId: clientId });
                  await client.shutdownServer();
                } finally {
                  await client.stop().catch(() => undefined);
                  socket.destroy();
                  await Promise.resolve(launcher.shutdown()).catch(() => undefined);
                  rmSync(work, { recursive: true, force: true });
                }
              }, 60_000);
            });
            """.formatted(mapper.writeValueAsString(diagramType), fixture.model(), mapper.writeValueAsString(fixture.nodes()), mapper.writeValueAsString(fixture.edges()),
                    fixture.updatedModel(), mapper.writeValueAsString(fixture.updatedNodes()), mapper.writeValueAsString(fixture.updatedEdges()));
    }

    /** Descripción: Genera la prueba de integración del servidor Java: un inyector Guice real con ServerModule y el HydraDiagramModule generado recorre GLSPServer (inicialización, sesión, carga con layout en cliente, marcadores del validador, guardado y cierre) y comprueba el GModel proyectado. No sustituye al cliente TypeScript: ejercita el servidor a través de su API de protocolo. Entradas: basePackage: paquete; diagramType: tipo; editor: definición visual canónica. Salida: fuente JUnit 5. */ String javaProtocolTest(String basePackage, String diagramType, EditorDefinitionModel editor){
        ProtocolFixture fixture=protocolFixture(editor);
        return """
            package %s;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertFalse;
            import static org.junit.jupiter.api.Assertions.assertNotNull;
            import java.nio.charset.StandardCharsets;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.util.ArrayList;
            import java.util.Comparator;
            import java.util.List;
            import java.util.Map;
            import java.util.concurrent.BlockingQueue;
            import java.util.concurrent.LinkedBlockingQueue;
            import java.util.concurrent.TimeUnit;
            import org.eclipse.glsp.graph.GEdge;
            import org.eclipse.glsp.graph.GModelElement;
            import org.eclipse.glsp.graph.GModelRoot;
            import org.eclipse.glsp.graph.util.GraphUtil;
            import org.eclipse.glsp.server.actions.Action;
            import org.eclipse.glsp.server.actions.ActionMessage;
            import org.eclipse.glsp.server.actions.SaveModelAction;
            import org.eclipse.glsp.server.actions.SetDirtyStateAction;
            import org.eclipse.glsp.server.di.ServerModule;
            import org.eclipse.glsp.server.features.core.model.ComputedBoundsAction;
            import org.eclipse.glsp.server.features.core.model.RequestBoundsAction;
            import org.eclipse.glsp.server.features.core.model.RequestModelAction;
            import org.eclipse.glsp.server.features.core.model.SetModelAction;
            import org.eclipse.glsp.server.features.core.model.UpdateModelAction;
            import org.eclipse.glsp.server.features.validation.RequestMarkersAction;
            import org.eclipse.glsp.server.features.validation.SetMarkersAction;
            import org.eclipse.glsp.server.protocol.DisposeClientSessionParameters;
            import org.eclipse.glsp.server.protocol.GLSPServer;
            import org.eclipse.glsp.server.protocol.InitializeClientSessionParameters;
            import org.eclipse.glsp.server.protocol.InitializeParameters;
            import org.eclipse.glsp.server.types.ElementAndBounds;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.io.TempDir;
            import com.google.inject.Guice;

            /** Drives the generated diagram module through the real GLSP server stack (session, action dispatcher, storage, factory, validator). */
            class HydraGlspServerIntegrationTest {
                private static final String DIAGRAM_TYPE = %s;
                private static final String SESSION = "hydra-it-session";
                // Fixture derived by HYDRA from the editor mappings; unmapped classes, unmapped relations and relations to hidden elements must stay out of the GModel.
                private static final String MODEL = %s;
                private static final List<List<String>> EXPECTED_NODES = %s;
                private static final List<List<String>> EXPECTED_EDGES = %s;

                @Test void servesTheHydraSourceModelThroughTheGlspServer(@TempDir Path work) throws Exception {
                    Path model = work.resolve("model.hydra.json");
                    Files.writeString(model, MODEL, StandardCharsets.UTF_8);
                    GLSPServer server = Guice.createInjector(new ServerModule().configureDiagramModule(new HydraDiagramModule())).getInstance(GLSPServer.class);
                    BlockingQueue<Action> received = new LinkedBlockingQueue<>();
                    server.connect(message -> received.add(message.getAction()));
                    InitializeParameters initialize = new InitializeParameters();
                    initialize.setApplicationId("hydra-it");
                    initialize.setProtocolVersion("1.0.0");
                    assertNotNull(server.initialize(initialize).get(30, TimeUnit.SECONDS));
                    server.initializeClientSession(new InitializeClientSessionParameters(SESSION, DIAGRAM_TYPE,
                            List.of("requestBounds", "setModel", "updateModel", "setDirtyState", "setMarkers", "setEditMode", "setTypeHints", "status", "serverMessage"))).get(30, TimeUnit.SECONDS);
                    try {
                        server.process(new ActionMessage(SESSION, new RequestModelAction(Map.of("sourceUri", model.toUri().toString(), "diagramType", DIAGRAM_TYPE))));
                        GModelRoot root = awaitModel(server, received);
                        assertEquals("hydra-root", root.getId());
                        assertEquals(DIAGRAM_TYPE, root.getType());
                        assertEquals(EXPECTED_NODES, nodes(root));
                        assertEquals(EXPECTED_EDGES, edges(root));

                        server.process(new ActionMessage(SESSION, new RequestMarkersAction(List.of(root.getId()))));
                        SetMarkersAction markers = await(received, SetMarkersAction.class);
                        assertEquals(List.of(), markers.getMarkers(), "the neutral HYDRA engine reports no diagnostics for a valid model");

                        String before = Files.readString(model, StandardCharsets.UTF_8);
                        server.process(new ActionMessage(SESSION, new SaveModelAction()));
                        assertFalse(await(received, SetDirtyStateAction.class).isDirty());
                        assertEquals(before, Files.readString(model, StandardCharsets.UTF_8), "saving an unmodified model must not rewrite its content");
                    } finally {
                        DisposeClientSessionParameters dispose = new DisposeClientSessionParameters();
                        dispose.setClientSessionId(SESSION);
                        server.disposeClientSession(dispose).get(30, TimeUnit.SECONDS);
                        server.shutdown();
                    }
                }

                private static GModelRoot awaitModel(GLSPServer server, BlockingQueue<Action> received) throws InterruptedException {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    List<String> seen = new ArrayList<>();
                    while (System.nanoTime() < deadline) {
                        Action action = received.poll(100, TimeUnit.MILLISECONDS);
                        if (action == null) continue;
                        seen.add(action.getKind());
                        if (action instanceof RequestBoundsAction request) {
                            List<ElementAndBounds> bounds = new ArrayList<>();
                            for (GModelElement element : request.getNewRoot().getChildren()) {
                                if (element instanceof GEdge) continue;
                                ElementAndBounds value = new ElementAndBounds();
                                value.setElementId(element.getId());
                                value.setNewSize(GraphUtil.dimension(80, 40));
                                value.setNewPosition(GraphUtil.point(0, 0));
                                bounds.add(value);
                            }
                            server.process(new ActionMessage(SESSION, new ComputedBoundsAction(bounds, List.of(), List.of(), request.getNewRoot().getRevision())));
                        } else if (action instanceof SetModelAction set) {
                            return set.getNewRoot();
                        } else if (action instanceof UpdateModelAction update) {
                            return update.getNewRoot();
                        }
                    }
                    throw new AssertionError("GLSP server did not publish a model; received " + seen);
                }

                private static <T extends Action> T await(BlockingQueue<Action> received, Class<T> type) throws InterruptedException {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    List<String> seen = new ArrayList<>();
                    while (System.nanoTime() < deadline) {
                        Action action = received.poll(100, TimeUnit.MILLISECONDS);
                        if (action == null) continue;
                        if (type.isInstance(action)) return type.cast(action);
                        seen.add(action.getKind());
                    }
                    throw new AssertionError("GLSP server did not send " + type.getSimpleName() + "; received " + seen);
                }

                private static List<List<String>> nodes(GModelRoot root) {
                    return root.getChildren().stream().filter(element -> !(element instanceof GEdge))
                            .map(element -> List.of(element.getId(), element.getType())).sorted(Comparator.comparing(value -> value.get(0))).toList();
                }

                private static List<List<String>> edges(GModelRoot root) {
                    return root.getChildren().stream().filter(GEdge.class::isInstance).map(GEdge.class::cast)
                            .map(edge -> List.of(edge.getId(), edge.getSourceId(), edge.getTargetId())).sorted(Comparator.comparing(value -> value.get(0))).toList();
                }
            }
            """.formatted(basePackage, javaLiteral(diagramType), javaLiteral(fixture.model()), javaRows(fixture.nodes()), javaRows(fixture.edges()));
    }

    /** Descripción: Escapa un literal Java generado igual que el adaptador. Entradas: value: texto. Salida: literal Java entre comillas. */ private static String javaLiteral(String value){String escaped=String.valueOf(value==null?"":value).replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n");return "\""+escaped+"\"";}
}
