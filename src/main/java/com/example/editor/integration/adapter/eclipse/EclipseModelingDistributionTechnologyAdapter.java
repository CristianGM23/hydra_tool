package com.example.editor.integration.adapter.eclipse;

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
import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.core.MetaModel;

import tools.jackson.databind.json.JsonMapper;

/**
 * Generates a feature-based Eclipse Modeling distribution that carries HYDRA's
 * canonical runtime layers as an isolated, reversible plug-in. The product
 * deliberately depends on the host modeling bundles instead of patching EMF,
 * Sirius, GMF or Xtext generated sources.
 */
@Component
public class EclipseModelingDistributionTechnologyAdapter implements TechnologyAdapter {
    private static final Set<IntegrationArtifactKind> SUPPORTED = EnumSet.allOf(IntegrationArtifactKind.class);
    private static final String DEFAULT_ECLIPSE_REPOSITORY = "https://download.eclipse.org/releases/2026-09";
    private final JsonMapper mapper;
    private final EcoreMetamodelAdapter ecoreAdapter;

    /** Descripción: Inicializa una instancia de EclipseModelingDistributionTechnologyAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento; ecoreAdapter: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EclipseModelingDistributionTechnologyAdapter(JsonMapper mapper, EcoreMetamodelAdapter ecoreAdapter) {
        this.mapper = mapper;
        this.ecoreAdapter = ecoreAdapter;
    }

    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology() { return IntegrationTechnology.ECLIPSE_MODELING_DISTRIBUTION; }
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> supportedArtifactKinds() { return Set.copyOf(SUPPORTED); }
    /** Eclipse puede empaquetar un editor desde metamodelo o desde grammar.<id> sin raíz Ecore obligatoria. */ /** Descripción: Ejecuta la operación «semanticRootKinds» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> semanticRootKinds() { return Set.of(IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.GRAMMAR); }

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request) {
        try {
            MetaModel metamodel = request.find(IntegrationArtifactKind.METAMODEL)
                    .map(artifact -> mapper.readValue(artifact.content(), MetaModel.class)).orElse(null);
            var grammar = TechnologyAdapterTextualCustomizationSupport.grammar(request, mapper);
            if(metamodel == null && grammar == null) throw new TechnologyCompilationException("Eclipse Modeling requires a metamodel or grammar semantic root");
            String semanticScopeId = request.profile().semanticScopeId();
            if(semanticScopeId == null || semanticScopeId.isBlank()) semanticScopeId = metamodel != null ? metamodel.id() : "grammar." + grammar.id();
            String semanticName = metamodel != null ? metamodel.name() : grammar.name();
            String root = TechnologyAdapterSupport.generatedRoot(request, semanticScopeId + "-eclipse-modeling");
            String slug = TechnologyAdapterSupport.slug(semanticScopeId);
            String parentGroupId = "com.generated." + slug + ".hydra";
            String parentArtifactId = "modeling-distribution";
            String runtimePluginId = TechnologyAdapterSupport.javaPackage(
                    request.profile().target().settings().get("runtimePluginId"),
                    "com.generated." + slug + ".hydra.runtime");
            String productId = TechnologyAdapterSupport.javaPackage(
                    request.profile().target().settings().get("productId"),
                    "com.generated." + slug + ".modeling.product");
            String featureId = TechnologyAdapterSupport.javaPackage(
                    request.profile().target().settings().get("featureId"),
                    "com.generated." + slug + ".modeling.feature");
            String repository = request.profile().target().settings().getOrDefault("eclipseRepository", DEFAULT_ECLIPSE_REPOSITORY);
            String tychoVersion = request.profile().target().settings().getOrDefault("tychoVersion", "4.0.10");
            String packageName = runtimePluginId + ".runtime";
            String commandPrefix = runtimePluginId + ".runtime";
            String javaPath = TechnologyAdapterSupport.javaPath(packageName);
            List<String> sourceIds = TechnologyAdapterGenerationSupport.sourceIds(request);
            boolean visualStack = TechnologyAdapterSelfContainedDependencySupport.hasVisualEditor(request);
            boolean xtextStack = TechnologyAdapterSelfContainedDependencySupport.grammarTechnology(request, mapper) == com.example.editor.grammar.core.GrammarTechnology.XTEXT;
            List<String> p2Units = requiredP2Units(visualStack, xtextStack);
            List<TechnologyGeneratedFile> files = new ArrayList<>();

            files.add(TechnologyAdapterSupport.text(root + "/pom.xml", "application/xml",
                    parentPom(parentGroupId, parentArtifactId, tychoVersion, repository), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/target-platform/eclipse-modeling.target", "application/xml",
                    targetPlatform(repository, p2Units), sourceIds));

            String runtimeRoot = root + "/runtime-plugin";
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/pom.xml", "application/xml", bundlePom(parentGroupId, parentArtifactId, runtimePluginId), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/META-INF/MANIFEST.MF", "text/plain", manifest(runtimePluginId, visualStack, xtextStack), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/build.properties", "text/plain", buildProperties(), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/plugin.xml", "application/xml", pluginXml(packageName, commandPrefix), sourceIds));
            if(metamodel != null) {
                files.add(TechnologyAdapterSupport.binary(runtimeRoot + "/model/" + metamodel.id() + ".ecore", "application/xml",
                        ecoreAdapter.toBytes(metamodel).value(), List.of(metamodel.id())));
            }
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/src/" + javaPath + "/HydraRuntimeActivation.java", "text/x-java-source",
                    persistentActivationSource(packageName, request.profile().metamodelId(), request.profile().semanticScopeId()), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/src/" + javaPath + "/HydraRuntimeToggleHandler.java", "text/x-java-source",
                    toggleHandlerSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/src/" + javaPath + "/HydraRuntimeEnableHandler.java", "text/x-java-source",
                    enableHandlerSource(packageName, true), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/src/" + javaPath + "/HydraRuntimeDisableHandler.java", "text/x-java-source",
                    enableHandlerSource(packageName, false), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/src/" + javaPath + "/HydraLayerRegistry.java", "text/x-java-source",
                    layerRegistrySource(packageName, request), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/src/" + javaPath + "/HydraSemanticRuntime.java", "text/x-java-source",
                    TechnologyAdapterRuntimeLifecycleSupport.javaSemanticRuntimeSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/src/" + javaPath + "/HydraPortableRuntimeBridge.java", "text/x-java-source",
                    portableRuntimeBridgeSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtimeRoot + "/src/" + javaPath + "/HydraEclipseCommandLifecycle.java", "text/x-java-source",
                    commandLifecycleSource(packageName), sourceIds));
            files.addAll(TechnologyAdapterGenerationSupport.canonicalDocuments(runtimeRoot, "hydra", request));
            if(request.find(IntegrationArtifactKind.GRAMMAR).isPresent()){files.addAll(TechnologyAdapterTextualCustomizationSupport.neutralFiles(runtimeRoot,request,mapper));files.add(TechnologyAdapterSupport.text(runtimeRoot + "/src/" + javaPath + "/HydraTextualEditorCustomization.java", "text/x-java-source", textualCustomizationSource(packageName,TechnologyAdapterTextualCustomizationSupport.customization(request,mapper)), sourceIds));}
            if(TechnologyAdapterVisualFullStackSupport.available(request)){files.addAll(TechnologyAdapterVisualFullStackSupport.neutralFiles(runtimeRoot,request,mapper));files.add(TechnologyAdapterSupport.text(runtimeRoot + "/src/" + javaPath + "/HydraVisualFullStack.java", "text/x-java-source", TechnologyAdapterVisualFullStackSupport.javaBridgeSource(packageName,request), sourceIds));}

            files.addAll(TechnologyAdapterSelfContainedDependencySupport.eclipseP2VendorFiles(root, runtimePluginId, repository, p2Units, sourceIds));

            String featureRoot = root + "/feature";
            files.add(TechnologyAdapterSupport.text(featureRoot + "/pom.xml", "application/xml", featurePom(parentGroupId, parentArtifactId, featureId), sourceIds));
            files.add(TechnologyAdapterSupport.text(featureRoot + "/feature.xml", "application/xml", featureXml(featureId, runtimePluginId, visualStack, xtextStack), sourceIds));

            String productRoot = root + "/product";
            files.add(TechnologyAdapterSupport.text(productRoot + "/pom.xml", "application/xml", productPom(parentGroupId, parentArtifactId, productId), sourceIds));
            files.add(TechnologyAdapterSupport.text(productRoot + "/hydra-modeling.product", "application/xml",
                    productXml(productId, featureId, semanticName), sourceIds));
            files.add(TechnologyAdapterSupport.text(productRoot + "/category.xml", "application/xml",
                    categoryXml(featureId), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/README.md", "text/markdown",
                    readme(request, semanticScopeId, runtimePluginId, repository, visualStack, xtextStack), sourceIds));

            List<IntegrationDependencyChange> dependencies = p2Units.stream().map(unit -> dependency("p2", unit, "0.0.0")).toList();
            List<IntegrationVerificationStep> verification = List.of(
                    new IntegrationVerificationStep("verify-eclipse-modeling-product",
                            "Resolve the target platform and build the Eclipse product", root,
                            List.of("mvn", "-q", "verify"), 0, true));
            List<IntegrationDiagnostic> diagnostics = new ArrayList<>(
                    TechnologyAdapterSupport.unsupportedDiagnostics(request, SUPPORTED, "Eclipse Modeling distribution"));
            return new TechnologyCompilationResult(technology(), files, dependencies,
                    List.of("Build the feature-based product with Tycho",
                            "Prepare offline/p2 once from the target repository",
                            "Install the generated product or feature together with only missing units from the local p2 repository",
                            "Use the HYDRA toolbar command to enable the runtime for semantic context " + semanticScopeId),
                    List.of("Disable the HYDRA runtime from the toolbar",
                            "Uninstall the generated HYDRA feature or remove the generated product module"),
                    verification, diagnostics,
                    Map.of("generatorVersion", request.generatorVersion(), "moduleRoot", root,
                            "runtimePluginId", runtimePluginId, "productId", productId,
                            "featureId", featureId, "eclipseRepository", repository,
                            "runtimeActivation", "PERSISTENT_TOGGLEABLE"));
        } catch (RuntimeException exception) {
            if (exception instanceof TechnologyCompilationException) throw exception;
            throw new TechnologyCompilationException("Could not generate Eclipse Modeling distribution", exception);
        }
    }

    /** Descripción: Ejecuta la operación «textualCustomizationSource» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación; c: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String textualCustomizationSource(String packageName,com.example.editor.grammar.core.GrammarTextualEditorCustomization c){StringBuilder hover=new StringBuilder();c.hovers().forEach((key,value)->{if(hover.length()>0)hover.append(",");hover.append("Map.entry(").append(javaString(key)).append(",").append(javaString(value.title()+"\n"+value.description())).append(")");});return """
            package %s;
            import java.util.Map;
            /** Neutral textual appearance/hover descriptor embedded in the Eclipse distribution. */
            public final class HydraTextualEditorCustomization {
              public static final String FONT_FAMILY=%s; public static final int FONT_SIZE=%d; public static final int LINE_HEIGHT=%d;
              public static final String FOREGROUND=%s; public static final String BACKGROUND=%s;
              public static final Map<String,String> HOVERS=Map.ofEntries(%s);
              private HydraTextualEditorCustomization(){}
            }
            """.formatted(packageName,javaString(c.fontFamily()),c.fontSize(),c.lineHeight(),javaString(c.foreground()),javaString(c.background()),hover);}

    /** Descripción: Ejecuta la operación «dependency» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: ecosystem: dato de entrada necesario para la operación; coordinate: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDependencyChange dependency(String ecosystem, String coordinate, String version) {
        return new IntegrationDependencyChange(ecosystem, coordinate, version, "runtime", IntegrationDependencyAction.ADD);
    }

    /** Descripción: Ejecuta la operación «parentPom» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: parentGroupId: identificador que delimita el recurso objetivo; parentArtifactId: identificador que delimita el recurso objetivo; tychoVersion: dato de entrada necesario para la operación; repository: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String parentPom(String parentGroupId, String parentArtifactId, String tychoVersion, String repository) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId><artifactId>%s</artifactId><version>1.0.0-SNAPSHOT</version><packaging>pom</packaging>
                  <properties><tycho.version>%s</tycho.version><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding><maven.compiler.release>21</maven.compiler.release></properties>
                  <modules><module>runtime-plugin</module><module>feature</module><module>product</module></modules>
                  <repositories><repository><id>eclipse-modeling-target</id><layout>p2</layout><url>%s</url></repository></repositories>
                  <build><plugins><plugin><groupId>org.eclipse.tycho</groupId><artifactId>tycho-maven-plugin</artifactId><version>${tycho.version}</version><extensions>true</extensions></plugin><plugin><groupId>org.eclipse.tycho</groupId><artifactId>target-platform-configuration</artifactId><version>${tycho.version}</version><configuration><resolver>p2</resolver><target><file>${maven.multiModuleProjectDirectory}/target-platform/eclipse-modeling.target</file></target></configuration></plugin></plugins></build>
                </project>
                """.formatted(parentGroupId, parentArtifactId, tychoVersion, TechnologyAdapterSupport.xml(repository));
    }

    /** Genera un target p2 mínimo y configurable; no incorpora Xtext/Sirius si el editor no los usa. */
    /** Descripción: Ejecuta la operación «targetPlatform» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: repository: dato de entrada necesario para la operación; units: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String targetPlatform(String repository, List<String> units) {
        StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<?pde version=\"3.8\"?>\n<target name=\"HYDRA Eclipse Modeling target\" sequenceNumber=\"1\"><locations><location includeAllPlatforms=\"false\" includeConfigurePhase=\"true\" includeMode=\"planner\" includeSource=\"false\" type=\"InstallableUnit\">\n");
        for(String unit:units) xml.append("  <unit id=\"").append(TechnologyAdapterSupport.xml(unit)).append("\" version=\"0.0.0\"/>\n");
        xml.append("  <repository location=\"").append(TechnologyAdapterSupport.xml(repository)).append("\"/>\n</location></locations></target>\n");
        return xml.toString();
    }

    /** Descripción: Valida las condiciones necesarias para «requiredP2Units» y rechaza entradas no válidas. Entradas: visual: dato de entrada necesario para la operación; xtext: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> requiredP2Units(boolean visual, boolean xtext){
        List<String> units=new ArrayList<>(List.of("org.eclipse.platform.ide","org.eclipse.emf.ecore","org.eclipse.emf.edit","org.eclipse.emf.edit.ui"));
        if(visual) units.addAll(List.of("org.eclipse.gmf.runtime.notation","org.eclipse.gmf.runtime.diagram.ui","org.eclipse.sirius","org.eclipse.sirius.diagram","org.eclipse.sirius.ui","org.eclipse.sirius.diagram.ui"));
        if(xtext) units.addAll(List.of("org.eclipse.xtext","org.eclipse.xtext.ui"));
        return List.copyOf(units);
    }

    /** Descripción: Ejecuta la operación «bundlePom» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: parentGroupId: identificador que delimita el recurso objetivo; parentArtifactId: identificador que delimita el recurso objetivo; runtimePluginId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String bundlePom(String parentGroupId, String parentArtifactId, String runtimePluginId) {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>%s</groupId><artifactId>%s</artifactId><version>1.0.0-SNAPSHOT</version><relativePath>../pom.xml</relativePath></parent>
                  <artifactId>%s</artifactId><packaging>eclipse-plugin</packaging>
                </project>
                """.formatted(parentGroupId, parentArtifactId, runtimePluginId);
    }

    /** Descripción: Ejecuta la operación «featurePom» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: parentGroupId: identificador que delimita el recurso objetivo; parentArtifactId: identificador que delimita el recurso objetivo; featureId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String featurePom(String parentGroupId, String parentArtifactId, String featureId) {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>%s</groupId><artifactId>%s</artifactId><version>1.0.0-SNAPSHOT</version><relativePath>../pom.xml</relativePath></parent>
                  <artifactId>%s</artifactId><packaging>eclipse-feature</packaging>
                </project>
                """.formatted(parentGroupId, parentArtifactId, featureId);
    }

    /** Descripción: Ejecuta la operación «productPom» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: parentGroupId: identificador que delimita el recurso objetivo; parentArtifactId: identificador que delimita el recurso objetivo; productId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String productPom(String parentGroupId, String parentArtifactId, String productId) {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>%s</groupId><artifactId>%s</artifactId><version>1.0.0-SNAPSHOT</version><relativePath>../pom.xml</relativePath></parent>
                  <artifactId>%s.repository</artifactId><packaging>eclipse-repository</packaging>
                  <build><plugins><plugin><groupId>org.eclipse.tycho</groupId><artifactId>tycho-p2-director-plugin</artifactId><version>${tycho.version}</version><executions><execution><id>materialize-products</id><goals><goal>materialize-products</goal><goal>archive-products</goal></goals><configuration><products><product><id>%s</id></product></products></configuration></execution></executions></plugin></plugins></build>
                </project>
                """.formatted(parentGroupId, parentArtifactId, productId, productId);
    }

    /** Genera Require-Bundle mínimo para evitar instalar frameworks que el editor no usa. */
    /** Descripción: Ejecuta la operación «manifest» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: pluginId: identificador que delimita el recurso objetivo; visual: dato de entrada necesario para la operación; xtext: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String manifest(String pluginId, boolean visual, boolean xtext) {
        List<String> bundles=new ArrayList<>(List.of("org.eclipse.core.runtime","org.eclipse.ui","org.eclipse.jface","org.eclipse.emf.ecore","org.eclipse.emf.edit","org.eclipse.emf.edit.ui"));
        if(visual) bundles.addAll(List.of("org.eclipse.gmf.runtime.notation","org.eclipse.gmf.runtime.diagram.ui","org.eclipse.sirius","org.eclipse.sirius.diagram","org.eclipse.sirius.ui","org.eclipse.sirius.diagram.ui"));
        if(xtext) bundles.addAll(List.of("org.eclipse.xtext","org.eclipse.xtext.ui"));
        return """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-Name: HYDRA Portable Modeling Runtime
                Bundle-SymbolicName: %s;singleton:=true
                Bundle-Version: 1.0.0.qualifier
                Bundle-RequiredExecutionEnvironment: JavaSE-21
                Require-Bundle: %s
                Export-Package: %s.runtime
                """.formatted(pluginId,String.join(",",bundles),pluginId);
    }

    /** Descripción: Crea o prepara el resultado de «buildProperties» dentro de EclipseModelingDistributionTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String buildProperties() { return "source.. = src/\noutput.. = bin/\nbin.includes = META-INF/,.,plugin.xml,hydra/,model/\n"; }

    /** Descripción: Ejecuta la operación «pluginXml» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación; commandPrefix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String pluginXml(String packageName, String commandPrefix) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <?eclipse version="3.4"?>
                <plugin>
                  <extension point="org.eclipse.ui.commands">
                    <category id="%s.category" name="HYDRA Runtime"/>
                    <command id="%s.toggle" name="Toggle HYDRA runtime" categoryId="%s.category"/>
                    <command id="%s.enable" name="Enable HYDRA runtime" categoryId="%s.category"/>
                    <command id="%s.disable" name="Disable HYDRA runtime" categoryId="%s.category"/>
                  </extension>
                  <extension point="org.eclipse.ui.handlers">
                    <handler commandId="%s.toggle" class="%s.HydraRuntimeToggleHandler"/>
                    <handler commandId="%s.enable" class="%s.HydraRuntimeEnableHandler"/>
                    <handler commandId="%s.disable" class="%s.HydraRuntimeDisableHandler"/>
                  </extension>
                  <extension point="org.eclipse.ui.menus">
                    <menuContribution locationURI="toolbar:org.eclipse.ui.main.toolbar?after=additions">
                      <toolbar id="%s.toolbar" label="HYDRA Runtime">
                        <command commandId="%s.toggle" label="HYDRA Runtime" style="push" tooltip="Enable or disable HYDRA validations, quick fixes, events and gamification for the associated metamodel"/>
                      </toolbar>
                    </menuContribution>
                    <menuContribution locationURI="menu:org.eclipse.ui.main.menu?after=additions">
                      <menu id="%s.menu" label="HYDRA Runtime">
                        <command commandId="%s.enable" label="Enable runtime" style="push"/>
                        <command commandId="%s.disable" label="Disable runtime" style="push"/>
                      </menu>
                    </menuContribution>
                  </extension>
                </plugin>
                """.formatted(commandPrefix, commandPrefix, commandPrefix, commandPrefix, commandPrefix, commandPrefix, commandPrefix,
                        commandPrefix, packageName, commandPrefix, packageName, commandPrefix, packageName,
                        commandPrefix, commandPrefix, commandPrefix, commandPrefix, commandPrefix);
    }

    /** Descripción: Genera el gate persistente de activación con ámbito semántico y época para rebaseline seguro. Entradas: packageName: paquete Java; metamodelId: identificador legado; semanticScopeId: ámbito neutral. Salida: devuelve el código fuente del gate. */ private String persistentActivationSource(String packageName, String metamodelId, String semanticScopeId) {
        return """
                package %s;

                import java.util.prefs.Preferences;

                /** Persistent semantic-scope activation gate for the complete Eclipse distribution. */
                public final class HydraRuntimeActivation {
                    public static final String METAMODEL_ID = %s;
                    public static final String SEMANTIC_SCOPE_ID = %s;
                    private static final String KEY = "enabled." + SEMANTIC_SCOPE_ID;
                    private static final String EPOCH_KEY = "epoch." + SEMANTIC_SCOPE_ID;
                    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(HydraRuntimeActivation.class);
                    private HydraRuntimeActivation() { }
                    public static boolean isEnabled(){ return PREFERENCES.getBoolean(KEY, false); }
                    public static boolean isEnabledFor(String semanticScopeId){ return SEMANTIC_SCOPE_ID.equals(semanticScopeId) && isEnabled(); }
                    public static long epoch(){ return PREFERENCES.getLong(EPOCH_KEY, 0L); }
                    public static synchronized boolean setEnabled(boolean value){
                        boolean previous=isEnabled();
                        if(previous!=value){PREFERENCES.putBoolean(KEY,value);PREFERENCES.putLong(EPOCH_KEY,epoch()+1L);}
                        return value;
                    }
                    public static synchronized boolean toggle(){ return setEnabled(!isEnabled()); }
                }
                """.formatted(packageName, javaString(metamodelId), javaString(semanticScopeId));
    }

    /** Descripción: Ejecuta la operación «toggleHandlerSource» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String toggleHandlerSource(String packageName) {
        return """
                package %s;
                import org.eclipse.core.commands.AbstractHandler;
                import org.eclipse.core.commands.ExecutionEvent;
                import org.eclipse.core.commands.ExecutionException;
                import org.eclipse.jface.dialogs.MessageDialog;
                import org.eclipse.ui.handlers.HandlerUtil;
                public final class HydraRuntimeToggleHandler extends AbstractHandler {
                    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
                        boolean enabled = HydraRuntimeActivation.toggle();
                        MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "HYDRA Runtime", enabled ? "HYDRA runtime enabled for this metamodel." : "HYDRA runtime disabled for this metamodel.");
                        return Boolean.valueOf(enabled);
                    }
                }
                """.formatted(packageName);
    }

    /** Descripción: Ejecuta la operación «enableHandlerSource» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación; enabled: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String enableHandlerSource(String packageName, boolean enabled) {
        String type = enabled ? "Enable" : "Disable";
        return """
                package %s;
                import org.eclipse.core.commands.AbstractHandler;
                import org.eclipse.core.commands.ExecutionEvent;
                import org.eclipse.core.commands.ExecutionException;
                public final class HydraRuntime%sHandler extends AbstractHandler {
                    @Override public Object execute(ExecutionEvent event) throws ExecutionException { return Boolean.valueOf(HydraRuntimeActivation.setEnabled(%s)); }
                }
                """.formatted(packageName, type, enabled);
    }

    /** Descripción: Ejecuta la operación «layerRegistrySource» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String layerRegistrySource(String packageName, TechnologyCompilationRequest request) {
        StringBuilder entries = new StringBuilder();
        request.artifacts().stream().sorted((a,b) -> a.kind().name().compareTo(b.kind().name())).forEach(artifact -> {
            if (entries.length() > 0) entries.append(",\n            ");
            entries.append("Map.entry(\"").append(artifact.kind().name()).append("\", \"hydra/")
                    .append(artifact.kind().name().toLowerCase()).append(".json\")");
        });
        return """
                package %s;
                import java.util.Map;
                /** Stable registry of canonical HYDRA layers embedded in the generated plug-in. */
                public final class HydraLayerRegistry {
                    private static final Map<String,String> LAYERS = Map.ofEntries(
                            %s
                    );
                    private HydraLayerRegistry() { }
                    public static Map<String,String> layers(){ return LAYERS; }
                    public static String path(String kind){ return LAYERS.getOrDefault(kind, ""); }
                    public static boolean available(String kind){ return LAYERS.containsKey(kind); }
                }
                """.formatted(packageName, entries);
    }

    /** Descripción: Genera la frontera ejecutable entre EObject y el lifecycle semántico HYDRA, con transacciones y rollback. Entradas: packageName: paquete Java. Salida: devuelve el código fuente del bridge. */ private String portableRuntimeBridgeSource(String packageName) {
        return """
                package %s;

                import java.util.List;
                import java.util.Map;
                import java.util.Objects;
                import org.eclipse.emf.ecore.EObject;

                /** Executable host boundary for portable HYDRA layers embedded in this Eclipse distribution. */
                public final class HydraPortableRuntimeBridge {
                    public interface Adapter {
                        String semanticState(EObject target);
                        List<HydraSemanticRuntime.Diagnostic> validate(String semanticState,String trigger);
                        List<HydraSemanticRuntime.Event> deriveEvents(String before,String after,String trigger,long sequence);
                        List<HydraSemanticRuntime.Event> runEventFlow(List<HydraSemanticRuntime.Event> confirmed,String semanticState);
                        HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> confirmed,List<HydraSemanticRuntime.Event> flow);
                        boolean applyQuickFix(EObject target,String diagnosticId,String actionId,Map<String,Object> parameters);
                        Object invoke(EObject target,String operationId,Map<String,Object> parameters);
                        void restoreHost(EObject target,String semanticState);
                    }
                    private final Adapter adapter;
                    private final HydraSemanticRuntime runtime;
                    public HydraPortableRuntimeBridge(Adapter adapter){
                        this.adapter=Objects.requireNonNull(adapter,"adapter");
                        this.runtime=new HydraSemanticRuntime(new HydraSemanticRuntime.Engine(){
                            @Override public List<HydraSemanticRuntime.Diagnostic> validate(String state,String trigger){return adapter.validate(state,trigger);}
                            @Override public List<HydraSemanticRuntime.Event> deriveEvents(String before,String after,String trigger,long sequence){return adapter.deriveEvents(before,after,trigger,sequence);}
                            @Override public List<HydraSemanticRuntime.Event> runEventFlow(List<HydraSemanticRuntime.Event> events,String state){return adapter.runEventFlow(events,state);}
                            @Override public HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> events,List<HydraSemanticRuntime.Event> flow){return adapter.updateGamification(previous,events,flow);}
                        });
                    }
                    public List<HydraSemanticRuntime.Diagnostic> validate(EObject target){return runtime.validateHost(adapter.semanticState(target),"ECLIPSE_VALIDATE");}
                    public HydraSemanticRuntime.CommitResult synchronize(EObject target,String trigger){return runtime.synchronize(adapter.semanticState(target),trigger);}
                    public HydraSemanticRuntime.Snapshot checkpoint(){return runtime.checkpoint();}
                    public HydraSemanticRuntime.Snapshot checkpointBaseline(){return runtime.checkpointBaseline();}
                    public void restoreRuntime(HydraSemanticRuntime.Snapshot snapshot){runtime.restore(snapshot);}
                    public boolean applyQuickFix(EObject target,String diagnosticId,String actionId,Map<String,Object> parameters){
                        HydraSemanticRuntime.Snapshot before=runtime.checkpoint();String hostBefore=adapter.semanticState(target);
                        try{boolean applied=adapter.applyQuickFix(target,diagnosticId,actionId,safe(parameters));if(applied)runtime.synchronize(adapter.semanticState(target),"ECLIPSE_QUICK_FIX");return applied;}
                        catch(RuntimeException exception){adapter.restoreHost(target,hostBefore);runtime.restore(before);throw exception;}
                    }
                    public Object invoke(EObject target,String operationId,Map<String,Object> parameters){
                        HydraSemanticRuntime.Snapshot before=runtime.checkpoint();String hostBefore=adapter.semanticState(target);
                        try{Object result=adapter.invoke(target,operationId,safe(parameters));runtime.synchronize(adapter.semanticState(target),"ECLIPSE_OPERATION");return result;}
                        catch(RuntimeException exception){adapter.restoreHost(target,hostBefore);runtime.restore(before);throw exception;}
                    }
                    public List<HydraSemanticRuntime.Event> events(){return runtime.events();}
                    public HydraSemanticRuntime.GamificationState gamification(){return runtime.gamification();}
                    private static Map<String,Object> safe(Map<String,Object> value){return value==null?Map.of():Map.copyOf(value);}
                }
                """.formatted(packageName);
    }

    /** Descripción: Genera el wrapper de CommandStack EMF que mantiene checkpoints completos en execute/undo/redo. Entradas: packageName: paquete Java. Salida: devuelve el código fuente del lifecycle. */ private String commandLifecycleSource(String packageName){return """
            package %s;
            import java.util.ArrayDeque;
            import java.util.Deque;
            import java.util.Objects;
            import org.eclipse.emf.common.command.Command;
            import org.eclipse.emf.common.command.CommandStack;
            import org.eclipse.emf.ecore.EObject;
            /** Transactional command lifecycle that keeps HYDRA observable state aligned with EMF undo/redo. */
            public final class HydraEclipseCommandLifecycle {
                private final HydraPortableRuntimeBridge bridge;
                private final Deque<Transition> undoTransitions=new ArrayDeque<>();
                private final Deque<Transition> redoTransitions=new ArrayDeque<>();
                public HydraEclipseCommandLifecycle(HydraPortableRuntimeBridge bridge){this.bridge=Objects.requireNonNull(bridge,"bridge");}
                public void baseline(EObject root){bridge.synchronize(root,"ECLIPSE_BASELINE");}
                public void execute(CommandStack stack,Command command,EObject root){
                    Objects.requireNonNull(stack,"stack");Objects.requireNonNull(command,"command");
                    HydraSemanticRuntime.Snapshot before=bridge.checkpoint();
                    stack.execute(command);
                    if(stack.getMostRecentCommand()!=command) return;
                    try{
                        bridge.synchronize(root,"ECLIPSE_COMMAND");
                        undoTransitions.push(new Transition(command,before,bridge.checkpoint()));redoTransitions.clear();
                    }catch(RuntimeException exception){
                        if(stack.canUndo()&&stack.getUndoCommand()==command) stack.undo();
                        bridge.restoreRuntime(before);
                        throw exception;
                    }
                }
                public void undo(CommandStack stack,EObject root){
                    Objects.requireNonNull(stack,"stack");if(!stack.canUndo()) return;
                    Command command=stack.getUndoCommand();HydraSemanticRuntime.Snapshot current=bridge.checkpoint();
                    stack.undo();if(stack.getMostRecentCommand()!=command) return;
                    Transition transition=!undoTransitions.isEmpty()&&undoTransitions.peek().command()==command?undoTransitions.peek():null;
                    try{
                        if(transition==null) bridge.synchronize(root,"ECLIPSE_UNDO"); else restoreOrRebaseline(transition.before(),root,"ECLIPSE_UNDO");
                    }catch(RuntimeException exception){
                        if(stack.canRedo()&&stack.getRedoCommand()==command) stack.redo();bridge.restoreRuntime(current);throw exception;
                    }
                    if(transition!=null){undoTransitions.pop();redoTransitions.push(transition);}
                }
                public void redo(CommandStack stack,EObject root){
                    Objects.requireNonNull(stack,"stack");if(!stack.canRedo()) return;
                    Command command=stack.getRedoCommand();HydraSemanticRuntime.Snapshot current=bridge.checkpoint();
                    stack.redo();if(stack.getMostRecentCommand()!=command) return;
                    Transition transition=!redoTransitions.isEmpty()&&redoTransitions.peek().command()==command?redoTransitions.peek():null;
                    try{
                        if(transition==null) bridge.synchronize(root,"ECLIPSE_REDO"); else restoreOrRebaseline(transition.after(),root,"ECLIPSE_REDO");
                    }catch(RuntimeException exception){
                        if(stack.canUndo()&&stack.getUndoCommand()==command) stack.undo();bridge.restoreRuntime(current);throw exception;
                    }
                    if(transition!=null){redoTransitions.pop();undoTransitions.push(transition);}
                }
                private void restoreOrRebaseline(HydraSemanticRuntime.Snapshot snapshot,EObject root,String trigger){
                    if(HydraRuntimeActivation.isEnabled()&&snapshot.activationEpoch()==HydraRuntimeActivation.epoch()) bridge.restoreRuntime(snapshot);
                    else bridge.synchronize(root,trigger);
                }
                private record Transition(Command command,HydraSemanticRuntime.Snapshot before,HydraSemanticRuntime.Snapshot after){}
            }
            """.formatted(packageName);}

    /** Feature mínimo: los requisitos externos son sólo los que el editor realmente consume. */
    /** Descripción: Ejecuta la operación «featureXml» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: featureId: identificador que delimita el recurso objetivo; runtimePluginId: identificador que delimita el recurso objetivo; visual: dato de entrada necesario para la operación; xtext: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String featureXml(String featureId, String runtimePluginId, boolean visual, boolean xtext) {
        StringBuilder requires=new StringBuilder("<import plugin=\"org.eclipse.emf.ecore\"/><import plugin=\"org.eclipse.emf.edit\"/><import plugin=\"org.eclipse.emf.edit.ui\"/>");
        if(visual) requires.append("<import plugin=\"org.eclipse.gmf.runtime.notation\"/><import plugin=\"org.eclipse.gmf.runtime.diagram.ui\"/><import plugin=\"org.eclipse.sirius\"/><import plugin=\"org.eclipse.sirius.diagram\"/><import plugin=\"org.eclipse.sirius.ui\"/><import plugin=\"org.eclipse.sirius.diagram.ui\"/>");
        if(xtext) requires.append("<import plugin=\"org.eclipse.xtext\"/><import plugin=\"org.eclipse.xtext.ui\"/>");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <feature id="%s" label="HYDRA Modeling Runtime" version="1.0.0.qualifier" provider-name="HYDRA">
                  <requires>%s</requires>
                  <plugin id="%s" download-size="0" install-size="0" version="0.0.0" unpack="false"/>
                </feature>
                """.formatted(featureId,requires,runtimePluginId);
    }

    /** Descripción: Ejecuta la operación «productXml» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: productId: identificador que delimita el recurso objetivo; featureId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String productXml(String productId, String featureId, String name) {
        String label = name == null || name.isBlank() ? "HYDRA Modeling Distribution" : name + " Modeling Distribution";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <?pde version="3.5"?>
                <product name="%s" uid="%s" id="org.eclipse.platform.ide" application="org.eclipse.ui.ide.workbench" version="1.0.0.qualifier" useFeatures="true" includeLaunchers="true">
                  <configIni use="default"/><launcherArgs><vmArgs>-Xms256m -Xmx2048m</vmArgs></launcherArgs>
                  <features><feature id="org.eclipse.platform"/><feature id="%s"/></features>
                  <configurations><plugin id="org.eclipse.core.runtime" autoStart="true" startLevel="4"/><plugin id="org.eclipse.equinox.common" autoStart="true" startLevel="2"/><plugin id="org.eclipse.equinox.ds" autoStart="true" startLevel="2"/></configurations>
                </product>
                """.formatted(TechnologyAdapterSupport.xml(label), productId, featureId);
    }

    /** Descripción: Ejecuta la operación «categoryXml» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: featureId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String categoryXml(String featureId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <site>
                  <feature url="features/%s_1.0.0.qualifier.jar" id="%s" version="1.0.0.qualifier">
                    <category name="hydra-modeling"/>
                  </feature>
                  <category-def name="hydra-modeling" label="HYDRA Modeling"/>
                </site>
                """.formatted(featureId, featureId);
    }

    /** Generates documentation that reflects the dependencies actually selected for this distribution. */
    /** Descripción: Obtiene la información correspondiente a «readme» dentro de EclipseModelingDistributionTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; metamodel: dato de entrada necesario para la operación; runtimePluginId: identificador que delimita el recurso objetivo; repository: dato de entrada necesario para la operación; visual: dato de entrada necesario para la operación; xtext: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String readme(TechnologyCompilationRequest request, String semanticScopeId, String runtimePluginId,
            String repository, boolean visual, boolean xtext) {
        StringBuilder stack = new StringBuilder("- Eclipse Platform / PDE runtime\n- EMF Ecore + Edit\n");
        if (visual) stack.append("- GMF Runtime notation/diagram UI\n- Sirius Desktop runtime/UI + diagram UI\n");
        if (xtext) stack.append("- Xtext runtime/UI\n");
        stack.append("- HYDRA portable runtime plug-in `").append(runtimePluginId).append("`\n");
        return """
                # HYDRA Eclipse Modeling distribution

                This generated project packages a feature-based Eclipse product for semantic context `%s`.

                ## Included modeling stack
                %s
                The build resolves third-party Eclipse bundles from `%s` once and can mirror the selected installable units into `offline/p2`; installation then uses only the local p2 repository and reuses compatible bundles already present in the host. The generated HYDRA plug-in contains the selected canonical documents and a persistent, metamodel-scoped activation gate. The main toolbar exposes **HYDRA Runtime** so validations, quick fixes, events/EventFlow and gamification can be enabled or disabled without uninstalling the feature.

                Runtime activation starts disabled unless the profile explicitly requests otherwise. The generated `HydraPortableRuntimeBridge` and `HydraEclipseCommandLifecycle` execute the HYDRA lifecycle around confirmed EMF commands. Validation is side-effect-free; confirmed commits run Events → EventFlow → Gamification; undo/redo restores full HYDRA checkpoints; activation epoch changes force a silent rebaseline.

                Build: `mvn verify`.

                Profile: `%s`.
                """.formatted(semanticScopeId, stack, repository, request.profile().id());
    }

    /** Descripción: Ejecuta la operación «javaString» correspondiente a la responsabilidad de EclipseModelingDistributionTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String javaString(String value) {
        String escaped = String.valueOf(value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
