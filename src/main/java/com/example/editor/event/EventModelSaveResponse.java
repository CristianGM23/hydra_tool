package com.example.editor.event;
/** Respuesta de guardado de contratos de eventos. */
public record EventModelSaveResponse(EventDefinitionModel definitions, String definitionEtag, EventBindingModel bindings, String bindingEtag, EventModelCheckReport checkReport) { }
