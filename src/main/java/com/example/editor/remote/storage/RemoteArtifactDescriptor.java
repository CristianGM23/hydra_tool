package com.example.editor.remote.storage;

/** Resultado neutral de una lectura o escritura remota. */
public record RemoteArtifactDescriptor(String profileId,RemoteStorageProviderType provider,RemoteArtifactScope scope,String path,
        String version,long size,String contentType,String webUrl,boolean created) { }
