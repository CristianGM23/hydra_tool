package com.example.editor.metamodel.importing.sql;

import java.util.ArrayList;
import java.util.List;

/** Small stateful lexer; parsing is deliberately not implemented with a monolithic regular expression. */
public final class SqlDdlLexer {
    private final String source;
    private int index;
    private int line = 1;
    private int column = 1;
    private final int maxTokens;

    /** Descripción: Inicializa una instancia de SqlDdlLexer con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SqlDdlLexer(String source) { this(source, SqlDdlLimits.defaults().maxTokens()); }
    /** Descripción: Inicializa el lexer con un presupuesto explícito de tokens. Entradas: source y maxTokens. Salida: lexer acotado. */ public SqlDdlLexer(String source,int maxTokens) { this.source = source == null ? "" : source; this.maxTokens=Math.max(1,maxTokens); }

    /** Descripción: Ejecuta la operación «tokenize» correspondiente a la responsabilidad de SqlDdlLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<SqlDdlToken> tokenize() {
        List<SqlDdlToken> tokens = new ArrayList<>();
        while(index < source.length()) {
            char current = source.charAt(index);
            if(Character.isWhitespace(current)) { whitespace(); continue; }
            if(current == '-' && peek(1) == '-') { lineComment(); continue; }
            if(current == '/' && peek(1) == '*') { blockComment(); continue; }
            int startLine = line, startColumn = column;
            if(current == '\'' ) { add(tokens,new SqlDdlToken(SqlDdlTokenType.STRING, quoted('\'', '\'',startLine,startColumn), startLine, startColumn)); continue; }
            if(current == '"') { add(tokens,new SqlDdlToken(SqlDdlTokenType.QUOTED_IDENTIFIER, quoted('"', '"',startLine,startColumn), startLine, startColumn)); continue; }
            if(current == '`') { add(tokens,new SqlDdlToken(SqlDdlTokenType.QUOTED_IDENTIFIER, quoted('`', '`',startLine,startColumn), startLine, startColumn)); continue; }
            if(current == '[') { add(tokens,new SqlDdlToken(SqlDdlTokenType.QUOTED_IDENTIFIER, bracketIdentifier(startLine,startColumn), startLine, startColumn)); continue; }
            if(Character.isDigit(current)) { add(tokens,new SqlDdlToken(SqlDdlTokenType.NUMBER, number(), startLine, startColumn)); continue; }
            if(isWordStart(current)) { add(tokens,new SqlDdlToken(SqlDdlTokenType.WORD, word(), startLine, startColumn)); continue; }
            advance();
            add(tokens,new SqlDdlToken(SqlDdlTokenType.SYMBOL, String.valueOf(current), startLine, startColumn));
        }
        tokens.add(new SqlDdlToken(SqlDdlTokenType.END, "<end>", line, column));
        return List.copyOf(tokens);
    }

    /** Descripción: Añade un token respetando el presupuesto configurado. Entradas: tokens y token. Salida: no devuelve valor. */ private void add(List<SqlDdlToken> tokens,SqlDdlToken token){if(tokens.size()>=maxTokens)throw new SqlDdlSafetyException("SQL DDL token count exceeds configured limit of "+maxTokens+".");tokens.add(token);}

    /** Descripción: Ejecuta la operación «whitespace» correspondiente a la responsabilidad de SqlDdlLexer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void whitespace() { while(index < source.length() && Character.isWhitespace(source.charAt(index))) advance(); }
    /** Descripción: Ejecuta la operación «lineComment» correspondiente a la responsabilidad de SqlDdlLexer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void lineComment() { while(index < source.length() && source.charAt(index) != '\n') advance(); }
    /** Descripción: Ejecuta la operación «blockComment» correspondiente a la responsabilidad de SqlDdlLexer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void blockComment() {
        advance(); advance();
        int startLine=line,startColumn=column;
        while(index < source.length() && !(source.charAt(index) == '*' && peek(1) == '/')) advance();
        if(index < source.length()) { advance(); advance(); } else throw new SqlDdlLexicalException(startLine,startColumn,"Unclosed block comment.");
    }
    /** Descripción: Ejecuta la operación «quoted» correspondiente a la responsabilidad de SqlDdlLexer. Entradas: open: dato de entrada necesario para la operación; close: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String quoted(char open, char close,int startLine,int startColumn) {
        advance(); StringBuilder out = new StringBuilder();
        while(index < source.length()) {
            char value = source.charAt(index);
            if(value == close) {
                if(peek(1) == close) { out.append(close); advance(); advance(); continue; }
                advance(); return out.toString();
            }
            out.append(value); advance();
        }
        throw new SqlDdlLexicalException(startLine,startColumn,"Unclosed quoted SQL value or identifier.");
    }
    /** Descripción: Ejecuta la operación «bracketIdentifier» correspondiente a la responsabilidad de SqlDdlLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String bracketIdentifier(int startLine,int startColumn) {
        advance(); StringBuilder out = new StringBuilder();
        while(index < source.length()) {
            char value = source.charAt(index);
            if(value == ']') { advance(); return out.toString(); }
            out.append(value); advance();
        }
        throw new SqlDdlLexicalException(startLine,startColumn,"Unclosed bracket-quoted SQL identifier.");
    }
    /** Descripción: Ejecuta la operación «number» correspondiente a la responsabilidad de SqlDdlLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String number() { int start = index; while(index < source.length() && (Character.isDigit(source.charAt(index)) || source.charAt(index)=='.')) advance(); return source.substring(start,index); }
    /** Descripción: Ejecuta la operación «word» correspondiente a la responsabilidad de SqlDdlLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String word() { int start = index; while(index < source.length() && isWordPart(source.charAt(index))) advance(); return source.substring(start,index); }
    /** Descripción: Evalúa la condición «isWordStart» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isWordStart(char value) { return Character.isLetter(value) || value == '_' || value == '$' || value == '#'; }
    /** Descripción: Evalúa la condición «isWordPart» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isWordPart(char value) { return Character.isLetterOrDigit(value) || value == '_' || value == '$' || value == '#' || value == '@'; }
    /** Descripción: Ejecuta la operación «peek» correspondiente a la responsabilidad de SqlDdlLexer. Entradas: offset: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private char peek(int offset) { int position = index + offset; return position < source.length() ? source.charAt(position) : '\0'; }
    /** Descripción: Ejecuta la operación «advance» correspondiente a la responsabilidad de SqlDdlLexer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void advance() { if(index >= source.length()) return; if(source.charAt(index++) == '\n') { line++; column = 1; } else column++; }
}
