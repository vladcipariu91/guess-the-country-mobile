import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildBoard } from '../src/board.js';

const now = new Date('2026-11-10T12:00:00Z');
const issue = (number, labels, extra = {}) => ({
  number, title: `T${number}`, url: `https://x/${number}`, state: 'open', labels: ['factory', ...labels], closedAt: null, ...extra,
});
const col = (b, id) => b.columns.find((c) => c.id === id);

test('puts each issue in its stage column', () => {
  const board = buildBoard({
    issues: [issue(1, ['stage:todo']), issue(2, ['stage:awaiting-approval']), issue(3, [], { state: 'closed', closedAt: '2026-11-09T00:00:00Z' })],
    status: {}, pulls: {}, now,
  });
  assert.deepEqual(board.columns.map((c) => c.title), ['To do', 'Planning', 'Awaiting you', 'Building', 'In review', 'Done']);
  assert.equal(col(board, 'todo').cards[0].number, 1);
  assert.equal(col(board, 'awaiting-approval').cards[0].chip, 'Approve plan');
  assert.equal(col(board, 'done').cards[0].number, 3);
  assert.equal(board.updatedAt, now.toISOString());
});

test('shows elapsed minutes for a running build', () => {
  const board = buildBoard({
    issues: [issue(4, ['stage:building'])],
    status: { 4: { step: 'building', state: 'running', startedAt: new Date(now - 6 * 60000 - 5000).toISOString() } },
    pulls: {}, now,
  });
  assert.equal(col(board, 'building').cards[0].chip, 'AI coding… 6 min');
});

test('shows queued and other running steps', () => {
  const at = new Date(now - 120000).toISOString();
  const board = buildBoard({
    issues: [issue(1, ['stage:planning']), issue(2, ['stage:planning']), issue(3, ['stage:building'])],
    status: {
      1: { step: 'planning', state: 'queued', startedAt: at },
      2: { step: 'reviewing-plan', state: 'running', startedAt: at },
      3: { step: 'reviewing-code', state: 'running', startedAt: at },
    }, pulls: {}, now,
  });
  assert.equal(col(board, 'planning').cards[0].chip, 'Queued');
  assert.equal(col(board, 'planning').cards[1].chip, 'AI reviewing plan… 2 min');
  assert.equal(col(board, 'building').cards[0].chip, 'AI reviewing code… 2 min');
});

test('shows Needs attention when a planning card has no status', () => {
  const board = buildBoard({ issues: [issue(5, ['stage:planning']), issue(6, ['stage:building'])], status: {}, pulls: {}, now });
  assert.equal(col(board, 'planning').cards[0].chip, 'Needs attention');
  assert.equal(col(board, 'building').cards[0].chip, 'Needs attention');
});

test('marks blocked cards', () => {
  const board = buildBoard({
    issues: [issue(7, ['stage:building', 'factory:blocked'])],
    status: { 7: { step: 'building', state: 'running', startedAt: now.toISOString() } }, pulls: {}, now,
  });
  const card = col(board, 'building').cards[0];
  assert.equal(card.blocked, true);
  assert.equal(card.chip, 'Needs attention');
});

test('in review shows the PR chip and number', () => {
  const board = buildBoard({
    issues: [issue(8, ['stage:in-review'])], status: {},
    pulls: { 8: { number: 21, url: 'https://x/pull/21', state: 'open' } }, now,
  });
  const card = col(board, 'in-review').cards[0];
  assert.equal(card.chip, 'PR #21 ready');
  assert.equal(card.prNumber, 21);
  assert.equal(col(board, 'todo').cards.length, 0);
});

test('limits Done to the 10 most recent', () => {
  const issues = Array.from({ length: 12 }, (_, i) =>
    issue(100 + i, [], { state: 'closed', closedAt: new Date(Date.UTC(2026, 10, 1 + i)).toISOString() }));
  const done = col(buildBoard({ issues, status: {}, pulls: {}, now }), 'done').cards;
  assert.equal(done.length, 10);
  assert.equal(done[0].number, 111);
  assert.equal(done[9].number, 102);
});

test('skips issues without a stage', () => {
  const board = buildBoard({ issues: [issue(9, [])], status: {}, pulls: {}, now });
  assert.equal(board.columns.flatMap((c) => c.cards).length, 0);
});

test('runner status only shows on planning and building cards', () => {
  const st = { step: 'building', state: 'running', startedAt: now.toISOString() };
  const board = buildBoard({
    issues: [issue(1, ['stage:todo']), issue(2, ['stage:awaiting-approval']), issue(3, ['stage:in-review'])],
    status: { 1: st, 2: st, 3: st }, pulls: {}, now,
  });
  assert.equal(col(board, 'todo').cards[0].chip, null);
  assert.equal(col(board, 'awaiting-approval').cards[0].chip, 'Approve plan');
  assert.equal(col(board, 'in-review').cards[0].chip, null);
});

test('a planning or building card with no job and not blocked is stale', () => {
  const st = { step: 'planning', state: 'running', startedAt: now.toISOString() };
  const board = buildBoard({
    issues: [issue(1, ['stage:planning']), issue(2, ['stage:building']), issue(3, ['stage:building', 'factory:blocked']), issue(4, ['stage:planning']), issue(5, ['stage:todo'])],
    status: { 4: st }, pulls: {}, now,
  });
  const card = (id, n) => col(board, id).cards.find((c) => c.number === n);
  assert.equal(card('planning', 1).stale, true);
  assert.equal(card('planning', 1).chip, 'Needs attention');
  assert.equal(card('building', 2).stale, true);
  assert.equal(card('building', 3).stale, false);
  assert.equal(card('planning', 4).stale, false);
  assert.equal(card('todo', 5).stale, false);
});
