/** Minimal deterministic model semantics for a standalone-editor installation smoke.
 * This is NOT an EMF/Sirius/Xtext/GLSP compatibility fixture. */
export function validate(model) {
  const diagnostics = [];
  if (!model || typeof model !== 'object' || typeof model.name !== 'string' || !model.name.trim())
    diagnostics.push({ code: 'NAME_REQUIRED', field: 'name', message: 'Name is required' });
  return diagnostics;
}
export function applyQuickFix(model, diagnostic) {
  if (diagnostic.code !== 'NAME_REQUIRED') throw new Error('Unsupported quick fix');
  return { ...model, name: 'Unnamed' };
}
export function rename(model, name) {
  const result = { ...model, name };
  return { model: result, events: [{ type: 'MODEL_RENAMED', element: model.id }], score: validate(result).length === 0 ? 1 : 0 };
}
export function serialize(model) { return JSON.stringify(model); }
export function deserialize(raw) {
  const result = JSON.parse(raw);
  if (typeof result !== 'object' || result === null || Array.isArray(result) || typeof result.id !== 'string')
    throw new Error('Invalid model document');
  return result;
}
