package com.example.editor.registry.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.registry.HydraDistributionRegistryService;
import com.example.editor.registry.HydraRegistryIndex;
import com.example.editor.persistence.support.HydraPersistenceSupport;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

class HydraCatalogFederationServiceTests {
    @TempDir Path temp;
    @Test void previewsAnOfflinePinnedCatalogWithoutApplyingIt() {
        var mapper=JsonMapper.builder().findAndAddModules().build();
        var registry=mock(HydraDistributionRegistryService.class);when(registry.entries()).thenReturn(List.of());
        var properties=new HydraCatalogFederationProperties();properties.setStorageRoot(temp);
        var service=new HydraCatalogFederationService(registry,properties,mapper);
        service.saveSource(new HydraFederatedCatalogSource("peer","Peer",HydraCatalogTrustLevel.REVIEW_REQUIRED,"",true,Map.of(),null));
        String checksum=HydraPersistenceSupport.fingerprint(mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS),Map.of("schemaVersion","1.0","entries",List.of()));
        HydraRegistryIndex index=new HydraRegistryIndex("1.0",Instant.now(),List.of(),checksum);
        HydraCatalogSyncReport report=service.synchronize(new HydraCatalogSyncRequest("peer",index,false,false));
        assertThat(report.applied()).isFalse();assertThat(report.diagnostics()).isEmpty();assertThat(report.indexSha256()).isEqualTo(checksum);
    }
}
