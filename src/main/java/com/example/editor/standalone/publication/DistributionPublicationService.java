package com.example.editor.standalone.publication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.editor.io.AtomicFiles;
import com.example.editor.io.BoundedIo;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Publishes validated distribution manifests atomically and resolves channel updates. */
@Service
public class DistributionPublicationService {
    private final DistributionPublicationValidator validator;
    private final JsonMapper mapper;
    private final Path distributionRoot;
    private final Path publicationRoot;

    /** Descripción: Inicializa una instancia de DistributionPublicationService con sus dependencias y datos de entrada. Entradas: validator: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento; distributionRoot: dato de entrada necesario para la operación; publicationRoot: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DistributionPublicationService(DistributionPublicationValidator validator, JsonMapper mapper,
            @Value("${hydra.publication.distribution-root:data/standalone-editors}") String distributionRoot,
            @Value("${hydra.publication.root:data/publications}") String publicationRoot){
        this.validator = validator; this.mapper = mapper;
        this.distributionRoot = Path.of(distributionRoot).toAbsolutePath().normalize();
        this.publicationRoot = Path.of(publicationRoot).toAbsolutePath().normalize();
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public PublicationValidationReport validate(PublicationRequest request){
        return validator.validate(resolveDistribution(request.distributionDirectory()), request);
    }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de DistributionPublicationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public PublicationResult publish(PublicationRequest request){
        Path sourceRoot = resolveDistribution(request.distributionDirectory());
        PublicationValidationReport validation = validator.validate(sourceRoot, request);
        if(!validation.valid()){throw new IllegalStateException("Publication contains blocking diagnostics");}
        String productToken = token(request.productId());
        String channelToken = request.channel().name().toLowerCase(Locale.ROOT);
        String versionToken = token(request.version());
        Path channelRoot = publicationRoot.resolve(productToken).resolve(channelToken).normalize();
        Path versionRoot = channelRoot.resolve(versionToken).normalize();
        if(!versionRoot.startsWith(publicationRoot)){throw new IllegalArgumentException("Publication path escapes configured root");}
        if(Files.exists(versionRoot)){throw new IllegalStateException("Published versions are immutable: " + request.version());}
        Path staging = channelRoot.resolve("." + versionToken + ".tmp-" + UUID.randomUUID()).normalize();
        List<PublicationArtifact> artifacts = new ArrayList<>();
        try{
            Files.createDirectories(staging);
            for(PublicationArtifactRequest item : request.artifacts()){
                Path source = validator.resolve(sourceRoot, item.relativePath());
                String fileName = source.getFileName().toString();
                Path target = staging.resolve(fileName).normalize();
                if(!target.startsWith(staging)){throw new IllegalArgumentException("Artifact name escapes publication staging");}
                copyAtomic(source, target);
                String base = request.updateBaseUrl().replaceAll("/+$", "");
                String downloadUrl = base.isBlank() ? "" : base + "/" + productToken + "/"
                        + channelToken + "/" + versionToken + "/" + fileName;
                String signature = "";
                if(request.signingMode() == PublicationSigningMode.EXTERNAL_SIGNATURE){
                    Path signatureSource = source.resolveSibling(source.getFileName() + ".sig");
                    signature = fileName + ".sig";
                    copyAtomic(signatureSource, staging.resolve(signature));
                }
                artifacts.add(new PublicationArtifact(item.platform(), fileName, item.mediaType(),
                        Files.size(target), sha256(target), downloadUrl, signature));
            }
            PublicationManifest manifest = new PublicationManifest("1.0", request.productId(), request.version(),
                    request.channel(), request.signingMode(), Instant.now(), artifacts, request.metadata());
            Path stagedManifest = staging.resolve("manifest.json");
            writeAtomic(stagedManifest, manifest);
            Files.createDirectories(channelRoot);
            moveDirectory(staging, versionRoot);
            Path manifestPath = versionRoot.resolve("manifest.json");
            Path channelPath = channelRoot.resolve("latest.json");
            writeAtomic(channelPath, manifest);
            return new PublicationResult(manifest, manifestPath.toString(), channelPath.toString(), validation);
        }catch(IOException exception){
            throw new IllegalStateException("Could not publish distribution artifacts", exception);
        }finally{
            deleteRecursively(staging);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateCheckResult check(UpdateCheckRequest request){
        Path latest = publicationRoot.resolve(token(request.productId()))
                .resolve((request.channel() == null ? PublicationChannel.INTERNAL : request.channel())
                        .name().toLowerCase(Locale.ROOT)).resolve("latest.json").normalize();
        if(!latest.startsWith(publicationRoot) || !Files.isRegularFile(latest)){
            return new UpdateCheckResult(false, request.currentVersion(), "", null, "No release is published for this channel.");
        }
        PublicationManifest manifest;
        try{manifest = mapper.readValue(latest.toFile(), PublicationManifest.class);}
        catch(JacksonException exception){throw new IllegalStateException("Could not parse channel update manifest", exception);}
        boolean newer = HydraSemanticVersion.parse(manifest.version())
                .compareTo(HydraSemanticVersion.parse(request.currentVersion())) > 0;
        PublicationArtifact artifact = manifest.artifacts().stream()
                .filter(item -> item.platform().equalsIgnoreCase(request.platform())).findFirst().orElse(null);
        if(!newer){return new UpdateCheckResult(false, request.currentVersion(), manifest.version(), artifact, "The distribution is up to date.");}
        if(artifact == null){return new UpdateCheckResult(false, request.currentVersion(), manifest.version(), null, "A newer release exists but not for this platform.");}
        return new UpdateCheckResult(true, request.currentVersion(), manifest.version(), artifact, "A verified update is available.");
    }

    /** Descripción: Obtiene la información correspondiente a «resolveDistribution» dentro de DistributionPublicationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveDistribution(String value){
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        if(normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")
                || normalized.contains("../") || normalized.equals(".") || normalized.equals("..")){
            throw new IllegalArgumentException("distributionDirectory must be a safe relative directory");
        }
        Path target = distributionRoot.resolve(normalized).normalize();
        if(!target.startsWith(distributionRoot)){throw new IllegalArgumentException("distributionDirectory escapes configured root");}
        Path current = distributionRoot;
        for(Path segment : distributionRoot.relativize(target)){
            current = current.resolve(segment);
            if(Files.isSymbolicLink(current)){throw new IllegalArgumentException("distributionDirectory crosses a symbolic link");}
        }
        return target;
    }

    /** Descripción: Serializa metadatos y los publica usando la utilidad atómica común; solo se degrada cuando el filesystem declara que ATOMIC_MOVE no está soportado. Entradas: target: destino; value: documento. Salida: no devuelve valor. */ private void writeAtomic(Path target, Object value){
        try{AtomicFiles.writeReplacing(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));}
        catch(IOException | JacksonException exception){throw new IllegalStateException("Could not publish distribution metadata", exception);}
    }

    /** Descripción: Copia un artefacto mediante streaming y publicación atómica con el presupuesto interno que ya gobernaba su posterior lectura para hash. Entradas: source: origen; target: destino. Salida: no devuelve valor. */ private void copyAtomic(Path source, Path target) throws IOException{
        if(Files.isSymbolicLink(source) || !Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new IOException("Publication artifact must be a regular file without symbolic links: " + source);
        AtomicFiles.copyReplacingBounded(source, target, BoundedIo.DEFAULT_INTERNAL_LIMIT_BYTES);
    }

    /** Descripción: Publica el staging completo como una versión nueva e inmutable usando el contrato atómico común. Entradas: source: staging; target: versión final. Salida: no devuelve valor. */ private void moveDirectory(Path source, Path target) throws IOException{
        AtomicFiles.moveNew(source, target);
    }

    /** Descripción: Elimina recursivamente un staging mediante recorrido incremental y sin materializar todas sus rutas. Entradas: root: raíz temporal. Salida: no devuelve valor; la limpieza es best-effort. */ private void deleteRecursively(Path root){
        if(root == null || !Files.exists(root)){return;}
        try{
            Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
                /** Descripción: Elimina cada fichero visitado durante la limpieza best-effort. Entradas: file: fichero; attrs: atributos. Salida: continúa el recorrido. */ @Override public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException { Files.deleteIfExists(file); return java.nio.file.FileVisitResult.CONTINUE; }
                /** Descripción: Elimina cada directorio después de sus hijos y propaga fallos del recorrido. Entradas: dir: directorio; error: fallo previo. Salida: continúa la limpieza. */ @Override public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException { if(error != null) throw error; Files.deleteIfExists(dir); return java.nio.file.FileVisitResult.CONTINUE; }
            });
        }catch(IOException ignored){/* Best-effort cleanup. */}
    }

    /** Descripción: Calcula SHA-256 por streaming sin materializar el artefacto completo y mantiene el mismo presupuesto interno de lectura que la implementación previa. Entradas: path: artefacto publicado. Salida: devuelve el digest hexadecimal. */ private String sha256(Path path){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0L;byte[] buffer=new byte[64*1024];
            try(InputStream input=Files.newInputStream(path)){int read;while((read=input.read(buffer))!=-1){if(read==0)continue;total=Math.addExact(total,read);if(total>BoundedIo.DEFAULT_INTERNAL_LIMIT_BYTES)throw new IOException("Publication artifact exceeds internal size limit");digest.update(buffer,0,read);}}
            return HexFormat.of().formatHex(digest.digest());
        }catch(Exception exception){throw new IllegalStateException("Could not hash publication artifact", exception);}
    }

    /** Descripción: Ejecuta la operación «token» correspondiente a la responsabilidad de DistributionPublicationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String token(String value){
        String result = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if(result.isBlank()){throw new IllegalArgumentException("Publication token must not be blank");}
        return result;
    }
}
