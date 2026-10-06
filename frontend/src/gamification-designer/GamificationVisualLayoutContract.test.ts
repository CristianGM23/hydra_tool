import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const visualEditor = readFileSync(resolve(process.cwd(), 'src/gamification-designer/GamificationVisualEditor.tsx'), 'utf8');
const gamificationCss = readFileSync(resolve(process.cwd(), 'src/gamification-designer/gamification-designer.css'), 'utf8');
const edgeCss = readFileSync(resolve(process.cwd(), 'src/designer-ui/hydra-directed-edge.css'), 'utf8');
const edgeSource = readFileSync(resolve(process.cwd(), 'src/designer-ui/HydraDirectedEdge.tsx'), 'utf8');
const edgeTypesSource = readFileSync(resolve(process.cwd(), 'src/designer-ui/hydraDirectedEdgeTypes.ts'), 'utf8');
const terminatorSource = readFileSync(resolve(process.cwd(), 'src/designer-ui/HydraConnectionTerminatorShape.tsx'), 'utf8');

describe('gamification visual layout contract', () => {
    it('registers the physical HYDRA edge and deterministic edge defaults', () => {
        expect(visualEditor).toContain('edgeTypes={hydraDirectedEdgeTypes}');
        expect(visualEditor).toContain("defaultEdgeOptions={{ type: 'hydraDirectedEdge', zIndex: 8, interactionWidth: 30 }}");
        expect(edgeSource).toContain('data-hydra-directed-edge-path');
        expect(edgeSource).toContain('edgeId={props.id}');
        expect(edgeSource).toContain('HydraConnectionTerminatorShape');
        expect(terminatorSource).toContain('<polygon');
        expect(terminatorSource).toContain("'data-hydra-directed-edge-arrow': edgeId");
        expect(edgeTypesSource).toContain('hydraDirectedEdge: HydraDirectedEdge');
        expect(edgeCss).toContain('.hydra-directed-edge-visible-path');
    });

    it('makes the palette and properties columns independently resizable', () => {
        expect(visualEditor).toContain("cssVariableName: '--gamification-palette-width'");
        expect(visualEditor).toContain("cssVariableName: '--gamification-inspector-width'");
        expect(visualEditor).toContain("resizeDirection: 'INCREASE_LEFT'");
        expect(gamificationCss).toContain('var(--gamification-palette-width, 250px)');
        expect(gamificationCss).toContain('var(--gamification-inspector-width, 320px)');
        expect(gamificationCss).toContain('.gamification-column-resizer');
    });

    it('keeps palette cards and visual connections compact at enlarged global fonts', () => {
        expect(gamificationCss).toContain('.gamification-visual-palette .gamification-palette-item');
        expect(gamificationCss).toContain('font-size: clamp(11px');
        expect(gamificationCss).toContain('.gamification-visual-inspector .gamification-connection-summary');
        expect(gamificationCss).toContain('border-radius: 6px');
        expect(gamificationCss).toContain('@container gamification-inspector (max-width: 300px)');
    });

    it('hides the column separators on narrow screens', () => {
        expect(gamificationCss).toContain('@media(max-width:900px)');
        expect(gamificationCss.replace(/\s+/g, '')).toContain('.gamification-column-resizer{display:none;}');
    });
});
