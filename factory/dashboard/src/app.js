import { readFile } from 'node:fs/promises';
import { extname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildBoard } from './board.js';
import { ACTIONS, columnFor } from './stages.js';

const DEFAULT_PUBLIC = fileURLToPath(new URL('../public', import.meta.url));
const CACHE_MS = 4000;
const MAX_BODY = 16 * 1024;
const MAX_PROMPT = 500;
const MAX_NOTE = 2000;
const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
};
const MARKERS = { 'factory:plan': 'plan', 'factory:plan-review': 'plan-review', 'factory:summary': 'summary', 'factory:code-review': 'code-review' };
const MARKER_RE = /^\s*<!--\s*(factory:[a-z-]+)\s*-->\s*\n?/;

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function toComment(c) {
  const m = MARKER_RE.exec(c.body ?? '');
  const kind = (m && MARKERS[m[1]]) || 'other';
  return { author: c.author, body: m ? c.body.slice(m[0].length) : c.body, createdAt: c.createdAt, kind };
}

function errorMessage(err) {
  return err?.name === 'GitHubError' || err instanceof HttpError ? err.message : 'Internal error';
}

async function readJson(req) {
  if (!String(req.headers['content-type'] ?? '').toLowerCase().startsWith('application/json')) {
    throw new HttpError(415, 'Content-Type must be application/json');
  }
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY) throw new HttpError(413, 'Request body too large');
    chunks.push(chunk);
  }
  try {
    const value = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
    if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error('not an object');
    return value;
  } catch {
    throw new HttpError(400, 'Invalid JSON');
  }
}

export function createApp({ client, writeTicket, runner, runsDir = null, demo = false, now = Date.now, publicDir = DEFAULT_PUBLIC }) {
  let cache = null; // { at, board }: freshness only
  let lastGood = null; // last board GitHub gave us, for the error fallback
  const invalidate = () => { cache = null; };
  const publicRoot = resolve(publicDir);

  function send(res, status, payload) {
    const text = JSON.stringify(payload);
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(text);
  }

  async function freshBoard() {
    const issues = await client.listFactoryIssues();
    const inReview = issues.filter((i) => columnFor(i) === 'in-review');
    const pulls = {};
    await Promise.all(inReview.map(async (i) => {
      const pull = await client.findPullForIssue(i.number);
      if (pull) pulls[i.number] = pull;
    }));
    const status = { ...(client.status?.() ?? {}), ...runner.status() };
    return buildBoard({ issues, status, pulls, now: new Date(now()) });
  }

  async function getBoard(res) {
    if (cache && now() - cache.at < CACHE_MS) {
      return send(res, 200, { ...cache.board, demo, error: null });
    }
    try {
      const board = await freshBoard();
      cache = { at: now(), board };
      lastGood = board;
      return send(res, 200, { ...board, demo, error: null });
    } catch (err) {
      const error = String(err?.message ?? 'Board fetch failed');
      if (lastGood) return send(res, 200, { ...lastGood, demo, error });
      return send(res, 502, { error });
    }
  }

  async function getIssueDetail(res, n) {
    const issue = await client.getIssue(n);
    const [own, pr] = await Promise.all([client.getComments(n), client.findPullForIssue(n)]);
    const prComments = pr ? await client.getComments(pr.number) : [];
    const comments = [...own, ...prComments]
      .map(toComment)
      .sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)));
    send(res, 200, {
      number: issue.number,
      title: issue.title,
      body: issue.body,
      url: issue.url,
      comments,
      pr: pr ? { number: pr.number, url: pr.url, state: pr.state } : null,
    });
  }

  async function createTicket(req, res) {
    const { prompt } = await readJson(req);
    const text = typeof prompt === 'string' ? prompt.trim().slice(0, MAX_PROMPT).trim() : '';
    if (!text) throw new HttpError(400, 'Prompt is required');
    const ticket = await writeTicket(text);
    const created = await client.createIssue({ title: ticket.title, body: ticket.body, labels: ['factory', 'stage:todo'] });
    invalidate();
    send(res, 201, { number: created.number, url: created.url, fallback: Boolean(ticket.fallback) });
  }

  async function runAction(req, res, n) {
    const { action, note } = await readJson(req);
    const def = Object.hasOwn(ACTIONS, action) ? ACTIONS[action] : null;
    if (!def) throw new HttpError(400, 'Unknown action');
    const issue = await client.getIssue(n);
    const column = columnFor(issue);
    const allowed = [].concat(def.from);
    const blocked = issue.labels.includes('factory:blocked');
    const retryable = blocked || !runner.status()[n]; // a restart can lose the job without leaving the label
    if (!allowed.includes(column) || (def.requiresBlocked && !retryable)) {
      throw new HttpError(409, `Cannot ${action} from this column`);
    }
    if (action === 'send-back' && typeof note === 'string' && note.trim()) {
      await client.addComment(n, note.trim().slice(0, MAX_NOTE));
    }
    if (action === 'retry') {
      await client.setBlocked(n, false);
    } else {
      await client.setStage(n, `stage:${def.to}`);
    }
    invalidate();
    runner.enqueue(n, action === 'retry' ? column : def.to);
    send(res, 200, { ok: true });
  }

  // The summary page the factory's summary step left in the ticket's worktree (self-contained HTML).
  async function getSummary(res, n) {
    if (!runsDir) return send(res, 404, { error: 'Not found' });
    try {
      const data = await readFile(join(runsDir, `issue-${n}`, '.factory', 'summary.html'));
      res.writeHead(200, { 'Content-Type': TYPES['.html'], 'Cache-Control': 'no-store' });
      res.end(data);
    } catch {
      send(res, 404, { error: 'No summary for this ticket yet' });
    }
  }

  async function serveStatic(res, pathname) {
    let rel;
    try { rel = decodeURIComponent(pathname); } catch { return send(res, 404, { error: 'Not found' }); }
    if (rel === '/') rel = '/index.html';
    const file = resolve(join(publicRoot, rel));
    const type = TYPES[extname(file).toLowerCase()];
    if (rel.includes('\0') || !file.startsWith(publicRoot + sep) || !type) return send(res, 404, { error: 'Not found' });
    try {
      const data = await readFile(file);
      res.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'no-store' });
      res.end(data);
    } catch {
      send(res, 404, { error: 'Not found' });
    }
  }

  return async function handler(req, res) {
    try {
      const { pathname } = new URL(req.url, 'http://localhost');
      const method = req.method;
      let m;
      if (pathname === '/api/board' && method === 'GET') return await getBoard(res);
      if (pathname === '/api/tickets' && method === 'POST') return await createTicket(req, res);
      if ((m = /^\/api\/issues\/(\d+)$/.exec(pathname)) && method === 'GET') return await getIssueDetail(res, Number(m[1]));
      if ((m = /^\/api\/issues\/(\d+)\/actions$/.exec(pathname)) && method === 'POST') return await runAction(req, res, Number(m[1]));
      if ((m = /^\/api\/issues\/(\d+)\/summary$/.exec(pathname)) && method === 'GET') return await getSummary(res, Number(m[1]));
      if (pathname.startsWith('/api/')) return send(res, 404, { error: 'Not found' });
      if (method === 'GET' || method === 'HEAD') return await serveStatic(res, pathname);
      return send(res, 404, { error: 'Not found' });
    } catch (err) {
      if (res.headersSent) return res.end();
      const status = err instanceof HttpError ? err.status : err?.status === 404 ? 404 : err?.name === 'GitHubError' ? 502 : 500;
      send(res, status, { error: errorMessage(err) });
    }
  };
}
