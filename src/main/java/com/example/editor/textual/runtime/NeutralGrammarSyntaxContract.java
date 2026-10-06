package com.example.editor.textual.runtime;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;

/**
 * Bounded, technology-neutral syntax contract reconstructed from the exact grammar sources retained in a successful snapshot.
 * Model input is never matched through one global regular expression. Small inputs use an iterative Earley recognizer and large
 * repetitive inputs are checked declaration-by-declaration, so model size cannot turn Java regex recursion into a StackOverflowError.
 */
final class NeutralGrammarSyntaxContract {
    private static final int MAX_GRAMMAR_RULES=2_000;
    private static final int MAX_GRAMMAR_NESTING=96;
    private static final int MAX_PRODUCTIONS=12_000;
    private static final int FULL_MAX_CHARS=65_536;
    private static final int FULL_MAX_TOKENS=900;
    private static final int INCREMENTAL_MAX_TOKENS=1_200;
    private static final int MAX_EARLEY_STATES=300_000;
    private static final Set<String> IDENT_TERMINALS=Set.of("ID","IDENTIFIER","NAME");
    private static final Set<String> NUMBER_TERMINALS=Set.of("INT","INTEGER","NUMBER","FLOAT","DOUBLE","DECIMAL","LONG","SHORT");
    private static final Set<String> STRING_TERMINALS=Set.of("STRING","TEXT","QUOTED_STRING");
    private static final Set<String> BOOLEAN_TERMINALS=Set.of("BOOL","BOOLEAN");
    private static final Set<String> ANY_TERMINALS=Set.of("ANY","ANY_OTHER","TOKEN","CHAR","CHARACTER");

    record ValidationIssue(int startOffset,int endOffset,String message,Map<String,String> metadata){
        ValidationIssue{metadata=metadata==null?Map.of():Map.copyOf(metadata);}
    }
    record ValidationResult(List<ValidationIssue> issues,String strategy,boolean truncated){
        ValidationResult{issues=issues==null?List.of():List.copyOf(issues);strategy=strategy==null?"NONE":strategy;}
    }

    private final GrammarTechnology technology;
    private final String entryRule;
    private final Map<String,Node> parserRules;
    private final Set<String> terminalRules;
    private final Map<String,String> typeAliases;
    private final List<String> incrementalRules;
    private final Set<String> grammarLiterals;
    private final Grammar grammar;
    private final boolean available;
    private final boolean fullRecognitionAvailable;

