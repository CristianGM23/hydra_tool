import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { GrammarGeneratedCodeView } from './GrammarGeneratedCodeView';
import type { GrammarCompilationSnapshot } from './types';

vi.mock('../i18n', () => ({ useHydraLanguage: () => ({ t: (text:string) => text }) }));
vi.mock('./GrammarMonacoEditor', () => ({
    GrammarMonacoEditor: ({value, languageId, readOnly, alwaysShowVerticalScrollbar}:
        {value:string;languageId:string;readOnly:boolean;alwaysShowVerticalScrollbar:boolean}) =>
        <textarea data-testid="generated-source" aria-label={languageId} value={value}
            readOnly={readOnly} data-scrollbar={String(alwaysShowVerticalScrollbar)} onChange={()=>undefined}/>
}));

function fixture(files:Record<string,string>):GrammarCompilationSnapshot {
    return {generatedProject:{files}} as unknown as GrammarCompilationSnapshot;
}

describe('Fix186 generated source inspection',()=>{
    it('shows an empty snapshot without changing React hook order on the next compilation',()=>{
        const {rerender}=render(<GrammarGeneratedCodeView snapshot={null}/>);
        expect(screen.getByText(/Compile or generate the language project/)).toBeInTheDocument();
        rerender(<GrammarGeneratedCodeView snapshot={fixture({'src/demo.ts':'const item=42;'})}/>);
        const code=screen.getByTestId('generated-source') as HTMLTextAreaElement;
        expect(code).toHaveValue('const item=42;');
        expect(code).toHaveAttribute('readonly');
        expect(code).toHaveAttribute('data-scrollbar','true');
    });
    it('switches the actual read-only source and language when selecting another generated file',()=>{
        render(<GrammarGeneratedCodeView snapshot={fixture({'src/demo.ts':'const item=42;','src/Main.java':'class Main {}'})}/>);
        // Explicitly select Java: localeCompare can choose another default file across OS locales.
        fireEvent.click(screen.getByRole('button',{name:'src/Main.java'}));
        const editor=screen.getByTestId('generated-source');
        expect(editor).toHaveValue('class Main {}');
        expect(editor).toHaveAttribute('aria-label','java');
        fireEvent.click(screen.getByRole('button',{name:'src/demo.ts'}));
        expect(editor).toHaveValue('const item=42;');
        expect(editor).toHaveAttribute('aria-label','typescript');
        expect(screen.getByText('TypeScript')).toBeInTheDocument();
    });
    it('does not modify the provided generated snapshot when selecting a file',()=>{
        const files={'src/demo.ts':'const a=1;','src/Main.java':'class Main {}'};
        render(<GrammarGeneratedCodeView snapshot={fixture(files)}/>);
        fireEvent.click(screen.getByRole('button',{name:'src/demo.ts'}));
        expect(files).toEqual({'src/demo.ts':'const a=1;','src/Main.java':'class Main {}'});
    });
});
