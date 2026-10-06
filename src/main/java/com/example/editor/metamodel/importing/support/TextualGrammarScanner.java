package com.example.editor.metamodel.importing.support;

/** Lexical scanning helpers shared by the textual grammar importer. */
final class TextualGrammarScanner {
    /** Descripción: Inicializa una instancia de TextualGrammarScanner con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TextualGrammarScanner() {
    }

    /** Descripción: Ejecuta la operación «stripComments» correspondiente a la responsabilidad de ScanState. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String stripComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean lineComment = false;
        boolean blockComment = false;
        boolean single = false;
        boolean dual = false;
        boolean regex = false;
        boolean escaped = false;

        for(int index = 0; index < source.length(); index += 1) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : 0;

            if(lineComment) {
                if(current == '\n' || current == '\r') {
                    lineComment = false;
                    result.append(current);
                } else {
                    result.append(' ');
                }
                continue;
            }

            if(blockComment) {
                if(current == '*' && next == '/') {
                    result.append("  ");
                    index += 1;
                    blockComment = false;
                } else {
                    result.append(current == '\n' || current == '\r' ? current : ' ');
                }
                continue;
            }

            if(!single && !dual && !regex && current == '/' && next == '/') {
                result.append("  ");
                index += 1;
                lineComment = true;
                continue;
            }

            if(!single && !dual && !regex && current == '/' && next == '*') {
                result.append("  ");
                index += 1;
                blockComment = true;
                continue;
            }

            result.append(current);

            if(escaped) {
                escaped = false;
                continue;
            }

            if((single || dual || regex) && current == '\\') {
                escaped = true;
                continue;
            }

            if(!dual && !regex && current == '\'') {
                single = !single;
            } else if(!single && !regex && current == '"') {
                dual = !dual;
            } else if(!single && !dual && current == '/') {
                if(regex) {
                    regex = false;
                } else if(looksLikeRegexStart(source, index)) {
                    regex = true;
                }
            }
        }

        return result.toString();
    }

    /** Descripción: Ejecuta la operación «looksLikeRegexStart» correspondiente a la responsabilidad de ScanState. Entradas: source: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean looksLikeRegexStart(String source, int index) {
        if(index > 0 && source.charAt(index - 1) == '\\') {
            return false;
        }

        int previous = index - 1;

        while(previous >= 0 && Character.isWhitespace(source.charAt(previous))) {
            previous -= 1;
        }

        return previous < 0 || source.charAt(previous) == ':' || source.charAt(previous) == '=' || source.charAt(previous) == '(' || source.charAt(previous) == '|';
    }

    /** Descripción: Ejecuta la operación «maskQuotedText» correspondiente a la responsabilidad de ScanState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String maskQuotedText(String value) {
        StringBuilder result = new StringBuilder(value);
        boolean single = false;
        boolean dual = false;
        boolean regex = false;
        boolean escaped = false;

        for(int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);

            if(escaped) {
                if(current != '\n' && current != '\r') {
                    result.setCharAt(index, ' ');
                }
                escaped = false;
                continue;
            }

            if((single || dual || regex) && current == '\\') {
                result.setCharAt(index, ' ');
                escaped = true;
                continue;
            }

            if(!dual && !regex && current == '\'') {
                single = !single;
                result.setCharAt(index, ' ');
                continue;
            }

            if(!single && !regex && current == '"') {
                dual = !dual;
                result.setCharAt(index, ' ');
                continue;
            }

            if(!single && !dual && current == '/') {
                if(regex) {
                    regex = false;
                    result.setCharAt(index, ' ');
                    continue;
                }

                if(looksLikeRegexStart(value, index)) {
                    regex = true;
                    result.setCharAt(index, ' ');
                    continue;
                }
            }

            if(single || dual || regex) {
                if(current != '\n' && current != '\r') {
                    result.setCharAt(index, ' ');
                }
            }
        }

        return result.toString();
    }

    /** Descripción: Obtiene la información correspondiente a «findStatementEnd» dentro de ScanState. Entradas: content: valor de entrada que debe procesarse; start: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int findStatementEnd(String content, int start) {
        int round = 0;
        int square = 0;
        int curly = 0;
        ScanState state = new ScanState();

        for(int index = start; index < content.length(); index += 1) {
            char current = content.charAt(index);
            state.accept(content, index);

            if(state.insideLiteral()) {
                continue;
            }

            if(current == '(') {
                round += 1;
            } else if(current == ')') {
                round = Math.max(0, round - 1);
            } else if(current == '[') {
                square += 1;
            } else if(current == ']') {
                square = Math.max(0, square - 1);
            } else if(current == '{') {
                curly += 1;
            } else if(current == '}') {
                curly = Math.max(0, curly - 1);
            } else if(current == ';' && round == 0 && square == 0 && curly == 0) {
                return index;
            }
        }

        return content.length();
    }

    /** Descripción: Obtiene la información correspondiente a «findMatching» dentro de ScanState. Entradas: value: valor de entrada que debe procesarse; start: dato de entrada necesario para la operación; open: dato de entrada necesario para la operación; close: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int findMatching(String value, int start, char open, char close) {
        if(start < 0 || start >= value.length()) {
            return -1;
        }

        int depth = 0;
        ScanState state = new ScanState();

        for(int index = start; index < value.length(); index += 1) {
            char current = value.charAt(index);
            state.accept(value, index);

            if(state.insideLiteral()) {
                continue;
            }

            if(current == open) {
                depth += 1;
            } else if(current == close) {
                depth -= 1;

                if(depth == 0) {
                    return index;
                }
            }
        }

        return -1;
    }

    /** Descripción: Obtiene la información correspondiente a «findStringEnd» dentro de ScanState. Entradas: value: valor de entrada que debe procesarse; start: dato de entrada necesario para la operación; quote: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int findStringEnd(String value, int start, char quote) {
        boolean escaped = false;

        for(int index = start + 1; index < value.length(); index += 1) {
            char current = value.charAt(index);

            if(escaped) {
                escaped = false;
            } else if(current == '\\') {
                escaped = true;
            } else if(current == quote) {
                return index;
            }
        }

        return value.length() - 1;
    }

    /** Descripción: Obtiene la información correspondiente a «readQuantifier» dentro de ScanState. Entradas: value: valor de entrada que debe procesarse; start: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static char readQuantifier(String value, int start) {
        int index = skipWhitespace(value, start);

        if(index < value.length() && (value.charAt(index) == '?' || value.charAt(index) == '*' || value.charAt(index) == '+')) {
            return value.charAt(index);
        }

        return 0;
    }

    /** Descripción: Ejecuta la operación «skipWhitespace» correspondiente a la responsabilidad de ScanState. Entradas: value: valor de entrada que debe procesarse; start: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int skipWhitespace(String value, int start) {
        int index = start;

        while(index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index += 1;
        }

        return index;
    }

    /** Descripción: Ejecuta la operación «mark» correspondiente a la responsabilidad de ScanState. Entradas: occupied: dato de entrada necesario para la operación; start: dato de entrada necesario para la operación; end: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ static void mark(boolean[] occupied, int start, int end) {
        for(int index = Math.max(0, start); index < Math.min(occupied.length, end); index += 1) {
            occupied[index] = true;
        }
    }

    /** Descripción: Evalúa la condición «isOccupied» sin modificar el estado externo. Entradas: occupied: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean isOccupied(boolean[] occupied, int index) {
        return index >= 0 && index < occupied.length && occupied[index];
    }

    /** Descripción: Ejecuta la operación «line» correspondiente a la responsabilidad de ScanState. Entradas: content: valor de entrada que debe procesarse; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int line(String content, int index) {
        int line = 1;

        for(int current = 0; current < Math.min(index, content.length()); current += 1) {
            if(content.charAt(current) == '\n') {
                line += 1;
            }
        }

        return line;
    }


    static final class ScanState {
        private boolean single;
        private boolean dual;
        private boolean regex;
        private boolean escaped;

        /** Descripción: Ejecuta la operación «accept» correspondiente a la responsabilidad de ScanState. Entradas: value: valor de entrada que debe procesarse; index: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void accept(String value, int index) {
            char current = value.charAt(index);

            if(escaped) {
                escaped = false;
                return;
            }

            if((single || dual || regex) && current == '\\') {
                escaped = true;
                return;
            }

            if(!dual && !regex && current == '\'') {
                single = !single;
            } else if(!single && !regex && current == '"') {
                dual = !dual;
            } else if(!single && !dual && current == '/') {
                if(regex) {
                    regex = false;
                } else if(looksLikeRegexStart(value, index)) {
                    regex = true;
                }
            }
        }

        /** Descripción: Ejecuta la operación «insideLiteral» correspondiente a la responsabilidad de ScanState. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean insideLiteral() {
            return single || dual || regex;
        }
    }
}
