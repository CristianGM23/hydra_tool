package com.example.editor.metamodel.importing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/** Detecta el formato mediante extensión, contenido y estructura de archivos ZIP. */
@Component
public class MetamodelImportDetector {

    private static final Pattern EMFATIC_PACKAGE = Pattern.compile("(?s).*\\bpackage\\s+[A-Za-z_][A-Za-z0-9_.]*\\s*;.*");
    private static final Pattern EMFATIC_STRUCTURE = Pattern.compile("(?s).*(\\battr\\b|\\bref\\b|\\bval\\b|\\bdatatype\\b|\\bmapentry\\b).*");
    private static final Pattern JAVA_STRUCTURE = Pattern.compile("(?s).*(?:\\bpackage\\s+[A-Za-z_][A-Za-z0-9_.]*\\s*;)?(?:.*\\b(?:class|interface|enum|record)\\s+[A-Za-z_$][A-Za-z0-9_$]*).*", Pattern.MULTILINE);
    private static final Pattern TYPESCRIPT_STRUCTURE = Pattern.compile("(?m)^\\s*(?:export\\s+)?(?:interface|abstract\\s+class|class)\\s+[A-Za-z_][A-Za-z0-9_]*.*");
    private static final Pattern JAVASCRIPT_STRUCTURE = Pattern.compile("(?m)^\\s*(?:export\\s+default\\s+|export\\s+)?class\\s+[A-Za-z_][A-Za-z0-9_]*(?:\\s+extends\\s+[A-Za-z_][A-Za-z0-9_]*)?\\s*\\{.*");
    private static final Pattern GO_STRUCTURE = Pattern.compile("(?m)^\\s*(?:package\\s+[A-Za-z_][A-Za-z0-9_]*|type\\s+[A-Za-z_][A-Za-z0-9_]*\\s+(?:struct|interface)\\s*\\{).*");
    private static final Pattern PYTHON_STRUCTURE = Pattern.compile("(?m)^(?:from\\s+[^\\n]+\\s+import|import\\s+[^\\n]+|@dataclass\\s*$|class\\s+[A-Za-z_][A-Za-z0-9_]*|def\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\().*");

