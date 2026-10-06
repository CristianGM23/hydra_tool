package com.example.editor.validation.action;
public record ActionModelSaveResponse(ActionModel actionModel, ActionModelCheckReport checkReport, String etag) { }
