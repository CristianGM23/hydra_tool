package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import tools.jackson.databind.JsonNode;

/** Validates commands, custom editor and host bridges of a generated VS Code extension. */
@Component
public class VsCodeExtensionProjectStructureValidator implements TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de VsCodeExtensionProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.VSCODE_EXTENSION;}
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<TechnologyValidationStepResult> validate(Path root, TechnologyCompilationResult compilation){
        return List.of(
                TechnologyProjectValidationSupport.check("vscode-extension-package", "Validate VS Code extension manifest", () -> {
                    JsonNode json=TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root,"/package.json","VS Code package.json"));
                    if(json.path("engines").path("vscode").asText().isBlank())throw new IllegalStateException("VS Code engine is required");
                    if(!json.path("contributes").path("commands").isArray()||json.path("contributes").path("commands").isEmpty())throw new IllegalStateException("HYDRA commands are required");
                    if(!json.path("contributes").path("customEditors").isArray()||json.path("contributes").path("customEditors").isEmpty())throw new IllegalStateException("HYDRA custom editor contribution is required");
                    String manifest=json.toString();
                    if(manifest.contains("onStartupFinished"))throw new IllegalStateException("Generated extension must not activate on every VS Code startup");
                    TechnologyProjectValidationSupport.requireContains(manifest,"workspaceContains:**/*.","VS Code package.json");
                    TechnologyProjectValidationSupport.requireContains(manifest,"onCustomEditor:","VS Code package.json");
                }),
                TechnologyProjectValidationSupport.check("vscode-extension-runtime", "Validate persistent HYDRA activation gate", () -> {
                    String source=TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root,"src/runtimeActivation.ts","runtime activation source"));
                    TechnologyProjectValidationSupport.requireContains(source,"workspaceState","runtime activation source");
                    TechnologyProjectValidationSupport.requireContains(source,"HYDRA_SEMANTIC_SCOPE_ID","runtime activation source");
                    TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root,"resources/hydra/runtime-activation.json","runtime activation descriptor"));
                }),
                TechnologyProjectValidationSupport.check("vscode-extension-bridges", "Validate Langium, GLSP and Xtext bridge declaration", () -> {
                    JsonNode json=TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root,"resources/hydra-host-bridges.json","host bridge descriptor"));
                    String text=json.toString();
                    TechnologyProjectValidationSupport.requireContains(text,"langium","host bridge descriptor");
                    TechnologyProjectValidationSupport.requireContains(text,"eclipse-glsp","host bridge descriptor");
                    TechnologyProjectValidationSupport.requireContains(text,"xtext","host bridge descriptor");
                }),
                TechnologyProjectValidationSupport.check("vscode-extension-sources", "Validate extension entry point and safe editor fallback", () -> {
                    String extension=TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root,"src/extension.ts","VS Code extension source"));
                    TechnologyProjectValidationSupport.requireContains(extension,"registerCommand","VS Code extension source");
                    TechnologyProjectValidationSupport.requireContains(extension,"registerCustomEditorProvider","VS Code extension source");
                    TechnologyProjectValidationSupport.requireContains(extension,"if(activation.isEnabled())await languages.startXtext()","VS Code extension source");
                    TechnologyProjectValidationSupport.requireSuffix(root,"src/modelEditorProvider.ts","custom editor provider");
                    TechnologyProjectValidationSupport.requireSuffix(root,"src/languageBridge.ts","language bridge");
                    TechnologyProjectValidationSupport.requireSuffix(root,"src/portableRuntimeBridge.ts","portable runtime bridge");
                })
        );
    }
}
