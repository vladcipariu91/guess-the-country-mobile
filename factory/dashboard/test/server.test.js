import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createApp } from '../src/app.js';
import { createDemoClient } from '../src/demo.js';
import { createFakeRunner, demoWriteTicket } from '../src/fake-runner.js';

const TOKEN = 'ghp_SECRET_TOKEN_123';

function issue(number, labels, extra = {}) {
  return { number, title: `Issue ${number}`, url: `https://x/issues/${number}`, state: 'open', labels: ['factory', ...labels], closedAt: null, ...extra };
}

function stubClient(issues, overrides = {}) {
  const calls = [];
  const rec = (name, fn) => async (...a) => { calls.push([name, ...a]); return fn ? fn(...a) : undefined; };
  const client = {
    calls,
    listFactoryIssues: rec('listFactoryIssues', async () => issues),
    getIssue: rec('getIssue', async (n) => ({ ...issues.find((i) => i.number === n), body: 'Body text' })),
    findPullForIssue: rec('findPullForIssue', async () => null),
    getComments: rec('getComments', async () => []),
    createIssue: rec('createIssue', async () => ({ number: 99, url: 'https://x/issues/99' })),
    setStage: rec('setStage'),
    setBlocked: rec('setBlocked'),
    addComment: rec('addComment'),
    ...overrides,
  };
  return client;
}

async function start({ client = stubClient([]), writeTicket, runner, now, publicDir, runsDir, demo = false } = {}) {
  const enq = [];
  runner ??= { enqueue: (n, s) => { enq.push([n, s]); return true; }, status: () => ({}) };
  writeTicket ??= async (p) => ({ title: `T:${p}`, body: `B:${p}`, fallback: false });
  const app = createApp({ client, writeTicket, runner, runsDir, demo, now, publicDir });
  const server = http.createServer(app);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const bodies = [];
  const call = async (method, path, body, raw, headers) => {
    const res = await fetch(base + path, {
      method,
      headers: headers ?? (body !== undefined || raw !== undefined ? { 'Content-Type': 'application/json' } : {}),
      body: raw ?? (body !== undefined ? JSON.stringify(body) : undefined),
    });
    const text = await res.text();
    bodies.push(text);
    let json = null;
    try { json = JSON.parse(text); } catch { /* not json */ }
    return { status: res.status, json, text, type: res.headers.get('content-type') };
  };
  return { call, client, enq, bodies, close: () => new Promise((r) => { server.closeAllConnections(); server.close(r); }) };
}

test('GET /api/board returns six columns', async () => {
  const s = await start({ client: stubClient([issue(1, ['stage:todo']), issue(2, ['stage:in-review'])]) });
  const r = await s.call('GET', '/api/board');
  assert.equal(r.status, 200);
  assert.deepEqual(r.json.columns.map((c) => c.title), ['To do', 'Planning', 'Awaiting you', 'Building', 'In review', 'Done']);
  assert.equal(r.json.error, null);
  assert.equal(r.json.demo, false);
  assert.deepEqual(s.client.calls.filter((c) => c[0] === 'findPullForIssue').map((c) => c[1]), [2]);
  await s.close();
});

test('serves the cached board with an error when GitHub fails', async () => {
  let t = 1_000_000;
  let fail = false;
  const issues = [issue(1, ['stage:todo'])];
  const client = stubClient(issues, { listFactoryIssues: async () => { if (fail) throw new Error('boom'); return issues; } });
  const s = await start({ client, now: () => t });
  assert.equal((await s.call('GET', '/api/board')).json.error, null);
  fail = true;
  t += 10_000;
  const r = await s.call('GET', '/api/board');
  assert.equal(r.status, 200);
  assert.match(r.json.error, /boom/);
  assert.equal(r.json.columns[0].cards.length, 1);
  await s.close();
});

test('502 when GitHub fails and there is no cached board', async () => {
  const client = stubClient([], { listFactoryIssues: async () => { throw new Error('down'); } });
  const s = await start({ client });
  const r = await s.call('GET', '/api/board');
  assert.equal(r.status, 502);
  assert.match(r.json.error, /down/);
  await s.close();
});

test('board is cached for 4 seconds', async () => {
  let t = 5_000_000;
  const s = await start({ client: stubClient([issue(1, ['stage:todo'])]), now: () => t });
  await s.call('GET', '/api/board');
  t += 3000;
  await s.call('GET', '/api/board');
  assert.equal(s.client.calls.filter((c) => c[0] === 'listFactoryIssues').length, 1);
  t += 2000;
  await s.call('GET', '/api/board');
  assert.equal(s.client.calls.filter((c) => c[0] === 'listFactoryIssues').length, 2);
  await s.close();
});

