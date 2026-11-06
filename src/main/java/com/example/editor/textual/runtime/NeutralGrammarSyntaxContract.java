package com.example.editor.textual.runtime;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;

/**
 * Bounded, technology-neutral syntax contract reconstructed from the exact grammar sources retained in a successful snapshot.
 * Model input is never matched through one global regular expression: the whole document is recognised by an iterative Earley
 * recognizer whose cost grows linearly with the model for list-shaped languages (repetitions are left-recursive, nullable symbols
 * are completed at prediction time and predictions are filtered by one token of lookahead). The work is bounded by a state budget
 * proportional to the input; a document that exceeds it is reported as not provable, never as valid and never as a grammar error.
 */
final class NeutralGrammarSyntaxContract {
    private static final int MAX_GRAMMAR_RULES=2_000;
    private static final int MAX_GRAMMAR_NESTING=96;
    private static final int MAX_PRODUCTIONS=12_000;
    private static final int MAX_PRODUCTION_LENGTH=255;
    private static final long MIN_EARLEY_STATES=300_000;
    private static final long EARLEY_STATES_PER_TOKEN=48;
    private static final long MAX_EARLEY_STATES=6_000_000;
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
    private final Grammar grammar;
    private final boolean available;
    private final boolean fullRecognitionAvailable;

