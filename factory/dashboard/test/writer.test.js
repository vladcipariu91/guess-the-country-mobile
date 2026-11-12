import { test } from 'node:test';
import assert from 'node:assert/strict';
import { writeTicket, parseWriterOutput, defaultGitRefresh } from '../src/writer.js';

const wrap = (text) => JSON.stringify({ type: 'result', result: text });
const good = { title: 'Add a hint button', body: '## Goal\nx\n## Why it matters\ny\n## Acceptance criteria\n- [ ] z\n## Out of scope\nw' };
const fenced = (obj) => wrap('Here you go:\n```json\n' + JSON.stringify(obj) + '\n```\n');
const noGit = async () => {};

test('parses title and body from a fenced json block', () => {
  assert.deepEqual(parseWriterOutput(fenced(good)), good);
});

test('returns null when the result has no usable json', () => {
  assert.equal(parseWriterOutput(wrap('sorry')), null);
  assert.equal(parseWriterOutput('not json at all'), null);
  assert.equal(parseWriterOutput(fenced({ title: 'x' })), null);
});

test('falls back to the raw prompt when Claude output is unusable', async () => {
  const t = await writeTicket('add a hint button', { repoDir: '/r', runClaude: async () => wrap('sorry'), gitRefresh: noGit });
  assert.equal(t.title, 'add a hint button');
  assert.equal(t.fallback, true);
  assert.match(t.body, /add a hint button/);
  assert.match(t.body, /_Written without AI: the writer failed\._/);
});

test('falls back when runClaude rejects', async () => {
  const t = await writeTicket('add a hint button', { repoDir: '/r', runClaude: async () => { throw new Error('timed out'); }, gitRefresh: noGit });
  assert.equal(t.fallback, true);
  assert.equal(t.title, 'add a hint button');
});

test('caps the title at 60 characters', async () => {
  const long = { ...good, title: 'x'.repeat(100) };
  const t = await writeTicket('p', { repoDir: '/r', runClaude: async () => fenced(long), gitRefresh: noGit });
  assert.equal(t.title.length, 60);
  assert.equal(t.fallback, false);
  const f = await writeTicket('y'.repeat(100), { repoDir: '/r', runClaude: async () => wrap('no'), gitRefresh: noGit });
  assert.equal(f.title.length, 60);
});

test('refreshes the repo before calling Claude and passes the filled prompt', async () => {
  const order = [];
  const t = await writeTicket('add a hint button', {
    repoDir: '/r',
    gitRefresh: async (dir) => { order.push('git:' + dir); },
    runClaude: async (prompt, opts) => {
      order.push('claude');
      assert.match(prompt, /add a hint button/);
      assert.doesNotMatch(prompt, /\{\{REQUEST\}\}/);
      assert.match(prompt, /unattended/i);
      assert.equal(opts.cwd, '/r');
      return fenced(good);
    },
  });
  assert.deepEqual(order, ['git:/r', 'claude']);
  assert.equal(t.fallback, false);
});

test('a failing git refresh does not stop the writer', async () => {
  const t = await writeTicket('p', { repoDir: '/r', gitRefresh: async () => { throw new Error('offline'); }, runClaude: async () => fenced(good) });
  assert.equal(t.fallback, false);
});

function fakeExec({ detached }) {
  const calls = [];
  const exec = async (cmd, args, opts) => {
    calls.push({ cmd, args, opts });
    if (args.includes('symbolic-ref') && detached) throw Object.assign(new Error('not a symbolic ref'), { code: 1 });
    return { stdout: '' };
  };
  return { calls, exec };
}

test('skips the refresh when the repo is on a branch', async () => {
  const { calls, exec } = fakeExec({ detached: false });
  await defaultGitRefresh('/r', exec);
  assert.equal(calls.length, 1);
  assert.ok(calls[0].args.includes('symbolic-ref'));
});

test('refreshes a detached repo with a timeout and no credential prompts', async () => {
  const { calls, exec } = fakeExec({ detached: true });
  await defaultGitRefresh('/r', exec);
  assert.deepEqual(calls.map((c) => c.args.filter((a) => ['symbolic-ref', 'fetch', 'checkout'].includes(a))[0]), ['symbolic-ref', 'fetch', 'checkout']);
  for (const c of calls) {
    assert.equal(c.opts.timeout, 30000);
    assert.equal(c.opts.env.GIT_TERMINAL_PROMPT, '0');
  }
});
