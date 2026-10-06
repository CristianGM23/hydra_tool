export type TechnicalSettingValueKind = 'BOOLEAN' | 'BYTES' | 'SECONDS' | 'MILLISECONDS' | 'HOURS' | 'COUNT' | 'RATIO' | 'TEXT';

export interface TechnicalSettingPresentation {
    label: string;
    help: string;
    valueKind: TechnicalSettingValueKind;
    unitLabel?: string;
}

const PRESENTATION: Record<string, TechnicalSettingPresentation> = {
    'runtime.headless': {
        label: 'Backend execution mode', valueKind: 'BOOLEAN',
        help: 'Controls whether the HYDRA backend starts without desktop user-interface facilities. It normally remains disabled in desktop installations and enabled in server-only deployments. This value is decided when HYDRA starts.'
    },
    'dsl.projects.local.native-path-picker-enabled': {
        label: 'Native file and folder picker', valueKind: 'BOOLEAN',
        help: 'Indicates whether HYDRA can open the operating-system file and folder selector. It is useful on desktop installations. Server and browser-only deployments normally keep it unavailable. This value is decided when HYDRA starts.'
    },
    'dsl.projects.local.native-path-picker-local-only': {
        label: 'Native picker restricted to this computer', valueKind: 'BOOLEAN',
        help: 'Prevents remote browser clients from asking the server to open a local operating-system path selector. Keep this enabled unless HYDRA is running as a trusted single-user desktop installation.'
    },
    'dsl.projects.local.max-upload-file-size-bytes': {
        label: 'Maximum project file upload', valueKind: 'BYTES',
        help: 'Largest individual project file that HYDRA accepts when it is uploaded through the browser. Increase it only when legitimate project resources exceed the current limit; larger limits consume more memory and network capacity.'
    },
    'dsl.imports.metamodel.max-file-size-bytes': {
        label: 'Maximum metamodel import size', valueKind: 'BYTES',
        help: 'Largest standalone metamodel file accepted by the import pipeline. This protects HYDRA from accidentally loading unexpectedly large input files.'
    },
    'dsl.imports.metamodel.python.fallback-enabled': {
        label: 'Alternative metamodel parser', valueKind: 'BOOLEAN',
        help: 'Allows HYDRA to use its bounded fallback parser when the preferred Python AST analysis process is unavailable. Disable it only when your deployment requires imports to fail instead of using the fallback path.'
    },
    'dsl.imports.metamodel.python.timeout-seconds': {
        label: 'Metamodel parser time limit', valueKind: 'SECONDS', unitLabel: 'seconds',
        help: 'Maximum time HYDRA waits for the external Python AST analysis process. If complex source-code imports are being terminated too early, review this value together with the source size and machine capacity.'
    },
    'dsl.imports.update-site.max-upload-bytes': {
        label: 'Maximum Update Site package size', valueKind: 'BYTES',
        help: 'Maximum compressed size of an Eclipse Update Site or repository package accepted for analysis.'
    },
    'dsl.imports.update-site.max-entry-bytes': {
        label: 'Maximum extracted Update Site file', valueKind: 'BYTES',
        help: 'Largest single file HYDRA will extract from an Update Site archive. It is an archive-safety limit and normally should not be increased unless a known legitimate entry exceeds it.'
    },
    'dsl.imports.update-site.max-uncompressed-bytes': {
        label: 'Maximum expanded Update Site size', valueKind: 'BYTES',
        help: 'Maximum total size after an Update Site archive is decompressed. This limit helps protect the application from archive expansion attacks and accidental oversized imports.'
    },
    'dsl.imports.update-site.max-compression-ratio': {
        label: 'Maximum archive compression ratio', valueKind: 'RATIO',
        help: 'Maximum accepted relationship between compressed and expanded data in Update Site archives. Very high ratios can indicate an archive bomb, so this is a security limit rather than a performance preference.'
    },
    'dsl.imports.update-site.max-archive-read-millis': {
        label: 'Update Site read time budget', valueKind: 'MILLISECONDS', unitLabel: 'milliseconds',
        help: 'Maximum wall-clock time allowed for reading and inspecting an Update Site archive before HYDRA stops the analysis.'
    },
    'dsl.imports.update-site.max-archive-cpu-millis': {
        label: 'Update Site CPU time budget', valueKind: 'MILLISECONDS', unitLabel: 'milliseconds',
        help: 'Maximum processor time that the archive-analysis phase may consume. It prevents malformed or unusually complex archives from monopolizing the backend.'
    },
    'dsl.imports.update-site.repository-snapshot-max-count': {
        label: 'Maximum repository snapshots', valueKind: 'COUNT',
        help: 'Maximum number of verified Update Site repository snapshots retained by HYDRA. Older snapshots are removed as the bounded history is rotated.'
    },
    'dsl.imports.update-site.repository-snapshot-retention-hours': {
        label: 'Repository snapshot retention', valueKind: 'HOURS', unitLabel: 'hours',
        help: 'How long verified Update Site repository snapshots are retained before they become eligible for cleanup.'
    },
    'dsl.imports.update-site.remote-cache-enabled': {
        label: 'Remote Update Site cache', valueKind: 'BOOLEAN',
        help: 'Allows HYDRA to reuse recently downloaded remote Update Site resources inside its bounded cache. Disable it when every analysis must fetch a fresh copy from the remote source.'
    },
    'dsl.imports.update-site.analysis-cache-entries': {
        label: 'Cached Update Site analyses', valueKind: 'COUNT',
        help: 'Maximum number of Update Site analysis results kept in memory for reuse. Reducing it lowers memory usage; increasing it can avoid repeated analysis when the same sources are inspected frequently.'
    },
    'dsl.imports.update-site.analysis-cache-ttl-seconds': {
        label: 'Update Site analysis cache duration', valueKind: 'SECONDS', unitLabel: 'seconds',
        help: 'How long an in-memory Update Site analysis may be reused before HYDRA recomputes it.'
    },
    'dsl.portability.packages.cache-enabled': {
        label: 'Capability package cache', valueKind: 'BOOLEAN',
        help: 'Allows local reuse of already inspected capability packages. Disable it when the deployment requires every package to be read again from its source.'
    },
    'dsl.portability.packages.require-signature': {
        label: 'Require package signature', valueKind: 'BOOLEAN',
        help: 'When enabled, HYDRA rejects imported capability packages that do not provide the required authenticity signature. This is a startup-bound security policy.'
    },
    'dsl.portability.packages.maximum-archive-bytes': {
        label: 'Maximum capability package size', valueKind: 'BYTES',
        help: 'Largest capability-package archive HYDRA accepts. It limits memory, disk and decompression exposure during package import.'
    },
    'hydra.remote-auth.allow-loopback-http': {
        label: 'Allow HTTP for local OAuth return only', valueKind: 'BOOLEAN',
        help: 'Permits non-HTTPS OAuth callback addresses only when they point to this same computer (loopback). Remote callbacks must remain HTTPS. Do not enable general insecure HTTP callbacks.'
    },
    'hydra.native-git.enabled': {
        label: 'Use installed Git executable', valueKind: 'BOOLEAN',
        help: 'Controls whether HYDRA may invoke the allowlisted Git executable installed on the machine for supported repository operations. This value is decided when HYDRA starts.'
    },
    'hydra.native-git.maximum-output-bytes': {
        label: 'Maximum retained Git command output', valueKind: 'BYTES',
        help: 'Maximum amount of stdout/stderr retained from an allowed Git process. Additional output is bounded so a noisy process cannot consume unlimited memory.'
    },
    'hydra.security.input.max-structured-request-bytes': {
        label: 'Maximum structured request size', valueKind: 'BYTES',
        help: 'Largest normal JSON/XML-style HTTP request accepted by HYDRA. This is an input-safety boundary and should only be raised for known legitimate payloads.'
    },
    'hydra.security.input.max-multipart-request-bytes': {
        label: 'Maximum upload request size', valueKind: 'BYTES',
        help: 'Largest multipart HTTP request accepted by HYDRA, including uploaded files and form fields. This is a security and resource-protection limit.'
    }
};

