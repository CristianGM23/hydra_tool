package com.example.editor.audit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.editor.runtime.StripedKeyLockRegistry;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Store local append-only particionado por día. Las consultas DESC leen cada
 * partición desde el final y no materializan el histórico completo en memoria.
 */
@Component
@ConditionalOnProperty(prefix = "hydra.audit", name = "storage-provider", havingValue = "local-jsonl", matchIfMissing = true)
public class JsonlAuditLogStore implements AuditLogStore, AuditLogMaintenance {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonlAuditLogStore.class);
    private final AuditLogProperties properties;
    private final AuditJsonlRecordCodec codec;
    private final Path root;
    private final AuditRetentionPolicy retentionPolicy;
    private final StripedKeyLockRegistry locks = new StripedKeyLockRegistry(64);
    private final AtomicReference<LocalDate> lastRetentionCleanup = new AtomicReference<>();
    private final ReentrantLock maintenanceLock = new ReentrantLock(true);
    private final AuditPartitionCatalog partitions;
    private final AuditStatisticsCache statisticsCache;
    private final ConcurrentHashMap<Path, Long> canonicalPartitionSizes = new ConcurrentHashMap<>();

    /** Descripción: Inicializa una instancia de JsonlAuditLogStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonlAuditLogStore(AuditLogProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.codec = new AuditJsonlRecordCodec(mapper);
        this.root = properties.getStorageRoot().toAbsolutePath().normalize();
        this.retentionPolicy = new AuditRetentionPolicy(properties);
        this.partitions = new AuditPartitionCatalog(root);
        this.statisticsCache = new AuditStatisticsCache(properties);
    }

    @Override
    /** Descripción: Ejecuta la operación «appendBatch» correspondiente a la responsabilidad de MutableStatistics. Entradas: entries: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void appendBatch(List<AuditEntry> entries) {
        if(entries == null || entries.isEmpty()) return;
        Map<LocalDate, List<AuditEntry>> partitions = new LinkedHashMap<>();
        for(AuditEntry entry : entries) {
            if(entry == null || entry.timestamp() == null) continue;
            LocalDate day = entry.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
            partitions.computeIfAbsent(day, ignored -> new ArrayList<>()).add(entry);
        }
        partitions.forEach(this::appendPartition);
        statisticsCache.clear();
        try {
            maybeCleanupRetention();
            maybeCleanupStoragePressure();
        } catch(RuntimeException exception) {
            // El log ya está persistido: un fallo de mantenimiento no debe provocar
            // que el publisher reintente el batch y duplique entradas.
            LOGGER.warn("Audit maintenance failed after append; it will be retried later: {}", exception.getMessage());
        }
    }

    @Override
    /** Descripción: Ejecuta la operación «query» correspondiente a la responsabilidad de MutableStatistics. Entradas: query: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public AuditPage query(AuditQuery query) {
        AuditQuery effective = requireQuery(query);
        long skip = Math.min(Long.MAX_VALUE - 1L, (long) effective.page() * (long) effective.pageSize());
        int target = effective.pageSize() + 1;
        List<AuditEntry> matches = new ArrayList<>(target);
        long[] skipped = { 0L };
        scan(effective, entry -> {
            if(skipped[0] < skip) { skipped[0]++; return true; }
            if(matches.size() < target) { matches.add(entry); return true; }
            return false;
        });
        boolean hasMore = matches.size() > effective.pageSize();
        if(hasMore) matches.remove(matches.size() - 1);
        return new AuditPage(matches, effective.page(), effective.pageSize(), hasMore);
    }


    @Override
    /** Descripción: Ejecuta la operación «statistics» correspondiente a la responsabilidad de MutableStatistics. Entradas: query: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public AuditStatistics statistics(AuditQuery query) {
        AuditQuery effective = requireQuery(query).withoutPaging();
        AuditStatistics cached = statisticsCache.get(effective);
        if(cached != null) return cached;
        MutableStatistics stats = new MutableStatistics();
        scan(effective, entry -> { stats.accept(entry); return true; });
        AuditStatistics snapshot = stats.snapshot();
        statisticsCache.put(effective, snapshot);
        return snapshot;
    }

    @Override
    /** Descripción: Ejecuta la operación «stream» correspondiente a la responsabilidad de MutableStatistics. Entradas: query: dato de entrada necesario para la operación; sink: dato de entrada necesario para la operación; maximumEntries: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public long stream(AuditQuery query, AuditEntrySink sink, long maximumEntries) {
        if(sink == null) throw new IllegalArgumentException("sink is required");
        AuditQuery effective = requireQuery(query).withoutPaging();
        long maximum = maximumEntries <= 0 ? Long.MAX_VALUE : maximumEntries;
        long[] count = { 0L };
        scan(effective, entry -> {
            if(count[0] >= maximum) return false;
            sink.accept(entry);
            count[0]++;
            return count[0] < maximum;
        });
        return count[0];
    }

    @Override
    /** Descripción: Ejecuta la operación «storageStatus» correspondiente a la responsabilidad de MutableStatistics. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditStorageStatus storageStatus() {
        return new AuditStorageStatus(properties.getStorageProvider(), true, partitions.storageBytes(), properties.getMaximumStorageBytes(), partitions.count(),
                properties.isRetentionEnabled(), properties.getNavigationRetentionDays(), properties.getOpenRetentionDays(),
                properties.getRoutineRetentionDays(), properties.getRetentionDays());
    }

    @Override
    /** Descripción: Ejecuta la operación «maintain» correspondiente a la responsabilidad de MutableStatistics. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditMaintenanceResult maintain() {
        maintenanceLock.lock();
        try {
            long before = partitions.storageBytes();
            CleanupAccumulator cleanup = new CleanupAccumulator();
            if(properties.isRetentionEnabled()) {
                Instant now = Instant.now();
                for(Path path : partitions.all()) rewritePartition(path, entry -> retentionPolicy.isExpired(entry, now), cleanup);
            }
            evictToStorageLimit(cleanup);
            long remaining = partitions.storageBytes();
            statisticsCache.clear();
            return new AuditMaintenanceResult(cleanup.deletedEntries, Math.max(0L, before - remaining), remaining, cleanup.partitionsTouched, "POLICY");
        } finally {
            maintenanceLock.unlock();
        }
    }

    @Override
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: criteria: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public AuditMaintenanceResult delete(AuditDeletionCriteria criteria) {
        if(criteria == null) throw new IllegalArgumentException("deletion criteria is required");
        maintenanceLock.lock();
        try {
            long before = partitions.storageBytes();
            CleanupAccumulator cleanup = new CleanupAccumulator();
            for(Path path : partitions.all()) rewritePartition(path, entry -> matchesDeletion(entry, criteria), cleanup);
            long remaining = partitions.storageBytes();
            statisticsCache.clear();
            return new AuditMaintenanceResult(cleanup.deletedEntries, Math.max(0L, before - remaining), remaining, cleanup.partitionsTouched, criteria.mode().name());
        } finally {
            maintenanceLock.unlock();
        }
    }

    @Override
    /** Descripción: Ejecuta la operación «purgeBefore» correspondiente a la responsabilidad de MutableStatistics. Entradas: cutoff: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void purgeBefore(Instant cutoff) {
        if(cutoff == null || !Files.isDirectory(root)) return;
        LocalDate threshold = cutoff.atZone(ZoneOffset.UTC).toLocalDate();
        maintenanceLock.lock();
        try {
            for(Path path : partitions.all()) {
                LocalDate day = partitions.day(path);
                if(day == null || !day.isBefore(threshold)) continue;
                ReentrantLock lock = locks.lockFor(day.toString());
                lock.lock();
                try { Files.deleteIfExists(path); canonicalPartitionSizes.remove(path); }
                catch(IOException exception) { throw new AuditStorageException("Could not delete expired audit partition: " + path, exception); }
                finally { lock.unlock(); }
            }
            statisticsCache.clear();
        } finally {
            maintenanceLock.unlock();
        }
    }

    /** Descripción: Ejecuta la operación «appendPartition» correspondiente a la responsabilidad de MutableStatistics. Entradas: day: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendPartition(LocalDate day, List<AuditEntry> entries) {
        Path file = partitions.partition(day);
        ReentrantLock lock = locks.lockFor(day.toString());
        lock.lock();
        try {
            Files.createDirectories(root);
            if(Files.exists(file)) ensureCanonicalPartition(file);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(1024, entries.size() * 512));
            for(AuditEntry entry : entries) {
                buffer.write(codec.write(entry));
                buffer.write('\n');
            }
            Files.write(file, buffer.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            canonicalPartitionSizes.put(file, Files.size(file));
        } catch(JacksonException | IOException exception) {
            throw new AuditStorageException("Could not append audit partition: " + file, exception);
        } finally {
            lock.unlock();
        }
    }

    /** Descripción: Ejecuta la operación «scan» correspondiente a la responsabilidad de MutableStatistics. Entradas: query: dato de entrada necesario para la operación; visitor: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void scan(AuditQuery query, EntryVisitor visitor) {
        List<Path> partitions = partitions(query);
        if(query.sortDirection() == AuditSortDirection.DESC) {
            for(Path path : partitions) if(!readReverse(path, query, visitor)) return;
        } else {
            for(Path path : partitions) if(!readForward(path, query, visitor)) return;
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readForward» dentro de MutableStatistics. Entradas: path: ruta o ubicación utilizada por la operación; query: dato de entrada necesario para la operación; visitor: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean readForward(Path path, AuditQuery query, EntryVisitor visitor) {
        ensureCanonicalPartition(path);
        try(var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();
            while(iterator.hasNext()) {
                AuditEntry entry = parse(iterator.next(), path);
                if(entry != null && matches(entry, query) && !visitor.visit(entry)) return false;
            }
            return true;
        } catch(IOException exception) {
            throw new AuditStorageException("Could not read audit partition: " + path, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readReverse» dentro de MutableStatistics. Entradas: path: ruta o ubicación utilizada por la operación; query: dato de entrada necesario para la operación; visitor: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean readReverse(Path path, AuditQuery query, EntryVisitor visitor) {
        ensureCanonicalPartition(path);
        final int blockSize = 64 * 1024;
        try(FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long position = channel.size();
            byte[] carry = new byte[0];
            while(position > 0) {
                int length = (int) Math.min(blockSize, position);
                position -= length;
                ByteBuffer buffer = ByteBuffer.allocate(length);
                long offset = position;
                while(buffer.hasRemaining()) {
                    int read = channel.read(buffer, offset + buffer.position());
                    if(read < 0) break;
                    if(read == 0) Thread.yield();
                }
                byte[] chunk = buffer.array();
                byte[] data = new byte[chunk.length + carry.length];
                System.arraycopy(chunk, 0, data, 0, chunk.length);
                System.arraycopy(carry, 0, data, chunk.length, carry.length);
                int end = data.length;
                for(int index = data.length - 1; index >= 0; index--) {
                    if(data[index] != '\n') continue;
                    int lineStart = index + 1;
                    if(lineStart < end && !visitUtf8Line(data, lineStart, end, path, query, visitor)) return false;
                    end = index;
                }
                carry = new byte[end];
                System.arraycopy(data, 0, carry, 0, end);
            }
            return carry.length == 0 || visitUtf8Line(carry, 0, carry.length, path, query, visitor);
        } catch(IOException exception) {
            throw new AuditStorageException("Could not read audit partition in reverse: " + path, exception);
        }
    }

    /** Descripción: Ejecuta la operación «visitUtf8Line» correspondiente a la responsabilidad de MutableStatistics. Entradas: data: dato de entrada necesario para la operación; start: dato de entrada necesario para la operación; end: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; query: dato de entrada necesario para la operación; visitor: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean visitUtf8Line(byte[] data, int start, int end, Path path, AuditQuery query, EntryVisitor visitor) {
        while(end > start && data[end - 1] == '\r') end--;
        if(end <= start) return true;
        AuditEntry entry = parse(new String(data, start, end - start, StandardCharsets.UTF_8), path);
        return entry == null || !matches(entry, query) || visitor.visit(entry);
    }

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de MutableStatistics. Entradas: line: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuditEntry parse(String line, Path path) {
        if(line == null || line.isBlank()) return null;
        try { return codec.read(line); }
        catch(JacksonException | IllegalArgumentException exception) {
            LOGGER.warn("Skipping malformed audit entry in {}: {}", path, exception.getMessage());
            return null;
        } catch(RuntimeException exception) {
            LOGGER.warn("Skipping incompatible audit entry in {}: {}", path, exception.getMessage());
            return null;
        }
    }

    /** Descripción: Ejecuta la operación «partitions» correspondiente a la responsabilidad de MutableStatistics. Entradas: query: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Path> partitions(AuditQuery query) {
        LocalDate fromDay=query.from()==null?null:query.from().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate toDay=query.to()==null?null:query.to().atZone(ZoneOffset.UTC).toLocalDate();
        List<Path> result=new ArrayList<>();
        for(Path path:partitions.all()){
            LocalDate day=partitions.day(path);
            if(day!=null&&(fromDay==null||!day.isBefore(fromDay))&&(toDay==null||!day.isAfter(toDay))) result.add(path);
        }
        if(query.sortDirection()==AuditSortDirection.DESC) java.util.Collections.reverse(result);
        return List.copyOf(result);
    }

    /** Registra el tamaño observado de una partición; cada línea se valida de forma independiente al leerla. */
    /** Descripción: Valida las condiciones necesarias para «ensureCanonicalPartition» y rechaza entradas no válidas. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureCanonicalPartition(Path path) {
        if(path == null || !Files.exists(path)) return;
        try { canonicalPartitionSizes.put(path, Files.size(path)); }
        catch(IOException exception) { throw new AuditStorageException("Could not inspect audit partition: " + path, exception); }
    }

    /** Descripción: Evalúa la condición «matches» sin modificar el estado externo. Entradas: entry: dato de entrada necesario para la operación; query: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean matches(AuditEntry entry, AuditQuery query) {
        if(!query.workspaceId().equals(entry.workspaceId())) return false;
        if(query.from() != null && entry.timestamp().isBefore(query.from())) return false;
        if(query.to() != null && entry.timestamp().isAfter(query.to())) return false;
        if(!query.actorIds().isEmpty() && !query.actorIds().contains(entry.actor().actorId())) return false;
        if(!query.moduleIds().isEmpty() && !query.moduleIds().contains(entry.moduleId())) return false;
        if(!query.actions().isEmpty() && !query.actions().contains(entry.action())) return false;
        if(!query.activityTypes().isEmpty() && !query.activityTypes().contains(entry.activityType())) return false;
        if(!query.resourceTypes().isEmpty() && !query.resourceTypes().contains(entry.resource().resourceType())) return false;
        if(!query.resourceIds().isEmpty() && !query.resourceIds().contains(entry.resource().resourceId())) return false;
        if(!query.correlationId().isBlank() && !query.correlationId().equals(entry.correlationId())) return false;
        return query.text().isBlank() || searchable(entry).contains(query.text().toLowerCase(Locale.ROOT));
    }

    /** Descripción: Obtiene la información correspondiente a «searchable» dentro de MutableStatistics. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String searchable(AuditEntry entry) {
        StringBuilder builder = new StringBuilder(256);
        builder.append(entry.activityType()).append(' ').append(entry.moduleId()).append(' ').append(entry.action()).append(' ')
                .append(entry.actor().actorId()).append(' ').append(entry.actor().displayName()).append(' ')
                .append(entry.resource().resourceType()).append(' ').append(entry.resource().resourceId()).append(' ')
                .append(entry.resource().displayName());
        for(AuditChange change : entry.changes()) builder.append(' ').append(change.path()).append(' ').append(String.valueOf(change.beforeValue())).append(' ').append(String.valueOf(change.afterValue()));
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    /** Descripción: Ejecuta la operación «maybeCleanupRetention» correspondiente a la responsabilidad de MutableStatistics. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void maybeCleanupRetention() {
        if(!properties.isRetentionEnabled()) return;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate previous = lastRetentionCleanup.get();
        if(today.equals(previous) || !lastRetentionCleanup.compareAndSet(previous, today)) return;
        try { maintain(); }
        catch(RuntimeException exception) {
            lastRetentionCleanup.compareAndSet(today, previous);
            throw exception;
        }
    }

    /** Descripción: Ejecuta la operación «maybeCleanupStoragePressure» correspondiente a la responsabilidad de MutableStatistics. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void maybeCleanupStoragePressure() {
        if(partitions.storageBytes() <= properties.getMaximumStorageBytes()) return;
        maintain();
    }

    /** Descripción: Evalúa la condición «matchesDeletion» sin modificar el estado externo. Entradas: entry: dato de entrada necesario para la operación; criteria: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean matchesDeletion(AuditEntry entry, AuditDeletionCriteria criteria) {
        if(entry == null || entry.actor() == null) return false;
        if(!criteria.workspaceId().equals(entry.workspaceId())) return false;
        if(!criteria.actorIds().isEmpty() && !criteria.actorIds().contains(entry.actor().actorId())) return false;
        if(criteria.mode() == AuditDeletionMode.NAVIGATION) return retentionPolicy.isNavigation(entry);
        if(criteria.mode() == AuditDeletionMode.ALL) return true;
        if(criteria.from() != null && entry.timestamp().isBefore(criteria.from())) return false;
        if(criteria.to() != null && entry.timestamp().isAfter(criteria.to())) return false;
        if(!criteria.moduleIds().isEmpty() && !criteria.moduleIds().contains(entry.moduleId())) return false;
        if(!criteria.actions().isEmpty() && !criteria.actions().contains(entry.action())) return false;
        return criteria.text().isBlank() || searchable(entry).contains(criteria.text().toLowerCase(Locale.ROOT));
    }

    /** Descripción: Ejecuta la operación «evictToStorageLimit» correspondiente a la responsabilidad de MutableStatistics. Entradas: cleanup: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void evictToStorageLimit(CleanupAccumulator cleanup) {
        long maximum = properties.getMaximumStorageBytes();
        if(partitions.storageBytes() <= maximum) return;
        for(int priority = 0; priority <= 3; priority++) {
            final int currentPriority = priority;
            for(Path path : partitions.all()) {
                if(partitions.storageBytes() <= maximum) return;
                rewritePartition(path, entry -> retentionPolicy.evictionPriority(entry) == currentPriority, cleanup);
            }
        }
    }

    /** Descripción: Ejecuta la operación «rewritePartition» correspondiente a la responsabilidad de MutableStatistics. Entradas: path: ruta o ubicación utilizada por la operación; remove: dato de entrada necesario para la operación; cleanup: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rewritePartition(Path path, Predicate<AuditEntry> remove, CleanupAccumulator cleanup) {
        ensureCanonicalPartition(path);
        LocalDate day = partitions.day(path);
        if(day == null || !Files.exists(path)) return;
        ReentrantLock lock = locks.lockFor(day.toString());
        lock.lock();
        Path temporary = path.resolveSibling(path.getFileName() + ".rewrite-" + System.nanoTime() + ".tmp");
        try {
            if(!Files.exists(path)) return;
            long originalSize = Files.size(path);
            long deleted = 0L;
            long kept = 0L;
            try(var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8); var output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                String line;
                while((line = reader.readLine()) != null) {
                    AuditEntry entry = parse(line, path);
                    if(entry != null && remove.test(entry)) { deleted++; continue; }
                    output.write(line);
                    output.newLine();
                    kept++;
                }
            }
            if(deleted == 0L) { Files.deleteIfExists(temporary); return; }
            if(kept == 0L) {
                Files.deleteIfExists(path);
                Files.deleteIfExists(temporary);
                canonicalPartitionSizes.remove(path);
            } else {
                replaceAtomically(temporary, path);
                canonicalPartitionSizes.put(path, Files.size(path));
            }
            cleanup.deletedEntries += deleted;
            cleanup.partitionsTouched++;
            long currentSize = Files.exists(path) ? Files.size(path) : 0L;
            cleanup.reclaimedBytes += Math.max(0L, originalSize - currentSize);
        } catch(IOException exception) {
            try { Files.deleteIfExists(temporary); } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/audit/JsonlAuditLogStore.java",ignored);}
            throw new AuditStorageException("Could not rewrite audit partition: " + path, exception);
        } finally {
            lock.unlock();
        }
    }

    /** Descripción: Actualiza el estado gestionado por MutableStatistics mediante la operación «replaceAtomically». Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void replaceAtomically(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch(AtomicMoveNotSupportedException exception) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static final class CleanupAccumulator {
        private long deletedEntries;
        private long reclaimedBytes;
        private int partitionsTouched;
    }

    /** Descripción: Valida las condiciones necesarias para «requireQuery» y rechaza entradas no válidas. Entradas: query: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuditQuery requireQuery(AuditQuery query) {
        if(query == null) throw new IllegalArgumentException("query is required");
        int size = Math.min(query.pageSize(), properties.getMaximumPageSize());
        return size == query.pageSize() ? query : new AuditQuery(query.workspaceId(), query.actorIds(), query.moduleIds(), query.actions(),
                query.activityTypes(), query.resourceTypes(), query.resourceIds(), query.from(), query.to(), query.text(), query.correlationId(),
                query.page(), size, query.sortDirection());
    }
    @FunctionalInterface private interface EntryVisitor { boolean visit(AuditEntry entry); }

    private static final class MutableStatistics {
        private long total, created, updated, deleted, exports;
        private final Map<String, Long> byModule = new HashMap<>();
        private final Map<String, Long> byAction = new HashMap<>();
        /** Descripción: Ejecuta la operación «accept» correspondiente a la responsabilidad de MutableStatistics. Entradas: entry: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void accept(AuditEntry entry) {
            total++;
            byModule.merge(entry.moduleId(), 1L, Long::sum);
            byAction.merge(entry.action(), 1L, Long::sum);
            switch(entry.action()) {
                case AuditActions.CREATE -> created++;
                case AuditActions.UPDATE -> updated++;
                case AuditActions.DELETE -> deleted++;
                case AuditActions.EXPORT -> exports++;
                default -> { }
            }
        }
        /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de MutableStatistics. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private AuditStatistics snapshot() { return new AuditStatistics(total, created, updated, deleted, exports, byModule, byAction); }
    }
}
