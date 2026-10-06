import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MetamodelPalette } from './MetamodelPalette';

describe('neutral metamodel palette', () => {
    it('describes neutral metamodel concepts without technology-specific Ecore vocabulary', () => {
        render(<MetamodelPalette connectionKind={null} connectionSourceClassId="" onAddClass={vi.fn()} onAddEnum={vi.fn()} onAddDataType={vi.fn()} onConnectionKindChange={vi.fn()} />);
        expect(screen.getByText(/Concrete class|Clase concreta/)).toBeInTheDocument();
        expect(screen.getByText(/Directed relationship|Relación dirigida/)).toBeInTheDocument();
        expect(screen.getByText(/Owned whole-part relationship|Relación todo-parte con propiedad/)).toBeInTheDocument();
        expect(document.body.textContent).not.toMatch(/\b(?:Ecore|EClass|EEnum|EDataType|EReference)\b/i);
    });
});
