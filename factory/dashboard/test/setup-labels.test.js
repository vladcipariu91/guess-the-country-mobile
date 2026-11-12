import { test } from 'node:test';
import assert from 'node:assert/strict';
import { labelColor } from '../src/setup-labels.js';
import { ALL_LABELS } from '../src/stages.js';

test('labelColor maps stages purple, factory grey, blocked red', () => {
  assert.equal(labelColor('factory'), '8a8a8a');
  assert.equal(labelColor('factory:blocked'), 'd73a4a');
  for (const n of ALL_LABELS.filter((l) => l.startsWith('stage:'))) assert.equal(labelColor(n), '6b5bff');
});
