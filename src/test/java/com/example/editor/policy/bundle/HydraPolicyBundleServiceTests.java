package com.example.editor.policy.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorChannel;
import com.example.editor.policy.HydraPolicyEvaluationReport;
import com.example.editor.policy.HydraPolicyKind;
import com.example.editor.policy.HydraPolicyRule;
import com.example.editor.policy.HydraPolicyService;
import com.example.editor.policy.HydraPolicySeverity;
import tools.jackson.databind.json.JsonMapper;

class HydraPolicyBundleServiceTests {
    @TempDir Path temp;
    @Test void keepsVersionsImmutableAndActivatesOnlySimulatedEvidence() {
        var mapper=JsonMapper.builder().findAndAddModules().build();var policies=mock(HydraPolicyService.class);
        when(policies.evaluate(any(),anyList(),anyList())).thenReturn(new HydraPolicyEvaluationReport("evaluation","av-1",true,List.of(),List.of(),"fingerprint",Instant.now()));
        var properties=new HydraPolicyBundleProperties();properties.setStorageRoot(temp);var service=new HydraPolicyBundleService(policies,properties,mapper);
        var request=new HydraPolicyBundleRequest("standard","1.0.0",UpdateSiteMirrorChannel.INTERNAL,List.of(new HydraPolicyRule("license",HydraPolicyKind.FORBID_LICENSE,"","UNKNOWN",HydraPolicySeverity.ERROR,true,Map.of())),List.of(),Map.of());
        HydraPolicyBundle bundle=service.save(request);
        assertThatThrownBy(()->service.save(new HydraPolicyBundleRequest("standard","1.0.0",UpdateSiteMirrorChannel.INTERNAL,List.of(),List.of(),Map.of()))).isInstanceOf(IllegalStateException.class);
        HydraPolicyBundleSimulationReport simulation=service.simulate("standard","1.0.0",new HydraPolicyBundleSimulationRequest(List.of("av-1"),true));
        HydraPolicyBundle active=service.activate("standard","1.0.0",new HydraPolicyBundleActivationRequest(simulation.reportId()));
        assertThat(bundle.status()).isEqualTo(HydraPolicyBundleStatus.DRAFT);assertThat(active.status()).isEqualTo(HydraPolicyBundleStatus.ACTIVE);
    }
}
