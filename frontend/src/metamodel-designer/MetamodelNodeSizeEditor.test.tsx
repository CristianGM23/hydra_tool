import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { MetamodelNodeSizeEditor } from './MetamodelNodeSizeEditor';
import { PIXELS_PER_CENTIMETER } from './nodeSizeUtils';

function SizeHarness(props: { onChange: (width: number, height: number) => void }) {
    const [size, setSize] = useState({ width: 240, height: 130 });
    return <MetamodelNodeSizeEditor elementType="Class" height={size.height} minimumHeight={110} minimumWidth={190} width={size.width} onChange={(width, height) => { setSize({ width, height }); props.onChange(width, height); }} />;
}

describe('MetamodelNodeSizeEditor', () => {
    it('updates dimensions in pixels', () => {
        const onChange = vi.fn();
        render(<SizeHarness onChange={onChange} />);

        fireEvent.change(screen.getByLabelText('Width (px)'), { target: { value: '260.5' } });

        expect(onChange).toHaveBeenCalledWith(260.5, 130);
    });

    it('converts centimeter input to persisted pixels', () => {
        const onChange = vi.fn();
        render(<SizeHarness onChange={onChange} />);
        fireEvent.change(screen.getByLabelText('Size unit'), { target: { value: 'CM' } });

        fireEvent.change(screen.getByLabelText('Height (cm)'), { target: { value: '4' } });

        const lastCall = onChange.mock.calls.at(-1);
        expect(lastCall?.[0]).toBe(240);
        expect(lastCall?.[1]).toBeCloseTo(4 * PIXELS_PER_CENTIMETER, 8);
    });

    it('rejects text and values below the minimum size', () => {
        const onChange = vi.fn();
        render(<SizeHarness onChange={onChange} />);
        const input = screen.getByLabelText('Width (px)');

        fireEvent.change(input, { target: { value: 'large' } });
        fireEvent.change(input, { target: { value: '100' } });

        expect(onChange).not.toHaveBeenCalled();
        expect(screen.getByRole('alert')).toBeTruthy();
    });
});
