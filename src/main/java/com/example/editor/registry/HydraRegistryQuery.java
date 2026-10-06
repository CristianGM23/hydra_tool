package com.example.editor.registry;

import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorChannel;

/** Search filters for the federable distribution catalog. */
public record HydraRegistryQuery(String text, String metamodelId, String capability,
        UpdateSiteMirrorChannel channel, boolean validOnly) { }
