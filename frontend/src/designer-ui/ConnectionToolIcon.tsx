/** Icono común de relación utilizado por paletas visuales HYDRA. */
export function ConnectionToolIcon() {
    return <span className="hydra-connection-tool-icon" role="img" aria-label="Connection arrow tool">
        <svg viewBox="0 0 40 24" focusable="false">
            <circle className="source" cx="5" cy="12" r="3.5" />
            <line x1="9" y1="12" x2="31" y2="12" />
            <polyline points="27,7 33,12 27,17" />
            <circle className="target" cx="35" cy="12" r="3.5" />
        </svg>
    </span>;
}
