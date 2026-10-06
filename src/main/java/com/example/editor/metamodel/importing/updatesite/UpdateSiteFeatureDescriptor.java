package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Feature Eclipse recuperado desde feature.xml o content.xml. */
public record UpdateSiteFeatureDescriptor(String id, String version, String name, String sourcePath,
        List<String> pluginIds, List<UpdateSiteDependency> dependencies) {
    public UpdateSiteFeatureDescriptor {
        id = id == null ? "" : id;
        version = version == null ? "" : version;
        name = name == null || name.isBlank() ? id : name;
        sourcePath = sourcePath == null ? "" : sourcePath;
        pluginIds = pluginIds == null ? List.of() : List.copyOf(pluginIds);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
