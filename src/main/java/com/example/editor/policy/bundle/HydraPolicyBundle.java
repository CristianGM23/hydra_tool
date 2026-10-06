package com.example.editor.policy.bundle;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorChannel;
import com.example.editor.policy.HydraPolicyRule;
import com.example.editor.policy.HydraRevocationEntry;

/** Immutable, attested set of declarative policies for one channel. */
public record HydraPolicyBundle(String bundleId, String version, UpdateSiteMirrorChannel channel,
        HydraPolicyBundleStatus status, List<HydraPolicyRule> rules, List<HydraRevocationEntry> revocations,
        String attestationSha256, String revocationReason, Map<String,String> annotations,
        Instant createdAt, Instant activatedAt, Instant revokedAt) {
    public HydraPolicyBundle {
        bundleId=bundleId==null?"":bundleId.trim(); version=version==null?"":version.trim();
        channel=channel==null?UpdateSiteMirrorChannel.INTERNAL:channel;
        status=status==null?HydraPolicyBundleStatus.DRAFT:status;
        rules=rules==null?List.of():List.copyOf(rules); revocations=revocations==null?List.of():List.copyOf(revocations);
        attestationSha256=attestationSha256==null?"":attestationSha256.trim(); revocationReason=revocationReason==null?"":revocationReason.trim();
        annotations=annotations==null?Map.of():Map.copyOf(annotations); createdAt=createdAt==null?Instant.now():createdAt;
    }
}
