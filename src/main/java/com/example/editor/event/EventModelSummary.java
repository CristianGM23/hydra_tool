package com.example.editor.event;
/** Resumen de contratos de eventos asociados a un metamodelo. */
public record EventModelSummary(String metamodelId, String definitionModelId, int eventTypeCount, String bindingModelId, int bindingCount, String definitionEtag, String bindingEtag) { }
