package com.example.editor.integration.adapter.besser;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationDependencyAction;
import com.example.editor.integration.IntegrationDependencyChange;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.compiler.TechnologyAdapter;
import com.example.editor.integration.compiler.TechnologyAdapterGenerationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterRuntimeLifecycleSupport;
import com.example.editor.integration.compiler.TechnologyAdapterSupport;
import com.example.editor.integration.compiler.TechnologyAdapterTextualCustomizationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterSelfContainedDependencySupport;
import com.example.editor.integration.compiler.TechnologyAdapterVisualFullStackSupport;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.Multiplicity;
import tools.jackson.databind.json.JsonMapper;

/** Genera un paquete Python BESSER SDK con un modelo B-UML nativo. */
@Component
public class BesserSdkTechnologyAdapter implements TechnologyAdapter {
    private static final Set<IntegrationArtifactKind> SUPPORTED = EnumSet.of(
            IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.EXPRESSIONS,
            IntegrationArtifactKind.VALIDATIONS, IntegrationArtifactKind.ACTIONS,
            IntegrationArtifactKind.OPERATION_BEHAVIORS, IntegrationArtifactKind.EVENT_DEFINITIONS,
            IntegrationArtifactKind.EVENT_BINDINGS, IntegrationArtifactKind.EVENT_FLOWS,
            IntegrationArtifactKind.GAMIFICATION_DEFINITIONS, IntegrationArtifactKind.GAMIFICATION_BINDINGS,
            IntegrationArtifactKind.GRAMMAR, IntegrationArtifactKind.TEXTUAL_EDITOR_DEFINITION, IntegrationArtifactKind.TEXTUAL_SYNTAX_STYLE,
            IntegrationArtifactKind.EDITOR_DEFINITION);
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de BesserSdkTechnologyAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public BesserSdkTechnologyAdapter(JsonMapper mapper){this.mapper = mapper;}
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.BESSER_SDK;}
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> supportedArtifactKinds(){return Set.copyOf(SUPPORTED);}

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request){
        try{
            MetaModel metamodel = mapper.readValue(request.require(IntegrationArtifactKind.METAMODEL).content(), MetaModel.class);
            String root = TechnologyAdapterSupport.generatedRoot(request, metamodel.id() + "-besser-sdk");
            String module = TechnologyAdapterGenerationSupport.pythonModule(request.profile().target().settings().get("pythonModule"),
                    TechnologyAdapterSupport.slug(metamodel.id()).replace('-', '_') + "_hydra");
            String besserVersion = request.profile().target().settings().getOrDefault("besserVersion", "7.13.0");
            List<String> sourceIds = TechnologyAdapterGenerationSupport.sourceIds(request);
            List<TechnologyGeneratedFile> files = new ArrayList<>();
            files.add(TechnologyAdapterSupport.text(root + "/pyproject.toml", "text/plain", pyproject(module, besserVersion), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/__init__.py", "text/x-python", initSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/model.py", "text/x-python", modelSource(metamodel), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/validation.py", "text/x-python", validationSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/quickfix.py", "text/x-python", quickFixSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/operations.py", "text/x-python", operationSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/events.py", "text/x-python", eventSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/semantic_runtime.py", "text/x-python", TechnologyAdapterRuntimeLifecycleSupport.pythonSemanticRuntimeSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/runtime.py", "text/x-python", runtimeSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/runtime_activation.py", "text/x-python",
                        TechnologyAdapterGenerationSupport.pythonRuntimeActivationSource(request), sourceIds));
            if(request.find(IntegrationArtifactKind.GRAMMAR).isPresent()){
                files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/textual_editor.py", "text/x-python",
                        TechnologyAdapterTextualCustomizationSupport.pythonFullStackBridgeSource(request, mapper), sourceIds));
                files.addAll(TechnologyAdapterTextualCustomizationSupport.fullStackNeutralFiles(root + "/src/" + module, request, mapper));
            }
            if(TechnologyAdapterVisualFullStackSupport.available(request)){
                files.add(TechnologyAdapterSupport.text(root + "/src/" + module + "/visual_editor.py", "text/x-python",
                        TechnologyAdapterVisualFullStackSupport.pythonBridgeSource(request), sourceIds));
                files.addAll(TechnologyAdapterVisualFullStackSupport.neutralFiles(root + "/src/" + module, request, mapper));
            }
            files.add(TechnologyAdapterSupport.text(root + "/tests/test_generated_model.py", "text/x-python", testSource(module, TechnologyAdapterVisualFullStackSupport.available(request)), sourceIds));
            files.addAll(TechnologyAdapterGenerationSupport.canonicalDocuments(root, "src/" + module + "/hydra", request));
            files.add(TechnologyAdapterSupport.text(root + "/README.md", "text/markdown", readme(request, metamodel, module), sourceIds));
            files.addAll(TechnologyAdapterSelfContainedDependencySupport.pythonWheelhouseFiles(root, module, List.of("besser==" + besserVersion), sourceIds));
            List<IntegrationDiagnostic> diagnostics = new ArrayList<>(TechnologyAdapterSupport.unsupportedDiagnostics(request, SUPPORTED, "BESSER SDK"));
            List<IntegrationDependencyChange> dependencies = List.of(dependency("besser", besserVersion), dependency("pytest", "8.4.0"));
            List<IntegrationVerificationStep> verification = List.of(
                    new IntegrationVerificationStep("install-besser-package", "Install the generated BESSER package and test dependencies", root,
                            List.of("python", "-m", "pip", "install", "-e", ".[test]"), 0, true),
                    new IntegrationVerificationStep("test-besser-package", "Import and test the generated BESSER package", root,
                            List.of("python", "-m", "pytest", "-q"), 0, true));
            return new TechnologyCompilationResult(IntegrationTechnology.BESSER_SDK, files, dependencies,
                    List.of("Create a Python 3.10-3.12 virtual environment", "Build the wheelhouse once with python offline/prepare-wheelhouse.py", "Install missing runtime dependencies offline with python offline/install-offline.py", "Install the generated package and run the B-UML model"),
                    List.of("Rollback by uninstalling the generated Python package", "Restore host dependency files from the HYDRA backup"),
                    verification, diagnostics, Map.of("generatorVersion", request.generatorVersion(), "nativeLanguage", "Python/B-UML",
                            "moduleRoot", root, "pythonModule", module, "besserVersion", besserVersion));
        }catch(RuntimeException exception){
            if(exception instanceof TechnologyCompilationException){throw exception;}
            throw new TechnologyCompilationException("Could not generate BESSER SDK integration", exception);
        }
    }

    /** Descripción: Ejecuta la operación «dependency» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: coordinate: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDependencyChange dependency(String coordinate, String version){return new IntegrationDependencyChange("pip", coordinate, version, "runtime", IntegrationDependencyAction.ADD);}
    /** Descripción: Ejecuta la operación «pyproject» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: module: dato de entrada necesario para la operación; besserVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String pyproject(String module, String besserVersion){return """
            [build-system]
            requires = ["setuptools>=75", "wheel"]
            build-backend = "setuptools.build_meta"
            [project]
            name = "%s"
            version = "1.0.0"
            requires-python = ">=3.10"
            dependencies = ["besser>=%s"]
            [project.optional-dependencies]
            test = ["pytest>=8.4"]
            [tool.setuptools.packages.find]
            where = ["src"]
            """.formatted(module.replace('_', '-'), besserVersion);}
    /** Descripción: Crea o prepara el resultado de «initSource» dentro de BesserSdkTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String initSource(){return "from .model import hydra_domain_model\nfrom .runtime import HydraBesserRuntime\n__all__=['hydra_domain_model','HydraBesserRuntime']\n";}

    /** Descripción: Ejecuta la operación «modelSource» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String modelSource(MetaModel metamodel){
        StringBuilder source = new StringBuilder();
        source.append("from besser.BUML.metamodel.structural import DomainModel, Class, Property, Multiplicity, BinaryAssociation, Generalization, Enumeration, EnumerationLiteral, StringType, IntegerType, FloatType, BooleanType, DateType, AnyType\n\n");
        for(var metaEnum : metamodel.enums()){
            String variable = py(metaEnum.name());
            source.append(variable).append("_literals={");
            source.append(String.join(",", metaEnum.literals().stream().map(value -> "EnumerationLiteral(name='" + escape(value.name()) + "')").toList()));
            source.append("}\n").append(variable).append("=Enumeration(name='").append(escape(metaEnum.name())).append("',literals=").append(variable).append("_literals)\n\n");
        }
        for(MetaClass metaClass : metamodel.classes()){
            String variable = py(metaClass.name());
            for(MetaAttribute attribute : metaClass.attributes()){
                source.append(variable).append('_').append(py(attribute.name())).append("=Property(name='").append(escape(attribute.name())).append("',type=")
                        .append(besserType(attribute.type(), metamodel)).append(",multiplicity=").append(multiplicity(attribute.multiplicity()))
                        .append(attribute.defaultValue().isBlank() ? "" : ",default_value='" + escape(attribute.defaultValue()) + "'")
                        .append(")\n");
            }
            source.append(variable).append("=Class(name='").append(escape(metaClass.name())).append("',attributes={")
                    .append(String.join(",", metaClass.attributes().stream().map(value -> variable + "_" + py(value.name())).toList())).append("}")
                    .append(metaClass.kind() != MetaClassKind.CLASS ? ",is_abstract=True" : "").append(")\n\n");
        }
        List<String> associationVariables = new ArrayList<>();
        List<String> generalizationVariables = new ArrayList<>();
        for(MetaRelation relation : metamodel.relations()){
            String relationVariable = py(relation.name().isBlank() ? relation.id() : relation.name());
            String sourceClass = classVar(metamodel, relation.sourceTypeId());
            String targetClass = classVar(metamodel, relation.targetTypeId());
            if(relation.kind() == MetaRelationKind.INHERITANCE || relation.kind() == MetaRelationKind.IMPLEMENTATION){
                source.append(relationVariable).append("=Generalization(general=").append(targetClass).append(",specific=").append(sourceClass).append(")\n");
                generalizationVariables.add(relationVariable); continue;
            }
            if(relation.kind() == MetaRelationKind.DEPENDENCY){continue;}
            String sourceEnd = relationVariable + "_source";
            String targetEnd = relationVariable + "_target";
            source.append(sourceEnd).append("=Property(name='").append(escape(relation.name().isBlank() ? "source" : relation.name() + "Source"))
                    .append("',type=").append(sourceClass).append(",multiplicity=").append(multiplicity(relation.sourceMultiplicity())).append(")\n");
            source.append(targetEnd).append("=Property(name='").append(escape(relation.name().isBlank() ? "target" : relation.name()))
                    .append("',type=").append(targetClass).append(",multiplicity=").append(multiplicity(relation.targetMultiplicity()))
                    .append(relation.kind() == MetaRelationKind.CONTAINMENT ? ",is_composite=True" : "").append(")\n");
            source.append(relationVariable).append("=BinaryAssociation(name='").append(escape(relation.name().isBlank() ? relation.id() : relation.name()))
                    .append("',ends={").append(sourceEnd).append(',').append(targetEnd).append("})\n\n");
            associationVariables.add(relationVariable);
        }
        List<String> typeVariables = new ArrayList<>();
        metamodel.classes().forEach(value -> typeVariables.add(py(value.name())));
        metamodel.enums().forEach(value -> typeVariables.add(py(value.name())));
        source.append("hydra_domain_model=DomainModel(name='").append(escape(metamodel.name())).append("',types={")
                .append(String.join(",", typeVariables)).append("},associations={").append(String.join(",", associationVariables))
                .append("},generalizations={").append(String.join(",", generalizationVariables)).append("})\n");
        source.append("hydra_domain_model.validate()\n");
        return source.toString();
    }
    /** Descripción: Ejecuta la operación «classVar» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String classVar(MetaModel metamodel, String id){return metamodel.classes().stream().filter(value -> value.id().equals(id)).map(value -> py(value.name())).findFirst().orElse("AnyType");}
    /** Descripción: Ejecuta la operación «besserType» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: type: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String besserType(MetaTypeReference type, MetaModel metamodel){
        if(type == null){return "AnyType";}
        if(type.kind() == MetaTypeReferenceKind.ENUM){return metamodel.enums().stream().filter(value -> value.id().equals(type.targetId())).map(value -> py(value.name())).findFirst().orElse("StringType");}
        if(type.kind() != MetaTypeReferenceKind.PRIMITIVE){return "AnyType";}
        try{return switch(MetaPrimitiveType.valueOf(type.targetId())){case BOOLEAN -> "BooleanType"; case BYTE, SHORT, INTEGER, LONG, BIG_INTEGER -> "IntegerType"; case FLOAT, DOUBLE, DECIMAL -> "FloatType"; case DATE -> "DateType"; case STRING, CHARACTER -> "StringType"; default -> "AnyType";};}
        catch(IllegalArgumentException exception){return "AnyType";}
    }
    /** Descripción: Ejecuta la operación «multiplicity» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String multiplicity(Multiplicity value){if(value == null){return "Multiplicity(0,1)";}return "Multiplicity(" + value.lowerBound() + "," + (value.unbounded() ? "'*'" : value.upperBound()) + ")";}
    /** Descripción: Ejecuta la operación «py» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String py(String value){return TechnologyAdapterGenerationSupport.pythonModule(value, "element");}
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de BesserSdkTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escape(String value){return String.valueOf(value == null ? "" : value).replace("\\", "\\\\").replace("'", "\\'");}

    /** Descripción: Genera la fachada de validación Python conectada al lifecycle transaccional. Entradas: no requiere parámetros. Salida: devuelve código Python ejecutable. */ private String validationSource(){return """
            from .semantic_runtime import HydraDiagnostic
            class HydraValidationBridge:
                def __init__(self,runtime): self.runtime=runtime
                def validate(self,model): return self.runtime.validate(model,'BESSER_SDK_DRAFT_VALIDATE')
            """;}
    /** Descripción: Genera la fachada de quick fixes Python con rollback del lifecycle ante fallos. Entradas: no requiere parámetros. Salida: devuelve código Python ejecutable. */ private String quickFixSource(){return """
            class HydraQuickFixBridge:
                def __init__(self,runtime): self.runtime=runtime
                def apply(self,model,diagnostic_id:str,action_id:str,parameters:dict): return self.runtime.apply_quick_fix(model,diagnostic_id,action_id,parameters)[0]
            """;}
    /** Descripción: Genera la fachada de operaciones Python con commit confirmado y restauración transaccional. Entradas: no requiere parámetros. Salida: devuelve código Python ejecutable. */ private String operationSource(){return """
            class HydraOperationBridge:
                def __init__(self,runtime): self.runtime=runtime
                def invoke(self,model,operation_id:str,parameters:dict): return self.runtime.invoke(model,operation_id,parameters)[0]
            """;}
    /** Descripción: Genera la fachada de eventos Python sobre el historial confirmado del lifecycle. Entradas: no requiere parámetros. Salida: devuelve código Python ejecutable. */ private String eventSource(){return """
            class HydraEventBridge:
                def __init__(self,runtime): self.runtime=runtime
                def publish_after_commit(self,model,trigger='BESSER_SDK_COMMIT'): return self.runtime.commit(model,trigger)
                def history(self): return self.runtime.events()
            """;}
    /** Descripción: Genera el runtime BESSER SDK concreto sobre el lifecycle Python neutral. Entradas: no requiere parámetros. Salida: devuelve código Python que valida borradores, confirma cambios y restaura checkpoints completos. */ private String runtimeSource(){return """
            import json
            from .semantic_runtime import HydraDiagnostic, HydraEvent, HydraGamificationState, HydraSemanticRuntime

            def _semantic_state(model):
                if isinstance(model,str): return model
                if isinstance(model,(dict,list,tuple,int,float,bool)) or model is None:
                    return json.dumps(model,sort_keys=True,separators=(',',':'),default=str)
                to_dict=getattr(model,'to_dict',None)
                if callable(to_dict):
                    try: return json.dumps(to_dict(),sort_keys=True,separators=(',',':'),default=str)
                    except Exception: pass
                return repr(model)

            def _decode(state):
                try: return json.loads(state)
                except Exception: return state

            def _structural(model):
                if not isinstance(model,dict) or not isinstance(model.get('elements'),list): return []
                diagnostics=[]; ids=set()
                for element in model.get('elements',[]):
                    element_id=str(element.get('id','')) if isinstance(element,dict) else ''
                    if not element_id: continue
                    if element_id in ids: diagnostics.append(HydraDiagnostic(f'duplicate:{element_id}','ERROR',f'Duplicate element id {element_id}.',element_id,()))
                    ids.add(element_id)
                for relation in model.get('relations',[]) if isinstance(model.get('relations'),list) else []:
                    if not isinstance(relation,dict): continue
                    source=str(relation.get('sourceElementId','')); target=str(relation.get('targetElementId',''))
                    if (source and source not in ids) or (target and target not in ids): diagnostics.append(HydraDiagnostic(f"broken:{relation.get('id','relation')}",'ERROR','Relationship references an unknown element.',source,()))
                return diagnostics

            def _diff_events(before,after,trigger,sequence):
                if not isinstance(before,dict) or not isinstance(after,dict): return [HydraEvent(sequence,'MODEL_CHANGED',str(after.get('id','model')) if isinstance(after,dict) else 'model',{'trigger':trigger})]
                result=[]
                def diff(kind,left,right):
                    old={str(v.get('id','')):v for v in left if isinstance(v,dict) and v.get('id')}; new={str(v.get('id','')):v for v in right if isinstance(v,dict) and v.get('id')}
                    for key in new.keys()-old.keys(): result.append(HydraEvent(sequence,f'{kind}_CREATED',key,{'trigger':trigger}))
                    for key in old.keys()-new.keys(): result.append(HydraEvent(sequence,f'{kind}_DELETED',key,{'trigger':trigger}))
                    for key in new.keys()&old.keys():
                        if new[key]!=old[key]: result.append(HydraEvent(sequence,f'{kind}_UPDATED',key,{'trigger':trigger}))
                diff('ELEMENT',before.get('elements',[]) if isinstance(before.get('elements'),list) else [],after.get('elements',[]) if isinstance(after.get('elements'),list) else [])
                diff('RELATION',before.get('relations',[]) if isinstance(before.get('relations'),list) else [],after.get('relations',[]) if isinstance(after.get('relations'),list) else [])
                return result or [HydraEvent(sequence,'MODEL_CHANGED',str(after.get('id','model')),{'trigger':trigger})]

            class _Engine:
                def __init__(self,adapter): self.adapter=adapter
                def validate(self,state,trigger):
                    model=_decode(state); handler=getattr(self.adapter,'validate',None)
                    return list(handler(model,trigger)) if callable(handler) else _structural(model)
                def derive_events(self,before_state,after_state,trigger,sequence):
                    before,after=_decode(before_state),_decode(after_state); handler=getattr(self.adapter,'derive_events',None)
                    return list(handler(before,after,trigger,sequence)) if callable(handler) else _diff_events(before,after,trigger,sequence)
                def run_event_flow(self,events,state):
                    handler=getattr(self.adapter,'run_event_flow',None); return list(handler(events,_decode(state))) if callable(handler) else []
                def update_gamification(self,previous,events,flow_events):
                    handler=getattr(self.adapter,'update_gamification',None); return handler(previous,events,flow_events) if callable(handler) else previous

            class HydraBesserRuntime:
                def __init__(self,adapter=None,history_limit=256): self.adapter=adapter or object(); self.lifecycle=HydraSemanticRuntime(_Engine(self.adapter),history_limit)
                def validate(self,model,trigger='BESSER_SDK_DRAFT_VALIDATE'): return self.lifecycle.validate_host(_semantic_state(model),trigger)
                def commit(self,model,trigger='BESSER_SDK_COMMIT'): return self.lifecycle.synchronize(_semantic_state(model),trigger)
                def checkpoint(self): return self.lifecycle.checkpoint()
                def restore(self,snapshot): self.lifecycle.restore(snapshot)
                def events(self): return self.lifecycle.events()
                def gamification(self): return self.lifecycle.gamification()
                def apply_quick_fix(self,model,diagnostic_id,action_id,parameters):
                    handler=getattr(self.adapter,'apply_quick_fix',None)
                    if not callable(handler): raise RuntimeError('No HYDRA quick-fix adapter is installed.')
                    before=self.lifecycle.checkpoint()
                    try:
                        changed=handler(model,diagnostic_id,action_id,parameters); return changed,self.commit(changed,'BESSER_SDK_QUICK_FIX')
                    except Exception:
                        self.lifecycle.restore(before); raise
                def invoke(self,model,operation_id,parameters):
                    handler=getattr(self.adapter,'invoke',None)
                    if not callable(handler): raise RuntimeError('No HYDRA operation adapter is installed.')
                    before=self.lifecycle.checkpoint()
                    try:
                        changed=handler(model,operation_id,parameters); return changed,self.commit(changed,'BESSER_SDK_OPERATION')
                    except Exception:
                        self.lifecycle.restore(before); raise
            """;}
    /** Descripción: Genera las pruebas pytest autocontenidas del paquete BESSER SDK, incluyendo lifecycle transaccional, rollback y reactivación. Entradas: module: módulo Python generado; visual: indica si se prueba también la personalización visual. Salida: devuelve código Python ejecutable por el proyecto generado. */ private String testSource(String module, boolean visual){return """
            import pytest
            from %s import HydraBesserRuntime, hydra_domain_model
            from %s import runtime_activation
            from %s.semantic_runtime import HydraEvent, HydraGamificationState
            %s

            BASELINE={'id':'model','elements':[{'id':'a','name':'A'}],'relations':[]}
            CHANGED={'id':'model','elements':[{'id':'a','name':'B'}],'relations':[]}
            OFF_CHANGE={'id':'model','elements':[{'id':'a','name':'OFF'}],'relations':[]}

            class LifecycleAdapter:
                def validate(self,model,trigger): return []
                def derive_events(self,before,after,trigger,sequence): return [HydraEvent(sequence,'ELEMENT_UPDATED','a',{'trigger':trigger})]
                def run_event_flow(self,events,model): return [HydraEvent(event.sequence,'FLOW_'+event.type,event.element_id,dict(event.payload)) for event in events]
                def update_gamification(self,previous,events,flow):
                    points=dict(previous.points); points['xp']=points.get('xp',0)+5
                    return HydraGamificationState(points,previous.level,previous.achievements,previous.penalties)

            def _enable():
                runtime_activation.set_enabled(False)
                runtime_activation.set_enabled(True)

            def test_generated_model_is_valid(): assert hydra_domain_model.validate(raise_exception=False)['success'] is True

            def test_draft_validation_is_side_effect_free_and_commit_is_confirmed():
                _enable(); runtime=HydraBesserRuntime(LifecycleAdapter())
                try:
                    assert runtime.commit(BASELINE).rebaselined is True
                    before=runtime.checkpoint()
                    assert runtime.validate(CHANGED)==[]
                    assert runtime.checkpoint()==before
                    result=runtime.commit(CHANGED)
                    assert result.changed is True
                    assert [event.type for event in result.confirmed_events]==['ELEMENT_UPDATED']
                    assert [event.type for event in result.flow_events]==['FLOW_ELEMENT_UPDATED']
                    assert result.gamification.points['xp']==5
                finally: runtime_activation.set_enabled(False)

            def test_failed_commit_restores_complete_checkpoint():
                class FailingAdapter(LifecycleAdapter):
                    def update_gamification(self,previous,events,flow): raise RuntimeError('boom')
                _enable(); runtime=HydraBesserRuntime(FailingAdapter())
                try:
                    runtime.commit(BASELINE); before=runtime.checkpoint()
                    with pytest.raises(RuntimeError,match='boom'): runtime.commit(CHANGED)
                    assert runtime.checkpoint()==before
                finally: runtime_activation.set_enabled(False)

            def test_reactivation_rebaselines_without_replaying_offline_changes():
                _enable(); runtime=HydraBesserRuntime(LifecycleAdapter())
                try:
                    runtime.commit(BASELINE); runtime.commit(CHANGED); event_count=len(runtime.events())
                    runtime_activation.set_enabled(False)
                    assert runtime.commit(OFF_CHANGE).rebaselined is True
                    runtime_activation.set_enabled(True)
                    resumed=runtime.commit(OFF_CHANGE)
                    assert resumed.rebaselined is True and resumed.confirmed_events==()
                    assert len(runtime.events())==event_count
                finally: runtime_activation.set_enabled(False)

            %s
            """.formatted(module, module, module,
                    visual ? "from %s.visual_editor import HydraVisualFullStack".formatted(module) : "",
                    visual ? """
            def test_visual_customization_executes_in_besser_sdk_bridge():
                class Host:
                    def evaluate_expression(self,expression_id,subject,context): return expression_id == 'expr:vip'
                    def apply_node_appearance(self,element_id,appearance): self.applied=(element_id,appearance)
                host=Host(); runtime=HydraVisualFullStack(host)
                mapping={'id':'node:test','targetClassId':'class:Person','shape':'RECTANGLE','styles':{'fillColor':'#fff'},'conditionalStyles':[{'id':'style:vip','enabled':True,'priority':10,'conditionExpressionId':'expr:vip','styles':{'fillColor':'#fc0','shape':'ELLIPSE'}}]}
                appearance=runtime.resolve_node_appearance(mapping,{'id':'person-1'})
                assert appearance['styles']['fillColor']=='#fc0' and appearance['shape']=='ELLIPSE'
            """ : "");}
    /** Descripción: Obtiene la información correspondiente a «readme» dentro de BesserSdkTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; metamodel: dato de entrada necesario para la operación; module: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String readme(TechnologyCompilationRequest request, MetaModel metamodel, String module){return """
            # HYDRA BESSER SDK integration

            Generated from canonical `MetaModel 2.1` document `%s`.

            - Python package: `%s`.
            - Classes, properties, enumerations, binary associations, multiplicities and generalizations are translated to B-UML objects.
            - Validation, quick fixes, operations, confirmed events, EventFlow and gamification share one transactional Python lifecycle with full checkpoint rollback.
            - When a grammar is selected, `textual_editor.py` consumes HYDRA appearance/hover and coordinates validation, quick fixes, events, EventFlow and gamification through the same neutral contract.
            - `runtime_activation.py` plus `runtime-activation.json` provide a semantic-scope enable/disable gate with activation epochs and silent rebaseline, disabled by default for safe package installation.
            - Canonical documents are embedded under the package `hydra` directory for traceability.
            - The generated B-UML model is a technology projection and does not replace the HYDRA source of truth.
            - The generated pytest suite verifies draft/commit separation, rollback and OFF/ON rebaseline in the exported package itself.
            - UI contributions belong to the separate BESSER Web Modeling Editor adapter.
            - Injection profile: `%s`.
            """.formatted(metamodel.id(), module, request.profile().id());}
}
