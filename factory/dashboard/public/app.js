// Software Factory dashboard: conveyor-belt board. Plain browser JS, no build step.
// All API text is put into the page with textContent / text nodes; innerHTML is never used.
'use strict';

(() => {
  const POLL_MS = 5000;
  const GLOW_MS = 1700;
  const PLACEHOLDER_GRACE_MS = 15000;

  const COLUMNS = [
    { id: 'todo', title: 'To do', role: 'neutral', sub: 'Click Plan ▸ to start', empty: 'Type a feature above' },
    { id: 'planning', title: 'Planning', role: 'ai', sub: 'AI plans, AI reviews', empty: 'Nothing planning' },
    { id: 'awaiting-approval', title: 'Awaiting you', role: 'you', sub: 'Approve or send back', empty: 'Nothing to approve' },
    { id: 'building', title: 'Building', role: 'ai', sub: 'AI codes, AI reviews', empty: 'Nothing building' },
    { id: 'in-review', title: 'In review', role: 'you', sub: 'You merge the PR', empty: 'No open PRs' },
    { id: 'done', title: 'Done', role: 'done', sub: 'Merged and closed', empty: 'Nothing shipped yet' },
  ];
  const COLUMN_BY_ID = Object.fromEntries(COLUMNS.map((c) => [c.id, c]));
  const ROLE_CHIP = { ai: 'chip-ai', you: 'chip-you', done: 'chip-ok', neutral: '' };

  const ACTION_TOAST = {
    plan: (n) => `#${n} sent to the AI planner`,
    approve: (n) => `#${n} approved: the AI starts building`,
    'send-back': (n) => `#${n} sent back to planning`,
    retry: (n) => `Retrying #${n}`,
  };

  const $ = (id) => document.getElementById(id);
  const ui = {
    board: $('board'),
    banner: $('banner'),
    bannerText: $('banner-text'),
    demoPill: $('demo-pill'),
    live: $('live'),
    liveText: $('live-text'),
    form: $('prompt-form'),
    input: $('prompt-input'),
    createBtn: $('create-btn'),
    panel: $('panel'),
    panelNum: $('panel-num'),
    panelStage: $('panel-stage'),
    panelTitle: $('panel-title'),
    panelLinks: $('panel-links'),
    panelBody: $('panel-body'),
    panelClose: $('panel-close'),
    toasts: $('toasts'),
  };

  const state = {
    lanes: new Map(), // column id -> { list, count, empty }
    cards: new Map(), // issue number -> card entry
    loaded: false,
    pollSeq: 0,
    renderedSeq: 0,
    timer: null,
    creating: false,
    pending: null, // { prompt, startedAt, number }
    pendingEl: null,
    pendingTick: null,
    openN: null,
    openSig: null,
    detailSeq: 0,
    lastFocus: null,
  };

  // ---------- small helpers ----------

  function h(tag, attrs, ...children) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v === null || v === undefined || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'text') el.textContent = v;
      else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
      else el.setAttribute(k, v === true ? '' : String(v));
    }
    for (const c of children.flat()) {
      if (c === null || c === undefined || c === false) continue;
      el.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return el;
  }

  function safeUrl(u) {
    return typeof u === 'string' && /^https?:\/\//i.test(u) ? u : null;
  }

  function extLink(url, children, cls) {
    const href = safeUrl(url);
    if (!href) return h('span', { class: cls }, children);
    return h('a', { href, target: '_blank', rel: 'noopener noreferrer', class: cls }, children);
  }

  function clockTime(iso) {
    const d = iso ? new Date(iso) : new Date();
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  function shortDate(iso) {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  }

  function chipKind(text) {
    if (!text) return '';
    if (/needs attention/i.test(text)) return 'chip-bad';
    if (/^approve/i.test(text)) return 'chip-you';
    if (/^PR #\d+ ready/i.test(text)) return 'chip-ok';
    if (/\bAI\b|queued/i.test(text)) return 'chip-ai';
    return '';
  }

  function isRunning(text) {
    return chipKind(text) === 'chip-ai' && !/queued/i.test(text);
  }

  function setChip(el, text, extraClass) {
    el.hidden = !text;
    el.textContent = text || '';
    const kind = extraClass ?? chipKind(text);
    el.className = ['chip', kind, isRunning(text) ? 'chip-live' : ''].filter(Boolean).join(' ');
  }

  async function api(path, { method = 'GET', body } = {}) {
    let res;
    try {
      res = await fetch(path, {
        method,
        cache: 'no-store',
        headers: body ? { 'Content-Type': 'application/json' } : undefined,
        body: body ? JSON.stringify(body) : undefined,
      });
    } catch {
      throw new Error("Can't reach the dashboard server");
    }
    let data = null;
    try { data = await res.json(); } catch { /* non-JSON body */ }
    if (!res.ok) {
      const msg = data && typeof data.error === 'string' && data.error ? data.error : `Request failed (${res.status})`;
      const err = new Error(msg);
      err.status = res.status;
      throw err;
    }
    return data;
  }

  // ---------- toasts, banner, live indicator ----------

  function toast(message, kind = 'info', ms) {
    const el = h('div', { class: `toast toast-${kind}`, role: kind === 'error' ? 'alert' : 'status', text: message });
    el.addEventListener('click', () => el.remove());
    ui.toasts.append(el);
    while (ui.toasts.children.length > 4) ui.toasts.firstElementChild.remove();
    setTimeout(() => {
      el.classList.add('is-leaving');
      setTimeout(() => el.remove(), 300);
    }, ms ?? (kind === 'error' ? 6500 : 4000));
  }

  function setBanner(message) {
    ui.banner.hidden = !message;
    ui.bannerText.textContent = message || '';
  }

  function setLive(kind, text) {
    ui.live.dataset.state = kind;
    ui.liveText.textContent = text;
  }

  // ---------- board skeleton ----------

  function buildLanes() {
    for (const col of COLUMNS) {
      const titleId = `lane-title-${col.id}`;
      const count = h('span', { class: 'lane-count', text: '0' });
      const empty = h('p', { class: 'lane-empty', text: col.empty, hidden: true });
      const list = h('div', { class: 'lane-cards' }, empty, h('div', { class: 'skeleton' }));
      if (col.id === 'todo' || col.id === 'planning' || col.id === 'building') list.append(h('div', { class: 'skeleton' }));
      const lane = h('section', { class: 'lane', 'data-role': col.role, 'data-column': col.id, 'aria-labelledby': titleId },
        h('header', { class: 'lane-head' },
          h('h2', { class: 'lane-title', id: titleId, text: col.title }),
          count,
          h('p', { class: 'lane-sub', text: col.sub })),
        list);
      ui.board.append(lane);
      state.lanes.set(col.id, { list, count, empty });
    }
  }

  function clearSkeletons() {
    for (const el of ui.board.querySelectorAll('.skeleton')) el.remove();
    for (const { list, empty } of state.lanes.values()) empty.hidden = list.querySelector('.card') !== null;
  }

  // ---------- cards ----------

  function createCard(card) {
    const entry = {
      n: card.number,
      num: h('span', { class: 'card-num' }),
      check: h('span', { class: 'card-check', 'aria-hidden': 'true', text: '✓', hidden: true }),
      title: h('h3', { class: 'card-title' }),
      chip: h('span', { class: 'chip', hidden: true }),
      actions: h('div', { class: 'card-actions' }),
      sig: null,
      actionsKey: null,
      column: null,
      card: null,
    };
    entry.foot = h('div', { class: 'card-foot' }, entry.chip, entry.actions);
    entry.el = h('article', { class: 'card', tabindex: '0', 'data-n': card.number },
      h('div', { class: 'card-top' }, entry.num, entry.check),
      entry.title,
      entry.foot);

    entry.el.addEventListener('click', () => openPanel(entry.n));
    entry.el.addEventListener('keydown', (e) => {
      if (e.target === entry.el && (e.key === 'Enter' || e.key === ' ')) {
        e.preventDefault();
        openPanel(entry.n);
      }
    });
    // Buttons and the send-back form never open the side panel.
    entry.actions.addEventListener('click', (e) => e.stopPropagation());
    entry.actions.addEventListener('keydown', (e) => e.stopPropagation());

    updateCard(entry, card);
    return entry;
  }

  function cardSig(card) {
    return [card.title, card.chip, card.blocked, card.column, card.prNumber].join('\u0001');
  }

  function updateCard(entry, card) {
    entry.card = card;
    const sig = cardSig(card);
    if (sig === entry.sig) return;
    entry.sig = sig;
    entry.column = card.column;

    entry.num.textContent = `#${card.number}`;
    entry.title.textContent = card.title || '(untitled)';
    entry.el.dataset.column = card.column;
    entry.el.classList.toggle('is-blocked', Boolean(card.blocked));
    entry.el.classList.toggle('is-running', isRunning(card.chip));
    entry.check.hidden = card.column !== 'done';
    setChip(entry.chip, card.chip);
    entry.el.setAttribute('aria-label', `#${card.number} ${card.title || ''}${card.chip ? `, ${card.chip}` : ''}`);

    const key = `${card.column}|${card.blocked ? 1 : 0}`;
    if (key !== entry.actionsKey) {
      entry.actionsKey = key;
      renderActions(entry);
    }
    syncFoot(entry);
  }

  function syncFoot(entry) {
    entry.foot.hidden = entry.chip.hidden && entry.actions.childElementCount === 0;
  }

  function actionButton(entry, label, cls, action) {
    const btn = h('button', { class: `btn ${cls}`, type: 'button' },
      h('span', { class: 'spinner', 'aria-hidden': 'true' }),
      h('span', { text: label }));
    btn.addEventListener('click', () => runAction(entry, action, undefined, btn));
    return btn;
  }

  function renderActions(entry) {
    const { column, blocked } = entry.card;
    const buttons = [];
    if (column === 'todo') buttons.push(actionButton(entry, 'Plan ▸', 'btn-ai-outline', 'plan'));
    if (column === 'awaiting-approval') {
      buttons.push(actionButton(entry, 'Approve', 'btn-you', 'approve'));
      const back = h('button', { class: 'btn btn-ghost', type: 'button', text: 'Send back' });
      back.addEventListener('click', () => openSendBack(entry));
      buttons.push(back);
    }
    if (blocked) buttons.push(actionButton(entry, 'Retry', 'btn-danger', 'retry'));
    entry.actions.replaceChildren(...buttons);
    syncFoot(entry);
  }

  function openSendBack(entry) {
    const input = h('input', {
      type: 'text', maxlength: '2000', placeholder: 'Note (optional)',
      'aria-label': 'Note for the planner',
    });
    const send = h('button', { class: 'btn btn-you', type: 'submit' },
      h('span', { class: 'spinner', 'aria-hidden': 'true' }), h('span', { text: 'Send back' }));
    const cancel = h('button', { class: 'btn btn-ghost', type: 'button', text: 'Cancel' });
    const form = h('form', { class: 'sendback' }, input, h('div', { class: 'sendback-row' }, send, cancel));
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      runAction(entry, 'send-back', input.value.trim() || undefined, send);
    });
    cancel.addEventListener('click', () => renderActions(entry));
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') renderActions(entry);
    });
    entry.actions.replaceChildren(form);
    syncFoot(entry);
    input.focus();
  }

  async function runAction(entry, action, note, btn) {
    const n = entry.n;
    const controls = [...entry.actions.querySelectorAll('button, input')];
    for (const c of controls) c.disabled = true;
    btn.classList.add('is-busy');
    try {
      await api(`/api/issues/${n}/actions`, { method: 'POST', body: note ? { action, note } : { action } });
      toast(ACTION_TOAST[action](n), 'success');
      await poll();
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      btn.classList.remove('is-busy');
      for (const c of controls) c.disabled = false;
    }
  }

  function glow(el) {
    el.classList.remove('is-moved');
    void el.offsetWidth; // restart the animation
    el.classList.add('is-moved');
    clearTimeout(el._glowTimer);
    el._glowTimer = setTimeout(() => el.classList.remove('is-moved'), GLOW_MS);
  }

  // Puts exactly `els` into `list` (after the empty-state line), moving only what is out of place.
  function place(list, empty, els) {
    const want = new Set(els);
    for (const child of [...list.children]) {
      if (child !== empty && !want.has(child)) child.remove();
    }
    els.forEach((el, i) => {
      const at = list.children[i + 1] || null;
      if (at !== el) list.insertBefore(el, at);
    });
    empty.hidden = els.length > 0;
  }

  // ---------- "writing…" placeholder ----------

  function elapsed(ms) {
    const s = Math.max(0, Math.floor(ms / 1000));
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  function showPlaceholder() {
    const p = state.pending;
    const chip = h('span', { class: 'chip' });
    const el = h('article', { class: 'card placeholder is-running', 'aria-live': 'polite' },
      h('div', { class: 'card-top' }, h('span', { text: 'New ticket' })),
      h('h3', { class: 'card-title', text: `“${p.prompt}”` }),
      h('div', { class: 'card-foot' }, chip));
    const tick = () => setChip(chip, `AI writing ticket… ${elapsed(Date.now() - p.startedAt)}`);
    tick();
    clearInterval(state.pendingTick);
    state.pendingTick = setInterval(tick, 1000);
    state.pendingEl = el;
    const lane = state.lanes.get('todo');
    lane.list.append(el);
    lane.empty.hidden = true;
    el.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }

  function dropPlaceholder() {
    state.pending = null;
    clearInterval(state.pendingTick);
    state.pendingTick = null;
    if (state.pendingEl) {
      state.pendingEl.remove();
      state.pendingEl = null;
      const lane = state.lanes.get('todo');
      lane.empty.hidden = lane.list.querySelector('.card') !== null;
    }
  }

  // ---------- render ----------

  function render(data) {
    if (!state.loaded) clearSkeletons();
    ui.demoPill.hidden = !data.demo;

    const byCol = new Map(COLUMNS.map((c) => [c.id, []]));
    const incoming = new Map();
    for (const col of data.columns) {
      for (const card of col.cards || []) {
        const id = byCol.has(card.column) ? card.column : col.id;
        if (!byCol.has(id) || incoming.has(card.number)) continue;
        const normalized = { ...card, column: id };
        byCol.get(id).push(normalized);
        incoming.set(card.number, normalized);
      }
    }

    for (const [n, entry] of state.cards) {
      if (!incoming.has(n)) {
        entry.el.remove();
        state.cards.delete(n);
      }
    }

    const toGlow = [];
    for (const [n, card] of incoming) {
      let entry = state.cards.get(n);
      if (!entry) {
        entry = createCard(card);
        state.cards.set(n, entry);
        if (state.loaded) toGlow.push(entry.el);
      } else {
        const before = entry.column;
        updateCard(entry, card);
        if (before !== card.column) toGlow.push(entry.el);
      }
      entry.el.classList.toggle('is-open', n === state.openN);
    }

    if (state.pending && state.pending.number !== null && incoming.has(state.pending.number)) {
      dropPlaceholder();
    }

    for (const col of COLUMNS) {
      const lane = state.lanes.get(col.id);
      const cards = byCol.get(col.id);
      const els = cards.map((c) => state.cards.get(c.number).el);
      if (col.id === 'todo' && state.pendingEl) els.push(state.pendingEl);
      place(lane.list, lane.empty, els);
      lane.count.textContent = String(cards.length);
    }

    for (const el of toGlow) glow(el);
    state.loaded = true;
    syncPanelWithBoard();
  }

  // ---------- polling ----------

  async function poll() {
    clearTimeout(state.timer);
    const seq = ++state.pollSeq;
    try {
      let res;
      let data = null;
      try {
        res = await fetch('/api/board', { cache: 'no-store' });
      } catch {
        throw new Error('offline');
      }
      try { data = await res.json(); } catch { /* non-JSON */ }
      if (seq < state.renderedSeq) return; // a newer response already rendered

      if (!res.ok || !data || !Array.isArray(data.columns)) {
        const msg = (data && data.error) || `server error ${res.status}`;
        if (!state.loaded) clearSkeletons();
        setBanner(state.loaded ? `Couldn't refresh the board: ${msg}. Showing the last known board.` : `Couldn't load the board: ${msg}`);
        setLive('down', 'Board unavailable');
        return;
      }
      state.renderedSeq = seq;
      render(data);
      if (data.error) {
        setBanner(`GitHub error: ${data.error}. Showing the last known board.`);
        setLive('stale', `Stale · ${clockTime(data.updatedAt)}`);
      } else {
        setBanner(null);
        setLive('ok', `Live · ${clockTime(data.updatedAt)}`);
      }
    } catch {
      if (!state.loaded) clearSkeletons();
      setBanner("Can't reach the dashboard server. Retrying every 5 seconds…");
      setLive('down', 'Offline');
    } finally {
      if (seq === state.pollSeq) state.timer = setTimeout(poll, POLL_MS);
    }
  }

  // ---------- create ticket ----------

  function setCreateBusy(busy) {
    ui.createBtn.disabled = busy;
    ui.createBtn.classList.toggle('is-busy', busy);
    ui.createBtn.querySelector('.btn-label').textContent = busy ? 'Writing…' : 'Create ticket';
    ui.input.disabled = busy;
  }

  async function createTicket(e) {
    e.preventDefault();
    if (state.creating) return;
    const prompt = ui.input.value.trim();
    if (!prompt) {
      ui.form.classList.remove('shake');
      void ui.form.offsetWidth;
      ui.form.classList.add('shake');
      ui.input.focus();
      return;
    }
    state.creating = true;
    setCreateBusy(true);
    dropPlaceholder();
    state.pending = { prompt, startedAt: Date.now(), number: null };
    showPlaceholder();
    try {
      const created = await api('/api/tickets', { method: 'POST', body: { prompt } });
      ui.input.value = '';
      if (state.pending) state.pending.number = created.number;
      toast(created.fallback
        ? `Ticket #${created.number} created (AI writer unavailable, saved as typed)`
        : `Ticket #${created.number} created`, created.fallback ? 'info' : 'success');
      await poll();
      const pending = state.pending;
      if (pending) setTimeout(() => { if (state.pending === pending) dropPlaceholder(); }, PLACEHOLDER_GRACE_MS);
    } catch (err) {
      dropPlaceholder();
      toast(err.message, 'error');
    } finally {
      state.creating = false;
      setCreateBusy(false);
      ui.input.focus();
    }
  }

  // ---------- side panel ----------

  function fillPanelHeader(n, title, url, card, pr) {
    ui.panelNum.textContent = `#${n}`;
    if (title) ui.panelTitle.textContent = title;
    const stage = [];
    if (card) {
      const col = COLUMN_BY_ID[card.column];
      if (col) {
        const c = h('span');
        setChip(c, col.title, ROLE_CHIP[col.role]);
        stage.push(c);
      }
      if (card.chip) {
        const c = h('span');
        setChip(c, card.chip);
        stage.push(c);
      }
    }
    ui.panelStage.replaceChildren(...stage);

    if (url !== undefined || pr !== undefined) {
      const links = [];
      if (safeUrl(url)) links.push(extLink(url, [`Issue #${n} on GitHub`, h('span', { class: 'arrow', text: '↗' })], 'link-btn'));
      if (pr && safeUrl(pr.url)) {
        links.push(extLink(pr.url, [`PR #${pr.number} · ${pr.state || 'open'}`, h('span', { class: 'arrow', text: '↗' })],
          `link-btn ${/merged/i.test(pr.state) ? 'pr-merged' : /open/i.test(pr.state || 'open') ? 'pr-open' : ''}`));
      }
      ui.panelLinks.replaceChildren(...links);
    }
  }

  function openPanel(n) {
    const entry = state.cards.get(n);
    const already = state.openN === n && !ui.panel.hidden;
    if (!already) {
      for (const e of state.cards.values()) e.el.classList.toggle('is-open', e.n === n);
      state.openN = n;
      state.openSig = entry ? entry.sig : null;
      state.lastFocus = entry ? entry.el : null;
      ui.panel.hidden = false;
      fillPanelHeader(n, entry?.card.title, entry?.card.url, entry?.card, null);
      ui.panelBody.replaceChildren(h('div', { class: 'panel-loading' },
        h('div', { class: 'skeleton' }), h('div', { class: 'skeleton' })));
      ui.panelBody.scrollTop = 0;
      loadDetail(n, false);
    }
    ui.panelClose.focus({ preventScroll: true });
  }

  function closePanel() {
    if (ui.panel.hidden) return;
    ui.panel.hidden = true;
    state.openN = null;
    state.openSig = null;
    state.detailSeq++;
    for (const e of state.cards.values()) e.el.classList.remove('is-open');
    const back = state.lastFocus;
    state.lastFocus = null;
    if (back && back.isConnected) back.focus({ preventScroll: true });
  }

  function syncPanelWithBoard() {
    if (state.openN === null) return;
    const entry = state.cards.get(state.openN);
    if (!entry) return; // card left the board (e.g. old Done card): keep what we show
    fillPanelHeader(entry.n, null, undefined, entry.card, undefined);
    if (entry.sig !== state.openSig) {
      state.openSig = entry.sig;
      loadDetail(entry.n, true);
    }
  }

  async function loadDetail(n, keepScroll) {
    const seq = ++state.detailSeq;
    try {
      const detail = await api(`/api/issues/${n}`);
      if (seq !== state.detailSeq || state.openN !== n) return;
      const scroll = ui.panelBody.scrollTop;
      const card = state.cards.get(n)?.card;
      fillPanelHeader(n, detail.title, detail.url, card, detail.pr);
      ui.panelBody.replaceChildren(renderStory(detail, card));
      if (keepScroll) ui.panelBody.scrollTop = scroll;
    } catch (err) {
      if (seq !== state.detailSeq || state.openN !== n) return;
      if (keepScroll && ui.panelBody.querySelector('.timeline')) return; // keep the last good story
      const retry = h('button', { class: 'btn btn-ghost', type: 'button', text: 'Try again' });
      retry.addEventListener('click', () => {
        ui.panelBody.replaceChildren(h('div', { class: 'panel-loading' }, h('div', { class: 'skeleton' })));
        loadDetail(n, false);
      });
      ui.panelBody.replaceChildren(h('div', { class: 'panel-error' },
        h('div', { text: `Couldn't load the story: ${err.message}` }), retry));
    }
  }

  const COMMENT_KIND = {
    plan: { badge: 'Plan', tone: 'ai', who: 'AI planner' },
    'plan-review': { badge: 'Plan review', tone: 'ai', who: 'AI plan reviewer' },
    'code-review': { badge: 'Code review', tone: 'ai', who: 'AI code reviewer' },
  };

  function storyEntry({ badge, tone, meta, body }) {
    return h('section', { class: `entry ${tone ? `entry-${tone}` : ''}` },
      h('div', { class: 'entry-head' },
        h('span', { class: 'entry-badge', text: badge }),
        meta ? h('span', { class: 'entry-meta', text: meta }) : null),
      body);
  }

  function prEntry(pr) {
    const merged = /merged/i.test(pr.state || '');
    const open = /open/i.test(pr.state || '');
    const tone = merged ? 'ai' : open ? 'ok' : '';
    return h('section', { class: `entry entry-pr ${tone ? `entry-${tone}` : ''}` },
      h('div', { class: 'entry-head' },
        h('span', { class: 'entry-badge', text: 'Pull request' }),
        h('span', { class: 'entry-meta', text: `#${pr.number} · ${pr.state || 'open'}` })),
      extLink(pr.url, ['Open PR on GitHub ', h('span', { class: 'arrow', text: '↗' })], 'link-btn'));
  }

  function hintFor(card, comments) {
    if (comments.length) return null;
    const col = card?.column;
    if (col === 'todo') return 'No plan yet. Click Plan ▸ on the card to send it to the AI planner.';
    if (col === 'planning') return 'The AI planner is working on a plan. It will appear here.';
    return null;
  }

  function renderStory(detail, card) {
    const comments = Array.isArray(detail.comments) ? detail.comments : [];
    const items = [storyEntry({
      badge: 'The ticket',
      tone: '',
      meta: `Issue #${detail.number}`,
      body: detail.body && detail.body.trim() ? renderMarkdown(detail.body) : h('p', { class: 'md', text: 'No description.' }),
    })];

    let prShown = false;
    for (const c of comments) {
      if (detail.pr && !prShown && c.kind === 'code-review') {
        items.push(prEntry(detail.pr));
        prShown = true;
      }
      const when = c.createdAt ? shortDate(c.createdAt) : '';
      const known = COMMENT_KIND[c.kind];
      let spec;
      if (known) spec = { badge: known.badge, tone: known.tone, who: known.who };
      else if (/^Factory step .+ failed/i.test(c.body || '')) spec = { badge: 'Failed', tone: 'bad', who: 'Factory' };
      else spec = { badge: 'Note', tone: 'you', who: c.author || 'someone' };
      items.push(storyEntry({
        badge: spec.badge,
        tone: spec.tone,
        meta: [spec.who, when].filter(Boolean).join(' · '),
        body: renderMarkdown(c.body || ''),
      }));
    }
    if (detail.pr && !prShown) items.push(prEntry(detail.pr));

    const hint = hintFor(card, comments);
    if (hint) items.push(h('p', { class: 'entry-meta', text: hint }));
    return h('div', { class: 'timeline' }, items);
  }

  // ---------- light Markdown, built as DOM nodes (text is never parsed as HTML) ----------

  const INLINE_RE = new RegExp([
    '`(?<code>[^`\\n]+)`',
    '\\[(?<ltext>[^\\]\\n]+)\\]\\((?<lurl>https?:\\/\\/[^\\s)]+)\\)',
    '\\*\\*(?<bold>[^*\\n]+?)\\*\\*',
    '(?<url>https?:\\/\\/[^\\s<>"\']*[^\\s<>"\'.,;:!?)\\]])',
    '(?<![\\w*])\\*(?<em>[^*\\n]+?)\\*(?![\\w*])',
    '(?<!\\w)_(?<em2>[^_\\n]+?)_(?!\\w)',
  ].join('|'), 'g');

  function inline(text) {
    const out = [];
    let last = 0;
    for (const m of text.matchAll(INLINE_RE)) {
      if (m.index > last) out.push(text.slice(last, m.index));
      const g = m.groups;
      if (g.code !== undefined) out.push(h('code', { text: g.code }));
      else if (g.ltext !== undefined) out.push(extLink(g.lurl, g.ltext));
      else if (g.bold !== undefined) out.push(h('strong', { text: g.bold }));
      else if (g.url !== undefined) out.push(extLink(g.url, g.url));
      else out.push(h('em', { text: g.em ?? g.em2 }));
      last = m.index + m[0].length;
    }
    if (last < text.length) out.push(text.slice(last));
    return out;
  }

  function renderMarkdown(source) {
    const root = h('div', { class: 'md' });
    const lines = String(source ?? '').replace(/\r\n?/g, '\n').split('\n');
    let para = null;
    let list = null;
    let listType = null;
    const endBlock = () => { para = null; list = null; listType = null; };
    const ensureList = (type) => {
      if (listType === type) return;
      para = null;
      list = type === 'ol' ? h('ol') : h('ul', { class: type === 'check' ? 'checklist' : null });
      listType = type;
      root.append(list);
    };

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      let m;
      if (/^\s*```/.test(line)) {
        endBlock();
        const buf = [];
        while (i + 1 < lines.length && !/^\s*```/.test(lines[i + 1])) buf.push(lines[++i]);
        i++; // skip the closing fence (or run past the end)
        root.append(h('pre', null, h('code', { text: buf.join('\n') })));
        continue;
      }
      if (!line.trim()) { endBlock(); continue; }
      if ((m = /^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$/.exec(line))) {
        endBlock();
        root.append(h(m[1].length <= 2 ? 'h4' : 'h5', null, inline(m[2])));
        continue;
      }
      if (/^\s{0,3}([-*_])(\s*\1){2,}\s*$/.test(line)) { endBlock(); root.append(h('hr')); continue; }
      if ((m = /^\s*[-*+]\s+\[([ xX])\]\s*(.*)$/.exec(line))) {
        ensureList('check');
        const done = m[1] !== ' ';
        list.append(h('li', null,
          h('span', { class: `box${done ? ' checked' : ''}`, role: 'img', 'aria-label': done ? 'done' : 'to do', text: done ? '✓' : '' }),
          h('span', null, inline(m[2]))));
        continue;
      }
      if ((m = /^\s*[-*+]\s+(.*)$/.exec(line))) { ensureList('ul'); list.append(h('li', null, inline(m[1]))); continue; }
      if ((m = /^\s*\d+[.)]\s+(.*)$/.exec(line))) { ensureList('ol'); list.append(h('li', null, inline(m[1]))); continue; }
      if ((m = /^\s*>\s?(.*)$/.exec(line))) { endBlock(); root.append(h('blockquote', null, inline(m[1]))); continue; }
      if (list && /^\s{2,}\S/.test(line)) { list.lastElementChild.append(h('br'), ...inline(line.trim())); continue; }

      list = null;
      listType = null;
      if (para) para.append(h('br'));
      else { para = h('p'); root.append(para); }
      para.append(...inline(line));
    }
    return root;
  }

  // ---------- wiring ----------

  function init() {
    buildLanes();
    ui.form.addEventListener('submit', createTicket);
    ui.panelClose.addEventListener('click', closePanel);
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && !ui.panel.hidden) closePanel();
    });
    document.addEventListener('click', (e) => {
      if (ui.panel.hidden || !(e.target instanceof Element)) return;
      if (ui.panel.contains(e.target) || e.target.closest('.card, .toast, .prompt')) return;
      closePanel();
    });
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') poll();
    });
    poll();
  }

  init();
})();