    /** Descripción: Inicializa una instancia de NeutralGrammarSyntaxContract con sus dependencias y datos de entrada. Entradas: technology: dato de entrada necesario para la operación; entryRule: dato de entrada necesario para la operación; parserRules: componente de conversión utilizado durante el procesamiento; terminalRules: dato de entrada necesario para la operación; typeAliases: dato de entrada necesario para la operación; grammar: dato de entrada necesario para la operación; available: dato de entrada necesario para la operación; fullRecognitionAvailable: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private NeutralGrammarSyntaxContract(GrammarTechnology technology,String entryRule,Map<String,Node> parserRules,
            Set<String> terminalRules,Map<String,String> typeAliases,Grammar grammar,boolean available,boolean fullRecognitionAvailable){
        this.technology=technology;
        this.entryRule=entryRule==null?"":entryRule;
        this.parserRules=Map.copyOf(parserRules);
        this.terminalRules=Set.copyOf(terminalRules);
        this.typeAliases=Map.copyOf(typeAliases);
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

        Grammar grammar=null;
        boolean full=!complex;
        if(full){
            try{grammar=GrammarCompiler.compile(nodes,terminals,MAX_PRODUCTIONS);}
            catch(GrammarComplexityException ignored){full=false;}
        }
        return new NeutralGrammarSyntaxContract(technology,entry,nodes,terminals,aliases,grammar,true,full&&grammar!=null);
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
        long budget=stateBudget(tokens.size());
        Recognition recognition=recognize(grammar,entryRule,tokens,budget);
        if(recognition.status()==RecognitionStatus.TOO_COMPLEX){
            // Never a grammar error (the text may well be valid) and never a pass: the draft is kept but cannot be promoted.
            return new ValidationResult(List.of(new ValidationIssue(0,Math.min(1,input.length()),
                    "El documento supera el presupuesto de verificación sintáctica completa ("+budget+" estados para "+tokens.size()+" símbolos). HYDRA conserva el borrador, pero no lo marcará como válido sin haberlo comprobado entero contra la gramática; divida el modelo en varios documentos o simplifique las construcciones ambiguas de la gramática.",
                    Map.of("entryRule",entryRule,"strategy","FULL_STATE_BUDGET_EXCEEDED","validationUnavailable","true","technology",technology.name(),
                            "tokens",Integer.toString(tokens.size()),"stateBudget",Long.toString(budget)))),"FULL_STATE_BUDGET_EXCEEDED",false);
        }
        if(recognition.accepted())return new ValidationResult(List.of(),tokens.isEmpty()?"EMPTY":"FULL_ITERATIVE",false);
        return mismatch(source,tokens,recognition,"FULL_ITERATIVE",max);
    }

    /** Descripción: Presupuesto de estados del reconocedor para un texto de «tokens» símbolos: proporcional al tamaño, con un mínimo para textos pequeños y un máximo que acota la memoria de una petición. Entradas: tokens: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */
    static long stateBudget(int tokens){
        return Math.min(MAX_EARLEY_STATES,Math.max(MIN_EARLEY_STATES,EARLEY_STATES_PER_TOKEN*(long)tokens));
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
        if(fix!=null&&"INSERT".equals(fix.kind())&&!tokens.isEmpty()){
            fix=new SuggestedFix(fix.kind(),insertionSeparator(tokens.get(tokens.size()-1).text(),fix.replacement())+fix.replacement(),fix.label());
        }
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

    /** Descripción: Decide si una inserción al final del texto necesita un espacio previo para no pegarse al último token (p. ej. «nodevalor»). Entradas: previousText: texto del último token existente; replacement: texto que se insertaría. Salida: devuelve un espacio cuando ambos extremos serían palabras o literales adyacentes, o la cadena vacía en otro caso. */ private static String insertionSeparator(String previousText,String replacement){
        if(previousText==null||previousText.isEmpty()||replacement==null||replacement.isEmpty())return "";
        char last=previousText.charAt(previousText.length()-1);char first=replacement.charAt(0);
        return isWordOrQuote(last)&&isWordOrQuote(first)?" ":"";
    }

    /** Descripción: Indica si un carácter forma parte de una palabra o abre/cierra un literal entre comillas. Entradas: value: carácter a evaluar. Salida: devuelve true si es letra, dígito, guion bajo o comilla. */ private static boolean isWordOrQuote(char value){
        return Character.isLetterOrDigit(value)||value=='_'||value=='"'||value=='\'';
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
        return new NeutralGrammarSyntaxContract(technology,entry,Map.of(),Set.of(),Map.of(),null,false,false);
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

    /** Descripción: Ejecuta la operación «nullable» correspondiente a la responsabilidad de GrammarToken. Entradas: node: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; visiting: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean nullable(Node node,Map<String,Node> rules,Set<String> visiting){
        if(node==null||node instanceof EmptyNode)return true;
        if(node instanceof RepeatNode repeat)return repeat.min()==0||nullable(repeat.child(),rules,visiting);
        if(node instanceof SeqNode seq)return seq.children().stream().allMatch(child->nullable(child,rules,new HashSet<>(visiting)));
        if(node instanceof ChoiceNode choice)return choice.options().stream().anyMatch(child->nullable(child,rules,new HashSet<>(visiting)));
        if(node instanceof RefNode ref&&rules.containsKey(ref.name())&&visiting.add(ref.name())){boolean value=nullable(rules.get(ref.name()),rules,visiting);visiting.remove(ref.name());return value;}
        return false;
    }

    /** Descripción: Reconoce «tokens» con la regla «startRule» mediante Earley iterativo. Las repeticiones son recursivas por la izquierda (un número constante de estados por elemento de una lista), los no terminales anulables se completan al predecirlos (Aycock-Horspool) y una producción solo se predice si su primer símbolo puede aceptar el siguiente token; las descartadas aportan sus terminales esperados al diagnóstico exactamente como si se hubieran probado. Entradas: grammar: gramática compilada; startRule: regla inicial; tokens: símbolos del modelo; maxStates: presupuesto de estados. Salida: aceptación, rechazo con la posición más lejana y lo esperado allí, o TOO_COMPLEX si se agota el presupuesto. */ private static Recognition recognize(Grammar grammar,String startRule,List<ModelToken> tokens,long maxStates){
        if(grammar==null||!grammar.byLhs().containsKey(startRule))return new Recognition(false,RecognitionStatus.INVALID_GRAMMAR,0,Set.of(),Set.of());
        Production start=grammar.start(startRule);
        int n=tokens.size();
        Chart[] chart=new Chart[n+1];Chart initial=new Chart();initial.add(Chart.item(start.id(),0,0));chart[0]=initial;
        long states=1;Frontier frontier=new Frontier();
        for(int position=0;position<=n;position++){
            Chart current=chart[position];
            if(current==null)continue; // no derivation reaches this position: the furthest point is already recorded
            if((position&1023)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Textual parsing cancelled");
            ModelToken lookahead=position<n?tokens.get(position):null;
            for(int cursor=0;cursor<current.size();cursor++){
                if(states>maxStates)return new Recognition(false,RecognitionStatus.TOO_COMPLEX,frontier.furthest,frontier.expected(),frontier.contexts());
                long item=current.get(cursor);
                Production production=grammar.production(Chart.production(item));int dot=Chart.dot(item);
                if(dot>=production.rhs().size()){
                    Chart origin=chart[Chart.origin(item)];
                    for(int j=0;j<origin.size();j++){
                        long parent=origin.get(j);Production waiting=grammar.production(Chart.production(parent));int parentDot=Chart.dot(parent);
                        if(parentDot<waiting.rhs().size()&&waiting.rhs().get(parentDot) instanceof Nonterminal nt&&nt.name().equals(production.lhs())&&current.add(Chart.advance(parent)))states++;
                    }
                    continue;
                }
                Symbol next=production.rhs().get(dot);
                if(next instanceof Nonterminal nt){
                    for(Production candidate:grammar.byLhs().getOrDefault(nt.name(),List.of())){
                        if(grammar.canStart(candidate,lookahead)){if(current.add(Chart.item(candidate.id(),0,position)))states++;}
                        else for(FirstTerminal first:grammar.first(candidate))frontier.record(position,first.terminal().label(),grammar.owner(nt.name()),position);
                    }
                    if(grammar.nullable(nt.name())&&current.add(Chart.advance(item)))states++;
                    continue;
                }
                Terminal terminal=(Terminal)next;
                if(lookahead!=null&&terminal.matches(lookahead)){
                    if(chart[position+1]==null)chart[position+1]=new Chart();
                    if(chart[position+1].add(Chart.advance(item)))states++;
                    frontier.furthest=Math.max(frontier.furthest,position+1);
                }
                else frontier.record(position,terminal.label(),grammar.owner(production.lhs()),Chart.origin(item));
            }
        }
        boolean accepted=chart[n]!=null&&chart[n].contains(Chart.item(start.id(),1,0));
        if(accepted)return new Recognition(true,RecognitionStatus.ACCEPTED,n,Set.of(),Set.of());
        return new Recognition(false,RecognitionStatus.REJECTED,frontier.furthest,frontier.expected(),frontier.contexts());
    }

    /** Lo que la gramática esperaba en la posición más lejana alcanzada: solo de esa posición (nunca restos de la anterior) y con la regla más interna primero, que es la que da sentido al diagnóstico. */
    private static final class Frontier{
        int furthest;private int recordedAt=-1;private final LinkedHashSet<String> expected=new LinkedHashSet<>();private final LinkedHashSet<String> rules=new LinkedHashSet<>();private String innermost;private int innermostOrigin=-1;
        /** Descripción: Registra que en «position» se esperaba «label» dentro de «rule», cuyo ítem empezó en «origin». Entradas: position: dato de entrada necesario para la operación; label: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; origin: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void record(int position,String label,String rule,int origin){
            if(position<furthest)return;
            if(position!=recordedAt){expected.clear();rules.clear();innermost=null;innermostOrigin=-1;recordedAt=position;furthest=position;}
            expected.add(label);
            if(rule==null||rule.startsWith("$"))return;
            rules.add(rule);
            if(origin>innermostOrigin){innermost=rule;innermostOrigin=origin;}
        }
        /** Descripción: Ejecuta la operación «expected» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Set<String> expected(){return recordedAt==furthest?expected:Set.of();}
        /** Descripción: Ejecuta la operación «contexts» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Set<String> contexts(){LinkedHashSet<String> out=new LinkedHashSet<>();if(recordedAt!=furthest)return out;if(innermost!=null)out.add(innermost);out.addAll(rules);return out;}
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
    private record Production(int id,String lhs,List<Symbol> rhs){Production{rhs=List.copyOf(rhs);}}
    private record FirstTerminal(Terminal terminal,String context){}
    /** Gramática compilada: producciones indexadas, conjunto de no terminales anulables y, por producción, los terminales con los que puede empezar (con la regla en la que aparecen, para el diagnóstico). */
    private static final class Grammar{
        private final Map<String,List<Production>> byLhs;private final List<Production> productions;private final Map<String,Production> starts;private final Map<String,String> owners;
        private final Set<String> nullable;private final List<List<FirstTerminal>> first;private final boolean[] nullableProduction;
        /** Descripción: Indexa las producciones y calcula por punto fijo los anulables y los primeros terminales. Entradas: byLhs: producciones por regla. Salida: deja la gramática lista para reconocer. */ Grammar(Map<String,List<Production>> byLhs,List<Production> productions,Map<String,String> owners){
            this.owners=Map.copyOf(owners);
            Map<String,List<Production>> copy=new LinkedHashMap<>();byLhs.forEach((key,value)->copy.put(key,List.copyOf(value)));this.byLhs=Map.copyOf(copy);
            List<Production> all=new ArrayList<>(productions);LinkedHashMap<String,Production> startProductions=new LinkedHashMap<>();
            for(String rule:copy.keySet()){Production start=new Production(all.size(),"$START$",List.of(new Nonterminal(rule)));all.add(start);startProductions.put(rule,start);}
            this.productions=List.copyOf(all);this.starts=Map.copyOf(startProductions);
            Set<String> nulls=new HashSet<>();boolean changed=true;
            while(changed){changed=false;for(Production p:all)if(!nulls.contains(p.lhs())&&p.rhs().stream().allMatch(s->s instanceof Nonterminal nt&&nulls.contains(nt.name()))){nulls.add(p.lhs());changed=true;}}
            this.nullable=Set.copyOf(nulls);
            this.nullableProduction=new boolean[all.size()];
            for(Production p:all)nullableProduction[p.id()]=p.rhs().stream().allMatch(s->s instanceof Nonterminal nt&&nulls.contains(nt.name()));
            Map<String,LinkedHashSet<FirstTerminal>> byRule=new HashMap<>();changed=true;
            while(changed){changed=false;for(Production p:all){LinkedHashSet<FirstTerminal> target=byRule.computeIfAbsent(p.lhs(),key->new LinkedHashSet<>());if(target.addAll(firstOf(p,byRule,nulls)))changed=true;}}
            List<List<FirstTerminal>> perProduction=new ArrayList<>(all.size());for(Production p:all)perProduction.add(List.copyOf(firstOf(p,byRule,nulls)));
            this.first=List.copyOf(perProduction);
        }
        /** Descripción: Terminales con los que puede empezar «p», recorriendo su lado derecho mientras los símbolos sean anulables. Entradas: p: dato de entrada necesario para la operación; byRule: dato de entrada necesario para la operación; nulls: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static LinkedHashSet<FirstTerminal> firstOf(Production p,Map<String,LinkedHashSet<FirstTerminal>> byRule,Set<String> nulls){
            LinkedHashSet<FirstTerminal> out=new LinkedHashSet<>();
            for(Symbol symbol:p.rhs()){
                if(symbol instanceof Terminal terminal){out.add(new FirstTerminal(terminal,p.lhs()));return out;}
                Nonterminal nt=(Nonterminal)symbol;out.addAll(byRule.getOrDefault(nt.name(),new LinkedHashSet<>()));
                if(!nulls.contains(nt.name()))return out;
            }
            return out;
        }
        /** Descripción: Ejecuta la operación «byLhs» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Map<String,List<Production>> byLhs(){return byLhs;}
        /** Descripción: Ejecuta la operación «production» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Production production(int id){return productions.get(id);}
        /** Descripción: Crea o prepara el resultado de «start» dentro de NeutralGrammarSyntaxContract. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Production start(String rule){return starts.get(rule);}
        /** Descripción: Ejecuta la operación «owner» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String owner(String rule){return owners.getOrDefault(rule,rule);}
        /** Descripción: Ejecuta la operación «nullable» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean nullable(String rule){return nullable.contains(rule);}
        /** Descripción: Ejecuta la operación «first» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<FirstTerminal> first(Production p){return first.get(p.id());}
        /** Descripción: Indica si «p» puede derivar el siguiente token (o la cadena vacía, que nunca se descarta). Entradas: p: dato de entrada necesario para la operación; lookahead: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean canStart(Production p,ModelToken lookahead){
            if(nullableProduction[p.id()])return true;
            if(lookahead==null)return false;
            for(FirstTerminal candidate:first.get(p.id()))if(candidate.terminal().matches(lookahead))return true;
            return false;
        }
    }
    /** Conjunto ordenado de ítems Earley de una posición, codificados en un long (producción, punto, origen) para que la memoria crezca con el número de estados y no con objetos. */
    private static final class Chart{
        private long[] items=new long[4];private int size;private long[] table=new long[8];private int used; // ~4 items per position in list-shaped models
        /** Descripción: Ejecuta la operación «item» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: production: dato de entrada necesario para la operación; dot: dato de entrada necesario para la operación; origin: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static long item(int production,int dot,int origin){return ((long)production<<40)|((long)dot<<32)|(origin&0xffffffffL);}
        /** Descripción: Ejecuta la operación «production» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: item: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int production(long item){return (int)(item>>>40);}
        /** Descripción: Ejecuta la operación «dot» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: item: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int dot(long item){return (int)((item>>>32)&0xff);}
        /** Descripción: Ejecuta la operación «origin» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: item: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int origin(long item){return (int)item;}
        /** Descripción: Ejecuta la operación «advance» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: item: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static long advance(long item){return item+(1L<<32);}
        /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ int size(){return size;}
        /** Descripción: Obtiene la información correspondiente a «get» dentro de NeutralGrammarSyntaxContract. Entradas: index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ long get(int index){return items[index];}
        /** Descripción: Añade el ítem si no estaba. Salida: true si es nuevo. Entradas: item: dato de entrada necesario para la operación. */ boolean add(long item){
            if((used+1)*2>table.length)rehash();
            long key=item+1;int mask=table.length-1;int slot=(int)(mix(key)&mask);
            while(table[slot]!=0){if(table[slot]==key)return false;slot=(slot+1)&mask;}
            table[slot]=key;used++;
            if(size==items.length)items=Arrays.copyOf(items,size*2);
            items[size++]=item;return true;
        }
        /** Descripción: Evalúa la condición «contains» sin modificar el estado externo. Entradas: item: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean contains(long item){long key=item+1;int mask=table.length-1;int slot=(int)(mix(key)&mask);while(table[slot]!=0){if(table[slot]==key)return true;slot=(slot+1)&mask;}return false;}
        /** Descripción: Ejecuta la operación «rehash» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rehash(){long[] old=table;table=new long[old.length*2];int mask=table.length-1;for(long key:old)if(key!=0){int slot=(int)(mix(key)&mask);while(table[slot]!=0)slot=(slot+1)&mask;table[slot]=key;}}
        /** Descripción: Ejecuta la operación «mix» correspondiente a la responsabilidad de NeutralGrammarSyntaxContract. Entradas: key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static long mix(long key){long h=key*0x9E3779B97F4A7C15L;return h^(h>>>29);}
    }
    private enum RecognitionStatus{ACCEPTED,REJECTED,TOO_COMPLEX,INVALID_GRAMMAR}
    private record Recognition(boolean accepted,RecognitionStatus status,int furthest,Set<String> expected,Set<String> contexts){Recognition{expected=expected==null?Set.of():Collections.unmodifiableSet(new LinkedHashSet<>(expected));contexts=contexts==null?Set.of():Collections.unmodifiableSet(new LinkedHashSet<>(contexts));}}
    private record SuggestedFix(String kind,String replacement,String label){}

    private static final class GrammarCompiler{
        private final Map<String,Node> rules;private final Set<String> terminals;private final int maxProductions;private final Map<String,List<Production>> productions=new LinkedHashMap<>();private final List<Production> indexed=new ArrayList<>();private final Map<String,String> owners=new HashMap<>();private String currentRule;private int synthetic;
        /** Descripción: Inicializa una instancia de GrammarCompiler con sus dependencias y datos de entrada. Entradas: rules: dato de entrada necesario para la operación; terminals: dato de entrada necesario para la operación; maxProductions: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private GrammarCompiler(Map<String,Node> rules,Set<String> terminals,int maxProductions){this.rules=rules;this.terminals=terminals;this.maxProductions=maxProductions;}
        /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de GrammarToken. Entradas: rules: dato de entrada necesario para la operación; terminals: dato de entrada necesario para la operación; maxProductions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Grammar compile(Map<String,Node> rules,Set<String> terminals,int maxProductions){GrammarCompiler compiler=new GrammarCompiler(rules,terminals,maxProductions);for(Map.Entry<String,Node> entry:rules.entrySet()){compiler.currentRule=entry.getKey();compiler.lowerInto(entry.getKey(),entry.getValue(),0);}return new Grammar(compiler.productions,compiler.indexed,compiler.owners);}
        /** Descripción: Ejecuta la operación «lowerInto» correspondiente a la responsabilidad de GrammarToken. Entradas: lhs: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void lowerInto(String lhs,Node node,int depth){if(depth>MAX_GRAMMAR_NESTING)throw new GrammarComplexityException();
            if(node instanceof ChoiceNode choice){for(Node option:choice.options())add(lhs,sequence(option,depth+1));return;}
            add(lhs,sequence(node,depth+1));
        }
        /** Descripción: Ejecuta la operación «sequence» correspondiente a la responsabilidad de GrammarToken. Entradas: node: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Symbol> sequence(Node node,int depth){if(depth>MAX_GRAMMAR_NESTING)throw new GrammarComplexityException();
            if(node==null||node instanceof EmptyNode)return List.of();
            if(node instanceof SeqNode seq){List<Symbol> result=new ArrayList<>();for(Node child:seq.children())result.addAll(sequence(child,depth+1));return result;}
            if(node instanceof LitNode literal)return List.of(new Terminal("LITERAL",literal.text()));
            if(node instanceof RefNode ref)return List.of(rules.containsKey(ref.name())?new Nonterminal(ref.name()):new Terminal("LEXICAL",ref.name()));
            String name="$N"+(++synthetic);owners.put(name,currentRule);
            if(node instanceof ChoiceNode choice){for(Node option:choice.options())add(name,sequence(option,depth+1));return List.of(new Nonterminal(name));}
            if(node instanceof RepeatNode repeat){List<Symbol> child=sequence(repeat.child(),depth+1);List<Symbol> recursive=new ArrayList<>();recursive.add(new Nonterminal(name));recursive.addAll(child);if(repeat.min()==0&&repeat.max()==1){add(name,List.of());add(name,child);}else if(repeat.min()==0){add(name,List.of());add(name,recursive);}else{add(name,child);add(name,recursive);}return List.of(new Nonterminal(name));}
            throw new GrammarComplexityException();
        }
        /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de GrammarToken. Entradas: lhs: dato de entrada necesario para la operación; rhs: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(String lhs,List<Symbol> rhs){if(indexed.size()>=maxProductions||rhs.size()>MAX_PRODUCTION_LENGTH)throw new GrammarComplexityException();Production production=new Production(indexed.size(),lhs,rhs);indexed.add(production);productions.computeIfAbsent(lhs,key->new ArrayList<>()).add(production);}
    }
    private static final class GrammarComplexityException extends RuntimeException{private static final long serialVersionUID=1L;}
}
