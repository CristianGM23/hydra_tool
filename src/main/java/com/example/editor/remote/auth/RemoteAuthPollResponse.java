package com.example.editor.remote.auth;

/** Estado seguro de un Device Authorization Grant; nunca contiene device_code ni tokens. */
public record RemoteAuthPollResponse(String state,String profileId,boolean completed,boolean pending,int pollAfterSeconds,RemoteAuthConnectionDescriptor connection) { }
