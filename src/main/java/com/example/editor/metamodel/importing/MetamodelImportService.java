package com.example.editor.metamodel.importing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.application.MetamodelSaveResponse;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramStorageService;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.metamodel.validation.ValidationReport;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.importing.canonicalization.ImportedMetamodelCanonicalization;
import com.example.editor.metamodel.importing.canonicalization.ImportedMetamodelCanonicalizer;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/**
 * Selecciona importadores, valida resultados y coordina la persistencia.
 */
@Service
public class MetamodelImportService implements MetamodelImportAnalysisProcessor {

    private final MetamodelImportProperties properties;
    private final MetamodelImportDetector detector;
    private final ImportedMetamodelNormalizer normalizer;
    private final AutomaticMetamodelDiagramLayoutService layoutService;
    private final ImportedDiagramRelationNormalizer relationNormalizer;
    private final NeutralMetamodelValidator validator;
    private final CanonicalMetamodelApplicationService canonicalService;
    private final MetamodelDiagramStorageService diagramStorageService;
    private final MetamodelRegistryService runtimeRegistry;
    private final Map<MetamodelImportFormat, MetamodelImporter> importers;

    /** Descripción: Inicializa una instancia de MetamodelImportService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; detector: dato de entrada necesario para la operación; normalizer: dato de entrada necesario para la operación; layoutService: servicio colaborador utilizado por la operación; relationNormalizer: dato de entrada necesario para la operación; validator: dato de entrada necesario para la operación; canonicalService: servicio colaborador utilizado por la operación; diagramStorageService: servicio colaborador utilizado por la operación; runtimeRegistry: dato de entrada necesario para la operación; availableImporters: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelImportService(MetamodelImportProperties properties, MetamodelImportDetector detector, ImportedMetamodelNormalizer normalizer, AutomaticMetamodelDiagramLayoutService layoutService, ImportedDiagramRelationNormalizer relationNormalizer, NeutralMetamodelValidator validator, CanonicalMetamodelApplicationService canonicalService, MetamodelDiagramStorageService diagramStorageService, MetamodelRegistryService runtimeRegistry, List<MetamodelImporter> availableImporters) {
        this.properties = properties;
        this.detector = detector;
        this.normalizer = normalizer;
        this.layoutService = layoutService;
        this.relationNormalizer = relationNormalizer;
        this.validator = validator;
        this.canonicalService = canonicalService;
        this.diagramStorageService = diagramStorageService;
        this.runtimeRegistry = runtimeRegistry;
        this.importers = indexImporters(availableImporters);
    }

    /** Descripción: Obtiene la información correspondiente a «listFormats» dentro de ProcessedImport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<MetamodelImportFormatResponse> listFormats() {
        return java.util.Arrays.stream(MetamodelImportFormat.values()).filter(this::isAllowed).map(format -> new MetamodelImportFormatResponse(format.id(), format.displayName(), format.category(), format.supportLevel().name(), format.extensions())).toList();
    }

    @Override
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de ProcessedImport. Entradas: fileName: archivo o contenido que debe procesarse; mediaType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; options: configuración que gobierna el comportamiento; progressListener: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportPreview preview(String fileName, String mediaType, byte[] content, MetamodelImportOptions options, MetamodelImportProgressListener progressListener) {
        return process(fileName, mediaType, content, options, progressListener).preview();
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de ProcessedImport. Entradas: fileName: archivo o contenido que debe procesarse; mediaType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportPreview preview(String fileName, String mediaType, byte[] content, MetamodelImportOptions options) {
        return preview(fileName, mediaType, content, options, MetamodelImportProgressListener.none());
    }

    /** Descripción: Ejecuta la operación «importAndSave» correspondiente a la responsabilidad de ProcessedImport. Entradas: fileName: archivo o contenido que debe procesarse; mediaType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; requestedOptions: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportSaveResult importAndSave(String fileName, String mediaType, byte[] content, MetamodelImportOptions requestedOptions) {
        MetamodelImportOptions options = requestedOptions == null ? MetamodelImportOptions.defaults() : requestedOptions;
        ProcessedImport processed = process(fileName, mediaType, content, options, MetamodelImportProgressListener.none());
        MetamodelSaveResponse saved = saveCanonical(processed.metamodel(), processed.targets(), options.overwrite());
        diagramStorageService.save(saved.metamodel().id(), processed.preview().diagram());
        runtimeRegistry.initialize();
        return new MetamodelImportSaveResult(saved.metamodel().id(), saved.etag(), processed.preview());
    }

    /**
     * Persiste un snapshot canónico ya producido por el análisis asíncrono.
     * Esta ruta evita volver a leer, detectar y parsear el archivo original.
     *
     * @param preview snapshot canónico preparado.
     * @param overwrite permite sustituir un metamodelo existente.
     * @return resultado persistido con el mismo preview analizado.
     */
    /** Descripción: Ejecuta la operación «importPrepared» correspondiente a la responsabilidad de ProcessedImport. Entradas: preview: dato de entrada necesario para la operación; overwrite: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportSaveResult importPrepared(MetamodelImportPreview preview, boolean overwrite) {
        if(preview == null || preview.metamodel() == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Prepared metamodel import preview is required");
        }
        MetamodelSaveResponse saved = saveCanonical(preview.metamodel(), preview.targets(), overwrite);
        diagramStorageService.save(saved.metamodel().id(), preview.diagram());
        runtimeRegistry.initialize();
        return new MetamodelImportSaveResult(saved.metamodel().id(), saved.etag(), preview);
    }

    /** Descripción: Actualiza el estado gestionado por ProcessedImport mediante la operación «saveCanonical». Entradas: metamodel: dato de entrada necesario para la operación; targets: dato de entrada necesario para la operación; overwrite: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelSaveResponse saveCanonical(MetaModel metamodel, TargetConfigurationDocument targets, boolean overwrite) {
        try {
            MetamodelLoadResult existing = canonicalService.findById(metamodel.id());
            if(!overwrite) {
                throw new MetamodelDesignException(Reason.CONFLICT, "Canonical metamodel already exists: " + metamodel.id());
            }
            return canonicalService.replace(metamodel.id(), metamodel, existing.etag(), targets);
        } catch(MetamodelApplicationException exception) {
            if(exception.getReason() == MetamodelApplicationException.Reason.NOT_FOUND) {
                return canonicalService.create(metamodel, targets);
            }
            throw exception;
        }
    }

    /** Descripción: Procesa la operación «process» coordinando las responsabilidades de ProcessedImport. Entradas: fileName: archivo o contenido que debe procesarse; mediaType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; requestedOptions: solicitud con los datos necesarios para ejecutar la operación; requestedProgressListener: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProcessedImport process(String fileName, String mediaType, byte[] content, MetamodelImportOptions requestedOptions, MetamodelImportProgressListener requestedProgressListener) {
        MetamodelImportProgressListener progress = requestedProgressListener == null ? MetamodelImportProgressListener.none() : requestedProgressListener;

        progress.stageStarted(MetamodelImportAnalysisStage.CONFIGURATION, "Checking metamodel import configuration", "requestedFile=" + safe(fileName));
        ensureEnabled();
        MetamodelImportOptions options = requestedOptions == null ? MetamodelImportOptions.defaults() : requestedOptions;
        String optionDetails = describeOptions(options);
        progress.log(MetamodelImportAnalysisStage.CONFIGURATION, MetamodelImportActivitySeverity.INFO, "IMPORT_CONFIGURATION_RESOLVED", "Import options were resolved", optionDetails);
        progress.stageCompleted(MetamodelImportAnalysisStage.CONFIGURATION, "Import configuration is valid", optionDetails);

        progress.stageStarted(MetamodelImportAnalysisStage.FILE_VALIDATION, "Validating the uploaded file", "fileName=" + safe(fileName));
        validateContent(content);
        MetamodelImportSource source = new MetamodelImportSource(fileName, mediaType, content);
        String sourceDetails = describeSource(source);
        progress.log(MetamodelImportAnalysisStage.FILE_VALIDATION, MetamodelImportActivitySeverity.INFO, "IMPORT_FILE_VALID", "The uploaded file passed validation", sourceDetails);
        progress.stageCompleted(MetamodelImportAnalysisStage.FILE_VALIDATION, "The uploaded file is valid", sourceDetails);

        progress.stageStarted(MetamodelImportAnalysisStage.FORMAT_DETECTION, "Detecting and checking the import format", "requestedFormat=" + options.formatId());
        MetamodelImportFormat format = resolveFormat(source, options);
        ensureAllowed(format);
        MetamodelImporter importer = importers.get(format);

        if(importer == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel importer is not registered: " + format.id());
        }

        String formatDetails = describeFormat(format, importer);
        progress.log(MetamodelImportAnalysisStage.FORMAT_DETECTION, MetamodelImportActivitySeverity.INFO, "IMPORT_FORMAT_DETECTED", "The source format was detected", formatDetails);
        progress.stageCompleted(MetamodelImportAnalysisStage.FORMAT_DETECTION, "The import format is ready", formatDetails);

        progress.stageStarted(MetamodelImportAnalysisStage.PARSING, "Reading the source metamodel structure", "importer=" + importer.getClass().getName());
        MetamodelImportOutput imported = importer.importMetamodel(source, options);
        String parsingDetails = describeMetamodel("parsed", imported.metamodel()) + System.lineSeparator() + describeDiagram("recoveredDiagram", imported.diagram()) + System.lineSeparator() + describeWarnings("parserWarnings", imported.warnings());
        progress.log(MetamodelImportAnalysisStage.PARSING, MetamodelImportActivitySeverity.INFO, "IMPORT_SOURCE_PARSED", "The source structure was read", parsingDetails);
        logWarnings(progress, MetamodelImportAnalysisStage.PARSING, imported.warnings());
        progress.stageCompleted(MetamodelImportAnalysisStage.PARSING, "Source parsing completed", parsingDetails);

        progress.stageStarted(MetamodelImportAnalysisStage.NORMALIZATION, "Normalizing identifiers, types and relations", describeMetamodel("input", imported.metamodel()));
        ImportedMetamodelNormalizer.NormalizedImport normalized = normalizer.normalize(imported, source, format, options);
        String normalizationDetails = describeMetamodel("normalized", normalized.metamodel()) + System.lineSeparator() + describeWarnings("normalizationWarnings", normalized.warnings());
        progress.log(MetamodelImportAnalysisStage.NORMALIZATION, MetamodelImportActivitySeverity.INFO, "IMPORT_MODEL_NORMALIZED", "Identifiers, types and relations were normalized", normalizationDetails);
        logWarnings(progress, MetamodelImportAnalysisStage.NORMALIZATION, normalized.warnings());
        progress.stageCompleted(MetamodelImportAnalysisStage.NORMALIZATION, "Normalization completed", normalizationDetails);

        progress.stageStarted(MetamodelImportAnalysisStage.DIAGRAM, "Preparing the editable diagram", "preserveLayout=" + options.preserveLayout() + System.lineSeparator() + "preserveStyles=" + options.preserveStyles());
        MetamodelDiagramDocument diagram = prepareDiagram(normalized.metamodel(), normalized.diagram(), options);
        String diagramDetails = describeDiagram("editableDiagram", diagram) + System.lineSeparator() + layoutService.describeImportRouting(normalized.metamodel());
        progress.log(MetamodelImportAnalysisStage.DIAGRAM, MetamodelImportActivitySeverity.INFO, "IMPORT_DIAGRAM_PREPARED", "The editable diagram was prepared", diagramDetails);
        progress.stageCompleted(MetamodelImportAnalysisStage.DIAGRAM, "Diagram preparation completed", diagramDetails);

        progress.stageStarted(MetamodelImportAnalysisStage.CANONICALIZATION, "Recovering canonical relations from the imported source", describeDiagram("diagram", diagram));
        MetaModel withRecoveredRelations = relationNormalizer.normalize(normalized.metamodel(), diagram);
        ImportedMetamodelCanonicalization prepared;

        try {
            prepared = ImportedMetamodelCanonicalizer.prepare(withRecoveredRelations);
        } catch (IllegalArgumentException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, exception.getMessage());
        }

        MetaModel canonical = prepared.metamodel();
        TargetConfigurationDocument targets = prepared.targetConfiguration().normalizedFor(canonical);
        String canonicalDetails = describeMetamodel("canonical", canonical) + System.lineSeparator()
                + "ecore.fileName=" + targets.ecore().fileName() + System.lineSeparator()
                + "ecore.namespaceUri=" + targets.ecore().namespaceUri() + System.lineSeparator()
                + "ecore.namespacePrefix=" + targets.ecore().namespacePrefix();
        progress.log(MetamodelImportAnalysisStage.CANONICALIZATION, MetamodelImportActivitySeverity.INFO, "IMPORT_CANONICAL_MODEL_READY", "The canonical MetaModel 2.0 was prepared", canonicalDetails);
        progress.stageCompleted(MetamodelImportAnalysisStage.CANONICALIZATION, "Canonical model preparation completed", canonicalDetails);

        List<TransformationWarning> warnings = new ArrayList<>(imported.warnings());
        warnings.addAll(normalized.warnings());
        progress.stageStarted(MetamodelImportAnalysisStage.VALIDATION, "Validating the imported canonical metamodel", describeMetamodel("validationInput", canonical));
        ValidationReport validation = validator.validate(canonical);

        if(validation.hasErrors()) {
            String message = validation.errors().stream().map(item -> item.code() + ": " + item.message()).collect(java.util.stream.Collectors.joining("; "));
            progress.log(MetamodelImportAnalysisStage.VALIDATION, MetamodelImportActivitySeverity.ERROR, "IMPORT_VALIDATION_FAILED", "The imported metamodel contains validation errors", message);
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Imported metamodel validation failed: " + message);
        }

        validation.warnings().forEach(item -> warnings.add(new TransformationWarning(item.code(), item.path(), item.message())));
        for(var item : validation.warnings()) {
            String details = "path=" + safe(item.path()) + System.lineSeparator() + "message=" + item.message();
            progress.log(MetamodelImportAnalysisStage.VALIDATION, MetamodelImportActivitySeverity.WARNING, item.code(), item.message(), details);
        }

        boolean strict = options.strict() || properties.isStrictByDefault();

        if(strict && containsLossyWarning(warnings)) {
            String message = warnings.stream().filter(this::isLossyWarning).map(item -> item.code() + ": " + item.message()).collect(java.util.stream.Collectors.joining("; "));
            progress.log(MetamodelImportAnalysisStage.VALIDATION, MetamodelImportActivitySeverity.ERROR, "IMPORT_STRICT_MODE_REJECTED", "Strict mode rejected information loss", message);
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Strict metamodel import rejected semantic loss: " + message);
        }

        String validationDetails = "errors=0" + System.lineSeparator() + "warnings=" + validation.warnings().size() + System.lineSeparator() + "strict=" + strict;
        progress.stageCompleted(MetamodelImportAnalysisStage.VALIDATION, "Metamodel validation completed", validationDetails);

        progress.stageStarted(MetamodelImportAnalysisStage.SUMMARY, "Preparing statistics and the final report", describeWarnings("allWarnings", warnings));
        MetamodelImportStatistics statistics = statistics(canonical, diagram);
        MetamodelImportPreview preview = new MetamodelImportPreview(format.id(), format.supportLevel().name(), canonical, targets, diagram, warnings, statistics);
        String summaryDetails = describeStatistics(statistics) + System.lineSeparator() + describeWarnings("warnings", warnings);
        progress.log(MetamodelImportAnalysisStage.SUMMARY, MetamodelImportActivitySeverity.INFO, "IMPORT_SUMMARY_READY", "The analysis summary was prepared", summaryDetails);
        progress.stageCompleted(MetamodelImportAnalysisStage.SUMMARY, "Analysis summary completed", summaryDetails);
        return new ProcessedImport(canonical, targets, preview);
    }

    /** Descripción: Ejecuta la operación «logWarnings» correspondiente a la responsabilidad de ProcessedImport. Entradas: progress: dato de entrada necesario para la operación; stage: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void logWarnings(MetamodelImportProgressListener progress, MetamodelImportAnalysisStage stage, List<TransformationWarning> warnings) {
        for(TransformationWarning warning : warnings) {
            MetamodelImportActivitySeverity severity = MetamodelImportActivitySeverity.WARNING;

            if(warning.code().startsWith("IMPORT_INFO_")) {
                severity = MetamodelImportActivitySeverity.INFO;
            } else if(isLossyWarning(warning)) {
                severity = MetamodelImportActivitySeverity.INFORMATION_LOSS;
            }

            String details = "path=" + safe(warning.path()) + System.lineSeparator() + "message=" + warning.message();
            progress.log(stage, severity, warning.code(), warning.message(), details);
        }
    }

    /** Descripción: Ejecuta la operación «describeOptions» correspondiente a la responsabilidad de ProcessedImport. Entradas: options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String describeOptions(MetamodelImportOptions options) {
        return "formatId=" + safe(options.formatId()) + System.lineSeparator() + "targetId=" + safe(options.targetId()) + System.lineSeparator() + "overwrite=" + options.overwrite() + System.lineSeparator() + "preserveLayout=" + options.preserveLayout() + System.lineSeparator() + "preserveStyles=" + options.preserveStyles() + System.lineSeparator() + "strict=" + options.strict();
    }

    /** Descripción: Ejecuta la operación «describeSource» correspondiente a la responsabilidad de ProcessedImport. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String describeSource(MetamodelImportSource source) {
        return "fileName=" + safe(source.fileName()) + System.lineSeparator() + "mediaType=" + safe(source.mediaType()) + System.lineSeparator() + "sizeBytes=" + source.content().length;
    }

    /** Descripción: Ejecuta la operación «describeFormat» correspondiente a la responsabilidad de ProcessedImport. Entradas: format: dato de entrada necesario para la operación; importer: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String describeFormat(MetamodelImportFormat format, MetamodelImporter importer) {
        return "formatId=" + format.id() + System.lineSeparator() + "displayName=" + format.displayName() + System.lineSeparator() + "category=" + format.category() + System.lineSeparator() + "supportLevel=" + format.supportLevel() + System.lineSeparator() + "importer=" + importer.getClass().getName();
    }

    /** Descripción: Ejecuta la operación «describeMetamodel» correspondiente a la responsabilidad de ProcessedImport. Entradas: prefix: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String describeMetamodel(String prefix, MetaModel metamodel) {
        if(metamodel == null) {
            return prefix + "=null";
        }

        int attributes = metamodel.classes().stream().mapToInt(item -> item.attributes().size()).sum();
        return prefix + ".id=" + safe(metamodel.id()) + System.lineSeparator() + prefix + ".classes=" + metamodel.classes().size() + System.lineSeparator() + prefix + ".enums=" + metamodel.enums().size() + System.lineSeparator() + prefix + ".dataTypes=" + metamodel.dataTypes().size() + System.lineSeparator() + prefix + ".attributes=" + attributes + System.lineSeparator() + prefix + ".relations=" + metamodel.relations().size();
    }

    /** Descripción: Ejecuta la operación «describeDiagram» correspondiente a la responsabilidad de ProcessedImport. Entradas: prefix: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String describeDiagram(String prefix, MetamodelDiagramDocument diagram) {
        if(diagram == null) {
            return prefix + "=null";
        }

        return prefix + ".nodes=" + diagram.nodes().size() + System.lineSeparator() + prefix + ".edges=" + diagram.edges().size();
    }

    /** Descripción: Ejecuta la operación «describeWarnings» correspondiente a la responsabilidad de ProcessedImport. Entradas: prefix: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String describeWarnings(String prefix, List<TransformationWarning> warnings) {
        long losses = warnings.stream().filter(this::isLossyWarning).count();
        return prefix + ".total=" + warnings.size() + System.lineSeparator() + prefix + ".informationLoss=" + losses;
    }

    /** Descripción: Ejecuta la operación «describeStatistics» correspondiente a la responsabilidad de ProcessedImport. Entradas: statistics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String describeStatistics(MetamodelImportStatistics statistics) {
        return "classes=" + statistics.classes() + System.lineSeparator() + "enums=" + statistics.enums() + System.lineSeparator() + "dataTypes=" + statistics.dataTypes() + System.lineSeparator() + "attributes=" + statistics.attributes() + System.lineSeparator() + "relations=" + statistics.relations() + System.lineSeparator() + "diagramNodes=" + statistics.diagramNodes() + System.lineSeparator() + "diagramEdges=" + statistics.diagramEdges();
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de ProcessedImport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safe(String value) {
        return value == null ? "" : value;
    }

    /** Descripción: Ejecuta la operación «prepareDiagram» correspondiente a la responsabilidad de ProcessedImport. Entradas: metamodel: dato de entrada necesario para la operación; importedDiagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDiagramDocument prepareDiagram(MetaModel metamodel, MetamodelDiagramDocument importedDiagram, MetamodelImportOptions options) {
        MetamodelDiagramDocument diagram;

        if(!options.preserveLayout() || importedDiagram == null || importedDiagram.nodes().isEmpty()) {
            diagram = layoutService.layout(metamodel);
        } else {
            diagram = importedDiagram;
        }

        if(!options.preserveStyles()) {
            diagram = layoutService.withoutStyles(diagram);
        }

        diagram = layoutService.applyImportRoutingPolicy(metamodel, diagram);
        return new MetamodelDiagramDocument(metamodel.id(), diagram.nodes(), diagram.edges());
    }

    /** Descripción: Obtiene la información correspondiente a «resolveFormat» dentro de ProcessedImport. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportFormat resolveFormat(MetamodelImportSource source, MetamodelImportOptions options) {
        if(!"auto".equalsIgnoreCase(options.formatId())) {
            return MetamodelImportFormat.fromId(options.formatId());
        }

        if(!properties.isAutoDetect()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Automatic metamodel import detection is disabled");
        }

        return detector.detect(source);
    }

    /** Descripción: Valida las condiciones necesarias para «ensureEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureEnabled() {
        if(!properties.isEnabled()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel import is disabled");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateContent» y rechaza entradas no válidas. Entradas: content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateContent(byte[] content) {
        if(content == null || content.length == 0) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel import file must not be empty");
        }

        if(content.length > properties.getMaxFileSizeBytes()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel import file exceeds the configured size limit");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «ensureAllowed» y rechaza entradas no válidas. Entradas: format: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureAllowed(MetamodelImportFormat format) {
        if(!isAllowed(format)) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel import format is disabled by configuration: " + format.id());
        }
    }

    /** Descripción: Evalúa la condición «isAllowed» sin modificar el estado externo. Entradas: format: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isAllowed(MetamodelImportFormat format) {
        return properties.getAllowedFormats().stream().anyMatch(value -> value.equalsIgnoreCase(format.id()));
    }

    /** Descripción: Ejecuta la operación «indexImporters» correspondiente a la responsabilidad de ProcessedImport. Entradas: availableImporters: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<MetamodelImportFormat, MetamodelImporter> indexImporters(List<MetamodelImporter> availableImporters) {
        Map<MetamodelImportFormat, MetamodelImporter> result = new EnumMap<>(MetamodelImportFormat.class);

        for(MetamodelImporter importer : availableImporters) {
            if(result.put(importer.format(), importer) != null) {
                throw new IllegalStateException("Duplicate metamodel importer registration: " + importer.format().id());
            }
        }

        return result;
    }

    /** Descripción: Evalúa la condición «containsLossyWarning» sin modificar el estado externo. Entradas: warnings: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean containsLossyWarning(List<TransformationWarning> warnings) {
        return warnings.stream().anyMatch(this::isLossyWarning);
    }

    /** Descripción: Evalúa la condición «isLossyWarning» sin modificar el estado externo. Entradas: warning: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isLossyWarning(TransformationWarning warning) {
        return warning.code().startsWith("IMPORT_LOSSY") || warning.code().startsWith("IMPORT_UNRESOLVED");
    }

    /** Descripción: Ejecuta la operación «statistics» correspondiente a la responsabilidad de ProcessedImport. Entradas: metamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportStatistics statistics(MetaModel metamodel, MetamodelDiagramDocument diagram) {
        int attributes = metamodel.classes().stream().mapToInt(item -> item.attributes().size()).sum();
        return new MetamodelImportStatistics(metamodel.classes().size(), metamodel.enums().size(), metamodel.dataTypes().size(), attributes, metamodel.relations().size(), diagram.nodes().size(), diagram.edges().size());
    }

    private record ProcessedImport(MetaModel metamodel, TargetConfigurationDocument targets, MetamodelImportPreview preview) {
    }
}
