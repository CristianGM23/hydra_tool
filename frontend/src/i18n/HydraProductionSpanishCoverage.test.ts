import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve, relative, join } from 'node:path';
import * as ts from 'typescript';
import { describe, expect, it } from 'vitest';
import { translateUiText } from './translationCatalog';

const VISIBLE_PROPERTIES = new Set([
    'label', 'title', 'help', 'description', 'placeholder', 'aria-label', 'alt',
    'introduction', 'finalNote', 'message', 'heading', 'subtitle', 'emptyText',
    'confirmLabel', 'cancelLabel', 'closeLabel', 'maximizeLabel', 'restoreLabel', 'advancedLabel', 'guidedLabel', 'finishLabel', 'downloadLabel', 'ariaLabel', 'emptyMessage', 'emptySelectionLabel', 'optionHelpTitle', 'purpose', 'recommendation', 'rationale',
    'tooltip', 'hint', 'caption', 'summary', 'successMessage', 'errorMessage', 'warningMessage',
    'outcome', 'callbackNote', 'idHelp', 'where', 'detail', 'capability', 'result', 'steps', 'context', 'scope'
]);

/**
 * Technical tokens, product names, examples and values that must not be
 * translated even when they are visible in the interface.
 */
const INTENTIONALLY_UNTRANSLATED = new Set([
    'HYDRA 1.0', 'HYbrid DSL &amp; Representation Architecture', 'bytes', 'bytes.', 'i',
    'people-model', 'EN', 'ES', 'X (px)', 'Y (px)', '+F', '+D',
    'C:\\Users\\...\\ModelingProjects', '...\\Railway', 'gen', 'sync-…', 'Español',
    'System UI', 'Segoe UI', 'Arial', 'Verdana', 'Tahoma', 'Georgia', 'Cascadia Code', 'JetBrains Mono', 'Fira Code', 'Consolas', 'Menlo / Monaco', 'Source Code Pro', '· v', 'v',
    'Book.authors, library.ecore, bytecode…', 'OCL', 'EVL', 'ATL', 'M2M', 'M2T', 'T2M', 'Sirius AQL', 'Epsilon Validation Language (EVL)', 'Epsilon Object Language (EOL)', 'AsyncAPI 3 JSON',
    'Java', 'JavaScript', 'TypeScript', 'Go', 'Python', 'Blockly', 'Blockly JSON', 'Blockly JavaScript', 'Xtext', 'XTEXT', 'Langium', 'LANGIUM', 'ANTLR', 'ANTLR 4', 'Monaco', 'entry', 'ID', 'Ecore', 'EMF / Emfatic', 'EPackage XMI', 'Neutral JSON', 'Neutral XML', 'draw.io UML', 'draw.io', 'Eclipse GLSP', 'PlantUML', 'Agent Markdown', 'Payload', 'R', 'G', 'B', 'ms', 'SHA-256', 'Color', 'Editable',
    'type', 'field_input', 'field_number', 'field_checkbox', 'input_value', 'input_statement', 'check', 'output', 'previousStatement', 'nextStatement', 'defineBlocksWithJsonArray(...)', 'jsonInit(...)',
    'General', 'Variables', '+ Variable', 'Variable', 'Compensable', 'No', 'Actor',
    'Beta', 'p2-…', 'Representable', 'Plug-ins', 'Bundle', 'HTML',
    'https://updates.example.org/releases/latest/', 'Decimal', 'Error', 'Incremental', 'Normal',
    'github-main', 'my-organization', 'models', 'group/project', 'https://git.example.org/team/hydra-artifacts.git', 'oauth2', 'main',
    'GitHub', 'GitLab', 'OneDrive', 'Dropbox', 'HYDRA', 'Local', 'Windows x64', 'Linux x64', 'macOS x64', 'macOS ARM64', 'https://…',
    'Markdown', 'assets/notation/node.svg', 'assets/notation/node.png', 'element:id, element:id', 'element:id',
    'Sirius Desktop (.odesign)', 'Sirius Web (View DSL / EMF JSON)', 'GMF (graph, tool, map and generator models)',
    'Google Drive', 'HYDRA/exports',
    'https://github.com/org/repository/tree/main/updatesite', 'Auto / main', 'Auto-detect / updatesite',
    'GitHub → Settings → Developer settings → OAuth Apps → New OAuth App',
    'GitLab → Preferences / User Settings → Applications → Add new application',
    'Microsoft Entra admin center → App registrations → New registration',
    'Google Cloud Console → APIs & Services → Credentials → Create credentials → OAuth client ID',
    'Dropbox App Console → Create app → Settings'
]);

type VisibleCopy = { file: string; line: number; text: string };

