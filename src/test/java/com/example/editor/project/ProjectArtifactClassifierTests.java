package com.example.editor.project;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.example.editor.project.storage.ProjectArtifactClassifier;

class ProjectArtifactClassifierTests {
    private final ProjectArtifactClassifier classifier=new ProjectArtifactClassifier();
    private final ProjectLayout layout=ProjectLayout.standard();
    @Test void classifiesEveryCanonicalFamily() {
        assertThat(classifier.classify("metamodels/people.metamodel.json",layout)).isEqualTo(ArtifactKind.METAMODEL);
        assertThat(classifier.classify("behavior/validations/people.validations.json",layout)).isEqualTo(ArtifactKind.VALIDATION_MODEL);
        assertThat(classifier.classify("events/definitions/people.event-definitions.json",layout)).isEqualTo(ArtifactKind.EVENT_DEFINITION_MODEL);
        assertThat(classifier.classify("events/definitions/people.event-architecture-diagram.json",layout)).isEqualTo(ArtifactKind.EVENT_ARCHITECTURE_DIAGRAM);
        assertThat(classifier.classify("events/flows/people.event-flows.json",layout)).isEqualTo(ArtifactKind.EVENT_FLOW_MODEL);
        assertThat(classifier.classify("events/flows/people.event-flow-diagram.json",layout)).isEqualTo(ArtifactKind.EVENT_FLOW_DIAGRAM);
        assertThat(classifier.classify("events/flows/people.event-flow-runtime.json",layout)).isEqualTo(ArtifactKind.EVENT_FLOW_RUNTIME);
        assertThat(classifier.classify("gamification/people.gamification-definitions.json",layout)).isEqualTo(ArtifactKind.GAMIFICATION_DEFINITION_MODEL);
        assertThat(classifier.classify("gamification/people.gamification-bindings.json",layout)).isEqualTo(ArtifactKind.GAMIFICATION_BINDING_MODEL);
        assertThat(classifier.classify("gamification/people.gamification-diagram.json",layout)).isEqualTo(ArtifactKind.GAMIFICATION_DIAGRAM);
        assertThat(classifier.classify("editors/people.editor.json",layout)).isEqualTo(ArtifactKind.EDITOR_DEFINITION);
        assertThat(classifier.classify("models/semantic/people.editor-session.json",layout)).isEqualTo(ArtifactKind.EDITOR_MODEL_SESSION);
        assertThat(classifier.classify("models/diagrams/people.editor-session-diagram.json",layout)).isEqualTo(ArtifactKind.EDITOR_SESSION_DIAGRAM);
        assertThat(classifier.classify("integrations/people.integration.json",layout)).isEqualTo(ArtifactKind.INTEGRATION_PROFILE);
    }
    @Test void generatedFilesHaveGeneratedOwnership() {
        assertThat(classifier.ownership("generated/drawio/people.drawio",layout)).isEqualTo(FileOwnership.GENERATED);
        assertThat(classifier.classify("generated/drawio/people.drawio",layout)).isEqualTo(ArtifactKind.GENERATED_DRAWIO);
    }
    @Test void internalFilesAreOwnedByTheTool() { assertThat(classifier.ownership(".modeling/project-index.json",layout)).isEqualTo(FileOwnership.OWNED); }
}
