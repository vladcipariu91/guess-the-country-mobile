import { readFile, writeFile, mkdir, rm, copyFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { join } from 'node:path';
import { runClaude as defaultRunClaude } from './claude.js';

const execFileP = promisify(execFile);
const PROMPTS = new URL('../../prompts/', import.meta.url);
const TIMEOUT = 30 * 60 * 1000;
const READ = 'Read,Glob,Grep,Write';
const BUILD = 'Read,Edit,Write,Glob,Grep,Agent,Task,Bash(./gradlew:*),Bash(git status:*),Bash(git diff:*),Bash(git add:*),Bash(git commit:*),Bash(git log:*)';
const REVIEW = 'Read,Glob,Grep,Bash(git diff:*),Bash(git log:*)';
const PLAN_MARK = '<!-- factory:plan -->';

const cleanEnv = () => {
  const env = { ...process.env };
  delete env.GITHUB_TOKEN;
  return env;
};

export function createRunner({ client, token, repo, repoDir, runsDir, runClaude = defaultRunClaude, git, exec = execFileP, now = () => new Date().toISOString() }) {
  const childEnv = cleanEnv();
  const gitEnv = { ...childEnv, GIT_TERMINAL_PROMPT: '0' };
  git ??= async (args, cwd) => (await exec('git', args, { cwd, env: gitEnv, maxBuffer: 10 * 1024 * 1024, timeout: args.includes('push') ? 300000 : 120000 })).stdout;
  const jobs = new Map(); // n -> { kind, step, state, startedAt }
  const queue = [];
  let working = false;
  let idleWaiters = [];

  const redact = (s) => (token ? String(s).split(token).join('***') : String(s));
  const prompt = async (name, vars) => {
    let t = await readFile(new URL(name, PROMPTS), 'utf8');
    for (const [k, v] of Object.entries(vars)) t = t.split(`{{${k}}}`).join(v);
    return t.replace(/\{\{[A-Z]+\}\}/g, '');
  };

  async function issueText(n) {
    const issue = await client.getIssue(n);
    const comments = await client.getComments(n);
    const parts = [`# Issue #${n}: ${issue.title}`, issue.body || '_(no description)_'];
    if (comments.length) {
      parts.push('## Comments');
      for (const c of comments) parts.push(`**${c.author ?? 'unknown'}:**\n${c.body}`);
    }
    const plan = [...comments].reverse().find((c) => c.body.startsWith(PLAN_MARK));
    return { title: issue.title, text: parts.join('\n\n'), plan: plan ? plan.body.slice(PLAN_MARK.length).trim() : null };
  }

  async function worktree(n) {
    const dir = join(runsDir, `issue-${n}`);
    if (!existsSync(dir)) {
      await git(['fetch', 'origin'], repoDir);
      await git(['worktree', 'prune'], repoDir);
      await git(['worktree', 'add', '-B', `factory/issue-${n}`, dir, 'origin/main'], repoDir);
      const props = join(repoDir, 'local.properties');
      if (existsSync(props)) await copyFile(props, join(dir, 'local.properties'));
    }
    return dir;
  }

  // Runs one Claude step; returns the text of its output file.
  async function step(job, name, dir, promptFile, vars, tools, maxTurns, out) {
    job.step = name;
    job.startedAt = now();
    const file = join(dir, '.factory', out);
    await rm(file, { force: true });
    await mkdir(join(dir, '.factory'), { recursive: true });
    await writeFile(join(dir, '.factory', '.gitignore'), '*\n');
    await runClaude(await prompt(promptFile, vars), { cwd: dir, timeoutMs: TIMEOUT, allowedTools: tools, maxTurns, settingSources: 'project,local', env: childEnv });
    if (!existsSync(file)) throw new Error(`step finished without writing .factory/${out}`);
    return readFile(file, 'utf8');
  }

  const verdictOf = (text) => {
    const first = (text.split('\n').find((l) => l.trim()) ?? '').replace(/^[\s*#>_]+/, '').replace(/[\s*_]+$/, '');
    return /^Verdict:\s*APPROVE\b/i.test(first) ? 'APPROVE' : 'CHANGES';
  };

  async function plan(n, job) {
    const dir = await worktree(n);
    const { text } = await issueText(n);
    const round = async (review) => {
      job.step = 'planning';
      const p = await step(job, 'planning', dir, 'planner.md', { ISSUE: text, REVIEW: review ? `## Review of your previous plan\n\n${review}` : '' }, READ, 30, 'plan.md');
      await client.addComment(n, `${PLAN_MARK}\n${p}`);
      const r = await step(job, 'reviewing-plan', dir, 'plan-reviewer.md', { ISSUE: text }, READ, 30, 'plan-review.md');
      await client.addComment(n, `<!-- factory:plan-review -->\n${r}`);
      return r;
    };
    const review = await round(null);
    if (verdictOf(review) === 'CHANGES') await round(review);
    await client.setStage(n, 'stage:awaiting-approval');
  }

  async function build(n, job) {
    const dir = await worktree(n);
    const { title, text, plan: approved } = await issueText(n);
    const planBlock = approved ? `## Approved plan (latest, from the issue comments)\n\n${approved}` : '';
    const branch = `factory/issue-${n}`;
    const prBody = await step(job, 'building', dir, 'builder.md', { ISSUE: text, PLAN: planBlock, NUMBER: String(n) }, BUILD, 80, 'pr.md');
    const count = Number((await git(['rev-list', '--count', 'origin/main..HEAD'], dir)).trim());
    if (!count) throw new Error('builder made no commits');
    await git(['-c', 'credential.helper=', 'push', `https://x-access-token:${token}@github.com/${repo}.git`, `+HEAD:refs/heads/${branch}`], dir);
    const found = await client.findPullForIssue(n);
    const pr = found?.state === 'open' ? found : (await client.createPull({ head: branch, title, body: `${prBody}\n\nCloses #${n}` }));
    const review = await step(job, 'reviewing-code', dir, 'code-reviewer.md', { ISSUE: text }, REVIEW, 30, 'code-review.md');
    await client.addComment(pr.number, `<!-- factory:code-review -->\n${review}`);
    await client.setStage(n, 'stage:in-review');
  }

  async function run(n, job) {
    job.state = 'running';
    job.startedAt = now();
    try {
      await (job.kind === 'planning' ? plan(n, job) : build(n, job));
    } catch (e) {
      console.error(`[factory] #${n} ${job.step} failed: ${redact(e?.stack ?? e?.message ?? e)}`);
      const reason = redact(e?.message ?? e).replace(/\s+/g, ' ').slice(0, 300);
      try {
        await client.setBlocked(n, true);
        await client.addComment(n, `Factory step ${job.step} failed: ${reason}`);
      } catch { /* GitHub unreachable: nothing more to do */ }
    }
  }

  async function drain() {
    if (working) return;
    working = true;
    while (queue.length) {
      const n = queue.shift();
      await run(n, jobs.get(n));
      jobs.delete(n);
    }
    working = false;
    idleWaiters.splice(0).forEach((r) => r());
  }

  return {
    enqueue(n, kind) {
      if (jobs.has(n)) return false;
      jobs.set(n, { kind, step: kind === 'planning' ? 'planning' : 'building', state: 'queued', startedAt: now() });
      queue.push(n);
      queueMicrotask(drain);
      return true;
    },
    status() {
      return Object.fromEntries([...jobs].map(([n, j]) => [n, { step: j.step, state: j.state, startedAt: j.startedAt }]));
    },
    whenIdle() {
      return working || queue.length ? new Promise((r) => idleWaiters.push(r)) : Promise.resolve();
    },
  };
}
