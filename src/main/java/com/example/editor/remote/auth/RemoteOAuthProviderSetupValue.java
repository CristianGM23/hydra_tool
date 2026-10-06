package com.example.editor.remote.auth;

import java.util.List;

/** Non-secret OAuth application coordinates that may be configured from the local HYDRA UI. */
public record RemoteOAuthProviderSetupValue(String clientId,String tenant,List<String> scopes,boolean deviceFlowPreferred) {
    public RemoteOAuthProviderSetupValue {
        clientId=clientId==null?"":clientId.trim();
        tenant=tenant==null?"":tenant.trim();
        scopes=scopes==null?List.of():scopes.stream().map(value->value==null?"":value.trim()).filter(value->!value.isBlank()).distinct().limit(32).toList();
    }
}
