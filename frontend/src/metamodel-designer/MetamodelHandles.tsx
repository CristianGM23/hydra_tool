import type { MouseEvent } from 'react';
import { Handle, Position } from '@xyflow/react';

import type { MetamodelHandleRole } from './types';
import type { MetamodelNodePort } from './routingTypes';

interface MetamodelHandlesProps {
    isConnectable: boolean;
    relationshipMode: boolean;
    activeSourceHandleId: string;
    ports: MetamodelNodePort[];
    onHandleClick?: (handleId: string, role: MetamodelHandleRole) => void;
}

const DEFAULT_PORTS: MetamodelNodePort[] = [
    { id: 'top-source', side: 'top', percent: 25, role: 'SOURCE' },
    { id: 'top-target', side: 'top', percent: 75, role: 'TARGET' },
    { id: 'right-source', side: 'right', percent: 25, role: 'SOURCE' },
    { id: 'right-target', side: 'right', percent: 75, role: 'TARGET' },
    { id: 'bottom-target', side: 'bottom', percent: 25, role: 'TARGET' },
    { id: 'bottom-source', side: 'bottom', percent: 75, role: 'SOURCE' },
    { id: 'left-target', side: 'left', percent: 25, role: 'TARGET' },
    { id: 'left-source', side: 'left', percent: 75, role: 'SOURCE' }
];

/**
 * Dibuja los puertos manuales y los puertos dinámicos usados por el router.
 */
export function MetamodelHandles({ isConnectable, relationshipMode, activeSourceHandleId, ports, onHandleClick }: MetamodelHandlesProps) {
    const visiblePorts = mergePorts(DEFAULT_PORTS, ports);

    return (
        <>
            {visiblePorts.map(port => (
                <Handle className={readHandleClass(port.id, port.role, activeSourceHandleId, DEFAULT_PORTS.some(item => item.id === port.id))} id={port.id} isConnectable={isConnectable} key={port.id} position={position(port.side)} style={portStyle(port)} type={port.role === 'SOURCE' ? 'source' : 'target'} onClick={event => selectHandle(event, port.id, port.role, relationshipMode, onHandleClick)} />
            ))}
        </>
    );
}

/**
 * Une puertos sin duplicados.
 */
function mergePorts(defaultPorts: MetamodelNodePort[], routedPorts: MetamodelNodePort[]): MetamodelNodePort[] {
    const result = new Map<string, MetamodelNodePort>();

    for (const port of defaultPorts) {
        result.set(port.id, port);
    }

    for (const port of routedPorts) {
        result.set(port.id, port);
    }

    return [...result.values()];
}

/**
 * Convierte el lado neutral a la posición de React Flow.
 */
function position(side: MetamodelNodePort['side']): Position {
    if (side === 'top') {
        return Position.Top;
    }

    if (side === 'right') {
        return Position.Right;
    }

    if (side === 'bottom') {
        return Position.Bottom;
    }

    return Position.Left;
}

/**
 * Coloca el puerto en el porcentaje asignado.
 */
function portStyle(port: MetamodelNodePort): { left?: string; top?: string } {
    const percent = `${Math.max(2, Math.min(98, port.percent))}%`;

    if (port.side === 'top' || port.side === 'bottom') {
        return { left: percent };
    }

    return { top: percent };
}

/**
 * Procesa la selección directa de un puerto.
 */
function selectHandle(event: MouseEvent, handleId: string, role: MetamodelHandleRole, relationshipMode: boolean, onHandleClick: ((handleId: string, role: MetamodelHandleRole) => void) | undefined): void {
    if (!relationshipMode || onHandleClick === undefined) {
        return;
    }

    event.preventDefault();
    event.stopPropagation();
    onHandleClick(handleId, role);
}

/**
 * Devuelve las clases visuales del puerto.
 */
function readHandleClass(handleId: string, role: MetamodelHandleRole, activeSourceHandleId: string, defaultPort: boolean): string {
    let className = 'metamodel-handle';

    if (role === 'SOURCE') {
        className += ' metamodel-handle-source';
    } else {
        className += ' metamodel-handle-target';
    }

    if (!defaultPort) {
        className += ' metamodel-handle-routed';
    }

    if (handleId === activeSourceHandleId) {
        className += ' active-source';
    }

    return className;
}
