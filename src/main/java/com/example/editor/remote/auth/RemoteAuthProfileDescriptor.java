package com.example.editor.remote.auth;

import java.time.Instant;
import java.util.List;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** Estado seguro de autenticación de un perfil remoto configurado. */
public record RemoteAuthProfileDescriptor(String profileId,String displayName,RemoteStorageProviderType provider,String connectionId,
        boolean oauthConfigured,boolean connected,String accountLabel,List<String> scopes,Instant expiresAt,boolean persistent,boolean vaultReady,String protectionMode) { }
