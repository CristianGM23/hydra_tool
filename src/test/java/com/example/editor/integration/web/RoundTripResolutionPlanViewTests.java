package com.example.editor.integration.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.roundtrip.RoundTripSyncPreview;
import com.example.editor.integration.roundtrip.resolution.RoundTripResolutionAction;
import com.example.editor.integration.roundtrip.resolution.RoundTripResolutionActionKind;
import com.example.editor.integration.roundtrip.resolution.RoundTripResolutionPlan;

class RoundTripResolutionPlanViewTests {
    @Test
    void omitsGeneratedBytesFromTheBrowserProjection(){
        RoundTripResolutionAction action = new RoundTripResolutionAction("src/generated.ts", RoundTripResolutionActionKind.WRITE_HYDRA,
                "text/typescript", "secret generated body".getBytes(StandardCharsets.UTF_8), "people@src/generated.ts");
        RoundTripResolutionPlan plan = new RoundTripResolutionPlan("sync-1", IntegrationTechnology.LANGIUM, "profile", Path.of("target"),
                new RoundTripSyncPreview(IntegrationTechnology.LANGIUM, "profile", List.of(), List.of(), List.of("src/generated.ts"), Map.of()),
                List.of(action), List.of(), true, Map.of("mode", "advanced"));

        RoundTripResolutionPlanView view = RoundTripResolutionPlanView.from(plan);

        assertThat(view.actions()).containsExactly(new RoundTripResolutionActionView("src/generated.ts", RoundTripResolutionActionKind.WRITE_HYDRA, "text/typescript", "people@src/generated.ts"));
        assertThat(view.canApply()).isTrue();
        assertThat(view.targetRoot()).isEqualTo(plan.targetRoot().toString());
    }
}