test('GET /api/issues/:n returns comments with kinds and PR code review', async () => {
  const client = stubClient([issue(7, ['stage:in-review'])], {
    findPullForIssue: async () => ({ number: 12, url: 'https://x/pull/12', state: 'open' }),
    getComments: async (n) => (n === 7
      ? [
        { author: 'bot', body: '<!-- factory:plan -->\nThe plan', createdAt: '2026-01-01T00:00:01Z' },
        { author: 'bot', body: '<!-- factory:plan-review -->\nLooks fine', createdAt: '2026-01-01T00:00:02Z' },
        { author: 'vlad', body: 'hello', createdAt: '2026-01-01T00:00:03Z' },
      ]
      : [{ author: 'bot', body: '<!-- factory:code-review -->\nGood code', createdAt: '2026-01-01T00:00:04Z' }]),
  });
  const s = await start({ client });
  const r = await s.call('GET', '/api/issues/7');
  assert.equal(r.status, 200);
  assert.equal(r.json.number, 7);
  assert.equal(r.json.body, 'Body text');
  assert.equal(r.json.pr.number, 12);
  assert.deepEqual(r.json.comments.map((c) => c.kind), ['plan', 'plan-review', 'other', 'code-review']);
  assert.ok(!r.json.comments[0].body.includes('factory:plan'));
  await s.close();
});

test('rejects an empty prompt', async () => {
  const s = await start();
  assert.equal((await s.call('POST', '/api/tickets', { prompt: '   ' })).status, 400);
  assert.equal((await s.call('POST', '/api/tickets', {})).status, 400);
  assert.equal(s.client.calls.filter((c) => c[0] === 'createIssue').length, 0);
  await s.close();
});

test('400 on invalid JSON and 413 on oversized body', async () => {
  const s = await start();
  assert.equal((await s.call('POST', '/api/tickets', undefined, '{nope')).status, 400);
  assert.equal((await s.call('POST', '/api/tickets', undefined, JSON.stringify({ prompt: 'x'.repeat(20000) }))).status, 413);
  await s.close();
});

test('creates a ticket with factory and stage:todo labels', async () => {
  const prompts = [];
  const s = await start({ writeTicket: async (p) => { prompts.push(p); return { title: 'Nice', body: 'Body', fallback: true }; } });
  const r = await s.call('POST', '/api/tickets', { prompt: `  ${'a'.repeat(600)}  ` });
  assert.equal(r.status, 201);
  assert.deepEqual(r.json, { number: 99, url: 'https://x/issues/99', fallback: true });
  assert.equal(prompts[0].length, 500);
  const create = s.client.calls.find((c) => c[0] === 'createIssue');
  assert.deepEqual(create[1], { title: 'Nice', body: 'Body', labels: ['factory', 'stage:todo'] });
  await s.close();
});

test('creating a ticket invalidates the board cache', async () => {
  const s = await start({ client: stubClient([issue(1, ['stage:todo'])]) });
  await s.call('GET', '/api/board');
  await s.call('POST', '/api/tickets', { prompt: 'x' });
  await s.call('GET', '/api/board');
  assert.equal(s.client.calls.filter((c) => c[0] === 'listFactoryIssues').length, 2);
  await s.close();
});

test('rejects an action that is not valid from the current column', async () => {
  const s = await start({ client: stubClient([issue(1, ['stage:todo'])]) });
  const r = await s.call('POST', '/api/issues/1/actions', { action: 'approve' });
  assert.equal(r.status, 409);
  assert.equal(s.client.calls.filter((c) => c[0] === 'setStage').length, 0);
  assert.equal(s.enq.length, 0);
  assert.equal((await s.call('POST', '/api/issues/1/actions', { action: 'bogus' })).status, 400);
  await s.close();
});

test('plan moves to planning and enqueues planning', async () => {
  const s = await start({ client: stubClient([issue(1, ['stage:todo'])]) });
  const r = await s.call('POST', '/api/issues/1/actions', { action: 'plan' });
  assert.deepEqual(r.json, { ok: true });
  assert.deepEqual(s.client.calls.find((c) => c[0] === 'setStage'), ['setStage', 1, 'stage:planning']);
  assert.deepEqual(s.enq, [[1, 'planning']]);
  await s.close();
});

