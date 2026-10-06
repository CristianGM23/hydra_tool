package com.example.editor.integration.adapter.eclipse;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

class EclipseModelingDistributionTechnologyAdapterTests {
    private final EclipseModelingDistributionTechnologyAdapter adapter = new EclipseModelingDistributionTechnologyAdapter(
            TechnologyAdapterTestFixtures.MAPPER, TechnologyAdapterTestFixtures.ecoreAdapter());

    @Test void generatesFeatureBasedEclipseModelingDistribution(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.eclipseDistributionRequest());
        assertThat(paths(result)).anyMatch(p->p.endsWith("target-platform/eclipse-modeling.target"))
                .anyMatch(p->p.endsWith("runtime-plugin/META-INF/MANIFEST.MF"))
                .anyMatch(p->p.endsWith("feature/feature.xml"))
                .anyMatch(p->p.endsWith("product/hydra-modeling.product"));
        assertThat(text(result,"eclipse-modeling.target"))
                .contains("org.eclipse.emf.ecore","org.eclipse.emf.edit.ui","org.eclipse.gmf.runtime.diagram.ui","org.eclipse.sirius.diagram.ui")
                .doesNotContain("org.eclipse.xtext","org.eclipse.xtend.lib","org.eclipse.xtend.ide");
        assertThat(text(result,"README.md")).contains("Sirius Desktop runtime/UI").doesNotContain("Xtext runtime/UI","Xtend");
    }

    @Test void addsXtextOnlyWhenAnXtextGrammarIsEmbedded(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.eclipseXtextDistributionRequest());
        assertThat(text(result,"eclipse-modeling.target")).contains("org.eclipse.xtext","org.eclipse.xtext.ui")
                .doesNotContain("org.eclipse.xtend.lib","org.eclipse.xtend.ide");
        assertThat(text(result,"runtime-plugin/META-INF/MANIFEST.MF")).contains("org.eclipse.xtext","org.eclipse.xtext.ui");
        assertThat(text(result,"feature/feature.xml")).contains("org.eclipse.xtext","org.eclipse.xtext.ui");
        assertThat(text(result,"README.md")).contains("Xtext runtime/UI").doesNotContain("Xtend");
    }

    @Test void scopesActivationCommandsToGeneratedPluginAndExportsPortableRuntimeBoundary(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.eclipseDistributionRequest());
        String plugin=text(result,"runtime-plugin/plugin.xml");
        assertThat(plugin).contains("com.generated.people.hydra.runtime.runtime.toggle").doesNotContain("id=\"hydra.runtime.toggle\"");
        assertThat(paths(result)).anyMatch(p->p.endsWith("HydraRuntimeActivation.java")).anyMatch(p->p.endsWith("HydraPortableRuntimeBridge.java"));
        assertThat(text(result,"HydraRuntimeActivation.java")).contains("Preferences.userNodeForPackage","getBoolean(KEY, false)","isEnabledFor");
    }

    @Test void embedsProjectedEcoreAndCanonicalActivationDescriptor(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.eclipseDistributionRequest());
        assertThat(paths(result)).anyMatch(p->p.endsWith("model/people.ecore")).anyMatch(p->p.endsWith("hydra/runtime-activation.json"));
        assertThat(text(result,"runtime-activation.json")).contains("\"metamodelId\": \"people\"","\"activationMode\": \"DISABLED\"");
    }

    @Test void keepsGenerationDeterministic(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.eclipseDistributionRequest()).files()).usingRecursiveComparison().isEqualTo(adapter.compile(TechnologyAdapterTestFixtures.eclipseDistributionRequest()).files());}


    @Test void embedsNeutralTextualCustomizationInEclipseDistribution(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.eclipseCustomizedRequest());
        assertThat(paths(result)).anyMatch(p->p.endsWith("HydraTextualEditorCustomization.java")).anyMatch(p->p.endsWith("hydra/textual-editor-customization.json"));
        assertThat(text(result,"HydraTextualEditorCustomization.java")).contains("FONT_FAMILY=\"Consolas, monospace\"","FONT_SIZE=17","LINE_HEIGHT=25","Persona","Elemento persona del lenguaje.");
    }

    @Test void generatesTransactionalEmfLifecycleWithEpochAwareUndoRedo(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.eclipseDistributionRequest());
        assertThat(paths(result)).anyMatch(p->p.endsWith("HydraSemanticRuntime.java")).anyMatch(p->p.endsWith("HydraEclipseCommandLifecycle.java"));
        assertThat(text(result,"HydraRuntimeActivation.java")).contains("SEMANTIC_SCOPE_ID","epoch()","EPOCH_KEY","isEnabledFor");
        assertThat(text(result,"HydraPortableRuntimeBridge.java")).contains("public final class HydraPortableRuntimeBridge","validateHost","synchronize","checkpointBaseline","restoreHost","ECLIPSE_QUICK_FIX","ECLIPSE_OPERATION").doesNotContain("public interface HydraPortableRuntimeBridge");
        assertThat(text(result,"HydraEclipseCommandLifecycle.java"))
                .contains("CommandStack","execute(","undo(","redo(","ECLIPSE_COMMAND","restoreOrRebaseline","activationEpoch()",
                        "getUndoCommand()==command","bridge.restoreRuntime(before)","bridge.restoreRuntime(current)");
        assertThat(text(result,"README.md")).contains("Events → EventFlow → Gamification","undo/redo restores full HYDRA checkpoints","activation epoch");
    }

    @Test void acceptsGrammarAsAutonomousSemanticRootWithoutSyntheticEcore(){
        var request=TechnologyAdapterTestFixtures.eclipseGrammarFirstRequest();
        assertThat(adapter.semanticRootKinds()).contains(com.example.editor.integration.IntegrationArtifactKind.GRAMMAR);
        var result=adapter.compile(request);
        assertThat(paths(result)).noneMatch(p->p.endsWith(".ecore"));
        assertThat(paths(result)).anyMatch(p->p.endsWith("hydra/grammar.json"));
        assertThat(text(result,"HydraRuntimeActivation.java")).contains("grammar.main-language");
        assertThat(text(result,"README.md")).contains("semantic context `grammar.main-language`");
    }
    private java.util.List<String> paths(TechnologyCompilationResult result){return result.files().stream().map(f->f.relativePath()).toList();}
    @Test void embedsExecutableVisualCustomizationRuntimeInEclipseDistribution(){var result=adapter.compile(TechnologyAdapterTestFixtures.eclipseDistributionRequest());assertThat(paths(result)).anyMatch(v->v.endsWith("HydraVisualFullStack.java")).anyMatch(v->v.endsWith("hydra/visual-editor-definition.json")).anyMatch(v->v.endsWith("hydra/visual-full-stack.json"));assertThat(text(result,"HydraVisualFullStack.java")).contains("resolveNodeAppearance","resolveEdgeAppearance","applyNode","applyEdge","applyNodeAppearance","applyEdgeAppearance");}

    private String text(TechnologyCompilationResult result,String suffix){return result.files().stream().filter(f->f.relativePath().endsWith(suffix)).map(f->new String(f.content(),StandardCharsets.UTF_8)).findFirst().orElseThrow();}
}
