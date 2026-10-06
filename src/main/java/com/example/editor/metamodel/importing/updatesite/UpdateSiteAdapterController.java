package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo declarativo de adaptadores de extensiones Update Site. */
@RestController
@RequestMapping("/api/update-site-imports")
public class UpdateSiteAdapterController {
    private final UpdateSiteExtensionAdapterCatalog catalog; private final UpdateSiteDeclarativeAdapterService service;
    /** Descripción: Inicializa una instancia de UpdateSiteAdapterController con sus dependencias y datos de entrada. Entradas: catalog: dato de entrada necesario para la operación; service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteAdapterController(UpdateSiteExtensionAdapterCatalog catalog,UpdateSiteDeclarativeAdapterService service){this.catalog=catalog;this.service=service;}
    @GetMapping("/extension-adapters") public List<UpdateSiteExtensionAdapterDescriptor> extensionAdapters(){return catalog.descriptors();}
    @PostMapping(value="/extension-adapters/preview",consumes=MediaType.APPLICATION_JSON_VALUE) public UpdateSiteDeclarativeAdapterValidation previewAdapter(@RequestBody byte[] content){return service.preview(content);}
    @PostMapping(value="/extension-adapters/import",consumes=MediaType.APPLICATION_JSON_VALUE) public UpdateSiteDeclarativeAdapterImportResult importAdapter(@RequestBody byte[] content,@RequestParam(defaultValue="false") boolean overwrite){return service.importAdapter(content,overwrite);}
    @GetMapping("/extension-adapters/documents") public List<UpdateSiteDeclarativeAdapterEntry> documents(){return service.listPersisted();}
    @PostMapping(value="/extension-adapters/test",consumes=MediaType.APPLICATION_JSON_VALUE) public UpdateSiteAdapterFixtureResult testAdapter(@RequestBody UpdateSiteAdapterTestRequest request){return service.testFixture(request.document().getBytes(StandardCharsets.UTF_8),request.fixture());}
    @PostMapping(value="/extension-adapters/{adapterId}/activation",consumes=MediaType.APPLICATION_JSON_VALUE) public UpdateSiteDeclarativeAdapterEntry activateAdapter(@PathVariable String adapterId,@RequestBody UpdateSiteAdapterActivationRequest request){return service.setEnabled(adapterId,request.enabled());}
    @GetMapping("/extension-adapters/{adapterId}/versions") public List<UpdateSiteDeclarativeAdapterVersionEntry> adapterVersions(@PathVariable String adapterId){return service.versions(adapterId);}
    @GetMapping("/extension-adapters/{adapterId}/versions/{version}/export") public ResponseEntity<byte[]> exportAdapterVersion(@PathVariable String adapterId,@PathVariable String version){
        byte[] content=service.exportVersion(adapterId,version);String fileName=adapterId.replace(':','-').replaceAll("[^A-Za-z0-9._-]","-")+"-"+version+".hydra-adapter.json";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+fileName+"\"").contentType(MediaType.APPLICATION_JSON).body(content);}
    @GetMapping("/extension-adapters/{adapterId}/export") public ResponseEntity<byte[]> exportAdapter(@PathVariable String adapterId){
        byte[] content=service.exportAdapter(adapterId);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+adapterId.replace(':','-').replaceAll("[^A-Za-z0-9._-]","-")+".hydra-adapter.json\"").contentType(MediaType.APPLICATION_JSON).body(content);}
    @DeleteMapping("/extension-adapters/{adapterId}") public void deleteAdapter(@PathVariable String adapterId){service.delete(adapterId);}
}
