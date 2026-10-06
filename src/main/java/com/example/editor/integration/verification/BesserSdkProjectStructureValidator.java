package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

/** Comprueba que el paquete BESSER SDK es instalable como paquete Python. */
@Component
public class BesserSdkProjectStructureValidator implements TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de BesserSdkProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.BESSER_SDK;}
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<TechnologyValidationStepResult> validate(Path root, TechnologyCompilationResult compilation){
        return List.of(
                TechnologyProjectValidationSupport.check("besser-pyproject", "Validate Python package descriptor", () -> {
                    String value = TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root, "/pyproject.toml", "BESSER pyproject.toml"));
                    TechnologyProjectValidationSupport.requireContains(value, "[project]", "pyproject.toml");
                    TechnologyProjectValidationSupport.requireContains(value, "besser", "pyproject.toml");
                }),
                TechnologyProjectValidationSupport.check("besser-python-sources", "Check generated B-UML package", () -> {
                    long sources = TechnologyProjectValidationSupport.files(root).stream().filter(path -> path.toString().endsWith(".py") && !path.toString().contains("__pycache__")).count();
                    if(sources < 5){throw new IllegalStateException("BESSER SDK export must contain Python sources");}
                    if(TechnologyProjectValidationSupport.files(root).stream().anyMatch(path -> path.toString().endsWith(".pyc"))){throw new IllegalStateException("Generated project must not contain .pyc files");}
                }),
                TechnologyProjectValidationSupport.check("besser-tests", "Check generated pytest acceptance test", () -> TechnologyProjectValidationSupport.requireSuffix(root, "/tests/test_generated_model.py", "BESSER pytest")),
                TechnologyProjectValidationSupport.check("besser-canonical", "Parse canonical metamodel", () -> TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root, "/hydra/metamodel.json", "canonical metamodel")))
        );
    }
}
