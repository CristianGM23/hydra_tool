package com.example.editor.project.recovery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Diario durable de una operación de importación externa. Se escribe <b>antes</b> de ejecutar cada efecto sobre los
 * almacenes canónicos (protocolo write-ahead) y es la fuente de verdad para recuperar tras una interrupción.
 *
 * <p>Estados de la operación: {@code STARTED} (en curso o interrumpida), {@code COMMITTED} (terminada con éxito; solo falta
 * borrar el diario), {@code COMPENSATING} y {@code COMPENSATION_FAILED} (alguna compensación no se pudo completar; el diario
 * se conserva con el detalle).
 *
 * @param operationId identidad estable de la operación
 * @param inputFingerprint huella SHA-256 del contenido importado
 * @param confirmedFingerprint huella confirmada por quien importa (igual a la anterior: la confirmación queda ligada a la operación)
 */
record ExternalImportJournal(String operationId, String inputFingerprint, String confirmedFingerprint, String projectName,
        String state, Instant createdAt, Instant updatedAt, List<Effect> effects, List<String> failures) {
    static final String STARTED = "STARTED";
    static final String COMMITTED = "COMMITTED";
    static final String COMPENSATING = "COMPENSATING";
    static final String COMPENSATION_FAILED = "COMPENSATION_FAILED";

    static final String METAMODEL = "METAMODEL";
    static final String METAMODEL_LAYERS = "METAMODEL_LAYERS";
    static final String ASSET = "ASSET";
    static final String PROJECT = "PROJECT";

    static final String CREATED = "CREATED";
    static final String REUSED = "REUSED";
    static final String REPLACED = "REPLACED";

    static final String INTENDED = "INTENDED";
    static final String APPLIED = "APPLIED";

    static final String PENDING = "PENDING";
    static final String DONE = "DONE";
    static final String FAILED = "FAILED";

    ExternalImportJournal {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        effects = effects == null ? List.of() : List.copyOf(effects);
        failures = failures == null ? List.of() : List.copyOf(failures);
        state = state == null ? STARTED : state;
    }

    /** Descripción: Crea o prepara el resultado de «start» dentro de ExternalImportJournal. Entradas: operationId: identificador que delimita el recurso objetivo; inputFingerprint: dato de entrada necesario para la operación; confirmedFingerprint: dato de entrada necesario para la operación; projectName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static ExternalImportJournal start(String operationId, String inputFingerprint, String confirmedFingerprint, String projectName) {
        Instant now = Instant.now();
        return new ExternalImportJournal(operationId, inputFingerprint, confirmedFingerprint, projectName, STARTED, now, now, List.of(), List.of());
    }

    /** Descripción: Ejecuta la operación «withState» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: newState: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ExternalImportJournal withState(String newState) {
        return new ExternalImportJournal(operationId, inputFingerprint, confirmedFingerprint, projectName, newState, createdAt, Instant.now(), effects, failures);
    }

    /** Descripción: Ejecuta la operación «withFailures» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: newFailures: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ExternalImportJournal withFailures(List<String> newFailures) {
        return new ExternalImportJournal(operationId, inputFingerprint, confirmedFingerprint, projectName, state, createdAt, Instant.now(), effects, newFailures);
    }

    /** Sustituye el efecto con el mismo identificador o lo añade al final. */
    /** Descripción: Ejecuta la operación «withEffect» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: effect: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ExternalImportJournal withEffect(Effect effect) {
        List<Effect> updated = new ArrayList<>(effects);
        boolean replaced = false;
        for(int index = 0; index < updated.size(); index++) {
            if(updated.get(index).id().equals(effect.id())) { updated.set(index, effect); replaced = true; break; }
        }
        if(!replaced) updated.add(effect);
        return new ExternalImportJournal(operationId, inputFingerprint, confirmedFingerprint, projectName, state, createdAt, Instant.now(), updated, failures);
    }

    /** Descripción: Ejecuta la operación «withoutEffect» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ ExternalImportJournal withoutEffect(String id) {
        List<Effect> kept = new ArrayList<>(effects);
        kept.removeIf(value -> value.id().equals(id));
        return new ExternalImportJournal(operationId, inputFingerprint, confirmedFingerprint, projectName, state, createdAt, Instant.now(), kept, failures);
    }

    /** Descripción: Ejecuta la operación «effect» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Effect effect(String id) {
        return effects.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    /** Descripción: Ejecuta la operación «terminal» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean terminal() { return COMMITTED.equals(state); }

    /** Descripción: Ejecuta la operación «compensationComplete» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean compensationComplete() { return effects.stream().allMatch(value -> DONE.equals(value.compensation())); }

    /**
     * Un efecto sobre un recurso canónico.
     *
     * @param kind METAMODEL, METAMODEL_LAYERS, ASSET o PROJECT
     * @param ownership CREATED (lo creó la operación), REUSED (preexistente y compartido: no se borra) o REPLACED (capas añadidas o sustituidas sobre un recurso preexistente)
     * @param status INTENDED (registrado, aplicación sin confirmar) o APPLIED
     * @param compensation PENDING, DONE o FAILED
     * @param existedBefore si el recurso existía antes de la operación
     * @param expected contenido esperado del recurso creado, para no borrar uno distinto
     * @param snapshot estado anterior necesario para restaurar un recurso modificado
     */
    record Effect(String id, String kind, String resourceId, String ownership, String status, String compensation,
            boolean existedBefore, JsonNode expected, JsonNode snapshot, String error) {
        Effect {
            status = status == null ? INTENDED : status;
            compensation = compensation == null ? PENDING : compensation;
        }

        /** Descripción: Ejecuta la operación «idOf» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: kind: dato de entrada necesario para la operación; resourceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ static String idOf(String kind, String resourceId) { return kind + ":" + resourceId; }

        /** Descripción: Ejecuta la operación «intended» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: kind: dato de entrada necesario para la operación; resourceId: identificador que delimita el recurso objetivo; ownership: dato de entrada necesario para la operación; existedBefore: dato de entrada necesario para la operación; expected: dato de entrada necesario para la operación; snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Effect intended(String kind, String resourceId, String ownership, boolean existedBefore, JsonNode expected, JsonNode snapshot) {
            return new Effect(idOf(kind, resourceId), kind, resourceId, ownership, INTENDED, PENDING, existedBefore, expected, snapshot, "");
        }

        /** Descripción: Ejecuta la operación «withStatus» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: newStatus: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Effect withStatus(String newStatus) {
            return new Effect(id, kind, resourceId, ownership, newStatus, compensation, existedBefore, expected, snapshot, error);
        }

        /** Descripción: Ejecuta la operación «withResource» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: newResourceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Effect withResource(String newResourceId) {
            return new Effect(idOf(kind, newResourceId), kind, newResourceId, ownership, status, compensation, existedBefore, expected, snapshot, error);
        }

        /** Descripción: Ejecuta la operación «withCompensation» correspondiente a la responsabilidad de ExternalImportJournal. Entradas: newCompensation: dato de entrada necesario para la operación; newError: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Effect withCompensation(String newCompensation, String newError) {
            return new Effect(id, kind, resourceId, ownership, status, newCompensation, existedBefore, expected, snapshot, newError == null ? "" : newError);
        }
    }
}
