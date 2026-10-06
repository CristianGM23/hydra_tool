package com.example.editor.remote.storage;

/** Descriptor seguro de un perfil remoto; nunca expone credenciales. */
public record RemoteStorageProfileDescriptor(String id,String displayName,RemoteStorageProviderType provider,boolean enabled,boolean configured,
        String repository,String branch,String rootPath,long maximumArtifactBytes) { }
