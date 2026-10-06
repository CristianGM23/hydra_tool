package com.example.editor.integration.bridge;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;import java.util.List;import java.util.Map;import java.util.Set;import org.junit.jupiter.api.Test;
import com.example.editor.gamification.feedback.*;import com.example.editor.integration.IntegrationCapability;import com.example.editor.integration.IntegrationTechnology;
class HostFeedbackPresentationServiceTests {
 @Test void degradesHostFailureWithoutBreakingCommittedRuntime(){var bridge=new Bridge(true);assertThat(HostFeedbackPresentationService.present(bridge,feedback())).isFalse();}
 @Test void mapsNeutralFeedbackToHostNotification(){var bridge=new Bridge(false);assertThat(HostFeedbackPresentationService.present(bridge,feedback())).isTrue();assertThat(bridge.notification.kind()).isEqualTo("SUCCESS");assertThat(bridge.notification.metadata()).containsEntry("pointsDelta","5");}
 private static UserFeedbackEnvelope feedback(){return new UserFeedbackEnvelope("1.0","feedback:1","scope","actor",UserFeedbackKind.POINTS_AWARDED,UserFeedbackSeverity.SUCCESS,"+5 Quality","Earned","","Continue",5,"points:quality",5,10,"","","","","event:1","c",Instant.now(),UserFeedbackPresentationPolicy.defaults(),List.of(),Map.of());}
 private static final class Bridge implements HostUiBridge{private final boolean fail;private HostNotification notification;Bridge(boolean fail){this.fail=fail;}public IntegrationTechnology technology(){return IntegrationTechnology.VSCODE_EXTENSION;}public Set<IntegrationCapability> capabilities(){return Set.of(IntegrationCapability.UI_CONTRIBUTION);}public void notify(HostNotification value){if(fail)throw new IllegalStateException("host down");notification=value;}public void openView(String id){}}
}