    /** Descripción: Inicializa una instancia de NeutralGrammarSyntaxContract con sus dependencias y datos de entrada. Entradas: technology: dato de entrada necesario para la operación; entryRule: dato de entrada necesario para la operación; parserRules: componente de conversión utilizado durante el procesamiento; terminalRules: dato de entrada necesario para la operación; typeAliases: dato de entrada necesario para la operación; incrementalRules: dato de entrada necesario para la operación; grammarLiterals: dato de entrada necesario para la operación; grammar: dato de entrada necesario para la operación; available: dato de entrada necesario para la operación; fullRecognitionAvailable: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private NeutralGrammarSyntaxContract(GrammarTechnology technology,String entryRule,Map<String,Node> parserRules,
            Set<String> terminalRules,Map<String,String> typeAliases,List<String> incrementalRules,Set<String> grammarLiterals,Grammar grammar,
            boolean available,boolean fullRecognitionAvailable){
        this.technology=technology;
        this.entryRule=entryRule==null?"":entryRule;
        this.parserRules=Map.copyOf(parserRules);
        this.terminalRules=Set.copyOf(terminalRules);
        this.typeAliases=Map.copyOf(typeAliases);
        this.incrementalRules=List.copyOf(incrementalRules);
        this.grammarLiterals=Set.copyOf(grammarLiterals);
        this.grammar=grammar;
        this.available=available;
        this.fullRecognitionAvailable=fullRecognitionAvailable;
    }

    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de GrammarToken. Entradas: snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static NeutralGrammarSyntaxContract from(GrammarCompilationSnapshot snapshot){
        Objects.requireNonNull(snapshot,"snapshot");
        GrammarTechnology technology=snapshot.technology();
        List<Source> sources=grammarSources(snapshot);
        if(sources.isEmpty())return unavailable(technology,snapshot.runtimeDescriptor().entryRule());
        LinkedHashMap<String,RuleSource> parsed=new LinkedHashMap<>();
        for(Source source:sources){
            for(RuleSource rule:extractRules(source.content(),technology)){
                if(parsed.size()>=MAX_GRAMMAR_RULES)break;
                parsed.putIfAbsent(rule.name(),rule);
            }
            if(parsed.size()>=MAX_GRAMMAR_RULES)break;
        }
        LinkedHashSet<String> terminals=new LinkedHashSet<>();
        LinkedHashMap<String,RuleSource> parserSources=new LinkedHashMap<>();
        parsed.values().forEach(rule->{if(rule.parser())parserSources.put(rule.name(),rule);else terminals.add(rule.name());});
        if(parserSources.isEmpty())return unavailable(technology,snapshot.runtimeDescriptor().entryRule());

        String entry=text(snapshot.runtimeDescriptor().entryRule());
        if(entry.isBlank())entry=parserSources.values().stream().filter(RuleSource::explicitEntry).map(RuleSource::name).findFirst().orElse("");
        if(entry.isBlank())entry=parserSources.keySet().iterator().next();
        if(!parserSources.containsKey(entry))return unavailable(technology,entry);

        LinkedHashMap<String,Node> nodes=new LinkedHashMap<>();
        boolean complex=false;
        for(RuleSource source:parserSources.values()){
            ExpressionParser parser=new ExpressionParser(source.body(),MAX_GRAMMAR_NESTING);
            Node node=parser.parse();
            nodes.put(source.name(),node);
            complex|=parser.tooComplex();
        }
        LinkedHashMap<String,String> aliases=new LinkedHashMap<>();
        nodes.forEach((name,node)->ownFirstLiteral(node).filter(NeutralGrammarSyntaxContract::identifierLike).ifPresent(literal->aliases.putIfAbsent(literal,name)));
        List<String> incremental=incrementalCandidates(nodes.get(entry));
        LinkedHashSet<String> literals=new LinkedHashSet<>();nodes.values().forEach(node->collectLiterals(node,literals,0));

        Grammar grammar=null;
        boolean full=!complex;
        if(full){
            try{grammar=GrammarCompiler.compile(nodes,terminals,MAX_PRODUCTIONS);}
            catch(GrammarComplexityException ignored){full=false;}
        }
        return new NeutralGrammarSyntaxContract(technology,entry,nodes,terminals,aliases,incremental,literals,grammar,true,full&&grammar!=null);
    }

    /** Descripción: Ejecuta la operación «available» correspondiente a la responsabilidad de GrammarToken. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean available(){return available;}
    /** Descripción: Ejecuta la operación «entryRule» correspondiente a la responsabilidad de GrammarToken. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String entryRule(){return entryRule;}
    /** Descripción: Ejecuta la operación «typeAliases» correspondiente a la responsabilidad de GrammarToken. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Map<String,String> typeAliases(){return typeAliases;}

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: source: dato de entrada necesario para la operación; maximumDiagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ValidationResult validate(String source,int maximumDiagnostics){
        int max=Math.max(1,maximumDiagnostics);
        String input=source==null?"":source;
        if(!available){
            return new ValidationResult(List.of(new ValidationIssue(0,Math.min(1,input.length()),
                    "HYDRA no puede reconstruir el contrato sintáctico del snapshot de gramática activo; el borrador no puede considerarse válido sin una validación real contra esa gramática.",
                    Map.of("entryRule",entryRule,"strategy","CONTRACT_UNAVAILABLE","validationUnavailable","true","technology",technology.name()))),"CONTRACT_UNAVAILABLE",false);
        }
        if(!fullRecognitionAvailable){
            return new ValidationResult(List.of(new ValidationIssue(0,Math.min(1,input.length()),
                    "La gramática activa supera el subconjunto o los límites que el reconocedor neutral puede verificar con garantías. HYDRA conserva el borrador, pero no lo marcará como válido hasta disponer de un contrato verificable o del runtime generado de la gramática.",
                    Map.of("entryRule",entryRule,"strategy","CONTRACT_NOT_PROVABLE","validationUnavailable","true","technology",technology.name()))),"CONTRACT_NOT_PROVABLE",false);
        }
        source=input;
        List<ModelToken> tokens=tokenizeModel(source);
        if(tokens.isEmpty()){
            if(fullRecognitionAvailable){
                Recognition recognition=recognize(grammar,entryRule,tokens,MAX_EARLEY_STATES);
                if(!recognition.accepted())return mismatch(source,tokens,recognition,"FULL_ITERATIVE",max);
            }
            return new ValidationResult(List.of(),"EMPTY",false);
        }
        if(fullRecognitionAvailable&&source.length()<=FULL_MAX_CHARS&&tokens.size()<=FULL_MAX_TOKENS){
            Recognition recognition=recognize(grammar,entryRule,tokens,MAX_EARLEY_STATES);
            if(recognition.status()==RecognitionStatus.TOO_COMPLEX)return validateIncrementally(source,tokens,max,"INCREMENTAL_AFTER_STATE_BUDGET");
            return recognition.accepted()?new ValidationResult(List.of(),"FULL_ITERATIVE",false):mismatch(source,tokens,recognition,"FULL_ITERATIVE",max);
        }
        return validateIncrementally(source,tokens,max,"INCREMENTAL_BOUNDED");
    }

    /** Descripción: Valida las condiciones necesarias para «validateIncrementally» y rechaza entradas no válidas. Entradas: source: dato de entrada necesario para la operación; tokens: dato de entrada necesario para la operación; max: dato de entrada necesario para la operación; strategy: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationResult validateIncrementally(String source,List<ModelToken> tokens,int max,String strategy){
        if(!fullRecognitionAvailable||incrementalRules.isEmpty()){
            ValidationIssue first=firstTokenIssue(tokens);
            return first==null?new ValidationResult(List.of(),strategy,false):new ValidationResult(List.of(first),strategy,false);
        }
        List<TokenSlice> slices=topLevelSlices(source,tokens);
        if(slices.size()<=1&&tokens.size()>INCREMENTAL_MAX_TOKENS){
            ValidationIssue first=firstTokenIssue(tokens);
            return first==null?new ValidationResult(List.of(),strategy,false):new ValidationResult(List.of(first),strategy,false);
        }
        List<ValidationIssue> issues=new ArrayList<>();
        Map<String,Recognition> recognitionCache=new HashMap<>();
        boolean truncated=false;
        for(TokenSlice slice:slices){
            if(slice.tokens().isEmpty())continue;
            if(slice.tokens().size()>INCREMENTAL_MAX_TOKENS){
                ValidationIssue first=firstTokenIssue(slice.tokens());
                if(first!=null)issues.add(first);
                if(issues.size()>=max){truncated=true;break;}
                continue;
            }
            Recognition best=null;
            boolean accepted=false;
            String signature=tokenSignature(slice.tokens(),grammarLiterals);
            for(String candidate:incrementalRules){
                String cacheKey=candidate+'|'+signature;
                Recognition current=recognitionCache.computeIfAbsent(cacheKey,key->recognize(grammar,candidate,slice.tokens(),Math.min(MAX_EARLEY_STATES,120_000)));
                if(current.accepted()){accepted=true;break;}
                if(best==null||current.furthest()>best.furthest())best=current;
            }
            if(!accepted&&best!=null){
                issues.add(issueFromRecognition(slice.tokens(),best,strategy));
                if(issues.size()>=max){truncated=true;break;}
            }
        }
        return new ValidationResult(issues,strategy,truncated);
    }

    /** Descripción: Ejecuta la operación «firstTokenIssue» correspondiente a la responsabilidad de GrammarToken. Entradas: tokens: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationIssue firstTokenIssue(List<ModelToken> tokens){
        if(tokens.isEmpty())return null;
        Set<String> expected=firstExpected(parserRules.get(entryRule),parserRules,new HashSet<>());
        if(expected.isEmpty())return null;
        ModelToken first=tokens.get(0);
        boolean matches=expected.stream().anyMatch(value->expectedMatches(value,first));
        if(matches)return null;
        return new ValidationIssue(first.start(),first.end(),"El texto no comienza con una construcción aceptada por la regla de entrada \""+entryRule+"\".",Map.of(
                "entryRule",entryRule,"expected",String.join(", ",expected.stream().limit(12).toList()),"strategy","INCREMENTAL_BOUNDED"));
    }

    /** Descripción: Ejecuta la operación «mismatch» correspondiente a la responsabilidad de GrammarToken. Entradas: source: dato de entrada necesario para la operación; tokens: dato de entrada necesario para la operación; recognition: dato de entrada necesario para la operación; strategy: dato de entrada necesario para la operación; max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationResult mismatch(String source,List<ModelToken> tokens,Recognition recognition,String strategy,int max){
        if(max<=0)return new ValidationResult(List.of(),strategy,true);
        return new ValidationResult(List.of(issueFromRecognition(tokens,recognition,strategy)),strategy,false);
    }

    /** Descripción: Evalúa la condición «issueFromRecognition» sin modificar el estado externo. Entradas: tokens: dato de entrada necesario para la operación; recognition: dato de entrada necesario para la operación; strategy: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private ValidationIssue issueFromRecognition(List<ModelToken> tokens,Recognition recognition,String strategy){
        int position=Math.max(0,Math.min(recognition.furthest(),tokens.size()));
        int start=position<tokens.size()?tokens.get(position).start():(tokens.isEmpty()?0:tokens.get(tokens.size()-1).end());
        int end=position<tokens.size()?Math.max(start+1,tokens.get(position).end()):start;
        List<String> expectedOptions=recognition.expected().stream().limit(16).toList();
        String expected=String.join(", ",expectedOptions);
        String actual=position<tokens.size()?tokens.get(position).text():"<fin de archivo>";
        String ruleContext=recognition.contexts().stream().filter(value->!value.startsWith("$")).findFirst().orElse(entryRule);
        String expectedHuman=humanExpected(expectedOptions);
        String message=humanMismatchMessage(ruleContext,actual,expectedHuman,position>=tokens.size());
        LinkedHashMap<String,String> metadata=new LinkedHashMap<>();
        metadata.put("entryRule",entryRule);metadata.put("ruleContext",ruleContext);metadata.put("expected",expected);metadata.put("expectedHuman",expectedHuman);
        metadata.put("actual",actual);metadata.put("strategy",strategy);metadata.put("furthestToken",Integer.toString(position));
        SuggestedFix fix=suggestedFix(expectedOptions,position>=tokens.size());
        if(fix!=null){metadata.put("quickFixKind",fix.kind());metadata.put("quickFixReplacement",fix.replacement());metadata.put("quickFixLabel",fix.label());}
        return new ValidationIssue(start,end,message,metadata);
    }

    /** Descripción: Convierte las expectativas internas del reconocedor en una explicación breve orientada al usuario. Entradas: expected: terminales o literales aceptables en la posición del error. Salida: devuelve una descripción legible de las opciones esperadas. */ private static String humanExpected(List<String> expected){
        if(expected==null||expected.isEmpty())return "una construcción válida";
        List<String> values=expected.stream().map(NeutralGrammarSyntaxContract::humanExpectedToken).distinct().limit(6).toList();
        if(values.size()==1)return values.get(0);
        return String.join(", ",values.subList(0,values.size()-1))+" o "+values.get(values.size()-1);
    }

