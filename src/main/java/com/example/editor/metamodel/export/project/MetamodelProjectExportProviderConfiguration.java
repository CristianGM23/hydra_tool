package com.example.editor.metamodel.export.project;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.example.editor.integration.IntegrationBuildSystem;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;

/** Proveedores estándar. El servicio de exportación no conoce ni enumera tecnologías concretas. */
@Configuration
public class MetamodelProjectExportProviderConfiguration {
    /** Descripción: Ejecuta la operación «emfProjectExportProvider» correspondiente a la responsabilidad de MetamodelProjectExportProviderConfiguration. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Bean MetamodelProjectExportProvider emfProjectExportProvider(MetamodelProjectExportProperties properties){
        return provider(MetamodelProjectExportKind.EMF_ECLIPSE, 10, metamodel -> Map.of("basePackage", "com.generated." + slug(metamodel.id()) + ".model", "workspaceMode", "MODEL_EDIT_EDITOR"));
    }
    /** Descripción: Registra el proveedor de distribución Eclipse Modeling completa con runtime HYDRA activable, feature y producto PDE/Tycho. Entradas: properties: configuración común de versiones y herramientas de exportación. Salida: devuelve el proveedor preparado para generar la distribución Eclipse Modeling. */ @Bean MetamodelProjectExportProvider eclipseModelingProjectExportProvider(MetamodelProjectExportProperties properties){
        return provider(MetamodelProjectExportKind.ECLIPSE_MODELING, 15, metamodel -> Map.of(
                "runtimePluginId", "com.generated." + slug(metamodel.id()) + ".hydra.runtime",
                "featureId", "com.generated." + slug(metamodel.id()) + ".modeling.feature",
                "productId", "com.generated." + slug(metamodel.id()) + ".modeling.product"));
    }
    /** Descripción: Registra el proveedor de extensión VS Code con runtime HYDRA portable y puentes Langium, GLSP y Xtext/LSP. Entradas: properties: configuración común de versiones y herramientas de exportación. Salida: devuelve el proveedor preparado para generar la extensión de modelado VS Code. */ @Bean MetamodelProjectExportProvider vscodeModelingProjectExportProvider(MetamodelProjectExportProperties properties){
        return provider(MetamodelProjectExportKind.VSCODE_MODELING, 25, metamodel -> Map.of(
                "packageName", "hydra-" + slug(metamodel.id()) + "-modeling",
                "commandPrefix", "hydra." + slug(metamodel.id()),
                "modelExtension", slug(metamodel.id()),
                "glspVersion", properties.getGlspVersion(),
                "langiumVersion", properties.getLangiumVersion()));
    }

