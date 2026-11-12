import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createGitHubClient, GitHubError } from '../src/github.js';

function stub(responses) {
  const calls = [];
  const queue = [...responses];
  const fetchImpl = async (url, init = {}) => {
    calls.push({ url, method: init.method ?? 'GET', headers: init.headers, body: init.body ? JSON.parse(init.body) : undefined });
    const r = queue.shift() ?? { status: 200, body: {} };
    return {
      ok: r.status >= 200 && r.status < 300,
      status: r.status,
      json: async () => r.body,
      text: async () => JSON.stringify(r.body ?? ''),
    };
  };
  return { calls, fetchImpl };
}
const client = (responses) => {
  const s = stub(responses);
  return { ...s, gh: createGitHubClient({ token: 'tok123', repo: 'me/app', fetchImpl: s.fetchImpl }) };
};
const rawIssue = (n, extra = {}) => ({ number: n, title: `t${n}`, html_url: `https://x/${n}`, state: 'open', labels: [{ name: 'factory' }], closed_at: null, ...extra });

test('listFactoryIssues drops pull requests', async () => {
  const { gh, calls } = client([{ status: 200, body: [rawIssue(1), rawIssue(2, { pull_request: {} })] }]);
  const issues = await gh.listFactoryIssues();
  assert.deepEqual(issues, [{ number: 1, title: 't1', url: 'https://x/1', state: 'open', labels: ['factory'], closedAt: null }]);
  assert.match(calls[0].url, /\/repos\/me\/app\/issues\?labels=factory&state=all&per_page=100/);
});

test('setStage replaces the old stage label and clears blocked', async () => {
  const { gh, calls } = client([
    { status: 200, body: rawIssue(5, { labels: [{ name: 'factory' }, { name: 'stage:awaiting-approval' }, { name: 'factory:blocked' }] }) },
    { status: 200, body: {} },
  ]);
  await gh.setStage(5, 'stage:building');
  assert.equal(calls[1].method, 'PUT');
  assert.match(calls[1].url, /\/issues\/5\/labels$/);
  assert.deepEqual(calls[1].body.labels, ['factory', 'stage:building']);
});

test('setBlocked adds and removes the blocked label', async () => {
  const a = client([{ status: 200, body: rawIssue(3) }, { status: 200, body: {} }]);
  await a.gh.setBlocked(3, true);
  assert.deepEqual(a.calls[1].body.labels, ['factory', 'factory:blocked']);
  const b = client([{ status: 200, body: rawIssue(3, { labels: [{ name: 'factory' }, { name: 'factory:blocked' }] }) }, { status: 200, body: {} }]);
  await b.gh.setBlocked(3, false);
  assert.deepEqual(b.calls[1].body.labels, ['factory']);
});

test('createPull targets main', async () => {
  const { gh, calls } = client([{ status: 201, body: { number: 9, html_url: 'https://x/pull/9' } }]);
  const pr = await gh.createPull({ head: 'factory/issue-4', title: 'T', body: 'Closes #4' });
  assert.deepEqual(pr, { number: 9, url: 'https://x/pull/9' });
  assert.equal(calls[0].method, 'POST');
  assert.equal(calls[0].body.base, 'main');
  assert.equal(calls[0].body.head, 'factory/issue-4');
});

test('findPullForIssue returns the most recent PR for the branch', async () => {
  const { gh, calls } = client([{ status: 200, body: [
    { number: 1, html_url: 'u1', state: 'closed', created_at: '2026-01-01T00:00:00Z' },
    { number: 2, html_url: 'u2', state: 'open', created_at: '2026-02-01T00:00:00Z' },
  ] }]);
  assert.deepEqual(await gh.findPullForIssue(7), { number: 2, url: 'u2', state: 'open' });
  assert.match(calls[0].url, /pulls\?head=me:factory\/issue-7&state=all/);
});

test('findPullForIssue returns null when none', async () => {
  const { gh } = client([{ status: 200, body: [] }]);
  assert.equal(await gh.findPullForIssue(7), null);
});

test('getIssue, getComments, createIssue, addComment', async () => {
  const { gh, calls } = client([
    { status: 200, body: rawIssue(1, { body: 'hi' }) },
    { status: 200, body: [{ user: { login: 'bob' }, body: 'c', created_at: 'd' }] },
    { status: 201, body: { number: 8, html_url: 'u8' } },
    { status: 201, body: {} },
  ]);
  assert.equal((await gh.getIssue(1)).body, 'hi');
  assert.deepEqual(await gh.getComments(1), [{ author: 'bob', body: 'c', createdAt: 'd' }]);
  assert.deepEqual(await gh.createIssue({ title: 'a', body: 'b', labels: ['factory'] }), { number: 8, url: 'u8' });
  await gh.addComment(12, 'yo');
  assert.match(calls[3].url, /\/issues\/12\/comments$/);
  assert.equal(calls[3].body.body, 'yo');
});

test('createLabel ignores 422 but throws otherwise', async () => {
  const a = client([{ status: 422, body: { message: 'exists' } }]);
  await a.gh.createLabel('factory', 'ededed');
  const b = client([{ status: 500, body: {} }]);
  await assert.rejects(() => b.gh.createLabel('factory', 'ededed'), GitHubError);
});

test('throws GitHubError with status on 401', async () => {
  const { gh } = client([{ status: 401, body: { message: 'Bad credentials' } }]);
  await assert.rejects(() => gh.listFactoryIssues(), (e) => {
    assert.ok(e instanceof GitHubError);
    assert.equal(e.status, 401);
    assert.ok(!e.message.includes('tok123'));
    return true;
  });
});

test('sends the bearer token header', async () => {
  const { gh, calls } = client([{ status: 200, body: [] }]);
  await gh.listFactoryIssues();
  assert.equal(calls[0].headers.Authorization, 'Bearer tok123');
  assert.equal(calls[0].headers.Accept, 'application/vnd.github+json');
  assert.equal(calls[0].headers['X-GitHub-Api-Version'], '2022-11-28');
});
