package com.example.editor.textual.runtime;

import java.util.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.diagnostic.SourceTextIndex;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.textual.source.*;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.example.editor.validation.model.*;

/**
 * Safe deterministic fallback textual parser. Provider runtimes may enrich parsing, but every successful parse converges to
 * CanonicalModelDocument and every failed parse leaves the previous last-known-good untouched.
 */
public final class NeutralTextualModelParser {
    private static final Pattern ELEMENT=Pattern.compile("(?m)^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_.-]*)\\b");
    private static final Pattern ASSIGNMENT=Pattern.compile("(?m)\\b([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(\\\"(?:\\\\.|[^\\\"])*\\\"|[-+]?\\d+(?:\\.\\d+)?|true|false|[A-Za-z_][A-Za-z0-9_.-]*)");

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de BindingInfo. Entradas: revision: dato de entrada necesario para la operación; docs: dato de entrada necesario para la operación; snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualParseResult parse(long revision,List<TextualModelSourceDocument> docs,GrammarCompilationSnapshot snapshot){
        return parse(revision,docs,snapshot,2_000);
    }

    /** Descripción: Ejecuta el parse textual respetando el límite de diagnósticos configurado y el contrato sintáctico del snapshot activo. */ /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de DiagnosticGuidance. Entradas: revision: dato de entrada necesario para la operación; docs: dato de entrada necesario para la operación; snapshot: dato de entrada necesario para la operación; maximumDiagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualParseResult parse(long revision,List<TextualModelSourceDocument> docs,GrammarCompilationSnapshot snapshot,int maximumDiagnostics){
        Objects.requireNonNull(snapshot,"snapshot");
        int diagnosticLimit=Math.max(1,maximumDiagnostics);
        List<TextualModelDiagnostic> diagnostics=new ArrayList<>();
        List<TextualSemanticSourceMapEntry> sourceMap=new ArrayList<>();
        List<ElementMatch> matches=new ArrayList<>();
        Set<String> ids=new LinkedHashSet<>();
        NeutralGrammarSyntaxContract syntaxContract=NeutralGrammarSyntaxContract.from(snapshot);
        Map<String,BindingInfo> types=allowedTypes(snapshot,syntaxContract);
        Set<String> syntaxStrategies=new LinkedHashSet<>();
        boolean diagnosticsTruncated=false;

        documentLoop: for(TextualModelSourceDocument doc:docs){
            checkInterrupted();
            SourceTextIndex index=new SourceTextIndex(doc.content());
            boolean grammarSyntaxReported=false;
            if(diagnostics.size()<diagnosticLimit){
                NeutralGrammarSyntaxContract.ValidationResult syntax=syntaxContract.validate(doc.content(),diagnosticLimit-diagnostics.size());
                syntaxStrategies.add(syntax.strategy());
                grammarSyntaxReported=!syntax.issues().isEmpty();
                for(NeutralGrammarSyntaxContract.ValidationIssue issue:syntax.issues()){
                    boolean unavailable="true".equalsIgnoreCase(issue.metadata().getOrDefault("validationUnavailable","false"));
                    String code=unavailable?"TEXTUAL_GRAMMAR_VALIDATION_UNAVAILABLE":"TEXTUAL_GRAMMAR_MISMATCH";
                    String rule=issue.metadata().getOrDefault("ruleContext",issue.metadata().getOrDefault("entryRule",""));
                    String title=unavailable?"No se puede validar el modelo con la gramática activa":rule.isBlank()?"El texto no cumple la gramática activa":"Error sintáctico en la regla «"+rule+"»";
                    if(!addDiagnostic(diagnostics,diagnosticLimit,diag(code,title,issue.message(),doc.id(),index.range(issue.startOffset(),issue.endOffset()),issue.metadata()))) {diagnosticsTruncated=true;break;}
                }
                diagnosticsTruncated|=syntax.truncated();
                if(diagnostics.size()>=diagnosticLimit){diagnosticsTruncated=true;break documentLoop;}
            }
            if(!grammarSyntaxReported)checkDelimiters(doc,index,diagnostics,diagnosticLimit);
            if(diagnostics.size()>=diagnosticLimit){diagnosticsTruncated=true;break;}
            Matcher matcher=ELEMENT.matcher(doc.content());
            while(matcher.find()){
                checkInterrupted();
                String type=matcher.group(1),id=matcher.group(2);
                BindingInfo binding=types.get(type);
                if(!types.isEmpty()&&binding==null){
                    if(!addDiagnostic(diagnostics,diagnosticLimit,diag("TEXTUAL_UNKNOWN_TYPE","Tipo no reconocido por la gramática","\""+type+"\" no forma parte del contrato semántico compilado.",doc.id(),index.range(matcher.start(1),matcher.end(1)),Map.of("type",type)))){diagnosticsTruncated=true;break documentLoop;}
                }
                if(!StableIdentifierPolicy.isValidElementId(id)){
                    if(!addDiagnostic(diagnostics,diagnosticLimit,diag("TEXTUAL_INVALID_ID","Identificador de elemento no válido","\""+id+"\" no cumple el contrato de identificadores estables de HYDRA.",doc.id(),index.range(matcher.start(2),matcher.end(2)),Map.of("id",id)))){diagnosticsTruncated=true;break documentLoop;}
                } else if(!ids.add(id)){
                    if(!addDiagnostic(diagnostics,diagnosticLimit,diag("TEXTUAL_DUPLICATE_ID","Identificador duplicado","El identificador \""+id+"\" ya existe en este modelo.",doc.id(),index.range(matcher.start(2),matcher.end(2)),Map.of("id",id)))){diagnosticsTruncated=true;break documentLoop;}
                }
                int bodyStart=matcher.end(); int bodyEnd=findBodyEnd(doc.content(),bodyStart);
                matches.add(new ElementMatch(doc,type,id,binding,matcher.start(),Math.max(matcher.end(),bodyEnd),bodyStart,bodyEnd));
                sourceMap.add(new TextualSemanticSourceMapEntry(id,"MODEL_ELEMENT",doc.id(),index.range(matcher.start(),Math.max(matcher.end(),bodyEnd)),
                        binding==null?type:binding.semanticClassId(),binding==null?"":binding.grammarRuleId(),Map.of("type",type)));
            }
        }

        Map<String,CanonicalModelElement> elements=new LinkedHashMap<>();
        List<CanonicalModelRelation> relations=new ArrayList<>();
        if(diagnostics.size()<diagnosticLimit){
            matchLoop: for(ElementMatch match:matches){
                checkInterrupted();
                if(!StableIdentifierPolicy.isValidElementId(match.id()))continue;
                SourceTextIndex index=new SourceTextIndex(match.document().content());
                Map<String,Object> attributes=new LinkedHashMap<>();
                Matcher assignment=ASSIGNMENT.matcher(match.document().content());
                assignment.region(Math.max(0,match.bodyStart()),Math.min(match.document().content().length(),Math.max(match.bodyStart(),match.bodyEnd())));
                while(assignment.find()){
                    String property=assignment.group(1),raw=assignment.group(2);
                    if(!StableIdentifierPolicy.isValidElementId(property))continue;
                    int valueStart=assignment.start(2),valueEnd=assignment.end(2);
                    if(isReference(property,raw,ids)){
                        String target=unquote(raw);
                        if(!ids.contains(target)){
                            if(!addDiagnostic(diagnostics,diagnosticLimit,diag("TEXTUAL_REFERENCE_NOT_FOUND","Referencia de modelo no resuelta","\""+target+"\" no identifica ningún elemento del modelo actual.",match.document().id(),index.range(valueStart,valueEnd),Map.of("property",property,"target",target)))){diagnosticsTruncated=true;break matchLoop;}
                        } else {
                            String relationInstanceId=relationInstanceId(match.id(),property,target,relations.size());
                            relations.add(new CanonicalModelRelation(relationInstanceId,property,match.id(),target,Map.of("source","TEXTUAL_MODEL")));
                            sourceMap.add(new TextualSemanticSourceMapEntry(relationInstanceId,"MODEL_RELATION",match.document().id(),index.range(valueStart,valueEnd),property,match.binding()==null?"":match.binding().grammarRuleId(),Map.of("sourceElementId",match.id(),"targetElementId",target,"property",property)));
                        }
                    } else {
                        attributes.put(property,literalValue(raw));
                        sourceMap.add(new TextualSemanticSourceMapEntry(match.id()+":"+property,"MODEL_FEATURE",match.document().id(),index.range(valueStart,valueEnd),property,match.binding()==null?"":match.binding().grammarRuleId(),Map.of("ownerId",match.id(),"property",property)));
                    }
                }
                String classId=match.binding()==null?match.type():match.binding().semanticClassId();
                if(StableIdentifierPolicy.isValidElementId(classId)) elements.put(match.id(),new CanonicalModelElement(match.id(),classId,attributes,Map.of("grammarType",match.type(),"documentId",match.document().id())));
            }
        }else diagnosticsTruncated=true;

        boolean valid=diagnostics.stream().noneMatch(d->d.severity()==GrammarSeverity.ERROR);
        CanonicalModelDocument model=null;
        if(valid){
            String metamodelId=canonicalMetamodelId(snapshot);
            List<String> roots=List.copyOf(elements.keySet());
            model=new CanonicalModelDocument(null,"textual."+snapshot.grammarId(),metamodelId,snapshot.semanticContractEtag(),snapshot.grammarId()+" textual model",roots,List.copyOf(elements.values()),List.copyOf(relations),Map.of("grammarId",snapshot.grammarId(),"grammarSnapshotId",snapshot.id(),"sourceHash",hash(docs),"source","TEXTUAL"));
        }
        Map<String,String> parseMetadata=new LinkedHashMap<>();
        parseMetadata.put("parser","HYDRA_NEUTRAL_SAFE_FALLBACK");parseMetadata.put("lastKnownGoodProtected","true");parseMetadata.put("canonicalModel","true");
        parseMetadata.put("grammarSyntaxContract",Boolean.toString(syntaxContract.available()));parseMetadata.put("grammarEntryRule",syntaxContract.entryRule());
        parseMetadata.put("grammarValidationStrategy",syntaxStrategies.isEmpty()?"NONE":String.join("+",syntaxStrategies));parseMetadata.put("diagnosticsTruncated",Boolean.toString(diagnosticsTruncated));
        parseMetadata.put("maximumDiagnostics",Integer.toString(diagnosticLimit));
        return new TextualParseResult(revision,hash(docs),valid,model,List.copyOf(sourceMap),List.copyOf(diagnostics),snapshot.id(),Map.copyOf(parseMetadata));
    }

