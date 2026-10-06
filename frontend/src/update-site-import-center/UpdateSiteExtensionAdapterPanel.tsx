import { useMemo, useState } from 'react';
import { HydraFormField, HydraTooltip } from '../design-system';
import type { UpdateSiteExtensionAdapterDescriptor, UpdateSiteSemanticRecovery } from './types';

interface Props {
    adapters: UpdateSiteExtensionAdapterDescriptor[];
    semantic: UpdateSiteSemanticRecovery;
}

export function UpdateSiteExtensionAdapterPanel({ adapters, semantic }: Props) {
    const [filter, setFilter] = useState('');
    const normalized = filter.trim().toLowerCase();
    const visible = useMemo(() => adapters.filter(adapter => [adapter.label, adapter.id, adapter.purpose,
        adapter.neutralTarget, ...adapter.extensionPointTokens, ...adapter.elementNames]
        .some(value => value.toLowerCase().includes(normalized))), [adapters, normalized]);
    const usedAdapterIds = useMemo(() => new Set(semantic.elements.flatMap(element => element.warnings
        .map(warning => warning.match(/declarative extension adapter:\s*([^.]+)\./i)?.[1] ?? '')
        .filter(Boolean))), [semantic.elements]);

    return <section className="update-site-extension-adapters">
        <header className="update-site-extension-adapters-header">
            <div>
                <h4>Declarative Eclipse extension adapters</h4>
                <p>Server-owned rules interpret common custom extension families without executing plug-in code.</p>
            </div>
            <HydraTooltip label="About extension adapters" title="Declarative adapters">
                <p>Adapters match extension-point identifiers and XML element names, then map them to the closest neutral HYDRA capability.</p>
                <p>A derived match remains lower-confidence evidence and can still require manual review.</p>
            </HydraTooltip>
        </header>
        <HydraFormField label="Filter adapters" targetId="extension-adapter-filter"
            help="Searches adapter names, extension tokens, XML elements and neutral targets.">
            <input id="extension-adapter-filter" value={filter} onChange={event => setFilter(event.target.value)}
                placeholder="Search importer, palette, generator…" />
        </HydraFormField>
        <div className="update-site-extension-adapter-grid">
            {visible.map(adapter => <article className={usedAdapterIds.has(adapter.id) ? 'used' : ''} key={adapter.id}>
                <header>
                    <div><strong>{adapter.label}</strong><code>{adapter.id}</code></div>
                    <span>{usedAdapterIds.has(adapter.id) ? 'Used in this analysis' : adapter.enabled ? 'Available' : 'Disabled'}</span>
                </header>
                <p>{adapter.purpose}</p>
                <dl>
                    <div><dt>Neutral target</dt><dd>{adapter.neutralTarget}</dd></div>
                    <div><dt>Semantic kind</dt><dd>{adapter.semanticKind.replaceAll('_', ' ')}</dd></div>
                    <div><dt>Confidence</dt><dd>{adapter.confidence}</dd></div>
                    <div><dt>Extension tokens</dt><dd>{adapter.extensionPointTokens.join(', ') || 'Any'}</dd></div>
                    <div><dt>XML elements</dt><dd>{adapter.elementNames.join(', ') || 'Any'}</dd></div>
                </dl>
            </article>)}
            {visible.length === 0 && <p>No declarative adapters match the current filter.</p>}
        </div>
    </section>;
}
