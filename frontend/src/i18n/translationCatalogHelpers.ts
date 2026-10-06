/** Pure helpers shared by the translation facade. Kept separate so the facade remains below the reviewed architecture ceiling. */
const PROTECTED_NAMES = new Set([
    'HYDRA', 'Windows', 'Linux', 'macOS', 'Eclipse', 'Ecore', 'EMF', 'GMF', 'Sirius', 'Xtext', 'Langium',
    'Spring', 'React', 'Vite', 'Maven', 'MongoDB', 'Java', 'Python', 'TypeScript', 'JavaScript', 'Node.js',
    'Docker', 'Kubernetes', 'Git', 'GitHub', 'GitLab', 'OneDrive', 'Google Drive', 'Dropbox', 'EventFlow', 'JSON', 'XML', 'SVG', 'PNG', 'PWA', 'Tauri', 'ATL', 'EVL', 'EOL', 'OCL', 'AsyncAPI', 'Node-RED', 'Siddhi'
]);

export function runtimeContextLabel(context: string): string {
    const labels: Readonly<Record<string,string>> = {
        validation: 'validación', problems: 'problemas', import: 'importación', update: 'actualización',
        compatibility: 'compatibilidad', publication: 'publicación', distribution: 'distribución',
        integration: 'integración', roundtrip: 'sincronización', execution: 'ejecución',
        layers: 'intercambio de capas', connection: 'conexión externa', operation: 'la operación'
    };
    return labels[context.trim().toLowerCase()] ?? 'la operación';
}

export function translateGuidedOperator(operator: string): string {
    const operators: Readonly<Record<string, string>> = {
        'equal to': 'es igual a', 'different from': 'es distinto de', 'greater than': 'es mayor que',
        'greater than or equal to': 'es mayor o igual que', 'less than': 'es menor que',
        'less than or equal to': 'es menor o igual que', 'contains': 'contiene', 'starts with': 'empieza por',
        'ends with': 'termina en', 'matches pattern': 'coincide con el patrón'
    };
    return operators[operator] ?? operator;
}

export function isProtectedText(text: string): boolean {
    if (PROTECTED_NAMES.has(text)) return true;
    if (/^[A-Z0-9_:-]{2,}$/.test(text)) return true;
    if (/^[A-Za-z]:\\/.test(text) || text.includes('\\') || /^\.?\/?[\w.-]+\/[\w./-]+$/.test(text)) return true;
    if (/^(?:https?:|file:|urn:|event:|rule:|flow:|binding:|points:|reward:|challenge:|achievement:)/i.test(text)) return true;
    return /^[\w.-]+\.(?:json|xml|ecore|xmi|svg|png|zip|jar|ts|tsx|java)$/i.test(text);
}
