package com.example.editor.audit;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Exportador streaming: no materializa el histórico completo en memoria. */
@Service
public class AuditExportService {
    private final AuditLogStore store;
    private final AuditPresentationService presentation;
    private final AuditLogProperties properties;
    private final ObjectWriter jsonWriter;

    /** Descripción: Inicializa una instancia de AuditExportService con sus dependencias y datos de entrada. Entradas: store: dato de entrada necesario para la operación; presentation: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public AuditExportService(AuditLogStore store, AuditPresentationService presentation, AuditLogProperties properties, ObjectMapper mapper) {
        this.store = store;
        this.presentation = presentation;
        this.properties = properties;
        this.jsonWriter = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de AuditExportService. Entradas: query: dato de entrada necesario para la operación; format: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; output: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public long export(AuditQuery query, AuditExportFormat format, AuditLanguage language, OutputStream output) {
        if(output == null) throw new IllegalArgumentException("output is required");
        AuditExportFormat effectiveFormat = format == null ? AuditExportFormat.JSON : format;
        AuditLanguage effectiveLanguage = language == null ? AuditLanguage.EN : language;
        try {
            return switch(effectiveFormat) {
                case JSON -> writeJson(query, effectiveLanguage, output);
                case CSV -> writeCsv(query, effectiveLanguage, output);
                case MARKDOWN -> writeMarkdown(query, effectiveLanguage, output);
                case TXT -> writeTxt(query, effectiveLanguage, output);
                case DOC -> writeDoc(query, effectiveLanguage, output);
            };
        } catch(UncheckedIOException exception) {
            throw new AuditStorageException("Could not stream audit export", exception.getCause());
        }
    }

    /** Descripción: Actualiza el estado gestionado por AuditExportService mediante la operación «writeJson». Entradas: query: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; output: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long writeJson(AuditQuery query, AuditLanguage language, OutputStream output) {
        write(output, "{\"schemaVersion\":\"1.0\",\"language\":\"" + language.code() + "\",\"entries\":[");
        boolean[] first = { true };
        long count = store.stream(query, entry -> {
            if(!first[0]) write(output, ",");
            first[0] = false;
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("entry", entry);
            record.put("presentation", presentation.present(entry, language));
            try { output.write(jsonWriter.writeValueAsBytes(record)); }
            catch(JacksonException | IOException exception) { throw new UncheckedIOException(asIo(exception)); }
        }, properties.getMaximumExportEntries());
        write(output, "]}");
        return count;
    }

    /** Descripción: Actualiza el estado gestionado por AuditExportService mediante la operación «writeCsv». Entradas: query: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; output: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long writeCsv(AuditQuery query, AuditLanguage language, OutputStream output) {
        String[] headers = language == AuditLanguage.ES
                ? new String[] { "Fecha", "Acción", "Módulo", "Título", "Recurso", "ID del recurso", "Usuario", "Cambios", "Resultado", "ID del evento" }
                : new String[] { "Timestamp", "Action", "Module", "Title", "Resource", "Resource ID", "User", "Changes", "Result", "Event ID" };
        write(output, csvRow(headers));
        return store.stream(query, entry -> {
            AuditPresentedEntry view = presentation.present(entry, language);
            write(output, csvRow(new String[] { view.timestamp().toString(), view.actionLabel(), view.module(), view.title(), view.resourceName(),
                    view.resourceId(), view.actorName(), summarizeChanges(view), view.result().name(), view.eventId() }));
        }, properties.getMaximumExportEntries());
    }

    /** Descripción: Actualiza el estado gestionado por AuditExportService mediante la operación «writeMarkdown». Entradas: query: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; output: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long writeMarkdown(AuditQuery query, AuditLanguage language, OutputStream output) {
        write(output, language == AuditLanguage.ES ? "# Registro de actividad de HYDRA\n\n" : "# HYDRA Activity Log\n\n");
        return store.stream(query, entry -> {
            AuditPresentedEntry view = presentation.present(entry, language);
            write(output, "## " + markdown(view.title()) + "\n\n");
            write(output, "- **" + (language == AuditLanguage.ES ? "Fecha" : "Date") + ":** " + view.timestamp() + "\n");
            write(output, "- **" + (language == AuditLanguage.ES ? "Módulo" : "Module") + ":** " + markdown(view.module()) + "\n");
            write(output, "- **" + (language == AuditLanguage.ES ? "Usuario" : "User") + ":** " + markdown(view.actorName()) + "\n");
            write(output, "- **" + (language == AuditLanguage.ES ? "Recurso" : "Resource") + ":** " + markdown(view.resourceName()) + " (`" + markdown(view.resourceId()) + "`)\n\n");
            writeChangesMarkdown(output, view, language);
        }, properties.getMaximumExportEntries());
    }

    /** Descripción: Actualiza el estado gestionado por AuditExportService mediante la operación «writeTxt». Entradas: query: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; output: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long writeTxt(AuditQuery query, AuditLanguage language, OutputStream output) {
        write(output, language == AuditLanguage.ES ? "REGISTRO DE ACTIVIDAD DE HYDRA\n==============================\n\n" : "HYDRA ACTIVITY LOG\n==================\n\n");
        return store.stream(query, entry -> {
            AuditPresentedEntry view = presentation.present(entry, language);
            write(output, view.timestamp() + " | " + view.title() + "\n");
            write(output, "  " + (language == AuditLanguage.ES ? "Módulo" : "Module") + ": " + view.module() + "\n");
            write(output, "  " + (language == AuditLanguage.ES ? "Usuario" : "User") + ": " + view.actorName() + "\n");
            write(output, "  " + (language == AuditLanguage.ES ? "Recurso" : "Resource") + ": " + view.resourceName() + " [" + view.resourceId() + "]\n");
            for(AuditPresentedChange change : view.changes()) write(output, "    - " + change.label() + ": " + change.beforeValue() + " -> " + change.afterValue() + "\n");
            if(view.changesTruncated()) write(output, language == AuditLanguage.ES ? "    - ... más cambios no mostrados\n" : "    - ... additional changes not shown\n");
            write(output, "\n");
        }, properties.getMaximumExportEntries());
    }

    /** DOC compatible con Word mediante HTML UTF-8, sin dependencia binaria externa. */
    /** Descripción: Actualiza el estado gestionado por AuditExportService mediante la operación «writeDoc». Entradas: query: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; output: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long writeDoc(AuditQuery query, AuditLanguage language, OutputStream output) {
        write(output, "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>HYDRA Activity Log</title>"
                + "<style>body{font-family:Segoe UI,Arial,sans-serif;color:#182235;margin:32px}h1{font-size:24px}"
                + ".entry{border:1px solid #d9e0ea;border-radius:10px;padding:14px 16px;margin:12px 0;page-break-inside:avoid}"
                + ".meta{color:#5a6677;font-size:10pt}.change{margin:6px 0;padding:7px 10px;background:#f5f7fa;border-radius:6px}"
                + ".before{color:#9b2c2c}.after{color:#176b3a}</style></head><body><h1>"
                + (language == AuditLanguage.ES ? "Registro de actividad de HYDRA" : "HYDRA Activity Log") + "</h1>");
        long count = store.stream(query, entry -> {
            AuditPresentedEntry view = presentation.present(entry, language);
            write(output, "<div class=\"entry\"><h2>" + html(view.title()) + "</h2><div class=\"meta\">" + html(view.timestamp().toString())
                    + " · " + html(view.module()) + " · " + html(view.actorName()) + "</div><p><strong>"
                    + (language == AuditLanguage.ES ? "Recurso: " : "Resource: ") + "</strong>" + html(view.resourceName()) + " [" + html(view.resourceId()) + "]</p>");
            for(AuditPresentedChange change : view.changes()) {
                write(output, "<div class=\"change\"><strong>" + html(change.label()) + "</strong><br><span class=\"before\">" + html(change.beforeValue())
                        + "</span> &rarr; <span class=\"after\">" + html(change.afterValue()) + "</span></div>");
            }
            write(output, "</div>");
        }, properties.getMaximumExportEntries());
        write(output, "</body></html>");
        return count;
    }

    /** Descripción: Actualiza el estado gestionado por AuditExportService mediante la operación «writeChangesMarkdown». Entradas: output: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeChangesMarkdown(OutputStream output, AuditPresentedEntry view, AuditLanguage language) {
        if(view.changes().isEmpty()) { write(output, "_" + markdown(view.summary()) + "_\n\n"); return; }
        write(output, "| " + (language == AuditLanguage.ES ? "Campo | Antes | Después" : "Field | Before | After") + " |\n|---|---|---|\n");
        for(AuditPresentedChange change : view.changes()) write(output, "| " + markdown(change.label()) + " | " + markdown(change.beforeValue()) + " | " + markdown(change.afterValue()) + " |\n");
        write(output, "\n");
    }

    /** Descripción: Ejecuta la operación «summarizeChanges» correspondiente a la responsabilidad de AuditExportService. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String summarizeChanges(AuditPresentedEntry entry) {
        StringBuilder builder = new StringBuilder();
        for(AuditPresentedChange change : entry.changes()) {
            if(builder.length() > 0) builder.append("; ");
            builder.append(change.label()).append(": ").append(change.beforeValue()).append(" -> ").append(change.afterValue());
        }
        return builder.toString();
    }

    /** Descripción: Ejecuta la operación «csvRow» correspondiente a la responsabilidad de AuditExportService. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String csvRow(String[] values) {
        StringBuilder row = new StringBuilder();
        for(int index = 0; index < values.length; index++) {
            if(index > 0) row.append(',');
            row.append(csv(values[index]));
        }
        return row.append("\r\n").toString();
    }
    /** Descripción: Ejecuta la operación «csv» correspondiente a la responsabilidad de AuditExportService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String csv(String value) {
        String safe = value == null ? "" : value;
        String trimmed = safe.stripLeading();
        if(!trimmed.isEmpty() && "=+-@\t\r".indexOf(trimmed.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
    /** Descripción: Ejecuta la operación «markdown» correspondiente a la responsabilidad de AuditExportService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String markdown(String value) { return value == null ? "" : value.replace("|", "\\|").replace("\r", " ").replace("\n", " "); }
    /** Descripción: Ejecuta la operación «html» correspondiente a la responsabilidad de AuditExportService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String html(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    /** Descripción: Actualiza el estado gestionado por AuditExportService mediante la operación «write». Entradas: output: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void write(OutputStream output, String value) {
        try { output.write(value.getBytes(StandardCharsets.UTF_8)); }
        catch(IOException exception) { throw new UncheckedIOException(exception); }
    }
    /** Descripción: Ejecuta la operación «asIo» correspondiente a la responsabilidad de AuditExportService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IOException asIo(Exception exception) { return exception instanceof IOException io ? io : new IOException(exception); }
}
