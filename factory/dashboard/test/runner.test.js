import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createRunner } from '../src/runner.js';

const TOKEN = 'ghp_SECRET123';

function setup({ outputs = {}, issues = {}, existingWorktree = [], pull = null, commits = '2', failOn = null, pushError = null } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'runner-'));
  const repoDir = join(root, 'repo');
  const runsDir = join(root, 'runs');
  mkdirSync(repoDir); mkdirSync(runsDir);
  writeFileSync(join(repoDir, 'local.properties'), 'sdk.dir=/sdk\n');
  for (const n of existingWorktree) mkdirSync(join(runsDir, `issue-${n}`), { recursive: true });
  const calls = { claude: [], git: [], comments: [], stages: [], blocked: [], pulls: [] };
  const verdicts = [...(outputs.verdicts ?? ['Verdict: APPROVE\nok'])];
  const client = {
    getIssue: async (n) => ({ number: n, title: `Title ${n}`, body: `Body ${n}`, ...issues[n] }),
    getComments: async () => [
      { author: 'vlad', body: 'note one' },
      { author: 'bot', body: '<!-- factory:plan -->\nOLD PLAN' },
      { author: 'bot', body: '<!-- factory:plan -->\nNEW PLAN' },
    ],
    addComment: async (n, body) => { calls.comments.push([n, body]); },
    setStage: async (n, l) => { calls.stages.push([n, l]); },
    setBlocked: async (n, b) => { calls.blocked.push([n, b]); },
    findPullForIssue: async () => pull,
    createPull: async (p) => { calls.pulls.push(p); return { number: 77, url: 'u' }; },
  };
  const runClaude = async (prompt, opts) => {
    calls.claude.push({ prompt, ...opts });
    const f = (name, text) => { mkdirSync(join(opts.cwd, '.factory'), { recursive: true }); writeFileSync(join(opts.cwd, '.factory', name), text); };
    const step = prompt.split('\n')[0];
    if (failOn && step.includes(failOn)) throw new Error(`boom ${TOKEN}`);
    if (step.includes('planner')) f('plan.md', `plan v${calls.claude.filter((c) => c.prompt.includes('planner')).length}`);
    else if (step.includes('plan reviewer')) f('plan-review.md', verdicts.shift() ?? 'Verdict: APPROVE');
    else if (step.includes('builder')) { f('pr.md', 'pr body'); }
    else if (step.includes('code reviewer')) f('code-review.md', 'review text');
    return JSON.stringify({ result: 'done' });
  };
  const git = async (args, cwd) => {
    calls.git.push({ args, cwd });
    if (args[0] === 'worktree') mkdirSync(args[args.indexOf('-B') + 2], { recursive: true });
    if (args[0] === 'rev-list') return `${commits}\n`;
    if (args.includes('push') && pushError) throw new Error(pushError);
    return '';
  };
  const runner = createRunner({ client, token: TOKEN, repo: 'o/r', repoDir, runsDir, runClaude, git, now: () => '2026-01-01T00:00:00.000Z' });
  return { runner, calls, runsDir, repoDir };
}

