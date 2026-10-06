import type { MetaAttribute, MetaClass, MetaModel, MetaRelation } from '../metamodel-designer/types';
import type { BinaryExpressionOperator, ExpressionNode, ExpressionValueType } from '../validation/types';
import { createValidationDesignerId } from '../validation-designer/validationDesignerFactory';

export function stableTextHash(value: string): string {
    let hash = 0x811c9dc5;
    for (let index = 0; index < value.length; index += 1) {
        hash ^= value.charCodeAt(index);
        hash = Math.imul(hash, 0x01000193);
    }
    return `fnv1a32:${(hash >>> 0).toString(16).padStart(8, '0')}`;
}

export function sanitizeIdentifier(value: string, fallback: string): string {
    const normalized = value.trim().replace(/[^A-Za-z0-9_]+/g, '_').replace(/^_+|_+$/g, '');
    return normalized === '' ? fallback : normalized;
}

export function findMetaClass(metamodel: MetaModel, externalName: string): MetaClass | undefined {
    const simple = externalName.split('!').pop()?.split('::').pop()?.trim() ?? externalName.trim();
    return metamodel.classes.find(item => item.name === simple || item.id === simple || item.name.toLowerCase() === simple.toLowerCase());
}

export function classAttributes(metamodel: MetaModel, classId: string): MetaAttribute[] {
    const visited = new Set<string>();
    const result: MetaAttribute[] = [];
    function collect(id: string): void {
        if (visited.has(id)) return;
        visited.add(id);
        const current = metamodel.classes.find(item => item.id === id);
        if (current !== undefined) result.push(...current.attributes);
        for (const relation of metamodel.relations.filter(item => item.kind === 'INHERITANCE' && item.sourceTypeId === id)) collect(relation.targetTypeId);
    }
    collect(classId);
    return result;
}

export function classRelations(metamodel: MetaModel, classId: string): MetaRelation[] {
    const ids = new Set<string>([classId]);
    let changed = true;
    while (changed) {
        changed = false;
        for (const relation of metamodel.relations.filter(item => item.kind === 'INHERITANCE')) {
            if (ids.has(relation.sourceTypeId) && !ids.has(relation.targetTypeId)) { ids.add(relation.targetTypeId); changed = true; }
        }
    }
    return metamodel.relations.filter(item => item.kind !== 'INHERITANCE' && ids.has(item.sourceTypeId));
}

export function findAttribute(metamodel: MetaModel, classId: string, name: string): MetaAttribute | undefined {
    return classAttributes(metamodel, classId).find(item => item.name === name || item.id === name || item.name.toLowerCase() === name.toLowerCase());
}

export function findRelation(metamodel: MetaModel, classId: string, name: string): MetaRelation | undefined {
    return classRelations(metamodel, classId).find(item => item.name === name || item.id === name || item.name.toLowerCase() === name.toLowerCase());
}

export interface ExternalExpressionParseResult { node: ExpressionNode; complete: boolean; warnings: string[]; }

type TokenKind = 'identifier' | 'number' | 'string' | 'operator' | 'punctuation';
interface Token { kind: TokenKind; value: string; }

export function parseExternalExpression(source: string, metamodel: MetaModel, contextClassId: string): ExternalExpressionParseResult {
    const warnings: string[] = [];
    try {
        const parser = new ExpressionParser(tokenize(source), metamodel, contextClassId, warnings);
        const node = parser.parse();
        if (!parser.atEnd()) warnings.push(`Ignored trailing expression text near "${parser.peek()?.value ?? ''}".`);
        return { node, complete: warnings.length === 0, warnings };
    } catch (error) {
        warnings.push(error instanceof Error ? error.message : 'Expression could not be converted.');
        return { node: booleanLiteral(true), complete: false, warnings };
    }
}

