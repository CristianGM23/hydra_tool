package com.example.editor.integration.compatibility;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Owns the allow-listed commands that may be launched from the compatibility center. */
@Service
public class NativeToolchainProbeCatalog {
    private final Path workingDirectory = Path.of("").toAbsolutePath().normalize();

    /** Descripción: Ejecuta la operación «entries» correspondiente a la responsabilidad de NativeToolchainProbeCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<NativeToolchainCatalogEntry> entries(){
        return definitions().values().stream().map(definition -> new NativeToolchainCatalogEntry(definition.key(),
                definition.technology(), definition.version(), description(definition.technology()),
                definition.commands().stream().map(NativeToolchainProbeCommand::id).toList())).toList();
    }

    /** Descripción: Ejecuta la operación «select» correspondiente a la responsabilidad de NativeToolchainProbeCatalog. Entradas: requestedKeys: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<NativeToolchainProbeDefinition> select(List<String> requestedKeys){
        Map<String, NativeToolchainProbeDefinition> available = definitions();
        if(requestedKeys == null || requestedKeys.isEmpty()){return List.copyOf(available.values());}
        List<NativeToolchainProbeDefinition> selected = new ArrayList<>();
        for(String key : requestedKeys){
            NativeToolchainProbeDefinition definition = available.get(key == null ? "" : key.trim().toLowerCase(Locale.ROOT));
            if(definition == null){throw new IllegalArgumentException("Unknown native toolchain profile: " + key);}
            if(!selected.contains(definition)){selected.add(definition);}
        }
        return selected;
    }

    /** Descripción: Ejecuta la operación «definitions» correspondiente a la responsabilidad de NativeToolchainProbeCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, NativeToolchainProbeDefinition> definitions(){
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String maven = windows ? "mvn.cmd" : "mvn";
        String npm = windows ? "npm.cmd" : "npm";
        String python = windows ? "python" : "python3";
        Map<String, NativeToolchainProbeDefinition> definitions = new LinkedHashMap<>();
        add(definitions, definition("EMF", "2.39/2.42", required("java", "Java runtime", List.of("java", "-version")), optional("maven", "Maven runtime", List.of(maven, "-version"))));
        add(definitions, definition("Sirius Desktop", "configured", required("java", "Java runtime", List.of("java", "-version")), optional("tycho-maven", "Maven/Tycho host", List.of(maven, "-version"))));
        add(definitions, definition("Xtext", "configured", required("java", "Java runtime", List.of("java", "-version")), required("maven", "Maven runtime", List.of(maven, "-version"))));
        add(definitions, definition("Langium", "configured", required("node", "Node.js runtime", List.of("node", "--version")), required("npm", "npm package manager", List.of(npm, "--version"))));
        add(definitions, definition("Eclipse GLSP", "configured", required("java", "Java runtime", List.of("java", "-version")), required("node", "Node.js runtime", List.of("node", "--version")), required("npm", "npm package manager", List.of(npm, "--version"))));
        add(definitions, definition("BESSER SDK", "configured", required("python", "Python runtime", List.of(python, "--version")), optional("pip", "Python package manager", List.of(python, "-m", "pip", "--version"))));
        add(definitions, definition("BESSER Web Modeling Editor", "configured", required("node", "Node.js runtime", List.of("node", "--version")), required("npm", "npm package manager", List.of(npm, "--version"))));
        return definitions;
    }

    /** Descripción: Ejecuta la operación «definition» correspondiente a la responsabilidad de NativeToolchainProbeCatalog. Entradas: technology: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación; commands: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NativeToolchainProbeDefinition definition(String technology, String version, NativeToolchainProbeCommand... commands){
        return new NativeToolchainProbeDefinition(technology, version, workingDirectory, List.of(commands), Map.of());
    }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo; description: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NativeToolchainProbeCommand required(String id, String description, List<String> command){return new NativeToolchainProbeCommand(id, description, command, 0, 20, true);}
    /** Descripción: Ejecuta la operación «optional» correspondiente a la responsabilidad de NativeToolchainProbeCatalog. Entradas: id: identificador que delimita el recurso objetivo; description: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NativeToolchainProbeCommand optional(String id, String description, List<String> command){return new NativeToolchainProbeCommand(id, description, command, 0, 20, false);}
    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de NativeToolchainProbeCatalog. Entradas: target: dato de entrada necesario para la operación; definition: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(Map<String, NativeToolchainProbeDefinition> target, NativeToolchainProbeDefinition definition){target.put(definition.key(), definition);}
    /** Descripción: Ejecuta la operación «description» correspondiente a la responsabilidad de NativeToolchainProbeCatalog. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String description(String technology){return "Runs bounded version probes for the native " + technology + " toolchain without accepting arbitrary commands.";}
}
