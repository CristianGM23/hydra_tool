package com.example.editor.model;

/**
 * Representa un archivo XMI almacenado.
 *
 * @param fileName nombre del archivo.
 * @param size tamaño del archivo en bytes.
 */
public record SavedModelSummary(String fileName, long size) {
}