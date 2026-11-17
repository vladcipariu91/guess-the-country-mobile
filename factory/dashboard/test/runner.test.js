import { test, mock } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createRunner } from '../src/runner.js';

const TOKEN = 'ghp_SECRET123';

function setup({ outputs = {}, issues = {}, existingWorktree = [], pull = null, commits = '2', failOn = null, pushError = null, branchAfterSummary = null, spy = null, now = () => '2026-01-01T00:00:00.000Z' } = {}) {
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
    spy?.();
    const f = (name, text) => { mkdirSync(join(opts.cwd, '.factory'), { recursive: true }); writeFileSync(join(opts.cwd, '.factory', name), text); };
    const step = prompt.split('\n')[0];
    if (failOn && step.includes(failOn)) throw new Error(`boom ${TOKEN}`);
    if (step.includes('planner')) f('plan.md', `plan v${calls.claude.filter((c) => c.prompt.includes('planner')).length}`);
    else if (step.includes('plan reviewer')) f('plan-review.md', verdicts.shift() ?? 'Verdict: APPROVE');
    else if (step.includes('summary writer')) f('summary.html', '<html>summary</html>');
    else if (step.includes('builder')) { f('pr.md', 'pr body'); }
    else if (step.includes('code reviewer')) f('code-review.md', 'review text');
    return JSON.stringify({ result: 'done' });
  };
  const git = async (args, cwd) => {
    calls.git.push({ args, cwd });
    if (args[0] === 'worktree' && args[1] === 'add') mkdirSync(args[args.indexOf('-B') + 2], { recursive: true });
    if (args[0] === 'rev-list') return `${commits}\n`;
    if (args[0] === 'rev-parse') return args.includes('--abbrev-ref') ? `${branchAfterSummary ?? `factory/issue-${cwd.split('issue-').pop()}`}\n` : 'abc123\n';
    if (args.includes('push') && pushError) throw new Error(pushError);
    return '';
  };
  const ctx = {};
  const runner = createRunner({ client, token: TOKEN, repo: 'o/r', repoDir, runsDir, runClaude, git, now });
  ctx.runner = runner;
  return { ctx, runner, calls, runsDir, repoDir };
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
  assert.match(b.allowedTools, /(^|,)Agent(,|$)/);
  assert.match(b.allowedTools, /(^|,)Task(,|$)/);
  const push = calls.git.find((g) => g.args.includes('push'));
  assert.equal(push.cwd, join(runsDir, 'issue-5'));
  assert.deepEqual(push.args.slice(0, 2), ['-c', 'credential.helper=']);
  assert.ok(push.args.some((a) => a.startsWith('+HEAD:') && a.endsWith('factory/issue-5')));
  assert.ok(push.args.includes(`https://x-access-token:${TOKEN}@github.com/o/r.git`));
  assert.ok(push.args.some((a) => a.includes('factory/issue-5')));
  assert.deepEqual(calls.pulls, [{ head: 'factory/issue-5', title: 'Title 5', body: 'pr body\n\nCloses #5' }]);
  assert.deepEqual(calls.comments, [[77, '<!-- factory:summary -->\n[Open the summary](http://localhost:4321/api/issues/5/summary): screenshots, what changed, checks. Served by the factory dashboard on this Mac.'], [77, '<!-- factory:code-review -->\nreview text']]);
  assert.deepEqual(calls.stages, [[5, 'stage:in-review']]);
  assert.match(calls.claude[1].prompt, /summary writer/);
  assert.match(calls.claude[1].prompt, /factory\/issue-5/);
  assert.match(calls.claude[1].allowedTools, /run-app\/scripts\/run\.sh/);
  assert.equal(calls.claude[1].maxTurns, 60);
  const summary = calls.git.findIndex((g) => g.args[0] === 'rev-parse');
  assert.ok(summary >= 0 && summary < calls.git.findIndex((g) => g.args.includes('push')));
  assert.equal(calls.claude[2].allowedTools, 'Read,Glob,Grep,Write,Bash(git diff:*),Bash(git log:*)');
});

test('retrying after a finished build skips the builder and only reviews', async () => {
  const { runner, calls, runsDir } = setup({ existingWorktree: [5], pull: { number: 31, url: 'u', state: 'open' } });
  mkdirSync(join(runsDir, 'issue-5', '.factory'));
  writeFileSync(join(runsDir, 'issue-5', '.factory', 'pr.md'), 'earlier pr body');
  writeFileSync(join(runsDir, 'issue-5', '.factory', 'summary.html'), 'earlier summary');
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  assert.equal(calls.claude.length, 1);
  assert.match(calls.claude[0].prompt, /code reviewer/);
  assert.ok(calls.git.some((g) => g.args.includes('push')));
  assert.deepEqual(calls.comments, [[31, '<!-- factory:summary -->\n[Open the summary](http://localhost:4321/api/issues/5/summary): screenshots, what changed, checks. Served by the factory dashboard on this Mac.'], [31, '<!-- factory:code-review -->\nreview text']]);
  assert.deepEqual(calls.stages, [[5, 'stage:in-review']]);
});

