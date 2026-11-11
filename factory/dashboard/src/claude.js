import { spawn } from 'node:child_process';

// Splits "A,Bash(x, y:*),B" on commas outside parentheses.
function splitTools(list) {
  const out = [];
  let depth = 0;
  let cur = '';
  for (const ch of list) {
    if (ch === '(') depth++;
    else if (ch === ')') depth = Math.max(0, depth - 1);
    if (ch === ',' && depth === 0) { out.push(cur); cur = ''; } else cur += ch;
  }
  out.push(cur);
  return out.map((t) => t.trim()).filter(Boolean);
}

export function buildArgs(prompt, { allowedTools, maxTurns, settingSources = 'project,local' }) {
  const tools = Array.isArray(allowedTools) ? allowedTools : splitTools(String(allowedTools));
  // --allowedTools is variadic: each rule is its own argv element so spaces inside a rule survive.
  const args = ['-p', prompt, '--output-format', 'json', '--allowedTools', ...tools, '--setting-sources', settingSources];
  if (maxTurns) args.push('--max-turns', String(maxTurns));
  return args;
}

/**
 * Runs headless Claude Code (`claude -p`) in `cwd`. Resolves with stdout (the
 * `--output-format json` document); rejects on non-zero exit or timeout.
 * `bin`/`binArgs` exist for tests only.
 */
export function runClaude(prompt, { cwd, timeoutMs = 120000, allowedTools, maxTurns, settingSources, bin = 'claude', binArgs = [], env = process.env }) {
  return new Promise((resolve, reject) => {
    const child = spawn(bin, [...binArgs, ...buildArgs(prompt, { allowedTools, maxTurns, settingSources })], {
      cwd,
      env,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stdout = '';
    let stderr = '';
    let settled = false;
    const done = (fn, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      fn(value);
    };
    const timer = setTimeout(() => {
      child.kill('SIGTERM');
      setTimeout(() => child.kill('SIGKILL'), 5000).unref();
      done(reject, new Error(`claude timed out after ${timeoutMs}ms`));
    }, timeoutMs);
    child.stdout.on('data', (d) => { stdout += d; });
    child.stderr.on('data', (d) => { stderr = (stderr + d).slice(-2000); });
    child.on('error', (e) => done(reject, new Error(`claude failed to start: ${e.message}`)));
    child.on('close', (code, signal) => {
      if (code === 0) return done(resolve, stdout);
      let tail = stderr.trim().slice(-500);
      if (!tail) {
        try { tail = String(JSON.parse(stdout).result ?? '').slice(0, 500); } catch { /* not json */ }
      }
      done(reject, new Error(`claude exit ${code ?? signal}${tail ? `: ${tail}` : ''}`));
    });
  });
}