    /** Descripción: Ejecuta la operación «detect» correspondiente a la responsabilidad de MetamodelImportDetector. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat detect(MetamodelImportSource source) {
        String fileName = source.fileName().toLowerCase(Locale.ROOT);
        MetamodelImportFormat archiveMatch = detectArchive(source);
        if(archiveMatch != null) return archiveMatch;
        MetamodelImportFormat extensionMatch = java.util.Arrays.stream(MetamodelImportFormat.values())
                .filter(format -> format.extensions().stream().anyMatch(fileName::endsWith))
                .sorted(Comparator.comparingInt((MetamodelImportFormat format) -> longestExtension(format, fileName)).reversed())
                .findFirst().orElse(null);
        String text = new String(source.content(), StandardCharsets.UTF_8).stripLeading();
        MetamodelImportFormat contentMatch = detectContent(text);

        if(isAuthoritativeSourceExtension(extensionMatch, fileName)) return extensionMatch;
        if(extensionMatch == MetamodelImportFormat.XMI && contentMatch == MetamodelImportFormat.ECORE) return MetamodelImportFormat.XMI;
        if(contentMatch != null) return contentMatch;
        if(extensionMatch != null && !fileName.endsWith(".zip")) return extensionMatch;
        throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel import format could not be detected for file: " + source.fileName());
    }

    /** Detecta la evidencia dominante dentro de un proyecto ZIP heterogéneo. */
    /** Descripción: Ejecuta la operación «detectArchive» correspondiente a la responsabilidad de MetamodelImportDetector. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportFormat detectArchive(MetamodelImportSource source) {
        byte[] content = source.content();
        if(content.length < 4 || content[0] != 'P' || content[1] != 'K') return null;
        ArchiveSignals signals = new ArchiveSignals();
        scanArchive(content, 0, signals);
        if(signals.glsp) return MetamodelImportFormat.GLSP;
        if(signals.ecore > 0) return MetamodelImportFormat.ECORE;
        if(signals.emfatic > 0) return MetamodelImportFormat.EMF;
        if(signals.xtext > 0) return MetamodelImportFormat.XTEXT;
        if(signals.langium > 0) return MetamodelImportFormat.LANGIUM;
        if(signals.antlr > 0) return MetamodelImportFormat.ANTLR;
        if(signals.blocklyJavaScript > 0 && signals.totalSourceFamilies() == 1) return MetamodelImportFormat.BLOCKLY_JAVASCRIPT;
        return signals.dominantSourceFormat();
    }

    /** Descripción: Ejecuta la operación «scanArchive» correspondiente a la responsabilidad de MetamodelImportDetector. Entradas: archive: archivo o contenido que debe procesarse; depth: dato de entrada necesario para la operación; signals: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void scanArchive(byte[] archive, int depth, ArchiveSignals signals) {
        if(depth > 4 || signals.inspected >= 5000) return;
        try(ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while((entry = input.getNextEntry()) != null && signals.inspected++ < 5000) {
                if(entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if(name.endsWith(".ecore")) signals.ecore++;
                if(name.endsWith(".emf") || name.endsWith(".emfatic")) signals.emfatic++;
                if(name.endsWith(".xtext")) signals.xtext++;
                if(name.endsWith(".langium")) signals.langium++;
                if(name.endsWith(".g4")) signals.antlr++;
                if(name.endsWith(".java")) signals.javaFiles++;
                if(name.endsWith(".ts") || name.endsWith(".tsx")) signals.typeScriptFiles++;
                if(name.endsWith(".blockly.js") || name.endsWith(".blocks.js")) signals.blocklyJavaScript++;
                else if(name.endsWith(".js") || name.endsWith(".mjs") || name.endsWith(".cjs")) signals.javaScriptFiles++;
                if(name.endsWith(".go")) signals.goFiles++;
                if(name.endsWith(".py")) signals.pythonFiles++;
                if(name.contains("glsp-client") || name.contains("glsp-server") || name.endsWith("diagrammodule.java")
                        || name.endsWith("diagram-module.ts") || name.endsWith("glspdiagrammodule.ts")) signals.glsp = true;

                boolean descriptor = name.endsWith("package.json") || name.endsWith("pom.xml") || name.endsWith("build.gradle") || name.endsWith("build.gradle.kts");
                boolean nested = name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".war") || name.endsWith(".ear");
                if(descriptor || nested) {
                    byte[] bytes = readPrefix(input, nested ? 5_242_880 : 262_144);
                    if(descriptor) {
                        String text = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                        if(text.contains("@eclipse-glsp/") || text.contains("org.eclipse.glsp")) signals.glsp = true;
                        if(text.contains("xtext")) signals.xtext++;
                        if(text.contains("langium")) signals.langium++;
                    }
                    if(nested && depth < 4 && bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K') scanArchive(bytes, depth + 1, signals);
                }
            }
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not inspect import archive", exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readPrefix» dentro de MetamodelImportDetector. Entradas: input: valor de entrada que debe procesarse; maximum: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readPrefix(ZipInputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while(output.size() < maximum && (read = input.read(buffer, 0, Math.min(buffer.length, maximum - output.size()))) > 0) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    /** Descripción: Ejecuta la operación «detectContent» correspondiente a la responsabilidad de MetamodelImportDetector. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportFormat detectContent(String text) {
        if(text.startsWith("{") || text.startsWith("[")) {
            if(text.contains("@eclipse-glsp/")) return MetamodelImportFormat.GLSP;
            if(text.contains("\"message0\"") && text.contains("\"type\"") && (text.contains("\"args0\"") || text.contains("\"previousStatement\"") || text.contains("\"output\""))) return MetamodelImportFormat.BLOCKLY_JSON;
            return MetamodelImportFormat.JSON;
        }
        if(text.contains("defineBlocksWithJsonArray") || (text.contains("Blockly.Blocks[") && text.contains("jsonInit"))) return MetamodelImportFormat.BLOCKLY_JAVASCRIPT;
        if(text.contains("<mxfile") || text.contains("<mxGraphModel")) return MetamodelImportFormat.DRAWIO;
        if(looksLikeSqlDdl(text)) return MetamodelImportFormat.SQL_DDL;
        if(text.contains("<ecore:EPackage") || text.contains("<EPackage") || text.contains("xsi:type=\"ecore:EPackage\"")) return MetamodelImportFormat.ECORE;
        if(looksLikeEmfatic(text)) return MetamodelImportFormat.EMF;
        if(text.startsWith("<?xml") && text.contains("<metamodel")) return MetamodelImportFormat.XML;
        if(text.contains("artifactType: metamodel") && text.contains("```plantuml")) return MetamodelImportFormat.MARKDOWN_AGENT;
        if(text.startsWith("# Metamodel:") && text.contains("## Classes")) return MetamodelImportFormat.MARKDOWN;
        if(text.contains("@startuml")) return MetamodelImportFormat.PLANTUML;
        if(text.startsWith("grammar generated.") || text.contains(" with org.eclipse.xtext.common.Terminals")) return MetamodelImportFormat.XTEXT;
        if(text.matches("(?s)^grammar\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;.*")) return MetamodelImportFormat.ANTLR;
        if(text.startsWith("grammar ") && text.contains("entry ")) return MetamodelImportFormat.LANGIUM;
        if(JAVA_STRUCTURE.matcher(text).matches()) return MetamodelImportFormat.JAVA;
        if(TYPESCRIPT_STRUCTURE.matcher(text).find()) return MetamodelImportFormat.TYPESCRIPT;
        if(JAVASCRIPT_STRUCTURE.matcher(text).find()) return MetamodelImportFormat.JAVASCRIPT;
        if(GO_STRUCTURE.matcher(text).find()) return MetamodelImportFormat.GO;
        if(PYTHON_STRUCTURE.matcher(text).find()) return MetamodelImportFormat.PYTHON;
        return null;
    }

    /** Descripción: Detecta estructura DDL relacional sin depender de una única expresión regular monolítica. Entradas: text: texto que debe analizarse. Salida: indica si el contenido parece un esquema SQL. */ private boolean looksLikeSqlDdl(String text) {
        String normalized=text.toLowerCase(Locale.ROOT).replaceAll("\s+"," ").trim();
        return normalized.startsWith("create table ") || normalized.contains("; create table ")
                || normalized.startsWith("create temporary table ") || normalized.startsWith("create temp table ")
                || (normalized.contains("alter table ") && normalized.contains(" foreign key"));
    }

