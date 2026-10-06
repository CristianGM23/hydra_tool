package com.example.editor.metamodel.importing.updatesite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.example.editor.metamodel.importing.support.SecureXmlSupport;

/** Analiza la metadata estándar de repositorios Eclipse p2. */
@Component
public class P2RepositoryMetadataParser {

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de P2RepositoryMetadataParser. Entradas: repository: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ P2MetadataSnapshot parse(UpdateSiteRepositoryContent repository) {
        Optional<Map.Entry<String, byte[]>> content = findMetadata(repository, "content.xml");
        Optional<Map.Entry<String, byte[]>> artifacts = findMetadata(repository, "artifacts.xml");
        Map<String, P2MetadataSnapshot.P2Unit> units = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        String repositoryName = "";

        if(content.isPresent()) {
            Document document = SecureXmlSupport.parse(content.get().getValue(), "p2 content metadata");
            repositoryName = attr(document.getDocumentElement(), "name");
            NodeList unitNodes = document.getElementsByTagName("unit");
            for(int index = 0; index < unitNodes.getLength(); index++) {
                Element unit = asElement(unitNodes.item(index));
                if(unit == null) {
                    continue;
                }
                String id = attr(unit, "id");
                if(id.isBlank()) {
                    continue;
                }
                String version = attr(unit, "version");
                String name = property(unit, "org.eclipse.equinox.p2.name").orElse(id);
                boolean feature = id.endsWith(".feature.group") || hasArtifactClassifier(unit, "org.eclipse.update.feature");
                List<UpdateSiteDependency> dependencies = requiredUnits(unit, id);
                List<String> classifiers = artifactClassifiers(unit);
                units.put(id + ":" + version, new P2MetadataSnapshot.P2Unit(id, version, name, feature, dependencies, classifiers));
            }
        } else {
            diagnostics.add("content.xml/content.jar was not found; bundle discovery falls back to repository files");
        }

        if(artifacts.isEmpty()) {
            diagnostics.add("artifacts.xml/artifacts.jar was not found; physical plugin paths are inferred from plugins/ and features/");
        } else {
            try {
                SecureXmlSupport.parse(artifacts.get().getValue(), "p2 artifact metadata");
            } catch(RuntimeException exception) {
                diagnostics.add("artifacts.xml could not be parsed: " + exception.getMessage());
            }
        }

        if(repositoryName.isBlank()) {
            repositoryName = inferRepositoryName(repository);
        }
        return new P2MetadataSnapshot(repositoryName, content.isPresent(), artifacts.isPresent(), units, diagnostics);
    }

    /** Descripción: Obtiene la información correspondiente a «findMetadata» dentro de P2RepositoryMetadataParser. Entradas: repository: dato de entrada necesario para la operación; fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Optional<Map.Entry<String, byte[]>> findMetadata(UpdateSiteRepositoryContent repository, String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return repository.files().entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).endsWith(lowerName))
                .sorted(Map.Entry.comparingByKey()).findFirst();
    }

    /** Descripción: Ejecuta la operación «property» correspondiente a la responsabilidad de P2RepositoryMetadataParser. Entradas: unit: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Optional<String> property(Element unit, String name) {
        NodeList properties = unit.getElementsByTagName("property");
        for(int index = 0; index < properties.getLength(); index++) {
            Element property = asElement(properties.item(index));
            if(property != null && name.equals(attr(property, "name"))) {
                return Optional.of(attr(property, "value"));
            }
        }
        return Optional.empty();
    }

    /** Descripción: Valida las condiciones necesarias para «requiredUnits» y rechaza entradas no válidas. Entradas: unit: dato de entrada necesario para la operación; sourceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteDependency> requiredUnits(Element unit, String sourceId) {
        List<UpdateSiteDependency> result = new ArrayList<>();
        NodeList requiredNodes = unit.getElementsByTagName("required");
        for(int index = 0; index < requiredNodes.getLength(); index++) {
            Element required = asElement(requiredNodes.item(index));
            if(required == null) {
                continue;
            }
            String namespace = attr(required, "namespace");
            if(!namespace.equals("org.eclipse.equinox.p2.iu")) {
                continue;
            }
            String target = attr(required, "name");
            if(target.isBlank()) {
                continue;
            }
            boolean optional = "true".equalsIgnoreCase(attr(required, "optional"))
                    || "0".equals(attr(required, "min"));
            result.add(new UpdateSiteDependency(sourceId, target, attr(required, "range"), optional, false, "content.xml"));
        }
        return List.copyOf(result);
    }

    /** Descripción: Evalúa la condición «hasArtifactClassifier» sin modificar el estado externo. Entradas: unit: dato de entrada necesario para la operación; expected: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasArtifactClassifier(Element unit, String expected) {
        return artifactClassifiers(unit).stream().anyMatch(expected::equals);
    }

    /** Descripción: Ejecuta la operación «artifactClassifiers» correspondiente a la responsabilidad de P2RepositoryMetadataParser. Entradas: unit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> artifactClassifiers(Element unit) {
        List<String> result = new ArrayList<>();
        NodeList artifacts = unit.getElementsByTagName("artifact");
        for(int index = 0; index < artifacts.getLength(); index++) {
            Element artifact = asElement(artifacts.item(index));
            if(artifact != null) {
                String classifier = attr(artifact, "classifier");
                if(!classifier.isBlank()) {
                    result.add(classifier);
                }
            }
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «inferRepositoryName» correspondiente a la responsabilidad de P2RepositoryMetadataParser. Entradas: repository: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String inferRepositoryName(UpdateSiteRepositoryContent repository) {
        return repository.files().keySet().stream().filter(path -> !path.contains("!/"))
                .map(path -> path.contains("/") ? path.substring(0, path.indexOf('/')) : "Eclipse update site")
                .findFirst().orElse("Eclipse update site");
    }

    /** Descripción: Ejecuta la operación «asElement» correspondiente a la responsabilidad de P2RepositoryMetadataParser. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Element asElement(Node node) {
        return node instanceof Element element ? element : null;
    }

    /** Descripción: Ejecuta la operación «attr» correspondiente a la responsabilidad de P2RepositoryMetadataParser. Entradas: element: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String attr(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name).trim() : "";
    }
}
