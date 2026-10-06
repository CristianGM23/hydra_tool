package com.example.editor.standalone.distribution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Atomically materializes planner artifacts without writing outside the editor root. */
@Service
public class StandaloneDistributionMaterializer {
    /** Descripción: Ejecuta la operación «materialize» correspondiente a la responsabilidad de StandaloneDistributionMaterializer. Entradas: editorRoot: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StandaloneDistributionMaterializationResult materialize(Path editorRoot,
            List<StandaloneDistributionArtifact> artifacts){
        Path root = Objects.requireNonNull(editorRoot, "editorRoot").toAbsolutePath().normalize();
        List<StandaloneDistributionArtifact> ordered = Objects.requireNonNull(artifacts, "artifacts").stream()
                .sorted(Comparator.comparing(StandaloneDistributionArtifact::path)).toList();
        Map<String, String> hashes = new LinkedHashMap<>();
        try{
            Files.createDirectories(root);
            for(StandaloneDistributionArtifact artifact : ordered){
                Path target = safeResolve(root, artifact.path()); byte[] content = artifact.content().getBytes(StandardCharsets.UTF_8);
                atomicWrite(target, content); hashes.put(artifact.path(), hash(content));
            }
            return new StandaloneDistributionMaterializationResult(root, ordered.stream().map(StandaloneDistributionArtifact::path).toList(), hashes);
        }catch(IOException exception){throw new IllegalStateException("Could not materialize standalone distribution", exception);}
    }
    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de StandaloneDistributionMaterializer. Entradas: target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path target, byte[] content) throws IOException{
        Files.createDirectories(target.getParent()); Path temporary = Files.createTempFile(target.getParent(), ".hydra-dist-", ".tmp");
        try{
            Files.write(temporary, content);
            try{Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException exception){Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    /** Descripción: Ejecuta la operación «safeResolve» correspondiente a la responsabilidad de StandaloneDistributionMaterializer. Entradas: root: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static Path safeResolve(Path root, String value){
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        if(normalized.isBlank() || normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")){
            throw new IllegalArgumentException("Unsafe distribution path: " + value);
        }
        Path result = root.resolve(normalized).normalize();
        if(!result.startsWith(root)){throw new IllegalArgumentException("Distribution path escapes editor root: " + value);}
        Path current = root;
        for(Path segment : root.relativize(result)){
            current = current.resolve(segment);
            if(Files.isSymbolicLink(current)){throw new IllegalArgumentException("Distribution path crosses a symbolic link: " + value);}
        }
        return result;
    }
    /** Descripción: Evalúa la condición «hash» sin modificar el estado externo. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String hash(byte[] content){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}}
}