test('send-back posts the note then moves to planning', async () => {
  const s = await start({ client: stubClient([issue(3, ['stage:awaiting-approval'])]) });
  const r = await s.call('POST', '/api/issues/3/actions', { action: 'send-back', note: 'Use a smaller change' });
  assert.equal(r.status, 200);
  const names = s.client.calls.map((c) => c[0]);
  assert.ok(names.indexOf('addComment') < names.indexOf('setStage'));
  assert.deepEqual(s.client.calls.find((c) => c[0] === 'addComment').slice(1), [3, 'Use a smaller change']);
  assert.deepEqual(s.client.calls.find((c) => c[0] === 'setStage'), ['setStage', 3, 'stage:planning']);
  assert.deepEqual(s.enq, [[3, 'planning']]);
  await s.close();
});

test('approve enqueues a build', async () => {
  const s = await start({ client: stubClient([issue(4, ['stage:awaiting-approval'])]) });
  const r = await s.call('POST', '/api/issues/4/actions', { action: 'approve' });
  assert.equal(r.status, 200);
  assert.deepEqual(s.client.calls.find((c) => c[0] === 'setStage'), ['setStage', 4, 'stage:building']);
  assert.deepEqual(s.enq, [[4, 'building']]);
  await s.close();
});

test('retry works on a blocked card and re-enqueues the current stage', async () => {
  const s = await start({ client: stubClient([issue(5, ['stage:building', 'factory:blocked'])]) });
  assert.equal((await s.call('POST', '/api/issues/5/actions', { action: 'retry' })).status, 200);
  assert.deepEqual(s.client.calls.find((c) => c[0] === 'setBlocked'), ['setBlocked', 5, false]);
  assert.deepEqual(s.enq, [[5, 'building']]);
  await s.close();
});

test('retry works when a planning card has no runner job', async () => {
  const s = await start({ client: stubClient([issue(6, ['stage:planning'])]) });
  assert.equal((await s.call('POST', '/api/issues/6/actions', { action: 'retry' })).status, 200);
  assert.deepEqual(s.enq, [[6, 'planning']]);
  await s.close();
});

test('retry still 409 when the job is running, or from other columns', async () => {
  const runner = { enqueue: () => true, status: () => ({ 6: { step: 'building', state: 'running', startedAt: 'x' } }) };
  const s = await start({ runner, client: stubClient([issue(6, ['stage:building']), issue(7, ['stage:todo'])]) });
  assert.equal((await s.call('POST', '/api/issues/6/actions', { action: 'retry' })).status, 409);
  assert.equal((await s.call('POST', '/api/issues/7/actions', { action: 'retry' })).status, 409);
  await s.close();
});

test('POSTs without a JSON content-type are rejected with 415', async () => {
  const s = await start({ client: stubClient([issue(1, ['stage:todo'])]) });
  const raw = JSON.stringify({ prompt: 'hi' });
  for (const headers of [{ 'Content-Type': 'text/plain' }, {}]) {
    assert.equal((await s.call('POST', '/api/tickets', undefined, raw, headers)).status, 415);
    assert.equal((await s.call('POST', '/api/issues/1/actions', undefined, JSON.stringify({ action: 'plan' }), headers)).status, 415);
  }
  assert.equal(s.client.calls.filter((c) => c[0] === 'createIssue' || c[0] === 'setStage').length, 0);
  assert.equal((await s.call('POST', '/api/tickets', undefined, raw, { 'Content-Type': 'application/json; charset=utf-8' })).status, 201);
  await s.close();
});

test('after a write invalidates the cache, a GitHub failure still serves the last good board', async () => {
  let t = 1_000_000;
  let fail = false;
  const issues = [issue(1, ['stage:todo'])];
  const client = stubClient(issues, { listFactoryIssues: async () => { if (fail) throw new Error('boom'); return issues; } });
  const s = await start({ client, now: () => t });
  assert.equal((await s.call('GET', '/api/board')).status, 200);
  assert.equal((await s.call('POST', '/api/issues/1/actions', { action: 'plan' })).status, 200);
  fail = true;
  const r = await s.call('GET', '/api/board');
  assert.equal(r.status, 200);
  assert.match(r.json.error, /boom/);
  assert.equal(r.json.columns[0].cards.length, 1);
  await s.close();
});

