export interface AccessibilityIssue { code: string; message: string; element: Element; }

/** Lightweight dependency-free DOM gate used in unit tests and development diagnostics. */
export function auditHydraAccessibility(root: ParentNode): AccessibilityIssue[] {
    const issues: AccessibilityIssue[]=[];
    for(const input of Array.from(root.querySelectorAll('input:not([type="hidden"]), select, textarea'))) {
        const id=input.getAttribute('id');
        const labelled=id!==null&&root.querySelector(`label[for="${cssEscape(id)}"]`)!==null;
        const named=labelled||hasText(input.getAttribute('aria-label'))||hasText(input.getAttribute('aria-labelledby'));
        if(!named) issues.push({code:'CONTROL_NAME',message:'Form control requires a visible label or accessible name.',element:input});
    }
    for(const button of Array.from(root.querySelectorAll('button,[role="button"]'))) {
        const named=hasText(button.getAttribute('aria-label'))||hasText(button.getAttribute('aria-labelledby'))||hasText(button.textContent);
        if(!named) issues.push({code:'BUTTON_NAME',message:'Interactive control requires an accessible name.',element:button});
    }
    for(const dialog of Array.from(root.querySelectorAll('[role="dialog"]'))) {
        if(!hasText(dialog.getAttribute('aria-label'))&&!hasText(dialog.getAttribute('aria-labelledby'))) issues.push({code:'DIALOG_NAME',message:'Dialog requires an accessible name.',element:dialog});
    }
    for(const hidden of Array.from(root.querySelectorAll('[aria-hidden="true"]'))) {
        if(hidden.matches('button,input,select,textarea,a[href],[tabindex]:not([tabindex="-1"])')||hidden.querySelector('button,input,select,textarea,a[href],[tabindex]:not([tabindex="-1"])')) issues.push({code:'HIDDEN_FOCUS',message:'aria-hidden content must not contain focusable controls.',element:hidden});
    }
    return issues;
}
function hasText(value:string|null|undefined):boolean{return value!==null&&value!==undefined&&value.trim()!=='';}
function cssEscape(value:string):string{return value.replace(/["\\]/g,'\\$&');}
