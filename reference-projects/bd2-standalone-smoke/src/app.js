import { validate, rename, serialize } from './model.mjs';
let model = { id: 'example', name: 'Example' };
const nameInput = document.getElementById('name');
const status = document.getElementById('status');
document.getElementById('save').addEventListener('click', () => {
  const updated = rename(model, nameInput.value);
  if (validate(updated.model).length) { status.textContent = 'Nombre requerido'; return; }
  model = updated.model;
  const blob = new Blob([serialize(model)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'model.json'; anchor.click();
  URL.revokeObjectURL(url); status.textContent = 'Modelo exportado';
});
if ('serviceWorker' in navigator) window.addEventListener('load', () =>
  navigator.serviceWorker.register('./service-worker.js').catch(() => { status.textContent = 'Sin caché offline'; }));
