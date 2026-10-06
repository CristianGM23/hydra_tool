package com.example.editor.remote.auth;

import java.util.List;

/** Guided OAuth setup request. It deliberately contains no client secret. */
public record RemoteOAuthProviderSetupRequest(String clientId,String tenant,List<String> scopes,Boolean deviceFlowPreferred) { }