    /** Descripción: Traduce un terminal o literal esperado a lenguaje sencillo. Entradas: value: representación interna del símbolo esperado. Salida: devuelve la explicación comprensible del símbolo. */ private static String humanExpectedToken(String value){
        String token=text(value);
        if(token.startsWith("'")&&token.endsWith("'")&&token.length()>=2)return "la palabra reservada \""+token.substring(1,token.length()-1)+"\"";
        if(token.startsWith("<")&&token.endsWith(">")&&token.length()>=3){
            String terminal=token.substring(1,token.length()-1).toUpperCase(Locale.ROOT);
            if(IDENT_TERMINALS.contains(terminal))return "un identificador";
            if(STRING_TERMINALS.contains(terminal))return "un texto entre comillas";
            if(NUMBER_TERMINALS.contains(terminal))return "un número";
            if(BOOLEAN_TERMINALS.contains(terminal))return "true o false";
            return "un valor "+token;
        }
        return token;
    }

    /** Descripción: Construye un mensaje sintáctico que indica la regla concreta, qué se encontró y qué esperaba la gramática. Entradas: ruleContext: regla donde falla; actual: token encontrado; expectedHuman: expectativa explicada; eof: indica fin de archivo. Salida: devuelve un mensaje de diagnóstico específico. */ private static String humanMismatchMessage(String ruleContext,String actual,String expectedHuman,boolean eof){
        String rule=ruleContext==null||ruleContext.isBlank()?"activa":"\""+ruleContext+"\"";
        if(eof)return "La regla "+rule+" está incompleta: al final del texto falta "+expectedHuman+".";
        return "En la regla "+rule+", HYDRA encontró \""+actual+"\" donde la gramática espera "+expectedHuman+".";
    }

    /** Descripción: Propone una corrección sintáctica local cuando existe una sustitución inequívoca y segura. Entradas: expected: alternativas sintácticas esperadas; eof: indica si falta contenido al final. Salida: devuelve una corrección segura o null cuando hay ambigüedad. */ private static SuggestedFix suggestedFix(List<String> expected,boolean eof){
        if(expected==null||expected.size()!=1)return null;
        String token=text(expected.get(0));String replacement="";String label="";
        if(token.startsWith("'")&&token.endsWith("'")&&token.length()>=2){replacement=token.substring(1,token.length()-1);label=(eof?"Insertar ":"Sustituir por ")+"\""+replacement+"\"";}
        else if(token.startsWith("<")&&token.endsWith(">")&&token.length()>=3){
            String terminal=token.substring(1,token.length()-1).toUpperCase(Locale.ROOT);
            if(IDENT_TERMINALS.contains(terminal)){replacement="valor";label=(eof?"Insertar":"Usar")+" un identificador de ejemplo";}
            else if(STRING_TERMINALS.contains(terminal)){replacement="\"texto\"";label=(eof?"Insertar":"Usar")+" un texto de ejemplo";}
            else if(NUMBER_TERMINALS.contains(terminal)){replacement="0";label=(eof?"Insertar":"Usar")+" un número de ejemplo";}
            else if(BOOLEAN_TERMINALS.contains(terminal)){replacement="true";label=(eof?"Insertar":"Usar")+" un valor booleano";}
        }
        if(replacement.isBlank())return null;
        return new SuggestedFix(eof?"INSERT":"REPLACE",replacement,label);

    }

