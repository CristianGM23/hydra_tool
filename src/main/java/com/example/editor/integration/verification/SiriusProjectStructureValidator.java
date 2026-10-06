package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

/** Comprueba que el plug-in Sirius es importable como proyecto Eclipse/Tycho. */
@Component
public class SiriusProjectStructureValidator implements TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de SiriusProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.SIRIUS_DESKTOP;}
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<TechnologyValidationStepResult> validate(Path root, TechnologyCompilationResult compilation){
        return List.of(
                TechnologyProjectValidationSupport.check("sirius-manifest", "Validate OSGi manifest", () -> {
                    String value = TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root, "META-INF/MANIFEST.MF", "OSGi manifest"));
                    TechnologyProjectValidationSupport.requireContains(value, "Bundle-SymbolicName", "OSGi manifest");
                    TechnologyProjectValidationSupport.requireContains(value, "org.eclipse.sirius", "OSGi manifest");
                }),
                TechnologyProjectValidationSupport.check("sirius-odesign", "Parse Sirius viewpoint specification", () -> TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root, ".odesign", "Sirius .odesign"))),
                TechnologyProjectValidationSupport.check("sirius-ecore", "Parse projected Ecore", () -> TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root, ".ecore", "Sirius Ecore"))),
                TechnologyProjectValidationSupport.check("sirius-build", "Parse Tycho descriptor", () -> TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root, "/pom.xml", "Sirius pom.xml")))
        );
    }
}
