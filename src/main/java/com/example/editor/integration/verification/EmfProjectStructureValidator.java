package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

/** Comprueba que el módulo EMF contiene un Ecore cargable por el runtime EMF real. */
@Component
public class EmfProjectStructureValidator implements TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de EmfProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.EMF;}
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<TechnologyValidationStepResult> validate(Path root, TechnologyCompilationResult compilation){
        return List.of(
                TechnologyProjectValidationSupport.check("emf-pom", "Parse EMF Maven descriptor", () -> TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root, "/pom.xml", "EMF pom.xml"))),
                TechnologyProjectValidationSupport.check("emf-ecore-load", "Load generated Ecore with EMF", () -> loadEcore(TechnologyProjectValidationSupport.requireSuffix(root, ".ecore", "generated Ecore"))),
                TechnologyProjectValidationSupport.check("emf-canonical-source", "Keep canonical metamodel for traceability", () -> TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root, "/hydra/metamodel.json", "canonical metamodel")))
        );
    }
    /** Descripción: Obtiene la información correspondiente a «loadEcore» dentro de EmfProjectStructureValidator. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void loadEcore(Path path){
        ResourceSetImpl resources = new ResourceSetImpl();
        resources.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        Resource resource = resources.getResource(URI.createFileURI(path.toAbsolutePath().toString()), true);
        if(resource.getContents().isEmpty() || !(resource.getContents().get(0) instanceof EPackage ePackage) || ePackage.getName() == null){
            throw new IllegalStateException("Generated Ecore does not contain an EPackage");
        }
    }
}
