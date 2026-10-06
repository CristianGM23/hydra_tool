package com.example.editor.registry.federation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.example.editor.registry.HydraDistributionRegistryService;
import com.example.editor.registry.HydraRegistryEntry;
import com.example.editor.registry.HydraRegistryIndex;
import com.example.editor.persistence.support.HydraPersistenceSupport;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** BB manual and offline synchronization of signed AW catalog indexes. */
@Service
public class HydraCatalogFederationService {
    private final HydraDistributionRegistryService registry; private final HydraCatalogFederationProperties properties;
    private final ObjectMapper mapper; private final ObjectWriter writer;
    /** Descripción: Inicializa una instancia de HydraCatalogFederationService con sus dependencias y datos de entrada. Entradas: registry: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraCatalogFederationService(HydraDistributionRegistryService registry,
            HydraCatalogFederationProperties properties,ObjectMapper mapper){this.registry=registry;this.properties=properties;this.mapper=mapper;this.writer=mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);}
    public synchronized HydraFederatedCatalogSource saveSource(HydraFederatedCatalogSource source){
        if(source==null)throw new IllegalArgumentException("Federated catalog source is required");
        String id=HydraPersistenceSupport.safeId(source.sourceId(),"sourceId");
        String pin=HydraPersistenceSupport.text(source.expectedIndexSha256());
        if(!pin.isBlank()&&!pin.matches("(?i)[a-f0-9]{64}"))throw new IllegalArgumentException("expectedIndexSha256 must be a SHA-256 value");
        HydraFederatedCatalogSource safe=new HydraFederatedCatalogSource(id,source.displayName(),source.trustLevel(),pin,source.enabled(),source.annotations(),Instant.now());
        write(sourcePath(id),safe);HydraPersistenceSupport.trim(sourcesRoot(),properties.getMaximumSources());return safe;
    }
    /** Descripción: Ejecuta la operación «sources» correspondiente a la responsabilidad de HydraCatalogFederationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraFederatedCatalogSource> sources(){return readAll(sourcesRoot(),HydraFederatedCatalogSource.class).stream().sorted(Comparator.comparing(HydraFederatedCatalogSource::sourceId)).toList();}
    public synchronized HydraCatalogSyncReport synchronize(HydraCatalogSyncRequest request){
        if(request==null||request.index()==null)throw new IllegalArgumentException("Federated catalog index is required");
        HydraFederatedCatalogSource source=findSource(HydraPersistenceSupport.safeId(request.sourceId(),"sourceId"));
        List<String> diagnostics=new ArrayList<>(); List<HydraCatalogSyncConflict> conflicts=new ArrayList<>();
        List<String> added=new ArrayList<>(),updated=new ArrayList<>(),unchanged=new ArrayList<>(),rejected=new ArrayList<>();
        validateIndex(request.index());
        if(!source.enabled())diagnostics.add("Catalog source is disabled");
        if(source.trustLevel()==HydraCatalogTrustLevel.BLOCKED)diagnostics.add("Catalog source is blocked");
        if(!source.expectedIndexSha256().isBlank()&&!source.expectedIndexSha256().equalsIgnoreCase(request.index().indexSha256())){
            diagnostics.add("Catalog index does not match the pinned checksum");
            conflicts.add(new HydraCatalogSyncConflict(source.sourceId(),"","",HydraCatalogConflictKind.INDEX_PIN_MISMATCH,"REJECT","Pinned catalog checksum mismatch"));
        }
        Map<String,HydraRegistryEntry> localByKey=new LinkedHashMap<>();
        for(HydraRegistryEntry entry:registry.entries())localByKey.put(key(entry),entry);
        List<HydraRegistryEntry> accepted=new ArrayList<>();
        for(HydraRegistryEntry incoming:request.index().entries()){
            HydraRegistryEntry local=localByKey.get(key(incoming));
            if(local==null){added.add(incoming.entryId());accepted.add(withSource(incoming,source,request.index().indexSha256()));continue;}
            if(local.catalogSignatureSha256().equals(incoming.catalogSignatureSha256())&&local.attestationSha256().equals(incoming.attestationSha256())){unchanged.add(incoming.entryId());continue;}
            boolean overwrite=local.entryId().equals(incoming.entryId())&&source.trustLevel()==HydraCatalogTrustLevel.TRUSTED&&request.overwriteTrustedConflicts();
            conflicts.add(new HydraCatalogSyncConflict(key(incoming),local.entryId(),incoming.entryId(),HydraCatalogConflictKind.VERSION_COLLISION,overwrite?"USE_INCOMING":"KEEP_LOCAL","The same product version has different provenance evidence"));
            if(overwrite){updated.add(incoming.entryId());accepted.add(withSource(incoming,source,request.index().indexSha256()));}else rejected.add(incoming.entryId());
        }
        boolean applicable=diagnostics.isEmpty()&&source.trustLevel()!=HydraCatalogTrustLevel.BLOCKED;
        boolean applied=request.apply()&&applicable;
        if(applied&&!accepted.isEmpty())registry.importIndex(index(accepted));
        String fingerprint=HydraPersistenceSupport.fingerprint(writer,Map.of("sourceId",source.sourceId(),"indexSha256",request.index().indexSha256(),"accepted",accepted.stream().map(HydraRegistryEntry::entryId).sorted().toList(),"conflicts",conflicts));
        HydraCatalogSyncReport report=new HydraCatalogSyncReport("catalog-sync-"+fingerprint.substring(0,20),source.sourceId(),source.trustLevel(),request.index().indexSha256(),added,updated,unchanged,rejected,conflicts,applied,diagnostics,Instant.now());
        write(reportPath(report.reportId()),report);HydraPersistenceSupport.trim(reportsRoot(),properties.getMaximumReports());return report;
    }
    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de HydraCatalogFederationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraCatalogSyncReport> reports(){return readAll(reportsRoot(),HydraCatalogSyncReport.class).stream().sorted(Comparator.comparing(HydraCatalogSyncReport::synchronizedAt).reversed()).toList();}
    /** Descripción: Ejecuta la operación «report» correspondiente a la responsabilidad de HydraCatalogFederationService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraCatalogSyncReport report(String id){return read(reportPath(HydraPersistenceSupport.safeId(id,"reportId")),HydraCatalogSyncReport.class,"Catalog synchronization report was not found");}
    /** Descripción: Obtiene la información correspondiente a «findSource» dentro de HydraCatalogFederationService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private HydraFederatedCatalogSource findSource(String id){return sources().stream().filter(value->value.sourceId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Federated catalog source was not found"));}
    /** Descripción: Valida las condiciones necesarias para «validateIndex» y rechaza entradas no válidas. Entradas: index: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateIndex(HydraRegistryIndex index){String expected=HydraPersistenceSupport.fingerprint(writer,Map.of("schemaVersion",index.schemaVersion(),"entries",index.entries()));if(!expected.equals(index.indexSha256()))throw new IllegalArgumentException("Catalog index checksum does not match its entries");}
    /** Descripción: Ejecuta la operación «index» correspondiente a la responsabilidad de HydraCatalogFederationService. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private HydraRegistryIndex index(List<HydraRegistryEntry> entries){List<HydraRegistryEntry> ordered=entries.stream().sorted(Comparator.comparing(HydraRegistryEntry::entryId)).toList();return new HydraRegistryIndex("1.0",Instant.now(),ordered,HydraPersistenceSupport.fingerprint(writer,Map.of("schemaVersion","1.0","entries",ordered)));}
    /** Descripción: Ejecuta la operación «withSource» correspondiente a la responsabilidad de HydraCatalogFederationService. Entradas: entry: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; indexSha: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraRegistryEntry withSource(HydraRegistryEntry entry,HydraFederatedCatalogSource source,String indexSha){Map<String,String> annotations=new LinkedHashMap<>(entry.annotations());annotations.put("federation.sourceId",source.sourceId());annotations.put("federation.trust",source.trustLevel().name());annotations.put("federation.indexSha256",indexSha);return new HydraRegistryEntry(entry.entryId(),entry.reportId(),entry.productId(),entry.version(),entry.metamodelId(),entry.channel(),entry.capabilities(),entry.attestationSha256(),entry.catalogSignatureSha256(),entry.registeredAt(),annotations);}
    /** Descripción: Ejecuta la operación «key» correspondiente a la responsabilidad de HydraCatalogFederationService. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String key(HydraRegistryEntry entry){return entry.productId()+"@"+entry.version();}
    /** Descripción: Obtiene la información correspondiente a «readAll» dentro de HydraCatalogFederationService. Entradas: root: ruta o ubicación utilizada por la operación; type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> List<T> readAll(Path root,Class<T> type){List<T> values=new ArrayList<>();for(Path path:HydraPersistenceSupport.jsonFiles(root))try{values.add(mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path),type));}catch(Exception ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/registry/federation/HydraCatalogFederationService.java",ignored);}return values;}
    /** Descripción: Obtiene la información correspondiente a «read» dentro de HydraCatalogFederationService. Entradas: path: ruta o ubicación utilizada por la operación; type: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> T read(Path path,Class<T> type,String message){try{return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path),type);}catch(Exception exception){throw new IllegalArgumentException(message,exception);}}
    /** Descripción: Actualiza el estado gestionado por HydraCatalogFederationService mediante la operación «write». Entradas: path: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void write(Path path,Object value){try{HydraPersistenceSupport.atomicWrite(path,writer.writeValueAsBytes(value));}catch(Exception exception){throw new IllegalStateException("Could not persist federated catalog evidence",exception);}}
    /** Descripción: Ejecuta la operación «sourcesRoot» correspondiente a la responsabilidad de HydraCatalogFederationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path sourcesRoot(){return properties.getStorageRoot().resolve("sources");} /** Descripción: Ejecuta la operación «reportsRoot» correspondiente a la responsabilidad de HydraCatalogFederationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportsRoot(){return properties.getStorageRoot().resolve("reports");}
    /** Descripción: Ejecuta la operación «sourcePath» correspondiente a la responsabilidad de HydraCatalogFederationService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path sourcePath(String id){return sourcesRoot().resolve(id+".json");} /** Descripción: Ejecuta la operación «reportPath» correspondiente a la responsabilidad de HydraCatalogFederationService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportPath(String id){return reportsRoot().resolve(id+".json");}
}
