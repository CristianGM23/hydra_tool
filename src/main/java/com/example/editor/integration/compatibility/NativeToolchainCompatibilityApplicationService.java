package com.example.editor.integration.compatibility;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Runs catalogued probes and persists the latest compatibility matrix atomically. */
@Service
public class NativeToolchainCompatibilityApplicationService {
    private final NativeToolchainProbeCatalog catalog;
    private final NativeToolchainCompatibilityOrchestrator orchestrator;
    private final TechnologyCompatibilityMatrixWriter writer;
    private final JsonMapper mapper;
    private final Path reportRoot;

    @Autowired
    /** Descripción: Inicializa una instancia de NativeToolchainCompatibilityApplicationService con sus dependencias y datos de entrada. Entradas: catalog: dato de entrada necesario para la operación; orchestrator: dato de entrada necesario para la operación; writer: componente de conversión utilizado durante el procesamiento; mapper: componente de conversión utilizado durante el procesamiento; reportRoot: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public NativeToolchainCompatibilityApplicationService(NativeToolchainProbeCatalog catalog,
            NativeToolchainCompatibilityOrchestrator orchestrator, TechnologyCompatibilityMatrixWriter writer,
            JsonMapper mapper, @Value("${hydra.compatibility.report-root:data}") String reportRoot){
        this(catalog, orchestrator, writer, mapper, Path.of(reportRoot));
    }

    /** Descripción: Inicializa una instancia de NativeToolchainCompatibilityApplicationService con sus dependencias y datos de entrada. Entradas: catalog: dato de entrada necesario para la operación; orchestrator: dato de entrada necesario para la operación; writer: componente de conversión utilizado durante el procesamiento; mapper: componente de conversión utilizado durante el procesamiento; reportRoot: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ NativeToolchainCompatibilityApplicationService(NativeToolchainProbeCatalog catalog,
            NativeToolchainCompatibilityOrchestrator orchestrator, TechnologyCompatibilityMatrixWriter writer,
            JsonMapper mapper, Path reportRoot){
        this.catalog = catalog; this.orchestrator = orchestrator; this.writer = writer; this.mapper = mapper;
        this.reportRoot = reportRoot.toAbsolutePath().normalize();
    }

    /** Descripción: Ejecuta la operación «catalog» correspondiente a la responsabilidad de NativeToolchainCompatibilityApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<NativeToolchainCatalogEntry> catalog(){return catalog.entries();}

    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de NativeToolchainCompatibilityApplicationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyCompatibilityMatrix run(NativeToolchainCompatibilityRunRequest request){
        TechnologyCompatibilityMatrix matrix = orchestrator.execute(
                catalog.select(request == null ? List.of() : request.selectedKeys()));
        writer.write(reportRoot, matrix);
        return matrix;
    }

    /** Descripción: Ejecuta la operación «latest» correspondiente a la responsabilidad de NativeToolchainCompatibilityApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<TechnologyCompatibilityMatrix> latest(){
        Path path = reportRoot.resolve(TechnologyCompatibilityMatrixWriter.DEFAULT_PATH).normalize();
        if(!path.startsWith(reportRoot) || !Files.isRegularFile(path)){return Optional.empty();}
        try{return Optional.of(mapper.readValue(path.toFile(), TechnologyCompatibilityMatrix.class));}
        catch(JacksonException exception){throw new IllegalStateException("Could not parse the latest compatibility matrix", exception);}
    }
}
