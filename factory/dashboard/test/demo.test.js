import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createDemoClient } from '../src/demo.js';
import { columnFor } from '../src/stages.js';

test('fixture has one ticket per column with matching extras', async () => {
  const c = createDemoClient();
  const issues = await c.listFactoryIssues();
  assert.deepEqual(issues.map(columnFor).sort(), ['awaiting-approval', 'building', 'done', 'in-review', 'planning', 'todo']);
  const review = issues.find((i) => columnFor(i) === 'in-review');
  assert.ok(await c.findPullForIssue(review.number));
  const building = issues.find((i) => columnFor(i) === 'building');
  const st = c.status();
  assert.equal(st[building.number].state, 'running');
  assert.ok(Date.now() - Date.parse(st[building.number].startedAt) > 60000);
});

test('createIssue then listFactoryIssues includes it in To do', async () => {
  const c = createDemoClient();
  const { number } = await c.createIssue({ title: 'New one', body: 'b', labels: ['factory', 'stage:todo'] });
  const found = (await c.listFactoryIssues()).find((i) => i.number === number);
  assert.equal(found.title, 'New one');
  assert.equal(columnFor(found), 'todo');
  assert.equal((await c.getIssue(number)).body, 'b');
});

test('setStage moves a demo issue', async () => {
  const c = createDemoClient();
  const [first] = await c.listFactoryIssues();
  await c.setStage(first.number, 'stage:building');
  const moved = (await c.listFactoryIssues()).find((i) => i.number === first.number);
  assert.equal(columnFor(moved), 'building');
  assert.equal(moved.labels.filter((l) => l.startsWith('stage:')).length, 1);
});

test('writes stay in memory per client', async () => {
  const a = createDemoClient();
  await a.addComment(1, 'hi');
  await a.setBlocked(1, true);
  assert.ok((await a.getComments(1)).some((x) => x.body === 'hi'));
  assert.ok((await a.getIssue(1)).labels.includes('factory:blocked'));
  const b = createDemoClient();
  assert.ok(!(await b.getComments(1)).some((x) => x.body === 'hi'));
  const pr = await b.createPull({ head: 'factory/issue-1', title: 't', body: 'Closes #1' });
  assert.deepEqual(await b.findPullForIssue(1), { number: pr.number, url: pr.url, state: 'open' });
});
