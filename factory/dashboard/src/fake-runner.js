// Demo-mode stand-ins: a runner that fakes AI steps and a writer that needs no Claude call.

const STEPS = {
  planning: ['planning', 'reviewing-plan'],
  building: ['building', 'reviewing-code'],
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export function demoWriteTicket(prompt) {
  const text = prompt.trim().replace(/\s+/g, ' ');
  const title = text.length > 70 ? `${text.slice(0, 67).trim()}...` : text;
  return {
    title,
    fallback: false,
    body: [
      '## Goal',
      text,
      '',
      '## Why it matters',
      'Demo ticket: written without calling Claude.',
      '',
      '## Acceptance criteria',
      '- [ ] The feature works as described above',
      '- [ ] Existing behaviour is unchanged',
      '',
      '## Out of scope',
      'Anything not mentioned above.',
      '',
    ].join('\n'),
  };
}

export function createFakeRunner({ client, stepDelayMs = 4000, now = () => new Date().toISOString() }) {
  const running = new Map(); // n -> { step, state, startedAt }
  const queued = new Set();
  let chain = Promise.resolve();

  async function runJob(n, kind) {
    queued.delete(n);
    for (const step of STEPS[kind]) {
      running.set(n, { step, state: 'running', startedAt: now() });
      await sleep(stepDelayMs);
    }
    try {
      if (kind === 'planning') {
        await client.addComment(n, '<!-- factory:plan -->\n## Demo plan\n1. Add the UI element\n2. Wire it to the existing state\n3. Cover it with a unit test');
        await client.addComment(n, '<!-- factory:plan-review -->\nDemo review: the plan is small and safe. No concerns.');
        await client.setStage(n, 'stage:awaiting-approval');
      } else {
        const pr = await client.createPull({ head: `factory/issue-${n}`, title: `Demo change for #${n}`, body: `Demo pull request.\n\nCloses #${n}` });
        await client.addComment(pr.number, '<!-- factory:code-review -->\nDemo review: the change is focused and matches the plan.');
        await client.setStage(n, 'stage:in-review');
      }
    } finally {
      running.delete(n);
    }
  }

  return {
    enqueue(n, kind) {
      if (!STEPS[kind] || running.has(n) || queued.has(n)) return false;
      queued.add(n);
      chain = chain.then(() => runJob(n, kind)).catch(() => {});
      return true;
    },
    status() {
      const out = {};
      for (const n of queued) out[n] = { state: 'queued' };
      for (const [n, s] of running) out[n] = { ...s };
      return out;
    },
    whenIdle() {
      return chain;
    },
  };
}
