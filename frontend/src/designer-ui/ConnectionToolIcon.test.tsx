import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ConnectionToolIcon } from './ConnectionToolIcon';

describe('shared visual connection tool icon', () => {
    it('shows the same source-arrow-target metaphor in every designer palette', () => {
        render(<ConnectionToolIcon />);
        const icon = screen.getByLabelText('Connection arrow tool');
        expect(icon.querySelector('.source')).not.toBeNull();
        expect(icon.querySelector('.target')).not.toBeNull();
        expect(icon.querySelector('polyline')).not.toBeNull();
    });
});
