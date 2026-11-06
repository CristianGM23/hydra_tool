package com.example.editor.integration.adapter.xtext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationBuildSystem;
import com.example.editor.integration.IntegrationDependencyChange;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationTarget;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

/**
 * Fase B del anfitrión Xtext (01/11): el módulo UI no se podía construir en ningún anfitrión (org.eclipse.xtext.ui solo está en p2, Maven Central
 * termina en la 2.10.0) y el adaptador ignoraba target.buildSystem. Con TYCHO el paquete es un reactor de bundles eclipse-plugin que hereda del
 * padre Tycho del anfitrión, con MANIFEST completos antes de MWE2 (Tycho los lee al montar el reactor). El contenido de los MANIFEST es el que se
 * verificó en un anfitrión Tycho 5.0.4 real (Eclipse 2026-09, Xtext 2.44.0): MWE2 no les añade nada y el bundle UI carga en un workbench.
 */
class XtextTychoPackageTests {
    private static final String ROOT = "generated-integration/people-xtext/";
    private static final String RUNTIME = "com.generated.people.xtext.people";
    private final XtextTechnologyAdapter adapter = new XtextTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);

    @Test void withTychoEveryModuleIsAnEclipsePluginOfAReactorThatInheritsFromTheHostTychoParent(){
        TechnologyCompilationResult result = adapter.compile(tycho(TechnologyAdapterTestFixtures.xtextRequest(), Map.of("tychoParent", "hydra.host:xtext-tycho-host:1.0.0-SNAPSHOT")));
        assertThat(text(result, ROOT + "pom.xml")).contains(
                "<parent><groupId>hydra.host</groupId><artifactId>xtext-tycho-host</artifactId><version>1.0.0-SNAPSHOT</version><relativePath>../../pom.xml</relativePath></parent>",
                "<packaging>pom</packaging>", "<module>" + RUNTIME + "</module>", "<module>" + RUNTIME + ".ide</module>", "<module>" + RUNTIME + ".ui</module>");
        for(String module : List.of(RUNTIME, RUNTIME + ".ide", RUNTIME + ".ui")){
            assertThat(text(result, ROOT + module + "/pom.xml")).contains("<artifactId>" + module + "</artifactId><packaging>eclipse-plugin</packaging>")
                    .doesNotContain("<dependencies>", "build-helper-maven-plugin");
            assertThat(text(result, ROOT + module + "/META-INF/MANIFEST.MF")).contains("Bundle-SymbolicName: " + module + ";singleton:=true\n", "Bundle-Version: 1.0.0.qualifier\n");
        }
        assertThat(result.dependencies()).extracting(IntegrationDependencyChange::ecosystem).containsOnly("p2");
        assertThat(result.dependencies()).extracting(IntegrationDependencyChange::coordinate)
                .contains("org.eclipse.xtext.ui.feature.group", "org.eclipse.xtext.ide", "org.eclipse.lsp4j");
        assertThat(result.diagnostics()).extracting(IntegrationDiagnostic::code).doesNotContain("XTEXT_UI_REQUIRES_TYCHO");
    }

    @Test void theManifestsDeclareWhatTheHydraClassesAndTheMwe2OutputNeedBeforeMwe2Runs(){
        TechnologyCompilationResult result = adapter.compile(tycho(TechnologyAdapterTestFixtures.xtextRequest(), Map.of("tychoParent", "h:p:1")));
        assertThat(text(result, ROOT + RUNTIME + "/META-INF/MANIFEST.MF")).contains(
                " " + RUNTIME + ".parser.antlr.internal,\n", " " + RUNTIME + ".runtime,\n", " " + RUNTIME + ".services,\n",
                " " + RUNTIME + ".people,\n", " " + RUNTIME + ".people.impl,\n", " " + RUNTIME + ".people.util,\n",
                "org.antlr.runtime;bundle-version=\"[3.2.0,3.2.1)\"", " org.eclipse.emf.ecore.change,\n", "org.eclipse.xtext.xbase.lib;bundle-version=\"2.44.0\"",
                "Import-Package: org.apache.log4j\n");
        assertThat(text(result, ROOT + RUNTIME + ".ide/META-INF/MANIFEST.MF")).contains(
                "Require-Bundle: " + RUNTIME + ",\n", " org.eclipse.xtext.ide,\n", " " + RUNTIME + ".ide.contentassist.antlr.internal\n",
                "Import-Package: com.google.inject,\n", " org.eclipse.lsp4j,\n", " org.eclipse.lsp4j.jsonrpc.messages\n");
        assertThat(text(result, ROOT + RUNTIME + ".ui/META-INF/MANIFEST.MF")).contains(
                "Bundle-Activator: " + RUNTIME + ".ui.internal.PeopleActivator\n", "Require-Bundle: " + RUNTIME + ",\n", " " + RUNTIME + ".ide,\n",
                " org.eclipse.xtext.ui,\n", " org.eclipse.xtext.builder,\n", " org.eclipse.xtext.ui.shared,\n", " " + RUNTIME + ".ui.quickfix\n");
    }

    @Test void buildPropertiesCompileTheMavenLayoutAndPackageWhatMwe2Writes(){
        TechnologyCompilationResult result = adapter.compile(tycho(TechnologyAdapterTestFixtures.xtextRequest(), Map.of("tychoParent", "h:p:1")));
        assertThat(text(result, ROOT + RUNTIME + "/build.properties")).isEqualTo("source.. = src/main/java/,\\\n           src/main/resources/,\\\n           src/main/xtext-gen/\n"
                + "bin.includes = model/generated/,\\\n               META-INF/,\\\n               .,\\\n               plugin.xml\n");
        assertThat(text(result, ROOT + RUNTIME + ".ide/build.properties")).contains("src/main/xtext-gen/").doesNotContain("plugin.xml");
        assertThat(text(result, ROOT + RUNTIME + ".ui/build.properties")).contains("src/main/xtext-gen/", "plugin.xml");
    }

    @Test void aGrammarFirstPackageExportsThePackagesOfItsOwnGrammarAndShipsItsEditorCustomization(){
        TechnologyCompilationResult result = adapter.compile(tycho(TechnologyAdapterTestFixtures.xtextGrammarFirstRequest(), Map.of("tychoParent", "h:p:1")));
        String runtime = result.files().stream().map(value -> value.relativePath()).filter(path -> path.endsWith("/META-INF/MANIFEST.MF") && !path.contains(".ide/") && !path.contains(".ui/")).findFirst().orElseThrow();
        assertThat(text(result, runtime)).contains(" org.example.main,\n", " org.example.parser.antlr,\n", " org.example.main.impl,\n");
        assertThat(text(result, runtime.replace("/META-INF/MANIFEST.MF", ".ui/META-INF/MANIFEST.MF"))).contains(" org.example.ui.internal,\n");
        assertThat(text(result, runtime.replace("/META-INF/MANIFEST.MF", ".ui/build.properties"))).contains("hydra/");
    }

    @Test void tychoWithoutTheHostParentIsRejectedInsteadOfShippingAStandaloneBuild(){
        assertThatThrownBy(() -> adapter.compile(tycho(TechnologyAdapterTestFixtures.xtextRequest(), Map.of())))
                .isInstanceOf(TechnologyCompilationException.class).hasMessageContaining("tychoParent");
        assertThat(text(adapter.compile(tycho(TechnologyAdapterTestFixtures.xtextRequest(), Map.of("tychoParent", "h:p:1", "tychoParentRelativePath", "../host/pom.xml"))), ROOT + "pom.xml"))
                .contains("<relativePath>../host/pom.xml</relativePath>");
    }

    @Test void theMavenPackageWarnsThatItsUiModuleNeedsTycho(){
        TechnologyCompilationResult result = adapter.compile(TechnologyAdapterTestFixtures.xtextRequest());
        assertThat(result.diagnostics()).filteredOn(value -> value.code().equals("XTEXT_UI_REQUIRES_TYCHO")).hasSize(1);
        assertThat(result.files()).extracting(value -> value.relativePath()).contains(ROOT + RUNTIME + ".ui/META-INF/MANIFEST.MF")
                .noneMatch(path -> path.endsWith("build.properties"));
    }

    private static String text(TechnologyCompilationResult result, String path){
        return result.files().stream().filter(value -> value.relativePath().equals(path)).map(value -> new String(value.content(), StandardCharsets.UTF_8))
                .findFirst().orElseThrow(() -> new AssertionError("missing " + path + " in " + result.files().stream().map(value -> value.relativePath()).toList()));
    }

    private static TechnologyCompilationRequest tycho(TechnologyCompilationRequest source, Map<String, String> settings){
        IntegrationProfileModel p = source.profile();
        IntegrationTarget t = p.target();
        IntegrationTarget target = new IntegrationTarget(t.technology(), t.kind(), t.technologyVersion(), t.projectId(), t.moduleName(), IntegrationBuildSystem.TYCHO,
                t.sourceRoots(), t.configurationFiles(), t.endpoint(), settings);
        IntegrationProfileModel profile = new IntegrationProfileModel(p.schemaVersion(), p.id(), p.metamodelId(), p.semanticScopeId(), p.name(), target, p.deliveryMode(),
                p.features(), p.artifacts(), p.mappings(), p.injectionPolicy(), p.requiredCapabilities(), p.annotations());
        return new TechnologyCompilationRequest(profile, source.artifacts(), source.generatorVersion(), source.options());
    }
}