    /** Descripción: Ejecuta la operación «unavailable» correspondiente a la responsabilidad de GrammarToken. Entradas: technology: dato de entrada necesario para la operación; entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static NeutralGrammarSyntaxContract unavailable(GrammarTechnology technology,String entry){
        return new NeutralGrammarSyntaxContract(technology,entry,Map.of(),Set.of(),Map.of(),List.of(),Set.of(),null,false,false);
    }

    /** Descripción: Ejecuta la operación «grammarSources» correspondiente a la responsabilidad de GrammarToken. Entradas: snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<Source> grammarSources(GrammarCompilationSnapshot snapshot){
        String suffix=switch(snapshot.technology()){
            case XTEXT -> ".xtext";
            case LANGIUM -> ".langium";
            case ANTLR -> ".g4";
        };
        return snapshot.generatedProject().files().entrySet().stream()
                .filter(entry->entry.getKey().toLowerCase(Locale.ROOT).endsWith(suffix))
                .sorted(Map.Entry.comparingByKey())
                .map(entry->new Source(entry.getKey(),entry.getValue()==null?"":entry.getValue())).toList();
    }

    /** Descripción: Ejecuta la operación «extractRules» correspondiente a la responsabilidad de GrammarToken. Entradas: source: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<RuleSource> extractRules(String source,GrammarTechnology technology){
        String masked=maskGrammar(source,technology);
        Pattern pattern=technology==GrammarTechnology.ANTLR
                ?Pattern.compile("(?m)^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^\\]]*\\]\\s*)?(?:returns\\s*\\[[^\\]]*\\]\\s*)?(?:locals\\s*\\[[^\\]]*\\]\\s*)?:")
                :Pattern.compile("(?m)^\\s*(?:(entry)\\s+)?(?:(hidden)\\s+)?(?:(terminal|fragment)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\b[^\\r\\n:;]*:");
        List<Header> headers=new ArrayList<>();
        Matcher matcher=pattern.matcher(masked);
        while(matcher.find()){
            String name=technology==GrammarTechnology.ANTLR?matcher.group(1):matcher.group(4);
            boolean terminal=technology==GrammarTechnology.ANTLR?Character.isUpperCase(name.charAt(0)):matcher.group(3)!=null;
            boolean parser=!terminal&&(technology!=GrammarTechnology.ANTLR||Character.isLowerCase(name.charAt(0)));
            boolean explicit=technology!=GrammarTechnology.ANTLR&&matcher.group(1)!=null;
            int colon=matcher.end()-1;
            headers.add(new Header(name,parser,explicit,matcher.start(),colon));
        }
        List<RuleSource> result=new ArrayList<>();
        for(int i=0;i<headers.size();i++){
            Header header=headers.get(i);int next=i+1<headers.size()?headers.get(i+1).start():masked.length();
            int semi=masked.indexOf(';',header.colon()+1);
            if(semi<0||semi>=next)continue;
            result.add(new RuleSource(header.name(),header.parser(),header.explicitEntry(),source.substring(header.colon()+1,semi)));
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «maskGrammar» correspondiente a la responsabilidad de GrammarToken. Entradas: source: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String maskGrammar(String source,GrammarTechnology technology){
        char[] chars=source.toCharArray();
        ScanState state=ScanState.NORMAL;boolean escaped=false;
        for(int i=0;i<chars.length;i++){
            char current=source.charAt(i),next=i+1<source.length()?source.charAt(i+1):'\0';
            if(state==ScanState.NORMAL){
                if(current=='/'&&next=='/'){blank(chars,i);blank(chars,++i);state=ScanState.LINE;continue;}
                if(current=='/'&&next=='*'){blank(chars,i);blank(chars,++i);state=ScanState.BLOCK;continue;}
                if(current=='\''){blank(chars,i);state=ScanState.SINGLE;escaped=false;continue;}
                if(current=='"'){blank(chars,i);state=ScanState.DOUBLE;escaped=false;continue;}
                if(current=='/'&&technology==GrammarTechnology.LANGIUM&&canStartRegex(source,i)){blank(chars,i);state=ScanState.REGEX;escaped=false;continue;}
                continue;
            }
            if(state==ScanState.LINE){if(current=='\n'||current=='\r')state=ScanState.NORMAL;else blank(chars,i);continue;}
            if(state==ScanState.BLOCK){if(current=='*'&&next=='/'){blank(chars,i);blank(chars,++i);state=ScanState.NORMAL;}else blank(chars,i);continue;}
            blank(chars,i);
            if(escaped){escaped=false;continue;}
            if(current=='\\'){escaped=true;continue;}
            if((state==ScanState.SINGLE&&current=='\'')||(state==ScanState.DOUBLE&&current=='"')||(state==ScanState.REGEX&&current=='/'))state=ScanState.NORMAL;
        }
        return new String(chars);
    }

    /** Descripción: Evalúa la condición «canStartRegex» sin modificar el estado externo. Entradas: source: dato de entrada necesario para la operación; offset: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean canStartRegex(String source,int offset){
        for(int i=offset-1;i>=0;i--)if(!Character.isWhitespace(source.charAt(i)))return source.charAt(i)!='/';
        return true;
    }
    /** Descripción: Ejecuta la operación «blank» correspondiente a la responsabilidad de GrammarToken. Entradas: chars: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void blank(char[] chars,int index){if(index>=0&&index<chars.length&&chars[index]!='\n'&&chars[index]!='\r')chars[index]=' ';}

    /** Descripción: Ejecuta la operación «tokenizeModel» correspondiente a la responsabilidad de GrammarToken. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<ModelToken> tokenizeModel(String source){
        List<ModelToken> result=new ArrayList<>();
        int i=0;
        while(i<source.length()){
            char c=source.charAt(i);
            if(Character.isWhitespace(c)){i++;continue;}
            if(c=='/'&&i+1<source.length()&&source.charAt(i+1)=='/'){i+=2;while(i<source.length()&&source.charAt(i)!='\n'&&source.charAt(i)!='\r')i++;continue;}
            if(c=='/'&&i+1<source.length()&&source.charAt(i+1)=='*'){i+=2;while(i+1<source.length()&&!(source.charAt(i)=='*'&&source.charAt(i+1)=='/'))i++;i=Math.min(source.length(),i+2);continue;}
            if(c=='#'){while(i<source.length()&&source.charAt(i)!='\n'&&source.charAt(i)!='\r')i++;continue;}
            int start=i;
            if(c=='\''||c=='"'){
                char quote=c;i++;boolean escaped=false;
                while(i<source.length()){
                    char value=source.charAt(i++);
                    if(escaped){escaped=false;continue;}
                    if(value=='\\'){escaped=true;continue;}
                    if(value==quote)break;
                }
                result.add(new ModelToken(source.substring(start,i),ModelTokenKind.STRING,start,i));continue;
            }
            if(Character.isLetter(c)||c=='_'){
                i++;while(i<source.length()&&(Character.isLetterOrDigit(source.charAt(i))||source.charAt(i)=='_'))i++;
                result.add(new ModelToken(source.substring(start,i),ModelTokenKind.IDENT,start,i));continue;
            }
            if(Character.isDigit(c)){
                i++;while(i<source.length()&&Character.isDigit(source.charAt(i)))i++;
                if(i<source.length()&&source.charAt(i)=='.'&&i+1<source.length()&&Character.isDigit(source.charAt(i+1))){i++;while(i<source.length()&&Character.isDigit(source.charAt(i)))i++;}
                result.add(new ModelToken(source.substring(start,i),ModelTokenKind.NUMBER,start,i));continue;
            }
            String two=i+1<source.length()?source.substring(i,i+2):"";
            if(Set.of("->","=>","::","+=","-=","*=","/=","==","!=","<=",">=","&&","||").contains(two)){i+=2;result.add(new ModelToken(two,ModelTokenKind.PUNCT,start,i));continue;}
            i++;result.add(new ModelToken(String.valueOf(c),ModelTokenKind.PUNCT,start,i));
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «tokenSignature» correspondiente a la responsabilidad de GrammarToken. Entradas: tokens: dato de entrada necesario para la operación; literals: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String tokenSignature(List<ModelToken> tokens,Set<String> literals){
        StringBuilder out=new StringBuilder(Math.min(512,tokens.size()*4));
        for(ModelToken token:tokens){
            switch(token.kind()){
                case IDENT -> out.append(literals.contains(token.text())?'L':'I').append(literals.contains(token.text())?token.text():"");
                case NUMBER -> out.append('N');
                case STRING -> out.append('S');
                case PUNCT -> out.append('P').append(token.text());
            }
            out.append(';');
            if(out.length()>2_048)break;
        }
        return out.toString();
    }

    /** Descripción: Ejecuta la operación «topLevelSlices» correspondiente a la responsabilidad de GrammarToken. Entradas: source: dato de entrada necesario para la operación; tokens: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<TokenSlice> topLevelSlices(String source,List<ModelToken> tokens){
        if(tokens.isEmpty())return List.of();
        List<TokenSlice> result=new ArrayList<>();int start=0,depth=0;
        for(int i=0;i<tokens.size();i++){
            ModelToken token=tokens.get(i);
            if(i>start&&depth==0){
                ModelToken previous=tokens.get(i-1);
                String gap=source.substring(Math.min(previous.end(),source.length()),Math.min(token.start(),source.length()));
                if(gap.indexOf('\n')>=0||gap.indexOf('\r')>=0){result.add(new TokenSlice(List.copyOf(tokens.subList(start,i))));start=i;}
            }
            if(token.text().equals("{")||token.text().equals("(")||token.text().equals("["))depth++;
            else if(token.text().equals("}")||token.text().equals(")")||token.text().equals("]"))depth=Math.max(0,depth-1);
            if(depth==0&&token.text().equals("}")&&i+1>start){result.add(new TokenSlice(List.copyOf(tokens.subList(start,i+1))));start=i+1;}
        }
        if(start<tokens.size())result.add(new TokenSlice(List.copyOf(tokens.subList(start,tokens.size()))));
        return result;
    }

    /** Descripción: Ejecuta la operación «incrementalCandidates» correspondiente a la responsabilidad de GrammarToken. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> incrementalCandidates(Node entry){
        LinkedHashSet<String> result=new LinkedHashSet<>();collectRepeatedRefs(entry,false,result,0);
        return List.copyOf(result);
    }
    /** Descripción: Ejecuta la operación «collectRepeatedRefs» correspondiente a la responsabilidad de GrammarToken. Entradas: node: dato de entrada necesario para la operación; repeated: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void collectRepeatedRefs(Node node,boolean repeated,Set<String> out,int depth){
        if(node==null||depth>MAX_GRAMMAR_NESTING)return;
        if(node instanceof RepeatNode repeat){collectRepeatedRefs(repeat.child(),true,out,depth+1);return;}
        if(node instanceof RefNode ref){if(repeated)out.add(ref.name());return;}
        if(node instanceof SeqNode seq)for(Node child:seq.children())collectRepeatedRefs(child,repeated,out,depth+1);
        else if(node instanceof ChoiceNode choice)for(Node child:choice.options())collectRepeatedRefs(child,repeated,out,depth+1);
    }

    /** Descripción: Ejecuta la operación «collectLiterals» correspondiente a la responsabilidad de GrammarToken. Entradas: node: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void collectLiterals(Node node,Set<String> out,int depth){
        if(node==null||depth>MAX_GRAMMAR_NESTING||out.size()>2_048)return;
        if(node instanceof LitNode literal){out.add(literal.text());return;}
        if(node instanceof RepeatNode repeat){collectLiterals(repeat.child(),out,depth+1);return;}
        if(node instanceof SeqNode seq)for(Node child:seq.children())collectLiterals(child,out,depth+1);
        else if(node instanceof ChoiceNode choice)for(Node child:choice.options())collectLiterals(child,out,depth+1);
    }

    /** Descripción: Ejecuta la operación «ownFirstLiteral» correspondiente a la responsabilidad de GrammarToken. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Optional<String> ownFirstLiteral(Node node){
        if(node==null)return Optional.empty();
        if(node instanceof LitNode literal)return Optional.of(literal.text());
        if(node instanceof RepeatNode repeat)return ownFirstLiteral(repeat.child());
        if(node instanceof SeqNode seq){for(Node child:seq.children()){Optional<String> value=ownFirstLiteral(child);if(value.isPresent())return value;if(!(child instanceof EmptyNode)&&!(child instanceof RepeatNode repeat&&repeat.min()==0))break;}return Optional.empty();}
        if(node instanceof ChoiceNode choice){String common=null;for(Node child:choice.options()){Optional<String> value=ownFirstLiteral(child);if(value.isEmpty())return Optional.empty();if(common==null)common=value.get();else if(!common.equals(value.get()))return Optional.empty();}return Optional.ofNullable(common);}
        return Optional.empty();
    }

    /** Descripción: Ejecuta la operación «firstLiteral» correspondiente a la responsabilidad de GrammarToken. Entradas: node: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; visiting: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Optional<String> firstLiteral(Node node,Map<String,Node> rules,Set<String> visiting){
        if(node==null)return Optional.empty();
        if(node instanceof LitNode literal)return Optional.of(literal.text());
        if(node instanceof RepeatNode repeat)return firstLiteral(repeat.child(),rules,visiting);
        if(node instanceof SeqNode seq){for(Node child:seq.children()){Optional<String> value=firstLiteral(child,rules,visiting);if(value.isPresent())return value;if(!nullable(child,rules,new HashSet<>()))break;}return Optional.empty();}
        if(node instanceof ChoiceNode choice){String common=null;for(Node child:choice.options()){Optional<String> value=firstLiteral(child,rules,visiting);if(value.isEmpty())return Optional.empty();if(common==null)common=value.get();else if(!common.equals(value.get()))return Optional.empty();}return Optional.ofNullable(common);}
        if(node instanceof RefNode ref&&visiting.add(ref.name())){Optional<String> value=firstLiteral(rules.get(ref.name()),rules,visiting);visiting.remove(ref.name());return value;}
        return Optional.empty();
    }

    /** Descripción: Ejecuta la operación «firstExpected» correspondiente a la responsabilidad de GrammarToken. Entradas: node: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; visiting: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Set<String> firstExpected(Node node,Map<String,Node> rules,Set<String> visiting){
        LinkedHashSet<String> out=new LinkedHashSet<>();firstExpected(node,rules,visiting,out,0);return out;
    }
    /** Descripción: Ejecuta la operación «firstExpected» correspondiente a la responsabilidad de GrammarToken. Entradas: node: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; visiting: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void firstExpected(Node node,Map<String,Node> rules,Set<String> visiting,Set<String> out,int depth){
        if(node==null||depth>MAX_GRAMMAR_NESTING||out.size()>=32)return;
        if(node instanceof LitNode literal){out.add("'"+literal.text()+"'");return;}
        if(node instanceof RefNode ref){if(rules.containsKey(ref.name())&&visiting.add(ref.name())){firstExpected(rules.get(ref.name()),rules,visiting,out,depth+1);visiting.remove(ref.name());}else out.add("<"+ref.name()+">");return;}
        if(node instanceof RepeatNode repeat){firstExpected(repeat.child(),rules,visiting,out,depth+1);return;}
        if(node instanceof ChoiceNode choice){for(Node child:choice.options())firstExpected(child,rules,visiting,out,depth+1);return;}
        if(node instanceof SeqNode seq){for(Node child:seq.children()){firstExpected(child,rules,visiting,out,depth+1);if(!nullable(child,rules,new HashSet<>()))break;}}
    }
    /** Descripción: Ejecuta la operación «expectedMatches» correspondiente a la responsabilidad de GrammarToken. Entradas: expected: dato de entrada necesario para la operación; token: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean expectedMatches(String expected,ModelToken token){
        if(expected.startsWith("'")&&expected.endsWith("'"))return token.text().equals(expected.substring(1,expected.length()-1));
        if(expected.startsWith("<")&&expected.endsWith(">"))return terminalMatches(expected.substring(1,expected.length()-1),token);
        return false;
    }

    /** Descripción: Ejecuta la operación «nullable» correspondiente a la responsabilidad de GrammarToken. Entradas: node: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; visiting: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean nullable(Node node,Map<String,Node> rules,Set<String> visiting){
        if(node==null||node instanceof EmptyNode)return true;
        if(node instanceof RepeatNode repeat)return repeat.min()==0||nullable(repeat.child(),rules,visiting);
        if(node instanceof SeqNode seq)return seq.children().stream().allMatch(child->nullable(child,rules,new HashSet<>(visiting)));
        if(node instanceof ChoiceNode choice)return choice.options().stream().anyMatch(child->nullable(child,rules,new HashSet<>(visiting)));
        if(node instanceof RefNode ref&&rules.containsKey(ref.name())&&visiting.add(ref.name())){boolean value=nullable(rules.get(ref.name()),rules,visiting);visiting.remove(ref.name());return value;}
        return false;
    }

    /** Descripción: Ejecuta la operación «recognize» correspondiente a la responsabilidad de GrammarToken. Entradas: grammar: dato de entrada necesario para la operación; startRule: dato de entrada necesario para la operación; tokens: dato de entrada necesario para la operación; maxStates: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Recognition recognize(Grammar grammar,String startRule,List<ModelToken> tokens,int maxStates){
        if(grammar==null||!grammar.byLhs().containsKey(startRule))return new Recognition(false,RecognitionStatus.INVALID_GRAMMAR,0,Set.of(),Set.of());
        Production start=new Production("$START$",List.of(new Nonterminal(startRule)));
        List<Chart> chart=new ArrayList<>(tokens.size()+1);for(int i=0;i<=tokens.size();i++)chart.add(new Chart());
        chart.get(0).add(new State(start,0,0));int states=1;int furthest=0;LinkedHashSet<String> expectedAtFurthest=new LinkedHashSet<>();LinkedHashSet<String> contextsAtFurthest=new LinkedHashSet<>();
        for(int position=0;position<=tokens.size();position++){
            Chart current=chart.get(position);
            for(int cursor=0;cursor<current.states.size();cursor++){
                State state=current.states.get(cursor);
                if(states>maxStates)return new Recognition(false,RecognitionStatus.TOO_COMPLEX,furthest,expectedAtFurthest,contextsAtFurthest);
                if(state.complete()){
                    Chart origin=chart.get(state.origin());
                    for(int j=0;j<origin.states.size();j++){
                        State parent=origin.states.get(j);Symbol next=parent.next();
                        if(next instanceof Nonterminal nt&&nt.name().equals(state.production().lhs()))if(current.add(parent.advance()))states++;
                    }
                    continue;
                }
                Symbol next=state.next();
                if(next instanceof Nonterminal nt){
                    for(Production production:grammar.byLhs().getOrDefault(nt.name(),List.of()))if(current.add(new State(production,0,position)))states++;
                    continue;
                }
                Terminal terminal=(Terminal)next;
                if(position<tokens.size()&&terminal.matches(tokens.get(position))){if(chart.get(position+1).add(state.advance()))states++;furthest=Math.max(furthest,position+1);}
                else if(position>=furthest){if(position>furthest){expectedAtFurthest.clear();contextsAtFurthest.clear();furthest=position;}expectedAtFurthest.add(terminal.label());contextsAtFurthest.add(state.production().lhs());}
            }
        }
        boolean accepted=chart.get(tokens.size()).contains(new State(start,1,0));
        if(accepted)return new Recognition(true,RecognitionStatus.ACCEPTED,tokens.size(),Set.of(),Set.of());
        return new Recognition(false,RecognitionStatus.REJECTED,furthest,expectedAtFurthest,contextsAtFurthest);
    }

    /** Descripción: Ejecuta la operación «terminalMatches» correspondiente a la responsabilidad de GrammarToken. Entradas: name: dato de entrada necesario para la operación; token: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean terminalMatches(String name,ModelToken token){
        String upper=name.toUpperCase(Locale.ROOT);
        if(IDENT_TERMINALS.contains(upper))return token.kind()==ModelTokenKind.IDENT;
        if(NUMBER_TERMINALS.contains(upper))return token.kind()==ModelTokenKind.NUMBER;
        if(STRING_TERMINALS.contains(upper))return token.kind()==ModelTokenKind.STRING;
        if(BOOLEAN_TERMINALS.contains(upper))return token.text().equals("true")||token.text().equals("false");
        if(upper.equals("EOF"))return false;
        if(upper.equals("WS"))return false;
        if(ANY_TERMINALS.contains(upper))return true;
        return token.kind()==ModelTokenKind.IDENT||token.kind()==ModelTokenKind.STRING||token.kind()==ModelTokenKind.NUMBER;
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarToken. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
    /** Descripción: Ejecuta la operación «identifierLike» correspondiente a la responsabilidad de GrammarToken. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean identifierLike(String value){return value!=null&&value.matches("[A-Za-z_][A-Za-z0-9_-]*");}

    private enum ScanState{NORMAL,LINE,BLOCK,SINGLE,DOUBLE,REGEX}
    private record Source(String path,String content){}
    private record Header(String name,boolean parser,boolean explicitEntry,int start,int colon){}
    private record RuleSource(String name,boolean parser,boolean explicitEntry,String body){}
    private enum ModelTokenKind{IDENT,NUMBER,STRING,PUNCT}
    private record ModelToken(String text,ModelTokenKind kind,int start,int end){}
    private record TokenSlice(List<ModelToken> tokens){}

    private interface Node{}
    private record EmptyNode() implements Node{}
    private record LitNode(String text) implements Node{}
    private record RefNode(String name) implements Node{}
    private record SeqNode(List<Node> children) implements Node{SeqNode{children=List.copyOf(children);}}
    private record ChoiceNode(List<Node> options) implements Node{ChoiceNode{options=List.copyOf(options);}}
    private record RepeatNode(Node child,int min,int max) implements Node{}

    private static final class ExpressionParser{
        private final List<GrammarToken> tokens;private final int maxDepth;private int index;private boolean tooComplex;
        /** Descripción: Inicializa una instancia de ExpressionParser con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación; maxDepth: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ ExpressionParser(String source,int maxDepth){this.tokens=tokenizeGrammarExpression(source);this.maxDepth=maxDepth;}
        /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de GrammarToken. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Node parse(){try{return parseChoice(0);}catch(GrammarComplexityException ex){tooComplex=true;return new EmptyNode();}}
        /** Descripción: Ejecuta la operación «tooComplex» correspondiente a la responsabilidad de GrammarToken. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean tooComplex(){return tooComplex;}
        /** Descripción: Ejecuta la operación «parseChoice» correspondiente a la responsabilidad de GrammarToken. Entradas: depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Node parseChoice(int depth){guard(depth);List<Node> options=new ArrayList<>();options.add(parseSequence(depth+1));while(match(GrammarTokenKind.ALT))options.add(parseSequence(depth+1));return options.size()==1?options.get(0):new ChoiceNode(options);}
        /** Descripción: Ejecuta la operación «parseSequence» correspondiente a la responsabilidad de GrammarToken. Entradas: depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Node parseSequence(int depth){guard(depth);List<Node> children=new ArrayList<>();while(!atEnd()&&!check(GrammarTokenKind.RPAREN)&&!check(GrammarTokenKind.ALT)&&!check(GrammarTokenKind.RBRACKET)){
                Node next=parsePostfix(depth+1);if(!(next instanceof EmptyNode))children.add(next);
            }return children.isEmpty()?new EmptyNode():children.size()==1?children.get(0):new SeqNode(children);}
        /** Descripción: Ejecuta la operación «parsePostfix» correspondiente a la responsabilidad de GrammarToken. Entradas: depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Node parsePostfix(int depth){guard(depth);Node node=parsePrimary(depth+1);if(match(GrammarTokenKind.QMARK))return new RepeatNode(node,0,1);if(match(GrammarTokenKind.STAR))return new RepeatNode(node,0,-1);if(match(GrammarTokenKind.PLUS))return new RepeatNode(node,1,-1);return node;}
        /** Descripción: Ejecuta la operación «parsePrimary» correspondiente a la responsabilidad de GrammarToken. Entradas: depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Node parsePrimary(int depth){guard(depth);if(atEnd())return new EmptyNode();
            if(match(GrammarTokenKind.LPAREN)){Node value=parseChoice(depth+1);match(GrammarTokenKind.RPAREN);return value;}
            if(match(GrammarTokenKind.LBRACKET)){String terminal="ID";while(!atEnd()&&!check(GrammarTokenKind.RBRACKET)){GrammarToken token=advance();if(token.kind()==GrammarTokenKind.IDENT)terminal=token.text();if(token.kind()==GrammarTokenKind.ALT)terminal="ID";}match(GrammarTokenKind.RBRACKET);return new RefNode(terminal);}
            if(match(GrammarTokenKind.LBRACE)){int nested=1;while(!atEnd()&&nested>0){GrammarToken token=advance();if(token.kind()==GrammarTokenKind.LBRACE)nested++;else if(token.kind()==GrammarTokenKind.RBRACE)nested--;}return new EmptyNode();}
            if(check(GrammarTokenKind.IDENT)&&peekNext(GrammarTokenKind.ASSIGN)){advance();advance();return parsePostfix(depth+1);}
            GrammarToken token=advance();
            if(token.kind()==GrammarTokenKind.STRING)return new LitNode(token.text());
            if(token.kind()==GrammarTokenKind.IDENT){if(token.text().equals("EOF"))return new EmptyNode();return new RefNode(token.text());}
            if(token.kind()==GrammarTokenKind.DOT)return new RefNode("ANY");
            return new EmptyNode();
        }
        /** Descripción: Ejecuta la operación «guard» correspondiente a la responsabilidad de GrammarToken. Entradas: depth: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void guard(int depth){if(depth>maxDepth)throw new GrammarComplexityException();}
        /** Descripción: Ejecuta la operación «match» correspondiente a la responsabilidad de GrammarToken. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean match(GrammarTokenKind kind){if(check(kind)){index++;return true;}return false;}
        /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean check(GrammarTokenKind kind){return !atEnd()&&tokens.get(index).kind()==kind;}
        /** Descripción: Ejecuta la operación «peekNext» correspondiente a la responsabilidad de GrammarToken. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean peekNext(GrammarTokenKind kind){return index+1<tokens.size()&&tokens.get(index+1).kind()==kind;}
        /** Descripción: Ejecuta la operación «advance» correspondiente a la responsabilidad de GrammarToken. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarToken advance(){return atEnd()?new GrammarToken(GrammarTokenKind.OTHER,""):tokens.get(index++);}
        /** Descripción: Ejecuta la operación «atEnd» correspondiente a la responsabilidad de GrammarToken. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean atEnd(){return index>=tokens.size();}
    }

    private enum GrammarTokenKind{IDENT,STRING,LPAREN,RPAREN,LBRACKET,RBRACKET,LBRACE,RBRACE,ALT,QMARK,STAR,PLUS,ASSIGN,DOT,OTHER}
    private record GrammarToken(GrammarTokenKind kind,String text){}
    /** Descripción: Ejecuta la operación «tokenizeGrammarExpression» correspondiente a la responsabilidad de GrammarToken. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<GrammarToken> tokenizeGrammarExpression(String source){
        List<GrammarToken> result=new ArrayList<>();int i=0;
        while(i<source.length()){
            char c=source.charAt(i);if(Character.isWhitespace(c)){i++;continue;}
            if(c=='/'&&i+1<source.length()&&source.charAt(i+1)=='/'){i+=2;while(i<source.length()&&source.charAt(i)!='\n'&&source.charAt(i)!='\r')i++;continue;}
            if(c=='/'&&i+1<source.length()&&source.charAt(i+1)=='*'){i+=2;while(i+1<source.length()&&!(source.charAt(i)=='*'&&source.charAt(i+1)=='/'))i++;i=Math.min(source.length(),i+2);continue;}
            if(c=='\''||c=='"'){char quote=c;i++;StringBuilder value=new StringBuilder();boolean escaped=false;while(i<source.length()){char current=source.charAt(i++);if(escaped){value.append(current);escaped=false;continue;}if(current=='\\'){escaped=true;continue;}if(current==quote)break;value.append(current);}result.add(new GrammarToken(GrammarTokenKind.STRING,value.toString()));continue;}
            if(Character.isLetter(c)||c=='_'){int start=i++;while(i<source.length()&&(Character.isLetterOrDigit(source.charAt(i))||source.charAt(i)=='_'||source.charAt(i)=='.'))i++;result.add(new GrammarToken(GrammarTokenKind.IDENT,source.substring(start,i)));continue;}
            if((c=='+'||c=='?'||c=='=')&&i+1<source.length()&&source.charAt(i+1)=='='){result.add(new GrammarToken(GrammarTokenKind.ASSIGN,source.substring(i,i+2)));i+=2;continue;}
            GrammarTokenKind kind=switch(c){case '('->GrammarTokenKind.LPAREN;case ')'->GrammarTokenKind.RPAREN;case '['->GrammarTokenKind.LBRACKET;case ']'->GrammarTokenKind.RBRACKET;case '{'->GrammarTokenKind.LBRACE;case '}'->GrammarTokenKind.RBRACE;case '|'->GrammarTokenKind.ALT;case '?'->GrammarTokenKind.QMARK;case '*'->GrammarTokenKind.STAR;case '+'->GrammarTokenKind.PLUS;case '='->GrammarTokenKind.ASSIGN;case '.'->GrammarTokenKind.DOT;default->GrammarTokenKind.OTHER;};
            result.add(new GrammarToken(kind,String.valueOf(c)));i++;
        }
        return result;
    }

    private interface Symbol{}
    private record Nonterminal(String name) implements Symbol{}
    private record Terminal(String kind,String value) implements Symbol{
        /** Descripción: Evalúa la condición «matches» sin modificar el estado externo. Entradas: token: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean matches(ModelToken token){return kind.equals("LITERAL")?token.text().equals(value):terminalMatches(value,token);}
        /** Descripción: Ejecuta la operación «label» correspondiente a la responsabilidad de GrammarToken. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String label(){return kind.equals("LITERAL")?"'"+value+"'":"<"+value+">";}
    }
    private record Production(String lhs,List<Symbol> rhs){Production{rhs=List.copyOf(rhs);}}
    private record Grammar(Map<String,List<Production>> byLhs){Grammar{Map<String,List<Production>> copy=new LinkedHashMap<>();byLhs.forEach((key,value)->copy.put(key,List.copyOf(value)));byLhs=Map.copyOf(copy);}}
    private record State(Production production,int dot,int origin){boolean complete(){return dot>=production.rhs().size();}/** Descripción: Ejecuta la operación «next» correspondiente a la responsabilidad de GrammarToken. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Symbol next(){return complete()?null:production.rhs().get(dot);}/** Descripción: Ejecuta la operación «advance» correspondiente a la responsabilidad de GrammarToken. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ State advance(){return new State(production,dot+1,origin);}}
    private static final class Chart{private final List<State> states=new ArrayList<>();private final Set<State> unique=new HashSet<>();/** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de GrammarToken. Entradas: state: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean add(State state){if(!unique.add(state))return false;states.add(state);return true;}/** Descripción: Evalúa la condición «contains» sin modificar el estado externo. Entradas: state: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean contains(State state){return unique.contains(state);}}
    private enum RecognitionStatus{ACCEPTED,REJECTED,TOO_COMPLEX,INVALID_GRAMMAR}
    private record Recognition(boolean accepted,RecognitionStatus status,int furthest,Set<String> expected,Set<String> contexts){Recognition{expected=expected==null?Set.of():Collections.unmodifiableSet(new LinkedHashSet<>(expected));contexts=contexts==null?Set.of():Collections.unmodifiableSet(new LinkedHashSet<>(contexts));}}
    private record SuggestedFix(String kind,String replacement,String label){}

    private static final class GrammarCompiler{
        private final Map<String,Node> rules;private final Set<String> terminals;private final int maxProductions;private final Map<String,List<Production>> productions=new LinkedHashMap<>();private int synthetic;
        /** Descripción: Inicializa una instancia de GrammarCompiler con sus dependencias y datos de entrada. Entradas: rules: dato de entrada necesario para la operación; terminals: dato de entrada necesario para la operación; maxProductions: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private GrammarCompiler(Map<String,Node> rules,Set<String> terminals,int maxProductions){this.rules=rules;this.terminals=terminals;this.maxProductions=maxProductions;}
        /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de GrammarToken. Entradas: rules: dato de entrada necesario para la operación; terminals: dato de entrada necesario para la operación; maxProductions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Grammar compile(Map<String,Node> rules,Set<String> terminals,int maxProductions){GrammarCompiler compiler=new GrammarCompiler(rules,terminals,maxProductions);for(Map.Entry<String,Node> entry:rules.entrySet())compiler.lowerInto(entry.getKey(),entry.getValue(),0);return new Grammar(compiler.productions);}
        /** Descripción: Ejecuta la operación «lowerInto» correspondiente a la responsabilidad de GrammarToken. Entradas: lhs: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void lowerInto(String lhs,Node node,int depth){if(depth>MAX_GRAMMAR_NESTING)throw new GrammarComplexityException();
            if(node instanceof ChoiceNode choice){for(Node option:choice.options())add(lhs,sequence(option,depth+1));return;}
            add(lhs,sequence(node,depth+1));
        }
        /** Descripción: Ejecuta la operación «sequence» correspondiente a la responsabilidad de GrammarToken. Entradas: node: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Symbol> sequence(Node node,int depth){if(depth>MAX_GRAMMAR_NESTING)throw new GrammarComplexityException();
            if(node==null||node instanceof EmptyNode)return List.of();
            if(node instanceof SeqNode seq){List<Symbol> result=new ArrayList<>();for(Node child:seq.children())result.addAll(sequence(child,depth+1));return result;}
            if(node instanceof LitNode literal)return List.of(new Terminal("LITERAL",literal.text()));
            if(node instanceof RefNode ref)return List.of(rules.containsKey(ref.name())?new Nonterminal(ref.name()):new Terminal("LEXICAL",ref.name()));
            String name="$N"+(++synthetic);
            if(node instanceof ChoiceNode choice){for(Node option:choice.options())add(name,sequence(option,depth+1));return List.of(new Nonterminal(name));}
            if(node instanceof RepeatNode repeat){List<Symbol> child=sequence(repeat.child(),depth+1);if(repeat.min()==0&&repeat.max()==1){add(name,List.of());add(name,child);}else if(repeat.min()==0){add(name,List.of());List<Symbol> recursive=new ArrayList<>(child);recursive.add(new Nonterminal(name));add(name,recursive);}else{add(name,child);List<Symbol> recursive=new ArrayList<>(child);recursive.add(new Nonterminal(name));add(name,recursive);}return List.of(new Nonterminal(name));}
            throw new GrammarComplexityException();
        }
        /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de GrammarToken. Entradas: lhs: dato de entrada necesario para la operación; rhs: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(String lhs,List<Symbol> rhs){if(productions.values().stream().mapToInt(List::size).sum()>=maxProductions)throw new GrammarComplexityException();productions.computeIfAbsent(lhs,key->new ArrayList<>()).add(new Production(lhs,rhs));}
    }
    private static final class GrammarComplexityException extends RuntimeException{private static final long serialVersionUID=1L;}
}