    /** Descripción: Ejecuta la operación «mxgraphDrawioProjectExportProvider» correspondiente a la responsabilidad de MetamodelProjectExportProviderConfiguration. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Bean MetamodelProjectExportProvider mxgraphDrawioProjectExportProvider(MetamodelProjectExportProperties properties){
        return provider(MetamodelProjectExportKind.MXGRAPH_DRAWIO, 27, metamodel -> Map.of(
                "packageName", "hydra-" + slug(metamodel.id()) + "-mxgraph",
                "commandPrefix", "hydra." + slug(metamodel.id()) + ".mxgraph"));
    }
    /** Descripción: Ejecuta la operación «xtextProjectExportProvider» correspondiente a la responsabilidad de MetamodelProjectExportProviderConfiguration. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Bean MetamodelProjectExportProvider xtextProjectExportProvider(MetamodelProjectExportProperties properties){
        return provider(MetamodelProjectExportKind.XTEXT, 30, metamodel -> Map.of("basePackage", "com.generated." + slug(metamodel.id()), "languageName", javaType(metamodel.name()) + "Dsl", "xtextVersion", properties.getXtextVersion()));
    }
    /** Descripción: Ejecuta la operación «langiumProjectExportProvider» correspondiente a la responsabilidad de MetamodelProjectExportProviderConfiguration. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Bean MetamodelProjectExportProvider langiumProjectExportProvider(MetamodelProjectExportProperties properties){
        return provider(MetamodelProjectExportKind.LANGIUM, 40, metamodel -> Map.of("packageName", "@hydra/" + slug(metamodel.id()) + "-langium", "languageName", javaType(metamodel.name()) + "Dsl", "fileExtension", slug(metamodel.id()), "langiumVersion", properties.getLangiumVersion(), "typescriptVersion", properties.getTypescriptVersion()));
    }
    /** Descripción: Ejecuta la operación «glspProjectExportProvider» correspondiente a la responsabilidad de MetamodelProjectExportProviderConfiguration. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Bean MetamodelProjectExportProvider glspProjectExportProvider(MetamodelProjectExportProperties properties){
        return provider(MetamodelProjectExportKind.ECLIPSE_GLSP, 20, metamodel -> Map.of("packageName", "@hydra/" + slug(metamodel.id()) + "-glsp", "diagramType", slug(metamodel.id()) + "-diagram", "serverLanguage", properties.getGlspServerLanguage(), "basePackage", "com.generated." + slug(metamodel.id()) + ".glsp", "glspVersion", properties.getGlspVersion(), "typescriptVersion", properties.getTypescriptVersion(), "platform", "standalone-web"));
    }
    /** Descripción: Ejecuta la operación «besserDesktopProjectExportProvider» correspondiente a la responsabilidad de MetamodelProjectExportProviderConfiguration. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Bean MetamodelProjectExportProvider besserDesktopProjectExportProvider(MetamodelProjectExportProperties properties){
        return provider(MetamodelProjectExportKind.BESSER_DESKTOP, 50, metamodel -> Map.of("pythonModule", slug(metamodel.id()).replace('-', '_') + "_besser", "besserVersion", properties.getBesserVersion(), "delivery", "desktop-sdk"));
    }
    /** Descripción: Ejecuta la operación «besserWebProjectExportProvider» correspondiente a la responsabilidad de MetamodelProjectExportProviderConfiguration. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Bean MetamodelProjectExportProvider besserWebProjectExportProvider(MetamodelProjectExportProperties properties){
        return provider(MetamodelProjectExportKind.BESSER_WEB, 60, metamodel -> Map.of("packageName", "@hydra/" + slug(metamodel.id()) + "-besser-wme", "diagramType", slug(metamodel.id()) + "-diagram", "wmeVersion", properties.getBesserWmeVersion(), "delivery", "standalone-web"));
    }

    /** Descripción: Ejecuta la operación «provider» correspondiente a la responsabilidad de MetamodelProjectExportProviderConfiguration. Entradas: kind: dato de entrada necesario para la operación; order: dato de entrada necesario para la operación; defaults: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelProjectExportProvider provider(MetamodelProjectExportKind kind, int order, java.util.function.Function<com.example.editor.metamodel.core.MetaModel, Map<String,String>> defaults){
        return new StandardMetamodelProjectExportProvider(kind.id(), kind.displayName(), kind.description(), kind.technology(), kind.targetKind(), kind.buildSystem(), kind.requiresEditor(), order, defaults);
    }
    /** Descripción: Ejecuta la operación «slug» correspondiente a la responsabilidad de MetamodelProjectExportProviderConfiguration. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String slug(String value){String result = value == null ? "metamodel" : value.toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", ""); return result.isBlank() ? "metamodel" : result;}
    /** Descripción: Ejecuta la operación «javaType» correspondiente a la responsabilidad de MetamodelProjectExportProviderConfiguration. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String javaType(String value){String[] tokens = (value == null ? "Model" : value).replaceAll("[^A-Za-z0-9]+", " ").trim().split("\\s+"); StringBuilder result = new StringBuilder(); for(String token : tokens){if(!token.isBlank()){result.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));}} return result.isEmpty() ? "Model" : result.toString();}
}
