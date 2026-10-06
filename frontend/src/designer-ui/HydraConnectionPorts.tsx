import type { MouseEvent } from 'react';
import { Handle } from '@xyflow/react';
import { hydraConnectionPorts } from './HydraConnectionVisual';

export function HydraConnectionPorts({ source = true, target = true, connectionMode = false, clickToConnect = false, activeSourceHandleId = '', label, onPortClick }: {
    source?: boolean; target?: boolean; connectionMode?: boolean; clickToConnect?: boolean; activeSourceHandleId?: string; label: string;
    onPortClick?: (handleId: string, role: 'SOURCE'|'TARGET') => void;
}) {
    return <>{hydraConnectionPorts().filter(port => port.type === 'source' ? source : target).map(port => <Handle
        aria-label={port.id === 'right-source' ? `Source connection point for ${label}` : port.id === 'left-target' ? `Target connection point for ${label}` : `${port.type === 'source' ? 'Source' : 'Target'} connection point ${port.id} for ${label}`}
        className={`hydra-connection-port ${port.type}-port metamodel-style-port${activeSourceHandleId === port.id ? ' active-source' : ''}`}
        id={port.id}
        isConnectable={true}
        key={port.id}
        position={port.position}
        style={portStyle(port.position, port.percent)}
        type={port.type}
        onClick={(event: MouseEvent) => { if ((!connectionMode && !clickToConnect) || onPortClick === undefined) return; event.preventDefault(); event.stopPropagation(); onPortClick(port.id, port.type === 'source' ? 'SOURCE' : 'TARGET'); }}
    />)}</>;
}

function portStyle(position: import('@xyflow/react').Position, percent: number): { left?: string; top?: string } {
    if (position === 'top' || position === 'bottom') return { left: `${percent}%` };
    return { top: `${percent}%` };
}
