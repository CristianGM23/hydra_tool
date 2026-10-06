package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

/** Comprueba la estructura mínima de un reactor Xtext utilizable. */
@Component
public class XtextProjectStructureValidator implements TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de XtextProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.XTEXT;}
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<TechnologyValidationStepResult> validate(Path root, TechnologyCompilationResult compilation){
        return List.of(
                TechnologyProjectValidationSupport.check("xtext-parent-pom", "Parse Xtext reactor", () -> TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root, "/pom.xml", "Xtext parent pom"))),
                TechnologyProjectValidationSupport.check("xtext-grammar", "Validate Xtext grammar contract", () -> {
                    String grammar = TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root, ".xtext", "Xtext grammar"));
                    TechnologyProjectValidationSupport.requireContains(grammar, "grammar ", "Xtext grammar");
                    TechnologyProjectValidationSupport.requireContains(grammar, "generate ", "Xtext grammar");
                    TechnologyProjectValidationSupport.requireContains(grammar, "Model:", "Xtext grammar");
                }),
                TechnologyProjectValidationSupport.check("xtext-modules", "Check runtime, IDE and UI modules", () -> {
                    long poms = TechnologyProjectValidationSupport.files(root).stream().filter(path -> path.getFileName().toString().equals("pom.xml")).count();
                    if(poms < 4){throw new IllegalStateException("Xtext reactor must contain parent, runtime, IDE and UI poms");}
                }),
                TechnologyProjectValidationSupport.check("xtext-canonical-source", "Parse canonical metamodel", () -> TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root, "/hydra/metamodel.json", "canonical metamodel")))
        );
    }
}
