package com.example.editor.metamodel.importing.updatesite;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Contenido interno de un repositorio p2.
 *
 * <p>Las instancias creadas por {@link UpdateSiteArchiveReader} conservan los
 * blobs fuera del heap y materializan un valor únicamente cuando un consumidor
 * solicita ese fichero. La vista {@link #files()} expone una vista {@code Map<String, byte[]>} sin forzar lecturas al iterar claves.</p>
 */
final class UpdateSiteRepositoryContent {
    private final Map<String, byte[]> inlineFiles;
    private final Map<String, UpdateSiteStoredArtifact> storedFiles;
    private final UpdateSiteArtifactBlobStore blobs;
    private final Map<String, byte[]> filesView;
    private final String fingerprint;
    private final long totalBytes;

    /** Constructor de conveniencia para contenido ya materializado en memoria. */
    /** Descripción: Inicializa una instancia de UpdateSiteRepositoryContent con sus dependencias y datos de entrada. Entradas: files: archivo o contenido que debe procesarse; fingerprint: dato de entrada necesario para la operación; totalBytes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ UpdateSiteRepositoryContent(Map<String, byte[]> files, String fingerprint, long totalBytes) {
        Map<String, byte[]> copy = new LinkedHashMap<>();
        if(files != null) {
            files.forEach((path, content) -> copy.put(path, content == null ? new byte[0] : content.clone()));
        }
        this.inlineFiles = Collections.unmodifiableMap(copy);
        this.storedFiles = Map.of();
        this.blobs = null;
        this.filesView = new InlineFilesView(this.inlineFiles);
        this.fingerprint = fingerprint == null ? "" : fingerprint;
        this.totalBytes = Math.max(0L, totalBytes);
    }

    /** Constructor lazy para el reader streaming/content-addressed. */
    /** Descripción: Inicializa una instancia de UpdateSiteRepositoryContent con sus dependencias y datos de entrada. Entradas: files: archivo o contenido que debe procesarse; blobs: dato de entrada necesario para la operación; fingerprint: dato de entrada necesario para la operación; totalBytes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ UpdateSiteRepositoryContent(Map<String, UpdateSiteStoredArtifact> files, UpdateSiteArtifactBlobStore blobs,
            String fingerprint, long totalBytes) {
        this.inlineFiles = Map.of();
        this.storedFiles = Collections.unmodifiableMap(new LinkedHashMap<>(files == null ? Map.of() : files));
        this.blobs = java.util.Objects.requireNonNull(blobs, "blobs");
        this.filesView = new StoredFilesView(this.storedFiles, blobs);
        this.fingerprint = fingerprint == null ? "" : fingerprint;
        this.totalBytes = Math.max(0L, totalBytes);
    }

    /** Descripción: Ejecuta la operación «artifacts» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<UpdateSiteRepositoryArtifact> artifacts() {
        if(blobs != null) {
            return storedFiles.entrySet().stream()
                    .map(entry -> new UpdateSiteRepositoryArtifact(entry.getKey(), entry.getValue().size(), entry.getValue().sha256()))
                    .toList();
        }
        return inlineFiles.entrySet().stream()
                .map(entry -> new UpdateSiteRepositoryArtifact(entry.getKey(), entry.getValue().length,
                        UpdateSiteHashing.sha256(entry.getValue())))
                .toList();
    }

    /** Descripción: Ejecuta la operación «files» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Map<String, byte[]> files() {
        return filesView;
    }

    /** Descripción: Ejecuta la operación «paths» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Set<String> paths() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(filesView.keySet()));
    }

    /** Descripción: Ejecuta la operación «fileCount» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ int fileCount() {
        return filesView.size();
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de LazyEntrySet. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ Optional<byte[]> find(String path) {
        byte[] content = filesView.get(path);
        return content == null ? Optional.empty() : Optional.of(content.clone());
    }

    /** Descripción: Ejecuta la operación «matchingSuffix» correspondiente a la responsabilidad de LazyEntrySet. Entradas: suffixes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<Map.Entry<String, byte[]>> matchingSuffix(String... suffixes) {
        List<String> normalized = java.util.Arrays.stream(suffixes == null ? new String[0] : suffixes)
                .map(value -> value == null ? "" : value.toLowerCase(Locale.ROOT)).toList();
        List<Map.Entry<String, byte[]>> matches = new ArrayList<>();
        filesView.keySet().stream().sorted().forEach(path -> {
            String lower = path.toLowerCase(Locale.ROOT);
            if(normalized.stream().anyMatch(lower::endsWith)) {
                byte[] value = filesView.get(path);
                matches.add(Map.entry(path, value == null ? new byte[0] : value.clone()));
            }
        });
        return List.copyOf(matches);
    }

    /** Descripción: Ejecuta la operación «fingerprint» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String fingerprint() {
        return fingerprint;
    }

    /** Descripción: Ejecuta la operación «totalBytes» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ long totalBytes() {
        return totalBytes;
    }

    /** Vista defensiva para el modo in-memory. */
    private static final class InlineFilesView extends AbstractMap<String, byte[]> {
        private final Map<String, byte[]> delegate;
        /** Descripción: Inicializa una instancia de InlineFilesView con sus dependencias y datos de entrada. Entradas: delegate: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private InlineFilesView(Map<String, byte[]> delegate) { this.delegate = delegate; }
        /** Descripción: Obtiene la información correspondiente a «get» dentro de LazyEntrySet. Entradas: key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] get(Object key) {
            byte[] value = delegate.get(key);
            return value == null ? null : value.clone();
        }
        /** Descripción: Evalúa la condición «containsKey» sin modificar el estado externo. Entradas: key: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean containsKey(Object key) { return delegate.containsKey(key); }
        /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int size() { return delegate.size(); }
        /** Descripción: Ejecuta la operación «keySet» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<String> keySet() { return delegate.keySet(); }
        /** Descripción: Ejecuta la operación «entrySet» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<Entry<String, byte[]>> entrySet() {
            return new LazyEntrySet(delegate.keySet(), this::get);
        }
    }

    /** Vista lazy: get/getValue son los únicos puntos que leen el blob. */
    private static final class StoredFilesView extends AbstractMap<String, byte[]> {
        private final Map<String, UpdateSiteStoredArtifact> stored;
        private final UpdateSiteArtifactBlobStore blobs;
        /** Descripción: Inicializa una instancia de StoredFilesView con sus dependencias y datos de entrada. Entradas: stored: dato de entrada necesario para la operación; blobs: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private StoredFilesView(Map<String, UpdateSiteStoredArtifact> stored, UpdateSiteArtifactBlobStore blobs) {
            this.stored = stored;
            this.blobs = blobs;
        }
        /** Descripción: Obtiene la información correspondiente a «get» dentro de LazyEntrySet. Entradas: key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] get(Object key) {
            UpdateSiteStoredArtifact artifact = stored.get(key);
            return artifact == null ? null : blobs.read(artifact);
        }
        /** Descripción: Evalúa la condición «containsKey» sin modificar el estado externo. Entradas: key: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean containsKey(Object key) { return stored.containsKey(key); }
        /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int size() { return stored.size(); }
        /** Descripción: Ejecuta la operación «keySet» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<String> keySet() { return stored.keySet(); }
        /** Descripción: Ejecuta la operación «entrySet» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<Entry<String, byte[]>> entrySet() {
            return new LazyEntrySet(stored.keySet(), this::get);
        }
    }

    @FunctionalInterface
    private interface ValueLookup { byte[] get(String key); }

    /** EntrySet cuya iteración no materializa el value hasta getValue(). */
    private static final class LazyEntrySet extends AbstractSet<Map.Entry<String, byte[]>> {
        private final Set<String> keys;
        private final ValueLookup lookup;
        /** Descripción: Inicializa una instancia de LazyEntrySet con sus dependencias y datos de entrada. Entradas: keys: dato de entrada necesario para la operación; lookup: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private LazyEntrySet(Set<String> keys, ValueLookup lookup) {
            this.keys = keys;
            this.lookup = lookup;
        }
        /** Descripción: Ejecuta la operación «iterator» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Iterator<Map.Entry<String, byte[]>> iterator() {
            Iterator<String> iterator = keys.iterator();
            return new Iterator<>() {
                /** Descripción: Evalúa la condición «hasNext» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean hasNext() { return iterator.hasNext(); }
                /** Descripción: Ejecuta la operación «next» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Map.Entry<String, byte[]> next() {
                    String key = iterator.next();
                    return new Map.Entry<>() {
                        /** Descripción: Obtiene la información correspondiente a «getKey» dentro de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String getKey() { return key; }
                        /** Descripción: Obtiene la información correspondiente a «getValue» dentro de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] getValue() { return lookup.get(key); }
                        /** Descripción: Actualiza el estado gestionado por LazyEntrySet mediante la operación «setValue». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] setValue(byte[] value) { throw new UnsupportedOperationException("read-only"); }
                    };
                }
            };
        }
        /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de LazyEntrySet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int size() { return keys.size(); }
    }
}
