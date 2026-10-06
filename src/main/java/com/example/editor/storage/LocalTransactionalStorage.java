package com.example.editor.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import com.example.editor.io.BoundedIo;

/** Proveedor local por defecto para {@link TransactionalStorage}. */
public final class LocalTransactionalStorage implements TransactionalStorage {
    private final Path root;

    /** Descripción: Inicializa una instancia de LocalTransactionalStorage con sus dependencias y datos de entrada. Entradas: root: ruta o ubicación utilizada por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public LocalTransactionalStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de LocalTransactionalStorage. Entradas: relativePath: ruta o ubicación utilizada por la operación; maxBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Optional<StorageDocument> read(Path relativePath, long maxBytes) throws IOException {
        Path path=resolve(relativePath); if(!Files.isRegularFile(path)) return Optional.empty();
        byte[] bytes=BoundedIo.read(path,maxBytes); return Optional.of(new StorageDocument(bytes,version(bytes)));
    }

    /** Descripción: Actualiza el estado gestionado por LocalTransactionalStorage mediante la operación «writeAtomically». Entradas: relativePath: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse; expectedVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public StorageDocument writeAtomically(Path relativePath, byte[] content, String expectedVersion) throws IOException {
        byte[] bytes=content==null?new byte[0]:content.clone(); Path target=resolve(relativePath); verifyExpected(target,expectedVersion);
        Path parent=target.getParent(); if(parent!=null) Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,target.getFileName().toString(),".tmp");
        try {
            Files.write(temporary,bytes,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException exception) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        return new StorageDocument(bytes,version(bytes));
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: relativePath: ruta o ubicación utilizada por la operación; expectedVersion: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean delete(Path relativePath, String expectedVersion) throws IOException {
        Path target=resolve(relativePath); if(!Files.exists(target)) return false; verifyExpected(target,expectedVersion); return Files.deleteIfExists(target);
    }

    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean exists(Path relativePath) { return Files.isRegularFile(resolve(relativePath)); }

    /** Descripción: Valida las condiciones necesarias para «verifyExpected» y rechaza entradas no válidas. Entradas: target: dato de entrada necesario para la operación; expectedVersion: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyExpected(Path target,String expectedVersion) throws IOException {
        if(expectedVersion==null||expectedVersion.isBlank()) return;
        if(!Files.isRegularFile(target)) throw new StorageVersionConflictException("Stored document no longer exists: "+target.getFileName());
        String current=version(BoundedIo.readInternal(target));
        if(!current.equals(expectedVersion)) throw new StorageVersionConflictException("Stored document changed concurrently: "+target.getFileName());
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de LocalTransactionalStorage. Entradas: relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolve(Path relativePath) {
        if(relativePath==null||relativePath.isAbsolute()) throw new IllegalArgumentException("Storage path must be relative");
        Path path=root.resolve(relativePath).normalize(); if(!path.startsWith(root)) throw new IllegalArgumentException("Storage path escapes configured root"); return path;
    }

    /** Descripción: Ejecuta la operación «version» correspondiente a la responsabilidad de LocalTransactionalStorage. Entradas: bytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String version(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
