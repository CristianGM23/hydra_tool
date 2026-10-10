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
