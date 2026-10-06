interface Props { moduleTitle: string; }

/** Fallback común mientras se descarga el chunk de una capacidad independiente. */
export function HydraModuleLoading({ moduleTitle }: Props) {
    return <section aria-busy="true" aria-live="polite" className="hydra-module-loading">
        <span aria-hidden="true" className="hydra-module-loading-spinner" />
        <div><h2>Loading {moduleTitle}</h2><p>HYDRA is downloading only the code required by this module.</p></div>
    </section>;
}
