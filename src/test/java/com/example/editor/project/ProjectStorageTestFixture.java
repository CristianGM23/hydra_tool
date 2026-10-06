package com.example.editor.project;

import java.nio.file.Path;
import java.util.function.Consumer;
import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.definition.runtime.session.EditorSessionProperties;
import com.example.editor.event.EventModelProperties;
import com.example.editor.integration.IntegrationProfileProperties;
import com.example.editor.project.storage.*;
import tools.jackson.databind.json.JsonMapper;

public final class ProjectStorageTestFixture {
    private ProjectStorageTestFixture() { }

    public static LocalDirectoryProjectStorage storage(Path temporary) {
        return storage(temporary,properties -> { });
    }

    public static LocalDirectoryProjectStorage storage(Path temporary,Consumer<LocalProjectProperties> configuration) {
        LocalProjectProperties properties=new LocalProjectProperties();
        properties.setRegistryDirectory(temporary.resolve("registry"));
        configuration.accept(properties);
        JsonMapper mapper=JsonMapper.builder().findAndAddModules().build();
        ProjectFileOperations files=new ProjectFileOperations();
        SafeProjectPathResolver paths=new SafeProjectPathResolver(properties);
        ProjectHashService hashes=new ProjectHashService();
        ProjectArtifactClassifier classifier=new ProjectArtifactClassifier();
        ProjectMediaTypeResolver media=new ProjectMediaTypeResolver();
        ProjectDescriptorStore descriptors=new ProjectDescriptorStore(mapper,files);
        ProjectIndexService index=new ProjectIndexService(paths,classifier,media,hashes,properties,files,mapper);
        ProjectRegistryStore registry=new ProjectRegistryStore(properties,mapper,files);
        ProjectZipService zip=new ProjectZipService(properties,files);
        ProjectChangeTracker changes=new ProjectChangeTracker(index,properties);
        StorageProperties storage=new StorageProperties(temporary.resolve("configured/metamodels"),temporary.resolve("configured/models"),temporary.resolve("configured/notations"),temporary.resolve("configured/validations"),temporary.resolve("configured/quickfixes"),temporary.resolve("configured/generators"),temporary.resolve("configured/assets"),temporary.resolve("configured/exports"),true);
        EditorDefinitionProperties editors=new EditorDefinitionProperties(); editors.setDirectory(temporary.resolve("configured/editors"));
        EditorSessionProperties sessions=new EditorSessionProperties(); sessions.setDirectory(temporary.resolve("configured/editor-sessions"));
        EventModelProperties events=new EventModelProperties(); events.setDirectory(temporary.resolve("configured/events"));
        IntegrationProfileProperties integrations=new IntegrationProfileProperties(); integrations.setDirectory(temporary.resolve("configured/integrations"));
        ProjectWorkspaceContributionRegistry workspaceRegistry=new ProjectWorkspaceContributionRegistry(java.util.List.of(
                new CoreProjectWorkspaceContributor(storage),new EditorProjectWorkspaceContributor(editors,sessions),
                new EventProjectWorkspaceContributor(events),new IntegrationProjectWorkspaceContributor(integrations)));
        ProjectWorkspaceBridge bridge=new ProjectWorkspaceBridge(workspaceRegistry,classifier,files,properties);
        return new LocalDirectoryProjectStorage(properties,paths,descriptors,index,classifier,media,hashes,files,registry,zip,changes,bridge,mapper);
    }
}
