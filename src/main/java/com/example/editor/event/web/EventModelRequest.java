package com.example.editor.event.web;
import com.example.editor.event.EventBindingModel;
import com.example.editor.event.EventDefinitionModel;
/** Petición conjunta para guardar definiciones y bindings. */
public record EventModelRequest(EventDefinitionModel definitions, EventBindingModel bindings) { }
