package com.example.editor.integration.compiler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarTextualEditorCustomization;
import com.example.editor.integration.IntegrationArtifactKind;
import tools.jackson.databind.json.JsonMapper;

/** Shared projection of HYDRA textual-editor personalization into generated IDE/runtime targets. */
public final class TechnologyAdapterTextualCustomizationSupport {
    /** Descripción: Inicializa una instancia de TechnologyAdapterTextualCustomizationSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TechnologyAdapterTextualCustomizationSupport() { }

    /** Descripción: Ejecuta la operación «grammar» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarDefinitionModel grammar(TechnologyCompilationRequest request, JsonMapper mapper) {
        return request.find(IntegrationArtifactKind.GRAMMAR).map(value -> {
            try { return mapper.readValue(value.content(), GrammarDefinitionModel.class); }
            catch (Exception exception) { throw new TechnologyCompilationException("Could not read the selected grammar personalization", exception); }
        }).orElse(null);
    }

    /** Descripción: Ejecuta la operación «customization» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarTextualEditorCustomization customization(TechnologyCompilationRequest request, JsonMapper mapper) {
        return GrammarTextualEditorCustomization.from(grammar(request, mapper));
    }

    /** Descripción: Ejecuta la operación «neutralJson» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: c: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static String neutralJson(GrammarTextualEditorCustomization c, JsonMapper mapper) {
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(c); }
        catch (Exception exception) { throw new TechnologyCompilationException("Could not serialize textual editor personalization", exception); }
    }

    /** Descripción: Ejecuta la operación «vscodeRuntimeSource» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: c: dato de entrada necesario para la operación; languageId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static String vscodeRuntimeSource(GrammarTextualEditorCustomization c, String languageId) {
        List<String> keywordItems = java.util.Arrays.stream(c.keywords().split(",")).filter(v -> !v.isBlank()).map(TechnologyAdapterTextualCustomizationSupport::tsString).toList();
        StringBuilder roles = new StringBuilder("{");
        c.roleStyles().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (roles.length() > 1) roles.append(',');
            roles.append(tsString(entry.getKey())).append(':').append(tsString(entry.getValue()));
        });
        roles.append('}');
        StringBuilder symbols = new StringBuilder("{");
        c.symbolStyles().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (symbols.length() > 1) symbols.append(',');
            symbols.append(tsString(entry.getKey())).append(':').append(tsString(entry.getValue()));
        });
        symbols.append('}');
        StringBuilder hovers = new StringBuilder("{");
        c.hovers().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (hovers.length() > 1) hovers.append(',');
            hovers.append(tsString(entry.getKey())).append(":{title:").append(tsString(entry.getValue().title())).append(",description:").append(tsString(entry.getValue().description())).append('}');
        });
        hovers.append('}');
        return """
                import * as vscode from 'vscode';
                const LANGUAGE_ID=%s;
                const KEYWORDS=new Set<string>([%s]);
                const ROLE_STYLES:Record<string,string>=%s;
                const SYMBOL_STYLES:Record<string,string>=%s;
                const HOVERS:Record<string,{title:string;description:string}>=%s;
                const MAX_DECORATIONS=50000;
                const word=/[A-Za-z_][A-Za-z0-9_.:-]*/g;
                const stringLiteral=/(?:"[^"]*"|'[^']*')/g;
                const numberLiteral=/\\b[0-9]+(?:\\.[0-9]+)?\\b/g;
                function styleEncoded(encoded:string|undefined,fallback='#1f2937|0|0'){const [color,bold,italic]=(encoded||fallback).split('|');return {color,fontWeight:bold==='1'?'bold':undefined,fontStyle:italic==='1'?'italic':undefined};}
                function style(role:string){return styleEncoded(ROLE_STYLES[role]);}
                function terminalFallback(role:string){const names=role==='NUMBER'?['INT','INTEGER','NUMBER','NUM','DECIMAL','FLOAT','DOUBLE']:['ID','IDENTIFIER','IDENT','NAME','WORD'];for(const name of names){const key=`TERMINAL:${name}`;if(Object.hasOwn(SYMBOL_STYLES,key))return key;}return '';}
                function specificKey(role:string,value:string){const preferred=role==='TOKEN'?`TERMINAL:${value}`:`RULE:${value}`;const alternate=role==='TOKEN'?`RULE:${value}`:`TERMINAL:${value}`;return Object.hasOwn(SYMBOL_STYLES,preferred)?preferred:Object.hasOwn(SYMBOL_STYLES,alternate)?alternate:(role==='TOKEN'||role==='NUMBER'?terminalFallback(role):'');}
                function hoverFor(value:string){if(HOVERS[value])return HOVERS[value];const names=/^[0-9]+(?:\\.[0-9]+)?$/.test(value)?['INT','INTEGER','NUMBER','NUM','DECIMAL','FLOAT','DOUBLE']:['ID','IDENTIFIER','IDENT','NAME','WORD'];for(const name of names)if(HOVERS[name])return HOVERS[name];return undefined;}
                export function registerHydraTextualCustomization(context:vscode.ExtensionContext){
                  const decorations:Record<string,vscode.TextEditorDecorationType>={KEYWORD:vscode.window.createTextEditorDecorationType(style('KEYWORD')),TOKEN:vscode.window.createTextEditorDecorationType(style('TOKEN')),STRING:vscode.window.createTextEditorDecorationType(style('STRING')),NUMBER:vscode.window.createTextEditorDecorationType(style('NUMBER')),COMMENT:vscode.window.createTextEditorDecorationType(style('COMMENT'))};
                  const symbolDecorations:Record<string,vscode.TextEditorDecorationType>=Object.fromEntries(Object.entries(SYMBOL_STYLES).map(([key,encoded])=>[key,vscode.window.createTextEditorDecorationType(styleEncoded(encoded))]));
                  let timer:ReturnType<typeof setTimeout>|undefined;
                  const refreshNow=(editor:vscode.TextEditor|undefined)=>{if(!editor||editor.document.languageId!==LANGUAGE_ID)return;const ranges:Record<string,vscode.Range[]>={KEYWORD:[],TOKEN:[],STRING:[],NUMBER:[],COMMENT:[]};const symbolRanges:Record<string,vscode.Range[]>=Object.fromEntries(Object.keys(SYMBOL_STYLES).map(key=>[key,[]]));let count=0;const push=(role:string,value:string,range:vscode.Range)=>{if(count>=MAX_DECORATIONS)return;const key=specificKey(role,value);if(key)symbolRanges[key].push(range);else ranges[role].push(range);count++;};for(let line=0;line<editor.document.lineCount&&count<MAX_DECORATIONS;line++){const text=editor.document.lineAt(line).text;const commentAt=text.indexOf('//');const code=commentAt>=0?text.slice(0,commentAt):text;if(commentAt>=0)push('COMMENT','',new vscode.Range(line,commentAt,line,text.length));for(const pattern of [stringLiteral,numberLiteral,word]){pattern.lastIndex=0;for(let m=pattern.exec(code);m&&count<MAX_DECORATIONS;m=pattern.exec(code)){const role=pattern===stringLiteral?'STRING':pattern===numberLiteral?'NUMBER':KEYWORDS.has(m[0])?'KEYWORD':'TOKEN';push(role,m[0],new vscode.Range(line,m.index,line,m.index+m[0].length));}}}Object.entries(decorations).forEach(([role,decoration])=>editor.setDecorations(decoration,ranges[role]));Object.entries(symbolDecorations).forEach(([key,decoration])=>editor.setDecorations(decoration,symbolRanges[key]||[]));};
                  const refresh=(editor:vscode.TextEditor|undefined)=>{if(timer)clearTimeout(timer);timer=setTimeout(()=>refreshNow(editor),120);};
                  context.subscriptions.push(...Object.values(decorations),...Object.values(symbolDecorations),vscode.window.onDidChangeActiveTextEditor(refresh),vscode.workspace.onDidChangeTextDocument(e=>{if(e.document===vscode.window.activeTextEditor?.document)refresh(vscode.window.activeTextEditor);}),vscode.languages.registerHoverProvider({language:LANGUAGE_ID},{provideHover(document,position){const range=document.getWordRangeAtPosition(position,/[-A-Za-z0-9_.:]+/);if(!range)return;const value=document.getText(range);const custom=hoverFor(value);if(custom)return new vscode.Hover([new vscode.MarkdownString(`**${custom.title||value}**`),new vscode.MarkdownString(custom.description||'')],range);if(KEYWORDS.has(value))return new vscode.Hover(`**Palabra reservada** · ${value}`,range);return undefined;}}));
                  refresh(vscode.window.activeTextEditor);
                }
                """.formatted(tsString(languageId), String.join(",",keywordItems), roles, symbols, hovers);
    }

    /** Descripción: Ejecuta la operación «xtextHighlightingConfiguration» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: packageName: dato de entrada necesario para la operación; c: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String xtextHighlightingConfiguration(String packageName, GrammarTextualEditorCustomization c) {
        String foreground = xtextRgb(c.roleStyles().get("TOKEN"), rgb(c.foreground()));
        String background = rgb(c.background());
        String keyword = xtextRgb(c.roleStyles().get("KEYWORD"), "29,78,216");
        String string = xtextRgb(c.roleStyles().get("STRING"), "4,120,87");
        String number = xtextRgb(c.roleStyles().get("NUMBER"), "180,83,9");
        String comment = xtextRgb(c.roleStyles().get("COMMENT"), "100,116,139");
        String reference = xtextRgb(c.roleStyles().get("REFERENCE"), "180,83,9");
        String font = javaEscape(xtextFontName(c.fontFamily()));
        return """
                package %s.ui.highlighting;
                import org.eclipse.swt.SWT;
                import org.eclipse.swt.graphics.FontData;
                import org.eclipse.swt.graphics.RGB;
                import org.eclipse.xtext.ui.editor.syntaxcoloring.DefaultHighlightingConfiguration;
                import org.eclipse.xtext.ui.editor.syntaxcoloring.IHighlightingConfigurationAcceptor;
                import org.eclipse.xtext.ui.editor.utils.TextStyle;
                public final class HydraHighlightingConfiguration extends DefaultHighlightingConfiguration {
                  public static final String HYDRA_REFERENCE="hydra.reference";
                  private static final String FONT="%s";
                  private static final int SIZE=%d;
                  private static final RGB BACKGROUND=new RGB(%s);
                  @Override public void configure(IHighlightingConfigurationAcceptor acceptor){super.configure(acceptor);acceptor.acceptDefaultHighlighting(HYDRA_REFERENCE,"Referencia",style(new RGB(%s),false,false));}
                  @Override public TextStyle defaultTextStyle(){return style(new RGB(%s),false,false);}
                  @Override public TextStyle keywordTextStyle(){return style(new RGB(%s),%s,%s);}
                  @Override public TextStyle stringTextStyle(){return style(new RGB(%s),%s,%s);}
                  @Override public TextStyle numberTextStyle(){return style(new RGB(%s),%s,%s);}
                  @Override public TextStyle commentTextStyle(){return style(new RGB(%s),%s,%s);}
                  private static TextStyle style(RGB color,boolean bold,boolean italic){int swt=(bold?SWT.BOLD:SWT.NORMAL)|(italic?SWT.ITALIC:SWT.NORMAL);TextStyle s=new TextStyle();s.setColor(color);s.setBackgroundColor(BACKGROUND);s.setFontData(new FontData(FONT,SIZE,swt));s.setStyle(swt);return s;}
                }
                """.formatted(packageName, font, c.fontSize(), background, reference, foreground,
                    keyword, xtextFlag(c.roleStyles().get("KEYWORD"),1), xtextFlag(c.roleStyles().get("KEYWORD"),2),
                    string, xtextFlag(c.roleStyles().get("STRING"),1), xtextFlag(c.roleStyles().get("STRING"),2),
                    number, xtextFlag(c.roleStyles().get("NUMBER"),1), xtextFlag(c.roleStyles().get("NUMBER"),2),
                    comment, xtextFlag(c.roleStyles().get("COMMENT"),1), xtextFlag(c.roleStyles().get("COMMENT"),2));
    }

    /** Descripción: Ejecuta la operación «xtextHoverProvider» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: packageName: dato de entrada necesario para la operación; c: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String xtextHoverProvider(String packageName, GrammarTextualEditorCustomization c) {
        StringBuilder body = new StringBuilder();
        c.hovers().entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(entry -> body
                .append("if(\"").append(javaEscape(entry.getKey())).append("\".equals(type)||\"")
                .append(javaEscape(entry.getKey())).append("\".equals(label)) return \"")
                .append(javaEscape(entry.getValue().title())).append("\\n")
                .append(javaEscape(entry.getValue().description())).append("\";"));
        return """
                package %s.ui.hover;
                import org.eclipse.emf.ecore.EObject;
                import org.eclipse.xtext.ui.editor.hover.html.DefaultEObjectHoverProvider;
                public final class HydraHoverProvider extends DefaultEObjectHoverProvider {
                  @Override protected String getFirstLine(EObject object){if(object==null)return super.getFirstLine(object);String type=object.eClass().getName();var feature=object.eClass().getEStructuralFeature("name");String label=feature==null?"":String.valueOf(object.eGet(feature));%s return super.getFirstLine(object);}
                }
                """.formatted(packageName, body);
    }

    /** Descripción: Ejecuta la operación «neutralFiles» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: root: ruta o ubicación utilizada por la operación; request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static List<TechnologyGeneratedFile> neutralFiles(String root, TechnologyCompilationRequest request, JsonMapper mapper) {
        if (request.find(IntegrationArtifactKind.GRAMMAR).isEmpty()) return List.of();
        GrammarTextualEditorCustomization c = customization(request, mapper);
        List<String> sourceIds = TechnologyAdapterGenerationSupport.sourceIds(request);
        return List.of(TechnologyAdapterSupport.text(root + "/hydra/textual-editor-customization.json", "application/json", neutralJson(c, mapper), sourceIds));
    }

    /** Neutral full-stack manifest shared by standalone, IDE and external modeling targets. */
    /** Descripción: Ejecuta la operación «fullStackManifest» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static String fullStackManifest(TechnologyCompilationRequest request, JsonMapper mapper) {
        GrammarTextualEditorCustomization c = customization(request, mapper);
        return "{\n"
                + "  \"schemaVersion\": \"1.0\",\n"
                + "  \"textualCustomization\": " + (request.find(IntegrationArtifactKind.GRAMMAR).isPresent()) + ",\n"
                + "  \"validation\": " + has(request, IntegrationArtifactKind.VALIDATIONS) + ",\n"
                + "  \"quickFixes\": " + has(request, IntegrationArtifactKind.ACTIONS) + ",\n"
                + "  \"events\": " + has(request, IntegrationArtifactKind.EVENT_DEFINITIONS) + ",\n"
                + "  \"eventBindings\": " + has(request, IntegrationArtifactKind.EVENT_BINDINGS) + ",\n"
                + "  \"eventFlows\": " + has(request, IntegrationArtifactKind.EVENT_FLOWS) + ",\n"
                + "  \"gamification\": " + has(request, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS) + ",\n"
                + "  \"gamificationBindings\": " + has(request, IntegrationArtifactKind.GAMIFICATION_BINDINGS) + ",\n"
                + "  \"fontSize\": " + c.fontSize() + ",\n"
                + "  \"symbolStyleEntries\": " + c.symbolStyles().size() + ",\n"
                + "  \"hoverEntries\": " + c.hovers().size() + "\n"
                + "}\n";
    }

    /** TypeScript coordinator that consumes every neutral textual layer through host bridges. */
    /** Descripción: Ejecuta la operación «typescriptFullStackBridgeSource» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static String typescriptFullStackBridgeSource(TechnologyCompilationRequest request, JsonMapper mapper) {
        GrammarTextualEditorCustomization c = customization(request, mapper);
        String customization = neutralJson(c, mapper).replace("`", "\\`").replace("${", "\\${");
        return """
                export type HydraTextualCustomization=Record<string,unknown>;
                export interface HydraTextualFullStackHost {
                  applyCustomization?(value:HydraTextualCustomization):void;
                  showHover?(token:string):unknown;
                  validate?(model:unknown):Promise<readonly unknown[]>|readonly unknown[];
                  applyQuickFix?(model:unknown,diagnosticId:string,actionId:string,parameters:Record<string,unknown>):Promise<unknown>|unknown;
                  publishEvent?(type:string,elementId:string,payload:Record<string,unknown>):Promise<void>|void;
                  runEventFlow?(type:string,payload:Record<string,unknown>):Promise<unknown>|unknown;
                  gamificationState?(actorId:string,event?:unknown):Promise<unknown>|unknown;
                  notifyFeedback?(feedback:unknown):void;
                }
                export const hydraTextualLayers={validation:%s,quickFixes:%s,events:%s,eventBindings:%s,eventFlows:%s,gamification:%s,gamificationBindings:%s} as const;
                export const hydraTextualCustomization:HydraTextualCustomization=JSON.parse(`%s`);
                export function createHydraTextualFullStack(host:HydraTextualFullStackHost){
                  host.applyCustomization?.(hydraTextualCustomization);
                  return {
                    customization:hydraTextualCustomization,
                    layers:hydraTextualLayers,
                    hover:(token:string)=>host.showHover?.(token),
                    validate:(model:unknown)=>hydraTextualLayers.validation&&host.validate?host.validate(model):[],
                    applyQuickFix:(model:unknown,diagnosticId:string,actionId:string,parameters:Record<string,unknown>={})=>{if(!hydraTextualLayers.quickFixes||!host.applyQuickFix)throw new Error('HYDRA quick fixes are not available in this package');return host.applyQuickFix(model,diagnosticId,actionId,parameters);},
                    afterCommit:async(type:string,elementId:string,payload:Record<string,unknown>,actorId='local')=>{if(hydraTextualLayers.events&&host.publishEvent)await host.publishEvent(type,elementId,payload);const flow=hydraTextualLayers.eventFlows&&host.runEventFlow?await host.runEventFlow(type,payload):undefined;const gamification=hydraTextualLayers.gamification&&host.gamificationState?await host.gamificationState(actorId,{type,elementId,payload,flow}):undefined;const feedback=(gamification as {feedback?:unknown[]}|undefined)?.feedback??[];if(host.notifyFeedback)for(const item of feedback)host.notifyFeedback(item);return {flow,gamification,feedback};}
                  } as const;
                }
                """.formatted(
                    has(request, IntegrationArtifactKind.VALIDATIONS), has(request, IntegrationArtifactKind.ACTIONS),
                    has(request, IntegrationArtifactKind.EVENT_DEFINITIONS), has(request, IntegrationArtifactKind.EVENT_BINDINGS),
                    has(request, IntegrationArtifactKind.EVENT_FLOWS), has(request, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS),
                    has(request, IntegrationArtifactKind.GAMIFICATION_BINDINGS), customization);
    }

    /** Python coordinator used by BESSER SDK and headless Python consumers. */
    /** Descripción: Ejecuta la operación «pythonFullStackBridgeSource» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static String pythonFullStackBridgeSource(TechnologyCompilationRequest request, JsonMapper mapper) {
        String customization = neutralJson(customization(request, mapper), mapper).replace("'''", "\\u0027\\u0027\\u0027");
        return """
                import json
                CUSTOMIZATION=json.loads(r'''%s''')
                LAYERS={'validation':%s,'quick_fixes':%s,'events':%s,'event_bindings':%s,'event_flows':%s,'gamification':%s,'gamification_bindings':%s}
                class HydraTextualFullStack:
                    def __init__(self,host):
                        self.host=host
                        apply=getattr(host,'apply_customization',None)
                        if apply: apply(CUSTOMIZATION)
                    def hover(self,token):
                        fn=getattr(self.host,'show_hover',None); return fn(token) if fn else None
                    def validate(self,model):
                        fn=getattr(self.host,'validate',None); return fn(model) if LAYERS['validation'] and fn else []
                    def apply_quick_fix(self,model,diagnostic_id,action_id,parameters=None):
                        fn=getattr(self.host,'apply_quick_fix',None)
                        if not LAYERS['quick_fixes'] or not fn: raise RuntimeError('HYDRA quick fixes are not available in this package')
                        return fn(model,diagnostic_id,action_id,parameters or {})
                    def after_commit(self,event_type,element_id,payload,actor_id='local'):
                        publish=getattr(self.host,'publish_event',None)
                        if LAYERS['events'] and publish: publish(event_type,element_id,payload)
                        flow_fn=getattr(self.host,'run_event_flow',None); flow=flow_fn(event_type,payload) if LAYERS['event_flows'] and flow_fn else None
                        game_fn=getattr(self.host,'gamification_state',None); game=game_fn(actor_id,{'type':event_type,'elementId':element_id,'payload':payload,'flow':flow}) if LAYERS['gamification'] and game_fn else None
                        feedback=(game or {}).get('feedback',[]) if isinstance(game,dict) else []
                        notify=getattr(self.host,'notify_feedback',None)
                        if notify:
                            for item in feedback: notify(item)
                        return {'flow':flow,'gamification':game,'feedback':feedback}
                """.formatted(customization,
                    pyBool(has(request, IntegrationArtifactKind.VALIDATIONS)), pyBool(has(request, IntegrationArtifactKind.ACTIONS)),
                    pyBool(has(request, IntegrationArtifactKind.EVENT_DEFINITIONS)), pyBool(has(request, IntegrationArtifactKind.EVENT_BINDINGS)),
                    pyBool(has(request, IntegrationArtifactKind.EVENT_FLOWS)), pyBool(has(request, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS)),
                    pyBool(has(request, IntegrationArtifactKind.GAMIFICATION_BINDINGS)));
    }

    /** Java coordinator for Eclipse/Sirius/GLSP hosts. */
    /** Descripción: Ejecuta la operación «javaFullStackBridgeSource» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: packageName: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static String javaFullStackBridgeSource(String packageName, TechnologyCompilationRequest request, JsonMapper mapper) {
        String customization = javaEscape(neutralJson(customization(request, mapper), mapper));
        return """
                package %s;
                import java.util.Map;
                public final class HydraTextualFullStack {
                  public static final String CUSTOMIZATION_JSON="%s";
                  public static final boolean VALIDATION=%s, QUICK_FIXES=%s, EVENTS=%s, EVENT_BINDINGS=%s, EVENT_FLOWS=%s, GAMIFICATION=%s, GAMIFICATION_BINDINGS=%s;
                  public interface Host {
                    default void applyCustomization(String json){}
                    default Object hover(String token){return null;}
                    default Object validate(Object model){return java.util.List.of();}
                    default Object applyQuickFix(Object model,String diagnosticId,String actionId,Map<String,Object> parameters){throw new UnsupportedOperationException();}
                    default void publishEvent(String type,String elementId,Map<String,Object> payload){}
                    default Object runEventFlow(String type,Map<String,Object> payload){return null;}
                    default Object gamificationState(String actorId,Object event){return null;}
                    default void notifyFeedback(Object feedback){}
                  }
                  private final Host host;
                  public HydraTextualFullStack(Host host){this.host=host;host.applyCustomization(CUSTOMIZATION_JSON);}
                  public Object hover(String token){return host.hover(token);}
                  public Object validate(Object model){return VALIDATION?host.validate(model):java.util.List.of();}
                  public Object applyQuickFix(Object model,String diagnosticId,String actionId,Map<String,Object> parameters){if(!QUICK_FIXES)throw new IllegalStateException("HYDRA quick fixes are not available in this package");return host.applyQuickFix(model,diagnosticId,actionId,parameters);}
                  public Result afterCommit(String type,String elementId,Map<String,Object> payload,String actorId){if(EVENTS)host.publishEvent(type,elementId,payload);Object flow=EVENT_FLOWS?host.runEventFlow(type,payload):null;Object game=GAMIFICATION?host.gamificationState(actorId,Map.of("type",type,"elementId",elementId,"payload",payload,"flow",flow==null?"":flow)):null;java.util.List<?> feedback=game instanceof Map<?,?> map&&map.get("feedback") instanceof java.util.List<?> list?list:java.util.List.of();feedback.forEach(host::notifyFeedback);return new Result(flow,game,feedback);}
                  public record Result(Object eventFlow,Object gamification,java.util.List<?> feedback){}
                }
                """.formatted(packageName, customization,
                    has(request, IntegrationArtifactKind.VALIDATIONS), has(request, IntegrationArtifactKind.ACTIONS),
                    has(request, IntegrationArtifactKind.EVENT_DEFINITIONS), has(request, IntegrationArtifactKind.EVENT_BINDINGS),
                    has(request, IntegrationArtifactKind.EVENT_FLOWS), has(request, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS),
                    has(request, IntegrationArtifactKind.GAMIFICATION_BINDINGS));
    }

    /** Descripción: Ejecuta la operación «fullStackNeutralFiles» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: root: ruta o ubicación utilizada por la operación; request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static List<TechnologyGeneratedFile> fullStackNeutralFiles(String root, TechnologyCompilationRequest request, JsonMapper mapper) {
        if (request.find(IntegrationArtifactKind.GRAMMAR).isEmpty()) return List.of();
        List<String> sourceIds = TechnologyAdapterGenerationSupport.sourceIds(request);
        List<TechnologyGeneratedFile> files = new ArrayList<>(neutralFiles(root, request, mapper));
        files.add(TechnologyAdapterSupport.text(root + "/hydra/textual-full-stack.json", "application/json", fullStackManifest(request, mapper), sourceIds));
        return files;
    }

    /** Descripción: Evalúa la condición «has» sin modificar el estado externo. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean has(TechnologyCompilationRequest request, IntegrationArtifactKind kind){return request.find(kind).isPresent();}
    /** Descripción: Ejecuta la operación «pyBool» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String pyBool(boolean value){return value?"True":"False";}

    /** Descripción: Ejecuta la operación «xtextFontName» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: fontFamily: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String xtextFontName(String fontFamily){String value=fontFamily==null?"Monospace":fontFamily.split(",",2)[0].trim().replace("\"","").replace("'","");return value.isBlank()?"Monospace":value;}
    /** Descripción: Ejecuta la operación «xtextRgb» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: encoded: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String xtextRgb(String encoded,String fallback){String value=encoded==null?"":encoded.split("\\|",-1)[0];if(!value.matches("#[0-9A-Fa-f]{6}"))return fallback;return rgb(value);}
    /** Descripción: Ejecuta la operación «rgb» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String rgb(String value){String v=value==null?"#1f2937":value;return Integer.parseInt(v.substring(1,3),16)+","+Integer.parseInt(v.substring(3,5),16)+","+Integer.parseInt(v.substring(5,7),16);}
    /** Descripción: Ejecuta la operación «xtextFlag» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: encoded: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String xtextFlag(String encoded,int index){String[] parts=encoded==null?new String[0]:encoded.split("\\|",-1);return parts.length>index&&"1".equals(parts[index])?"true":"false";}
    /** Descripción: Ejecuta la operación «tsString» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String tsString(String value){return "'"+(value==null?"":value.replace("\\","\\\\").replace("'","\\'").replace("\r"," ").replace("\n","\\n"))+"'";}
    /** Descripción: Ejecuta la operación «javaEscape» correspondiente a la responsabilidad de TechnologyAdapterTextualCustomizationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String javaEscape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"").replace("\r"," ").replace("\n","\\n");}
}
