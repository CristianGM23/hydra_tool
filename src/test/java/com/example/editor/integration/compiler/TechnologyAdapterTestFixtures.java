package com.example.editor.integration.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.definition.EditorDefinitionTestFixtures;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.integration.InjectionPolicy;
import com.example.editor.integration.InjectionStrategy;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationArtifactSelection;
import com.example.editor.integration.IntegrationBuildSystem;
import com.example.editor.integration.IntegrationDeliveryMode;
import com.example.editor.integration.IntegrationFeature;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationTarget;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationResult;
import tools.jackson.databind.json.JsonMapper;

public final class TechnologyAdapterTestFixtures {
    public static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    public static TechnologyCompilationRequest emfRequest(){return request(IntegrationTechnology.EMF, false);}
    public static TechnologyCompilationRequest siriusRequest(){return request(IntegrationTechnology.SIRIUS_DESKTOP, true);}
    public static TechnologyCompilationRequest xtextRequest(){return request(IntegrationTechnology.XTEXT, false);}
    public static TechnologyCompilationRequest langiumRequest(){return request(IntegrationTechnology.LANGIUM, false);}
    public static TechnologyCompilationRequest langiumGrammarFirstRequest(){return grammarFirstRequest(IntegrationTechnology.LANGIUM, GrammarTechnology.LANGIUM, "grammar Main\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n");}
    public static TechnologyCompilationRequest xtextGrammarFirstRequest(){return grammarFirstRequest(IntegrationTechnology.XTEXT, GrammarTechnology.XTEXT, "grammar org.example.Main with org.eclipse.xtext.common.Terminals\ngenerate main \"http://example.org/main\"\nModel: elements+=Element*;\nElement: 'element' name=ID;\n");}
    public static TechnologyCompilationRequest vscodeGrammarFirstRequest(){return grammarFirstRequest(IntegrationTechnology.VSCODE_EXTENSION, GrammarTechnology.LANGIUM, "grammar Main\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\n");}
    public static TechnologyCompilationRequest eclipseGrammarFirstRequest(){return grammarFirstRequest(IntegrationTechnology.ECLIPSE_MODELING_DISTRIBUTION, GrammarTechnology.XTEXT, "grammar org.example.Main with org.eclipse.xtext.common.Terminals\nModel: elements+=Element*;\n");}
    public static TechnologyCompilationRequest glspRequest(){return request(IntegrationTechnology.ECLIPSE_GLSP, true);}
    public static TechnologyCompilationRequest besserSdkRequest(){return request(IntegrationTechnology.BESSER_SDK, false);}
    public static TechnologyCompilationRequest besserSdkVisualRequest(){return request(IntegrationTechnology.BESSER_SDK, true);}
    public static TechnologyCompilationRequest besserWebRequest(){return request(IntegrationTechnology.BESSER_WEB_MODELING_EDITOR, true);}
    public static TechnologyCompilationRequest eclipseDistributionRequest(){return request(IntegrationTechnology.ECLIPSE_MODELING_DISTRIBUTION, true);}
    public static TechnologyCompilationRequest eclipseXtextDistributionRequest(){
        try {
            TechnologyCompilationRequest source=eclipseDistributionRequest();
            GrammarDefinitionModel grammar=new GrammarDefinitionModel("1.0","people-xtext-language","People Xtext language",GrammarTechnology.XTEXT,
                    GrammarAuthoringMode.METAMODEL_FIRST,"people","","","Model",List.of(),GrammarSemanticBindingMap.empty(),"",Map.of(),Map.of());
            List<IntegrationArtifactSelection> selections=new ArrayList<>(source.profile().artifacts());
            selections.add(new IntegrationArtifactSelection(IntegrationArtifactKind.GRAMMAR,grammar.id(),true));
            List<CanonicalArtifactContent> artifacts=new ArrayList<>(source.artifacts());
            artifacts.add(new CanonicalArtifactContent(IntegrationArtifactKind.GRAMMAR,grammar.id(),"application/json",MAPPER.writeValueAsBytes(grammar)));
            var p=source.profile();
            IntegrationProfileModel profile=new IntegrationProfileModel(p.schemaVersion(),p.id(),p.metamodelId(),p.name(),p.target(),p.deliveryMode(),p.features(),selections,p.mappings(),p.injectionPolicy(),p.requiredCapabilities(),p.annotations());
            return new TechnologyCompilationRequest(profile,artifacts,source.generatorVersion(),source.options());
        } catch(Exception exception){ throw new IllegalStateException(exception); }
    }
    public static TechnologyCompilationRequest vscodeExtensionRequest(){return request(IntegrationTechnology.VSCODE_EXTENSION, true);}
    public static TechnologyCompilationRequest mxgraphDrawioRequest(){return request(IntegrationTechnology.MXGRAPH_DRAWIO, true);}

