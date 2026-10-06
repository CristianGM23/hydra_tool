import { HydraAdaptiveSelect, type HydraAdaptiveSelectProps, type HydraSelectOption } from '../components/HydraAdaptiveSelect';

export type { HydraSelectOption };
export type HydraSelectProps = HydraAdaptiveSelectProps;

/** Selector oficial del sistema de diseño; conserva categorías adaptativas y semántica nativa accesible. */
export function HydraSelect(props: HydraSelectProps) {
    return <HydraAdaptiveSelect {...props} className={`hydra-ui-select${props.className === undefined || props.className === '' ? '' : ` ${props.className}`}`} />;
}
