package com.example.editor.event;
/** Documentos de eventos y ETags cargados conjuntamente. */
public record EventModelLoadResult(EventDefinitionModel definitions, String definitionEtag, EventBindingModel bindings, String bindingEtag, EventModelCheckReport checkReport) { }
