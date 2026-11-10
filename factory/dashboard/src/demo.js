import { readFileSync } from 'node:fs';

const BLOCKED = 'factory:blocked';
const DEFAULT_FIXTURE = new URL('../fixtures/demo.json', import.meta.url);

export function createDemoClient(fixturePath = DEFAULT_FIXTURE) {
  const data = JSON.parse(readFileSync(fixturePath, 'utf8'));
  const issues = structuredClone(data.issues);
  const pulls = structuredClone(data.pulls ?? {});
  const comments = structuredClone(data.comments ?? {});
  const fixtureStatus = structuredClone(data.status ?? {});
  let next = Math.max(0, ...issues.map((i) => i.number), ...Object.values(pulls).map((p) => p.number)) + 1;

  const find = (n) => {
    const i = issues.find((x) => x.number === n);
    if (!i) throw new Error(`Demo issue #${n} not found`);
    return i;
  };
  const view = ({ body, ...rest }) => ({ ...rest, labels: [...rest.labels] });

  return {
    async listFactoryIssues() {
      return issues.map(view);
    },
    async getIssue(n) {
      const i = find(n);
      return { ...view(i), body: i.body ?? '' };
    },
    async createIssue({ title, body, labels }) {
      const number = next++;
      issues.push({
        number, title, body, state: 'open', closedAt: null,
        url: `https://github.com/demo/guess-the-country/issues/${number}`,
        labels: [...new Set(['factory', 'stage:todo', ...(labels ?? [])])],
      });
      return { number, url: issues.at(-1).url };
    },
    async setStage(n, stageLabel) {
      const i = find(n);
      i.labels = [...i.labels.filter((l) => !l.startsWith('stage:') && l !== BLOCKED), stageLabel];
    },
    async setBlocked(n, blocked) {
      const i = find(n);
      const kept = i.labels.filter((l) => l !== BLOCKED);
      i.labels = blocked ? [...kept, BLOCKED] : kept;
    },
    async addComment(n, body) {
      (comments[n] ??= []).push({ author: 'demo', body, createdAt: new Date().toISOString() });
    },
    async getComments(n) {
      return structuredClone(comments[n] ?? []);
    },
    async createPull({ head, body }) {
      const number = next++;
      const pull = { number, url: `https://github.com/demo/guess-the-country/pull/${number}`, state: 'open' };
      const m = /factory\/issue-(\d+)/.exec(head) ?? /Closes #(\d+)/.exec(body ?? '');
      if (m) pulls[Number(m[1])] = pull;
      return { number, url: pull.url };
    },
    async findPullForIssue(n) {
      return pulls[n] ? { ...pulls[n] } : null;
    },
    async createLabel() {},
    // Demo-only: runner status from the fixture, with minutesAgo turned into startedAt.
    status() {
      const now = Date.now();
      return Object.fromEntries(Object.entries(fixtureStatus).map(([n, s]) => {
        const { minutesAgo, ...rest } = s;
        return [n, { ...rest, startedAt: rest.startedAt ?? new Date(now - (minutesAgo ?? 0) * 60000).toISOString() }];
      }));
    },
  };
}
