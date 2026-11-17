import http from 'node:http';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createApp } from './app.js';
import { createGitHubClient } from './github.js';
import { createDemoClient } from './demo.js';
import { createRunner } from './runner.js';
import { createFakeRunner, demoWriteTicket } from './fake-runner.js';
import { writeTicket } from './writer.js';

const dashboardDir = fileURLToPath(new URL('..', import.meta.url));
const env = process.env;
const demo = ['1', 'true'].includes(env.DEMO ?? '');
const repo = env.GITHUB_REPO || 'vladcipariu91/guess-the-country-mobile';
const repoDir = resolve(dashboardDir, env.FACTORY_REPO_DIR || '../..');
const runsDir = resolve(dashboardDir, env.FACTORY_RUNS_DIR || '../../../factory-runs');
const port = Number(env.PORT ?? 4321);

let client;
let runner;
let write;
if (demo) {
  client = createDemoClient();
  runner = createFakeRunner({ client });
  write = async (prompt) => demoWriteTicket(prompt);
} else {
  const token = env.GITHUB_TOKEN;
  delete process.env.GITHUB_TOKEN; // nothing the server spawns may inherit it
  if (!token) {
    console.error('GITHUB_TOKEN is missing. Put it in factory/dashboard/.env, or run with DEMO=1.');
    process.exit(1);
  }
  client = createGitHubClient({ token, repo });
  runner = createRunner({ client, token, repo, repoDir, runsDir, summaryUrl: (n) => `http://localhost:${port}/api/issues/${n}/summary` });
  write = (prompt) => writeTicket(prompt, { repoDir });
}

const server = http.createServer(createApp({ client, writeTicket: write, runner, runsDir: demo ? null : runsDir, demo }));
server.listen(port, '127.0.0.1', () => {
  console.log(`Factory dashboard: http://localhost:${server.address().port}${demo ? ' (DEMO)' : ''}`);
});
