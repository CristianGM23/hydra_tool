package com.example.editor.transformation.web;

import com.example.editor.transformation.core.TransformationDefinitionDocument;

/** Solicitud T2M de preview sobre un draft no persistido. */
public record TransformationDraftTextRequest(TransformationDefinitionDocument transformation,String text) {
    public TransformationDraftTextRequest { text=text==null?"":text; }
}
