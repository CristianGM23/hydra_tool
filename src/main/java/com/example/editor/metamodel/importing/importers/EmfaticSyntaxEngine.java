package com.example.editor.metamodel.importing.importers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.AttributeSpec;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.ClassSpec;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.DataTypeSpec;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.EnumLiteralSpec;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.EnumSpec;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.Modifiers;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.OperationSpec;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.ParameterSpec;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.ParsedEmfatic;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.ReferenceSpec;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.Token;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.TokenType;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter.TypeSpec;

/** Parses Emfatic syntax into the importer's neutral intermediate specification. */
final class EmfaticSyntaxEngine {
    private final List<Token> tokens;
    private final String fileName;
    private final List<TransformationWarning> warnings = new ArrayList<>();
    private final List<ClassSpec> classes = new ArrayList<>();
    private final List<EnumSpec> enums = new ArrayList<>();
    private final List<DataTypeSpec> dataTypes = new ArrayList<>();
    private final List<String> imports = new ArrayList<>();
    private int index;
    private String namespaceUri = "";
    private String namespacePrefix = "";

/** Descripción: Inicializa una instancia de EmfaticSyntaxEngine con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación; fileName: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ EmfaticSyntaxEngine(String source, String fileName) {
        this.tokens = new EmfaticLexer(source).lex();
        this.fileName = fileName;
    }

/** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ ParsedEmfatic parse() {
        Map<String, String> packageAnnotations = parseAnnotations();
        expect("package", "Emfatic source must declare a main package");
        String packageName = parseQualifiedName();
        expect(";", "Main Emfatic package must end with ';'");
        readNamespace(packageAnnotations);

        while(peek("import")) {
            consume();
            imports.add(parseNameOrString());
            expect(";", "Emfatic import must end with ';'");
        }

        parseDeclarations(packageName, false);
        Map<String, String> annotations = new LinkedHashMap<>(packageAnnotations);
        annotations.put("emfatic:parser", "hydra-native");
        return new ParsedEmfatic(packageName, namespaceUri, namespacePrefix, classes, enums, dataTypes, imports, annotations, warnings);
    }

    /** Descripción: Ejecuta la operación «parseDeclarations» correspondiente a la responsabilidad de EmfaticLexer. Entradas: packagePath: ruta o ubicación utilizada por la operación; nested: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseDeclarations(String packagePath, boolean nested) {
        while(!atEnd() && !(nested && peek("}"))) {
            Map<String, String> annotations = parseAnnotations();
            if(nested && peek("}")) break;
            if(match("package")) {
                String childName = parseIdentifier();
                expect("{", "Nested Emfatic package must open with '{'");
                parseDeclarations(packagePath + "." + childName, true);
                expect("}", "Nested Emfatic package must close with '}'");
                continue;
            }

            Modifiers modifiers = parseModifiers();
            if(match("class")) {
                parseClass(packagePath, false, modifiers.abstractType, annotations);
            } else if(match("interface")) {
                parseClass(packagePath, true, modifiers.abstractType, annotations);
            } else if(match("datatype")) {
                parseDataType(packagePath, modifiers.transientFeature, annotations);
            } else if(match("enum")) {
                parseEnum(packagePath, annotations);
            } else if(match("mapentry")) {
                parseMapEntry(packagePath, annotations);
            } else if(peek(";")) {
                consume();
            } else {
                Token token = consume();
                warnings.add(new TransformationWarning("IMPORT_LOSSY_EMFATIC_DECLARATION", "$.source[" + token.position() + "]", "Unsupported Emfatic declaration near '" + token.text() + "' was skipped"));
                skipDeclaration();
            }
        }
    }

    /** Descripción: Ejecuta la operación «parseClass» correspondiente a la responsabilidad de EmfaticLexer. Entradas: packagePath: ruta o ubicación utilizada por la operación; interfaceType: dato de entrada necesario para la operación; abstractType: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseClass(String packagePath, boolean interfaceType, boolean abstractType, Map<String, String> annotations) {
        String name = parseIdentifier();
        List<String> superTypes = new ArrayList<>();
        if(match("extends")) {
            do superTypes.add(parseQualifiedName()); while(match(","));
        }
        String instanceClassName = "";
        if(match(":")) instanceClassName = parseNameOrString();
        expect("{", "Emfatic class " + name + " must open with '{'");
        ClassSpec specification = new ClassSpec(packagePath, name, abstractType, interfaceType, instanceClassName, superTypes, annotations);

        while(!atEnd() && !peek("}")) {
            Map<String, String> featureAnnotations = parseAnnotations();
            Modifiers modifiers = parseModifiers();
            if(match("attr")) parseAttribute(specification, modifiers, featureAnnotations);
            else if(match("ref")) parseReference(specification, false, modifiers, featureAnnotations);
            else if(match("val")) parseReference(specification, true, modifiers, featureAnnotations);
            else if(match("op")) parseOperation(specification, modifiers, featureAnnotations);
            else {
                Token token = consume();
                warnings.add(new TransformationWarning("IMPORT_LOSSY_EMFATIC_FEATURE", "$.classes[" + name + "].source[" + token.position() + "]", "Unsupported Emfatic feature near '" + token.text() + "' was skipped"));
                skipDeclaration();
            }
        }
        expect("}", "Emfatic class " + name + " must close with '}'");
        match(";");
        classes.add(specification);
    }

    /** Descripción: Ejecuta la operación «parseAttribute» correspondiente a la responsabilidad de EmfaticLexer. Entradas: owner: dato de entrada necesario para la operación; modifiers: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseAttribute(ClassSpec owner, Modifiers modifiers, Map<String, String> annotations) {
        TypeSpec type = parseType();
        String name = parseIdentifier();
        String defaultValue = "";
        if(match("=")) defaultValue = collectUntil(Set.of(";"));
        expect(";", "Emfatic attribute " + name + " must end with ';'");
        owner.attributes.add(new AttributeSpec(name, type, defaultValue, modifiers, annotations));
    }

    /** Descripción: Ejecuta la operación «parseReference» correspondiente a la responsabilidad de EmfaticLexer. Entradas: owner: dato de entrada necesario para la operación; containment: dato de entrada necesario para la operación; modifiers: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseReference(ClassSpec owner, boolean containment, Modifiers modifiers, Map<String, String> annotations) {
        TypeSpec type = parseType();
        String name = parseIdentifier();
        expect(";", "Emfatic reference " + name + " must end with ';'");
        owner.references.add(new ReferenceSpec(name, type, containment, modifiers, annotations));
    }

    /** Descripción: Ejecuta la operación «parseOperation» correspondiente a la responsabilidad de EmfaticLexer. Entradas: owner: dato de entrada necesario para la operación; modifiers: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseOperation(ClassSpec owner, Modifiers modifiers, Map<String, String> annotations) {
        TypeSpec returnType = parseType();
        String name = parseIdentifier();
        expect("(", "Emfatic operation " + name + " must declare parameters");
        List<ParameterSpec> parameters = new ArrayList<>();
        if(!peek(")")) {
            do {
                Map<String, String> parameterAnnotations = parseAnnotations();
                Modifiers parameterModifiers = parseModifiers();
                TypeSpec parameterType = parseType();
                String parameterName = parseIdentifier();
                parseAnnotations();
                parameters.add(new ParameterSpec(parameterName, parameterType, parameterModifiers, parameterAnnotations));
            } while(match(","));
        }
        expect(")", "Emfatic operation " + name + " has an unterminated parameter list");
        List<String> exceptions = new ArrayList<>();
        if(match("throws")) do exceptions.add(parseQualifiedName()); while(match(","));
        expect(";", "Emfatic operation " + name + " must end with ';'");
        owner.operations.add(new OperationSpec(name, returnType, parameters, exceptions, modifiers, annotations));
    }

    /** Descripción: Ejecuta la operación «parseDataType» correspondiente a la responsabilidad de EmfaticLexer. Entradas: packagePath: ruta o ubicación utilizada por la operación; transientType: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseDataType(String packagePath, boolean transientType, Map<String, String> annotations) {
        String name = parseIdentifier();
        expect(":", "Emfatic datatype " + name + " must declare an instance class");
        String instanceClass = parseNameOrString();
        expect(";", "Emfatic datatype " + name + " must end with ';'");
        dataTypes.add(new DataTypeSpec(packagePath, name, instanceClass, transientType, annotations));
    }

    /** Descripción: Ejecuta la operación «parseEnum» correspondiente a la responsabilidad de EmfaticLexer. Entradas: packagePath: ruta o ubicación utilizada por la operación; annotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseEnum(String packagePath, Map<String, String> annotations) {
        String name = parseIdentifier();
        expect("{", "Emfatic enum " + name + " must open with '{'");
        List<EnumLiteralSpec> literals = new ArrayList<>();
        while(!atEnd() && !peek("}")) {
            Map<String, String> literalAnnotations = parseAnnotations();
            String literalName = parseIdentifier();
            Integer value = null;
            if(match("=")) {
                boolean negative = match("-");
                String number = consume().text();
                try { value = Integer.valueOf((negative ? "-" : "") + number); }
                catch(NumberFormatException exception) { throw error("Invalid Emfatic enum value for " + literalName); }
            }
            parseAnnotations();
            expect(";", "Emfatic enum literal " + literalName + " must end with ';'");
            literals.add(new EnumLiteralSpec(literalName, value, literalAnnotations));
        }
        expect("}", "Emfatic enum " + name + " must close with '}'");
        match(";");
        enums.add(new EnumSpec(packagePath, name, literals, annotations));
    }

    /** Descripción: Ejecuta la operación «parseMapEntry» correspondiente a la responsabilidad de EmfaticLexer. Entradas: packagePath: ruta o ubicación utilizada por la operación; annotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseMapEntry(String packagePath, Map<String, String> annotations) {
        String name = parseIdentifier();
        expect(":", "Emfatic mapentry " + name + " must declare key and value types");
        TypeSpec keyType = parseType();
        expect("->", "Emfatic mapentry " + name + " must contain '->'");
        TypeSpec valueType = parseType();
        expect(";", "Emfatic mapentry " + name + " must end with ';'");
        ClassSpec specification = new ClassSpec(packagePath, name, false, false, "java.util.Map$Entry", List.of(), annotations);
        specification.attributes.add(new AttributeSpec("key", keyType, "", Modifiers.defaults(), Map.of("emfatic:mapEntryRole", "key")));
        specification.attributes.add(new AttributeSpec("value", valueType, "", Modifiers.defaults(), Map.of("emfatic:mapEntryRole", "value")));
        specification.annotations.put("emfatic:mapEntry", "true");
        classes.add(specification);
    }

    /** Descripción: Ejecuta la operación «parseType» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private TypeSpec parseType() {
        if(match("void")) return TypeSpec.voidSpec();
        String name = parseQualifiedName();
        Multiplicity multiplicity = Multiplicity.optional();
        if(match("[")) {
            String expression = collectUntil(Set.of("]"));
            expect("]", "Emfatic multiplicity must close with ']'");
            multiplicity = emfaticMultiplicity(expression);
        }
        String oppositeName = match("#") ? parseIdentifier() : "";
        return new TypeSpec(name, multiplicity, oppositeName, false);
    }

    /** Descripción: Ejecuta la operación «emfaticMultiplicity» correspondiente a la responsabilidad de EmfaticLexer. Entradas: expression: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Multiplicity emfaticMultiplicity(String expression) {
        String value = expression.replace(" ", "");
        if(value.isBlank() || "*".equals(value)) return Multiplicity.many();
        if("?".equals(value)) return Multiplicity.optional();
        if("+".equals(value)) return new Multiplicity(1, null);
        if(value.endsWith("..?")) {
            warnings.add(new TransformationWarning("IMPORT_LOSSY_EMFATIC_UNSPECIFIED_UPPER_BOUND", "$.multiplicity[" + value + "]", "Unspecified EMF upper bound was normalized to unbounded"));
            return new Multiplicity(ImportParsingSupport.integer(value.substring(0, value.indexOf("..")), 0), null);
        }
        return ImportParsingSupport.multiplicity(value, Multiplicity.optional());
    }

    /** Descripción: Ejecuta la operación «parseModifiers» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Modifiers parseModifiers() {
        Modifiers result = Modifiers.defaults();
        boolean progress = true;
        while(progress && !atEnd()) {
            progress = false;
            boolean negated = match("!");
            String modifier = peek().text();
            if(EmfaticMetamodelImporter.MODIFIERS.contains(modifier)) {
                consume();
                result.apply(modifier, negated);
                progress = true;
            } else if(negated) {
                index--;
            }
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «parseAnnotations» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> parseAnnotations() {
        Map<String, String> result = new LinkedHashMap<>();
        while(match("@")) {
            String source = parseNameOrString();
            Map<String, String> details = new LinkedHashMap<>();
            if(match("(")) {
                while(!atEnd() && !peek(")")) {
                    String key = parseNameOrString();
                    String value = "true";
                    if(match("=")) value = parseNameOrString();
                    details.put(key, value);
                    if(!match(",")) break;
                }
                expect(")", "Emfatic annotation must close with ')'");
            }
            String base = "emfatic:annotation:" + ImportParsingSupport.safeId(source, "annotation");
            if(details.isEmpty()) result.put(base, "true");
            else for(Map.Entry<String, String> entry : details.entrySet()) result.put(base + ":" + entry.getKey(), entry.getValue());
            if("namespace".equalsIgnoreCase(source)) {
                namespaceUri = details.getOrDefault("uri", namespaceUri);
                namespacePrefix = details.getOrDefault("prefix", namespacePrefix);
            }
        }
        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readNamespace» dentro de EmfaticLexer. Entradas: annotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void readNamespace(Map<String, String> annotations) {
        for(Map.Entry<String, String> entry : annotations.entrySet()) {
            if(entry.getKey().endsWith(":uri")) namespaceUri = entry.getValue();
            if(entry.getKey().endsWith(":prefix")) namespacePrefix = entry.getValue();
        }
    }

    /** Descripción: Ejecuta la operación «parseQualifiedName» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String parseQualifiedName() {
        StringBuilder result = new StringBuilder(parseIdentifier());
        while(match(".")) result.append('.').append(parseIdentifier());
        return result.toString();
    }

    /** Descripción: Ejecuta la operación «parseNameOrString» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String parseNameOrString() {
        if(peek().type() == TokenType.STRING) return consume().text();
        return parseQualifiedName();
    }

    /** Descripción: Ejecuta la operación «parseIdentifier» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String parseIdentifier() {
        Token token = consume();
        if(token.type() != TokenType.IDENTIFIER) throw error("Expected identifier but found '" + token.text() + "'");
        return cleanName(token.text());
    }

    /** Descripción: Ejecuta la operación «collectUntil» correspondiente a la responsabilidad de EmfaticLexer. Entradas: terminators: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String collectUntil(Set<String> terminators) {
        StringBuilder result = new StringBuilder();
        while(!atEnd() && !terminators.contains(peek().text())) {
            Token token = consume();
            if(result.length() > 0 && needsSpace(result.charAt(result.length() - 1), token.text())) result.append(' ');
            result.append(token.text());
        }
        return result.toString().trim();
    }

    /** Descripción: Ejecuta la operación «needsSpace» correspondiente a la responsabilidad de EmfaticLexer. Entradas: previous: dato de entrada necesario para la operación; next: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean needsSpace(char previous, String next) {
        return Character.isLetterOrDigit(previous) && !next.isBlank() && Character.isLetterOrDigit(next.charAt(0));
    }

    /** Descripción: Ejecuta la operación «skipDeclaration» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void skipDeclaration() {
        int braces = 0;
        while(!atEnd()) {
            if(peek("{") ) braces++;
            if(peek("}")) {
                if(braces == 0) return;
                braces--;
            }
            if(peek(";") && braces == 0) {
                consume();
                return;
            }
            consume();
        }
    }

    /** Descripción: Ejecuta la operación «match» correspondiente a la responsabilidad de EmfaticLexer. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean match(String text) {
        if(!peek(text)) return false;
        index++;
        return true;
    }

    /** Descripción: Ejecuta la operación «peek» correspondiente a la responsabilidad de EmfaticLexer. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean peek(String text) {
        return peek().text().equals(text);
    }

    /** Descripción: Ejecuta la operación «peek» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Token peek() {
        return tokens.get(Math.min(index, tokens.size() - 1));
    }

    /** Descripción: Ejecuta la operación «consume» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Token consume() {
        Token token = peek();
        if(index < tokens.size() - 1) index++;
        return token;
    }

    /** Descripción: Ejecuta la operación «expect» correspondiente a la responsabilidad de EmfaticLexer. Entradas: text: valor de entrada que debe procesarse; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void expect(String text, String message) {
        if(!match(text)) throw error(message + "; found '" + peek().text() + "'");
    }

    /** Descripción: Ejecuta la operación «atEnd» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean atEnd() {
        return peek().type() == TokenType.EOF;
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de EmfaticLexer. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDesignException error(String message) {
        return new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not parse EMF/Emfatic file " + fileName + " at offset " + peek().position() + ": " + message);
    }
    /** Descripción: Ejecuta la operación «cleanName» correspondiente a la responsabilidad de EmfaticLexer. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String cleanName(String value) {
        String result = value == null ? "" : value.trim();
        return result.startsWith("~") ? result.substring(1) : result;
    }
}


final class EmfaticLexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int index;

    /** Descripción: Inicializa una instancia de EmfaticLexer con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ EmfaticLexer(String source) {
        this.source = source == null ? "" : source;
    }

    /** Descripción: Ejecuta la operación «lex» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<Token> lex() {
        while(index < source.length()) {
            char current = source.charAt(index);
            if(Character.isWhitespace(current)) { index++; continue; }
            if(current == '/' && next('/')) { skipLineComment(); continue; }
            if(current == '/' && next('*')) { skipBlockComment(); continue; }
            if(current == '"') { string(); continue; }
            if(Character.isDigit(current)) { number(); continue; }
            if(Character.isLetter(current) || current == '_' || current == '~') { identifier(); continue; }
            int position = index;
            if(current == '-' && next('>')) { tokens.add(new Token(TokenType.SYMBOL, "->", position)); index += 2; continue; }
            if(current == '.' && next('.')) { tokens.add(new Token(TokenType.SYMBOL, "..", position)); index += 2; continue; }
            tokens.add(new Token(TokenType.SYMBOL, String.valueOf(current), position));
            index++;
        }
        tokens.add(new Token(TokenType.EOF, "<eof>", source.length()));
        return List.copyOf(tokens);
    }

    /** Descripción: Ejecuta la operación «identifier» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void identifier() {
        int start = index++;
        while(index < source.length()) {
            char value = source.charAt(index);
            if(!Character.isLetterOrDigit(value) && value != '_' && value != '$' && value != '~') break;
            index++;
        }
        tokens.add(new Token(TokenType.IDENTIFIER, source.substring(start, index), start));
    }

    /** Descripción: Ejecuta la operación «number» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void number() {
        int start = index++;
        while(index < source.length() && Character.isDigit(source.charAt(index))) index++;
        tokens.add(new Token(TokenType.NUMBER, source.substring(start, index), start));
    }

    /** Descripción: Ejecuta la operación «string» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void string() {
        int start = index++;
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        while(index < source.length()) {
            char current = source.charAt(index++);
            if(escaped) {
                value.append(switch(current) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> current; });
                escaped = false;
            } else if(current == '\\') escaped = true;
            else if(current == '"') { tokens.add(new Token(TokenType.STRING, value.toString(), start)); return; }
            else value.append(current);
        }
        throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unterminated string in EMF/Emfatic source at offset " + start);
    }

    /** Descripción: Ejecuta la operación «next» correspondiente a la responsabilidad de EmfaticLexer. Entradas: expected: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean next(char expected) {
        return index + 1 < source.length() && source.charAt(index + 1) == expected;
    }

    /** Descripción: Ejecuta la operación «skipLineComment» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void skipLineComment() {
        index += 2;
        while(index < source.length() && source.charAt(index) != '\n') index++;
    }

    /** Descripción: Ejecuta la operación «skipBlockComment» correspondiente a la responsabilidad de EmfaticLexer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void skipBlockComment() {
        int start = index;
        index += 2;
        while(index + 1 < source.length() && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) index++;
        if(index + 1 >= source.length()) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unterminated block comment in EMF/Emfatic source at offset " + start);
        index += 2;
    }
}


