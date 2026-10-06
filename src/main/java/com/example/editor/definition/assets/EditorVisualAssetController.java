package com.example.editor.definition.assets;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint de solo lectura para figuras visuales canónicas de editores. */
@RestController
@RequestMapping("/api/editor-assets")
public class EditorVisualAssetController {
    private final EditorVisualAssetService assets;

    /** Inicializa el endpoint con el almacén validado. */
    /** Descripción: Inicializa una instancia de EditorVisualAssetController con sus dependencias y datos de entrada. Entradas: assets: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorVisualAssetController(EditorVisualAssetService assets) { this.assets = assets; }

    /** Sirve una imagen canónica en línea con tipo y políticas de seguridad estrictos. */
    @GetMapping
    /** Descripción: Ejecuta la operación «visual» correspondiente a la responsabilidad de EditorVisualAssetController. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ByteArrayResource> visual(@RequestParam String path) {
        EditorVisualAssetService.EditorVisualAsset asset = assets.read(path);
        byte[] content = asset.content();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.mediaType())).contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(asset.key(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src data:; style-src 'unsafe-inline'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(new ByteArrayResource(content));
    }
}
