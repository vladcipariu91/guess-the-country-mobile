import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildArgs, runClaude } from '../src/claude.js';

test('buildArgs builds the claude -p argument list', () => {
  assert.deepEqual(buildArgs('hi', { allowedTools: 'Read,Grep', maxTurns: 5 }), [
    '-p', 'hi', '--output-format', 'json', '--allowedTools', 'Read', 'Grep', '--setting-sources', 'project,local', '--max-turns', '5',
  ]);
  assert.ok(!buildArgs('hi', { allowedTools: 'Read' }).includes('--max-turns'));
  assert.ok(buildArgs('hi', { allowedTools: 'Read', settingSources: 'user' }).includes('user'));
});

test('buildArgs passes each tool rule as its own argument, splitting only outside parentheses', () => {
  const tools = 'Read,Bash(git add:*),Bash(git commit -m "a, b":*),Agent';
  const expected = ['Read', 'Bash(git add:*)', 'Bash(git commit -m "a, b":*)', 'Agent'];
  for (const input of [tools, expected]) {
    const args = buildArgs('hi', { allowedTools: input });
    const i = args.indexOf('--allowedTools');
    assert.deepEqual(args.slice(i + 1, i + 1 + expected.length), expected);
    assert.equal(args[i + 1 + expected.length], '--setting-sources');
  }
});

test('runClaude resolves stdout, rejects on non-zero exit and on timeout', async () => {
  const dir = process.cwd();
  const ok = await runClaude('x', { cwd: dir, allowedTools: 'Read', bin: process.execPath, binArgs: ['-e', 'process.stdout.write("out")', '--'] });
  assert.equal(ok, 'out');
  await assert.rejects(runClaude('x', { cwd: dir, allowedTools: 'Read', bin: process.execPath, binArgs: ['-e', 'console.error("boom");process.exit(3)', '--'] }), /exit 3.*boom/s);
  await assert.rejects(runClaude('x', { cwd: dir, allowedTools: 'Read', timeoutMs: 100, bin: process.execPath, binArgs: ['-e', 'setTimeout(()=>{},10000)', '--'] }), /timed out/);
});
