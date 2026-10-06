package com.example.editor.definition.runtime.session;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/**
 * Buffer commit-before-publish con memoria acotada. Los primeros eventos se
 * mantienen en RAM y, al alcanzar el umbral configurado, los siguientes se
 * derraman a un JSONL temporal. El spool nunca se recupera tras reiniciar la
 * aplicación porque tampoco se recuperan las copias de trabajo no confirmadas.
 */
@Component
public class EditorSessionPendingEventBuffer {
    private static final Logger LOGGER=LoggerFactory.getLogger(EditorSessionPendingEventBuffer.class);
    private static final int LOCK_STRIPES=64;
    private final EditorSessionProperties properties;
    private final ObjectMapper mapper;
    private final ObjectWriter compactWriter;
    private final ConcurrentHashMap<String,SessionBuffer> buffers=new ConcurrentHashMap<>();
    private final Object[] locks=new Object[LOCK_STRIPES];

    /** Descripción: Inicializa una instancia de EditorSessionPendingEventBuffer con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionPendingEventBuffer(EditorSessionProperties properties,ObjectMapper mapper) {
        this.properties=properties;
        this.mapper=mapper==null?new ObjectMapper():mapper;
        this.compactWriter=this.mapper.writer().without(SerializationFeature.INDENT_OUTPUT);
        for(int index=0;index<locks.length;index++) locks[index]=new Object();
        clearOrphanedSpool();
    }

    /** Descripción: Ejecuta la operación «enqueue» correspondiente a la responsabilidad de CapacityStatus. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void enqueue(PendingEditorEvent event) {
        if(event==null) return;
        String sessionId=event.context().sessionId();
        synchronized(lock(sessionId)) {
            SessionBuffer buffer=buffers.get(sessionId);
            if(buffer==null) {
                if(buffers.size()>=properties.getMaximumPendingEventSessions()) throw capacity("Too many editor sessions have pending unpublished events");
                SessionBuffer created=new SessionBuffer(spoolPath(sessionId));
                SessionBuffer previous=buffers.putIfAbsent(sessionId,created); buffer=previous==null?created:previous;
            }
            if(!buffer.spooling&&buffer.memory.size()<properties.getMaximumPendingEventsInMemory()) {
                buffer.memory.addLast(event);
                return;
            }
            if(buffer.spoolFailed) {
                if(buffer.fallback.size()>=properties.getMaximumPendingEventFallbackEvents()) throw capacity("Pending editor event fallback queue is full");
                buffer.fallback.addLast(event); return;
            }
            buffer.spooling=true;
            if(sizeQuietly(buffer.spool)>=properties.getMaximumPendingEventSpoolBytesPerSession()) throw capacity("Pending editor event spool reached its per-session byte limit");
            try { append(buffer.spool,event); }
            catch(RuntimeException exception) {
                // Desde el primer fallo todos los eventos posteriores permanecen en una cola
                // de respaldo para conservar el orden: memoria -> spool válido -> fallback.
                LOGGER.warn("Pending editor event spool failed for {}; keeping subsequent events in ordered memory fallback: {}",sessionId,exception.getMessage());
                buffer.spoolFailed=true;
                buffer.fallback.addLast(event);
            }
        }
    }

    /** Descripción: Ejecuta la operación «flush» correspondiente a la responsabilidad de CapacityStatus. Entradas: sessionId: identificador que delimita el recurso objetivo; consumer: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void flush(String sessionId,Consumer<PendingEditorEvent> consumer) {
        if(sessionId==null||sessionId.isBlank()||consumer==null) return;
        synchronized(lock(sessionId)) {
            SessionBuffer buffer=buffers.remove(sessionId);
            if(buffer==null) {
                deleteQuietly(spoolPath(sessionId));
                return;
            }
            for(PendingEditorEvent event:buffer.memory) consumer.accept(event);
            if(Files.isRegularFile(buffer.spool)) stream(buffer.spool,consumer);
            for(PendingEditorEvent event:buffer.fallback) consumer.accept(event);
            deleteQuietly(buffer.spool);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «discard» de forma controlada. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void discard(String sessionId) {
        if(sessionId==null||sessionId.isBlank()) return;
        synchronized(lock(sessionId)) {
            SessionBuffer buffer=buffers.remove(sessionId);
            deleteQuietly(buffer==null?spoolPath(sessionId):buffer.spool);
        }
    }

    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de CapacityStatus. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public PendingEventBufferStatus status(String sessionId) {
        if(sessionId==null||sessionId.isBlank()) return new PendingEventBufferStatus(0,0,false,false);
        synchronized(lock(sessionId)) {
            SessionBuffer buffer=buffers.get(sessionId);
            if(buffer==null) return new PendingEventBufferStatus(0,0,false,false);
            long spoolBytes=sizeQuietly(buffer.spool);
            return new PendingEventBufferStatus(buffer.memory.size()+buffer.fallback.size(),spoolBytes,spoolBytes>0,buffer.spoolFailed);
        }
    }

    /** Descripción: Ejecuta la operación «append» correspondiente a la responsabilidad de CapacityStatus. Entradas: path: ruta o ubicación utilizada por la operación; event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void append(Path path,PendingEditorEvent event) {
        try {
            Files.createDirectories(path.getParent());
            try(BufferedWriter writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND)) {
                writer.write(compactWriter.writeValueAsString(event));
                writer.newLine();
            }
        } catch(IOException exception) { throw new IllegalStateException("Could not spool pending editor event",exception); }
    }

    /** Descripción: Ejecuta la operación «stream» correspondiente a la responsabilidad de CapacityStatus. Entradas: path: ruta o ubicación utilizada por la operación; consumer: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void stream(Path path,Consumer<PendingEditorEvent> consumer) {
        try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line;
            while((line=reader.readLine())!=null) {
                if(line.isBlank()) continue;
                try { consumer.accept(mapper.readValue(line,PendingEditorEvent.class)); }
                catch(JacksonException exception) {
                    LOGGER.warn("Skipping malformed pending editor event from {}: {}",path,exception.getOriginalMessage());
                }
            }
        } catch(IOException exception) {
            LOGGER.warn("Pending editor event spool could not be read from {}: {}",path,exception.getMessage());
        }
    }

    /** Descripción: Ejecuta la operación «spoolPath» correspondiente a la responsabilidad de CapacityStatus. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path spoolPath(String sessionId) {
        UUID stable=UUID.nameUUIDFromBytes(sessionId.getBytes(StandardCharsets.UTF_8));
        return properties.getPendingEventSpoolDirectory().toAbsolutePath().normalize().resolve("session-"+stable+".jsonl");
    }

    /** Descripción: Elimina o libera el recurso gestionado por «clearOrphanedSpool» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void clearOrphanedSpool() {
        Path root=properties.getPendingEventSpoolDirectory().toAbsolutePath().normalize();
        try {
            if(!Files.isDirectory(root)) return;
            try(var stream=Files.list(root)) {
                stream.filter(Files::isRegularFile).filter(path->path.getFileName().toString().endsWith(".jsonl")).forEach(this::deleteQuietly);
            }
        } catch(IOException exception) {
            LOGGER.warn("Could not clean orphaned pending editor event spool: {}",exception.getMessage());
        }
    }

    /** Descripción: Ejecuta la operación «sessionCount» correspondiente a la responsabilidad de CapacityStatus. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int sessionCount(){return buffers.size();}
    /** Descripción: Ejecuta la operación «maximumSessionCount» correspondiente a la responsabilidad de CapacityStatus. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int maximumSessionCount(){return properties.getMaximumPendingEventSessions();}
    /** Descripción: Ejecuta la operación «capacityStatus» correspondiente a la responsabilidad de CapacityStatus. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public CapacityStatus capacityStatus(){
        long inMemory=0L,spoolBytes=0L; int failed=0;
        for(SessionBuffer buffer:buffers.values()){inMemory+=buffer.memory.size()+buffer.fallback.size();spoolBytes+=sizeQuietly(buffer.spool);if(buffer.spoolFailed)failed++;}
        return new CapacityStatus(buffers.size(),properties.getMaximumPendingEventSessions(),inMemory,spoolBytes,failed,
                properties.getMaximumPendingEventsInMemory(),properties.getMaximumPendingEventFallbackEvents(),properties.getMaximumPendingEventSpoolBytesPerSession());
    }
    /** Descripción: Ejecuta la operación «capacity» correspondiente a la responsabilidad de CapacityStatus. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorSessionApplicationException capacity(String message){return new EditorSessionApplicationException(EditorSessionApplicationException.Reason.CONFLICT,message);}
    /** Descripción: Ejecuta la operación «lock» correspondiente a la responsabilidad de CapacityStatus. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Object lock(String sessionId) { return locks[Math.floorMod(sessionId.hashCode(),locks.length)]; }
    /** Descripción: Ejecuta la operación «sizeQuietly» correspondiente a la responsabilidad de CapacityStatus. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long sizeQuietly(Path path) { try { return Files.isRegularFile(path)?Files.size(path):0; } catch(IOException ignored) { return 0; } }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteQuietly» de forma controlada. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteQuietly(Path path) { try { if(path!=null) Files.deleteIfExists(path); } catch(IOException exception) { LOGGER.warn("Could not delete pending editor event spool {}: {}",path,exception.getMessage()); } }

    private static final class SessionBuffer {
        private final Deque<PendingEditorEvent> memory=new ArrayDeque<>();
        private final Deque<PendingEditorEvent> fallback=new ArrayDeque<>();
        private final Path spool;
        private boolean spooling;
        private boolean spoolFailed;
        /** Descripción: Inicializa una instancia de SessionBuffer con sus dependencias y datos de entrada. Entradas: spool: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private SessionBuffer(Path spool) { this.spool=spool; }
    }

    public record PendingEventBufferStatus(int inMemoryEvents,long spooledBytes,boolean spooling,boolean spoolFailed) { }
    public record CapacityStatus(int sessions,int maximumSessions,long inMemoryEvents,long spooledBytes,int spoolFailures,
            int maximumMemoryEventsPerSession,int maximumFallbackEventsPerSession,long maximumSpoolBytesPerSession) {
        /** Descripción: Ejecuta la operación «saturated» correspondiente a la responsabilidad de CapacityStatus. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean saturated(){return sessions>=maximumSessions;}
    }
}

