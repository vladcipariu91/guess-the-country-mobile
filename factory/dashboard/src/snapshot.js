// Writes the live board's issues, pulls and comments into fixtures/demo.json so the
// dashboard can run offline in demo mode. Needs GITHUB_TOKEN and GITHUB_REPO.
import { readFileSync, writeFileSync } from 'node:fs';
import { createGitHubClient } from './github.js';

const fixtureUrl = new URL('../fixtures/demo.json', import.meta.url);
const { GITHUB_TOKEN: token, GITHUB_REPO: repo } = process.env;
if (!token || !repo) {
  console.error('Set GITHUB_TOKEN and GITHUB_REPO (see .env).');
  process.exit(1);
}

const gh = createGitHubClient({ token, repo });
const listed = await gh.listFactoryIssues();
const issues = [];
const pulls = {};
const comments = {};

for (const item of listed) {
  const full = await gh.getIssue(item.number);
  issues.push(full);
  const pull = await gh.findPullForIssue(item.number);
  if (pull) pulls[item.number] = pull;
  comments[item.number] = await gh.getComments(item.number);
  if (pull) comments[pull.number] = await gh.getComments(pull.number);
}

// Keep the hand-set status (the Building card's running chip) from the existing fixture.
let status = {};
try { status = JSON.parse(readFileSync(fixtureUrl, 'utf8')).status ?? {}; } catch { /* no fixture yet */ }
// Status is keyed by issue number: drop entries whose issue isn't a planning/building card in this snapshot.
const running = new Set(issues.filter((i) => i.state !== 'closed' && i.labels.some((l) => l === 'stage:planning' || l === 'stage:building')).map((i) => String(i.number)));
status = Object.fromEntries(Object.entries(status).filter(([n]) => running.has(n)));

writeFileSync(fixtureUrl, `${JSON.stringify({ issues, status, pulls, comments }, null, 2)}\n`);
console.log(`Wrote ${issues.length} issues to fixtures/demo.json`);
