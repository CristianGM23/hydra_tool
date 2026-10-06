import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { auditHydraAccessibility } from './accessibilityAudit';

describe('HYDRA accessibility gate',()=>{
    it('accepts labelled controls and named dialogs',()=>{const view=render(<div role="dialog" aria-label="Settings"><label htmlFor="x">Name</label><input id="x"/><button>Save</button></div>);expect(auditHydraAccessibility(view.container)).toEqual([]);});
    it('detects unnamed controls and focusable aria-hidden content',()=>{const view=render(<div><input/><button aria-label=""/><div aria-hidden="true"><button>Hidden</button></div></div>);expect(auditHydraAccessibility(view.container).map(x=>x.code)).toEqual(expect.arrayContaining(['CONTROL_NAME','BUTTON_NAME','HIDDEN_FOCUS']));});
});
