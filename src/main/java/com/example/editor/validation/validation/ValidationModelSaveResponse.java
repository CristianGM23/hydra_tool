package com.example.editor.validation.validation;
public record ValidationModelSaveResponse(ValidationModel validationModel, ValidationModelCheckReport checkReport, String etag) { }
