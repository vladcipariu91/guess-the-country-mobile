import { STAGES, columnFor } from './stages.js';

const STEP_LABEL = {
  planning: 'AI planning',
  'reviewing-plan': 'AI reviewing plan',
  building: 'AI coding',
  summarizing: 'AI summarizing',
  'reviewing-code': 'AI reviewing code',
};
const ATTENTION = 'Needs attention';
const DONE_LIMIT = 10;

function chipFor({ column, blocked, st, pull, now }) {
  if (blocked) return ATTENTION;
  if (st && (column === 'planning' || column === 'building')) {
    if (st.state === 'queued') return 'Queued';
    const minutes = Math.max(0, Math.floor((now - new Date(st.startedAt)) / 60000));
    return `${STEP_LABEL[st.step] ?? 'AI working'}… ${minutes} min`;
  }
  if (column === 'planning' || column === 'building') return ATTENTION;
  if (column === 'awaiting-approval') return 'Approve plan';
  if (column === 'in-review' && pull) return `PR #${pull.number} ready`;
  return null;
}

export function buildBoard({ issues, status = {}, pulls = {}, now = new Date() }) {
  const columns = STAGES.map((s) => ({ id: s.id, title: s.title, cards: [] }));
  const byId = Object.fromEntries(columns.map((c) => [c.id, c]));
  const sorted = [...issues].sort((a, b) => a.number - b.number);

  for (const issue of sorted) {
    const column = columnFor(issue);
    if (!column) continue;
    const blocked = issue.labels.includes('factory:blocked');
    const pull = pulls[issue.number] ?? null;
    const st = status[issue.number];
    byId[column].cards.push({
      number: issue.number,
      title: issue.title,
      url: issue.url,
      column,
      blocked,
      stale: (column === 'planning' || column === 'building') && !blocked && !st,
      chip: chipFor({ column, blocked, st, pull, now }),
      prNumber: pull ? pull.number : null,
      closedAt: issue.closedAt ?? null,
    });
  }

  byId.done.cards.sort((a, b) => (b.closedAt ?? '').localeCompare(a.closedAt ?? ''));
  byId.done.cards = byId.done.cards.slice(0, DONE_LIMIT);
  for (const c of columns) for (const card of c.cards) delete card.closedAt;

  return { columns, updatedAt: now.toISOString() };
}
