import test from 'node:test';
import assert from 'node:assert/strict';
import { validate, applyQuickFix, rename, serialize, deserialize } from '../src/model.mjs';
test('detects invalid models and offers a verified quick fix', () => {
  const invalid = { id: 'x', name: '' };
  assert.equal(validate(invalid)[0].code, 'NAME_REQUIRED');
  assert.equal(validate(applyQuickFix(invalid, validate(invalid)[0])).length, 0);
});
test('preserves model semantics during independent execution', () => {
  const origin = { id: 'x', name: 'before' };
  const result = rename(origin, 'after');
  assert.deepEqual(result.events, [{ type: 'MODEL_RENAMED', element: 'x' }]);
  assert.equal(result.score, 1);
  assert.deepEqual(deserialize(serialize(result.model)), result.model);
  assert.equal(origin.name, 'before');
});
test('rejects malformed persisted documents', () => {
  assert.throws(() => deserialize('[]'), /Invalid model/);
  assert.throws(() => deserialize('{bad json'), SyntaxError);
});

// B3: emit real, invocation-bound semantic evidence only after the actual
// generated editor tests have executed. This reference editor does not prove
// parity for native EMF, Xtext, Sirius or GLSP adapters.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve, dirname } from 'node:path';

test('emits challenge-bound semantic evidence for the generated editor', () => {
  const challenge = process.env.HYDRA_PARITY_CHALLENGE;
  if (!challenge) return;
  assert.match(challenge, /^[0-9a-f]{48}$/);
  const root = resolve(import.meta.dirname, '..');
  const suite = JSON.parse(readFileSync(resolve(root, 'tests/hydra-parity/scenarios.json')));
  const outputs = {
    validation: source => validate(source),
    quickfix: source => applyQuickFix(source, validate(source)[0]),
    operation: source => rename(source, 'after'),
    events: source => rename(source, 'after').events,
    gamification: source => rename(source, 'after').score,
    persistence: source => deserialize(serialize(source)),
    roundtrip: source => deserialize(serialize(source)),
  };
  for (const scenario of suite.scenarios) {
    const inputBytes = readFileSync(resolve(root, scenario.input));
    const input = JSON.parse(inputBytes);
    const actual = outputs[scenario.id](input);
    const expected = JSON.parse(readFileSync(resolve(root, scenario.expected)));
    assert.deepEqual(actual, expected);
    const evidence = {
      schemaVersion: 1, scenarioId: scenario.id, challenge,
      inputSHA256: createHash('sha256').update(inputBytes).digest('hex'), actual,
    };
    const path = resolve(root, scenario.evidence);
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, JSON.stringify(evidence, null, 2) + '\n');
  }
});

// The generated reference UI genuinely calls Rust-compatible IPC operations.
// A Node fake of the IPC boundary exercises create, optimistic update, conflict
// and reopen; the Windows/Linux/macOS installed GUI is still a separate gate.
test('generated desktop UI persists and reopens a model across fresh sessions', async () => {
  const previousWindow = globalThis.window;
  const previousDocument = globalThis.document;
  const stored = new Map();
  const calls = [];
  const invoke = async (command, args) => {
    calls.push({ command, args });
    const current = stored.get(args.id);
    if (command === 'hydra_read_model') {
      if (!current) throw new Error('Model not found');
      return structuredClone(current);
    }
    if (command === 'hydra_write_model') {
      if (current && args.ifMatch !== current.etag) throw new Error('Concurrent model change');
      if (!current && !args.ifNoneMatch) throw new Error('Create requires precondition');
      const value = JSON.parse(args.body);
      const etag = createHash('sha256').update(args.body).digest('hex');
      const result = { value, etag };
      stored.set(args.id, structuredClone(result));
      return result;
    }
    throw new Error('Unexpected native command ' + command);
  };
  function mockWindow() {
    const nodes = Object.fromEntries(['name', 'save', 'reload', 'status'].map(id => [id, {
      value: id === 'name' ? 'Example' : '', textContent: '', disabled: false, listeners: {},
      addEventListener(name, callback) { this.listeners[name] = callback; },
    }]));
    globalThis.window = { __TAURI__: { core: { invoke } } };
    globalThis.document = { getElementById: id => nodes[id] };
    return nodes;
  }
  try {
    const nodes = mockWindow();
    await import('../src/app.js?tauri-session=1');
    await new Promise(resolve => setImmediate(resolve));
    nodes.name.value = 'Persisted from Tauri';
    await nodes.save.listeners.click();
    assert.equal(stored.get('example').value.name, 'Persisted from Tauri');
    assert.equal(calls.at(-1).args.ifNoneMatch, true);
    const firstEtag = stored.get('example').etag;
    nodes.name.value = 'Updated from Tauri';
    await nodes.save.listeners.click();
    assert.equal(calls.at(-1).args.ifMatch, firstEtag);
    assert.equal(stored.get('example').value.name, 'Updated from Tauri');
    const restarted = mockWindow();
    await import('../src/app.js?tauri-session=2');
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(restarted.name.value, 'Updated from Tauri');
    restarted.name.value = 'Conflict should not persist';
    stored.set('example', { value: { id: 'example', name: 'External revision' }, etag: 'remote-revision' });
    await restarted.save.listeners.click();
    assert.match(restarted.status.textContent, /Guardado rechazado/);
    assert.equal(stored.get('example').value.name, 'External revision');
  } finally {
    globalThis.window = previousWindow;
    globalThis.document = previousDocument;
  }
});
