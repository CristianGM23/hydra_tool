import { HydraButton } from '../design-system/HydraButton';
import type { ChangeEvent } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { useHydraLanguage } from '../i18n';

import type { MetamodelLayoutDirection } from './layoutTypes';

import './metamodel-layout-controls.css';

interface MetamodelLayoutToolbarProps {
    direction: MetamodelLayoutDirection;
    disabled: boolean;
    focusSelected: boolean;
    hideDependencies: boolean;
    onApply: () => void;
    onElkApply: () => void;
    onRoute: () => void;
    onDirectionChange: (direction: MetamodelLayoutDirection) => void;
    onFocusSelectedChange: (enabled: boolean) => void;
    onHideDependenciesChange: (enabled: boolean) => void;
}

/**
 * Muestra los controles desacoplados de colocación, rutas y enfoque.
 */
export function MetamodelLayoutToolbar({ direction, disabled, focusSelected, hideDependencies, onApply, onElkApply, onRoute, onDirectionChange, onFocusSelectedChange, onHideDependenciesChange }: MetamodelLayoutToolbarProps) {
    const { t } = useHydraLanguage();
    return (
        <div className="metamodel-layout-toolbar">
            <label className="metamodel-layout-direction">{t('Layout direction')}
                <HydraAdaptiveSelect
                    ariaLabel={t('Layout direction')}
                    disabled={disabled}
                    value={direction}
                    options={[
                        { value: 'LEFT_TO_RIGHT', label: t('Left to right'), description: t('Places roots and sources on the left and advances dependent elements towards the right.'), category: t('Diagram direction') },
                        { value: 'TOP_TO_BOTTOM', label: t('Top to bottom'), description: t('Places roots and containers above the elements that depend on them.'), category: t('Diagram direction') }
                    ]}
                    onChange={next => onDirectionChange(next as MetamodelLayoutDirection)}
                />
            </label>
            <HydraButton className="metamodel-auto-layout-button" disabled={disabled} type="button" onClick={onApply}>{t('Organize diagram')}</HydraButton>
            <HydraButton className="metamodel-elk-layout-button" disabled={disabled} type="button" onClick={onElkApply}>{t('ELK-style layout')}</HydraButton>
            <HydraButton className="metamodel-route-layout-button" disabled={disabled} type="button" onClick={onRoute}>{t('Route relations')}</HydraButton>
            <label className="metamodel-layout-check"><input checked={focusSelected} onChange={(event: ChangeEvent<HTMLInputElement>) => onFocusSelectedChange(event.target.checked)} type="checkbox" /> {t('Focus selected class')}</label>
            <label className="metamodel-layout-check"><input checked={hideDependencies} onChange={(event: ChangeEvent<HTMLInputElement>) => onHideDependenciesChange(event.target.checked)} type="checkbox" /> {t('Hide dependencies')}</label>
        </div>
    );
}
