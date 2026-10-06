package com.example.editor.remote.auth;

import java.time.Instant;
import java.util.List;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** Vista segura de una conexión externa. No incluye access ni refresh tokens. */
public record RemoteAuthConnectionDescriptor(String connectionId,RemoteStorageProviderType provider,String accountId,String accountLabel,
        List<String> scopes,Instant expiresAt,boolean refreshable,boolean persistent,String protectionMode) { }
