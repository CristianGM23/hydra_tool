declare module 'axe-core' {
    export interface Result { id: string; impact?: string | null; description: string; help: string; nodes: unknown[]; }
    export interface AxeResults { violations: Result[]; incomplete: Result[]; }
    interface Axe { run(context?: Element | Document | string): Promise<AxeResults>; }
    const axe: Axe;
    export default axe;
}
