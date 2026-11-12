import { test } from 'node:test';
import assert from 'node:assert/strict';
import { STAGES, ALL_LABELS, ACTIONS, columnFor } from '../src/stages.js';

test('columnFor returns done for closed issues', () => {
  assert.equal(columnFor({ state: 'closed', labels: ['factory', 'stage:in-review'] }), 'done');
});

test('columnFor uses the stage label and ignores blocked', () => {
  assert.equal(columnFor({ state: 'open', labels: ['factory', 'stage:building', 'factory:blocked'] }), 'building');
});

test('columnFor returns null without a stage label', () => {
  assert.equal(columnFor({ state: 'open', labels: ['factory'] }), null);
});

test('STAGES titles are in column order', () => {
  assert.deepEqual(STAGES.map((s) => s.title), ['To do', 'Planning', 'Awaiting you', 'Building', 'In review', 'Done']);
  assert.equal(STAGES.at(-1).label, null);
});

test('ALL_LABELS lists the seven labels', () => {
  assert.equal(ALL_LABELS.length, 7);
  assert.ok(ALL_LABELS.includes('factory:blocked'));
});

test('ACTIONS describe transitions', () => {
  assert.deepEqual(ACTIONS.plan, { from: 'todo', to: 'planning' });
  assert.deepEqual(ACTIONS.approve, { from: 'awaiting-approval', to: 'building' });
  assert.deepEqual(ACTIONS['send-back'], { from: 'awaiting-approval', to: 'planning' });
  assert.deepEqual(ACTIONS.retry, { from: ['planning', 'building'], requiresBlocked: true });
});