    public static TechnologyCompilationRequest besserSdkFullTextualRequest(){return withFullTextualStack(besserSdkRequest());}
    public static TechnologyCompilationRequest besserWebFullTextualRequest(){return withFullTextualStack(besserWebRequest());}
    public static TechnologyCompilationRequest glspFullTextualRequest(){return withFullTextualStack(glspRequest());}
    public static TechnologyCompilationRequest siriusFullTextualRequest(){return withFullTextualStack(siriusRequest());}

    public static TechnologyCompilationRequest xtextCustomizedRequest(){return withTextualCustomization(xtextRequest());}
    public static TechnologyCompilationRequest langiumCustomizedRequest(){return withTextualCustomization(langiumRequest());}
    public static TechnologyCompilationRequest vscodeCustomizedRequest(){return withTextualCustomization(vscodeExtensionRequest());}
    public static TechnologyCompilationRequest eclipseCustomizedRequest(){return withTextualCustomization(eclipseDistributionRequest());}

    public static TechnologyCompilationRequest withTextualCustomization(TechnologyCompilationRequest source){
        try{
            Map<String,String> settings = Map.ofEntries(
                    Map.entry("hydra.textual.fontFamily", "Consolas, monospace"),
                    Map.entry("hydra.textual.fontSize", "17"),
                    Map.entry("hydra.textual.lineHeight", "25"),
                    Map.entry("hydra.textual.foreground", "#102030"),
                    Map.entry("hydra.textual.background", "#f8fafc"),
                    Map.entry("hydra.textual.keywords", "person,company"),
                    Map.entry("hydra.textual.role.KEYWORD", "#123456|1|0"),
                    Map.entry("hydra.textual.role.TOKEN", "#234567|0|0"),
                    Map.entry("hydra.textual.role.STRING", "#345678|0|1"),
                    Map.entry("hydra.textual.role.NUMBER", "#456789|0|0"),
                    Map.entry("hydra.textual.role.COMMENT", "#56789a|0|1"),
                    Map.entry("hydra.textual.symbol.RULE.Person", "#334455|1|0"),
                    Map.entry("hydra.textual.symbol.TERMINAL.ID", "#556677|0|1"),
                    Map.entry("hydra.textual.hover.Person.title", "Persona"),
                    Map.entry("hydra.textual.hover.Person.description", "Elemento persona del lenguaje."));
            GrammarDefinitionModel grammar = new GrammarDefinitionModel("1.0", "people-language", "People language", GrammarTechnology.LANGIUM,
                    GrammarAuthoringMode.METAMODEL_FIRST, "people", "", "", "Model", List.of(), GrammarSemanticBindingMap.empty(), "", settings, Map.of());
            List<IntegrationArtifactSelection> selections = new ArrayList<>(source.profile().artifacts());
            selections.add(new IntegrationArtifactSelection(IntegrationArtifactKind.GRAMMAR, grammar.id(), true));
            List<CanonicalArtifactContent> artifacts = new ArrayList<>(source.artifacts());
            artifacts.add(new CanonicalArtifactContent(IntegrationArtifactKind.GRAMMAR, grammar.id(), "application/json", MAPPER.writeValueAsBytes(grammar)));
            var p=source.profile();
            IntegrationProfileModel profile = new IntegrationProfileModel(p.schemaVersion(),p.id(),p.metamodelId(),p.name(),p.target(),p.deliveryMode(),p.features(),selections,p.mappings(),p.injectionPolicy(),p.requiredCapabilities(),p.annotations());
            return new TechnologyCompilationRequest(profile, artifacts, source.generatorVersion(), source.options());
        }catch(Exception exception){throw new IllegalStateException(exception);}
    }

