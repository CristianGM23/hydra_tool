package com.example.editor.rc1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.runtime.CanonicalTransformationRuntime;
import com.example.editor.transformation.runtime.CompiledTransformationPlan;
import com.example.editor.transformation.runtime.TransformationPlanCompiler;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;

/** Cualificación de escala RC1 sobre el runtime canónico, no un microbenchmark sintético. */
class Rc1TransformationScaleQualificationTests {
    private final ExpressionTypeChecker checker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
    private final TransformationPlanCompiler compiler = new TransformationPlanCompiler(new TransformationDefinitionChecker(checker));
    private final CanonicalTransformationRuntime runtime = new CanonicalTransformationRuntime(new ExpressionEvaluator(checker));

    @Test @Timeout(30) void transformsOneHundredElements() { qualify(100); }
    @Test @Timeout(30) void transformsOneThousandElements() { qualify(1_000); }
    @Test @Timeout(60) void transformsTenThousandElements() { qualify(10_000); }
    @Test @Timeout(180) void transformsFiftyThousandElementsWhenFullRcScaleIsEnabled() {
        assumeTrue(Boolean.getBoolean("hydra.rc1.scale.full")); qualify(50_000);
    }

    private void qualify(int count) {
        MetaModel metamodel = ExpressionTestFixtures.metamodel();
        CompiledTransformationPlan plan = compiler.compile(document(), metamodel, metamodel);
        long start = System.nanoTime();
        var result = runtime.execute(plan, metamodel, metamodel, people(count));
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(result.targetModel().elements()).hasSize(count);
        assertThat(result.trace().links()).hasSize(count);
        assertThat(result.trace().bindingTraces()).hasSize(count);
        System.out.printf("RC1_SCALE elements=%d millis=%d%n", count, millis);
    }

    private CanonicalModelDocument people(int count) {
        List<CanonicalModelElement> elements = new ArrayList<>(count); List<String> roots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String id="rc1:person:"+index; roots.add(id);
            elements.add(new CanonicalModelElement(id, ExpressionTestFixtures.PERSON,
                    Map.of(ExpressionTestFixtures.AGE,index,ExpressionTestFixtures.NAME,"Person "+index),Map.of()));
        }
        return new CanonicalModelDocument("1.0","rc1-scale-"+count,ExpressionTestFixtures.METAMODEL_ID,
                "rc1-scale-etag","RC1 scale",roots,elements,List.of(),Map.of());
    }

    private TransformationDefinitionDocument document() {
        TransformationRule rule=new TransformationRule("rule:rc1-person","RC1 Person",true,"source",ExpressionTestFixtures.PERSON,"person",null,
                List.of(new TransformationTargetPattern("target:person","target",ExpressionTestFixtures.PERSON,"targetPerson",Map.of())),
                List.of(new TransformationBinding("binding:name","targetPerson",ExpressionTestFixtures.NAME,TransformationBindingKind.DIRECT,
                        ExpressionTestFixtures.NAME,null,null,null,Map.of())),Map.of());
        return new TransformationDefinitionDocument("1.1","rc1-scale-transformation","RC1 Scale",null,
                List.of(new TransformationEndpoint("source",TransformationEndpointRole.SOURCE,ExpressionTestFixtures.METAMODEL_ID,"fingerprint","source",true,Map.of())),
                List.of(new TransformationEndpoint("target",TransformationEndpointRole.TARGET,ExpressionTestFixtures.METAMODEL_ID,"fingerprint","target",true,Map.of())),
                List.of(rule),List.of(),null,null,null,Map.of());
    }
}
