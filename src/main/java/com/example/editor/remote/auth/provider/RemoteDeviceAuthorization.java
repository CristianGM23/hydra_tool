package com.example.editor.remote.auth.provider;

import java.net.URI;
import java.time.Instant;

/** Datos efímeros del Device Authorization Grant que nunca contienen access tokens. */
public record RemoteDeviceAuthorization(String deviceCode,String userCode,URI verificationUri,Instant expiresAt,int pollIntervalSeconds) { }
