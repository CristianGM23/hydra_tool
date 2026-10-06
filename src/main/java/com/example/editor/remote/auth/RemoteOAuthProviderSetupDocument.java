package com.example.editor.remote.auth;

import java.util.Map;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** Versioned non-secret document for guided OAuth provider setup. */
public record RemoteOAuthProviderSetupDocument(String schemaVersion,Map<RemoteStorageProviderType,RemoteOAuthProviderSetupValue> providers) {
    public RemoteOAuthProviderSetupDocument {
        schemaVersion=schemaVersion==null||schemaVersion.isBlank()?"1.0":schemaVersion.trim();
        providers=providers==null?Map.of():Map.copyOf(providers);
    }
}