    /** Descripción: Ejecuta la operación «allowedTypes» correspondiente a la responsabilidad de BindingInfo. Entradas: snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,BindingInfo> allowedTypes(GrammarCompilationSnapshot snapshot,NeutralGrammarSyntaxContract syntaxContract){
        Map<String,BindingInfo> result=new LinkedHashMap<>();
        snapshot.semanticBindings().bindings().stream().filter(b->b.semanticElementKind().equalsIgnoreCase("CLASS")&& !b.semanticElementId().isBlank()).forEach(b->{
            String grammarName=b.metadata().getOrDefault("grammarName","");
            if(grammarName.isBlank())grammarName=ruleName(b.grammarElementId(),b.semanticElementId());
            result.putIfAbsent(grammarName,new BindingInfo(b.semanticElementId(),b.grammarElementId()));
            result.putIfAbsent(b.semanticElementId(),new BindingInfo(b.semanticElementId(),b.grammarElementId()));
        });
        syntaxContract.typeAliases().forEach((literal,rule)->{BindingInfo binding=result.get(rule);if(binding==null)binding=result.entrySet().stream().filter(entry->entry.getKey().equalsIgnoreCase(rule)||entry.getValue().semanticClassId().equalsIgnoreCase(rule)).map(Map.Entry::getValue).findFirst().orElse(null);if(binding!=null)result.putIfAbsent(literal,binding);});
        return Map.copyOf(result);
    }
    /** Descripción: Ejecuta la operación «ruleName» correspondiente a la responsabilidad de BindingInfo. Entradas: grammarElementId: identificador que delimita el recurso objetivo; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String ruleName(String grammarElementId,String fallback){int idx=grammarElementId.lastIndexOf(':');if(idx>0){String prefix=grammarElementId.substring(0,idx);int p=prefix.lastIndexOf(':');if(p>=0&&p+1<prefix.length())return prefix.substring(p+1);}return fallback;}
    /** Descripción: Evalúa la condición «canonicalMetamodelId» sin modificar el estado externo. Entradas: snapshot: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static String canonicalMetamodelId(GrammarCompilationSnapshot snapshot){String id=snapshot.semanticContractId();if(StableIdentifierPolicy.isValidDocumentId(id))return id;String fallback="grammar."+snapshot.grammarId();return fallback.length()<=120&&StableIdentifierPolicy.isValidDocumentId(fallback)?fallback:"grammar.semantic";}
    /** Descripción: Obtiene la información correspondiente a «findBodyEnd» dentro de BindingInfo. Entradas: s: dato de entrada necesario para la operación; start: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int findBodyEnd(String s,int start){int open=s.indexOf('{',Math.max(0,start-1));if(open<0||open>start+3){int nl=s.indexOf('\n',start);return nl<0?s.length():nl;}int depth=0;boolean quote=false,escape=false;for(int i=open;i<s.length();i++){char c=s.charAt(i);if(escape){escape=false;continue;}if(quote&&c=='\\'){escape=true;continue;}if(c=='\"'){quote=!quote;continue;}if(quote)continue;if(c=='{')depth++;else if(c=='}'&&--depth==0)return i+1;}return s.length();}
    /** Descripción: Evalúa la condición «isReference» sin modificar el estado externo. Entradas: property: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación; ids: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isReference(String property,String raw,Set<String>ids){String target=unquote(raw);return !raw.startsWith("\"")&&(ids.contains(target)||property.toLowerCase(Locale.ROOT).matches(".*(ref|target|source|parent|owner|customer|type|extends|super).*"));}
    /** Descripción: Ejecuta la operación «literalValue» correspondiente a la responsabilidad de BindingInfo. Entradas: raw: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Object literalValue(String raw){if(raw.startsWith("\"")&&raw.endsWith("\""))return unquote(raw);if(raw.equals("true")||raw.equals("false"))return Boolean.valueOf(raw);try{return raw.contains(".")?Double.valueOf(raw):Long.valueOf(raw);}catch(NumberFormatException ignored){return raw;}}
    /** Descripción: Ejecuta la operación «unquote» correspondiente a la responsabilidad de BindingInfo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String unquote(String value){if(value.length()>=2&&value.startsWith("\"")&&value.endsWith("\""))return value.substring(1,value.length()-1).replace("\\\"","\"").replace("\\\\","\\");return value;}
    /** Descripción: Ejecuta la operación «relationInstanceId» correspondiente a la responsabilidad de BindingInfo. Entradas: source: dato de entrada necesario para la operación; property: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; ordinal: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String relationInstanceId(String source,String property,String target,int ordinal){String id=source+":"+property+":"+target+(ordinal==0?"":"-"+ordinal);return id.length()<=240?id:(source+":"+property+":"+ordinal);}
    /** Descripción: Valida las condiciones necesarias para «checkDelimiters» y rechaza entradas no válidas. Entradas: d: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void checkDelimiters(TextualModelSourceDocument d,SourceTextIndex index,List<TextualModelDiagnostic>out,int maximumDiagnostics){Deque<Character>stack=new ArrayDeque<>();Deque<Integer>pos=new ArrayDeque<>();boolean quote=false,escape=false;String s=d.content();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(escape){escape=false;continue;}if(quote&&c=='\\'){escape=true;continue;}if(c=='\"'){quote=!quote;continue;}if(quote)continue;if(c=='{'||c=='['||c=='('){stack.push(c);pos.push(i);}else if(c=='}'||c==']'||c==')'){char e=c=='}'?'{':c==']'?'[':'(';if(stack.isEmpty()||stack.peek()!=e){addDiagnostic(out,maximumDiagnostics,diag("TEXTUAL_SYNTAX_ERROR","Delimitador inesperado","Hay un delimitador de cierre sin pareja.",d.id(),index.range(i,i+1),Map.of()));return;}stack.pop();pos.pop();}}if(quote)addDiagnostic(out,maximumDiagnostics,diag("TEXTUAL_SYNTAX_ERROR","Literal sin cerrar","Falta cerrar un literal de texto.",d.id(),index.range(Math.max(0,s.length()-1),s.length()),Map.of()));if(!stack.isEmpty())addDiagnostic(out,maximumDiagnostics,diag("TEXTUAL_SYNTAX_ERROR","Delimitador sin cerrar","Falta cerrar un bloque del modelo.",d.id(),index.range(pos.peek(),pos.peek()+1),Map.of()));}
    /** Descripción: Añade un diagnóstico sin superar el presupuesto configurado. */ /** Descripción: Ejecuta la operación «addDiagnostic» correspondiente a la responsabilidad de DiagnosticGuidance. Entradas: out: dato de entrada necesario para la operación; maximumDiagnostics: dato de entrada necesario para la operación; diagnostic: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean addDiagnostic(List<TextualModelDiagnostic>out,int maximumDiagnostics,TextualModelDiagnostic diagnostic){if(out.size()>=maximumDiagnostics)return false;out.add(diagnostic);return out.size()<maximumDiagnostics;}

    /**
     * Descripción: Construye un diagnóstico textual accionable con causa y solución específicas del tipo de problema.
     * Entradas: code: código estable; title: título comprensible; msg: explicación concreta; doc: documento; range: rango exacto; meta: contexto semántico.
     * Salida: devuelve un diagnóstico que preserva el borrador y explica por qué ocurrió y cómo resolverlo sin adivinar.
     */
    /** Descripción: Ejecuta la operación «diag» correspondiente a la responsabilidad de DiagnosticGuidance. Entradas: code: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; msg: dato de entrada necesario para la operación; doc: dato de entrada necesario para la operación; range: dato de entrada necesario para la operación; meta: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TextualModelDiagnostic diag(String code, String title, String msg, String doc, TextRange range, Map<String,String> meta) {
        DiagnosticGuidance guidance = guidance(code, meta);
        Map<String,String> details = new LinkedHashMap<>();
        if (meta != null) details.putAll(meta);
        details.put("cause", guidance.cause());
        details.put("recommendedAction", guidance.solution());
        return new TextualModelDiagnostic(code, GrammarSeverity.ERROR, title, msg,
                "El borrador se conserva y puede seguir editándose, pero no sustituirá al último modelo semántico válido hasta resolver este problema.",
                guidance.solution(), doc, range, details);
    }

    /**
     * Descripción: Traduce códigos textuales estables a una explicación de alto nivel y una reparación concreta.
     * Entradas: code: código del diagnóstico; metadata: tipo, ID, propiedad o destino implicado cuando existe.
     * Salida: devuelve causa y solución recomendada específicas para evitar mensajes genéricos.
     */
    /** Descripción: Ejecuta la operación «guidance» correspondiente a la responsabilidad de DiagnosticGuidance. Entradas: code: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static DiagnosticGuidance guidance(String code, Map<String,String> metadata) {
        Map<String,String> values = metadata == null ? Map.of() : metadata;
        return switch (code) {
            case "TEXTUAL_UNKNOWN_TYPE" -> new DiagnosticGuidance(
                    "El tipo escrito no existe entre las clases o reglas semánticas publicadas por el snapshot de gramática activo.",
                    "Cambie el tipo por uno definido en el contrato compilado. Si el tipo debería existir, vuelva al Diseñador de gramáticas, corrija o vincule su regla y genere/active un snapshot nuevo antes de reintentar.");
            case "TEXTUAL_INVALID_ID" -> new DiagnosticGuidance(
                    "El identificador no cumple la política de identificadores estables que HYDRA necesita para referencias, persistencia y eventos.",
                    "Renombre el elemento con un identificador estable válido y único; evite espacios, separadores no admitidos o formas reservadas por el contrato.");
            case "TEXTUAL_DUPLICATE_ID" -> new DiagnosticGuidance(
                    "Dos elementos del borrador comparten el mismo identificador estable, por lo que HYDRA no puede distinguirlos semánticamente.",
                    "Renombre uno de los elementos y actualice las referencias que apunten al identificador duplicado. Cada elemento del modelo debe conservar un ID único.");
            case "TEXTUAL_REFERENCE_NOT_FOUND" -> new DiagnosticGuidance(
                    "La propiedad de referencia apunta a un identificador que no existe en el borrador analizado o no puede resolverse como destino válido.",
                    "Revise el nombre del destino «" + values.getOrDefault("target", "") + "». Corríjalo para apuntar a un elemento existente o cree primero el elemento de destino esperado por la propiedad «" + values.getOrDefault("property", "") + "».");
            case "TEXTUAL_GRAMMAR_MISMATCH" -> {
                String rule=values.getOrDefault("ruleContext",values.getOrDefault("entryRule",""));
                String expected=values.getOrDefault("expectedHuman",values.getOrDefault("expected","una construcción válida"));
                String actual=values.getOrDefault("actual","");
                String cause=rule.isBlank()
                        ? "La gramática activa no admite el elemento encontrado en el rango marcado."
                        : "La regla «"+rule+"» no admite «"+actual+"» en ese punto; en su lugar necesita "+expected+".";
                String automatic=values.getOrDefault("quickFixLabel","");
                String solution=automatic.isBlank()
                        ? "Corrija únicamente el fragmento marcado para introducir "+expected+". Consulte la regla «"+(rule.isBlank()?values.getOrDefault("entryRule",""):rule)+"» si necesita comprobar el orden exacto de sus elementos."
                        : "Puede aplicar la corrección sugerida «"+automatic+"» o editar manualmente el fragmento para introducir "+expected+".";
                yield new DiagnosticGuidance(cause,solution);
            }
            case "TEXTUAL_GRAMMAR_VALIDATION_UNAVAILABLE" -> new DiagnosticGuidance(
                    "HYDRA no dispone de una prueba sintáctica fiable para este snapshot y, por seguridad, no puede tratar el borrador como un modelo válido.",
                    "Vuelva al Diseñador de gramáticas, guarde y compile la gramática para generar un snapshot verificable. Si la gramática usa construcciones fuera del reconocedor neutral, genere el runtime del lenguaje y utilice ese runtime antes de promover el modelo.");
            case "TEXTUAL_SYNTAX_ERROR" -> new DiagnosticGuidance(
                    "La estructura del texto no está equilibrada: hay un bloque, delimitador o literal que no puede cerrarse de forma coherente.",
                    "Corrija exactamente el delimitador o literal señalado. Después espere al nuevo análisis; HYDRA conservará el último modelo válido mientras el borrador siga incompleto.");
            default -> new DiagnosticGuidance(
                    "El texto no puede reconciliarse de forma segura con el contrato semántico compilado en el rango señalado.",
                    "Revise el rango marcado y el contexto del diagnóstico. Corrija el borrador y vuelva a analizarlo antes de promoverlo como modelo válido.");
        };
    }
    /** Descripción: Evalúa la condición «hash» sin modificar el estado externo. Entradas: docs: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static String hash(List<TextualModelSourceDocument>docs){try{MessageDigest md=MessageDigest.getInstance("SHA-256");docs.stream().sorted(Comparator.comparing(TextualModelSourceDocument::relativePath).thenComparing(TextualModelSourceDocument::id)).forEach(d->{md.update(d.id().getBytes(StandardCharsets.UTF_8));md.update((byte)0);md.update(d.relativePath().getBytes(StandardCharsets.UTF_8));md.update((byte)0);md.update(d.content().getBytes(StandardCharsets.UTF_8));md.update((byte)0);});return HexFormat.of().formatHex(md.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    /** Descripción: Valida las condiciones necesarias para «checkInterrupted» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void checkInterrupted(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Textual parsing cancelled");}
    private record DiagnosticGuidance(String cause,String solution){}
    private record BindingInfo(String semanticClassId,String grammarRuleId){}
    private record ElementMatch(TextualModelSourceDocument document,String type,String id,BindingInfo binding,int start,int end,int bodyStart,int bodyEnd){}
}
