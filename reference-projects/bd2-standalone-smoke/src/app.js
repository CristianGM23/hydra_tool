import { validate, rename, serialize } from './model.mjs';

// Same small editor on web and Tauri: web exports JSON, native saves in user data.
// The Tauri branch uses Rust commands, never the Studio backend.
let model = { id: 'example', name: 'Example' };
let etag = null;
let saving = false;
const nameInput = document.getElementById('name');
const status = document.getElementById('status');
const saveButton = document.getElementById('save');
const invoke = window.__TAURI__?.core?.invoke;

async function reload() {
  if (typeof invoke !== 'function') {
    status.textContent = 'La reapertura local requiere el editor de escritorio';
    return;
  }
  try {
    const result = await invoke('hydra_read_model', { id: model.id });
    if (!result || validate(result.value).length || !result.etag) {
      throw new Error('Modelo o revisión persistida no válida');
    }
    model = result.value;
    etag = result.etag;
    nameInput.value = model.name;
    status.textContent = 'Modelo reabierto desde el almacenamiento nativo';
  } catch (error) {
    status.textContent = 'No se ha podido reabrir el modelo: ' + String(error);
  }
}

saveButton.addEventListener('click', async () => {
  if (saving) return;
  const updated = rename(model, nameInput.value);
  if (validate(updated.model).length) {
    status.textContent = 'Nombre requerido';
    return;
  }
  if (typeof invoke === 'function') {
    saving = true;
    saveButton.disabled = true;
    try {
      const result = await invoke('hydra_write_model', {
        id: model.id,
        body: serialize(updated.model),
        ifMatch: etag,
        ifNoneMatch: etag === null,
      });
      if (!result || !result.etag) throw new Error('Falta revisión confirmada');
      model = result.value;
      etag = result.etag;
      status.textContent = 'Modelo guardado con revisión ' + etag.slice(0, 12);
    } catch (error) {
      // A conflict must never be overwritten using a stale ETag.
      status.textContent = 'Guardado rechazado; reabre el modelo: ' + String(error);
    } finally {
      saving = false;
      saveButton.disabled = false;
    }
    return;
  }
  model = updated.model;
  const blob = new Blob([serialize(model)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'model.json';
  anchor.click();
  URL.revokeObjectURL(url);
  status.textContent = 'Modelo exportado';
});

document.getElementById('reload').addEventListener('click', reload);
if (typeof invoke === 'function') {
  // A missing model reports an error, but never silently creates an empty file.
  reload();
} else if ('serviceWorker' in navigator) {
  window.addEventListener('load', () =>
    navigator.serviceWorker.register('./service-worker.js').catch(() => {
      status.textContent = 'Sin caché offline';
    }));
}
