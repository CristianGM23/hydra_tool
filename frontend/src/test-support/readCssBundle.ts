import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

/** Reads a CSS entry point together with its local @import graph in cascade order. */
export function readCssBundle(relativePath: string): string {
    const candidates = [resolve(process.cwd(), relativePath), resolve(process.cwd(), 'frontend', relativePath)];
    const entry = candidates.find(existsSync);
    if(entry === undefined) throw new Error(`Could not locate CSS bundle ${relativePath}`);
    const visited = new Set<string>();
    const read = (path: string): string => {
        if(visited.has(path)) return '';
        visited.add(path);
        const source = readFileSync(path, 'utf8');
        const imports = [...source.matchAll(/@import\s+['"]([^'"]+)['"]\s*;/g)]
            .map(match => match[1])
            .filter(value => value.startsWith('.'));
        const imported = imports.map(value => read(resolve(dirname(path), value))).join('\n');
        return `${imported}\n${source.replace(/@import\s+['"][^'"]+['"]\s*;/g, '')}`;
    };
    return read(entry);
}
