package com.example.editor.integration.adapter.sirius;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.definition.EdgeCreationTool;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.SemanticEdgeDescriptor;
import com.example.editor.definition.NodeCreationTool;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationLayerDefinition;
import com.example.editor.definition.RepresentationFilterDefinition;
import com.example.editor.definition.EditorTool;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationDependencyAction;
import com.example.editor.integration.IntegrationDependencyChange;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationDiagnosticSeverity;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.compiler.TechnologyAdapter;
import com.example.editor.integration.compiler.TechnologyAdapterGenerationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterRuntimeLifecycleSupport;
import com.example.editor.integration.compiler.TechnologyAdapterSupport;
import com.example.editor.integration.compiler.TechnologyFidelityManifestSupport;
import com.example.editor.integration.compiler.TechnologyAdapterTextualCustomizationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterSelfContainedDependencySupport;
import com.example.editor.integration.compiler.TechnologyAdapterVisualFullStackSupport;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.core.MetaModel;
import tools.jackson.databind.json.JsonMapper;

/** Genera un plug-in Sirius Desktop separado, reversible y basado en artefactos canónicos. */
@Component
public class SiriusDesktopTechnologyAdapter implements TechnologyAdapter {
    private static final Set<IntegrationArtifactKind> SUPPORTED = EnumSet.of(
            IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.EDITOR_DEFINITION, IntegrationArtifactKind.CODE_GENERATION,
            IntegrationArtifactKind.EXPRESSIONS, IntegrationArtifactKind.VALIDATIONS,
            IntegrationArtifactKind.ACTIONS, IntegrationArtifactKind.OPERATION_BEHAVIORS,
            IntegrationArtifactKind.EVENT_DEFINITIONS, IntegrationArtifactKind.EVENT_BINDINGS,
            IntegrationArtifactKind.EVENT_FLOWS, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS,
            IntegrationArtifactKind.GAMIFICATION_BINDINGS, IntegrationArtifactKind.GRAMMAR,
            IntegrationArtifactKind.TEXTUAL_EDITOR_DEFINITION, IntegrationArtifactKind.TEXTUAL_SYNTAX_STYLE);
    private final JsonMapper mapper;
    private final EcoreMetamodelAdapter ecoreAdapter;