test('a leftover pr.md with no commits still runs the builder', async () => {
  const { runner, calls, runsDir } = setup({ existingWorktree: [5], commits: '0' });
  mkdirSync(join(runsDir, 'issue-5', '.factory'));
  writeFileSync(join(runsDir, 'issue-5', '.factory', 'pr.md'), 'stale');
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  assert.match(calls.claude[0].prompt, /builder/);
  assert.match(calls.comments[0][1], /builder made no commits/);
});

test('retrying a build reuses the existing worktree branch and PR', async () => {
  const { runner, calls } = setup({ existingWorktree: [5], pull: { number: 31, url: 'u', state: 'open' } });
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  assert.ok(!calls.git.some((g) => g.args[0] === 'worktree'));
  assert.equal(calls.pulls.length, 0);
  assert.deepEqual(calls.comments, [[31, '<!-- factory:summary -->\n[Open the summary](http://localhost:4321/api/issues/5/summary): screenshots, what changed, checks. Served by the factory dashboard on this Mac.'], [31, '<!-- factory:code-review -->\nreview text']]);
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
  assert.deepEqual(calls.comments, [[77, '<!-- factory:summary -->\n[Open the summary](http://localhost:4321/api/issues/5/summary): screenshots, what changed, checks. Served by the factory dashboard on this Mac.'], [77, '<!-- factory:code-review -->\nreview text']]);
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

test('default git passes timeouts: 120s generally, 300s for push', async () => {
  const root = mkdtempSync(join(tmpdir(), 'runner-git-'));
  const runsDir = join(root, 'runs'); mkdirSync(runsDir);
  const seen = [];
  const exec = async (cmd, args, opts) => { seen.push({ args, opts }); return { stdout: args[0] === 'rev-list' ? '1\n' : '' }; };
  const calls = [];
  const client = {
    getIssue: async (n) => ({ number: n, title: 't', body: '' }), getComments: async () => [],
    addComment: async (n, b) => { calls.push(b); }, setStage: async () => {}, setBlocked: async () => {},
    findPullForIssue: async () => null, createPull: async () => ({ number: 7 }),
  };
  const runClaude = async (prompt, opts) => {
    mkdirSync(join(opts.cwd, '.factory'), { recursive: true });
    const first = prompt.split('\n')[0];
    const out = first.includes('summary writer') ? 'summary.html' : first.includes('builder') ? 'pr.md' : 'code-review.md';
    writeFileSync(join(opts.cwd, '.factory', out), 'x');
    return '{}';
  };
  const runner = createRunner({ client, token: TOKEN, repo: 'o/r', repoDir: root, runsDir, runClaude, exec });
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  const fetch = seen.find((s) => s.args[0] === 'fetch');
  const push = seen.find((s) => s.args.includes('push'));
  assert.equal(fetch.opts.timeout, 120000);
  assert.equal(push.opts.timeout, 300000);
  assert.equal(push.opts.env.GIT_TERMINAL_PROMPT, '0');
});

test('a failed step is logged server-side, redacted', async () => {
  const log = mock.method(console, 'error', () => {});
  try {
    const { runner } = setup({ failOn: 'planner' });
    runner.enqueue(5, 'planning');
    await runner.whenIdle();
    const text = log.mock.calls.map((c) => c.arguments.join(' ')).join('\n');
    assert.match(text, /\[factory\] #5 planning failed: .*boom \*\*\*/s);
    assert.ok(!text.includes(TOKEN));
  } finally {
    log.mock.restore();
  }
});

test('the job start time is reset when each step starts', async () => {
  let tick = 0;
  const seen = [];
  let ctx;
  const s = setup({ now: () => `t${tick++}`, spy: () => seen.push(ctx.runner.status()[5].startedAt) });
  ctx = s.ctx;
  s.runner.enqueue(5, 'planning');
  await s.runner.whenIdle();
  assert.equal(seen.length, 2);
  assert.notEqual(seen[0], seen[1]);
});

test('retrying a finished build with no summary runs the summary, not the builder', async () => {
  const { runner, calls, runsDir } = setup({ existingWorktree: [5], pull: { number: 31, url: 'u', state: 'open' } });
  mkdirSync(join(runsDir, 'issue-5', '.factory'));
  writeFileSync(join(runsDir, 'issue-5', '.factory', 'pr.md'), 'earlier pr body');
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  assert.deepEqual(calls.claude.map((c) => c.prompt.split('\n')[0].match(/(builder|summary writer|code reviewer)/)[0]), ['summary writer', 'code reviewer']);
  assert.deepEqual(calls.stages, [[5, 'stage:in-review']]);
});

test('the summary step is put back on the branch if it leaves it detached', async () => {
  const { runner, calls } = setup({ branchAfterSummary: 'HEAD' });
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  assert.ok(calls.git.some((g) => g.args[0] === 'checkout' && g.args[1] === 'factory/issue-5'));
  assert.deepEqual(calls.stages, [[5, 'stage:in-review']]);
});

test('a failed summary blocks the ticket and does not push', async () => {
  const { runner, calls } = setup({ failOn: 'summary writer' });
  runner.enqueue(5, 'building');
  await runner.whenIdle();
  assert.ok(!calls.git.some((g) => g.args.includes('push')));
  assert.deepEqual(calls.blocked, [[5, true]]);
  assert.match(calls.comments[0][1], /Factory step summarizing failed/);
});
