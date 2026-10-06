import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { GrammarMetamodelField } from './GrammarMetamodelField';

describe('GrammarMetamodelField',()=>{
    it('offers existing metamodels plus file and directory import actions',()=>{
        render(<GrammarMetamodelField value="" authoringMode="GRAMMAR_FIRST" metamodels={[{id:'people',name:'People',schemaVersion:'2.1',etag:'e'} as never]} onChange={vi.fn()} onImported={vi.fn()}/>);
        const select=screen.getByLabelText('Optional metamodel');
        expect(select).toHaveTextContent('People');
        expect(screen.getByRole('button',{name:'Import metamodel file'})).toBeInTheDocument();
        expect(screen.getByRole('button',{name:'Import metamodel folder'})).toBeInTheDocument();
    });
});
