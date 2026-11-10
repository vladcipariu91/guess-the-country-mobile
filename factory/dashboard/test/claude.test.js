import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildArgs, runClaude } from '../src/claude.js';

test('buildArgs builds the claude -p argument list', () => {
  assert.deepEqual(buildArgs('hi', { allowedTools: 'Read,Grep', maxTurns: 5 }), [
    '-p', 'hi', '--output-format', 'json', '--allowedTools', 'Read,Grep', '--setting-sources', 'project,local', '--max-turns', '5',
  ]);
  assert.ok(!buildArgs('hi', { allowedTools: 'Read' }).includes('--max-turns'));
  assert.ok(buildArgs('hi', { allowedTools: 'Read', settingSources: 'user' }).includes('user'));
});

test('runClaude resolves stdout, rejects on non-zero exit and on timeout', async () => {
  const dir = process.cwd();
  const ok = await runClaude('x', { cwd: dir, allowedTools: 'Read', bin: process.execPath, binArgs: ['-e', 'process.stdout.write("out")', '--'] });
  assert.equal(ok, 'out');
  await assert.rejects(runClaude('x', { cwd: dir, allowedTools: 'Read', bin: process.execPath, binArgs: ['-e', 'console.error("boom");process.exit(3)', '--'] }), /exit 3.*boom/s);
  await assert.rejects(runClaude('x', { cwd: dir, allowedTools: 'Read', timeoutMs: 100, bin: process.execPath, binArgs: ['-e', 'setTimeout(()=>{},10000)', '--'] }), /timed out/);
});
