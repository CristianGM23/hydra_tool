package com.example.editor.metamodel.web;

import java.nio.file.Path;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Test-only helper: points every persistent directory of the full HYDRA Spring context at a temporary root so that
 * HTTP integration tests never touch the repository's {@code data/} folder.
 */
public final class MetamodelHttpTestEnvironment {
    private MetamodelHttpTestEnvironment() { }

    public static void register(DynamicPropertyRegistry registry, Path root) {
        String[][] directories = {
            {"dsl.storage.metamodels", "metamodels"}, {"dsl.storage.models", "models"}, {"dsl.storage.notations", "notations"},
            {"dsl.storage.validations", "validations"}, {"dsl.storage.quick-fixes", "quickfixes"},
            {"dsl.storage.generators", "generators"}, {"dsl.storage.assets", "assets"}, {"dsl.storage.exports", "exports"},
            {"dsl.metamodel.validation.receipt-root", "metamodel-validation"},
            {"dsl.imports.metamodel.staging-root", "metamodel-import-staging"},
            {"dsl.events.directory", "events"}, {"dsl.events.flows.directory", "event-flows"},
            {"dsl.editors.directory", "editors"}, {"dsl.editors.runtime.sessions.directory", "editor-sessions"},
            {"dsl.integrations.directory", "integrations"}, {"dsl.integrations.validation.workspace-root", "integration-validation"},
            {"dsl.integrations.injection.allowed-root", "injection-targets"},
            {"dsl.gamification.directory", "gamification"}, {"dsl.projects.local.registry-directory", "projects"},
            {"hydra.audit.storage-root", "audit-log"}, {"dsl.grammars.directory", "grammars"},
            {"dsl.grammars.compilation-directory", "grammar-compilations"}, {"dsl.grammars.activation-directory", "grammar-activations"},
            {"dsl.textual-models.definition-directory", "textual-editor-definitions"},
            {"dsl.textual-models.session-directory", "textual-model-sessions"},
            {"dsl.textual-models.syntax-style-directory", "textual-syntax-styles"},
            {"dsl.capability-transactions.storage-root", "capability-transactions"},
            {"hydra.artifact-transactions.directory", "artifact-transactions"},
            {"dsl.imports.update-site.storage-root", "update-site-imports"},
            {"hydra.security.external-process.temporary-root", "external-process-workspaces"}
        };
        for (String[] entry : directories) {
            registry.add(entry[0], () -> root.resolve(entry[1]).toString());
        }
        registry.add("dsl.storage.create-directories", () -> "true");
        registry.add("hydra.instance-lock.enabled", () -> "false");
    }
}
