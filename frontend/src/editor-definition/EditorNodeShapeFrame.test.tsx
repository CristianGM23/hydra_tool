import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EditorNodeShapeFrame } from './EditorNodeShapeFrame';
import { DEFAULT_NODE_NOTATION_STYLE } from './editorNotationStyle';
import type { NodeMapping } from './types';

const REQUIRED_SHAPES: NodeMapping['shape'][] = ['RECTANGLE', 'ELLIPSE', 'CYLINDER', 'DOCUMENT', 'CLOUD', 'STAR', 'CROSS'];

describe('EditorNodeShapeFrame', () => {
    it.each(REQUIRED_SHAPES)('renders %s with the same neutral shape frame used by runtime', (shape: NodeMapping['shape']) => {
        const { container } = render(<EditorNodeShapeFrame shape={shape} notation={DEFAULT_NODE_NOTATION_STYLE}/>);
        const svg = container.querySelector(`svg[data-shape="${shape}"]`);
        expect(svg).toBeTruthy();
        expect(svg?.querySelector('rect,ellipse,path,polygon')).toBeTruthy();
    });
});
