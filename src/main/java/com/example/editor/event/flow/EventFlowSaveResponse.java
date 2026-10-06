package com.example.editor.event.flow;
public record EventFlowSaveResponse(EventFlowModel eventFlowModel, String etag, EventFlowCheckReport checkReport) { }
