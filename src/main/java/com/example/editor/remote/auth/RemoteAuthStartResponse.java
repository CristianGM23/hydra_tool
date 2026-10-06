package com.example.editor.remote.auth;

import java.time.Instant;

/** Inicio de autorización seguro para callback PKCE o Device Authorization Grant. */
public record RemoteAuthStartResponse(String profileId,String connectionId,String state,String authorizationUrl,String userCode,
        boolean deviceFlow,int pollIntervalSeconds,Instant expiresAt) { }
