/**
 * Declara los marcadores SVG utilizados por las conexiones.
 *
 * @returns definiciones SVG reutilizables.
 */
export function MarkerDefinitions() {
    const content = (
        <svg aria-hidden="true" className="marker-definitions">
            <defs>
                <marker id="marker-arrow-open" markerHeight="10" markerWidth="10" orient="auto-start-reverse" refX="9" refY="5" viewBox="0 0 10 10">
                    <path d="M 1 1 L 9 5 L 1 9" fill="none" stroke="context-stroke" strokeWidth="1.5" />
                </marker>

                <marker id="marker-arrow-closed" markerHeight="10" markerWidth="10" orient="auto-start-reverse" refX="9" refY="5" viewBox="0 0 10 10">
                    <path d="M 1 1 L 9 5 L 1 9 Z" fill="context-stroke" stroke="context-stroke" />
                </marker>

                <marker id="marker-triangle-open" markerHeight="12" markerWidth="12" orient="auto-start-reverse" refX="10" refY="6" viewBox="0 0 12 12">
                    <path d="M 1 1 L 11 6 L 1 11 Z" fill="white" stroke="context-stroke" strokeWidth="1.5" />
                </marker>

                <marker id="marker-diamond-open" markerHeight="12" markerWidth="12" orient="auto-start-reverse" refX="10" refY="6" viewBox="0 0 12 12">
                    <path d="M 1 6 L 6 1 L 11 6 L 6 11 Z" fill="white" stroke="context-stroke" strokeWidth="1.5" />
                </marker>

                <marker id="marker-diamond-closed" markerHeight="12" markerWidth="12" orient="auto-start-reverse" refX="10" refY="6" viewBox="0 0 12 12">
                    <path d="M 1 6 L 6 1 L 11 6 L 6 11 Z" fill="context-stroke" stroke="context-stroke" />
                </marker>

                <marker id="marker-circle-open" markerHeight="12" markerWidth="12" orient="auto-start-reverse" refX="10" refY="6" viewBox="0 0 12 12">
                    <circle cx="6" cy="6" fill="white" r="4.5" stroke="context-stroke" strokeWidth="1.5" />
                </marker>

                <marker id="marker-circle-closed" markerHeight="12" markerWidth="12" orient="auto-start-reverse" refX="10" refY="6" viewBox="0 0 12 12">
                    <circle cx="6" cy="6" fill="context-stroke" r="4.5" stroke="context-stroke" />
                </marker>

                <marker id="marker-square-open" markerHeight="12" markerWidth="12" orient="auto-start-reverse" refX="10" refY="6" viewBox="0 0 12 12">
                    <rect fill="white" height="9" stroke="context-stroke" strokeWidth="1.5" width="9" x="1.5" y="1.5" />
                </marker>

                <marker id="marker-square-closed" markerHeight="12" markerWidth="12" orient="auto-start-reverse" refX="10" refY="6" viewBox="0 0 12 12">
                    <rect fill="context-stroke" height="9" stroke="context-stroke" width="9" x="1.5" y="1.5" />
                </marker>
            </defs>
        </svg>
    );

    return content;
}