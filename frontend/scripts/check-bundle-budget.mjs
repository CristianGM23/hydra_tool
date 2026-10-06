import { readFile, stat } from 'node:fs/promises';
import { gzipSync } from 'node:zlib';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

function matchesModule(entry, moduleBudget) {
    const patterns = Array.isArray(moduleBudget.patterns) ? moduleBudget.patterns : [];
    return patterns.some(pattern => entry.file.toLowerCase().includes(String(pattern).toLowerCase()));
}

export function evaluateBundle(entries, budget) {
    const exemptions = new Set(budget.exemptions ?? []);
    const violations = [];
    const modules = {};
    let totalJavaScriptBytes = 0;
    let totalApplicationJavaScriptBytes = 0;
    for (const entry of entries) {
        if (!entry.file.endsWith('.js')) continue;
        totalJavaScriptBytes += entry.bytes;
        const matchingBudgets = Object.entries(budget.moduleBudgets ?? {}).filter(([, moduleBudget]) => matchesModule(entry, moduleBudget));
        const excludedFromGenericChunkBudget = matchingBudgets.some(([, moduleBudget]) => moduleBudget.excludeFromGenericChunkBudget === true);
        const excludedFromApplicationTotal = matchingBudgets.some(([, moduleBudget]) => moduleBudget.excludeFromApplicationTotal === true);
        if (!excludedFromApplicationTotal) totalApplicationJavaScriptBytes += entry.bytes;
        if (!exemptions.has(entry.file) && !excludedFromGenericChunkBudget) {
            const rawLimit = entry.isEntry ? budget.entryMaxBytes : budget.asyncChunkMaxBytes;
            const gzipLimit = entry.isEntry ? budget.entryMaxGzipBytes : budget.asyncChunkMaxGzipBytes;
            if (entry.bytes > rawLimit) violations.push(`${entry.file} is ${entry.bytes} bytes; limit ${rawLimit}`);
            if (entry.gzipBytes > gzipLimit) violations.push(`${entry.file} gzip is ${entry.gzipBytes} bytes; limit ${gzipLimit}`);
        }
        for (const [moduleId, moduleBudget] of matchingBudgets) {
            const current = modules[moduleId] ?? { bytes: 0, gzipBytes: 0, files: [] };
            current.bytes += entry.bytes;
            current.gzipBytes += entry.gzipBytes;
            current.files.push(entry.file);
            modules[moduleId] = current;
        }
    }
    if (Number.isFinite(budget.totalJavaScriptMaxBytes) && totalJavaScriptBytes > budget.totalJavaScriptMaxBytes) {
        violations.push(`Total JavaScript is ${totalJavaScriptBytes} bytes; limit ${budget.totalJavaScriptMaxBytes}`);
    }
    const applicationLimit = budget.totalApplicationJavaScriptMaxBytes ?? budget.totalJavaScriptMaxBytes;
    if (Number.isFinite(applicationLimit) && totalApplicationJavaScriptBytes > applicationLimit) {
        violations.push(`Application JavaScript is ${totalApplicationJavaScriptBytes} bytes; limit ${applicationLimit}`);
    }
    for (const [moduleId, moduleBudget] of Object.entries(budget.moduleBudgets ?? {})) {
        const measurement = modules[moduleId] ?? { bytes: 0, gzipBytes: 0, files: [] };
        if (moduleBudget.required === true && measurement.files.length === 0) {
            violations.push(`Module ${moduleId} did not produce a matching JavaScript chunk`);
        }
        if (measurement.bytes > moduleBudget.maxBytes) {
            violations.push(`Module ${moduleId} is ${measurement.bytes} bytes; limit ${moduleBudget.maxBytes}`);
        }
        if (measurement.gzipBytes > moduleBudget.maxGzipBytes) {
            violations.push(`Module ${moduleId} gzip is ${measurement.gzipBytes} bytes; limit ${moduleBudget.maxGzipBytes}`);
        }
    }
    return { violations, totalJavaScriptBytes, totalApplicationJavaScriptBytes, modules };
}

async function loadEntries(distRoot, manifest) {
    const unique = new Map();
    for (const descriptor of Object.values(manifest)) {
        if (typeof descriptor?.file !== 'string' || !descriptor.file.endsWith('.js')) continue;
        const current = unique.get(descriptor.file);
        unique.set(descriptor.file, { file: descriptor.file, isEntry: Boolean(descriptor.isEntry || current?.isEntry) });
    }
    const entries = [];
    for (const item of unique.values()) {
        const path = resolve(distRoot, item.file);
        const bytes = (await stat(path)).size;
        const content = await readFile(path);
        entries.push({ ...item, bytes, gzipBytes: gzipSync(content).byteLength });
    }
    return entries.sort((left, right) => Number(right.isEntry) - Number(left.isEntry) || right.bytes - left.bytes);
}

async function main() {
    const frontendRoot = resolve(import.meta.dirname, '..');
    const budgetPath = resolve(frontendRoot, 'config/hydra-bundle-budget.json');
    const manifestPath = resolve(frontendRoot, 'dist/.vite/manifest.json');
    const budget = JSON.parse(await readFile(budgetPath, 'utf8'));
    const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
    const entries = await loadEntries(resolve(frontendRoot, 'dist'), manifest);
    const result = evaluateBundle(entries, budget);
    console.log('HYDRA bundle budget');
    for (const entry of entries) {
        console.log(`${entry.isEntry ? 'ENTRY' : 'ASYNC'}  ${entry.file}  ${entry.bytes} B  ${entry.gzipBytes} B gzip`);
    }
    for (const [moduleId, measurement] of Object.entries(result.modules)) {
        console.log(`MODULE ${moduleId}  ${measurement.bytes} B  ${measurement.gzipBytes} B gzip  ${measurement.files.join(', ')}`);
    }
    console.log(`Total JavaScript: ${result.totalJavaScriptBytes} B`);
    console.log(`Application JavaScript (vendor exclusions applied): ${result.totalApplicationJavaScriptBytes} B`);
    if (result.violations.length === 0) {
        console.log('PASS: all bundle budgets are satisfied.');
        return;
    }
    for (const violation of result.violations) console.error(`BUDGET: ${violation}`);
    const mode = process.env.HYDRA_BUNDLE_BUDGET_MODE ?? budget.mode ?? 'strict';
    if (mode === 'strict') process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
