package com.example.editor.integration;
/** Estrategia portable para localizar un elemento dentro de una tecnología externa. */
public enum IntegrationLocatorStrategy {
    NS_URI_AND_CLASSIFIER,
    URI_FRAGMENT,
    QUALIFIED_NAME,
    DOCUMENT_PATH,
    AST_PATH,
    BESSER_ELEMENT_ID,
    GLSP_DIAGRAM_TYPE,
    GLSP_ELEMENT_ID,
    PLANTUML_ALIAS,
    CUSTOM
}
