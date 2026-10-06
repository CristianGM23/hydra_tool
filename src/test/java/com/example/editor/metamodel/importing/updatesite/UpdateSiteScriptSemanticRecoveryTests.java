package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteScriptSemanticRecoveryTests {
    private final UpdateSiteScriptSemanticRecovery recovery = new UpdateSiteScriptSemanticRecovery();

    @Test
    void extractsValidationAndOperationSignaturesWithoutExecutingScripts() {
        var evl = recovery.recover("validation/project.evl", UpdateSiteArtifactKind.EVL,
                "context Task { constraint NameRequired { check: self.name.isDefined() } }".getBytes(StandardCharsets.UTF_8));
        var ocl = recovery.recover("validation/project.ocl", UpdateSiteArtifactKind.OCL,
                "context Task inv HasName: not self.name.oclIsUndefined()".getBytes(StandardCharsets.UTF_8));
        var eol = recovery.recover("operations/project.eol", UpdateSiteArtifactKind.EOL,
                "operation Task rename(value : String) { self.name = value; }".getBytes(StandardCharsets.UTF_8));

        assertThat(evl).extracting(UpdateSiteSemanticElementDraft::kind).contains(UpdateSiteSemanticKind.VALIDATION);
        assertThat(ocl).extracting(UpdateSiteSemanticElementDraft::kind).contains(UpdateSiteSemanticKind.VALIDATION);
        assertThat(eol).extracting(UpdateSiteSemanticElementDraft::kind).contains(UpdateSiteSemanticKind.COMMAND);
        assertThat(eol.getFirst().warnings()).anyMatch(value -> value.contains("review"));
    }
}