export function technicalSettingPresentation(key: string): TechnicalSettingPresentation {
    return PRESENTATION[key] ?? {
        label: 'Advanced HYDRA setting',
        help: 'This is an allowlisted runtime setting. Keep the current value unless a HYDRA diagnostic or deployment guide explicitly tells you to change it.',
        valueKind: 'TEXT'
    };
}

export function formatTechnicalSettingValue(value: string, kind: TechnicalSettingValueKind): string {
    if (kind === 'BOOLEAN') return value === 'true' ? 'Enabled' : value === 'false' ? 'Disabled' : value;
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return value;
    if (kind === 'BYTES') {
        if (numeric >= 1024 ** 3) return `${trim(numeric / 1024 ** 3)} GB`;
        if (numeric >= 1024 ** 2) return `${trim(numeric / 1024 ** 2)} MB`;
        if (numeric >= 1024) return `${trim(numeric / 1024)} KB`;
        return `${numeric} B`;
    }
    if (kind === 'SECONDS') return `${numeric} s`;
    if (kind === 'MILLISECONDS') return numeric >= 1000 ? `${trim(numeric / 1000)} s` : `${numeric} ms`;
    if (kind === 'HOURS') return `${numeric} h`;
    if (kind === 'RATIO') return `${numeric}:1`;
    return new Intl.NumberFormat().format(numeric);
}

function trim(value: number): string { return Number.isInteger(value) ? String(value) : value.toFixed(1).replace(/\.0$/, ''); }
