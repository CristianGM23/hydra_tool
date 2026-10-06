package com.example.editor.policy.bundle;

import java.util.List;

/** AV reports evaluated before a BD bundle can be activated. */
public record HydraPolicyBundleSimulationRequest(List<String> provenanceReportIds, boolean includeWarnings) {
    public HydraPolicyBundleSimulationRequest { provenanceReportIds=provenanceReportIds==null?List.of():provenanceReportIds.stream().filter(value->value!=null&&!value.isBlank()).distinct().toList(); }
}
