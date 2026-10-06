/** Shared option catalogues for validation-rule authoring. Kept outside the editor to avoid hiding complexity in one component. */
export const CONTEXT_OPTIONS = [
    { value:'CLASS', label:'Class', description:'Checks each instance of a selected metamodel class.', category:'Model structure' },
    { value:'ATTRIBUTE', label:'Attribute', description:'Attaches the diagnostic to one attribute and supports precise incremental execution.', category:'Model structure' },
    { value:'RELATION', label:'Relation', description:'Attaches the diagnostic to one relation or connection.', category:'Model structure' },
    { value:'OBJECT', label:'Object', description:'Checks an object without tying the diagnostic to one feature.', category:'Model structure' },
    { value:'MODEL', label:'Complete model', description:'Evaluates a global constraint over the complete model.', category:'Global scopes' },
    { value:'REPRESENTATION', label:'Representation', description:'Checks one graphical or tabular representation.', category:'Editor scopes' },
    { value:'SELECTION', label:'Selection', description:'Checks the elements currently selected by the user.', category:'Editor scopes' },
    { value:'PROJECT', label:'Project', description:'Checks several artifacts or documents within the active project.', category:'Global scopes' }
] as const;

export const AUTHORITATIVE_OPTIONS = [
    { value:'CANONICAL', label:'HYDRA', description:'HYDRA owns the editable definition of the rule.', category:'HYDRA' },
    { value:'OCL', label:'OCL', description:'The authoritative rule remains in Object Constraint Language.', category:'External rule languages' },
    { value:'EVL', label:'EVL', description:'The authoritative rule remains in Epsilon Validation Language.', category:'External rule languages' },
    { value:'ATL', label:'ATL', description:'The authoritative Boolean helper remains in ATL and is adapted to the canonical HYDRA validation model when possible.', category:'External rule languages' },
    { value:'SIRIUS_AQL', label:'Sirius AQL', description:'The authoritative expression remains in a Sirius AQL definition.', category:'Eclipse modeling' },
    { value:'JAVA', label:'Java', description:'The authoritative implementation is Java code.', category:'Code-based' },
    { value:'XTEXT', label:'Xtext', description:'The authoritative rule remains in an Xtext validator.', category:'Eclipse modeling' },
    { value:'EXTERNAL', label:'Other external runtime', description:'Another external technology owns the rule.', category:'Code-based' }
] as const;

export const SEVERITY_OPTIONS = [
    { value:'ERROR', label:'Error', description:'Blocking or critical problem that normally requires correction.', category:'Problem importance' },
    { value:'WARNING', label:'Warning', description:'Important recommendation that still allows the workflow to continue.', category:'Problem importance' },
    { value:'INFO', label:'Information', description:'Non-blocking guidance or explanatory diagnostic.', category:'Problem importance' }
] as const;

export const EXECUTION_MODE_OPTIONS = [
    { value:'INCREMENTAL', label:'Incremental', description:'Runs automatically only for the changed scope and its dependencies.', category:'Automatic execution' },
    { value:'FULL_ONLY', label:'Full validation only', description:'Runs only when the complete model is validated.', category:'Automatic execution' },
    { value:'MANUAL_ONLY', label:'Manual only', description:'Runs only after an explicit user request.', category:'On demand' },
    { value:'EXTERNAL', label:'External runtime', description:'Execution is delegated to another validation technology.', category:'External execution' }
] as const;

export const CONVERSION_STATUS_OPTIONS = [
    { value:'FULL', label:'Complete', description:'The complete rule can be represented and edited directly in HYDRA.', category:'Editable in HYDRA' },
    { value:'PARTIAL', label:'Partial', description:'Only part of the imported rule can be represented; the remaining fragment is preserved externally.', category:'Partly external' },
    { value:'EXTERNAL', label:'External', description:'The authoritative implementation remains outside HYDRA.', category:'External' },
    { value:'UNSUPPORTED', label:'Unsupported', description:'HYDRA cannot currently execute or edit this imported rule.', category:'External' }
] as const;
