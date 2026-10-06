import { readFileSync, readdirSync } from 'node:fs';
import { extname, join } from 'node:path';
import { describe, expect, it } from 'vitest';

function sourceFiles(directory: string): string[] {
    return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) return sourceFiles(path);
        return extname(entry.name) === '.tsx' ? [path] : [];
    });
}

describe('global controlled checkbox contract', () => {
    it('binds every application checkbox to an explicit checked value', () => {
        const uncontrolled: string[] = [];
        for (const file of sourceFiles(join(process.cwd(), 'src'))) {
            const source = readFileSync(file, 'utf8');
            for (const match of source.matchAll(/<input\b[^>]*type=["']checkbox["'][^>]*>/gs)) {
                if (!/\bchecked\s*=/.test(match[0])) uncontrolled.push(file);
            }
        }
        expect(uncontrolled).toEqual([]);
    });
});