function tokenize(source: string): Token[] {
    const tokens: Token[] = [];
    let index = 0;
    while (index < source.length) {
        const rest = source.slice(index);
        const whitespace = rest.match(/^\s+/);
        if (whitespace !== null) { index += whitespace[0].length; continue; }
        const string = rest.match(/^'(?:''|\\'|[^'])*'|^"(?:\\"|[^"])*"/);
        if (string !== null) { tokens.push({ kind: 'string', value: string[0] }); index += string[0].length; continue; }
        const number = rest.match(/^-?\d+(?:\.\d+)?/);
        if (number !== null) { tokens.push({ kind: 'number', value: number[0] }); index += number[0].length; continue; }
        const punctuation = rest.match(/^(?:->|\.|\(|\)|,)/);
        if (punctuation !== null) { tokens.push({ kind: 'punctuation', value: punctuation[0] }); index += punctuation[0].length; continue; }
        const operator = rest.match(/^(?:<>|!=|>=|<=|==|=|>|<|\+|-|\*|\/)/);
        if (operator !== null) { tokens.push({ kind: 'operator', value: operator[0] }); index += operator[0].length; continue; }
        const identifier = rest.match(/^[A-Za-z_][A-Za-z0-9_:!]*/);
        if (identifier !== null) { tokens.push({ kind: 'identifier', value: identifier[0] }); index += identifier[0].length; continue; }
        throw new Error(`Unsupported expression token near "${rest.slice(0, 18)}".`);
    }
    return tokens;
}

class ExpressionParser {
    private index = 0;
    private readonly tokens: Token[];
    private readonly metamodel: MetaModel;
    private readonly contextClassId: string;
    private readonly warnings: string[];
    constructor(tokens: Token[], metamodel: MetaModel, contextClassId: string, warnings: string[]) {
        this.tokens = tokens;
        this.metamodel = metamodel;
        this.contextClassId = contextClassId;
        this.warnings = warnings;
    }
    atEnd(): boolean { return this.index >= this.tokens.length; }
    peek(): Token | undefined { return this.tokens[this.index]; }
    private take(): Token { const token = this.tokens[this.index]; if (token === undefined) throw new Error('Unexpected end of expression.'); this.index += 1; return token; }
    private match(value: string): boolean { if ((this.peek()?.value ?? '').toLowerCase() !== value.toLowerCase()) return false; this.index += 1; return true; }
    parse(): ExpressionNode { return this.parseOr(); }
    private parseOr(): ExpressionNode { let node = this.parseAnd(); while (this.match('or')) node = binary('OR', node, this.parseAnd()); return node; }
    private parseAnd(): ExpressionNode { let node = this.parseComparison(); while (this.match('and')) node = binary('AND', node, this.parseComparison()); return node; }
    private parseComparison(): ExpressionNode {
        let node = this.parseUnary();
        const token = this.peek()?.value;
        const map: Record<string, BinaryExpressionOperator> = { '=': 'EQUALS', '==': 'EQUALS', '<>': 'NOT_EQUALS', '!=': 'NOT_EQUALS', '>': 'GREATER_THAN', '>=': 'GREATER_OR_EQUAL', '<': 'LESS_THAN', '<=': 'LESS_OR_EQUAL' };
        if (token !== undefined && map[token] !== undefined) { this.take(); node = binary(map[token], node, this.parseUnary()); }
        return node;
    }
    private parseUnary(): ExpressionNode {
        if (this.match('not')) return { kind: 'UNARY', nodeId: createValidationDesignerId('node'), operator: 'NOT', operand: this.parseUnary() };
        return this.parsePostfix();
    }
    private parsePostfix(): ExpressionNode {
        let node = this.parsePrimary();
        while (!this.atEnd()) {
            if (this.match('.') || this.match('->')) {
                const member = this.take().value;
                const lower = member.toLowerCase();
                if (this.match('(')) {
                    const args: ExpressionNode[] = [];
                    if (!this.match(')')) { do { args.push(this.parseOr()); } while (this.match(',')); if (!this.match(')')) throw new Error(`Expected ')' after ${member}.`); }
                    if (lower === 'size') node = { kind: 'UNARY', nodeId: createValidationDesignerId('node'), operator: 'SIZE', operand: node };
                    else if (lower === 'isempty') node = { kind: 'UNARY', nodeId: createValidationDesignerId('node'), operator: 'IS_EMPTY', operand: node };
                    else if (lower === 'notempty' || lower === 'isnotempty') node = { kind: 'UNARY', nodeId: createValidationDesignerId('node'), operator: 'IS_NOT_EMPTY', operand: node };
                    else if (lower === 'oclisundefined' || lower === 'isundefined') node = { kind: 'UNARY', nodeId: createValidationDesignerId('node'), operator: 'IS_NULL', operand: node };
                    else if (lower === 'toupper' || lower === 'touppercase') node = { kind: 'UNARY', nodeId: createValidationDesignerId('node'), operator: 'TO_UPPER', operand: node };
                    else if (lower === 'tolower' || lower === 'tolowercase') node = { kind: 'UNARY', nodeId: createValidationDesignerId('node'), operator: 'TO_LOWER', operand: node };
                    else { this.warnings.push(`Method ${member} is represented as a generic function.`); node = { kind: 'FUNCTION', nodeId: createValidationDesignerId('node'), functionName: member, arguments: [node, ...args] }; }
                } else {
                    const attribute = findAttribute(this.metamodel, this.contextClassId, member);
                    if (attribute !== undefined) node = { kind: 'ATTRIBUTE_ACCESS', nodeId: createValidationDesignerId('node'), target: node, attributeId: attribute.id };
                    else {
                        const relation = findRelation(this.metamodel, this.contextClassId, member);
                        if (relation !== undefined) node = { kind: 'RELATION_NAVIGATION', nodeId: createValidationDesignerId('node'), target: node, relationId: relation.id };
                        else { this.warnings.push(`Feature ${member} was not found in the current metamodel.`); node = { kind: 'FUNCTION', nodeId: createValidationDesignerId('node'), functionName: `unresolvedFeature:${member}`, arguments: [node] }; }
                    }
                }
                continue;
            }
            break;
        }
        return node;
    }
    private parsePrimary(): ExpressionNode {
        if (this.match('(')) { const inner = this.parseOr(); if (!this.match(')')) throw new Error("Expected ')' in expression."); return inner; }
        const token = this.take();
        if (token.kind === 'string') return stringLiteral(unquote(token.value));
        if (token.kind === 'number') return numberLiteral(Number(token.value));
        const lower = token.value.toLowerCase();
        if (lower === 'true' || lower === 'false') return booleanLiteral(lower === 'true');
        if (lower === 'null' || lower === 'oclundefined' || lower === 'undefined') return { kind: 'LITERAL', nodeId: createValidationDesignerId('node'), valueType: { kind: 'NULL' }, value: null };
        if (lower === 'self') return { kind: 'SELF', nodeId: createValidationDesignerId('node') };
        const attribute = findAttribute(this.metamodel, this.contextClassId, token.value);
        if (attribute !== undefined) return { kind: 'ATTRIBUTE_ACCESS', nodeId: createValidationDesignerId('node'), target: { kind: 'SELF', nodeId: createValidationDesignerId('node') }, attributeId: attribute.id };
        const relation = findRelation(this.metamodel, this.contextClassId, token.value);
        if (relation !== undefined) return { kind: 'RELATION_NAVIGATION', nodeId: createValidationDesignerId('node'), target: { kind: 'SELF', nodeId: createValidationDesignerId('node') }, relationId: relation.id };
        this.warnings.push(`Identifier ${token.value} was not resolved in the current metamodel.`);
        return { kind: 'VARIABLE', nodeId: createValidationDesignerId('node'), variableName: token.value };
    }
}

function unquote(value: string): string { return value.slice(1, -1).replace(/''/g, "'").replace(/\\'/g, "'").replace(/\\"/g, '"'); }
function booleanLiteral(value: boolean): ExpressionNode { return { kind: 'LITERAL', nodeId: createValidationDesignerId('node'), valueType: { kind: 'BOOLEAN' }, value }; }
function numberLiteral(value: number): ExpressionNode { return { kind: 'LITERAL', nodeId: createValidationDesignerId('node'), valueType: { kind: Number.isInteger(value) ? 'INTEGER' : 'DECIMAL' }, value }; }
function stringLiteral(value: string): ExpressionNode { return { kind: 'LITERAL', nodeId: createValidationDesignerId('node'), valueType: { kind: 'STRING' }, value }; }
function binary(operator: BinaryExpressionOperator, left: ExpressionNode, right: ExpressionNode): ExpressionNode { return { kind: 'BINARY', nodeId: createValidationDesignerId('node'), operator, left, right }; }

export function externalExpression(node: ExpressionNode, metamodel: MetaModel, syntax: 'OCL' | 'EVL' | 'ATL' = 'OCL'): string {
    const op: Record<BinaryExpressionOperator, string> = { AND: 'and', OR: 'or', EQUALS: '=', NOT_EQUALS: '<>', GREATER_THAN: '>', GREATER_OR_EQUAL: '>=', LESS_THAN: '<', LESS_OR_EQUAL: '<=', ADD: '+', SUBTRACT: '-', MULTIPLY: '*', DIVIDE: '/', CONTAINS: 'includes', STARTS_WITH: 'startsWith', ENDS_WITH: 'endsWith', MATCHES: 'matches' };
    switch (node.kind) {
        case 'LITERAL': return externalLiteral(node.value, node.valueType);
        case 'SELF': return 'self';
        case 'MODEL': return syntax === 'ATL' ? 'thisModule' : 'Model.allInstances()';
        case 'CLASS_INSTANCES': return `${metamodel.classes.find(item => item.id === node.classId)?.name ?? node.classId}.allInstances()`;
        case 'ATTRIBUTE_ACCESS': return `${externalExpression(node.target, metamodel, syntax)}.${attributeName(metamodel, node.attributeId)}`;
        case 'RELATION_NAVIGATION': return `${externalExpression(node.target, metamodel, syntax)}.${relationName(metamodel, node.relationId)}`;
        case 'UNARY': {
            if (node.operator === 'NOT') return `not (${externalExpression(node.operand, metamodel, syntax)})`;
            if (node.operator === 'IS_NULL') return `${externalExpression(node.operand, metamodel, syntax)}.oclIsUndefined()`;
            if (node.operator === 'IS_NOT_NULL') return `not ${externalExpression(node.operand, metamodel, syntax)}.oclIsUndefined()`;
            if (node.operator === 'IS_EMPTY') return `${externalExpression(node.operand, metamodel, syntax)}->isEmpty()`;
            if (node.operator === 'IS_NOT_EMPTY') return `${externalExpression(node.operand, metamodel, syntax)}->notEmpty()`;
            if (node.operator === 'SIZE') return `${externalExpression(node.operand, metamodel, syntax)}->size()`;
            if (node.operator === 'NEGATE') return `-(${externalExpression(node.operand, metamodel, syntax)})`;
            if (node.operator === 'TO_UPPER') return `${externalExpression(node.operand, metamodel, syntax)}.toUpper()`;
            return `${externalExpression(node.operand, metamodel, syntax)}.toLower()`;
        }
        case 'BINARY': return `(${externalExpression(node.left, metamodel, syntax)} ${op[node.operator]} ${externalExpression(node.right, metamodel, syntax)})`;
        case 'COLLECTION': {
            const source = externalExpression(node.source, metamodel, syntax);
            if (node.operator === 'COUNT') return `${source}->size()`;
            const method = node.operator.toLowerCase();
            return node.expression === undefined ? `${source}->${method}()` : `${source}->${method}(value | ${externalExpression(node.expression, metamodel, syntax)})`;
        }
        case 'CONDITIONAL': return `if ${externalExpression(node.condition, metamodel, syntax)} then ${externalExpression(node.whenTrue, metamodel, syntax)} else ${externalExpression(node.whenFalse, metamodel, syntax)} endif`;
        case 'FUNCTION': return `${node.functionName}(${node.arguments.map(item => externalExpression(item, metamodel, syntax)).join(', ')})`;
        case 'VARIABLE': return node.variableName;
        case 'OPERATION_CALL': return `${externalExpression(node.target, metamodel, syntax)}.${operationName(metamodel, node.operationId)}(${node.arguments.map(item => externalExpression(item, metamodel, syntax)).join(', ')})`;
    }
}

function externalLiteral(value: unknown, type: ExpressionValueType): string {
    if (value === null || value === undefined || type.kind === 'NULL') return 'null';
    if (typeof value === 'string') return `'${value.replace(/'/g, "''")}'`;
    if (typeof value === 'boolean') return value ? 'true' : 'false';
    return String(value);
}

export function attributeName(metamodel: MetaModel, id: string): string { return metamodel.classes.flatMap(item => item.attributes).find(item => item.id === id)?.name ?? id; }
export function relationName(metamodel: MetaModel, id: string): string { return metamodel.relations.find(item => item.id === id)?.name ?? id; }
export function operationName(metamodel: MetaModel, id: string): string { return metamodel.classes.flatMap(item => item.operations).find(item => item.id === id)?.name ?? id; }
export function className(metamodel: MetaModel, id: string | undefined): string { return metamodel.classes.find(item => item.id === id)?.name ?? id ?? 'Model'; }

export function parseBalancedBlocks(source: string, keywordPattern: RegExp): Array<{ header: string; body: string; start: number; end: number }> {
    const flags = keywordPattern.flags.includes('g') ? keywordPattern.flags : `${keywordPattern.flags}g`;
    const pattern = new RegExp(keywordPattern.source, flags);
    const blocks: Array<{ header: string; body: string; start: number; end: number }> = [];
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(source)) !== null) {
        const brace = source.indexOf('{', match.index + match[0].length - 1);
        if (brace < 0) continue;
        let depth = 0; let quote = ''; let end = -1;
        for (let index = brace; index < source.length; index += 1) {
            const char = source[index];
            const previous = index > 0 ? source[index - 1] : '';
            if (quote !== '') { if (char === quote && previous !== '\\') quote = ''; continue; }
            if (char === "'" || char === '"') { quote = char; continue; }
            if (char === '{') depth += 1;
            else if (char === '}') { depth -= 1; if (depth === 0) { end = index + 1; break; } }
        }
        if (end < 0) break;
        blocks.push({ header: match[0].slice(0, match[0].lastIndexOf('{')).trim(), body: source.slice(brace + 1, end - 1), start: match.index, end });
        pattern.lastIndex = end;
    }
    return blocks;
}
