package com.example.editor.remote.auth;

import java.util.List;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** Public OAuth setup status. Secrets are represented only as a boolean capability flag. */
public record RemoteOAuthProviderSetupDescriptor(RemoteStorageProviderType provider,String clientId,String tenant,List<String> scopes,
        boolean deviceFlowPreferred,boolean uiOverride,boolean oauthReady,boolean confidentialCredentialConfigured,
        boolean uiConfigurationAllowed,String callbackUrl) {
    public RemoteOAuthProviderSetupDescriptor { scopes=scopes==null?List.of():List.copyOf(scopes); }
}
