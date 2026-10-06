import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DesignerStructureTree, type DesignerStructureNode } from './DesignerStructureTree';

interface Selection { kind: 'ITEM'; id: string }

function nodes(): DesignerStructureNode<Selection>[] {
    return [{
        id: 'root',
        label: 'Root',
        children: [{
            id: 'group',
            label: 'Generated elements',
            children: [{ id: 'item', label: 'Generated item', data: { kind: 'ITEM', id: 'item' } }]
        }]
    }];
}

describe('reusable designer structure tree', () => {
    it('merges default expanded groups added after the first render', () => {
        const rendered = render(<DesignerStructureTree ariaLabel="Test structure" nodes={nodes()} defaultExpandedIds={['root']} />);
        expect(screen.queryByText('Generated item')).not.toBeInTheDocument();

        rendered.rerender(<DesignerStructureTree ariaLabel="Test structure" nodes={nodes()} defaultExpandedIds={['root', 'group']} />);

        expect(screen.getByText('Generated item')).toBeInTheDocument();
    });

    it('keeps leaf selection independent from branch expansion', () => {
        const onSelect = vi.fn();
        render(<DesignerStructureTree ariaLabel="Test structure" nodes={nodes()} defaultExpandedIds={['root', 'group']} onSelect={onSelect} />);

        fireEvent.click(screen.getByText('Generated item').closest('button')!);

        expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 'item', data: { kind: 'ITEM', id: 'item' } }));
    });

    it('opens the ancestor branch of a selected generated leaf', () => {
        render(<DesignerStructureTree ariaLabel="Test structure" nodes={nodes()} selectedId="item" defaultExpandedIds={['root']} />);

        expect(screen.getByText('Generated item')).toBeInTheDocument();
    });

    it('keeps descendants visible when their owner matches the filter', () => {
        render(<DesignerStructureTree ariaLabel="Filtered owner tree" nodes={[{ id: 'operation', label: 'publish', children: [{ id: 'parameter', label: 'channel' }] }]} filter="publish" />);
        expect(screen.getByText('publish')).toBeInTheDocument();
        expect(screen.getByText('channel')).toBeInTheDocument();
    });

});
