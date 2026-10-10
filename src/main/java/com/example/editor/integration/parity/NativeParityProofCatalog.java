package com.example.editor.integration.parity;

import java.util.List;
import java.util.Map;
import com.example.editor.integration.IntegrationTechnology;

/**
 * Generator-owned conservative mapping between native verification steps and the
 * *actual scope* of their fixtures. A build step proves a build, not feature parity.
 * Known fixtures cannot be re-labelled by REST callers.
 */
public final class NativeParityProofCatalog {
    private static final Map<IntegrationTechnology, Map<NativeParityCapability, List<String>>> VERIFIED_SCOPES = Map.ofEntries(
            Map.entry(IntegrationTechnology.EMF, Map.of(
                    NativeParityCapability.NATIVE_BUILD, List.of("compile-emf-module"))),
            Map.entry(IntegrationTechnology.SIRIUS_DESKTOP, Map.of(
                    NativeParityCapability.NATIVE_BUILD, List.of("validate-sirius-plugin"))),
            Map.entry(IntegrationTechnology.XTEXT, Map.of(
                    NativeParityCapability.NATIVE_BUILD, List.of("generate-xtext-language"))),
            Map.entry(IntegrationTechnology.LANGIUM, Map.of(
                    NativeParityCapability.LANGUAGE_SERVICE_SMOKE, List.of("generate-langium-language"))),
            Map.entry(IntegrationTechnology.ECLIPSE_GLSP, Map.of(
                    NativeParityCapability.NATIVE_BUILD, List.of("build-glsp-client", "build-glsp-server"))),
            Map.entry(IntegrationTechnology.BESSER_SDK, Map.of(
                    NativeParityCapability.METAMODEL, List.of("test-besser-package"),
                    NativeParityCapability.EVENT_FLOW, List.of("test-besser-package"),
                    NativeParityCapability.GAMIFICATION, List.of("test-besser-package"),
                    NativeParityCapability.RUNTIME_LIFECYCLE, List.of("test-besser-package"))),
            Map.entry(IntegrationTechnology.BESSER_WEB_MODELING_EDITOR, Map.of(
                    NativeParityCapability.NATIVE_BUILD, List.of("test-besser-wme-extension"))),
            Map.entry(IntegrationTechnology.VSCODE_EXTENSION, Map.of(
                    NativeParityCapability.RUNTIME_LIFECYCLE, List.of("test-vscode-runtime"))),
            Map.entry(IntegrationTechnology.MXGRAPH_DRAWIO, Map.of(
                    NativeParityCapability.GRAPHICAL_BRIDGE_SMOKE,
                    List.of("mxgraph-runtime-smoke", "drawio-plugin-smoke")))
    );

    /** Descripción: Devuelve únicamente IDs de prueba con alcance revisado, nunca comandos aportados por el cliente. Entradas: tecnología y capacidad. Salida: pruebas nativas requeridas o lista vacía. */
    public List<String> proofIds(IntegrationTechnology technology, NativeParityCapability capability) {
        return VERIFIED_SCOPES.getOrDefault(technology, Map.of()).getOrDefault(capability, List.of());
    }
}
