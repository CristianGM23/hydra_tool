package com.example.editor.recovery.continuity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.recovery.HydraRecoveryBackupManifest;
import com.example.editor.recovery.HydraRecoveryDrillReport;
import com.example.editor.recovery.HydraRecoveryService;
import tools.jackson.databind.json.JsonMapper;

class HydraContinuityServiceTests {
    @TempDir Path temp;
    @Test void measuresObjectivesAndRotatesBackups() {
        var recovery=mock(HydraRecoveryService.class);var backup=new HydraRecoveryBackupManifest("backup",Instant.now(),temp.resolve("backup.zip").toString(),0,List.of(),"sha",true);
        when(recovery.backups()).thenReturn(List.of());when(recovery.backup()).thenReturn(backup);when(recovery.drill(any())).thenReturn(new HydraRecoveryDrillReport("drill","backup",true,temp.resolve("restore").toString(),0,List.of(),Instant.now()));when(recovery.retain(3)).thenReturn(List.of("old"));
        var properties=new HydraContinuityProperties();properties.setStorageRoot(temp);var service=new HydraContinuityService(recovery,properties,JsonMapper.builder().findAndAddModules().build());
        service.save(new HydraContinuityPlan("standard","Standard",true,60,60,3,30,false,Map.of(),null));
        HydraContinuityRunReport report=service.run(new HydraContinuityRunRequest("standard",true));
        assertThat(report.passed()).isTrue();assertThat(report.rpoSatisfied()).isTrue();assertThat(report.rtoSatisfied()).isTrue();assertThat(report.rotatedBackupIds()).containsExactly("old");
    }
}
