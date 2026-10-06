package com.example.editor.metamodel.importing.updatesite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Clasifica un bundle mediante descriptores, artefactos y dependencias. */
@Component
public class P2BundleClassifier {

    /** Descripción: Ejecuta la operación «classify» correspondiente a la responsabilidad de Classification. Entradas: id: identificador que delimita el recurso objetivo; headers: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación; pluginXml: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Classification classify(String id, Map<String, String> headers, List<UpdateSiteArtifactDescriptor> artifacts,
            String pluginXml) {
        String lowerId = id.toLowerCase(Locale.ROOT);
        String dependencies = (headers.getOrDefault("Require-Bundle", "") + " "
                + headers.getOrDefault("Import-Package", "")).toLowerCase(Locale.ROOT);
        String xml = pluginXml == null ? "" : pluginXml.toLowerCase(Locale.ROOT);
        List<String> signals = new ArrayList<>();

        if(hasAny(artifacts, UpdateSiteArtifactKind.GMF_GRAPH, UpdateSiteArtifactKind.GMF_TOOL,
                UpdateSiteArtifactKind.GMF_MAP, UpdateSiteArtifactKind.GMF_GEN)
                || dependencies.contains("org.eclipse.gmf")) {
            signals.add("GMF descriptor or dependency detected");
            return new Classification(UpdateSiteBundleKind.GMF_DIAGRAM_PLUGIN, signals);
        }
        if(hasAny(artifacts, UpdateSiteArtifactKind.SIRIUS_VIEWPOINT, UpdateSiteArtifactKind.SIRIUS_SESSION)
                || dependencies.contains("org.eclipse.sirius") || xml.contains("org.eclipse.sirius.componentization")) {
            signals.add("Sirius viewpoint descriptor or dependency detected");
            return new Classification(UpdateSiteBundleKind.SIRIUS_VIEWPOINT_PLUGIN, signals);
        }
        if(hasAny(artifacts, UpdateSiteArtifactKind.XTEXT_GRAMMAR, UpdateSiteArtifactKind.LANGIUM_GRAMMAR)
                || dependencies.contains("org.eclipse.xtext") || lowerId.endsWith(".ide") && dependencies.contains("xtext")) {
            signals.add("Xtext or Langium grammar/runtime detected");
            return new Classification(UpdateSiteBundleKind.XTEXT_LANGUAGE_PLUGIN, signals);
        }
        if(lowerId.endsWith(".edit") || containsClass(artifacts, "itemprovideradapterfactory.class")) {
            signals.add("EMF edit naming or item-provider classes detected");
            return new Classification(UpdateSiteBundleKind.EDIT_PLUGIN, signals);
        }
        if(lowerId.endsWith(".editor") || xml.contains("org.eclipse.ui.editors")
                || containsClass(artifacts, "editorplugin.class")) {
            signals.add("Generated EMF editor or org.eclipse.ui.editors extension detected");
            return new Classification(UpdateSiteBundleKind.EDITOR_PLUGIN, signals);
        }
        if(hasAny(artifacts, UpdateSiteArtifactKind.ECORE, UpdateSiteArtifactKind.GENMODEL,
                UpdateSiteArtifactKind.UML, UpdateSiteArtifactKind.XSD)
                || xml.contains("org.eclipse.emf.ecore.generated_package")
                || containsClass(artifacts, "packageimpl.class")) {
            signals.add("Metamodel, GenModel, generated-package registration or EMF package bytecode detected");
            return new Classification(UpdateSiteBundleKind.MODEL_PLUGIN, signals);
        }
        if(dependencies.contains("org.eclipse.ui") || xml.contains("org.eclipse.ui.")) {
            signals.add("Eclipse UI dependency or extension detected");
            return new Classification(UpdateSiteBundleKind.UI_PLUGIN, signals);
        }
        if(lowerId.endsWith(".runtime") || headers.containsKey("Bundle-Activator")) {
            signals.add("Runtime naming or bundle activator detected");
            return new Classification(UpdateSiteBundleKind.RUNTIME_PLUGIN, signals);
        }
        signals.add("No HYDRA-specific model, editor, diagram or language signal was detected");
        return new Classification(UpdateSiteBundleKind.THIRD_PARTY_DEPENDENCY, signals);
    }

    /** Descripción: Evalúa la condición «hasAny» sin modificar el estado externo. Entradas: artifacts: dato de entrada necesario para la operación; kinds: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasAny(List<UpdateSiteArtifactDescriptor> artifacts, UpdateSiteArtifactKind... kinds) {
        List<UpdateSiteArtifactKind> expected = List.of(kinds);
        return artifacts.stream().anyMatch(item -> expected.contains(item.kind()));
    }

    /** Descripción: Evalúa la condición «containsClass» sin modificar el estado externo. Entradas: artifacts: dato de entrada necesario para la operación; suffix: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean containsClass(List<UpdateSiteArtifactDescriptor> artifacts, String suffix) {
        return artifacts.stream().filter(item -> item.kind() == UpdateSiteArtifactKind.JAVA_CLASS)
                .map(UpdateSiteArtifactDescriptor::path).map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.endsWith(suffix));
    }

    record Classification(UpdateSiteBundleKind kind, List<String> signals) {
        Classification {
            signals = signals == null ? List.of() : List.copyOf(signals);
        }
    }
}
