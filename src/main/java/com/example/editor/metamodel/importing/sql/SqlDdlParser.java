package com.example.editor.metamodel.importing.sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Column;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Diagnostic;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.EnumType;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.ForeignKey;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.PrimaryKey;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Severity;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Table;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.UniqueKey;

/** Statement parser for portable relational DDL. Unsupported statements become diagnostics instead of partial silent mutations. */
public final class SqlDdlParser {
    private static final java.util.Set<String> COLUMN_CONSTRAINTS = java.util.Set.of(
            "NOT","NULL","DEFAULT","PRIMARY","UNIQUE","REFERENCES","CHECK","CONSTRAINT","COLLATE","GENERATED","IDENTITY","AUTO_INCREMENT");
    private final String source;
    private final SqlDdlDialect dialect;
    private final SqlDdlLimits limits;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Map<String,TableBuilder> tables = new LinkedHashMap<>();
    private final List<EnumType> enums = new ArrayList<>();

    /** Descripción: Inicializa una instancia de SqlDdlParser con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SqlDdlParser(String source) { this(source, SqlDdlLimits.defaults()); }
    /** Descripción: Inicializa una instancia de SqlDdlParser con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación; limits: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SqlDdlParser(String source, SqlDdlLimits limits) {
        this.source=source==null?"":(source.startsWith("\uFEFF")?source.substring(1):source); this.dialect=SqlDdlDialect.infer(this.source); this.limits=limits==null?SqlDdlLimits.defaults():limits;
    }

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de NameRead. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public SqlDdlAst.Document parse() {
        if(source.length()>limits.maxSourceCharacters()) throw new SqlDdlSafetyException("SQL DDL source exceeds configured character limit of " + limits.maxSourceCharacters() + ".");
        List<SqlDdlToken> tokens = new SqlDdlLexer(source,limits.maxTokens()).tokenize();
        if(tokens.size()>limits.maxTokens()+1) throw new SqlDdlSafetyException("SQL DDL token count exceeds configured limit of " + limits.maxTokens() + ".");
        List<List<SqlDdlToken>> splitStatements=statements(tokens);
        if(splitStatements.size()>limits.maxStatements()) throw new SqlDdlSafetyException("SQL DDL statement count exceeds configured limit of " + limits.maxStatements() + ".");
        for(List<SqlDdlToken> statement : splitStatements) parseStatement(statement);
        List<Table> built = tables.values().stream().map(TableBuilder::build).toList();
        if(built.isEmpty() && enums.isEmpty()) diagnostics.add(new Diagnostic("SQL_DDL_NO_SCHEMA", Severity.ERROR, 1, 1, "No CREATE TABLE or supported schema definition was found."));
        return new SqlDdlAst.Document(dialect, built, enums, diagnostics);
    }

    /** Descripción: Ejecuta la operación «parseStatement» correspondiente a la responsabilidad de NameRead. Entradas: statement: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseStatement(List<SqlDdlToken> statement) {
        if(statement.isEmpty()) return;
        if(starts(statement,"CREATE","TABLE") || starts(statement,"CREATE","TEMP","TABLE") || starts(statement,"CREATE","TEMPORARY","TABLE")) { parseCreateTable(statement); return; }
        if(starts(statement,"ALTER","TABLE")) { parseAlterTable(statement); return; }
        if(starts(statement,"CREATE","TYPE")) { if(parseEnum(statement)) return; }
        if(starts(statement,"CREATE","UNIQUE","INDEX")) { parseUniqueIndex(statement); return; }
        if(starts(statement,"CREATE","INDEX")) { info(statement.get(0),"SQL_DDL_NON_UNIQUE_INDEX_IGNORED","Non-unique index does not change the neutral metamodel and was ignored."); return; }
        String keyword = statement.get(0).text().toUpperCase(Locale.ROOT);
        if(java.util.Set.of("USE","SET","PRAGMA","COMMENT","GRANT","REVOKE","CREATE").contains(keyword)) {
            info(statement.get(0),"SQL_DDL_AUXILIARY_STATEMENT_IGNORED","Auxiliary SQL statement was ignored because it does not alter the recovered neutral structure.");
        } else {
            warning(statement.get(0),"SQL_DDL_UNSUPPORTED_STATEMENT","Unsupported DDL statement was not applied: " + keyword + ".");
        }
    }

    /** Descripción: Ejecuta la operación «parseCreateTable» correspondiente a la responsabilidad de NameRead. Entradas: s: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseCreateTable(List<SqlDdlToken> s) {
        int tableKeyword=indexOf(s,"TABLE",0); int cursor=tableKeyword+1;
        if(matchesAt(s,cursor,"IF","NOT","EXISTS")) cursor+=3;
        NameRead name=qualifiedName(s,cursor);
        if(name.value().isBlank()) { error(s.get(Math.min(cursor,s.size()-1)),"SQL_DDL_TABLE_NAME_REQUIRED","CREATE TABLE requires a table name."); return; }
        cursor=name.next(); int open=indexOfSymbol(s,"(",cursor);
        if(open<0) { error(s.get(0),"SQL_DDL_TABLE_BODY_REQUIRED","CREATE TABLE " + name.value() + " requires a parenthesized body."); return; }
        int close=matchingParen(s,open);
        if(close<0) { error(s.get(open),"SQL_DDL_UNCLOSED_TABLE","CREATE TABLE " + name.value() + " has an unclosed parenthesized body."); return; }
        TableBuilder table=tables.computeIfAbsent(key(name.value()), ignored -> new TableBuilder(name.value()));
        if(table.declared){error(s.get(0),"SQL_DDL_DUPLICATE_TABLE","CREATE TABLE declares "+name.value()+" more than once.");return;} table.declared=true;
        for(List<SqlDdlToken> part : splitTopLevel(s.subList(open+1,close),",")) parseTablePart(table,part);
        if(close+1<s.size()) table.annotations.put("sql.tableOptions", join(s.subList(close+1,s.size())));
    }

    /** Descripción: Ejecuta la operación «parseTablePart» correspondiente a la responsabilidad de NameRead. Entradas: table: dato de entrada necesario para la operación; part: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseTablePart(TableBuilder table,List<SqlDdlToken> part) {
        if(part.isEmpty()) return; int cursor=0; String constraintName="";
        if(is(part,cursor,"CONSTRAINT")) { if(cursor+1>=part.size()) { error(part.get(0),"SQL_DDL_CONSTRAINT_NAME_REQUIRED","CONSTRAINT requires a name."); return; } constraintName=identifier(part.get(cursor+1)); cursor+=2; }
        if(is(part,cursor,"PRIMARY") && is(part,cursor+1,"KEY")) { table.primaryKeys.add(new PrimaryKey(constraintName, columnListAfter(part,cursor+2))); return; }
        if(is(part,cursor,"FOREIGN") && is(part,cursor+1,"KEY")) { ForeignKey fk=parseForeignKey(part,cursor,constraintName); if(fk!=null)table.foreignKeys.add(fk); return; }
        if(is(part,cursor,"UNIQUE")) { table.uniqueKeys.add(new UniqueKey(constraintName,columnListAfter(part,cursor+1))); return; }
        if(is(part,cursor,"CHECK")) { info(part.get(cursor),"SQL_DDL_CHECK_PRESERVED_AS_METADATA","CHECK constraint is preserved only as SQL metadata in the current neutral model."); table.annotations.put("sql.check."+table.annotations.size(),join(part)); return; }
        parseColumn(table,part,cursor);
    }

    /** Descripción: Ejecuta la operación «parseColumn» correspondiente a la responsabilidad de NameRead. Entradas: table: dato de entrada necesario para la operación; part: dato de entrada necesario para la operación; cursor: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseColumn(TableBuilder table,List<SqlDdlToken> part,int cursor) {
        if(cursor>=part.size()) return; String name=identifier(part.get(cursor++));
        if(name.isBlank() || cursor>=part.size()) { error(part.get(0),"SQL_DDL_COLUMN_INVALID","Column definition must contain a name and a SQL type."); return; }
        int typeStart=cursor, depth=0;
        while(cursor<part.size()) {
            String text=part.get(cursor).text();
            if("(".equals(text)) depth++; else if(")".equals(text)) depth=Math.max(0,depth-1);
            if(depth==0 && cursor>typeStart && isConstraintKeyword(part.get(cursor))) break;
            cursor++;
        }
        String sqlType=join(part.subList(typeStart,cursor));
        boolean nullable=true, primary=false, unique=false, generated=false; String defaultExpression=""; ForeignKey inlineReference=null;
        while(cursor<part.size()) {
            if(matchesAt(part,cursor,"NOT","NULL")) { nullable=false; cursor+=2; continue; }
            if(is(part,cursor,"NULL")) { nullable=true; cursor++; continue; }
            if(matchesAt(part,cursor,"PRIMARY","KEY")) { primary=true; nullable=false; cursor+=2; continue; }
            if(is(part,cursor,"UNIQUE")) { unique=true; cursor++; continue; }
            if(is(part,cursor,"DEFAULT")) { int start=++cursor; cursor=scanUntilConstraint(part,cursor); defaultExpression=join(part.subList(start,cursor)); continue; }
            if(is(part,cursor,"REFERENCES")) { inlineReference=parseInlineReference(part,cursor,name); cursor=part.size(); continue; }
            if(is(part,cursor,"GENERATED") || is(part,cursor,"IDENTITY") || is(part,cursor,"AUTO_INCREMENT")) { generated=true; cursor++; continue; }
            if(is(part,cursor,"CHECK") || is(part,cursor,"COLLATE") || is(part,cursor,"CONSTRAINT")) { cursor++; continue; }
            cursor++;
        }
        Map<String,String> annotations=new LinkedHashMap<>(); annotations.put("sql.type",sqlType); annotations.put("sql.column",name);
        table.columns.add(new Column(name,sqlType,nullable,defaultExpression,primary,unique,generated,annotations));
        if(primary)table.primaryKeys.add(new PrimaryKey("",List.of(name))); if(unique)table.uniqueKeys.add(new UniqueKey("",List.of(name))); if(inlineReference!=null)table.foreignKeys.add(inlineReference);
    }

    /** Descripción: Ejecuta la operación «parseInlineReference» correspondiente a la responsabilidad de NameRead. Entradas: part: dato de entrada necesario para la operación; cursor: dato de entrada necesario para la operación; sourceColumn: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ForeignKey parseInlineReference(List<SqlDdlToken> part,int cursor,String sourceColumn) {
        NameRead target=qualifiedName(part,cursor+1); if(target.value().isBlank()) { error(part.get(cursor),"SQL_DDL_REFERENCE_TARGET_REQUIRED","REFERENCES requires a target table."); return null; }
        List<String> targetColumns=columnListAfter(part,target.next());
        return new ForeignKey("",List.of(sourceColumn),target.value(),targetColumns,action(part,"DELETE",target.next()),action(part,"UPDATE",target.next()));
    }

    /** Descripción: Ejecuta la operación «parseForeignKey» correspondiente a la responsabilidad de NameRead. Entradas: part: dato de entrada necesario para la operación; cursor: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ForeignKey parseForeignKey(List<SqlDdlToken> part,int cursor,String name) {
        List<String> columns=columnListAfter(part,cursor+2); int refs=indexOf(part,"REFERENCES",cursor+2);
        if(columns.isEmpty() || refs<0) { error(part.get(cursor),"SQL_DDL_FOREIGN_KEY_INVALID","FOREIGN KEY requires source columns and REFERENCES target."); return null; }
        NameRead target=qualifiedName(part,refs+1); List<String> targetColumns=columnListAfter(part,target.next());
        if(target.value().isBlank() || targetColumns.isEmpty()) { error(part.get(cursor),"SQL_DDL_FOREIGN_KEY_TARGET_INVALID","FOREIGN KEY target table and columns are required."); return null; }
        if(columns.size()!=targetColumns.size()) warning(part.get(cursor),"SQL_DDL_FOREIGN_KEY_ARITY","Foreign key column count differs from referenced column count.");
        return new ForeignKey(name,columns,target.value(),targetColumns,action(part,"DELETE",target.next()),action(part,"UPDATE",target.next()));
    }

    /** Descripción: Ejecuta la operación «parseAlterTable» correspondiente a la responsabilidad de NameRead. Entradas: s: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseAlterTable(List<SqlDdlToken> s) {
        NameRead name=qualifiedName(s,2); if(name.value().isBlank()) { error(s.get(0),"SQL_DDL_ALTER_TABLE_NAME_REQUIRED","ALTER TABLE requires a table name."); return; }
        TableBuilder table=tables.computeIfAbsent(key(name.value()),ignored->new TableBuilder(name.value())); int cursor=name.next(); if(is(s,cursor,"ADD"))cursor++;
        String constraintName=""; if(is(s,cursor,"CONSTRAINT") && cursor+1<s.size()) { constraintName=identifier(s.get(cursor+1)); cursor+=2; }
        if(matchesAt(s,cursor,"FOREIGN","KEY")) { ForeignKey fk=parseForeignKey(s,cursor,constraintName); if(fk!=null)table.foreignKeys.add(fk); return; }
        if(matchesAt(s,cursor,"PRIMARY","KEY")) { table.primaryKeys.add(new PrimaryKey(constraintName,columnListAfter(s,cursor+2))); return; }
        if(is(s,cursor,"UNIQUE")) { table.uniqueKeys.add(new UniqueKey(constraintName,columnListAfter(s,cursor+1))); return; }
        warning(s.get(0),"SQL_DDL_UNSUPPORTED_ALTER","ALTER TABLE operation is not structurally supported and was not applied.");
    }

    /** Descripción: Ejecuta la operación «parseEnum» correspondiente a la responsabilidad de NameRead. Entradas: s: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean parseEnum(List<SqlDdlToken> s) {
        NameRead name=qualifiedName(s,2); int as=indexOf(s,"AS",name.next());
        if(as<0 || !is(s,as+1,"ENUM")) return false;
        int open=indexOfSymbol(s,"(",as+2), close=open<0?-1:matchingParen(s,open);
        if(open<0||close<0) { error(s.get(0),"SQL_DDL_ENUM_INVALID","CREATE TYPE ... AS ENUM requires a literal list."); return true; }
        List<String> literals=new ArrayList<>(); for(List<SqlDdlToken> part:splitTopLevel(s.subList(open+1,close),",")) if(!part.isEmpty()) literals.add(part.get(0).text());
        enums.add(new EnumType(name.value(),literals)); return true;
    }

    /** Descripción: Ejecuta la operación «parseUniqueIndex» correspondiente a la responsabilidad de NameRead. Entradas: s: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseUniqueIndex(List<SqlDdlToken> s) {
        int on=indexOf(s,"ON",3); if(on<0)return; NameRead tableName=qualifiedName(s,on+1); List<String> columns=columnListAfter(s,tableName.next());
        String indexName=s.size()>3?identifier(s.get(3)):""; tables.computeIfAbsent(key(tableName.value()),ignored->new TableBuilder(tableName.value())).uniqueKeys.add(new UniqueKey(indexName,columns));
    }

    /** Descripción: Ejecuta la operación «statements» correspondiente a la responsabilidad de NameRead. Entradas: tokens: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<List<SqlDdlToken>> statements(List<SqlDdlToken> tokens) {
        List<List<SqlDdlToken>> result=new ArrayList<>(); List<SqlDdlToken> current=new ArrayList<>(); int depth=0;
        for(SqlDdlToken token:tokens) { if(token.type()==SqlDdlTokenType.END)break; if("(".equals(token.text())){depth++;if(depth>limits.maxParenthesisDepth())throw new SqlDdlSafetyException("SQL DDL parenthesis nesting exceeds configured limit of " + limits.maxParenthesisDepth() + ".");} else if(")".equals(token.text()))depth=Math.max(0,depth-1); if(";".equals(token.text())&&depth==0){if(!current.isEmpty())result.add(List.copyOf(current));current.clear();if(result.size()>limits.maxStatements())throw new SqlDdlSafetyException("SQL DDL statement count exceeds configured limit of " + limits.maxStatements() + ".");}else current.add(token); }
        if(!current.isEmpty())result.add(List.copyOf(current)); return result;
    }
    /** Descripción: Ejecuta la operación «splitTopLevel» correspondiente a la responsabilidad de NameRead. Entradas: tokens: dato de entrada necesario para la operación; separator: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<List<SqlDdlToken>> splitTopLevel(List<SqlDdlToken> tokens,String separator) { List<List<SqlDdlToken>> result=new ArrayList<>(); List<SqlDdlToken> current=new ArrayList<>(); int depth=0; for(SqlDdlToken token:tokens){if("(".equals(token.text()))depth++;else if(")".equals(token.text()))depth--;if(separator.equals(token.text())&&depth==0){result.add(List.copyOf(current));current.clear();}else current.add(token);}if(!current.isEmpty())result.add(List.copyOf(current));return result; }
    /** Descripción: Ejecuta la operación «columnListAfter» correspondiente a la responsabilidad de NameRead. Entradas: tokens: dato de entrada necesario para la operación; cursor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> columnListAfter(List<SqlDdlToken> tokens,int cursor) { int open=indexOfSymbol(tokens,"(",cursor); if(open<0)return List.of(); int close=matchingParen(tokens,open); if(close<0)return List.of(); List<String> out=new ArrayList<>(); for(List<SqlDdlToken> part:splitTopLevel(tokens.subList(open+1,close),","))if(!part.isEmpty())out.add(identifier(part.get(0)));return List.copyOf(out); }
    /** Descripción: Ejecuta la operación «action» correspondiente a la responsabilidad de NameRead. Entradas: tokens: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; from: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String action(List<SqlDdlToken> tokens,String kind,int from) { int on=indexOf(tokens,"ON",from); while(on>=0&&on+2<tokens.size()){if(tokens.get(on+1).is(kind))return tokens.get(on+2).text().toUpperCase(Locale.ROOT)+(on+3<tokens.size()&&tokens.get(on+2).is("SET")?" "+tokens.get(on+3).text().toUpperCase(Locale.ROOT):"");on=indexOf(tokens,"ON",on+1);}return ""; }
    /** Descripción: Ejecuta la operación «scanUntilConstraint» correspondiente a la responsabilidad de NameRead. Entradas: tokens: dato de entrada necesario para la operación; cursor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int scanUntilConstraint(List<SqlDdlToken> tokens,int cursor){int depth=0;while(cursor<tokens.size()){String text=tokens.get(cursor).text();if("(".equals(text))depth++;else if(")".equals(text))depth=Math.max(0,depth-1);if(depth==0&&isConstraintKeyword(tokens.get(cursor)))break;cursor++;}return cursor;}
    /** Descripción: Evalúa la condición «isConstraintKeyword» sin modificar el estado externo. Entradas: token: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isConstraintKeyword(SqlDdlToken token){return token.type()==SqlDdlTokenType.WORD&&COLUMN_CONSTRAINTS.contains(token.text().toUpperCase(Locale.ROOT));}
    /** Descripción: Ejecuta la operación «qualifiedName» correspondiente a la responsabilidad de NameRead. Entradas: tokens: dato de entrada necesario para la operación; cursor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NameRead qualifiedName(List<SqlDdlToken> tokens,int cursor){StringBuilder out=new StringBuilder();boolean expectName=true;while(cursor<tokens.size()){SqlDdlToken token=tokens.get(cursor);if(expectName&&(token.type()==SqlDdlTokenType.WORD||token.type()==SqlDdlTokenType.QUOTED_IDENTIFIER)){if(!out.isEmpty())out.append('.');out.append(identifier(token));cursor++;expectName=false;continue;}if(!expectName&&".".equals(token.text())){cursor++;expectName=true;continue;}break;}return new NameRead(out.toString(),cursor);}
    /** Descripción: Ejecuta la operación «matchingParen» correspondiente a la responsabilidad de NameRead. Entradas: tokens: dato de entrada necesario para la operación; open: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int matchingParen(List<SqlDdlToken> tokens,int open){int depth=0;for(int i=open;i<tokens.size();i++){if("(".equals(tokens.get(i).text()))depth++;else if(")".equals(tokens.get(i).text())&&--depth==0)return i;}return -1;}
    /** Descripción: Ejecuta la operación «indexOf» correspondiente a la responsabilidad de NameRead. Entradas: tokens: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; from: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int indexOf(List<SqlDdlToken> tokens,String value,int from){for(int i=Math.max(0,from);i<tokens.size();i++)if(tokens.get(i).is(value))return i;return -1;}
    /** Descripción: Ejecuta la operación «indexOfSymbol» correspondiente a la responsabilidad de NameRead. Entradas: tokens: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; from: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int indexOfSymbol(List<SqlDdlToken> tokens,String value,int from){for(int i=Math.max(0,from);i<tokens.size();i++)if(value.equals(tokens.get(i).text()))return i;return -1;}
    /** Descripción: Crea o prepara el resultado de «starts» dentro de NameRead. Entradas: tokens: dato de entrada necesario para la operación; values: colección de elementos que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean starts(List<SqlDdlToken> tokens,String... values){return matchesAt(tokens,0,values);}
    /** Descripción: Evalúa la condición «matchesAt» sin modificar el estado externo. Entradas: tokens: dato de entrada necesario para la operación; cursor: dato de entrada necesario para la operación; values: colección de elementos que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean matchesAt(List<SqlDdlToken> tokens,int cursor,String... values){if(cursor<0||cursor+values.length>tokens.size())return false;for(int i=0;i<values.length;i++)if(!tokens.get(cursor+i).is(values[i]))return false;return true;}
    /** Descripción: Evalúa la condición «is» sin modificar el estado externo. Entradas: tokens: dato de entrada necesario para la operación; cursor: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean is(List<SqlDdlToken> tokens,int cursor,String value){return cursor>=0&&cursor<tokens.size()&&tokens.get(cursor).is(value);}
    /** Descripción: Ejecuta la operación «identifier» correspondiente a la responsabilidad de NameRead. Entradas: token: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String identifier(SqlDdlToken token){return token==null?"":token.text();}
    /** Descripción: Ejecuta la operación «join» correspondiente a la responsabilidad de NameRead. Entradas: tokens: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String join(List<SqlDdlToken> tokens){StringBuilder out=new StringBuilder();String previous="";for(SqlDdlToken token:tokens){String text=token.text();boolean tight=java.util.Set.of(")",",",";",".").contains(text)||"(".equals(previous)||".".equals(previous);if(!out.isEmpty()&&!tight&&!"(".equals(text))out.append(' ');out.append(text);previous=text;}return out.toString();}
    /** Descripción: Ejecuta la operación «key» correspondiente a la responsabilidad de NameRead. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String key(String value){return value.toLowerCase(Locale.ROOT);}
    /** Descripción: Ejecuta la operación «info» correspondiente a la responsabilidad de NameRead. Entradas: token: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void info(SqlDdlToken token,String code,String message){diagnostics.add(new Diagnostic(code,Severity.INFO,token.line(),token.column(),message));}
    /** Descripción: Ejecuta la operación «warning» correspondiente a la responsabilidad de NameRead. Entradas: token: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void warning(SqlDdlToken token,String code,String message){diagnostics.add(new Diagnostic(code,Severity.WARNING,token.line(),token.column(),message));}
    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de NameRead. Entradas: token: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void error(SqlDdlToken token,String code,String message){diagnostics.add(new Diagnostic(code,Severity.ERROR,token.line(),token.column(),message));}
    private record NameRead(String value,int next){}
    private static final class TableBuilder { final String name; final List<Column> columns=new ArrayList<>(); final List<PrimaryKey> primaryKeys=new ArrayList<>(); final List<ForeignKey> foreignKeys=new ArrayList<>(); final List<UniqueKey> uniqueKeys=new ArrayList<>(); final Map<String,String> annotations=new LinkedHashMap<>(); boolean declared; /** Descripción: Inicializa una instancia de TableBuilder con sus dependencias y datos de entrada. Entradas: name: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ TableBuilder(String name){this.name=name;} /** Descripción: Crea o prepara el resultado de «build» dentro de NameRead. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Table build(){return new Table(name,columns,primaryKeys,foreignKeys,uniqueKeys,annotations);} }
}
