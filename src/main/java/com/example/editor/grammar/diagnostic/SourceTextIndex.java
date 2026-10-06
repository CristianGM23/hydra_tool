package com.example.editor.grammar.diagnostic;

import java.util.ArrayList;
import java.util.List;
import com.example.editor.grammar.core.TextPosition;
import com.example.editor.grammar.core.TextRange;

/** Índice de offsets Java/UTF-16 a línea-columna 0-based compatible con LSP/Monaco y CR/LF/CRLF. */
public final class SourceTextIndex {
    private final String source;
    private final int[] lineStarts;

    /** Descripción: Inicializa una instancia de SourceTextIndex con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SourceTextIndex(String source) {
        this.source = source == null ? "" : source;
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < this.source.length(); i++) {
            char ch = this.source.charAt(i);
            if (ch == '\r') {
                if (i + 1 < this.source.length() && this.source.charAt(i + 1) == '\n') i++;
                starts.add(i + 1);
            } else if (ch == '\n') {
                starts.add(i + 1);
            }
        }
        this.lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Descripción: Ejecuta la operación «position» correspondiente a la responsabilidad de SourceTextIndex. Entradas: offset: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextPosition position(int offset) {
        int bounded = Math.max(0, Math.min(source.length(), offset));
        int low = 0, high = lineStarts.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lineStarts[mid] <= bounded) low = mid + 1; else high = mid - 1;
        }
        int line = Math.max(0, high);
        return new TextPosition(line, bounded - lineStarts[line]);
    }

    /** Descripción: Ejecuta la operación «range» correspondiente a la responsabilidad de SourceTextIndex. Entradas: startOffset: dato de entrada necesario para la operación; endOffset: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextRange range(int startOffset, int endOffset) {
        int start = Math.max(0, Math.min(source.length(), startOffset));
        int end = Math.max(start, Math.min(source.length(), endOffset));
        return new TextRange(position(start), position(end));
    }

    /** Descripción: Ejecuta la operación «offset» correspondiente a la responsabilidad de SourceTextIndex. Entradas: position: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public int offset(TextPosition position) {
        if (position == null) return 0;
        int line = Math.max(0, Math.min(lineStarts.length - 1, position.line()));
        int lineStart = lineStarts[line];
        int lineEnd = line + 1 < lineStarts.length ? lineStarts[line + 1] : source.length();
        return Math.max(lineStart, Math.min(lineEnd, lineStart + Math.max(0, position.column())));
    }
}
