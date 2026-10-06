package com.example.editor.remote.auth;

/** Identificador opaco del flujo de dispositivo que el navegador puede sondear. */
public record RemoteAuthPollRequest(String state) { }
