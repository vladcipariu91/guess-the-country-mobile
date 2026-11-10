export const STAGES = [
  { id: 'todo', title: 'To do', label: 'stage:todo' },
  { id: 'planning', title: 'Planning', label: 'stage:planning' },
  { id: 'awaiting-approval', title: 'Awaiting you', label: 'stage:awaiting-approval' },
  { id: 'building', title: 'Building', label: 'stage:building' },
  { id: 'in-review', title: 'In review', label: 'stage:in-review' },
  { id: 'done', title: 'Done', label: null },
];

export const ALL_LABELS = [
  'factory',
  'stage:todo',
  'stage:planning',
  'stage:awaiting-approval',
  'stage:building',
  'stage:in-review',
  'factory:blocked',
];

export function columnFor(issue) {
  if (issue.state === 'closed') return 'done';
  const stage = STAGES.find((s) => s.label && issue.labels.includes(s.label));
  return stage ? stage.id : null;
}

export const ACTIONS = {
  plan: { from: 'todo', to: 'planning' },
  approve: { from: 'awaiting-approval', to: 'building' },
  'send-back': { from: 'awaiting-approval', to: 'planning' },
  retry: { from: ['planning', 'building'], requiresBlocked: true },
};