    public static TechnologyCompilationRequest withFullTextualStack(TechnologyCompilationRequest source){
        try{
            TechnologyCompilationRequest customized = withTextualCustomization(source);
            List<IntegrationArtifactSelection> selections = new ArrayList<>(customized.profile().artifacts());
            List<CanonicalArtifactContent> artifacts = new ArrayList<>(customized.artifacts());
            List<IntegrationArtifactKind> kinds = List.of(IntegrationArtifactKind.TEXTUAL_EDITOR_DEFINITION, IntegrationArtifactKind.TEXTUAL_SYNTAX_STYLE,
                    IntegrationArtifactKind.EXPRESSIONS, IntegrationArtifactKind.VALIDATIONS, IntegrationArtifactKind.ACTIONS,
                    IntegrationArtifactKind.OPERATION_BEHAVIORS, IntegrationArtifactKind.EVENT_DEFINITIONS, IntegrationArtifactKind.EVENT_BINDINGS,
                    IntegrationArtifactKind.EVENT_FLOWS, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS, IntegrationArtifactKind.GAMIFICATION_BINDINGS);
            for(IntegrationArtifactKind kind:kinds){
                if(artifacts.stream().anyMatch(a->a.kind()==kind)) continue;
                String id="full-"+kind.name().toLowerCase();
                selections.add(new IntegrationArtifactSelection(kind,id,true));
                artifacts.add(new CanonicalArtifactContent(kind,id,"application/json",("{\"schemaVersion\":\"1.0\",\"id\":\""+id+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            var p=customized.profile();
            Set<IntegrationFeature> features=Set.of(IntegrationFeature.VALIDATION_RUNTIME,IntegrationFeature.QUICK_FIX_RUNTIME,IntegrationFeature.OPERATION_RUNTIME,
                    IntegrationFeature.EVENT_PUBLICATION,IntegrationFeature.EVENT_CONSUMPTION,IntegrationFeature.EVENT_FLOW_RUNTIME,IntegrationFeature.GAMIFICATION_RUNTIME);
            IntegrationProfileModel profile = new IntegrationProfileModel(p.schemaVersion(),p.id(),p.metamodelId(),p.name(),p.target(),p.deliveryMode(),features,selections,p.mappings(),p.injectionPolicy(),p.requiredCapabilities(),p.annotations());
            return new TechnologyCompilationRequest(profile,artifacts,customized.generatorVersion(),customized.options());
        }catch(Exception exception){throw new IllegalStateException(exception);}
    }

    public static TechnologyCompilationRequest request(IntegrationTechnology technology, boolean includeEditor){
        try{
            List<IntegrationArtifactSelection> selections = new ArrayList<>();
            List<CanonicalArtifactContent> artifacts = new ArrayList<>();
            selections.add(new IntegrationArtifactSelection(IntegrationArtifactKind.METAMODEL, "people", true));
            artifacts.add(new CanonicalArtifactContent(IntegrationArtifactKind.METAMODEL, "people", "application/json", MAPPER.writeValueAsBytes(EditorDefinitionTestFixtures.metamodel())));
            if(includeEditor){
                selections.add(new IntegrationArtifactSelection(IntegrationArtifactKind.EDITOR_DEFINITION, "people-editor", true));
                artifacts.add(new CanonicalArtifactContent(IntegrationArtifactKind.EDITOR_DEFINITION, "people-editor", "application/json", MAPPER.writeValueAsBytes(EditorDefinitionTestFixtures.editor())));
            }
            IntegrationTargetKind targetKind = switch(technology){
                case SIRIUS_DESKTOP, ECLIPSE_GLSP, BESSER_WEB_MODELING_EDITOR, MXGRAPH_DRAWIO -> IntegrationTargetKind.GRAPHICAL_MODELING_EDITOR;
                case ECLIPSE_MODELING_DISTRIBUTION -> IntegrationTargetKind.IDE_DISTRIBUTION;
                case VSCODE_EXTENSION -> IntegrationTargetKind.IDE_EXTENSION;
                case XTEXT, LANGIUM -> IntegrationTargetKind.TEXTUAL_LANGUAGE_TOOLING;
                case BESSER_SDK -> IntegrationTargetKind.LOW_CODE_PLATFORM;
                default -> IntegrationTargetKind.MODELING_RUNTIME;
            };
            IntegrationBuildSystem buildSystem = switch(technology){
                case SIRIUS_DESKTOP, ECLIPSE_MODELING_DISTRIBUTION -> IntegrationBuildSystem.TYCHO;
                case XTEXT, EMF -> IntegrationBuildSystem.MAVEN;
                case LANGIUM, ECLIPSE_GLSP, BESSER_WEB_MODELING_EDITOR, VSCODE_EXTENSION, MXGRAPH_DRAWIO -> IntegrationBuildSystem.NPM;
                case BESSER_SDK -> IntegrationBuildSystem.PIP;
                default -> IntegrationBuildSystem.NONE;
            };
            String moduleName = switch(technology){
                case SIRIUS_DESKTOP -> "people-sirius";
                case XTEXT -> "people-xtext";
                case LANGIUM -> "people-langium";
                case ECLIPSE_GLSP -> "people-glsp";
                case BESSER_SDK -> "people-besser-sdk";
                case BESSER_WEB_MODELING_EDITOR -> "people-besser-wme";
                case ECLIPSE_MODELING_DISTRIBUTION -> "people-eclipse-modeling";
                case VSCODE_EXTENSION -> "people-vscode";
                case MXGRAPH_DRAWIO -> "people-mxgraph-drawio";
                default -> "people-emf";
            };
            IntegrationTarget target = new IntegrationTarget(technology, targetKind, "", "host", moduleName,
                    buildSystem, List.of(), List.of(), "", Map.of());
            InjectionStrategy strategy = switch(technology){
                case SIRIUS_DESKTOP, ECLIPSE_MODELING_DISTRIBUTION -> InjectionStrategy.ADD_PLUGIN;
                case ECLIPSE_GLSP, LANGIUM, BESSER_SDK, BESSER_WEB_MODELING_EDITOR, VSCODE_EXTENSION, MXGRAPH_DRAWIO -> InjectionStrategy.ADD_PACKAGE;
                default -> InjectionStrategy.ADD_MODULE;
            };
            InjectionPolicy policy = new InjectionPolicy(strategy, "generated-integration", target.moduleName(), true, true, true, false, false, true);
            IntegrationProfileModel profile = new IntegrationProfileModel("1.0", "profile-" + technology.name().toLowerCase(), "people", "Profile",
                    target, IntegrationDeliveryMode.GENERATED_MODULE, Set.of(), selections, List.of(), policy, Set.of(), Map.of());
            return new TechnologyCompilationRequest(profile, artifacts, "test-generator", Map.of());
        }catch(RuntimeException exception){throw exception;}
    }


    private static TechnologyCompilationRequest grammarFirstRequest(IntegrationTechnology technology, GrammarTechnology grammarTechnology, String content){
        try{
            TechnologyCompilationRequest base=request(technology,false);
            GrammarSourceDocument document=new GrammarSourceDocument("main","main."+(grammarTechnology==GrammarTechnology.XTEXT?"xtext":"langium"),grammarTechnology.name().toLowerCase(),content,"",true,false);
            GrammarDefinitionModel grammar=new GrammarDefinitionModel("1.0","main-language","Main",grammarTechnology,GrammarAuthoringMode.GRAMMAR_FIRST,"","","main","Model",List.of(document),GrammarSemanticBindingMap.empty(),"",Map.of(),Map.of());
            List<IntegrationArtifactSelection> selections=List.of(new IntegrationArtifactSelection(IntegrationArtifactKind.GRAMMAR,grammar.id(),true));
            List<CanonicalArtifactContent> artifacts=List.of(new CanonicalArtifactContent(IntegrationArtifactKind.GRAMMAR,grammar.id(),"application/json",MAPPER.writeValueAsBytes(grammar)));
            var p=base.profile();
            IntegrationProfileModel profile=new IntegrationProfileModel(p.schemaVersion(),p.id()+"-grammar-first","","grammar."+grammar.id(),p.name(),p.target(),p.deliveryMode(),p.features(),selections,p.mappings(),p.injectionPolicy(),p.requiredCapabilities(),p.annotations());
            return new TechnologyCompilationRequest(profile,artifacts,base.generatorVersion(),base.options());
        }catch(Exception exception){throw new IllegalStateException(exception);}
    }

    public static EcoreMetamodelAdapter ecoreAdapter(){
        return new EcoreMetamodelAdapter(){
            @Override public TransformationResult<org.eclipse.emf.ecore.EPackage> toEPackage(MetaModel metamodel){throw new UnsupportedOperationException();}
            @Override public TransformationResult<byte[]> toBytes(MetaModel metamodel){return new TransformationResult<>(("<ecore id=\"" + metamodel.id() + "\"/>").getBytes(java.nio.charset.StandardCharsets.UTF_8), List.of());}
            @Override public TransformationResult<MetaModel> fromPath(java.nio.file.Path path){throw new UnsupportedOperationException();}
            @Override public TransformationResult<MetaModel> fromEPackage(org.eclipse.emf.ecore.EPackage ePackage, String artifactName){throw new UnsupportedOperationException();}
        };
    }
    private TechnologyAdapterTestFixtures(){ }
}