    /** Descripción: Ejecuta la operación «looksLikeEmfatic» correspondiente a la responsabilidad de MetamodelImportDetector. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean looksLikeEmfatic(String text) {
        return EMFATIC_PACKAGE.matcher(text).matches() && EMFATIC_STRUCTURE.matcher(text).matches();
    }

    /** Descripción: Evalúa la condición «isAuthoritativeSourceExtension» sin modificar el estado externo. Entradas: format: dato de entrada necesario para la operación; fileName: archivo o contenido que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isAuthoritativeSourceExtension(MetamodelImportFormat format, String fileName) {
        if(format == null) return false;
        return switch(format) {
            case EMF -> fileName.endsWith(".emf") || fileName.endsWith(".emfatic");
            case SQL_DDL -> fileName.endsWith(".sql") || fileName.endsWith(".ddl");
            case JAVA -> fileName.endsWith(".java") || fileName.endsWith(".jar") || fileName.endsWith(".java.zip");
            case TYPESCRIPT -> fileName.endsWith(".ts") || fileName.endsWith(".tsx") || fileName.endsWith(".typescript.zip");
            case JAVASCRIPT -> fileName.endsWith(".js") || fileName.endsWith(".mjs") || fileName.endsWith(".cjs") || fileName.endsWith(".javascript.zip");
            case GO -> fileName.endsWith(".go") || fileName.endsWith(".go.zip");
            case PYTHON -> fileName.endsWith(".py") || fileName.endsWith(".python.zip");
            case BLOCKLY_JSON -> fileName.endsWith(".blockly.json") || fileName.endsWith(".blocks.json");
            case BLOCKLY_JAVASCRIPT -> fileName.endsWith(".blockly.js") || fileName.endsWith(".blocks.js");
            default -> false;
        };
    }

    /** Descripción: Ejecuta la operación «longestExtension» correspondiente a la responsabilidad de MetamodelImportDetector. Entradas: format: dato de entrada necesario para la operación; fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private int longestExtension(MetamodelImportFormat format, String fileName) {
        return format.extensions().stream().filter(fileName::endsWith).mapToInt(String::length).max().orElse(0);
    }
    private static final class ArchiveSignals {
        private int inspected;
        private int ecore;
        private int emfatic;
        private int xtext;
        private int langium;
        private int antlr;
        private int javaFiles;
        private int typeScriptFiles;
        private int javaScriptFiles;
        private int goFiles;
        private int pythonFiles;
        private int blocklyJavaScript;
        private boolean glsp;

        /** Descripción: Ejecuta la operación «totalSourceFamilies» correspondiente a la responsabilidad de MetamodelImportDetector. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private int totalSourceFamilies() {
            return (javaFiles > 0 ? 1 : 0) + (typeScriptFiles > 0 ? 1 : 0) + (javaScriptFiles > 0 ? 1 : 0)
                    + (goFiles > 0 ? 1 : 0) + (pythonFiles > 0 ? 1 : 0) + (blocklyJavaScript > 0 ? 1 : 0);
        }

        /** Descripción: Ejecuta la operación «dominantSourceFormat» correspondiente a la responsabilidad de MetamodelImportDetector. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportFormat dominantSourceFormat() {
            Map<MetamodelImportFormat, Integer> counts = new java.util.LinkedHashMap<>();
            counts.put(MetamodelImportFormat.JAVA, javaFiles);
            counts.put(MetamodelImportFormat.TYPESCRIPT, typeScriptFiles);
            counts.put(MetamodelImportFormat.JAVASCRIPT, javaScriptFiles);
            counts.put(MetamodelImportFormat.GO, goFiles);
            counts.put(MetamodelImportFormat.PYTHON, pythonFiles);
            counts.put(MetamodelImportFormat.BLOCKLY_JAVASCRIPT, blocklyJavaScript);
            return counts.entrySet().stream().filter(entry -> entry.getValue() > 0)
                    .max(Map.Entry.<MetamodelImportFormat, Integer>comparingByValue()
                            .thenComparing(entry -> entry.getKey().id()))
                    .map(Map.Entry::getKey).orElse(null);
        }
    }

}
