package com.example.editor.metamodel.validation;

import java.util.List;

/**
 * Agrupa los resultados de validación.
 *
 * @param issues contiene los problemas encontrados.
 */
public record ValidationReport(List<ValidationIssue> issues) {

    /**
     * Descripción:
     * Normaliza los problemas encontrados.
     *
     * Entrada:
     * Usa los componentes del record.
     *
     * Salida:
     * Crea un informe inmutable.
     */
    public ValidationReport {
        if (issues == null) {
            issues = List.of();
        } else {
            issues = List.copyOf(issues);
        }
    }

    /**
     * Descripción:
     * Indica si existen errores.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return true cuando existe algún error.
     */
    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }

    /**
     * Descripción:
     * Devuelve solamente los errores.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return la lista de errores.
     */
    /** Descripción: Ejecuta la operación «errors» correspondiente a la responsabilidad de ValidationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ValidationIssue> errors() {
        return issues.stream().filter(issue -> issue.severity() == ValidationSeverity.ERROR).toList();
    }

    /**
     * Descripción:
     * Devuelve solamente las advertencias.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return la lista de advertencias.
     */
    /** Descripción: Ejecuta la operación «warnings» correspondiente a la responsabilidad de ValidationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ValidationIssue> warnings() {
        return issues.stream().filter(issue -> issue.severity() == ValidationSeverity.WARNING).toList();
    }
}
