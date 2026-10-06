package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

/** Validates the generated feature-based Eclipse Modeling distribution before delivery. */
@Component
public class EclipseModelingDistributionProjectStructureValidator implements TechnologyProjectStructureValidator {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de EclipseModelingDistributionProjectStructureValidator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.ECLIPSE_MODELING_DISTRIBUTION;}
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<TechnologyValidationStepResult> validate(Path root, TechnologyCompilationResult compilation){
        return List.of(
                TechnologyProjectValidationSupport.check("eclipse-distribution-parent", "Parse Tycho reactor", () ->
                        TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root, "/pom.xml", "Eclipse distribution parent pom"))),
                TechnologyProjectValidationSupport.check("eclipse-distribution-target", "Parse target platform", () -> {
                    String value=TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root, ".target", "Eclipse target definition"));
                    TechnologyProjectValidationSupport.requireContains(value,"org.eclipse.emf.ecore","Eclipse target definition");
                    boolean visual=value.contains("org.eclipse.gmf.runtime.notation") || value.contains("org.eclipse.sirius");
                    if(visual){
                        TechnologyProjectValidationSupport.requireContains(value,"org.eclipse.gmf.runtime.notation","Eclipse target definition");
                        TechnologyProjectValidationSupport.requireContains(value,"org.eclipse.gmf.runtime.diagram.ui","Eclipse target definition");
                        TechnologyProjectValidationSupport.requireContains(value,"org.eclipse.sirius","Eclipse target definition");
                        TechnologyProjectValidationSupport.requireContains(value,"org.eclipse.sirius.diagram.ui","Eclipse target definition");
                    }
                    if(value.contains("org.eclipse.xtext")){
                        TechnologyProjectValidationSupport.requireContains(value,"org.eclipse.xtext.ui","Eclipse target definition");
                    }
                    TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root, ".target", "Eclipse target definition"));
                }),
                TechnologyProjectValidationSupport.check("eclipse-distribution-runtime-plugin", "Validate HYDRA runtime plug-in", () -> {
                    String manifest=TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root,"runtime-plugin/META-INF/MANIFEST.MF","runtime plug-in manifest"));
                    TechnologyProjectValidationSupport.requireContains(manifest,"org.eclipse.ui","runtime plug-in manifest");
                    TechnologyProjectValidationSupport.requireContains(manifest,"org.eclipse.emf.edit.ui","runtime plug-in manifest");
                    String target=TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root,".target","Eclipse target definition"));
                    if(target.contains("org.eclipse.sirius")){
                        TechnologyProjectValidationSupport.requireContains(manifest,"org.eclipse.gmf.runtime.diagram.ui","runtime plug-in manifest");
                        TechnologyProjectValidationSupport.requireContains(manifest,"org.eclipse.sirius.diagram.ui","runtime plug-in manifest");
                    }
                    if(target.contains("org.eclipse.xtext")){
                        TechnologyProjectValidationSupport.requireContains(manifest,"org.eclipse.xtext","runtime plug-in manifest");
                        TechnologyProjectValidationSupport.requireContains(manifest,"org.eclipse.xtext.ui","runtime plug-in manifest");
                    }
                    String plugin=TechnologyProjectValidationSupport.text(TechnologyProjectValidationSupport.requireSuffix(root,"runtime-plugin/plugin.xml","runtime plugin.xml"));
                    TechnologyProjectValidationSupport.requireContains(plugin,".runtime.toggle","runtime plugin.xml");
                    TechnologyProjectValidationSupport.requireContains(plugin,"toolbar:org.eclipse.ui.main.toolbar","runtime plugin.xml");
                    TechnologyProjectValidationSupport.requireSuffix(root,"HydraPortableRuntimeBridge.java","portable Eclipse runtime bridge");
                    TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root,"runtime-plugin/plugin.xml","runtime plugin.xml"));
                }),
                TechnologyProjectValidationSupport.check("eclipse-distribution-model", "Parse projected Ecore and canonical activation", () -> {
                    TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root,".ecore","projected Ecore"));
                    TechnologyProjectValidationSupport.json(TechnologyProjectValidationSupport.requireSuffix(root,"runtime-plugin/hydra/runtime-activation.json","runtime activation descriptor"));
                }),
                TechnologyProjectValidationSupport.check("eclipse-distribution-product", "Parse feature and product descriptors", () -> {
                    TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root,"feature/feature.xml","Eclipse feature"));
                    TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root,"product/hydra-modeling.product","Eclipse product"));
                    TechnologyProjectValidationSupport.xml(TechnologyProjectValidationSupport.requireSuffix(root,"product/category.xml","p2 category"));
                })
        );
    }
}