test('never includes the token in any response', async () => {
  const client = stubClient([issue(1, ['stage:todo'])], {
    getIssue: async () => { throw Object.assign(new Error('GitHub GET failed: 500'), { status: 500 }); },
  });
  const s = await start({ client });
  await s.call('GET', '/api/board');
  await s.call('GET', '/api/issues/1');
  await s.call('GET', '/api/issues/zzz');
  await s.call('POST', '/api/issues/1/actions', { action: 'plan' });
  await s.call('GET', '/nope');
  for (const b of s.bodies) assert.ok(!b.includes(TOKEN));
  await s.close();
});

test('serves static files from public/ and rejects traversal', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'pub-'));
  const pub = join(dir, 'public');
  mkdirSync(pub);
  writeFileSync(join(pub, 'index.html'), '<h1>hi</h1>');
  writeFileSync(join(pub, 'app.js'), 'x');
  writeFileSync(join(dir, 'secret.txt'), 'secret');
  const s = await start({ publicDir: pub });
  const root = await s.call('GET', '/');
  assert.equal(root.status, 200);
  assert.match(root.type, /text\/html/);
  assert.match((await s.call('GET', '/app.js')).type, /javascript/);
  assert.equal((await s.call('GET', '/../secret.txt')).status, 404);
  assert.equal((await s.call('GET', '/%2e%2e/secret.txt')).status, 404);
  assert.equal((await s.call('GET', '/missing.css')).status, 404);
  await s.close();
});

test('404 for static when public/ does not exist', async () => {
  const s = await start({ publicDir: join(tmpdir(), 'does-not-exist-xyz') });
  assert.equal((await s.call('GET', '/')).status, 404);
  await s.close();
});

test('demo: fake runner moves a planned card on and keeps fixture chips', async () => {
  const client = createDemoClient();
  const runner = createFakeRunner({ client, stepDelayMs: 0 });
  const s = await start({ client, runner, writeTicket: async (p) => demoWriteTicket(p), demo: true });
  const created = await s.call('POST', '/api/tickets', { prompt: 'Add a sound toggle' });
  assert.equal(created.status, 201);
  const n = created.json.number;
  assert.equal((await s.call('POST', `/api/issues/${n}/actions`, { action: 'plan' })).status, 200);
  await runner.whenIdle();
  let board = (await s.call('GET', '/api/board')).json;
  assert.equal(board.demo, true);
  const card = board.columns.find((c) => c.id === 'awaiting-approval').cards.find((c) => c.number === n);
  assert.equal(card.chip, 'Approve plan');
  assert.match(board.columns.find((c) => c.id === 'planning').cards.find((c) => c.number === 2).chip, /AI planning/);
  await s.call('POST', `/api/issues/${n}/actions`, { action: 'approve' });
  await runner.whenIdle();
  board = (await s.call('GET', '/api/board')).json;
  const done = board.columns.find((c) => c.id === 'in-review').cards.find((c) => c.number === n);
  assert.ok(done.prNumber);
  const detail = (await s.call('GET', `/api/issues/${n}`)).json;
  assert.ok(detail.comments.some((c) => c.kind === 'code-review'));
  assert.ok(detail.comments.some((c) => c.kind === 'plan'));
  await s.close();
});

test('GET /api/issues/:n/summary serves the worktree summary page, 404 when missing', async () => {
  const runsDir = mkdtempSync(join(tmpdir(), 'runs-'));
  mkdirSync(join(runsDir, 'issue-7', '.factory'), { recursive: true });
  writeFileSync(join(runsDir, 'issue-7', '.factory', 'summary.html'), '<html>sum</html>');
  const { call, close } = await start({ runsDir });
  try {
    const ok = await call('GET', '/api/issues/7/summary');
    assert.equal(ok.status, 200);
    assert.match(ok.type, /text\/html/);
    assert.equal(ok.text, '<html>sum</html>');
    assert.equal((await call('GET', '/api/issues/8/summary')).status, 404);
  } finally { await close(); }
});

test('GET /api/issues/:n/summary is 404 without a runs dir (demo)', async () => {
  const { call, close } = await start();
  try { assert.equal((await call('GET', '/api/issues/7/summary')).status, 404); } finally { await close(); }
});

test('a summary comment gets the summary kind', async () => {
  const client = stubClient([issue(4, ['stage:in-review'])], {
    getComments: async () => [{ author: 'bot', body: '<!-- factory:summary -->\n[Open the summary](http://localhost:4321/api/issues/4/summary)', createdAt: '2026-01-01' }],
  });
  const { call, close } = await start({ client });
  try {
    const { json } = await call('GET', '/api/issues/4');
    assert.equal(json.comments[0].kind, 'summary');
    assert.doesNotMatch(json.comments[0].body, /factory:summary/);
  } finally { await close(); }
});