    /** Descripción: Inicializa una instancia de SiriusDesktopTechnologyAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento; ecoreAdapter: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SiriusDesktopTechnologyAdapter(JsonMapper mapper, EcoreMetamodelAdapter ecoreAdapter){this.mapper = mapper; this.ecoreAdapter = ecoreAdapter;}
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.SIRIUS_DESKTOP;}
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> supportedArtifactKinds(){return Set.copyOf(SUPPORTED);}

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request){
        try{
            MetaModel metamodel = mapper.readValue(request.require(IntegrationArtifactKind.METAMODEL).content(), MetaModel.class);
            EditorDefinitionModel editor = mapper.readValue(request.require(IntegrationArtifactKind.EDITOR_DEFINITION).content(), EditorDefinitionModel.class);
            String root = TechnologyAdapterSupport.generatedRoot(request, metamodel.id() + "-sirius");
            String eclipseRepository = request.profile().target().settings().getOrDefault("eclipseRepository", "https://download.eclipse.org/releases/2026-09");
            String pluginId = request.profile().target().settings().getOrDefault("pluginId",
                    "com.generated." + TechnologyAdapterSupport.slug(metamodel.id()) + ".sirius");
            String packageName = TechnologyAdapterSupport.javaPackage(pluginId, "com.generated.hydra.sirius");
            String javaPath = TechnologyAdapterSupport.javaPath(packageName);
            String descriptionFile = TechnologyAdapterSupport.slug(editor.id()) + ".odesign";
            List<String> sourceIds = request.artifacts().stream().map(value -> value.documentId()).distinct().toList();
            List<TechnologyGeneratedFile> files = new ArrayList<>();
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "pom.xml"), "application/xml", pom(pluginId), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "META-INF/MANIFEST.MF"), "text/plain", manifest(pluginId), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "build.properties"), "text/plain", buildProperties(), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "plugin.xml"), "application/xml", pluginXml(pluginId, descriptionFile, packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "description", descriptionFile), "application/xml", odesign(metamodel, editor, packageName), sourceIds));
            files.add(TechnologyAdapterSupport.binary(TechnologyAdapterSupport.join(root, "model", metamodel.id() + ".ecore"), "application/xml",
                    ecoreAdapter.toBytes(metamodel).value(), List.of(metamodel.id())));
            files.addAll(TechnologyAdapterGenerationSupport.canonicalDocuments(root, "hydra", request));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraSiriusServices.java"), "text/x-java-source", servicesSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraSiriusValidationService.java"), "text/x-java-source", validationSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraSiriusQuickFixService.java"), "text/x-java-source", quickFixSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraSiriusOperationService.java"), "text/x-java-source", operationSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraSiriusEventBridge.java"), "text/x-java-source", eventSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraRuntimeActivation.java"), "text/x-java-source", TechnologyAdapterGenerationSupport.javaRuntimeActivationSource(packageName, request), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraSemanticRuntime.java"), "text/x-java-source", TechnologyAdapterRuntimeLifecycleSupport.javaSemanticRuntimeSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraSiriusRuntime.java"), "text/x-java-source", siriusRuntimeSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraSiriusTransactionLifecycle.java"), "text/x-java-source", transactionLifecycleSource(packageName), sourceIds));
            if(request.find(IntegrationArtifactKind.GRAMMAR).isPresent()){
                files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraTextualFullStack.java"), "text/x-java-source", TechnologyAdapterTextualCustomizationSupport.javaFullStackBridgeSource(packageName, request, mapper), sourceIds));
                files.addAll(TechnologyAdapterTextualCustomizationSupport.fullStackNeutralFiles(root, request, mapper));
            }
            if(TechnologyAdapterVisualFullStackSupport.available(request)){
                files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraVisualFullStack.java"), "text/x-java-source", TechnologyAdapterVisualFullStackSupport.javaBridgeSource(packageName, request), sourceIds));
                files.addAll(TechnologyAdapterVisualFullStackSupport.neutralFiles(root, request, mapper));
            }
                files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src", javaPath, "HydraRuntimeToggleHandler.java"), "text/x-java-source", runtimeToggleHandlerSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "README.md"), "text/markdown", readme(request, metamodel, editor, pluginId), sourceIds));
            List<String> p2Units = new ArrayList<>(List.of("org.eclipse.emf.ecore","org.eclipse.emf.ecore.change","org.eclipse.emf.edit","org.eclipse.emf.transaction","org.eclipse.sirius","org.eclipse.sirius.diagram"));
            if(TechnologyAdapterSelfContainedDependencySupport.grammarTechnology(request, mapper) == com.example.editor.grammar.core.GrammarTechnology.XTEXT) p2Units.addAll(List.of("org.eclipse.xtext","org.eclipse.xtext.ui"));
            files.addAll(TechnologyAdapterSelfContainedDependencySupport.eclipseP2VendorFiles(root, pluginId, eclipseRepository, p2Units, sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "META-INF", "hydra-fidelity-obligations.json"),
                    "application/json", TechnologyFidelityManifestSupport.json(mapper, request, editor, "SIRIUS_DESKTOP"), sourceIds));
            List<IntegrationDiagnostic> diagnostics = new ArrayList<>(TechnologyAdapterSupport.unsupportedDiagnostics(request, SUPPORTED, "Sirius Desktop"));
            for(RepresentationDefinition representation : editor.representations()){
                if(representation.kind() != RepresentationKind.DIAGRAM){
                    diagnostics.add(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.WARNING, "SIRIUS_NON_DIAGRAM_VIEWPOINT",
                            representation.id(), "editor.representations", "The representation is preserved canonically but Sirius Desktop generation currently materializes diagram viewpoints only."));
                }
            }
            List<IntegrationDependencyChange> dependencies = List.of(
                    dependency("org.eclipse.sirius:org.eclipse.sirius", "7.6.1"),
                    dependency("org.eclipse.sirius:org.eclipse.sirius.diagram", "7.6.1"),
                    dependency("org.eclipse.emf:org.eclipse.emf.ecore", "2.43.0"),
                    dependency("org.eclipse.emf:org.eclipse.emf.ecore.change", "2.18.0"),
                    dependency("org.eclipse.emf.transaction:org.eclipse.emf.transaction", "1.14.3"));
            List<IntegrationVerificationStep> verification = List.of(
                    new IntegrationVerificationStep("validate-sirius-plugin", "Build the generated Sirius plug-in with Tycho", root,
                            List.of("mvn", "-q", "verify"), 0, true));
            return new TechnologyCompilationResult(IntegrationTechnology.SIRIUS_DESKTOP, files, dependencies,
                    List.of("Build the local p2 dependency mirror once", "Install the generated plug-in and only missing Sirius/EMF/Xtext units from offline/p2", "Restart Eclipse and verify the generated viewpoint"),
                    List.of("Uninstall the generated plug-in", "Restore the previous target-platform configuration from HYDRA backup"),
                    verification, diagnostics, Map.of("generatorVersion", request.generatorVersion(), "nativeLanguage", "Java/Sirius AQL",
                            "pluginId", pluginId, "moduleRoot", root, "odesign", "description/" + descriptionFile));
        }catch(RuntimeException exception){
            if(exception instanceof TechnologyCompilationException){throw exception;}
            throw new TechnologyCompilationException("Could not generate Sirius Desktop integration", exception);
        }
    }

    /** Descripción: Ejecuta la operación «dependency» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: coordinate: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDependencyChange dependency(String coordinate, String version){return new IntegrationDependencyChange("osgi", coordinate, version, "required-bundle", IntegrationDependencyAction.ADD);}

    /** Descripción: Ejecuta la operación «pom» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: pluginId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String pom(String pluginId){return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>%s</groupId><artifactId>%s</artifactId><version>1.0.0-SNAPSHOT</version><packaging>eclipse-plugin</packaging>
              <properties><tycho.version>5.0.3</tycho.version><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
              <build><plugins><plugin><groupId>org.eclipse.tycho</groupId><artifactId>tycho-maven-plugin</artifactId><version>${tycho.version}</version><extensions>true</extensions></plugin></plugins></build>
            </project>
            """.formatted(TechnologyAdapterSupport.xml(pluginId), TechnologyAdapterSupport.xml(pluginId));}

    /** Descripción: Ejecuta la operación «manifest» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: pluginId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String manifest(String pluginId){return """
            Manifest-Version: 1.0
            Bundle-ManifestVersion: 2
            Bundle-Name: HYDRA generated Sirius integration
            Bundle-SymbolicName: %s;singleton:=true
            Bundle-Version: 1.0.0.qualifier
            Bundle-RequiredExecutionEnvironment: JavaSE-21
            Require-Bundle: org.eclipse.core.runtime,
             org.eclipse.ui,
             org.eclipse.jface,
             org.eclipse.emf.ecore,
             org.eclipse.emf.ecore.change,
             org.eclipse.emf.edit,
             org.eclipse.emf.transaction,
             org.eclipse.sirius,
             org.eclipse.sirius.diagram
            Export-Package: %s
            Automatic-Module-Name: %s
            """.formatted(pluginId, pluginId, pluginId);}

    /** Descripción: Crea o prepara el resultado de «buildProperties» dentro de SiriusDesktopTechnologyAdapter. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String buildProperties(){return """
            source.. = src/
            output.. = bin/
            bin.includes = META-INF/,.,plugin.xml,description/,model/,hydra/
            """;}

    /** Eclipse contributions include a reversible toolbar command for the metamodel-scoped HYDRA runtime. */ /** Descripción: Ejecuta la operación «pluginXml» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: pluginId: identificador que delimita el recurso objetivo; descriptionFile: archivo o contenido que debe procesarse; packageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String pluginXml(String pluginId, String descriptionFile, String packageName){return """
            <?xml version="1.0" encoding="UTF-8"?>
            <?eclipse version="3.4"?>
            <plugin>
              <extension point="org.eclipse.sirius.componentization">
                <component class="org.eclipse.sirius.business.api.componentization.ViewpointRegistry" id="%s"/>
              </extension>
              <extension point="org.eclipse.sirius.ecore.extender">
                <resource uri="description/%s"/>
              </extension>
              <extension point="org.eclipse.ui.commands">
                <command id="%s.toggleHydraRuntime" name="Enable or disable HYDRA runtime"/>
              </extension>
              <extension point="org.eclipse.ui.handlers">
                <handler class="%s.HydraRuntimeToggleHandler" commandId="%s.toggleHydraRuntime"/>
              </extension>
              <extension point="org.eclipse.ui.menus">
                <menuContribution locationURI="toolbar:org.eclipse.ui.main.toolbar?after=additions">
                  <toolbar id="%s.hydraRuntimeToolbar">
                    <command commandId="%s.toggleHydraRuntime" label="HYDRA runtime" style="push" tooltip="Enable or disable HYDRA validation, quick fixes, events and gamification for the associated metamodel"/>
                  </toolbar>
                </menuContribution>
              </extension>
            </plugin>
            """.formatted(TechnologyAdapterSupport.xml(pluginId), TechnologyAdapterSupport.xml(descriptionFile),
                    TechnologyAdapterSupport.xml(pluginId), TechnologyAdapterSupport.xml(packageName), TechnologyAdapterSupport.xml(pluginId),
                    TechnologyAdapterSupport.xml(pluginId), TechnologyAdapterSupport.xml(pluginId));}

    /** Genera un .odesign que conserva capas, filtros y aristas semánticas sin ejecutar expresiones externas. */
    /** Descripción: Ejecuta la operación «odesign» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String odesign(MetaModel metamodel, EditorDefinitionModel editor, String packageName){
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<description:Group xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:description=\"http://www.eclipse.org/sirius/description/1.1.0\" xmlns:diagram=\"http://www.eclipse.org/sirius/diagram/description/1.1.0\" xmlns:style=\"http://www.eclipse.org/sirius/diagram/description/style/1.1.0\" xmlns:dtool=\"http://www.eclipse.org/sirius/diagram/description/tool/1.1.0\" name=\"").append(TechnologyAdapterSupport.xml(editor.name())).append("\" version=\"15.0.0\">\n");
        xml.append("  <ownedViewpoints name=\"").append(TechnologyAdapterSupport.xml(editor.id())).append("\" modelFileExtension=\"xmi model\">\n");
        xml.append("    <ownedJavaExtensions qualifiedClassName=\"").append(TechnologyAdapterSupport.xml(packageName + ".HydraSiriusServices")).append("\"/>\n");
        for(RepresentationDefinition representation : editor.representations()){
            if(representation.kind() != RepresentationKind.DIAGRAM) continue;
            xml.append("    <ownedRepresentations xsi:type=\"diagram:DiagramDescription\" name=\"").append(TechnologyAdapterSupport.xml(representation.name())).append("\" domainClass=\"").append(TechnologyAdapterSupport.xml(rootClass(metamodel, representation))).append("\" enablePopupBars=\"true\">\n");
            RepresentationLayerDefinition defaultLayer = representation.layers().stream().filter(RepresentationLayerDefinition::defaultLayer).findFirst().orElse(null);
            java.util.Set<String> referencedNodes = new java.util.LinkedHashSet<>(), referencedEdges = new java.util.LinkedHashSet<>(), referencedTools = new java.util.LinkedHashSet<>();
            representation.layers().forEach(layer -> { referencedNodes.addAll(layer.nodeMappingIds()); referencedEdges.addAll(layer.edgeMappingIds()); referencedTools.addAll(layer.toolIds()); });
            java.util.Set<String> defaultNodes = new java.util.LinkedHashSet<>(), defaultEdges = new java.util.LinkedHashSet<>(), defaultTools = new java.util.LinkedHashSet<>();
            if(defaultLayer != null){ defaultNodes.addAll(defaultLayer.nodeMappingIds()); defaultEdges.addAll(defaultLayer.edgeMappingIds()); defaultTools.addAll(defaultLayer.toolIds()); }
            representation.nodeMappings().stream().map(NodeMapping::id).filter(id -> !referencedNodes.contains(id)).forEach(defaultNodes::add);
            representation.edgeMappings().stream().map(EdgeMapping::id).filter(id -> !referencedEdges.contains(id)).forEach(defaultEdges::add);
            representation.toolIds().stream().filter(id -> !referencedTools.contains(id)).forEach(defaultTools::add);
            editor.palette().tools().stream().filter(tool -> tool.representationIds().isEmpty() || tool.representationIds().contains(representation.id())).map(EditorTool::id).filter(id -> !referencedTools.contains(id)).forEach(defaultTools::add);
            xml.append("      <defaultLayer name=\"").append(TechnologyAdapterSupport.xml(defaultLayer == null ? "Default" : defaultLayer.name())).append("\">\n");
            appendLayerContents(xml, metamodel, editor, representation, defaultNodes, defaultEdges, defaultTools, "        ");
            xml.append("      </defaultLayer>\n");
            for(RepresentationLayerDefinition layer : representation.layers()){
                if(layer.defaultLayer()) continue;
                xml.append("      <additionalLayers name=\"").append(TechnologyAdapterSupport.xml(layer.name())).append("\" activeByDefault=\"").append(layer.activeByDefault()).append("\">\n");
                appendLayerContents(xml, metamodel, editor, representation, new java.util.LinkedHashSet<>(layer.nodeMappingIds()), new java.util.LinkedHashSet<>(layer.edgeMappingIds()), new java.util.LinkedHashSet<>(layer.toolIds()), "        ");
                xml.append("      </additionalLayers>\n");
            }
            appendFilters(xml, editor, representation);
            xml.append("    </ownedRepresentations>\n");
        }
        xml.append("  </ownedViewpoints>\n</description:Group>\n");
        return xml.toString();
    }

    /** Descripción: Ejecuta la operación «appendLayerContents» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: xml: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación; nodeIds: dato de entrada necesario para la operación; edgeIds: dato de entrada necesario para la operación; toolIds: dato de entrada necesario para la operación; indent: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendLayerContents(StringBuilder xml, MetaModel metamodel, EditorDefinitionModel editor, RepresentationDefinition representation,
            java.util.Set<String> nodeIds, java.util.Set<String> edgeIds, java.util.Set<String> toolIds, String indent) {
        representation.nodeMappings().stream().filter(mapping -> nodeIds.contains(mapping.id())).forEach(mapping -> appendNodeMapping(xml, metamodel, mapping, indent));
        representation.edgeMappings().stream().filter(mapping -> edgeIds.contains(mapping.id())).forEach(mapping -> appendEdgeMapping(xml, metamodel, editor, representation, mapping, indent));
        List<EditorTool> tools = editor.palette().tools().stream().filter(tool -> toolIds.contains(tool.id())).toList();
        if(!tools.isEmpty()) {
            xml.append(indent).append("<toolSections name=\"HYDRA palette\">\n");
            for(EditorTool tool : tools) {
                if(tool instanceof NodeCreationTool node) appendNodeCreationTool(xml, metamodel, editor, representation, node, indent + "  ");
                else if(tool instanceof EdgeCreationTool edge) appendEdgeCreationTool(xml, metamodel, editor, representation, edge, indent + "  ");
            }
            xml.append(indent).append("</toolSections>\n");
        }
    }

    /** Descripción: Ejecuta la operación «appendNodeMapping» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: xml: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; mapping: dato de entrada necesario para la operación; indent: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendNodeMapping(StringBuilder xml, MetaModel metamodel, NodeMapping mapping, String indent) {
        xml.append(indent).append("<nodeMappings name=\"").append(TechnologyAdapterSupport.xml(mapping.id())).append("\" semanticCandidatesExpression=\"aql:self.eAllContents()->filter(").append(TechnologyAdapterSupport.xml(className(metamodel, mapping.targetClassId()))).append(")\" domainClass=\"").append(TechnologyAdapterSupport.xml(className(metamodel, mapping.targetClassId()))).append("\">\n");
        String styleType = switch(mapping.shape()) {
            case ELLIPSE -> "style:EllipseNodeDescription";
            case DIAMOND -> "style:LozengeNodeDescription";
            case DOCUMENT -> "style:NoteDescription";
            case PROJECT_IMAGE -> "style:WorkspaceImageDescription";
            default -> "style:SquareDescription";
        };
        xml.append(indent).append("  <style xsi:type=\"").append(styleType).append("\" labelExpression=\"service:stableLabel\" width=\"").append(Math.max(1, Math.round(mapping.defaultWidth()/10.0))).append("\" height=\"").append(Math.max(1, Math.round(mapping.defaultHeight()/10.0))).append("\"");
        if(mapping.imageBacked() && !mapping.imageAssetPath().isBlank()) xml.append(" workspacePath=\"/").append(TechnologyAdapterSupport.xml(mapping.imageAssetPath().replaceFirst("^external-source/", ""))).append("\"");
        xml.append("/>\n").append(indent).append("</nodeMappings>\n");
    }

    /** Descripción: Ejecuta la operación «appendNodeCreationTool» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: xml: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; indent: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendNodeCreationTool(StringBuilder xml, MetaModel metamodel, EditorDefinitionModel editor, RepresentationDefinition representation, NodeCreationTool node, String indent) {
        String mappingId = nodeMappingForClass(representation, node.targetClassId());
        xml.append(indent).append("<ownedTools xsi:type=\"dtool:NodeCreationDescription\" name=\"").append(TechnologyAdapterSupport.xml(node.label())).append("\" nodeMappings=\"").append(mappingPath(editor, representation, mappingId, true)).append("\">\n");
        xml.append(indent).append("  <initialOperation><firstModelOperations xsi:type=\"description:ChangeContext\" browseExpression=\"aql:self\"/></initialOperation>\n");
        xml.append(indent).append("</ownedTools>\n");
    }

    /** Exporta una conexión directa o un objeto semántico de conexión sin perder su semántica. */
    /** Descripción: Ejecuta la operación «appendEdgeMapping» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: xml: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación; mapping: dato de entrada necesario para la operación; indent: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendEdgeMapping(StringBuilder xml, MetaModel metamodel, EditorDefinitionModel editor, RepresentationDefinition representation, EdgeMapping mapping, String indent){
        xml.append(indent).append("<edgeMappings name=\"").append(TechnologyAdapterSupport.xml(mapping.id())).append("\" sourceMapping=\"").append(mappingPath(editor, representation, nodeMappingForClass(representation, mapping.sourceClassId()), true)).append("\" targetMapping=\"").append(mappingPath(editor, representation, nodeMappingForClass(representation, mapping.targetClassId()), true)).append("\"");
        if(mapping.semanticObjectBacked()){
            SemanticEdgeDescriptor semantic=mapping.semanticEdge();
            xml.append(" domainClass=\"").append(TechnologyAdapterSupport.xml(className(metamodel,semantic.edgeClassId()))).append("\" sourceFinderExpression=\"feature:").append(TechnologyAdapterSupport.xml(relationName(metamodel,semantic.sourceRelationId()))).append("\" targetFinderExpression=\"feature:").append(TechnologyAdapterSupport.xml(relationName(metamodel,semantic.targetRelationId()))).append("\"");
        } else xml.append(" targetFinderExpression=\"aql:self.").append(TechnologyAdapterSupport.xml(relationName(metamodel,mapping.relationId()))).append("\"");
        xml.append("/>\n");
    }

    /** Exporta la operación de creación. Los objetos-arista se crean como instancias reales con extremos explícitos. */
    /** Descripción: Ejecuta la operación «appendEdgeCreationTool» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: xml: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación; edge: dato de entrada necesario para la operación; indent: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendEdgeCreationTool(StringBuilder xml, MetaModel metamodel, EditorDefinitionModel editor, RepresentationDefinition representation, EdgeCreationTool edge, String indent){
        xml.append(indent).append("<ownedTools xsi:type=\"dtool:EdgeCreationDescription\" name=\"").append(TechnologyAdapterSupport.xml(edge.label())).append("\" edgeMappings=\"").append(mappingPath(editor, representation, edge.edgeMappingId(), false)).append("\"");
        if(!edge.semanticObjectBacked()) { xml.append("/>\n"); return; }
        SemanticEdgeDescriptor semantic=edge.semanticEdge();
        String containment=semantic.containmentRelationId().isBlank()?"":relationName(metamodel,semantic.containmentRelationId());
        if(containment.isBlank()){
            xml.append(">\n").append(indent).append("  <!-- HYDRA preserved a semantic edge object but no unambiguous containment feature was available. -->\n").append(indent).append("</ownedTools>\n");
            return;
        }
        xml.append(">\n").append(indent).append("  <initialOperation><firstModelOperations xsi:type=\"description:ChangeContext\" browseExpression=\"aql:self\">\n");
        xml.append(indent).append("    <subModelOperations xsi:type=\"description:CreateInstance\" typeName=\"").append(TechnologyAdapterSupport.xml(className(metamodel,semantic.edgeClassId()))).append("\" referenceName=\"").append(TechnologyAdapterSupport.xml(containment)).append("\">\n");
        xml.append(indent).append("      <subModelOperations xsi:type=\"description:SetValue\" featureName=\"").append(TechnologyAdapterSupport.xml(relationName(metamodel,semantic.sourceRelationId()))).append("\" valueExpression=\"var:source\"/>\n");
        xml.append(indent).append("      <subModelOperations xsi:type=\"description:SetValue\" featureName=\"").append(TechnologyAdapterSupport.xml(relationName(metamodel,semantic.targetRelationId()))).append("\" valueExpression=\"var:target\"/>\n");
        semantic.defaultValues().forEach((attributeId,value)->{ String attributeName=attributeName(metamodel,semantic.edgeClassId(),attributeId); if(!attributeName.isBlank()) xml.append(indent).append("      <subModelOperations xsi:type=\"description:SetValue\" featureName=\"").append(TechnologyAdapterSupport.xml(attributeName)).append("\" valueExpression=\"").append(TechnologyAdapterSupport.xml(siriusLiteral(value))).append("\"/>\n"); });
        xml.append(indent).append("    </subModelOperations>\n").append(indent).append("  </firstModelOperations></initialOperation>\n").append(indent).append("</ownedTools>\n");
    }

    /** Descripción: Ejecuta la operación «appendFilters» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: xml: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendFilters(StringBuilder xml, EditorDefinitionModel editor, RepresentationDefinition representation) {
        for(RepresentationFilterDefinition filter : representation.filters()) {
            if(filter.mappingIds().isEmpty()) continue;
            String condition = filter.externalCondition();
            String language = filter.annotations().getOrDefault("hydra.import.conditionLanguage", "");
            if(condition.isBlank() || !(language.equalsIgnoreCase("AQL") || condition.startsWith("aql:"))) {
                xml.append("      <!-- HYDRA filter '").append(TechnologyAdapterSupport.xml(filter.name())).append("' preserved canonically; its condition is not safely translatable to AQL. -->\n");
                continue;
            }
            String refs = filter.mappingIds().stream().map(id -> mappingPath(editor, representation, id, representation.nodeMappings().stream().anyMatch(node -> node.id().equals(id)))).collect(java.util.stream.Collectors.joining(" "));
            xml.append("      <filters xsi:type=\"diagram:CompositeFilterDescription\" name=\"").append(TechnologyAdapterSupport.xml(filter.name())).append("\">\n");
            xml.append("        <filters xsi:type=\"diagram:MappingFilter\" mappings=\"").append(refs).append("\" semanticConditionExpression=\"").append(TechnologyAdapterSupport.xml(condition)).append("\"/>\n");
            xml.append("      </filters>\n");
        }
    }

    /** Descripción: Ejecuta la operación «mappingPath» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: editor: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación; mappingId: identificador que delimita el recurso objetivo; node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String mappingPath(EditorDefinitionModel editor, RepresentationDefinition representation, String mappingId, boolean node) {
        String collection = node ? "nodeMappings" : "edgeMappings";
        RepresentationLayerDefinition owner = representation.layers().stream().filter(layer -> (node ? layer.nodeMappingIds() : layer.edgeMappingIds()).contains(mappingId)).findFirst().orElse(null);
        String layer = owner == null || owner.defaultLayer() ? "/@defaultLayer" : "/@additionalLayers[name='" + TechnologyAdapterSupport.xml(owner.name()) + "']";
        return "//@ownedViewpoints[name='" + TechnologyAdapterSupport.xml(editor.id()) + "']/@ownedRepresentations[name='" + TechnologyAdapterSupport.xml(representation.name()) + "']" + layer + "/@" + collection + "[name='" + TechnologyAdapterSupport.xml(mappingId) + "']";
    }

    /** Descripción: Ejecuta la operación «attributeName» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo; attributeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String attributeName(MetaModel metamodel,String classId,String attributeId){return metamodel.classes().stream().filter(value->value.id().equals(classId)).flatMap(value->value.attributes().stream()).filter(value->value.id().equals(attributeId)).map(value->value.name()).findFirst().orElse("");}
    /** Descripción: Ejecuta la operación «siriusLiteral» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String siriusLiteral(Object value){if(value==null)return "aql:null";if(value instanceof Number||value instanceof Boolean)return "aql:"+value;String text=String.valueOf(value).replace("'","\\'");return "aql:'"+text+"'";}

    /** Descripción: Ejecuta la operación «rootClass» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String rootClass(MetaModel metamodel, RepresentationDefinition representation){
        if(!representation.rootClassIds().isEmpty()){return className(metamodel, representation.rootClassIds().get(0));}
        return metamodel.classes().isEmpty() ? "EObject" : metamodel.classes().get(0).name();
    }
    /** Descripción: Ejecuta la operación «className» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String className(MetaModel metamodel, String id){return metamodel.classes().stream().filter(value -> value.id().equals(id)).map(value -> value.name()).findFirst().orElse(id);}
    /** Descripción: Ejecuta la operación «relationName» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String relationName(MetaModel metamodel, String id){return metamodel.relations().stream().filter(value -> value.id().equals(id)).map(value -> value.name()).findFirst().orElse(id);}
    /** Descripción: Ejecuta la operación «nodeMappingForClass» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: representation: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String nodeMappingForClass(RepresentationDefinition representation, String classId){return representation.nodeMappings().stream().filter(value -> value.targetClassId().equals(classId)).map(value -> value.id()).findFirst().orElse(classId);}

    /** Descripción: Ejecuta la operación «servicesSource» correspondiente a la responsabilidad de SiriusDesktopTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String servicesSource(String packageName){return """
            package %s;

            import org.eclipse.emf.ecore.EObject;

            /** AQL-callable services generated from HYDRA representation and form contracts. */
            public final class HydraSiriusServices {
                public String stableLabel(EObject self){if(self==null)return "";HydraSiriusTransactionLifecycle.attach(self);return self.eClass().getName() + " · " + self.toString();}
                public boolean canEdit(EObject self){if(self==null)return false;HydraSiriusTransactionLifecycle.attach(self);return !self.eClass().isAbstract();}
            }
            """.formatted(packageName);}

    /** Descripción: Genera validación Sirius side-effect free sobre el runtime transaccional. Entradas: packageName. Salida: fuente Java del servicio. */ private String validationSource(String packageName){return """
            package %s;
            import java.util.List;
            import org.eclipse.emf.ecore.EObject;
            /** Side-effect-free HYDRA validation used by Sirius decorations. */
            public final class HydraSiriusValidationService { public List<HydraSemanticRuntime.Diagnostic> validate(EObject object){HydraSiriusTransactionLifecycle.attach(object);return HydraSiriusRuntime.validateHost(object);} }
            """.formatted(packageName);}

    /** Descripción: Genera quick fixes Sirius ejecutados dentro del TransactionalEditingDomain. Entradas: packageName. Salida: fuente Java del servicio. */ private String quickFixSource(String packageName){return """
            package %s;
            import java.util.Map;
            import org.eclipse.emf.ecore.EObject;
            /** Executes a HYDRA quick fix inside the Sirius transactional editing domain. */
            public final class HydraSiriusQuickFixService { public boolean apply(EObject target,String diagnosticId,String actionId,Map<String,Object> parameters){return HydraSiriusRuntime.applyQuickFix(target,diagnosticId,actionId,parameters);} }
            """.formatted(packageName);}

    /** Descripción: Genera operaciones Sirius ejecutadas mediante el command stack transaccional de EMF. Entradas: packageName. Salida: fuente Java del servicio. */ private String operationSource(String packageName){return """
            package %s;
            import java.util.Map;
            import org.eclipse.emf.ecore.EObject;
            /** Executes neutral HYDRA operations in the Sirius transaction. */
            public final class HydraSiriusOperationService { public Object invoke(EObject target,String operationId,Map<String,Object> parameters){return HydraSiriusRuntime.invoke(target,operationId,parameters);} }
            """.formatted(packageName);}

    /** Descripción: Genera acceso a eventos confirmados sin volver a emitirlos durante undo/redo. Entradas: packageName. Salida: fuente Java del bridge. */ private String eventSource(String packageName){return """
            package %s;
            import java.util.List;
            import org.eclipse.emf.ecore.EObject;
            /** Exposes the confirmed HYDRA event history owned by the Sirius transaction lifecycle. */
            public final class HydraSiriusEventBridge { public List<HydraSemanticRuntime.Event> history(EObject target){HydraSiriusTransactionLifecycle.attach(target);return HydraSiriusRuntime.events(target);} }
            """.formatted(packageName);}

    /** Descripción: Genera el runtime Sirius por recurso con proyección EMF neutral y ejecución de capacidades dentro del command stack. Entradas: packageName. Salida: fuente Java del runtime. */ private String siriusRuntimeSource(String packageName){return """
            package %s;
            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.Comparator;
            import java.util.IdentityHashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.Objects;
            import java.util.WeakHashMap;
            import java.util.concurrent.atomic.AtomicBoolean;
            import java.util.concurrent.atomic.AtomicReference;
            import org.eclipse.emf.ecore.EObject;
            import org.eclipse.emf.ecore.EStructuralFeature;
            import org.eclipse.emf.ecore.change.ChangeDescription;
            import org.eclipse.emf.ecore.change.util.ChangeRecorder;
            import org.eclipse.emf.ecore.resource.Resource;
            import org.eclipse.emf.ecore.util.EcoreUtil;
            import org.eclipse.emf.transaction.RecordingCommand;
            import org.eclipse.emf.transaction.TransactionalEditingDomain;
            import org.eclipse.emf.transaction.util.TransactionUtil;
            /** Runtime shared by Sirius services and the transactional pre-commit listener. */
            public final class HydraSiriusRuntime {
                public interface Adapter {
                    default List<HydraSemanticRuntime.Diagnostic> validate(String state,String trigger){return List.of();}
                    default List<HydraSemanticRuntime.Event> deriveEvents(String before,String after,String trigger,long sequence){return Objects.equals(before,after)?List.of():List.of(new HydraSemanticRuntime.Event(sequence,"MODEL_CHANGED","model",Map.of("trigger",trigger)));}
                    default List<HydraSemanticRuntime.Event> runEventFlow(List<HydraSemanticRuntime.Event> confirmed,String state){return List.of();}
                    default HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> confirmed,List<HydraSemanticRuntime.Event> flow){return previous;}
                    default boolean applyQuickFix(EObject target,String diagnosticId,String actionId,Map<String,Object> parameters){return false;}
                    default Object invoke(EObject target,String operationId,Map<String,Object> parameters){throw new UnsupportedOperationException("No HYDRA operation adapter is installed for " + operationId);}
                }
                private static final Map<Object,HydraSemanticRuntime> RUNTIMES=Collections.synchronizedMap(new WeakHashMap<>());
                private static volatile Adapter adapter=new Adapter(){};
                private HydraSiriusRuntime(){ }
                public static void install(Adapter value){adapter=Objects.requireNonNull(value,"value");synchronized(RUNTIMES){RUNTIMES.clear();}}
                public static List<HydraSemanticRuntime.Diagnostic> validateHost(EObject target){if(target==null)return List.of();HydraSiriusTransactionLifecycle.attach(target);return runtime(target).validateHost(semanticState(target),"SIRIUS_DRAFT_VALIDATE");}
                public static HydraSemanticRuntime.CommitResult synchronize(EObject target,String trigger){Objects.requireNonNull(target,"target");return runtime(target).synchronize(semanticState(target),trigger);}
                public static HydraSemanticRuntime.Snapshot checkpoint(EObject target){return runtime(target).checkpoint();}
                public static void restoreOrRebaseline(EObject target,HydraSemanticRuntime.Snapshot snapshot,String trigger){HydraSemanticRuntime lifecycle=runtime(target);if(snapshot.activationEpoch()==HydraRuntimeActivation.epoch())lifecycle.restore(snapshot);else lifecycle.synchronize(semanticState(target),trigger);}
                public static List<HydraSemanticRuntime.Event> events(EObject target){return runtime(target).events();}
                public static HydraSemanticRuntime.GamificationState gamification(EObject target){return runtime(target).gamification();}
                public static boolean applyQuickFix(EObject target,String diagnosticId,String actionId,Map<String,Object> parameters){
                    TransactionalEditingDomain domain=requireDomain(target);HydraSiriusTransactionLifecycle.attach(target);AtomicBoolean applied=new AtomicBoolean(false);
                    domain.getCommandStack().execute(new RecordingCommand(domain,"HYDRA quick fix"){@Override protected void doExecute(){ChangeRecorder recorder=new ChangeRecorder(EcoreUtil.getRootContainer(target));ChangeDescription changes=null;try{applied.set(adapter.applyQuickFix(target,safe(diagnosticId),safe(actionId),safeMap(parameters)));changes=recorder.endRecording();if(!applied.get()&&changes!=null)changes.apply();}finally{recorder.dispose();}}});
                    return applied.get();
                }
                public static Object invoke(EObject target,String operationId,Map<String,Object> parameters){
                    TransactionalEditingDomain domain=requireDomain(target);HydraSiriusTransactionLifecycle.attach(target);AtomicReference<Object> result=new AtomicReference<>();
                    domain.getCommandStack().execute(new RecordingCommand(domain,"HYDRA operation"){@Override protected void doExecute(){result.set(adapter.invoke(target,safe(operationId),safeMap(parameters)));}});return result.get();
                }
                public static String semanticState(EObject target){EObject root=EcoreUtil.getRootContainer(target);StringBuilder out=new StringBuilder(256);append(root,out,new IdentityHashMap<>());return out.toString();}
                private static void append(EObject object,StringBuilder out,IdentityHashMap<EObject,Boolean> seen){if(object==null||seen.put(object,Boolean.TRUE)!=null)return;out.append(object.eClass().getEPackage().getNsURI()).append('#').append(object.eClass().getName()).append('{');List<EStructuralFeature> features=new ArrayList<>(object.eClass().getEAllStructuralFeatures());features.sort(Comparator.comparing(EStructuralFeature::getName));for(EStructuralFeature feature:features){if(feature.isDerived()||feature.isTransient()||!object.eIsSet(feature))continue;Object value=object.eGet(feature,false);out.append(feature.getName()).append('=');appendValue(value,out,seen);out.append(';');}out.append('}');}
                private static void appendValue(Object value,StringBuilder out,IdentityHashMap<EObject,Boolean> seen){if(value instanceof EObject eo){if(eo.eContainmentFeature()!=null)append(eo,out,seen);else out.append(EcoreUtil.getURI(eo));return;}if(value instanceof Iterable<?> values){out.append('[');for(Object item:values){appendValue(item,out,seen);out.append(',');}out.append(']');return;}out.append(String.valueOf(value));}
                private static HydraSemanticRuntime runtime(EObject target){Object key=target.eResource()!=null?target.eResource():EcoreUtil.getRootContainer(target);synchronized(RUNTIMES){return RUNTIMES.computeIfAbsent(key,ignored->new HydraSemanticRuntime(new HydraSemanticRuntime.Engine(){public List<HydraSemanticRuntime.Diagnostic> validate(String state,String trigger){return adapter.validate(state,trigger);}public List<HydraSemanticRuntime.Event> deriveEvents(String before,String after,String trigger,long sequence){return adapter.deriveEvents(before,after,trigger,sequence);}public List<HydraSemanticRuntime.Event> runEventFlow(List<HydraSemanticRuntime.Event> confirmed,String state){return adapter.runEventFlow(confirmed,state);}public HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> confirmed,List<HydraSemanticRuntime.Event> flow){return adapter.updateGamification(previous,confirmed,flow);}}));}}
                private static TransactionalEditingDomain requireDomain(EObject target){Objects.requireNonNull(target,"target");TransactionalEditingDomain domain=TransactionUtil.getEditingDomain(target);if(domain==null)throw new IllegalStateException("Sirius HYDRA operation requires a TransactionalEditingDomain");return domain;}
                private static Map<String,Object> safeMap(Map<String,Object> value){return value==null?Map.of():Map.copyOf(value);}private static String safe(String value){return value==null?"":value;}
            }
            """.formatted(packageName);}

    /** Descripción: Genera el listener pre-commit EMF Transaction que convierte commits Sirius en commits HYDRA reversibles. Entradas: packageName. Salida: fuente Java del lifecycle. */ private String transactionLifecycleSource(String packageName){return """
            package %s;
            import java.lang.ref.WeakReference;
            import java.util.Collections;
            import java.util.Map;
            import java.util.WeakHashMap;
            import org.eclipse.emf.common.command.AbstractCommand;
            import org.eclipse.emf.common.command.Command;
            import org.eclipse.emf.common.notify.Notification;
            import org.eclipse.emf.ecore.EObject;
            import org.eclipse.emf.ecore.resource.Resource;
            import org.eclipse.emf.ecore.util.EcoreUtil;
            import org.eclipse.emf.transaction.ResourceSetChangeEvent;
            import org.eclipse.emf.transaction.ResourceSetListenerImpl;
            import org.eclipse.emf.transaction.RollbackException;
            import org.eclipse.emf.transaction.TransactionalEditingDomain;
            import org.eclipse.emf.transaction.util.TransactionUtil;
            /** Installs one pre-commit listener per Sirius editing domain and owns undo/redo restoration of HYDRA state. */
            public final class HydraSiriusTransactionLifecycle extends ResourceSetListenerImpl {
                private static final Map<TransactionalEditingDomain,WeakReference<HydraSiriusTransactionLifecycle>> LISTENERS=Collections.synchronizedMap(new WeakHashMap<>());
                private HydraSiriusTransactionLifecycle(){super();}
                public static boolean attach(EObject target){if(target==null)return false;TransactionalEditingDomain domain=TransactionUtil.getEditingDomain(target);if(domain==null)return false;synchronized(LISTENERS){WeakReference<HydraSiriusTransactionLifecycle> reference=LISTENERS.get(domain);HydraSiriusTransactionLifecycle listener=reference==null?null:reference.get();if(listener==null){listener=new HydraSiriusTransactionLifecycle();domain.addResourceSetListener(listener);LISTENERS.put(domain,new WeakReference<>(listener));}return true;}}
                @Override public boolean isPrecommitOnly(){return true;}
                @Override public Command transactionAboutToCommit(ResourceSetChangeEvent event)throws RollbackException{EObject root=root(event);return root==null?null:new HydraCommitCommand(root);}
                private static EObject root(ResourceSetChangeEvent event){for(Notification notification:event.getNotifications()){Object notifier=notification.getNotifier();if(notifier instanceof EObject object)return EcoreUtil.getRootContainer(object);if(notifier instanceof Resource resource&&!resource.getContents().isEmpty())return EcoreUtil.getRootContainer(resource.getContents().get(0));}return null;}
                private static final class HydraCommitCommand extends AbstractCommand {
                    private final EObject root;private HydraSemanticRuntime.Snapshot before;private HydraSemanticRuntime.Snapshot after;
                    HydraCommitCommand(EObject root){super("HYDRA semantic commit");this.root=root;}
                    @Override protected boolean prepare(){return root!=null;}
                    @Override public void execute(){before=HydraSiriusRuntime.checkpoint(root);try{HydraSiriusRuntime.synchronize(root,"SIRIUS_TRANSACTION_COMMIT");after=HydraSiriusRuntime.checkpoint(root);}catch(RuntimeException|Error failure){HydraSiriusRuntime.restoreOrRebaseline(root,before,"SIRIUS_FAILED_COMMIT_REBASELINE");throw failure;}}
                    @Override public boolean canUndo(){return before!=null;}
                    @Override public void undo(){if(before!=null)HydraSiriusRuntime.restoreOrRebaseline(root,before,"SIRIUS_UNDO_REBASELINE");}
                    @Override public void redo(){if(after!=null)HydraSiriusRuntime.restoreOrRebaseline(root,after,"SIRIUS_REDO_REBASELINE");}
                }
            }
            """.formatted(packageName);}

    /** Descripción: Procesa la operación «runtimeToggleHandlerSource» coordinando las responsabilidades de SiriusDesktopTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String runtimeToggleHandlerSource(String packageName){return """
            package %s;

            import org.eclipse.core.commands.AbstractHandler;
            import org.eclipse.core.commands.ExecutionEvent;
            import org.eclipse.core.commands.ExecutionException;
            import org.eclipse.jface.dialogs.MessageDialog;
            import org.eclipse.ui.handlers.HandlerUtil;

            /** Eclipse toolbar handler for the generated HYDRA runtime gate. */
            public final class HydraRuntimeToggleHandler extends AbstractHandler {
                @Override public Object execute(ExecutionEvent event) throws ExecutionException {
                    boolean enabled = HydraRuntimeActivation.toggle();
                    MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "HYDRA runtime",
                            "HYDRA runtime for " + HydraRuntimeActivation.METAMODEL_ID + (enabled ? " enabled." : " disabled."));
                    return null;
                }
            }
            """.formatted(packageName);}

    /** Descripción: Obtiene la información correspondiente a «readme» dentro de SiriusDesktopTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación; pluginId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String readme(TechnologyCompilationRequest request, MetaModel metamodel, EditorDefinitionModel editor, String pluginId){return """
            # HYDRA Sirius Desktop integration

            Separate Eclipse plug-in generated from `%s` and editor definition `%s`.

            - Plug-in ID: `%s`.
            - The generated plug-in does not modify EMF-generated Java classes.
            - Diagram viewpoints, node/edge mappings and palette tools are translated to an `.odesign` resource.
            - Non-diagram HYDRA representations remain in canonical JSON and are reported as partial fidelity.
            - `runtime-activation.json` binds runtime behavior to the source metamodel; the generated Eclipse toolbar command toggles it without uninstalling the plug-in.
            - Validation, quick-fix, operation and event services execute through a shared transactional lifecycle; Sirius pre-commit triggers publish Events → EventFlow → Gamification atomically and undo/redo restores checkpoints without replay.
            - Build with Tycho using `mvn verify` and install the resulting bundle in an Eclipse application containing Sirius Desktop.
            - Injection profile: `%s`.
            """.formatted(metamodel.id(), editor.id(), pluginId, request.profile().id());}
}
