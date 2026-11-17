/*
 * Parte 66: visualizador da simulação (aba "Visualizador" do caso).
 *
 * Reproduz o registro de eventos gravado pelo simulador (simulation.trace -> <caso>/visualizador/*.json): cada passo
 * é a chegada de uma requisição (com o resultado: rota, modulação, núcleo, slots, SNR/XT, sessão QKD, QChs e PIChs,
 * ou a causa do bloqueio) ou a saída de uma requisição. O estado da rede é refeito no navegador a partir do retrato
 * inicial do registro; para voltar no tempo há um retrato a cada KEY passos.
 */
(function (App) {
  'use strict';

  const { el, clear, svg } = App;
  const KEY = 100;
  const SPEEDS = [1, 2, 5, 10, 20, 50, 100];

  const CAUSE_PT = {
    'Fragmentation': 'fragmentação',
    'QoTN': 'QoT do novo circuito (QoTN)',
    'QoTO': 'QoT dos outros circuitos (QoTO)',
    'Crosstalk': 'crosstalk do novo circuito (XTN)',
    'Crosstalk in other': 'crosstalk nos outros circuitos (XTO)',
    'Lack of transmitters': 'falta de transmissores',
    'Lack of receivers': 'falta de receptores',
    'Lack of free spectrum': 'falta de espectro livre',
    'Free band in a core not tried': 'faixa livre num núcleo não testado',
    'Other': 'outra',
    'QKD session protection': 'proteção das sessões QKD ativas',
    'QKD QCh spectrum': 'espectro do QCh',
    'QKD SKR': 'taxa de chave (SKR)',
    'QKD PICh': 'PICh',
    'QKD QTx/QRx': 'falta de QTx/QRx',
    'QKD other': 'QKD (outra)',
  };
  const causePt = (c) => (c ? CAUSE_PT[c] || c : 'desconhecida');

  // ------------------------------------------------------------------ appearance (saved in this browser)

  const CAUSE_COLORS = {
    'QoTO': '#e34948', 'QoTN': '#eb6834', 'Crosstalk': '#e87ba4', 'Crosstalk in other': '#6250d6',
    'Fragmentation': '#eda100', 'Lack of free spectrum': '#7f7f7f', 'Free band in a core not tried': '#b5a642', 'Other': '#5b6573',
    'Lack of transmitters': '#8c564b', 'Lack of receivers': '#c49c94',
    'QKD session protection': '#1baf7a', 'QKD QCh spectrum': '#0e7490', 'QKD SKR': '#008300', 'QKD PICh': '#e08a00',
    'QKD QTx/QRx': '#2a78d6', 'QKD other': '#3b3b3b',
  };
  const MOD_PALETTE = ['#9ecae1', '#6baed6', '#4292c6', '#2171b5', '#08519c', '#08306b', '#41ab5d', '#fd8d3c'];
  const DEFAULTS = {
    colorMode: 'slots', slotLow: '#9ecae1', slotHigh: '#08254f', single: '#9fb6c4', classical: '#5b9bd5', qkdColor: '#a06cd5',
    modColors: {}, slotColors: {}, cur: '#ffd400', blk: '#d62728', qch: '#7030a0', pich: '#e08a00', victim: '#ff7f0e',
    grid: 'slots', label: 'slots', subSteps: false, rowH: 16, onlyCurrent: false, contrib: true, tlMode: 'cause', causeColors: {},
  };
  const PREF_KEY = 'snets.viz.prefs';
  let P = loadPrefs();

  function loadPrefs() {
    let saved = null;
    try { saved = JSON.parse(localStorage.getItem(PREF_KEY) || 'null'); } catch (e) { saved = null; }
    const p = Object.assign({}, DEFAULTS, saved || {});
    p.modColors = Object.assign({}, (saved && saved.modColors) || {});
    p.causeColors = Object.assign({}, (saved && saved.causeColors) || {});
    p.slotColors = Object.assign({}, (saved && saved.slotColors) || {});
    return p;
  }
  function savePrefs() { try { localStorage.setItem(PREF_KEY, JSON.stringify(P)); } catch (e) { /* ignore */ } }
  const causeColor = (c) => P.causeColors[c] || CAUSE_COLORS[c] || '#d62728';
  function modColor(name) {
    if (P.modColors[name]) return P.modColors[name];
    const mods = (V.data && V.data.modulations) || [];
    const i = Math.max(0, mods.findIndex((m) => m.name === name));
    return MOD_PALETTE[i % MOD_PALETTE.length];
  }
  function hexRgb(h) { const m = /^#?([0-9a-f]{6})$/i.exec(h || ''); const n = m ? parseInt(m[1], 16) : 0; return [n >> 16 & 255, n >> 8 & 255, n & 255]; }
  function mix(a, b, t) { const A = hexRgb(a), B = hexRgb(b); return 'rgb(' + A.map((v, i) => Math.round(v + (B[i] - v) * t)).join(',') + ')'; }
  function textOn(color) {
    const m = /rgb\((\d+),(\d+),(\d+)\)/.exec(color); const c = m ? [+m[1], +m[2], +m[3]] : hexRgb(color);
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) > 150 ? '#111' : '#fff';
  }
  /** Colour of a circuit with n slots: the slot counts seen in the trace are spread evenly over the gradient. */
  function slotColor(n) {
    if (P.slotColors[n]) return P.slotColors[n];
    const vals = V.slotValues || [n];
    let i = vals.indexOf(n);
    if (i < 0) i = vals.filter((v) => v < n).length;
    return mix(P.slotLow, P.slotHigh, vals.length > 1 ? i / (vals.length - 1) : 1);
  }

  /** Fill of an active circuit in the spectrum, by the chosen colour mode. */
  function circuitColor(c) {
    const n = c.band[1] - c.band[0] + 1;
    switch (P.colorMode) {
      case 'slots': return slotColor(n);
      case 'mod': return modColor(c.mod);
      case 'type': return c.q ? P.qkdColor : P.classical;
      case 'single': return P.single;
      default: return 'hsl(' + hue(c.id) + ',45%,58%)';
    }
  }

  // ------------------------------------------------------------------ module state

  const V = {
    casePath: null, files: null, file: null, data: null, error: null, loading: false,
    idx: 0, sub: -1, playing: false, speed: 5, timer: null,
    show: 'all', stopCause: '', stopPair: '', linkSel: 'route', occupancy: true,
    keyframes: null, state: null, stateIdx: -1, steps: null, blockIdx: null,
  };

  // ------------------------------------------------------------------ network state

  function emptyState() {
    return { circ: new Map(), qch: new Map(), pich: new Map(), reqs: new Map(), acc: 0, blk: 0, rel: 0, byCause: {} };
  }

  function cloneState(s) {
    const circ = new Map();
    for (const [k, v] of s.circ) circ.set(k, Object.assign({}, v, { reqs: new Set(v.reqs) }));
    const qch = new Map(); for (const [k, v] of s.qch) qch.set(k, Object.assign({}, v));
    const pich = new Map(); for (const [k, v] of s.pich) pich.set(k, Object.assign({}, v));
    return { circ, qch, pich, reqs: new Map(s.reqs), acc: s.acc, blk: s.blk, rel: s.rel, byCause: Object.assign({}, s.byCause) };
  }

  const routeLinks = (nodes) => { const out = []; for (let i = 0; i + 1 < (nodes || []).length; i++) out.push(nodes[i] + '-' + nodes[i + 1]); return out; };

  function addRequest(s, e) {
    const held = { circuits: [], qch: [], pich: [] };
    for (const c of e.c || []) {
      if (!c.band || c.core === undefined) continue;
      let x = s.circ.get(c.id);
      if (!x) {
        x = { id: c.id, links: routeLinks(c.route), core: c.core, band: c.band, mod: c.mod, reqs: new Set(), n: e.n, q: !!e.q };
        s.circ.set(c.id, x);
      }
      x.reqs.add(e.r);
      held.circuits.push(c.id);
    }
    if (e.qkd) {
      for (const q of e.qkd.qch || []) {
        const key = q.join('|');
        const x = s.qch.get(key) || { link: q[0], core: q[1], band: [q[2], q[3]], n: 0 };
        x.n++; s.qch.set(key, x); held.qch.push(key);
      }
      for (const p of e.qkd.pich || []) {
        const key = [p.link, p.core, p.band[0], p.band[1]].join('|');
        const x = s.pich.get(key) || { id: p.id, link: p.link, core: p.core, band: p.band, mod: p.mod, sections: p.sections, n: 0 };
        x.n++; s.pich.set(key, x); held.pich.push(key);
      }
    }
    s.reqs.set(e.r, held);
  }

  function removeRequest(s, rid) {
    const held = s.reqs.get(rid);
    if (!held) return null;
    s.reqs.delete(rid);
    for (const id of held.circuits) {
      const x = s.circ.get(id);
      if (!x) continue;
      x.reqs.delete(rid);
      if (!x.reqs.size) s.circ.delete(id);
    }
    for (const k of held.qch) { const x = s.qch.get(k); if (x && --x.n <= 0) s.qch.delete(k); }
    for (const k of held.pich) { const x = s.pich.get(k); if (x && --x.n <= 0) s.pich.delete(k); }
    return held;
  }

  function apply(s, e) {
    if (e.k === 'req') {
      if (e.ok) { addRequest(s, e); s.acc++; } else { s.blk++; s.byCause[e.cause || '?'] = (s.byCause[e.cause || '?'] || 0) + 1; }
    } else if (e.k === 'rel') {
      removeRequest(s, e.r); s.rel++;
    }
  }

  /** State after the steps 0..i (i = -1: the initial snapshot). */
  function stateAt(i) {
    if (V.state && V.stateIdx === i) return V.state;
    let s, from;
    if (V.state && V.stateIdx >= 0 && V.stateIdx < i && i - V.stateIdx < KEY) { s = V.state; from = V.stateIdx + 1; } else {
      const k = Math.floor((i + 1) / KEY);
      s = cloneState(V.keyframes[Math.min(k, V.keyframes.length - 1)]);
      from = Math.min(k, V.keyframes.length - 1) * KEY;
    }
    for (let j = from; j <= i; j++) apply(s, V.steps[j]);
    V.state = s; V.stateIdx = i;
    return s;
  }

  function prepare(data) {
    V.data = data;
    V.pos = null;
    V.links = new Map((data.links || []).map((l) => [l.id, l]));
    V.steps = data.events || [];
    const base = emptyState();
    for (const e of data.snapshot || []) addRequest(base, e);
    V.keyframes = [base];
    let s = cloneState(base);
    for (let i = 0; i < V.steps.length; i++) {
      apply(s, V.steps[i]);
      if ((i + 1) % KEY === 0) V.keyframes.push(cloneState(s));
    }
    V.blockIdx = [];
    V.steps.forEach((e, i) => { if (e.k === 'req' && !e.ok) V.blockIdx.push(i); });
    const counts = new Set();
    for (const e of (data.snapshot || []).concat(V.steps)) if (e.k === 'req' && e.ok) for (const c of e.c || []) if (c.band) counts.add(c.band[1] - c.band[0] + 1);
    V.slotValues = [...counts].sort((a, b) => a - b);
    V.slotRange = [V.slotValues[0] || 1, V.slotValues[V.slotValues.length - 1] || 1];
    V.causes = [...new Set(V.steps.filter((e) => e.k === 'req' && !e.ok).map((e) => e.cause || '?'))].sort();
    V.state = null; V.stateIdx = -1;
    V.idx = 0;
  }

  // ------------------------------------------------------------------ files

  async function loadFiles() {
    const st = App.state;
    V.casePath = st.casePath;
    V.files = null; V.error = null;
    try {
      const list = await App.api.files(st.casePath + '/visualizador');
      V.files = (list || []).filter((f) => !f.dir && /\.json$/i.test(f.name)).sort((a, b) => b.mtime - a.mtime);
    } catch (e) {
      V.files = [];
    }
    if (V.files.length && (!V.file || !V.files.some((f) => f.name === V.file))) await openFile(V.files[0].name, true);
    App.renderTab();
  }

  async function openFile(name, quiet) {
    stop();
    V.loading = true; V.error = null; V.file = name;
    if (!quiet) App.renderTab();
    try {
      prepare(await App.api.trace(App.state.casePath + '/visualizador/' + name)); // Parte 68: any size
    } catch (e) {
      V.error = 'Não foi possível abrir ' + name + ': ' + e.message;
      V.data = null;
    }
    V.loading = false;
    if (!quiet) App.renderTab();
  }

  // ------------------------------------------------------------------ navigation

  function visible(e) {
    if (V.show === 'req') return e.k === 'req';
    if (V.show === 'blocked') return e.k === 'req' && !e.ok;
    if (V.show === 'qkd') return e.k === 'req' && e.q;
    return true;
  }

  function matchesStop(e) {
    if (e.k !== 'req') return false;
    if (V.stopCause && !(!e.ok && (V.stopCause === '*' || e.cause === V.stopCause))) return false;
    if (V.stopPair) {
      const [a, b] = V.stopPair.split(/[\s>-]+/).filter(Boolean);
      if (!(String(e.s) === a && (!b || String(e.d) === b))) return false;
    }
    return !!(V.stopCause || V.stopPair);
  }

  function go(i, sub) {
    const n = V.steps.length;
    V.idx = Math.max(0, Math.min(n - 1, i));
    V.sub = sub === undefined ? -1 : sub;
    draw();
  }

  const attemptsOf = (e) => (e && e.k === 'req' && Array.isArray(e.a) ? e.a : []);

  /**
   * One step forward or back. With "passos internos" on, an arrival is shown as its internal attempts (sub 0..n-1)
   * followed by its result (sub = -1).
   */
  function stepBy(dir) {
    const a = attemptsOf(V.steps[V.idx]);
    if (P.subSteps && a.length) {
      if (dir > 0 && V.sub >= 0) { go(V.idx, V.sub + 1 < a.length ? V.sub + 1 : -1); return true; }
      if (dir < 0 && V.sub > 0) { go(V.idx, V.sub - 1); return true; }
      if (dir < 0 && V.sub === -1) { go(V.idx, a.length - 1); return true; }
    }
    let i = V.idx + dir;
    while (i >= 0 && i < V.steps.length && !visible(V.steps[i])) i += dir;
    if (i < 0 || i >= V.steps.length) return false;
    const b = attemptsOf(V.steps[i]);
    go(i, P.subSteps && b.length && dir > 0 ? 0 : -1);
    return true;
  }

  function nextBlock(dir) {
    const list = V.blockIdx;
    const i = dir > 0 ? list.find((x) => x > V.idx) : [...list].reverse().find((x) => x < V.idx);
    if (i !== undefined) go(i); else App.toast(dir > 0 ? 'Não há mais bloqueios no registro.' : 'Não há bloqueio antes deste passo.', 'warn');
  }

  function play() {
    if (V.playing) return stop();
    V.playing = true;
    tick();
    syncPlay();
  }

  function tick() {
    if (!V.playing) return;
    const moved = stepBy(1);
    if (!moved) { stop(); return; }
    if (matchesStop(V.steps[V.idx])) { stop(); App.toast('Parado: o passo atende à condição de parada.', 'ok'); return; }
    V.timer = setTimeout(tick, V.speed >= 100 ? 0 : 1000 / V.speed);
  }

  function stop() {
    V.playing = false;
    if (V.timer) clearTimeout(V.timer);
    V.timer = null;
    syncPlay();
  }

  function syncPlay() {
    const b = document.getElementById('vizPlay');
    if (b) b.textContent = V.playing ? '❚❚ Pausar' : '▶ Reproduzir';
  }

  // ------------------------------------------------------------------ rendering

  let ui = null; // nodes redrawn at every step
  let ui_timeline = null;

  App.viz = {
    /** The list of traces is read again on the next render (after a run). */
    invalidate() { if (!V.playing) V.files = null; },
    render() {
      const st = App.state;
      if (V.casePath !== st.casePath) { stop(); V.file = null; V.data = null; V.files = null; V.casePath = st.casePath; }
      const root = el('div.stack.viz');
      root.append(configPanel());
      if (V.files === null) { loadFiles(); root.append(el('section.panel', el('p.lead', 'Procurando registros na pasta visualizador do caso…'))); return root; }
      root.append(filesPanel());
      if (V.loading) root.append(el('section.panel', el('p.lead', 'Abrindo o registro…')));
      else if (V.error) root.append(el('div.notice.err', V.error));
      else if (V.data) root.append(viewer());
      return root;
    },
  };

  function configPanel() {
    const st = App.state;
    const sim = st.model.simulation || {};
    const t = Object.assign({ enabled: false, loadPoint: 0, replication: 0, firstRequest: 0, maxRequests: 2000, internalAttempts: true, maxAttemptsPerRequest: 300 }, sim.trace || {});
    const set = (k, v) => { App.setValue('simulation', ['trace', k], v); };
    const ensure = () => { if (!App.isPlainObject(sim.trace)) App.setValue('simulation', ['trace'], Object.assign({}, t)); };
    const num = (k, label, help, min) => {
      const i = el('input.input.num', { type: 'text', value: String(t[k]) });
      i.addEventListener('change', () => {
        const v = App.parseNumber(i.value);
        const ok = Number.isInteger(v) && v >= min;
        i.classList.toggle('invalid', !ok);
        if (ok) { ensure(); set(k, v); }
      });
      return el('label.field', el('div.flabel', el('span', label)), i, help ? el('div.help', help) : null);
    };
    const cb = el('input', { type: 'checkbox', checked: !!t.enabled });
    cb.addEventListener('change', () => { ensure(); set('enabled', cb.checked); App.renderTab(); });
    const loads = Number(sim.loadPoints) || 1, reps = Number(sim.replications) || 1;
    return el('details.group.panel', { open: !V.data },
      el('summary', el('strong', 'Gravar registro para o visualizador')),
      el('p.lead', 'Cada execução do caso roda várias simulações (uma por ponto de carga × replicação). Com o registro ligado, só UMA delas é gravada: '
        + 'a do ponto de carga e da replicação escolhidos abaixo. Dentro dela são gravadas as requisições a partir da "primeira requisição", '
        + 'na quantidade escolhida, com as saídas que acontecem nesse intervalo. Os resultados não mudam. Salve em "Revisar e salvar" antes de executar.'),
      el('label.check', cb, ' Gravar o registro na próxima execução (simulation.trace.enabled)'),
      el('div.fields',
        num('loadPoint', 'Ponto de carga gravado (só este)', 'índice do ponto de carga: 0 = primeiro, ' + (loads - 1) + ' = último', 0),
        num('replication', 'Replicação gravada (só esta)', 'índice da replicação: 0 a ' + (reps - 1), 0),
        num('firstRequest', 'Primeira requisição gravada', 'número da chegada nessa simulação, contando do início (transitório incluído). '
          + 'Ex.: 5000 começa depois de 5 000 chegadas; os circuitos que já estão na rede entram como estado inicial', 0),
        num('maxRequests', 'Quantas requisições gravar', 'a partir da primeira; até 50 000 (~0,6 KB cada; ~2–3 KB com as tentativas internas)', 1),
        num('maxAttemptsPerRequest', 'Tentativas internas por requisição (máx.)', 'as que passarem disso só são contadas', 1)),
      (() => {
        const c = el('input', { type: 'checkbox', checked: t.internalAttempts !== false });
        c.addEventListener('change', () => { ensure(); set('internalAttempts', c.checked); });
        return el('label.check', c, ' Gravar também as tentativas internas dos algoritmos (rotas, modulações, núcleos e faixas testados e o resultado de cada teste)');
      })(),
      t.enabled && t.loadPoint < loads && t.replication < reps ? el('div.notice.info', 'Será gravado: ponto de carga ' + t.loadPoint + ', replicação ' + t.replication
        + ', requisições ' + t.firstRequest + ' a ' + (t.firstRequest + t.maxRequests - 1) + ' (se a simulação tiver ' + (t.firstRequest + t.maxRequests) + ' chegadas ou mais; requests do caso: ' + (sim.requests || '?') + ').') : null,
      t.enabled && (t.loadPoint >= loads || t.replication >= reps) ? el('div.notice.warn', 'Ponto de carga ou replicação fora do que o caso simula: nada será gravado.') : null);
  }

  function filesPanel() {
    if (!V.files.length) {
      return el('section.panel', el('h2', 'Registros'), el('p.lead', 'Nenhum registro em visualizador/. Ligue a gravação acima, salve e execute o caso.'),
        el('button.btn.small', { type: 'button', on: { click: () => { V.files = null; App.renderTab(); } } }, 'Procurar de novo'));
    }
    const sel = el('select.input', V.files.map((f) => el('option', { value: f.name, selected: f.name === V.file }, f.name + ' · ' + App.fmtBytes(f.size) + ' · ' + App.fmtDate(f.mtime))));
    sel.addEventListener('change', () => openFile(sel.value));
    return el('section.panel.viz-files',
      el('div.viz-row', el('strong', 'Registro'), sel,
        el('button.btn.small', { type: 'button', on: { click: () => { V.files = null; App.renderTab(); } } }, 'Atualizar lista')),
      V.data ? el('div.small.muted', describe(V.data)) : null);
  }

  function describe(d) {
    const nReq = (d.events || []).filter((e) => e.k === 'req').length;
    return 'Carga ' + d.loadPoint + ' · replicação ' + d.replication + ' · requisições ' + d.firstRequest + ' a ' + (d.firstRequest + nReq - 1)
      + ' · ' + (d.events || []).length + ' passos · ' + (d.snapshot || []).length + ' requisições já na rede no início'
      + (d.endReason === 'simulation end' ? ' · a simulação terminou antes do fim da janela' : '');
  }

  function viewer() {
    const box = el('section.panel.viz-main');
    const slider = el('input.viz-slider', { type: 'range', min: 0, max: Math.max(0, V.steps.length - 1), value: V.idx });
    slider.addEventListener('input', () => { stop(); go(Number(slider.value)); });
    const showSel = el('select.input.small', [['all', 'todos os passos'], ['req', 'só chegadas'], ['blocked', 'só bloqueios'], ['qkd', 'só requisições QKD']]
      .map(([v, l]) => el('option', { value: v, selected: V.show === v }, l)));
    showSel.addEventListener('change', () => { V.show = showSel.value; });
    const speedSel = el('select.input.small', SPEEDS.map((v) => el('option', { value: v, selected: V.speed === v }, v + ' passos/s')));
    speedSel.addEventListener('change', () => { V.speed = Number(speedSel.value); });
    const causeSel = el('select.input.small', [['', 'sem parada por causa'], ['*', 'qualquer bloqueio']].concat(V.causes.map((c) => [c, causePt(c)]))
      .map(([v, l]) => el('option', { value: v, selected: V.stopCause === v }, l)));
    causeSel.addEventListener('change', () => { V.stopCause = causeSel.value; });
    const pairIn = el('input.input.small', { type: 'text', value: V.stopPair, placeholder: 'par, ex.: 5-12', style: { width: '9em' } });
    pairIn.addEventListener('change', () => { V.stopPair = pairIn.value.trim(); });
    const subCb = el('input', { type: 'checkbox', checked: !!P.subSteps });
    subCb.addEventListener('change', () => { P.subSteps = subCb.checked; savePrefs(); if (!P.subSteps) V.sub = -1; draw(); });
    const subToggle = el('label.check.small', { title: 'Avança também pelas tentativas internas dos algoritmos em cada chegada' }, subCb, ' passos internos');
    const goIn = el('input.input.small', { type: 'text', placeholder: V.data.firstRequest, style: { width: '6em' } });
    goIn.addEventListener('change', () => {
      const n = App.parseNumber(goIn.value);
      const i = V.steps.findIndex((x) => x.k === 'req' && x.n === n);
      if (i >= 0) { stop(); go(i); } else App.toast('A requisição ' + goIn.value + ' não está no registro.', 'warn');
    });
    const btn = (label, title, fn, id) => el('button.btn.small', { type: 'button', title, id, on: { click: fn } }, label);
    const controls = el('div.viz-controls',
      btn('⏮', 'Início do registro', () => { stop(); go(0); }),
      btn('◀', 'Passo anterior (seta esquerda)', () => { stop(); stepBy(-1); }),
      btn(V.playing ? '❚❚ Pausar' : '▶ Reproduzir', 'Reproduzir ou pausar (espaço)', play, 'vizPlay'),
      btn('▶', 'Próximo passo (seta direita)', () => { stop(); stepBy(1); }),
      btn('⏭', 'Fim do registro', () => { stop(); go(V.steps.length - 1); }),
      btn('⟸ bloqueio', 'Bloqueio anterior', () => { stop(); nextBlock(-1); }),
      btn('bloqueio ⟹', 'Próximo bloqueio', () => { stop(); nextBlock(1); }),
      el('span.viz-sep'), el('span.small.muted', 'Mostrar'), showSel, speedSel,
      el('span.viz-sep'), el('span.small.muted', 'Parar em'), causeSel, pairIn,
      el('span.viz-sep'), el('span.small.muted', 'Ir para a requisição nº'), goIn,
      el('span.viz-sep'), subToggle);
    const timeline = el('div.viz-tlbox');
    ui_timeline = timeline;
    ui = { slider, header: el('div.viz-header'), topo: el('div.viz-topo'), detail: el('div.viz-detail'), cores: el('div.viz-cores'),
      log: el('div.viz-log'), counters: el('div.viz-counters'), spectrum: el('div.viz-spectrum'), linkSel: null };
    const linkSel = el('select.input.small');
    linkSel.addEventListener('change', () => { V.linkSel = linkSel.value; draw(); });
    ui.linkSel = linkSel;
    const occ = el('input', { type: 'checkbox', checked: V.occupancy });
    occ.addEventListener('change', () => { V.occupancy = occ.checked; draw(); });
    const tg = (key, label, title) => {
      const c = el('input', { type: 'checkbox', checked: !!P[key] });
      c.addEventListener('change', () => { P[key] = c.checked; savePrefs(); draw(); });
      return el('label.check.small', { title }, c, ' ' + label);
    };
    ui.legend = el('span.viz-legend');
    box.append(controls, timeline, slider, ui.header,
      el('div.viz-grid',
        el('div.viz-left', ui.topo, el('label.check.small', occ, ' cor dos enlaces = ocupação dos núcleos clássicos')),
        el('div.viz-right', ui.detail, ui.cores, el('h3', 'Log'), ui.log)),
      ui.counters,
      el('div.viz-row', el('h3', 'Espectro'), el('span.small.muted', 'Enlaces:'), linkSel,
        tg('onlyCurrent', 'só a requisição atual', 'Esconde os outros circuitos; mostra a requisição do passo, a tentativa bloqueada e o que sai'),
        tg('contrib', 'destacar quem contribuiu para o bloqueio', 'Nos bloqueios: realça os circuitos ativos que podem ter causado a recusa e apaga os outros')),
      el('div.viz-row', ui.legend),
      ui.spectrum,
      appearancePanel(),
      exportPanel());
    setTimeout(() => { drawTimeline(); draw(); }, 0);
    return box;
  }

  function drawTimeline() {
    if (!ui_timeline) return;
    const n = Math.max(1, V.steps.length);
    const W = 1000;
    const s = svg('svg', { class: 'viz-timeline', viewBox: '0 0 ' + W + ' 14', preserveAspectRatio: 'none' });
    s.append(svg('rect', { x: 0, y: 5, width: W, height: 4, fill: 'var(--surface-3)' }));
    for (const i of V.blockIdx) {
      const c = P.tlMode === 'cause' ? causeColor(V.steps[i].cause) : P.blk;
      s.append(svg('rect', { x: (i / n) * W, y: 1, width: Math.max(1, W / n), height: 12, fill: c, opacity: 0.9 }));
    }
    s.addEventListener('click', (ev) => {
      const r = s.getBoundingClientRect();
      stop(); go(Math.round(((ev.clientX - r.left) / r.width) * (n - 1)));
    });
    s.append(svg('title', { text: 'Marcas: bloqueios. Clique para ir ao ponto.' }));
    const leg = P.tlMode === 'cause' && V.causes.length
      ? el('div.viz-tllegend', V.causes.map((c) => el('span.viz-leg', el('span.viz-sw', { style: { background: causeColor(c) } }),
        causePt(c) + ' (' + V.steps.filter((x) => x.k === 'req' && !x.ok && x.cause === c).length + ')')))
      : null;
    clear(ui_timeline).append(s, leg);
  }

  function drawLegend() {
    if (!ui.legend) return;
    const item = (style, label) => el('span.viz-leg', el('span.viz-sw', { style }), label);
    const items = [];
    if (P.colorMode === 'slots') {
      items.push(el('span.viz-leg', 'slots por circuito:'), ...(V.slotValues || []).map((n) => item({ background: slotColor(n) }, String(n))));
    } else if (P.colorMode === 'mod') {
      for (const m of (V.data.modulations || [])) items.push(item({ background: modColor(m.name) }, m.name));
    } else if (P.colorMode === 'type') {
      items.push(item({ background: P.classical }, 'circuito de requisição clássica'), item({ background: P.qkdColor }, 'circuito de requisição QKD'));
    } else if (P.colorMode === 'single') {
      items.push(item({ background: P.single }, 'outros circuitos'));
    } else items.push(item({ background: 'linear-gradient(90deg,hsl(0,45%,58%),hsl(120,45%,58%),hsl(240,45%,58%))' }, 'uma cor por circuito'));
    items.push(item({ background: P.cur, outline: '1px solid #000' }, 'requisição atual'), item({ border: '1.5px dashed #6b6b6b', background: 'transparent' }, 'saindo'),
      item({ background: P.blk }, 'tentativa bloqueada'), item({ background: 'transparent', outline: '2px solid ' + P.victim }, 'vítima (QoTO/XTO)'),
      item({ background: P.qch }, 'QCh'), item({ background: P.pich }, 'PICh'));
    clear(ui.legend).append(items);
  }

  function appearancePanel() {
    const box = el('details.group.viz-appearance');
    const redraw = () => { savePrefs(); drawTimeline(); draw(); };
    const color = (obj, key, label, def) => {
      const i = el('input.viz-color', { type: 'color', value: (obj[key] || def || '#000000') });
      i.addEventListener('input', () => { obj[key] = i.value; redraw(); });
      return el('label.viz-colorfield', i, label);
    };
    const select = (key, label, options, after) => {
      const sl = el('select.input.small', options.map(([v, l]) => el('option', { value: v, selected: String(P[key]) === String(v) }, l)));
      sl.addEventListener('change', () => { P[key] = typeof DEFAULTS[key] === 'number' ? Number(sl.value) : sl.value; savePrefs(); if (after) after(); else redraw(); });
      return el('label.field', el('div.flabel', el('span', label)), sl);
    };
    const body = el('div.viz-app-body');
    const fill = () => {
      clear(body);
      const modeColors = el('div.viz-colors');
      if (P.colorMode === 'slots') {
        modeColors.append(color(P, 'slotLow', 'início do degradê (menos slots)'), color(P, 'slotHigh', 'fim do degradê (mais slots)'));
        const own = el('div.viz-colors', (V.slotValues || []).map((n) => {
          const c = slotColor(n);
          const hex = /^#/.test(c) ? c : '#' + c.replace(/[^\d,]/g, '').split(',').map((v) => (+v).toString(16).padStart(2, '0')).join('');
          return color(P.slotColors, String(n), n + (n === 1 ? ' slot' : ' slots'), hex);
        }));
        modeColors.append(el('div.small.muted', { style: { flexBasis: '100%' } }, 'Ou escolha a cor de cada quantidade de slots (sobrepõe o degradê):'), own);
      }
      else if (P.colorMode === 'mod') modeColors.append(...(V.data.modulations || []).map((m) => color(P.modColors, m.name, m.name, modColor(m.name))));
      else if (P.colorMode === 'type') modeColors.append(color(P, 'classical', 'clássica'), color(P, 'qkdColor', 'QKD'));
      else if (P.colorMode === 'single') modeColors.append(color(P, 'single', 'outros circuitos'));
      const causeColors = el('div.viz-colors', V.causes.map((c) => color(P.causeColors, c, causePt(c), causeColor(c))));
      body.append(
        el('div.fields',
          select('colorMode', 'Cor dos circuitos', [['slots', 'pelo número de slots (degradê)'], ['mod', 'pela modulação'], ['type', 'clássica ou QKD'],
            ['circuit', 'uma cor por circuito'], ['single', 'uma cor só']], () => { fill(); redraw(); }),
          select('label', 'Texto nos circuitos', [['none', 'nenhum'], ['slots', 'nº de slots'], ['req', 'nº da requisição'], ['mod', 'modulação']]),
          select('grid', 'Grade dos slots', [['none', 'sem grade'], ['slots', 'uma linha por slot'], ['10', 'uma linha a cada 10 slots']]),
          select('rowH', 'Altura de cada núcleo', [[12, 'pequena'], [16, 'média'], [22, 'grande'], [30, 'muito grande']]),
          select('tlMode', 'Bloqueios na linha do tempo', [['single', 'uma cor só'], ['cause', 'uma cor por causa']], () => { fill(); redraw(); })),
        el('h4', 'Cores dos circuitos'), modeColors,
        el('h4', 'Cores fixas'), el('div.viz-colors', color(P, 'cur', 'requisição atual'), color(P, 'blk', 'tentativa bloqueada' + (P.tlMode === 'single' ? ' e bloqueios na linha do tempo' : '')),
          color(P, 'victim', 'vítima (QoTO/XTO)'), color(P, 'qch', 'QCh'), color(P, 'pich', 'PICh')),
        P.tlMode === 'cause' && V.causes.length ? el('h4', 'Cores das causas de bloqueio') : null,
        P.tlMode === 'cause' && V.causes.length ? causeColors : null,
        el('div.viz-row', el('button.btn.small', { type: 'button', on: { click: () => {
          P = Object.assign({}, DEFAULTS, { modColors: {}, causeColors: {}, slotColors: {} }); savePrefs(); fill(); drawTimeline(); draw();
        } } }, 'Restaurar o padrão'), el('span.small.muted', 'As escolhas ficam gravadas neste navegador.')));
    };
    fill();
    box.append(el('summary', el('strong', 'Aparência do visualizador')), body);
    return box;
  }

  // ------------------------------------------------------------------ internal attempts

  const KIND_PT = {
    QoT: 'QoT do candidato', XT: 'crosstalk do candidato', QoTO: 'QoT dos outros circuitos', XTO: 'crosstalk nos outros circuitos',
    QKD: 'admissão QKD (QCh, SKR, PICh)', PROTECT: 'proteção das sessões QKD ativas', PICh: 'posição de PICh testada', QCh: 'QChs avaliados',
  };
  const METHOD_PT = {
    findRoute: 'roteamento', assignCoreAndSpectrum: 'núcleo e espectro', assignSpectrum: 'espectro', selectModulation: 'modulação',
    rsa: 'RMCSA integrado (resultado)', searchCircuitsForGrooming: 'grooming', computeRoutes: 'k rotas', findRoutes: 'k rotas',
  };
  const WHY_PT = { QoT: 'QoT do PICh', classical: 'interferência nos circuitos clássicos', qch: 'interferência nos QChs', spectrum: 'espectro' };

  function candText(x) {
    const parts = [];
    if (x.route) parts.push(x.route.join('→'));
    if (x.link) parts.push(x.link);
    if (x.mod) parts.push(x.mod);
    if (x.core !== undefined) parts.push('núcleo ' + x.core);
    if (x.band) parts.push('slots ' + (x.band[0] === x.band[1] ? x.band[0] : x.band[0] + '–' + x.band[1]));
    return parts.join(' · ');
  }

  function attemptText(x) {
    let what, res = '';
    if (x.k === 'call') {
      what = (METHOD_PT[x.m] || x.m) + ' (' + x.c + ')';
      if (x.routes) res = x.routes.length + ' rota(s): ' + x.routes.map((r) => r.join('→')).join(' | ');
      else if (x.res) res = '→ ' + x.res;
      else if (x.ok !== undefined) res = x.ok ? (x.band || x.route ? '' : 'ok') : (x.m === 'rsa' ? 'sem solução aceita' : 'não encontrou');
      if (x.m === 'assignCoreAndSpectrum' && x.ok !== false && !x.band) res = 'sem faixa livre';
      if (x.slots !== undefined) what += ' para ' + x.slots + ' slot(s)';
    } else if (x.k === 'QCh') {
      what = KIND_PT.QCh; res = x.n + ' faixa(s): ' + (x.bands || []).slice(0, 6).map((b) => b[0] + ' n' + b[1] + ' ' + b[2] + (b[3] !== b[2] ? '–' + b[3] : '')).join(', ') + (x.n > 6 ? '…' : '');
    } else {
      what = KIND_PT[x.k] || x.k;
      if (x.v !== undefined) res = (x.k === 'XT' || x.k === 'XTO') ? (x.v <= -190 ? 'sem XT' : App.fmtNum(x.v, 2) + ' dB') : App.fmtNum(x.v, 2) + ' dB';
      if (x.why) res += (res ? ' · ' : '') + WHY_PT[x.why];
      if (x.msg) res += (res ? ' · ' : '') + x.msg;
      if (x.victim !== undefined) res += (res ? ' · ' : '') + 'vítima: ' + (x.victim < 0 ? 'PICh' : 'circuito ' + x.victim);
    }
    const mark = x.ok === undefined ? '·' : x.ok ? '✓' : '✗';
    return { mark, what, cand: x.k === 'QCh' ? '' : candText(x), res, diag: !!x.diag, ok: x.ok };
  }

  // ------------------------------------------------------------------ one step

  function draw() {
    if (!ui || !V.data || !document.body.contains(ui.slider)) return;
    const e = V.steps[V.idx];
    if (!e) return;
    const before = stateAt(V.idx - 1);
    const s = before === V.state ? cloneState(before) : before; // keep the cached state at idx-1
    const prevHeld = e.k === 'rel' ? (s.reqs.get(e.r) || null) : null;
    const relCircuits = prevHeld ? prevHeld.circuits.map((id) => s.circ.get(id)).filter(Boolean) : [];
    const after = cloneState(s); apply(after, e);
    const trial = trialAt(e, V.sub);
    // during an internal attempt the network is still as before the arrival
    const shown = trial ? s : after;
    const contrib = trial ? (trial.step.victim !== undefined ? { circ: new Set(), pich: new Set(), victim: trial.step.victim, why: 'vítima deste teste' } : null)
      : P.contrib ? contributors(e, after) : null;
    ui.slider.value = V.idx;
    drawHeader(e, trial);
    drawLegend();
    drawTopo(e, shown, relCircuits, contrib, trial);
    drawDetail(e, relCircuits, prevHeld, contrib, trial);
    drawCores(e, trial);
    drawLog();
    drawCounters(shown);
    drawSpectrum(e, shown, relCircuits, contrib, trial);
  }

  /** The internal attempt number sub of the arrival e, with the candidate (route, modulation, core, band) at that point. */
  function trialAt(e, sub) {
    const a = attemptsOf(e);
    if (sub < 0 || sub >= a.length) return null;
    const cand = {};
    for (let i = 0; i <= sub; i++) {
      const x = a[i];
      for (const k of ['route', 'mod', 'core', 'band']) if (x[k] !== undefined) cand[k] = x[k];
      if (x.link && x.k === 'PICh') { cand.pichLink = x.link; } else if (x.k !== 'PICh') cand.pichLink = undefined;
    }
    return { step: a[sub], cand, k: sub, n: a.length };
  }

  /**
   * Active circuits (and PIChs) that may have caused a block, from the cause and the attempt:
   * QoT (QoTN/QoTO): circuits of the same core on a link of the route (nonlinear interference);
   * crosstalk (XT/XTO): circuits of an adjacent core on a link of the route that overlap the attempted slots;
   * spectrum, fragmentation and the others: every circuit on the links of the route.
   * The victim of QoTO/XTO (the circuit the simulator found failing) is recorded in the trace.
   */
  function contributors(e, s) {
    if (e.k !== 'req' || e.ok) return null;
    const c = (e.c || [])[0];
    if (!c || !c.route) return null;
    const L = new Set(routeLinks(c.route));
    const cause = e.cause || '';
    const qot = /QoT/.test(cause), xt = /Crosstalk/.test(cause);
    const overlap = (b) => c.band && b[0] <= c.band[1] && b[1] >= c.band[0];
    const adj = (lid) => { const l = V.links.get(lid); const k = l && l.cores.find((x) => x.id === c.core); return new Set(k ? k.adj : []); };
    const hits = (links, core, band) => links.some((lid) => {
      if (!L.has(lid)) return false;
      if (qot) return core === c.core;
      if (xt) return adj(lid).has(core) && overlap(band);
      return true;
    });
    const circ = new Set(), pich = new Set();
    for (const x of s.circ.values()) if (hits(x.links, x.core, x.band)) circ.add(x.id);
    for (const [k, p] of s.pich) if (hits([p.link], p.core, p.band)) pich.add(k);
    const victim = e.victim ? e.victim.id : null;
    const why = qot ? 'circuitos no mesmo núcleo (' + c.core + ') nos enlaces da rota: interferência não linear'
      : xt ? 'circuitos dos núcleos vizinhos do ' + c.core + ' nos enlaces da rota, sobrepostos aos slots tentados: crosstalk'
        : 'todos os circuitos nos enlaces da rota (ocupação do espectro)';
    return { circ, pich, victim, why };
  }

  function reqTitle(e) {
    return (e.q ? 'QKD' : 'Clássica') + ' · ' + e.s + ' → ' + e.d + ' · ' + App.fmtNum(e.gbps) + ' Gb/s' + (e.q ? ' · chave ' + App.fmtNum(e.keyMbps) + ' Mb/s' : '');
  }

  function findReq(rid) {
    for (let i = V.idx; i >= 0; i--) { const x = V.steps[i]; if (x.k === 'req' && x.r === rid) return x; }
    return (V.data.snapshot || []).find((x) => x.r === rid) || null;
  }

  function drawHeader(e, trial) {
    const d = V.data;
    const pos = 'Passo ' + (V.idx + 1) + ' de ' + V.steps.length + ' · t = ' + App.fmtNum(e.t, 4) + ' h';
    let main;
    if (e.k === 'req') {
      main = el('div.viz-req', el('strong', 'Requisição ' + e.n), e.try ? ' (tentativa ' + e.try + ' desta requisição)' : '', ' · ', reqTitle(e), ' · ponto de carga ' + d.loadPoint + ' ',
        e.ok ? el('span.viz-chip.ok', 'aceita') : el('span.viz-chip.err', 'bloqueada: ' + causePt(e.cause)));
    } else {
      const r = findReq(e.r);
      main = el('div.viz-req', el('strong', 'Saída'), ' da requisição ' + (r && r.n !== undefined ? r.n : '(anterior ao registro)') + (r ? ' · ' + reqTitle(r) : ''),
        ' ', el('span.viz-chip', 'recursos liberados'));
    }
    const tr = trial ? attemptText(trial.step) : null;
    clear(ui.header).append(main, el('div.small.muted', pos),
      tr ? el('div.viz-trialbar' + (tr.ok === false ? '.fail' : tr.ok ? '.ok' : ''), el('strong', 'Passo interno ' + (trial.k + 1) + ' de ' + trial.n + ': '),
        tr.mark + ' ' + tr.what + (tr.cand ? ' — ' + tr.cand : '') + (tr.res ? ' — ' + tr.res : '') + (tr.diag ? ' (diagnóstico da causa do bloqueio)' : '')) : null);
  }

  function nodePositions() {
    if (V.pos) return V.pos;
    const net = App.state.model.network;
    const names = new Set(); for (const l of V.data.links) { names.add(l.a); names.add(l.b); }
    let pos = null;
    try {
      const c = net ? App.presets.coordsFor(net).pos : null;
      if (c && [...names].every((n) => c.has(n))) pos = c;
    } catch (e) { pos = null; }
    if (!pos) {
      const fake = { nodes: [...names].map((n) => ({ name: n })), links: V.data.links.map((l) => ({ source: l.a, destination: l.b, size: l.km })) };
      pos = App.presets.coordsFor(fake).pos;
    }
    V.pos = pos;
    return pos;
  }

  function linkUse(s, id) {
    const l = V.links.get(id);
    if (!l) return NaN;
    let used = 0, tot = 0;
    for (const c of l.cores) { if (c.qkd || c.off) continue; tot += c.slots; }
    for (const x of s.circ.values()) if (x.links.includes(id)) used += x.band[1] - x.band[0] + 1;
    for (const x of s.pich.values()) if (x.link === id) used += x.band[1] - x.band[0] + 1;
    return tot ? used / tot : NaN;
  }

  function drawTopo(e, s, relCircuits, contrib, trial) {
    const pos = nodePositions();
    const W = 560, H = 380, pad = 26;
    const pts = [...pos.values()];
    const x0 = Math.min(...pts.map((p) => p.x)), x1 = Math.max(...pts.map((p) => p.x));
    const y0 = Math.min(...pts.map((p) => p.y)), y1 = Math.max(...pts.map((p) => p.y));
    const sc = Math.min((W - 2 * pad) / Math.max(1e-9, x1 - x0), (H - 2 * pad) / Math.max(1e-9, y1 - y0));
    const XY = (n) => { const p = pos.get(n); return { x: pad + (p.x - x0) * sc + ((W - 2 * pad) - (x1 - x0) * sc) / 2, y: pad + (p.y - y0) * sc + ((H - 2 * pad) - (y1 - y0) * sc) / 2 }; };
    const root = svg('svg', { viewBox: '0 0 ' + W + ' ' + H, class: 'viz-svg' });
    const pairs = new Map();
    for (const l of V.data.links) {
      const k = App.presets.pairKey(l.a, l.b);
      const u = linkUse(s, l.id);
      const p = pairs.get(k) || { a: l.a, b: l.b, km: l.km, use: 0 };
      if (Number.isFinite(u)) p.use = Math.max(p.use, u);
      pairs.set(k, p);
    }
    for (const p of pairs.values()) {
      const A = XY(p.a), B = XY(p.b);
      const col = V.occupancy ? App.presets.heatColor(p.use) : 'var(--border-strong)';
      root.append(svg('line', { x1: A.x, y1: A.y, x2: B.x, y2: B.y, stroke: col, 'stroke-width': 4, 'stroke-linecap': 'round' }, svg('title', { text: p.a + '–' + p.b + ' · ' + App.fmtNum(p.km) + ' km · ocupação ' + Math.round(p.use * 100) + '%' })));
      root.append(svg('text', { x: (A.x + B.x) / 2, y: (A.y + B.y) / 2 - 5, 'text-anchor': 'middle', class: 'viz-km', text: App.fmtNum(p.km) }));
    }
    const req = e.k === 'req' ? e : findReq(e.r);
    const routes = [];
    if (trial) {
      if (trial.cand.route) routes.push({ nodes: trial.cand.route, cls: 'viz-route try' + (trial.step.ok === false ? ' fail' : '') });
      else if (trial.cand.pichLink) routes.push({ nodes: trial.cand.pichLink.split('-'), cls: 'viz-route try' + (trial.step.ok === false ? ' fail' : '') });
    } else if (e.k === 'req') {
      for (const c of e.c || []) if (c.route) routes.push({ nodes: c.route, cls: e.ok ? 'viz-route' : 'viz-route blocked' });
      if (e.qkd && e.qkd.route) routes.push({ nodes: e.qkd.route, cls: 'viz-route qkd' });
    } else {
      for (const c of relCircuits) routes.push({ nodes: linkNodes(c.links), cls: 'viz-route rel' });
    }
    if (contrib && contrib.victim !== null) {
      const v = s.circ.get(contrib.victim);
      if (v) routes.push({ nodes: linkNodes(v.links), cls: 'viz-route victim', style: 'stroke:' + P.victim });
    }
    for (const r of routes) {
      const d = r.nodes.map((n, i) => (i ? 'L' : 'M') + XY(n).x.toFixed(1) + ' ' + XY(n).y.toFixed(1)).join(' ');
      root.append(svg('path', { d, class: r.cls, fill: 'none', style: r.style }));
    }
    // trusted nodes of the QKD route (span ends inside the links)
    const qk = !trial && e.k === 'req' && e.ok && e.qkd ? e.qkd : null;
    if (qk && qk.spans) {
      for (const [lid, , end] of qk.spans) {
        const l = V.links.get(lid);
        if (!l || end >= l.km - 1e-6) continue;
        const A = XY(l.a), B = XY(l.b), f = end / l.km;
        root.append(svg('circle', { cx: A.x + (B.x - A.x) * f, cy: A.y + (B.y - A.y) * f, r: 3.5, class: 'viz-trusted' }, svg('title', { text: 'nó confiável a ' + App.fmtNum(end) + ' km de ' + l.a })));
      }
    }
    for (const n of pos.keys()) {
      const p = XY(n);
      const cls = req && String(req.s) === n ? 'viz-node src' : req && String(req.d) === n ? 'viz-node dst' : 'viz-node';
      root.append(svg('circle', { cx: p.x, cy: p.y, r: 11, class: cls }));
      root.append(svg('text', { x: p.x, y: p.y + 4, 'text-anchor': 'middle', class: 'viz-nodelabel', text: n }));
    }
    clear(ui.topo).append(root);
  }

  function linkNodes(links) {
    if (!links.length) return [];
    const out = [links[0].split('-')[0]];
    for (const id of links) out.push(id.split('-')[1]);
    return out;
  }

  function kv(k, v) { return el('div', el('div.k', k), el('div.v', v)); }

  function drawDetail(e, relCircuits, prevHeld, contrib, trial) {
    const box = clear(ui.detail);
    if (e.k === 'rel') {
      box.append(el('h3', 'Saída'),
        el('div.kv', kv('Circuitos removidos', String(relCircuits.filter((c) => c.reqs.size <= 1).length)),
          kv('QChs da sessão', prevHeld ? String(prevHeld.qch.length) : '0'), kv('PIChs da sessão', prevHeld ? String(prevHeld.pich.length) : '0')),
        prevHeld && !relCircuits.length && !prevHeld.qch.length ? el('div.small.muted', 'A requisição não ocupava recursos registrados.') : null);
      return;
    }
    const c = (e.c || [])[0];
    const items = [];
    if (c) {
      if (c.route) items.push(kv('Rota', c.route.join(' → ')));
      items.push(kv('Modulação', c.mod ? c.mod + (c.band ? ' (' + (c.band[1] - c.band[0] + 1) + ' slots)' : '') : '—'));
      items.push(kv('Núcleo', c.core !== undefined ? String(c.core) : '—'));
      items.push(kv('Faixa de slots', c.band ? c.band[0] + '–' + c.band[1] : 'nenhuma'));
      if (c.snr !== undefined) items.push(kv('SNR', App.fmtNum(c.snr, 2) + ' dB' + (c.snrTh !== undefined ? ' (limiar ' + App.fmtNum(c.snrTh, 2) + ')' : '')));
      if (c.xt !== undefined) items.push(kv('XT', App.fmtNum(c.xt, 2) + ' dB' + (c.xtTh !== undefined ? ' (limiar ' + App.fmtNum(c.xtTh, 2) + ')' : '')));
      if (c.dbm !== undefined) items.push(kv('Potência', App.fmtNum(c.dbm, 2) + ' dBm'));
    }
    box.append(el('h3', trial ? 'Resultado final da requisição (depois das tentativas)' : e.ok ? 'Circuito' : 'Tentativa bloqueada'), el('div.kv', items));
    if ((e.c || []).length > 1) box.append(el('div.small.muted', (e.c.length) + ' circuitos nesta requisição; os detalhes acima são do primeiro.'));
    if (!e.ok) box.append(el('div.notice.err', 'Causa: ' + causePt(e.cause) + (e.qkdMsg ? ' · última recusa do lado QKD: ' + e.qkdMsg : '')));
    if (!e.ok && e.victim) {
      const v = e.victim;
      const st = stateAt(V.idx - 1);
      const vc = st.circ.get(v.id);
      const who = v.pich ? 'o PICh' : 'o circuito ' + v.id + (vc && vc.n !== undefined ? ' (requisição ' + vc.n + ')' : '') + (vc ? ' ' + linkNodes(vc.links).join('→') + ', núcleo ' + vc.core + ', slots ' + vc.band[0] + '–' + vc.band[1] : '');
      const val = v.snr !== undefined ? 'a SNR dele cairia para ' + App.fmtNum(v.snr, 2) + ' dB (limiar ' + App.fmtNum(v.snrTh, 2) + ' dB, ' + v.mod + ')'
        : 'o XT dele subiria para ' + App.fmtNum(v.xt, 2) + ' dB (limiar ' + App.fmtNum(v.xtTh, 2) + ' dB, ' + v.mod + ')';
      box.append(el('div.notice.warn', 'Vítima encontrada pelo simulador: ' + who + ' — com o novo circuito, ' + val + '.'));
    }
    const a = attemptsOf(e);
    if (a.length) {
      const list = el('div.viz-attempts');
      a.forEach((x, i) => {
        const t = attemptText(x);
        list.append(el('div.viz-att' + (t.ok === false ? '.fail' : t.ok ? '.ok' : '') + (t.diag ? '.diag' : '') + (trial && trial.k === i ? '.cur' : ''),
          { on: { click: () => { stop(); go(V.idx, i); } } },
          el('span.viz-attn', String(i + 1)), el('span.viz-attm', t.mark), ' ', el('strong', t.what), t.cand ? ' — ' + t.cand : '', t.res ? ' — ' + t.res : '',
          t.diag ? el('span.viz-attd', ' diagnóstico') : null));
      });
      const det = el('details.viz-attbox', { open: !!(trial || P.subSteps) },
        el('summary', 'Tentativas internas dos algoritmos (' + a.length + (e.aMore ? ' + ' + e.aMore + ' não gravadas' : '') + ')'), list,
        el('div.small.muted', 'Chamadas aos algoritmos (roteamento, modulação, núcleo e espectro, RMCSA integrado) e testes do candidato feitos pelas funções comuns do simulador. '
          + '"Diagnóstico": testes refeitos depois da recusa, só para classificar a causa. Clique numa linha para ver o candidato.'));
      box.append(det);
      if (trial) setTimeout(() => { const c = det.querySelector('.viz-att.cur'); if (c) c.scrollIntoView({ block: 'nearest' }); }, 0);
    }
    if (contrib && trial) box.append(el('div.small.muted', 'A vítima deste teste está marcada com "V" no espectro.'));
    else if (contrib) box.append(el('div.small.muted', 'Destacados no espectro: ' + contrib.circ.size + ' circuito(s)' + (contrib.pich.size ? ' e ' + contrib.pich.size + ' PICh(s)' : '') + ' — ' + contrib.why + '.'));
    if (e.qkd) {
      const q = e.qkd;
      const pichTxt = (q.pich || []).map((p) => p.link + ' núcleo ' + p.core + ' slots ' + p.band[0] + '–' + p.band[1] + (p.mod ? ' ' + p.mod : '')
        + (p.sections ? ' · seções ' + p.sections.map((x) => App.fmtNum(x)).join('+') + ' km' : '') + (p.snr !== undefined ? ' · SNR ' + App.fmtNum(p.snr, 1) + ' dB' : '')).join('\n');
      box.append(el('h3', 'Sessão QKD'),
        el('div.kv',
          kv('Rota QKD', (q.route || []).join(' → ') + (q.alt ? ' (alternativa)' : '')),
          kv('SKR mínima', q.skrMbps !== undefined ? App.fmtNum(q.skrMbps, 3) + ' Mb/s' : '—'),
          kv('QBER máx.', q.qber !== undefined ? App.fmtNum(q.qber * 100, 2) + ' %' : '—'),
          kv('Trechos (nós confiáveis)', String((q.spans || []).length)),
          kv('QChs', (q.qch || []).map((x) => x[0] + ' núcleo ' + x[1] + ' slot ' + (x[2] === x[3] ? x[2] : x[2] + '–' + x[3])).join('; ') || '—')),
        pichTxt ? el('div.viz-pich', el('div.k', 'PIChs'), el('pre', pichTxt)) : null);
    }
  }

  function drawCores(e, trial) {
    const box = clear(ui.cores);
    const c = trial ? (trial.cand.core !== undefined ? Object.assign({}, trial.cand) : null) : e.k === 'req' ? (e.c || [])[0] : null;
    const lid = c && c.route && c.route.length > 1 ? c.route[0] + '-' + c.route[1] : V.data.links[0].id;
    const l = V.links.get(lid);
    if (!l) return;
    const pos = App.presets.coreLayout(l.cores.map((x) => ({ id: x.id, adjacentCores: x.adj })));
    const cr = l.cores.length > 7 ? 11 : 15, W = 180, R = 70;
    // fit the layout (presets may use several fibres side by side) into the drawing
    const ps = [...pos.values()];
    const minX = Math.min(...ps.map((p) => p.x)), maxX = Math.max(...ps.map((p) => p.x));
    const minY = Math.min(...ps.map((p) => p.y)), maxY = Math.max(...ps.map((p) => p.y));
    const span = Math.max(maxX - minX, maxY - minY, 1e-9);
    const k = l.cores.length > 1 ? (1.45 * R) / span : 0;
    const cx0 = (minX + maxX) / 2, cy0 = (minY + maxY) / 2;
    for (const [id, p] of pos) pos.set(id, { x: (p.x - cx0) * k / R, y: (p.y - cy0) * k / R });
    const s = svg('svg', { viewBox: '0 0 ' + W + ' ' + W, class: 'viz-fiber' });
    s.append(svg('circle', { cx: W / 2, cy: W / 2, r: R + cr + 6, class: 'viz-clad' }));
    const pichCores = new Set(e.qkd ? (e.qkd.pich || []).filter((p) => p.link === lid).map((p) => p.core) : []);
    for (const core of l.cores) {
      const p = pos.get(core.id) || { x: 0, y: 0 };
      const cls = 'viz-core' + (core.qkd ? ' qkd' : '') + (core.off ? ' off' : '') + (c && c.core === core.id ? (trial ? (trial.step.ok === false ? ' blk' : ' cur') : e.ok ? ' cur' : ' blk') : '') + (pichCores.has(core.id) ? ' pich' : '');
      s.append(svg('circle', { cx: W / 2 + p.x * R, cy: W / 2 + p.y * R, r: cr, class: cls }));
      s.append(svg('text', { x: W / 2 + p.x * R, y: W / 2 + p.y * R + 4, 'text-anchor': 'middle', class: 'viz-corelabel', text: String(core.id) }));
    }
    box.append(el('div.small.muted', 'Núcleos do enlace ' + l.a + '→' + l.b + (core0(l) ? ' · roxo: núcleo QKD' : '')), s);
  }
  const core0 = (l) => l.cores.some((c) => c.qkd);

  function logLine(x, i) {
    if (x.k === 'rel') { const r = findReq(x.r); return r && r.n !== undefined ? 'saída da requisição ' + r.n : 'saída de uma requisição anterior ao registro' + (r ? ' (' + r.s + '→' + r.d + ')' : ''); }
    const c = (x.c || [])[0];
    return 'req ' + x.n + ' ' + (x.q ? 'QKD ' : '') + x.s + '→' + x.d + ': ' + (x.ok
      ? 'aceita' + (c ? ' · ' + (c.mod || '') + ' núcleo ' + c.core + ' slots ' + c.band[0] + '–' + c.band[1] : '')
      : 'BLOQUEADA (' + causePt(x.cause) + ')');
  }

  function drawLog() {
    const box = clear(ui.log);
    const from = Math.max(0, V.idx - 12), to = Math.min(V.steps.length - 1, V.idx + 6);
    for (let i = from; i <= to; i++) {
      const x = V.steps[i];
      const line = el('div.viz-logline' + (i === V.idx ? '.cur' : '') + (x.k === 'req' && !x.ok ? '.blk' : ''),
        { on: { click: () => { stop(); go(i); } } }, el('span.viz-logt', App.fmtNum(x.t, 4) + ' h'), ' ', logLine(x, i));
      box.append(line);
    }
  }

  function drawCounters(s) {
    const causes = Object.entries(s.byCause).sort((a, b) => b[1] - a[1]).map(([c, n]) => causePt(c) + ': ' + n).join(' · ');
    const tot = s.acc + s.blk;
    clear(ui.counters).append(el('div.kv',
      kv('Aceitas', String(s.acc)), kv('Bloqueadas', String(s.blk) + (tot ? ' (' + App.fmtNum((100 * s.blk) / tot, 1) + ' %)' : '')),
      kv('Saídas', String(s.rel)), kv('Circuitos ativos', String(s.circ.size)), kv('QChs ativos', String(s.qch.size)), kv('PIChs ativos', String(s.pich.size))),
    causes ? el('div.small.muted', 'Bloqueios por causa (desde o início do registro): ' + causes) : null);
  }

  function hue(id) { return (Math.abs(Number(id)) * 47) % 360; }

  function drawSpectrum(e, s, relCircuits, contrib, trial) {
    const cur = e.k === 'req' && !trial ? (e.c || []) : [];
    const curIds = new Set(e.ok ? cur.map((c) => c.id) : []);
    const curHeld = e.k === 'req' && e.ok ? s.reqs.get(e.r) : null;
    const curQch = new Set(curHeld ? curHeld.qch : []), curPich = new Set(curHeld ? curHeld.pich : []);
    // links to show
    let ids;
    const routeIds = new Set();
    for (const c of cur) routeLinks(c.route).forEach((x) => routeIds.add(x));
    if (e.qkd) for (const p of e.qkd.pich || []) routeIds.add(p.link);
    for (const c of relCircuits) c.links.forEach((x) => routeIds.add(x));
    if (trial && trial.cand.route) routeLinks(trial.cand.route).forEach((x) => routeIds.add(x));
    if (trial && trial.cand.pichLink) routeIds.add(trial.cand.pichLink);
    if (trial && e.k === 'req') for (const c of e.c || []) routeLinks(c.route).forEach((x) => routeIds.add(x));
    const opts = [['route', 'da requisição atual'], ['all', 'todos']].concat(V.data.links.map((l) => [l.id, l.a + '→' + l.b]));
    clear(ui.linkSel).append(...opts.map(([v, lab]) => el('option', { value: v, selected: V.linkSel === v }, lab)));
    if (V.linkSel === 'all') ids = V.data.links.map((l) => l.id);
    else if (V.linkSel === 'route') ids = V.data.links.map((l) => l.id).filter((x) => routeIds.has(x));
    else ids = [V.linkSel];
    if (!ids.length) { clear(ui.spectrum).append(el('div.small.muted', 'Nenhum enlace para esta requisição.')); return; }
    const maxSlots = Math.max(...V.data.links.flatMap((l) => l.cores.map((c) => c.slots)));
    const rowH = Number(P.rowH) || 16, gap = 8, LW = 74, CW = 26;
    // wide enough to give each slot at least ~3.4 units (the box scrolls sideways when needed)
    const W = Math.max(1100, LW + CW + 4 + maxSlots * 3.4);
    const sx = (W - LW - CW - 4) / maxSlots;
    let rows = 0; for (const id of ids) rows += V.links.get(id).cores.length;
    const H = rows * rowH + ids.length * gap + 22;
    const root = svg('svg', { viewBox: '0 0 ' + W + ' ' + H, class: 'viz-spec', width: W > 1100 ? W : '100%' });
    const X = (slot) => LW + CW + (slot - 1) * sx;
    const tickStep = maxSlots > 400 ? 50 : maxSlots > 200 ? 20 : 10;
    for (let t = 0; t <= maxSlots; t += tickStep) {
      root.append(svg('text', { x: X(Math.max(1, t)) + sx / 2, y: 10, 'text-anchor': 'middle', class: 'viz-tick', text: String(Math.max(1, t)) }));
    }
    const dim = contrib && P.contrib;
    const fs = Math.max(7, Math.min(12, rowH - 5));
    let y = 16;
    const blkBand = !trial && !e.ok && e.k === 'req' && cur[0] && cur[0].band ? cur[0] : null;
    const tryLinks = trial ? new Set(trial.cand.pichLink && trial.step.k === 'PICh' ? [trial.cand.pichLink] : routeLinks(trial.cand.route)) : null;
    for (const id of ids) {
      const l = V.links.get(id);
      const y0 = y;
      for (const core of l.cores) {
        const h = rowH - 2;
        root.append(svg('rect', { x: LW + CW, y, width: maxSlots * sx, height: h, class: 'viz-row' + (core.qkd ? ' qkd' : '') + (core.off ? ' off' : '') }));
        root.append(svg('text', { x: LW + CW - 4, y: y + h / 2 + 3.5, 'text-anchor': 'end', class: 'viz-tick', text: String(core.id) }));
        const seg = (band, o) => {
          const w = Math.max(1.5, (band[1] - band[0] + 1) * sx);
          const r = svg('rect', { x: X(band[0]), y, width: w, height: h, class: 'viz-seg' + (o.cls ? ' ' + o.cls : ''),
            style: (o.fill ? 'fill:' + o.fill + ';' : '') + (o.stroke ? 'stroke:' + o.stroke + ';stroke-width:' + (o.sw || 1.6) + ';' : '') + (o.op !== undefined ? 'opacity:' + o.op + ';' : '') });
          if (o.title) r.append(svg('title', { text: o.title }));
          root.append(r);
          if (o.text && P.label !== 'none') {
            const txt = String(o.text);
            if (w >= txt.length * fs * 0.62 + 2 && h >= 9) {
              root.append(svg('text', { x: X(band[0]) + w / 2, y: y + h / 2 + fs * 0.36, 'text-anchor': 'middle', class: 'viz-segtext',
                style: 'font-size:' + fs + 'px;fill:' + (o.textColor || textOn(o.fill || '#ffffff')) + (o.op !== undefined && o.op < 1 ? ';opacity:' + o.op : ''), text: txt }));
            }
          }
        };
        const label = (c, n) => (P.label === 'slots' ? n : P.label === 'req' ? (c.n !== undefined ? c.n : '') : P.label === 'mod' ? (c.mod || '') : '');
        if (!P.onlyCurrent || dim) {
          for (const c of s.circ.values()) {
            if (c.core !== core.id || !c.links.includes(id) || curIds.has(c.id)) continue;
            const isC = dim && contrib.circ.has(c.id), isV = dim && contrib.victim === c.id;
            if (P.onlyCurrent && !isC && !isV) continue;
            const n = c.band[1] - c.band[0] + 1, fill = circuitColor(c);
            seg(c.band, { fill, op: dim && !isC && !isV ? 0.18 : 1, stroke: isV ? P.victim : isC ? 'var(--fg)' : null, sw: isV ? 2.6 : 1.4,
              text: isV ? 'V' : label(c, n), textColor: isV ? '#000' : null,
              title: (isV ? 'VÍTIMA · ' : isC ? 'contribuiu · ' : '') + 'circuito ' + c.id + (c.n !== undefined ? ' · requisição ' + c.n : '') + (c.q ? ' (QKD)' : '') + ' · ' + (c.mod || '') + ' · ' + n + ' slots (' + c.band[0] + '–' + c.band[1] + ')' });
          }
        }
        for (const c of relCircuits) if (c.core === core.id && c.links.includes(id)) seg(c.band, { cls: 'rel', title: 'saindo: circuito ' + c.id + ' · ' + (c.band[1] - c.band[0] + 1) + ' slots', text: label(c, c.band[1] - c.band[0] + 1), textColor: 'var(--fg-2)' });
        for (const [k, q] of s.qch) {
          if (q.link !== id || q.core !== core.id) continue;
          if (P.onlyCurrent && !curQch.has(k)) continue;
          seg(q.band, { fill: P.qch, op: dim ? 0.35 : 1, stroke: curQch.has(k) ? P.cur : null, sw: 2, title: 'QCh · ' + q.n + ' sessão(ões)' + (curQch.has(k) ? ' · usado pela requisição atual' : '') });
        }
        for (const [k, pc] of s.pich) {
          if (pc.link !== id || pc.core !== core.id) continue;
          const isC = dim && contrib.pich.has(k), isV = dim && contrib.victim !== null && pc.id === contrib.victim;
          if (P.onlyCurrent && !curPich.has(k) && !isC && !isV) continue;
          seg(pc.band, { fill: P.pich, op: dim && !isC && !isV ? 0.25 : 1, stroke: isV ? P.victim : curPich.has(k) ? P.cur : isC ? 'var(--fg)' : null, sw: isV ? 2.6 : 2,
            text: isV ? 'V' : null, title: (isV ? 'VÍTIMA · ' : '') + 'PICh ' + (pc.mod || '') + ' · ' + pc.n + ' sessão(ões)' + (curPich.has(k) ? ' · da requisição atual' : '') });
        }
        for (const c of cur) {
          if (!e.ok || c.core !== core.id || !c.band || !routeLinks(c.route).includes(id)) continue;
          const n = c.band[1] - c.band[0] + 1;
          seg(c.band, { fill: P.cur, stroke: '#000', sw: 1.2, text: label(Object.assign({ n: e.n }, c), n), title: 'requisição atual · ' + c.mod + ' · ' + n + ' slots (' + c.band[0] + '–' + c.band[1] + ')' });
        }
        if (trial && trial.cand.band && trial.cand.core === core.id && tryLinks.has(id)) {
          const n = trial.cand.band[1] - trial.cand.band[0] + 1;
          seg(trial.cand.band, { cls: 'try' + (trial.step.ok === false ? ' fail' : ''), text: P.label === 'slots' ? n : null, textColor: 'var(--fg)',
            title: 'candidato do passo interno ' + (trial.k + 1) + ' · ' + n + ' slots (' + trial.cand.band[0] + '–' + trial.cand.band[1] + ')' });
        }
        if (blkBand && blkBand.core === core.id && routeLinks(blkBand.route).includes(id)) {
          const n = blkBand.band[1] - blkBand.band[0] + 1;
          seg(blkBand.band, { cls: 'blk', fill: P.blk, stroke: P.blk, sw: 1.5, text: label(Object.assign({ n: e.n }, blkBand), n), title: 'tentativa bloqueada · ' + n + ' slots (' + blkBand.band[0] + '–' + blkBand.band[1] + ')' });
        }
        y += rowH;
      }
      // slot grid of this link
      if (P.grid !== 'none') {
        const every = P.grid === '10' ? 10 : 1;
        let d = '';
        for (let k = 0; k <= maxSlots; k += every) d += 'M' + (LW + CW + k * sx).toFixed(2) + ' ' + y0 + 'V' + (y - 2);
        root.append(svg('path', { d, class: 'viz-grid' + (every === 1 ? ' fine' : '') }));
      }
      root.append(svg('text', { x: 2, y: (y0 + y) / 2 + 4, class: 'viz-linklabel' + (routeIds.has(id) ? ' on' : ''), text: l.a + '→' + l.b }));
      y += gap;
    }
    clear(ui.spectrum).append(root);
  }

  // ------------------------------------------------------------------ export (GIF / WebM / MP4)

  const EXPORT_PROPS = ['fill', 'fill-opacity', 'stroke', 'stroke-width', 'stroke-opacity', 'stroke-dasharray', 'stroke-linecap',
    'stroke-linejoin', 'opacity', 'font-size', 'font-weight', 'font-family', 'paint-order', 'text-anchor'];

  /** Copy of an on-screen SVG with its computed styles inlined (the CSS of the page does not go with the image). */
  function standaloneSvg(src) {
    const clone = src.cloneNode(true);
    const a = [src, ...src.querySelectorAll('*')], b = [clone, ...clone.querySelectorAll('*')];
    for (let i = 0; i < a.length; i++) {
      const cs = getComputedStyle(a[i]);
      b[i].setAttribute('style', EXPORT_PROPS.map((k) => k + ':' + cs.getPropertyValue(k)).join(';'));
      b[i].removeAttribute('class');
    }
    const vb = (src.getAttribute('viewBox') || '0 0 100 100').split(/\s+/).map(Number);
    clone.setAttribute('width', vb[2]); clone.setAttribute('height', vb[3]);
    clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    return { svg: clone, w: vb[2], h: vb[3] };
  }

  function svgImage(src) {
    return new Promise((resolve, reject) => {
      const s = standaloneSvg(src);
      const text = new XMLSerializer().serializeToString(s.svg);
      const img = new Image();
      const url = URL.createObjectURL(new Blob([text], { type: 'image/svg+xml;charset=utf-8' }));
      img.onload = () => { URL.revokeObjectURL(url); resolve({ img, w: s.w, h: s.h }); };
      img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Falha ao desenhar o quadro')); };
      img.src = url;
    });
  }

  function wrapText(ctx, text, maxW) {
    const words = String(text).split(' '), lines = [];
    let line = '';
    for (const w of words) {
      const t = line ? line + ' ' + w : w;
      if (ctx.measureText(t).width > maxW && line) { lines.push(line); line = w; } else line = t;
    }
    if (line) lines.push(line);
    return lines;
  }

  /** One frame of the export: header, topology, details of the step and spectrum, drawn on the canvas. */
  async function renderFrame(ctx, W, opt) {
    const cs = getComputedStyle(document.body);
    const bg = cs.getPropertyValue('--surface').trim() || '#ffffff', fg = cs.getPropertyValue('--fg').trim() || '#000', fg2 = cs.getPropertyValue('--fg-2').trim() || '#444';
    const font = getComputedStyle(document.body).fontFamily;
    ctx.fillStyle = bg; ctx.fillRect(0, 0, W, ctx.canvas.height);
    const pad = 16;
    let y = pad + 18;
    ctx.fillStyle = fg; ctx.font = '600 18px ' + font;
    for (const l of wrapText(ctx, ui.header.querySelector('.viz-req').textContent, W - 2 * pad)) { ctx.fillText(l, pad, y); y += 22; }
    ctx.font = '13px ' + font; ctx.fillStyle = fg2;
    for (const n of ui.header.querySelectorAll('.small, .viz-trialbar')) for (const l of wrapText(ctx, n.textContent, W - 2 * pad)) { ctx.fillText(l, pad, y); y += 17; }
    y += 6;
    let topH = 0;
    const colW = (W - 3 * pad) / 2;
    if (opt.topo) {
      const t = await svgImage(ui.topo.querySelector('svg'));
      const w = opt.details ? colW : W - 2 * pad, h = (t.h / t.w) * w;
      ctx.drawImage(t.img, pad, y, w, h); topH = h;
    }
    if (opt.details) {
      const x0 = opt.topo ? pad * 2 + colW : pad, maxW = opt.topo ? colW : W - 2 * pad;
      let yy = y + 14;
      ctx.font = '13px ' + font;
      for (const kv of ui.detail.querySelectorAll('.kv > div')) {
        const k = kv.querySelector('.k'), v = kv.querySelector('.v');
        if (!k || !v) continue;
        ctx.fillStyle = fg2; ctx.fillText(k.textContent + ':', x0, yy);
        ctx.fillStyle = fg;
        const lines = wrapText(ctx, v.textContent, maxW - 150);
        lines.forEach((l, i) => ctx.fillText(l, x0 + 150, yy + i * 16));
        yy += 16 * Math.max(1, lines.length) + 3;
        if (yy > y + Math.max(topH, 260)) break;
      }
      for (const n of ui.detail.querySelectorAll('.notice')) {
        ctx.fillStyle = n.classList.contains('err') ? '#d62728' : '#c77700';
        for (const l of wrapText(ctx, n.textContent, maxW)) { ctx.fillText(l, x0, yy); yy += 16; }
        yy += 3;
      }
      topH = Math.max(topH, yy - y);
    }
    y += topH + 10;
    if (opt.spectrum) {
      const sv = ui.spectrum.querySelector('svg');
      if (sv) {
        const t = await svgImage(sv);
        const w = W - 2 * pad, h = (t.h / t.w) * w;
        ctx.drawImage(t.img, pad, y, w, h); y += h;
      } else { ctx.fillStyle = fg2; ctx.fillText(ui.spectrum.textContent, pad, y + 14); y += 20; }
    }
    return y + pad;
  }

  /** Positions (step, internal step) of the export, following the filter of the steps and the internal steps option. */
  function exportPositions(from, to) {
    const out = [];
    for (let i = from; i <= to; i++) {
      const e = V.steps[i];
      if (!visible(e)) continue;
      const a = attemptsOf(e);
      if (P.subSteps && a.length) for (let k = 0; k < a.length; k++) out.push([i, k]);
      out.push([i, -1]);
    }
    return out;
  }

  function exportPanel() {
    const box = el('details.group.viz-appearance');
    const X = Object.assign({ from: 1, to: 50, dt: 0.6, width: 1280, fmt: 'gif', topo: true, details: true, spectrum: true, dest: 'folder', loop: true },
      (() => { try { return JSON.parse(localStorage.getItem('snets.viz.export') || 'null') || {}; } catch (e) { return {}; } })());
    const save = () => { try { localStorage.setItem('snets.viz.export', JSON.stringify(X)); } catch (e) { /* ignore */ } };
    const num = (k, label, help) => {
      const i = el('input.input.num', { type: 'text', value: String(X[k]) });
      i.addEventListener('change', () => { const v = App.parseNumber(i.value); if (Number.isFinite(v) && v > 0) { X[k] = v; save(); est(); } });
      return el('label.field', el('div.flabel', el('span', label)), i, help ? el('div.help', help) : null);
    };
    const sel = (k, label, opts) => {
      const s = el('select.input.small', opts.map(([v, l]) => el('option', { value: v, selected: String(X[k]) === String(v) }, l)));
      s.addEventListener('change', () => { X[k] = s.value; save(); est(); });
      return el('label.field', el('div.flabel', el('span', label)), s);
    };
    const cb = (k, label) => {
      const c = el('input', { type: 'checkbox', checked: !!X[k] });
      c.addEventListener('change', () => { X[k] = c.checked; save(); });
      return el('label.check.small', c, ' ' + label);
    };
    const info = el('div.small.muted');
    const status = el('div.small');
    const est = () => {
      const from = Math.max(1, Math.round(X.from)), to = Math.min(V.steps.length, Math.round(X.to));
      const n = to >= from ? exportPositions(from - 1, to - 1).length : 0;
      const secs = n * Number(X.dt);
      info.textContent = n + ' quadro(s) · ' + App.fmtNum(secs, 1) + ' s de vídeo' + (X.fmt === 'gif' && n > 200 ? ' · GIF com muitos quadros fica grande e lento: corte o intervalo, use o filtro "só bloqueios" ou grave em vídeo.' : '')
        + (X.fmt !== 'gif' ? ' · o vídeo é gravado em tempo real (leva o mesmo tempo que dura).' : '');
    };
    const useCur = el('button.btn.small', { type: 'button', on: { click: () => {
      X.from = V.idx + 1; X.to = Math.min(V.steps.length, V.idx + 50); save(); box.replaceWith(exportPanel()); } } }, 'Começar no passo atual');
    let cancel = false;
    const go2 = el('button.btn.small.primary', { type: 'button', on: { click: async () => {
      if (go2.disabled) return;
      stop();
      cancel = false;
      go2.disabled = true; stopBtn.hidden = false;
      try { await runExport(X, (m) => { status.textContent = m; }, () => cancel); }
      catch (e) { status.textContent = 'Erro: ' + e.message; }
      go2.disabled = false; stopBtn.hidden = true;
    } } }, 'Exportar');
    const stopBtn = el('button.btn.small', { type: 'button', hidden: true, on: { click: () => { cancel = true; } } }, 'Cancelar');
    box.append(el('summary', el('strong', 'Exportar animação (GIF, WebM ou MP4)')),
      el('div.viz-app-body',
        el('p.lead', 'Grava os passos do intervalo como animação, com os painéis escolhidos e a aparência atual. Segue o filtro "Mostrar" e a opção "passos internos".'),
        el('div.fields', num('from', 'Do passo', '1 a ' + V.steps.length), num('to', 'Até o passo'), num('dt', 'Tempo de cada quadro (s)'),
          num('width', 'Largura (px)', 'altura conforme o conteúdo'),
          sel('fmt', 'Formato', [['gif', 'GIF (PowerPoint, qualquer lugar)'], ['webm', 'WebM (navegador, players)'], ['mp4', 'MP4 (PowerPoint, players; se o navegador gravar)']]),
          sel('dest', 'Destino', [['folder', 'pasta das figuras do launcher'], ['download', 'baixar pelo navegador']])),
        el('div.viz-row', cb('topo', 'topologia'), cb('details', 'detalhes do passo'), cb('spectrum', 'espectro'), cb('loop', 'GIF em ciclo')),
        info, el('div.viz-row', useCur, go2, stopBtn), status));
    est();
    return box;
  }

  async function runExport(X, status, cancelled) {
    const from = Math.max(1, Math.round(X.from)) - 1, to = Math.min(V.steps.length, Math.round(X.to)) - 1;
    const pos = exportPositions(from, to);
    if (!pos.length) throw new Error('Nenhum passo no intervalo (veja o filtro "Mostrar").');
    const opt = { topo: !!X.topo, details: !!X.details, spectrum: !!X.spectrum };
    const W = Math.max(400, Math.round(Number(X.width) || 1280));
    const keep = [V.idx, V.sub];
    // height: the tallest frame of a sample
    const probe = document.createElement('canvas'); probe.width = W; probe.height = 4000;
    let H = 0;
    for (const p of [pos[0], pos[Math.floor(pos.length / 2)], pos[pos.length - 1]]) {
      go(p[0], p[1]); H = Math.max(H, await renderFrame(probe.getContext('2d'), W, opt));
    }
    H = Math.min(4000, H + (H % 2)); const Wv = W + (W % 2);
    const cv = document.createElement('canvas'); cv.width = Wv; cv.height = H;
    const ctx = cv.getContext('2d');
    const name = (V.data.case + '_lp' + V.data.loadPoint + '_rep' + V.data.replication + '_passos' + (from + 1) + '-' + (to + 1)).replace(/[^\w\-.]+/g, '_');
    let blob;
    try {
      if (X.fmt === 'gif') {
        // palette from a sample of frames, then one frame at a time (memory stays small)
        const sample = [];
        const every = Math.max(1, Math.floor(pos.length / 8));
        for (let i = 0; i < pos.length && sample.length < 8; i += every) {
          go(pos[i][0], pos[i][1]); await renderFrame(ctx, Wv, opt); sample.push(ctx.getImageData(0, 0, Wv, H));
          status('Preparando as cores do GIF… ' + sample.length);
        }
        const { pal, nearest } = App.anim.buildPalette(sample);
        const parts = [], head = [];
        const str = (t) => { for (let i = 0; i < t.length; i++) head.push(t.charCodeAt(i)); };
        const u16 = (v, arr) => arr.push(v & 0xff, (v >> 8) & 0xff);
        str('GIF89a'); u16(Wv, head); u16(H, head); head.push(0xf7, 0, 0);
        for (let i = 0; i < pal.length; i++) head.push(pal[i]);
        if (X.loop) head.push(0x21, 0xff, 0x0b, ...'NETSCAPE2.0'.split('').map((c) => c.charCodeAt(0)), 3, 1, 0, 0, 0);
        parts.push(new Uint8Array(head));
        for (let f = 0; f < pos.length; f++) {
          if (cancelled()) throw new Error('cancelado');
          go(pos[f][0], pos[f][1]);
          await renderFrame(ctx, Wv, opt);
          status('GIF: quadro ' + (f + 1) + ' de ' + pos.length);
          const d = ctx.getImageData(0, 0, Wv, H).data;
          const idx = new Uint8Array(Wv * H);
          for (let i = 0, p = 0; i < d.length; i += 4, p++) idx[p] = nearest(((d[i] >> 3) << 10) | ((d[i + 1] >> 3) << 5) | (d[i + 2] >> 3));
          const fr = [0x21, 0xf9, 4, 0x04];
          u16(Math.max(2, Math.round(Number(X.dt) * 100)), fr);
          fr.push(0, 0, 0x2c, 0, 0, 0, 0);
          u16(Wv, fr); u16(H, fr); fr.push(0);
          App.anim.lzw(idx, fr);
          parts.push(new Uint8Array(fr));
          await new Promise((r) => setTimeout(r, 0));
        }
        parts.push(new Uint8Array([0x3b]));
        blob = new Blob(parts, { type: 'image/gif' });
      } else {
        const mime = App.anim.videoMime(X.fmt);
        if (!mime) throw new Error('Este navegador não grava ' + X.fmt.toUpperCase() + '. Use ' + (X.fmt === 'mp4' ? 'WebM ou GIF' : 'GIF') + '.');
        const out = document.createElement('canvas'); out.width = Wv; out.height = H;
        const octx = out.getContext('2d');
        go(pos[0][0], pos[0][1]); await renderFrame(ctx, Wv, opt); octx.drawImage(cv, 0, 0);
        const stream = out.captureStream(30);
        const rec = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: 8000000 });
        const chunks = [];
        rec.ondataavailable = (ev) => { if (ev.data && ev.data.size) chunks.push(ev.data); };
        const done = new Promise((res) => { rec.onstop = res; });
        rec.start();
        const ms = Number(X.dt) * 1000;
        for (let f = 0; f < pos.length; f++) {
          if (cancelled()) { rec.stop(); await done; throw new Error('cancelado'); }
          const t0 = performance.now();
          if (f) { go(pos[f][0], pos[f][1]); await renderFrame(ctx, Wv, opt); octx.drawImage(cv, 0, 0); }
          status('Vídeo: quadro ' + (f + 1) + ' de ' + pos.length + ' (gravando em tempo real)');
          await new Promise((r) => setTimeout(r, Math.max(10, ms - (performance.now() - t0))));
        }
        await new Promise((r) => setTimeout(r, 150));
        rec.stop(); await done;
        stream.getTracks().forEach((t) => t.stop());
        blob = new Blob(chunks, { type: mime.split(';')[0] });
      }
    } finally {
      go(keep[0], keep[1]);
    }
    const ext = X.fmt;
    status('Gravando o arquivo…');
    if (X.dest === 'folder') {
      const body = await new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(r.result); r.onerror = () => rej(new Error('Falha ao ler o arquivo')); r.readAsDataURL(blob); });
      const n = (await App.api.figureImageName(name, ext)).name;
      await App.api.saveFigureImage(n, ext, body);
      status('Gravado na pasta das figuras: ' + n + '.' + ext + ' (' + App.fmtBytes(blob.size) + ')');
      App.toast('Animação gravada: ' + n + '.' + ext, 'ok');
    } else {
      const u = URL.createObjectURL(blob);
      const a = el('a', { href: u, download: name + '.' + ext });
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(u), 10000);
      status('Arquivo baixado: ' + name + '.' + ext + ' (' + App.fmtBytes(blob.size) + ')');
    }
  }

  // keyboard: arrows and space while the viewer is on screen
  document.addEventListener('keydown', (ev) => {
    if (!ui || !V.data || !document.body.contains(ui.slider)) return;
    const tag = (ev.target && ev.target.tagName) || '';
    if (/INPUT|SELECT|TEXTAREA/.test(tag)) return;
    if (ev.key === 'ArrowRight') { stop(); stepBy(1); ev.preventDefault(); }
    else if (ev.key === 'ArrowLeft') { stop(); stepBy(-1); ev.preventDefault(); }
    else if (ev.key === ' ') { play(); ev.preventDefault(); }
  });
})(window.App);
