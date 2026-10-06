package com.example.editor.validation.importing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.semantic.expression.AttributeAccessExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionOperator;
import com.example.editor.semantic.expression.ConditionalExpressionNode;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionNode;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.FunctionExpressionNode;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.semantic.expression.RelationNavigationExpressionNode;
import com.example.editor.semantic.expression.SelfExpressionNode;
import com.example.editor.semantic.expression.UnaryExpressionNode;
import com.example.editor.semantic.expression.UnaryExpressionOperator;
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/**
 * Traduce únicamente un subconjunto inequívoco de AQL/OCL/EVL a expresiones neutrales.
 * No evalúa ni ejecuta nunca código del proyecto importado. Cuando la equivalencia no
 * puede demostrarse, devuelve {@code null} y el llamador debe conservar la regla como
 * evidencia externa deshabilitada.
 */
@Component
public class SafeExternalConstraintTranslator {

    /** Intenta producir una expresión booleana canónica equivalente a una condición externa. */
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de ComparatorMatch. Entradas: expressionId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; contextClassId: identificador que delimita el recurso objetivo; source: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionDefinition translate(String expressionId, String name, String contextClassId,
            String source, MetaModel metamodel, String technology) {
        if(expressionId == null || expressionId.isBlank() || contextClassId == null || contextClassId.isBlank()
                || source == null || source.isBlank() || metamodel == null) return null;
        Parser parser = new Parser(expressionId, contextClassId, normalize(source), metamodel);
        ExpressionNode root = parser.parseBoolean();
        if(root == null || !parser.consumed()) return null;
        return new ExpressionDefinition(expressionId, name == null || name.isBlank() ? "Imported constraint" : name,
                "Deterministic neutral translation of a safe external constraint; the original source remains in provenance.",
                contextClassId, ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN), root,
                Map.of("hydra.import.safeTranslation", "true", "hydra.import.technology", technology == null ? "EXTERNAL" : technology,
                        "hydra.import.originalCondition", source));
    }

    /** Traduce un valor determinista (literal, feature o condicional simple) para usarlo en una operación declarativa. */
    /** Descripción: Ejecuta la operación «translateValue» correspondiente a la responsabilidad de ComparatorMatch. Entradas: expressionId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; contextClassId: identificador que delimita el recurso objetivo; source: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionDefinition translateValue(String expressionId, String name, String contextClassId,
            String source, MetaModel metamodel, String technology) {
        if(expressionId == null || expressionId.isBlank() || contextClassId == null || contextClassId.isBlank()
                || source == null || source.isBlank() || metamodel == null) return null;
        Parser parser = new Parser(expressionId, contextClassId, normalize(source), metamodel);
        ExpressionNode root = parser.parseValue();
        if(root == null || !parser.consumed()) return null;
        return new ExpressionDefinition(expressionId, name == null || name.isBlank() ? "Imported value" : name,
                "Deterministic neutral translation of a safe external value expression; the original source remains in provenance.",
                contextClassId, ExpressionValueType.scalar(ExpressionValueKind.ANY), root,
                Map.of("hydra.import.safeTranslation", "true", "hydra.import.technology", technology == null ? "EXTERNAL" : technology,
                        "hydra.import.originalExpression", source));
    }

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de ComparatorMatch. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String source) {
        String value = source.trim();
        if(value.regionMatches(true,0,"aql:",0,4)) value=value.substring(4).trim();
        if(value.regionMatches(true,0,"ocl:",0,4)) value=value.substring(4).trim();
        return value.replace("&lt;", "<").replace("&gt;", ">");
    }

    private static final class Parser {
        private final String prefix;
        private final String contextClassId;
        private final MetaModel metamodel;
        private final CanonicalMetamodelIndex index;
        private String source;
        private int sequence;
        private boolean failed;

        /** Descripción: Inicializa una instancia de Parser con sus dependencias y datos de entrada. Entradas: expressionId: identificador que delimita el recurso objetivo; contextClassId: identificador que delimita el recurso objetivo; source: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ Parser(String expressionId, String contextClassId, String source, MetaModel metamodel) {
            this.prefix="node:"+sha256(expressionId).substring(0,16);
            this.contextClassId=contextClassId;
            this.source=source.trim();
            this.metamodel=metamodel;
            this.index=new CanonicalMetamodelIndex(metamodel);
        }

        /** Descripción: Ejecuta la operación «parseBoolean» correspondiente a la responsabilidad de ComparatorMatch. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ ExpressionNode parseBoolean() {
            String value=stripOuter(source.trim());
            ExpressionNode result=parseBooleanValue(value);
            if(result==null) failed=true;
            source="";
            return result;
        }

        /** Descripción: Ejecuta la operación «parseValue» correspondiente a la responsabilidad de ComparatorMatch. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ ExpressionNode parseValue() {
            String value=stripOuter(source.trim());
            ExpressionNode result;
            Matcher conditional=PatternHolder.CONDITIONAL.matcher(value);
            if(conditional.matches()) {
                ExpressionNode condition=parseBooleanValue(conditional.group(1));
                ExpressionNode whenTrue=term(conditional.group(2));
                ExpressionNode whenFalse=term(conditional.group(3));
                result=condition==null||whenTrue==null||whenFalse==null?null:new ConditionalExpressionNode(id(),condition,whenTrue,whenFalse);
            } else result=term(value);
            if(result==null) failed=true;
            source="";
            return result;
        }

        /** Descripción: Ejecuta la operación «consumed» correspondiente a la responsabilidad de ComparatorMatch. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean consumed(){return !failed && source.isBlank();}

        /** Descripción: Ejecuta la operación «parseBooleanValue» correspondiente a la responsabilidad de ComparatorMatch. Entradas: raw: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionNode parseBooleanValue(String raw) {
            String value=stripOuter(raw.trim());
            List<String> orParts=splitTopLevelWord(value,"or");
            if(orParts.size()>1) return fold(orParts,BinaryExpressionOperator.OR);
            List<String> andParts=splitTopLevelWord(value,"and");
            if(andParts.size()>1) return fold(andParts,BinaryExpressionOperator.AND);

            String lower=value.toLowerCase(Locale.ROOT);
            if(lower.startsWith("not ")) {
                ExpressionNode operand=parseBooleanValue(value.substring(4));
                return operand==null?null:new UnaryExpressionNode(id(),UnaryExpressionOperator.NOT,operand);
            }
            if(lower.matches("self\\.[a-z_][\\w]*\\.oclisundefined\\(\\)")) {
                String feature=value.substring(5,value.indexOf('.',5));
                ExpressionNode term=feature(feature);
                return term==null?null:new UnaryExpressionNode(id(),UnaryExpressionOperator.IS_NULL,term);
            }
            if(lower.matches("self\\.[a-z_][\\w]*\\.isdefined\\(\\)")) {
                String feature=value.substring(5,value.indexOf('.',5));
                ExpressionNode term=feature(feature);
                return term==null?null:new UnaryExpressionNode(id(),UnaryExpressionOperator.IS_NOT_NULL,term);
            }
            ComparatorMatch comparison=findComparator(value);
            if(comparison!=null) {
                ExpressionNode left=term(comparison.left()); ExpressionNode right=term(comparison.right());
                if(left==null||right==null)return null;
                BinaryExpressionOperator operator=switch(comparison.operator()) {
                    case "=" -> BinaryExpressionOperator.EQUALS;
                    case "<>","!=" -> BinaryExpressionOperator.NOT_EQUALS;
                    case ">" -> BinaryExpressionOperator.GREATER_THAN;
                    case ">=" -> BinaryExpressionOperator.GREATER_OR_EQUAL;
                    case "<" -> BinaryExpressionOperator.LESS_THAN;
                    case "<=" -> BinaryExpressionOperator.LESS_OR_EQUAL;
                    default -> null;
                };
                return operator==null?null:new BinaryExpressionNode(id(),operator,left,right);
            }
            if("true".equalsIgnoreCase(value)||"false".equalsIgnoreCase(value)) return literal(Boolean.parseBoolean(value));
            return null;
        }

        /** Descripción: Ejecuta la operación «fold» correspondiente a la responsabilidad de ComparatorMatch. Entradas: values: colección de elementos que debe procesarse; operator: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionNode fold(List<String> values,BinaryExpressionOperator operator) {
            ExpressionNode result=null;
            for(String value:values){ExpressionNode next=parseBooleanValue(value);if(next==null)return null;result=result==null?next:new BinaryExpressionNode(id(),operator,result,next);}return result;
        }

        /** Descripción: Ejecuta la operación «term» correspondiente a la responsabilidad de ComparatorMatch. Entradas: raw: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionNode term(String raw) {
            String value=stripOuter(raw.trim()); String lower=value.toLowerCase(Locale.ROOT);
            if(lower.matches("self\\.[a-z_][\\w]*\\.trim\\(\\)\\.(?:size|length)\\(\\)")) {
                String feature=value.substring(5,value.indexOf('.',5)); ExpressionNode base=feature(feature); if(base==null)return null;
                return new FunctionExpressionNode(id(),"length",List.of(new FunctionExpressionNode(id(),"trim",List.of(base))));
            }
            if(lower.matches("self\\.[a-z_][\\w]*\\.(?:size|length)\\(\\)")) {
                String feature=value.substring(5,value.indexOf('.',5)); ExpressionNode base=feature(feature); if(base==null)return null;
                return new FunctionExpressionNode(id(),"length",List.of(base));
            }
            if(lower.matches("self\\.[a-z_][\\w]*")) return feature(value.substring(5));
            if("null".equalsIgnoreCase(value)||"undefined".equalsIgnoreCase(value)) return new LiteralExpressionNode(id(),ExpressionValueType.scalar(ExpressionValueKind.NULL),null);
            if("true".equalsIgnoreCase(value)||"false".equalsIgnoreCase(value)) return literal(Boolean.parseBoolean(value));
            if(value.matches("[-+]?\\d+")) { try{return literal(Long.parseLong(value));}catch(NumberFormatException ignored){return null;} }
            if(value.matches("[-+]?\\d+\\.\\d+")) { try{return literal(new BigDecimal(value));}catch(NumberFormatException ignored){return null;} }
            if((value.startsWith("'")&&value.endsWith("'"))||(value.startsWith("\"")&&value.endsWith("\""))) return literal(value.substring(1,value.length()-1));
            return null;
        }

        /** Descripción: Ejecuta la operación «feature» correspondiente a la responsabilidad de ComparatorMatch. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionNode feature(String name) {
            MetaAttribute attribute=resolveAttribute(name);
            if(attribute!=null)return new AttributeAccessExpressionNode(id(),new SelfExpressionNode(id()),attribute.id());
            MetaRelation relation=resolveRelation(name);
            if(relation!=null)return new RelationNavigationExpressionNode(id(),new SelfExpressionNode(id()),relation.id());
            return null;
        }

        /** Descripción: Obtiene la información correspondiente a «resolveAttribute» dentro de ComparatorMatch. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaAttribute resolveAttribute(String name) {
            for(MetaClass candidate:metamodel.classes()) if(index.isSameOrSubtype(contextClassId,candidate.id()))
                for(MetaAttribute attribute:candidate.attributes()) if(attribute.name().equals(name)) return attribute;
            return null;
        }

        /** Descripción: Obtiene la información correspondiente a «resolveRelation» dentro de ComparatorMatch. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaRelation resolveRelation(String name) {
            return metamodel.relations().stream().filter(relation->relation.name().equals(name)&&index.isSameOrSubtype(contextClassId,relation.sourceTypeId())).findFirst().orElse(null);
        }

        /** Descripción: Ejecuta la operación «literal» correspondiente a la responsabilidad de ComparatorMatch. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionNode literal(Object value) {
            ExpressionValueKind kind=value instanceof Boolean?ExpressionValueKind.BOOLEAN:value instanceof BigDecimal?ExpressionValueKind.DECIMAL:
                    value instanceof Number?ExpressionValueKind.INTEGER:ExpressionValueKind.STRING;
            return new LiteralExpressionNode(id(),ExpressionValueType.scalar(kind),value);
        }

        /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de ComparatorMatch. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String id(){return prefix+":"+(++sequence);}
    }

    /** Descripción: Obtiene la información correspondiente a «findComparator» dentro de ComparatorMatch. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static ComparatorMatch findComparator(String value) {
        int depth=0; char quote=0;
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i); if(quote!=0){if(c==quote&&value.charAt(Math.max(0,i-1))!='\\')quote=0;continue;} if(c=='\''||c=='\"'){quote=c;continue;} if(c=='('){depth++;continue;}if(c==')'){depth--;continue;}if(depth!=0)continue;
            for(String op:List.of(">=","<=","<>","!=","=",">","<")) if(value.startsWith(op,i)) return new ComparatorMatch(value.substring(0,i).trim(),op,value.substring(i+op.length()).trim());
        }
        return null;
    }

    /** Descripción: Ejecuta la operación «splitTopLevelWord» correspondiente a la responsabilidad de ComparatorMatch. Entradas: value: valor de entrada que debe procesarse; word: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> splitTopLevelWord(String value,String word) {
        List<String> result=new ArrayList<>();int depth=0;char quote=0;int start=0;
        for(int i=0;i<=value.length()-word.length();i++) {char c=value.charAt(i);if(quote!=0){if(c==quote&&value.charAt(Math.max(0,i-1))!='\\')quote=0;continue;}if(c=='\''||c=='\"'){quote=c;continue;}if(c=='('){depth++;continue;}if(c==')'){depth--;continue;}if(depth!=0)continue;
            if(value.regionMatches(true,i,word,0,word.length())&&boundary(value,i-1)&&boundary(value,i+word.length())){result.add(value.substring(start,i).trim());start=i+word.length();i=start-1;}}
        if(result.isEmpty())return List.of(value);result.add(value.substring(start).trim());return result;
    }

    /** Descripción: Ejecuta la operación «boundary» correspondiente a la responsabilidad de ComparatorMatch. Entradas: value: valor de entrada que debe procesarse; index: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean boundary(String value,int index){return index<0||index>=value.length()||!Character.isLetterOrDigit(value.charAt(index))&&value.charAt(index)!='_';}
    /** Descripción: Ejecuta la operación «stripOuter» correspondiente a la responsabilidad de ComparatorMatch. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String stripOuter(String value){String result=value;while(result.startsWith("(")&&result.endsWith(")")&&matchingOuter(result))result=result.substring(1,result.length()-1).trim();return result;}
    /** Descripción: Ejecuta la operación «matchingOuter» correspondiente a la responsabilidad de ComparatorMatch. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean matchingOuter(String value){int depth=0;char quote=0;for(int i=0;i<value.length();i++){char c=value.charAt(i);if(quote!=0){if(c==quote&&value.charAt(Math.max(0,i-1))!='\\')quote=0;continue;}if(c=='\''||c=='\"'){quote=c;continue;}if(c=='(')depth++;else if(c==')'){depth--;if(depth==0&&i<value.length()-1)return false;}}return depth==0;}
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de ComparatorMatch. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static final class PatternHolder {
        private static final java.util.regex.Pattern CONDITIONAL = java.util.regex.Pattern.compile("(?is)^if\\s+(.+?)\\s+then\\s+(.+?)\\s+else\\s+(.+?)\\s+endif$");
    }
    private record ComparatorMatch(String left,String operator,String right){}
}
