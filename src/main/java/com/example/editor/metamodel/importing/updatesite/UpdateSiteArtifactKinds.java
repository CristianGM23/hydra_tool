package com.example.editor.metamodel.importing.updatesite;

import java.util.Locale;

/** Clasifica extensiones y descriptores Eclipse sin depender de la tecnología destino. */
final class UpdateSiteArtifactKinds {
    /** Descripción: Inicializa una instancia de UpdateSiteArtifactKinds con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private UpdateSiteArtifactKinds() { }

    /** Descripción: Ejecuta la operación «fromPath» correspondiente a la responsabilidad de UpdateSiteArtifactKinds. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ static UpdateSiteArtifactKind fromPath(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".ecore")) return UpdateSiteArtifactKind.ECORE;
        if(lower.endsWith(".emf") || lower.endsWith(".emfatic")) return UpdateSiteArtifactKind.EMFATIC;
        if(lower.endsWith(".ecorediag") || lower.endsWith(".ecore_diagram")) return UpdateSiteArtifactKind.ECORE_DIAGRAM;
        if(lower.endsWith(".uml")) return UpdateSiteArtifactKind.UML;
        if(lower.endsWith(".xmi")) return UpdateSiteArtifactKind.XMI;
        if(lower.endsWith(".xsd")) return UpdateSiteArtifactKind.XSD;
        if(lower.endsWith(".schema.json") || lower.endsWith(".jsonschema")) return UpdateSiteArtifactKind.JSON_SCHEMA;
        if(lower.endsWith(".blockly.json") || lower.endsWith(".blocks.json")) return UpdateSiteArtifactKind.BLOCKLY_JSON;
        if(lower.endsWith(".blockly.js") || lower.endsWith(".blocks.js")) return UpdateSiteArtifactKind.BLOCKLY_JAVASCRIPT;
        if(lower.endsWith(".json")) return UpdateSiteArtifactKind.JSON;
        if(lower.endsWith(".graphql") || lower.endsWith(".graphqls")) return UpdateSiteArtifactKind.GRAPHQL;
        if(lower.endsWith(".proto")) return UpdateSiteArtifactKind.PROTO;
        if(lower.endsWith(".genmodel")) return UpdateSiteArtifactKind.GENMODEL;
        if(lower.endsWith(".gmfgraph")) return UpdateSiteArtifactKind.GMF_GRAPH;
        if(lower.endsWith(".gmftool")) return UpdateSiteArtifactKind.GMF_TOOL;
        if(lower.endsWith(".gmfmap")) return UpdateSiteArtifactKind.GMF_MAP;
        if(lower.endsWith(".gmfgen")) return UpdateSiteArtifactKind.GMF_GEN;
        if(lower.endsWith(".odesign")) return UpdateSiteArtifactKind.SIRIUS_VIEWPOINT;
        if(lower.endsWith(".aird")) return UpdateSiteArtifactKind.SIRIUS_SESSION;
        if(lower.endsWith(".xtext")) return UpdateSiteArtifactKind.XTEXT_GRAMMAR;
        if(lower.endsWith(".langium")) return UpdateSiteArtifactKind.LANGIUM_GRAMMAR;
        if(lower.endsWith(".g4")) return UpdateSiteArtifactKind.ANTLR_GRAMMAR;
        if(lower.endsWith("plugin.xml")) return UpdateSiteArtifactKind.PLUGIN_XML;
        if(lower.endsWith("fragment.xml")) return UpdateSiteArtifactKind.FRAGMENT_XML;
        if(lower.endsWith("meta-inf/manifest.mf")) return UpdateSiteArtifactKind.MANIFEST;
        if(lower.endsWith("build.properties")) return UpdateSiteArtifactKind.BUILD_PROPERTIES;
        if(lower.endsWith("feature.xml")) return UpdateSiteArtifactKind.FEATURE_XML;
        if(lower.endsWith("category.xml")) return UpdateSiteArtifactKind.CATEGORY_XML;
        if(lower.endsWith("site.xml")) return UpdateSiteArtifactKind.SITE_XML;
        if(lower.endsWith(".target")) return UpdateSiteArtifactKind.TARGET_PLATFORM;
        if(lower.endsWith(".product")) return UpdateSiteArtifactKind.ECLIPSE_PRODUCT;
        if(lower.endsWith(".properties")) return UpdateSiteArtifactKind.PROPERTIES;
        if(lower.endsWith(".ocl")) return UpdateSiteArtifactKind.OCL;
        if(lower.endsWith(".evl")) return UpdateSiteArtifactKind.EVL;
        if(lower.endsWith(".eol")) return UpdateSiteArtifactKind.EOL;
        if(lower.endsWith(".xcore")) return UpdateSiteArtifactKind.XCORE;
        if(lower.endsWith(".ts") || lower.endsWith(".tsx")) return UpdateSiteArtifactKind.TYPESCRIPT_SOURCE;
        if(lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".cjs")) return UpdateSiteArtifactKind.JAVASCRIPT_SOURCE;
        if(lower.endsWith(".go")) return UpdateSiteArtifactKind.GO_SOURCE;
        if(lower.endsWith(".java")) return UpdateSiteArtifactKind.JAVA_SOURCE;
        if(lower.endsWith(".py")) return UpdateSiteArtifactKind.PYTHON_SOURCE;
        if(lower.endsWith(".puml") || lower.endsWith(".plantuml")) return UpdateSiteArtifactKind.PLANTUML;
        if(lower.endsWith(".kt")) return UpdateSiteArtifactKind.KOTLIN_SOURCE;
        if(lower.endsWith(".class")) return UpdateSiteArtifactKind.JAVA_CLASS;
        return UpdateSiteArtifactKind.OTHER;
    }

    /** Descripción: Ejecuta la operación «relevant» correspondiente a la responsabilidad de UpdateSiteArtifactKinds. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean relevant(UpdateSiteArtifactKind kind) {
        return kind != UpdateSiteArtifactKind.OTHER;
    }
}