function sourceRoot(): string {
    const candidates = [resolve(process.cwd(), 'src'), resolve(process.cwd(), 'frontend', 'src')];
    const root = candidates.find(candidate => existsSync(candidate));
    if (root === undefined) throw new Error('Could not locate the frontend source directory.');
    return root;
}

function propertyName(node: ts.Node): string {
    return ts.isIdentifier(node) || ts.isStringLiteral(node) ? node.text : '';
}

function collectLiteralBranches(node: ts.Expression | undefined, add: (node: ts.Node, value: string) => void): void {
    if (node === undefined) return;
    if (ts.isStringLiteralLike(node) || ts.isNoSubstitutionTemplateLiteral(node)) { add(node, node.text); return; }
    if (ts.isConditionalExpression(node)) { collectLiteralBranches(node.whenTrue, add); collectLiteralBranches(node.whenFalse, add); return; }
    if (ts.isArrayLiteralExpression(node)) { for (const element of node.elements) collectLiteralBranches(element as ts.Expression, add); return; }
    if (ts.isBinaryExpression(node) && (node.operatorToken.kind === ts.SyntaxKind.BarBarToken || node.operatorToken.kind === ts.SyntaxKind.QuestionQuestionToken)) {
        collectLiteralBranches(node.left, add); collectLiteralBranches(node.right, add);
    }
}

function productionVisibleCopy(): VisibleCopy[] {
    const root = sourceRoot();
    const found: VisibleCopy[] = [];

    const add = (file: string, node: ts.Node, value: string): void => {
        const text = value.replace(/\s+/g, ' ').trim();
        if (text === '' || !/[A-Za-z]/.test(text) || text.length > 800) return;
        const sourceFile = node.getSourceFile();
        const location = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
        found.push({ file: relative(root, file).replaceAll('\\', '/'), line: location.line + 1, text });
    };

    const visit = (file: string, sourceFile: ts.SourceFile, node: ts.Node): void => {
        if (ts.isJsxText(node)) add(file, node, node.getText(sourceFile));
        if (ts.isJsxAttribute(node) && VISIBLE_PROPERTIES.has(propertyName(node.name)) && node.initializer !== undefined) {
            const initializer = node.initializer;
            if (ts.isStringLiteral(initializer)) add(file, initializer, initializer.text);
            if (ts.isJsxExpression(initializer)) {
                collectLiteralBranches(initializer.expression, (literal, value) => add(file, literal, value));
            }
        }
        if (ts.isPropertyAssignment(node) && VISIBLE_PROPERTIES.has(propertyName(node.name))) {
            collectLiteralBranches(node.initializer, (literal, value) => add(file, literal, value));
        }
        if (ts.isJsxExpression(node) && (ts.isJsxElement(node.parent) || ts.isJsxFragment(node.parent))) {
            collectLiteralBranches(node.expression, (literal, value) => add(file, literal, value));
        }
        if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 't' && node.arguments.length > 0) {
            const argument = node.arguments[0];
            if (ts.isStringLiteralLike(argument) || ts.isNoSubstitutionTemplateLiteral(argument)) add(file, argument, argument.text);
        }
        ts.forEachChild(node, child => visit(file, sourceFile, child));
    };

    const walk = (directory: string): void => {
        for (const entry of readdirSync(directory, { withFileTypes: true })) {
            const entryPath = join(directory, entry.name);
            if (entry.isDirectory()) {
                walk(entryPath);
            } else if (/\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name) && !entry.name.endsWith('.d.ts')) {
                const relativePath = relative(root, entryPath).replaceAll('\\', '/');
                if (relativePath.startsWith('i18n/') && /(?:SpanishCatalog|translationCatalog|runtimeSpanishPatterns)/.test(entry.name)) continue;
                const source = readFileSync(entryPath, 'utf8');
                const sourceFile = ts.createSourceFile(
                    entryPath,
                    source,
                    ts.ScriptTarget.Latest,
                    true,
                    entryPath.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS
                );
                visit(entryPath, sourceFile, sourceFile);
            }
        }
    };

    walk(root);
    return found;
}

describe('production Spanish coverage', () => {
    it('translates every static visible production string unless it is an approved technical value', () => {
        const missing = productionVisibleCopy()
            .filter(item => !INTENTIONALLY_UNTRANSLATED.has(item.text))
            .filter(item => translateUiText(item.text, 'es') === item.text)
            .filter((item, index, items) => items.findIndex(candidate => candidate.text === item.text) === index);

        expect(missing, missing.map(item => `${item.file}:${item.line} · ${item.text}`).join('\n')).toEqual([]);
    }, 60_000);

    it('keeps the approved technical values unchanged', () => {
        for (const technical of INTENTIONALLY_UNTRANSLATED) {
            expect(translateUiText(technical, 'es'), technical).toBe(technical);
        }
    });
});