test('planning posts plan and review then awaits approval', async () => {
  const { runner, calls, runsDir, repoDir } = setup();
  assert.equal(runner.enqueue(5, 'planning'), true);
  assert.deepEqual(runner.status()[5], { step: 'planning', state: 'queued', startedAt: '2026-01-01T00:00:00.000Z' });
  await runner.whenIdle();
  assert.deepEqual(calls.comments, [[5, '<!-- factory:plan -->\nplan v1'], [5, '<!-- factory:plan-review -->\nVerdict: APPROVE\nok']]);
  assert.deepEqual(calls.stages, [[5, 'stage:awaiting-approval']]);
  assert.deepEqual(runner.status(), {});
  assert.equal(calls.claude.length, 2);
  assert.equal(calls.claude[0].allowedTools, 'Read,Glob,Grep,Write');
  assert.equal(calls.claude[0].maxTurns, 30);
  assert.equal(calls.claude[0].timeoutMs, 30 * 60 * 1000);
  assert.equal(calls.claude[0].cwd, join(runsDir, 'issue-5'));
  assert.match(calls.claude[0].prompt, /Title 5/);
  assert.match(calls.claude[0].prompt, /Body 5/);
  assert.match(calls.claude[0].prompt, /note one/);
  assert.equal(readFileSync(join(runsDir, 'issue-5', 'local.properties'), 'utf8'), 'sdk.dir=/sdk\n');
  assert.deepEqual(calls.git[0], { args: ['fetch', 'origin'], cwd: repoDir });
  assert.deepEqual(calls.git[1].args, ['worktree', 'prune']);
  assert.deepEqual(calls.git[2].args, ['worktree', 'add', '-B', 'factory/issue-5', join(runsDir, 'issue-5'), 'origin/main']);
  assert.equal(readFileSync(join(runsDir, 'issue-5', '.factory', '.gitignore'), 'utf8'), '*\n');
  assert.ok(!calls.claude.some((c) => /\{\{/.test(c.prompt)));
});

test('a CHANGES verdict triggers exactly one revision', async () => {
  const { runner, calls } = setup({ outputs: { verdicts: ['Verdict: CHANGES\nfix x', 'Verdict: CHANGES\nstill'] } });
  runner.enqueue(5, 'planning');
  await runner.whenIdle();
  assert.equal(calls.claude.length, 4);
  assert.match(calls.claude[2].prompt, /fix x/);
  assert.deepEqual(calls.comments.map((c) => c[1].split('\n')[0]), [
    '<!-- factory:plan -->', '<!-- factory:plan-review -->', '<!-- factory:plan -->', '<!-- factory:plan-review -->']);
  assert.deepEqual(calls.stages, [[5, 'stage:awaiting-approval']]);
  assert.deepEqual(calls.blocked, []);
});

test('an unparseable verdict counts as CHANGES once', async () => {
  const { runner, calls } = setup({ outputs: { verdicts: ['hmm', 'whatever'] } });
  runner.enqueue(5, 'planning');
  await runner.whenIdle();
  assert.equal(calls.claude.length, 4);
});

test('building pushes, opens a PR with Closes #N and posts the code review', async () => {
  const { runner, calls, runsDir } = setup();
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  const b = calls.claude[0];
  assert.match(b.prompt, /NEW PLAN/);
  assert.doesNotMatch(b.prompt.split('## Approved plan')[1], /OLD PLAN/);
  assert.match(b.prompt, /factory\/issue-5/);
  assert.equal(b.maxTurns, 80);
  assert.match(b.allowedTools, /Bash\(git commit:\*\)/);
  const push = calls.git.find((g) => g.args.includes('push'));
  assert.equal(push.cwd, join(runsDir, 'issue-5'));
  assert.deepEqual(push.args.slice(0, 2), ['-c', 'credential.helper=']);
  assert.ok(push.args.some((a) => a.startsWith('+HEAD:') && a.endsWith('factory/issue-5')));
  assert.ok(push.args.includes(`https://x-access-token:${TOKEN}@github.com/o/r.git`));
  assert.ok(push.args.some((a) => a.includes('factory/issue-5')));
  assert.deepEqual(calls.pulls, [{ head: 'factory/issue-5', title: 'Title 5', body: 'pr body\n\nCloses #5' }]);
  assert.deepEqual(calls.comments, [[77, '<!-- factory:code-review -->\nreview text']]);
  assert.deepEqual(calls.stages, [[5, 'stage:in-review']]);
  assert.equal(calls.claude[1].allowedTools, 'Read,Glob,Grep,Bash(git diff:*),Bash(git log:*)');
});

test('retrying a build reuses the existing worktree branch and PR', async () => {
  const { runner, calls } = setup({ existingWorktree: [5], pull: { number: 31, url: 'u', state: 'open' } });
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  assert.ok(!calls.git.some((g) => g.args[0] === 'worktree'));
  assert.equal(calls.pulls.length, 0);
  assert.deepEqual(calls.comments, [[31, '<!-- factory:code-review -->\nreview text']]);
});

test('a failed step marks the issue blocked and runs the next queued job', async () => {
  const { runner, calls } = setup({ failOn: 'planner' });
  runner.enqueue(5, 'planning');
  runner.enqueue(6, 'building');
  assert.deepEqual(runner.status()[6].step, 'building');
  assert.equal(runner.status()[6].state, 'queued');
  await runner.whenIdle();
  assert.ok(calls.blocked.some(([n, b]) => n === 5 && b === true));
  const fail = calls.comments.find(([n]) => n === 5);
  assert.match(fail[1], /^Factory step planning failed: boom \*\*\*$/);
  assert.ok(calls.claude.some((c) => c.prompt.includes('builder')));
  assert.deepEqual(runner.status(), {});
});

test('a build with no commits fails and does not push', async () => {
  const a = setup({ commits: '0' });
  a.runner.enqueue(5, 'building');
  await a.runner.whenIdle();
  assert.match(a.calls.comments[0][1], /^Factory step building failed: .*no commits/);
  assert.ok(!a.calls.git.some((g) => g.args.includes('push')));
});

test('enqueue refuses a ticket already queued', async () => {
  const { runner } = setup();
  assert.equal(runner.enqueue(5, 'planning'), true);
  assert.equal(runner.enqueue(5, 'planning'), false);
  assert.equal(runner.enqueue(5, 'building'), false);
  await runner.whenIdle();
  assert.equal(runner.enqueue(5, 'planning'), true);
  await runner.whenIdle();
});

test('the token never appears in comments or status when the push fails', async () => {
  const { runner, calls } = setup({ pushError: `fatal: unable to access https://x-access-token:${TOKEN}@github.com/o/r.git` });
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  const all = JSON.stringify([calls.comments, runner.status()]);
  assert.ok(!all.includes(TOKEN));
  assert.match(calls.comments[0][1], /\*\*\*/);
  assert.ok(calls.comments[0][1].length <= 'Factory step building failed: '.length + 300);
});

test('deletes a stale output file before each step', async () => {
  const { runner, calls, runsDir } = setup({ existingWorktree: [5] });
  mkdirSync(join(runsDir, 'issue-5', '.factory'));
  writeFileSync(join(runsDir, 'issue-5', '.factory', 'plan.md'), 'STALE');
  // a planner that writes nothing must fail rather than pass on the stale file
  const r2 = createRunner({
    client: { getIssue: async () => ({ title: 't', body: '' }), getComments: async () => [], addComment: async (n, b) => calls.comments.push([n, b]), setBlocked: async () => {}, setStage: async () => {} },
    token: TOKEN, repo: 'o/r', repoDir: '/nope', runsDir, runClaude: async () => '{}', git: async () => '',
  });
  r2.enqueue(5, 'planning');
  await r2.whenIdle();
  assert.match(calls.comments[0][1], /Factory step planning failed: .*plan\.md/);
  assert.ok(!existsSync(join(runsDir, 'issue-5', '.factory', 'plan.md')));
  void runner;
});

test('a closed or merged PR is not reused', async () => {
  const { runner, calls } = setup({ existingWorktree: [5], pull: { number: 31, url: 'u', state: 'closed' } });
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  assert.equal(calls.pulls.length, 1);
  assert.deepEqual(calls.comments, [[77, '<!-- factory:code-review -->\nreview text']]);
});

test('bold or heading-wrapped verdicts still parse', async () => {
  for (const v of ['**Verdict: APPROVE**', '## Verdict: APPROVE', '> _Verdict: approve_']) {
    const { runner, calls } = setup({ outputs: { verdicts: [v] } });
    runner.enqueue(5, 'planning');
    await runner.whenIdle();
    assert.equal(calls.claude.length, 2, v);
  }
});

test('child processes get an env without GITHUB_TOKEN', async () => {
  const saved = process.env.GITHUB_TOKEN;
  process.env.GITHUB_TOKEN = 'leak-me';
  try {
    const { runner, calls } = setup();
    runner.enqueue(5, 'planning');
    await runner.whenIdle();
    assert.ok(calls.claude.length > 0);
    for (const c of calls.claude) {
      assert.ok(c.env && !('GITHUB_TOKEN' in c.env));
      assert.equal(c.env.PATH, process.env.PATH);
    }
  } finally {
    if (saved === undefined) delete process.env.GITHUB_TOKEN; else process.env.GITHUB_TOKEN = saved;
  }
});
