package com.example.editor.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.example.editor.provenance.HydraDistributionProvenanceReport;
import com.example.editor.provenance.HydraDistributionProvenanceService;
import com.example.editor.provenance.HydraSbomComponent;
import com.example.editor.persistence.support.HydraPersistenceSupport;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** AW local catalog with portable indexes and deterministic integrity signatures. */
@Service
public class HydraDistributionRegistryService {
    private final HydraDistributionProvenanceService provenance;
    private final HydraDistributionRegistryProperties properties;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;
    /** Descripción: Inicializa una instancia de HydraDistributionRegistryService con sus dependencias y datos de entrada. Entradas: provenance: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraDistributionRegistryService(HydraDistributionProvenanceService provenance,
            HydraDistributionRegistryProperties properties, ObjectMapper mapper) {
        this.provenance=provenance; this.properties=properties; this.mapper=mapper;
        this.writer=mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
    public synchronized HydraRegistryEntry register(String reportId) {
        HydraDistributionProvenanceReport report=provenance.find(HydraPersistenceSupport.safeId(reportId,"reportId"));
        if(!report.valid()) throw new IllegalStateException("Only valid AV provenance reports can be registered");
        String metamodelId=report.components().stream().filter(item -> "HYDRA_PACKAGE".equals(item.type()))
                .map(item -> item.attributes().getOrDefault("metamodelId", "")).filter(value -> !value.isBlank()).findFirst().orElse("");
        List<String> capabilities=report.components().stream().filter(item -> "CANONICAL_DOCUMENT".equals(item.type()))
                .map(HydraSbomComponent::name).distinct().sorted().toList();
        String entryId=HydraPersistenceSupport.safeId(report.productId()+":"+report.version(),"entryId");
        Map<String,Object> signatureEvidence=signatureEvidence(entryId,report.reportId(),report.productId(),report.version(),
                metamodelId,report.channel(),capabilities,report.attestationSha256());
        HydraRegistryEntry entry=new HydraRegistryEntry(entryId,report.reportId(),report.productId(),report.version(),metamodelId,
                report.channel(),capabilities,report.attestationSha256(),HydraPersistenceSupport.fingerprint(writer,signatureEvidence),Instant.now(),Map.of("source","HYDRA_AV"));
        write(entry); HydraPersistenceSupport.trim(properties.getStorageRoot(),properties.getMaximumEntries()); return entry;
    }
    /** Descripción: Obtiene la información correspondiente a «search» dentro de HydraDistributionRegistryService. Entradas: query: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraRegistryEntry> search(HydraRegistryQuery query) {
        HydraRegistryQuery effective=query==null?new HydraRegistryQuery("","","",null,true):query;
        String needle=HydraPersistenceSupport.text(effective.text()).toLowerCase(Locale.ROOT);
        return entries().stream().filter(entry -> !effective.validOnly() || valid(entry))
                .filter(entry -> effective.channel()==null || entry.channel()==effective.channel())
                .filter(entry -> HydraPersistenceSupport.text(effective.metamodelId()).isBlank() || entry.metamodelId().equals(effective.metamodelId().trim()))
                .filter(entry -> HydraPersistenceSupport.text(effective.capability()).isBlank() || entry.capabilities().stream().anyMatch(value -> value.contains(effective.capability().trim())))
                .filter(entry -> needle.isBlank() || (entry.productId()+" "+entry.version()+" "+entry.metamodelId()+" "+String.join(" ",entry.capabilities())).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(HydraRegistryEntry::registeredAt).reversed()).toList();
    }
    /** Descripción: Ejecuta la operación «exportIndex» correspondiente a la responsabilidad de HydraDistributionRegistryService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public HydraRegistryIndex exportIndex() {
        List<HydraRegistryEntry> entries=entries().stream().filter(this::valid).sorted(Comparator.comparing(HydraRegistryEntry::entryId)).toList();
        Map<String,Object> evidence=Map.of("schemaVersion","1.0","entries",entries);
        return new HydraRegistryIndex("1.0",Instant.now(),entries,HydraPersistenceSupport.fingerprint(writer,evidence));
    }
    public synchronized HydraRegistryIndex importIndex(HydraRegistryIndex index) {
        if(index==null) throw new IllegalArgumentException("Catalog index is required");
        String expected=HydraPersistenceSupport.fingerprint(writer,Map.of("schemaVersion",index.schemaVersion(),"entries",index.entries()));
        if(!expected.equals(index.indexSha256())) throw new IllegalArgumentException("Catalog index checksum does not match its entries");
        for(HydraRegistryEntry entry:index.entries()) { if(!entryIntegrity(entry)) throw new IllegalArgumentException("Catalog entry signature is invalid: "+entry.entryId()); write(entry); }
        HydraPersistenceSupport.trim(properties.getStorageRoot(),properties.getMaximumEntries()); return exportIndex();
    }
    /** Descripción: Ejecuta la operación «entries» correspondiente a la responsabilidad de HydraDistributionRegistryService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraRegistryEntry> entries() {
        List<HydraRegistryEntry> result=new ArrayList<>();
        for(Path path:HydraPersistenceSupport.jsonFiles(properties.getStorageRoot())) try { result.add(mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path),HydraRegistryEntry.class)); }
        catch(Exception ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/registry/HydraDistributionRegistryService.java",ignored);}
        return result;
    }
    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de HydraDistributionRegistryService. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean valid(HydraRegistryEntry entry) {
        try {
            HydraDistributionProvenanceReport report=provenance.find(entry.reportId());
            return entryIntegrity(entry) && report.valid() && report.attestationSha256().equals(entry.attestationSha256());
        } catch(RuntimeException exception) { return false; }
    }
    /** Descripción: Ejecuta la operación «entryIntegrity» correspondiente a la responsabilidad de HydraDistributionRegistryService. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean entryIntegrity(HydraRegistryEntry entry) {
        if(entry==null) return false;
        Map<String,Object> evidence=signatureEvidence(entry.entryId(),entry.reportId(),entry.productId(),entry.version(),
                entry.metamodelId(),entry.channel(),entry.capabilities(),entry.attestationSha256());
        return HydraPersistenceSupport.fingerprint(writer,evidence).equals(entry.catalogSignatureSha256());
    }
    /** Descripción: Ejecuta la operación «signatureEvidence» correspondiente a la responsabilidad de HydraDistributionRegistryService. Entradas: entryId: identificador que delimita el recurso objetivo; reportId: identificador que delimita el recurso objetivo; productId: identificador que delimita el recurso objetivo; version: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; channel: dato de entrada necesario para la operación; capabilities: dato de entrada necesario para la operación; attestation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,Object> signatureEvidence(String entryId,String reportId,String productId,String version,
            String metamodelId,Object channel,List<String> capabilities,String attestation) {
        Map<String,Object> evidence=new LinkedHashMap<>();
        evidence.put("entryId",entryId); evidence.put("reportId",reportId); evidence.put("productId",productId);
        evidence.put("version",version); evidence.put("metamodelId",metamodelId); evidence.put("channel",channel);
        evidence.put("capabilities",capabilities); evidence.put("attestation",attestation); return evidence;
    }
    /** Descripción: Actualiza el estado gestionado por HydraDistributionRegistryService mediante la operación «write». Entradas: entry: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void write(HydraRegistryEntry entry) {
        try { HydraPersistenceSupport.atomicWrite(path(entry.entryId()),writer.writeValueAsBytes(entry)); }
        catch(Exception exception) { throw new IllegalStateException("Could not persist registry entry",exception); }
    }
    /** Descripción: Ejecuta la operación «path» correspondiente a la responsabilidad de HydraDistributionRegistryService. Entradas: entryId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path path(String entryId){return properties.getStorageRoot().resolve(HydraPersistenceSupport.safeId(entryId,"entryId").replace(':','_')+".json");}
}
