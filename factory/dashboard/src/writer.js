import { readFile } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { runClaude } from './claude.js';

const execFileP = promisify(execFile);
const PROMPT_PATH = new URL('../../prompts/writer.md', import.meta.url);
const TITLE_MAX = 60;

export const defaultRunClaude = (fullPrompt, { cwd, timeoutMs }) =>
  runClaude(fullPrompt, { cwd, timeoutMs, allowedTools: 'Read,Grep,Glob' });

// Best-effort: bring the shared checkout to the latest main. Never used for per-ticket worktrees.
export async function defaultGitRefresh(cwd) {
  await execFileP('git', ['-C', cwd, 'fetch', 'origin']);
  await execFileP('git', ['-C', cwd, 'checkout', '--detach', 'origin/main']);
}

export function parseWriterOutput(stdout) {
  let text;
  try {
    text = JSON.parse(stdout)?.result;
  } catch {
    return null;
  }
  if (typeof text !== 'string') return null;
  const fence = text.match(/```json\s*([\s\S]*?)```/);
  try {
    const obj = JSON.parse(fence ? fence[1] : text);
    if (typeof obj?.title === 'string' && typeof obj?.body === 'string' && obj.title.trim() && obj.body.trim()) {
      return { title: obj.title, body: obj.body };
    }
  } catch {
    // fall through
  }
  return null;
}

const cap = (s) => s.trim().replace(/\s+/g, ' ').slice(0, TITLE_MAX).trim();

export async function writeTicket(prompt, { repoDir, runClaude: run = defaultRunClaude, gitRefresh = defaultGitRefresh, timeoutMs = 120000 } = {}) {
  try {
    try { await gitRefresh(repoDir); } catch { /* best-effort */ }
    const template = await readFile(PROMPT_PATH, 'utf8');
    const stdout = await run(template.replace('{{REQUEST}}', () => prompt), { cwd: repoDir, timeoutMs });
    const parsed = parseWriterOutput(stdout);
    if (parsed) return { title: cap(parsed.title), body: parsed.body, fallback: false };
  } catch {
    // fall through to the fallback ticket
  }
  return { title: cap(prompt), body: `${prompt}\n\n_Written without AI: the writer failed._`, fallback: true };
}
