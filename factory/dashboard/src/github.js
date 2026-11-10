const API = 'https://api.github.com';
const BLOCKED = 'factory:blocked';

export class GitHubError extends Error {
  constructor(status, message) {
    super(message);
    this.name = 'GitHubError';
    this.status = status;
  }
}

export function createGitHubClient({ token, repo, fetchImpl = fetch }) {
  const owner = repo.split('/')[0];

  async function request(method, path, body, { allow } = {}) {
    const res = await fetchImpl(`${API}/repos/${repo}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) {
      if (allow?.includes(res.status)) return null;
      let detail = '';
      try { detail = (await res.json())?.message ?? ''; } catch { /* no body */ }
      throw new GitHubError(res.status, `GitHub ${method} ${path} failed: ${res.status}${detail ? ` ${detail}` : ''}`);
    }
    return res.status === 204 ? null : res.json();
  }

  const toIssue = (i) => ({
    number: i.number,
    title: i.title,
    url: i.html_url,
    state: i.state,
    labels: (i.labels ?? []).map((l) => (typeof l === 'string' ? l : l.name)),
    closedAt: i.closed_at ?? null,
  });

  async function getIssue(n) {
    const i = await request('GET', `/issues/${n}`);
    return { ...toIssue(i), body: i.body ?? '' };
  }

  const putLabels = (n, labels) => request('PUT', `/issues/${n}/labels`, { labels });

  return {
    async listFactoryIssues() {
      const items = await request('GET', '/issues?labels=factory&state=all&per_page=100');
      return items.filter((i) => !i.pull_request).map(toIssue);
    },
    async createPull({ head, title, body }) {
      const pr = await request('POST', '/pulls', { head, base: 'main', title, body });
      return { number: pr.number, url: pr.html_url };
    },
    getIssue,
    async findPullForIssue(n) {
      const pulls = await request('GET', `/pulls?head=${owner}:factory/issue-${n}&state=all`);
      if (!pulls.length) return null;
      const newest = [...pulls].sort((a, b) => b.created_at.localeCompare(a.created_at))[0];
      return { number: newest.number, url: newest.html_url, state: newest.state };
    },
    async getComments(n) {
      const items = await request('GET', `/issues/${n}/comments?per_page=100`);
      return items.map((c) => ({ author: c.user?.login ?? null, body: c.body, createdAt: c.created_at }));
    },
    async createIssue({ title, body, labels }) {
      const i = await request('POST', '/issues', { title, body, labels });
      return { number: i.number, url: i.html_url };
    },
    async setStage(n, stageLabel) {
      const { labels } = await getIssue(n);
      const kept = labels.filter((l) => !l.startsWith('stage:') && l !== BLOCKED);
      await putLabels(n, [...kept, stageLabel]);
    },
    async setBlocked(n, blocked) {
      const { labels } = await getIssue(n);
      const kept = labels.filter((l) => l !== BLOCKED);
      await putLabels(n, blocked ? [...kept, BLOCKED] : kept);
    },
    async addComment(n, body) {
      await request('POST', `/issues/${n}/comments`, { body });
    },
    async createLabel(name, color) {
      await request('POST', '/labels', { name, color }, { allow: [422] });
    },
  };
}
