package com.example.editor.policy.bundle;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorChannel;

/** BD versioned policy governance API. */
@RestController
@RequestMapping("/api/policies/bundles")
public class HydraPolicyBundleController {
    private final HydraPolicyBundleService service;
    /** Descripción: Inicializa una instancia de HydraPolicyBundleController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraPolicyBundleController(HydraPolicyBundleService service){this.service=service;}
    /** Descripción: Ejecuta la operación «bundles» correspondiente a la responsabilidad de HydraPolicyBundleController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public List<HydraPolicyBundle> bundles(){return service.bundles();}
    /** Descripción: Actualiza el estado gestionado por HydraPolicyBundleController mediante la operación «save». Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PostMapping public HydraPolicyBundle save(@RequestBody HydraPolicyBundleRequest request){return service.save(request);}
    @PostMapping("/{bundleId}/{version}/simulate") public HydraPolicyBundleSimulationReport simulate(@PathVariable String bundleId,@PathVariable String version,@RequestBody HydraPolicyBundleSimulationRequest request){return service.simulate(bundleId,version,request);}
    @PostMapping("/{bundleId}/{version}/activate") public HydraPolicyBundle activate(@PathVariable String bundleId,@PathVariable String version,@RequestBody HydraPolicyBundleActivationRequest request){return service.activate(bundleId,version,request);}
    @PostMapping("/{bundleId}/{version}/revoke") public HydraPolicyBundle revoke(@PathVariable String bundleId,@PathVariable String version,@RequestBody HydraPolicyBundleRevocationRequest request){return service.revoke(bundleId,version,request);}
    @GetMapping("/simulations") public List<HydraPolicyBundleSimulationReport> simulations(){return service.simulations();}
    @GetMapping("/channels/{channel}/active") public HydraPolicyBundle active(@PathVariable UpdateSiteMirrorChannel channel){return service.active(channel);}
}
