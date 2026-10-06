package com.example.editor.policy.bundle;

import java.util.List;
import java.util.Map;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorChannel;
import com.example.editor.policy.HydraPolicyRule;
import com.example.editor.policy.HydraRevocationEntry;

/** Draft content for a versioned BD policy bundle. */
public record HydraPolicyBundleRequest(String bundleId, String version, UpdateSiteMirrorChannel channel,
        List<HydraPolicyRule> rules, List<HydraRevocationEntry> revocations, Map<String,String> annotations) {
    public HydraPolicyBundleRequest { rules=rules==null?List.of():List.copyOf(rules);revocations=revocations==null?List.of():List.copyOf(revocations);annotations=annotations==null?Map.of():Map.copyOf(annotations); }
}
