/*
 * 1.18.2: batch editor. Several cases are marked in the sidebar ("Selecionar"); the forms of the case view are
 * shown with the values of one of them (the template) and only the fields changed there are applied to the other
 * marked cases. Every file written is first copied to simulations/backup_lote/<lote>/<caso>/<arquivo>.bak.
 */
(function (App) {
  'use strict';
  const { el, clear, fmtNum, fmtDate, deepClone, deepEqual, isPlainObject } = App;

  const FILES = ['network', 'physicalLayer', 'simulation', 'traffic', 'others', 'qkdPhysicalLayer'];
  /** Fields that identify an element of an array (matched by value instead of position). */
  const ID_FIELDS = ['name', 'id', 'source', 'destination', 'bitRate'];
  const st = () => App.state;

  // ================================================================== selection (sidebar)

  App.selection = {
    toggleMode() {
      const s = st();
      s.selectMode = !s.selectMode;
      if (!s.selected) s.selected = new Set();
      App.redrawTree();
    },
    isOn: () => !!st().selectMode,
    has: (p) => !!(st().selected && st().selected.has(p)),
    set(paths, on) {
      const s = st();
      if (!s.selected) s.selected = new Set();
      for (const p of paths) { if (on) s.selected.add(p); else s.selected.delete(p); }
      App.redrawTree();
    },
    clear() { st().selected = new Set(); App.redrawTree(); },
    list: () => [...(st().selected || [])].filter((p) => App.allCases(true).some((c) => c.path === p)),
  };

  /** Toolbar shown above the tree while the selection mode is on. */
  function selectionBar() {
    const n = App.selection.list().length;
    return el('div.sel-bar',
      el('div.small', n ? el('b', n + (n === 1 ? ' caso marcado' : ' casos marcados')) : el('span.muted', 'Marque casos ou pastas para editar em lote.')),
      el('div.sel-actions',
        el('button.btn.small.primary', { type: 'button', disabled: n < 1, on: { click: () => open(App.selection.list()) } }, 'Editar em lote'),
        el('button.btn.small', { type: 'button', disabled: !n, on: { click: () => App.selection.clear() } }, 'Limpar'),
        el('button.btn.small.ghost', { type: 'button', on: { click: () => App.selection.toggleMode() } }, 'Sair')));
  }

  // ================================================================== diff of the template

  function idOf(v) {
    if (!isPlainObject(v)) return null;
    const k = {};
    let any = false;
    for (const f of ID_FIELDS) if (v[f] !== undefined && typeof v[f] !== 'object') { k[f] = v[f]; any = true; }
    return any ? k : null;
  }

  /** True when every element has an identity and no two elements share it. */
  function allIds(arr) {
    if (!arr.length) return false;
    const seen = new Set();
    for (const v of arr) {
      const k = idOf(v);
      if (!k) return false;
      const t = JSON.stringify(k);
      if (seen.has(t)) return false;
      seen.add(t);
    }
    return true;
  }

  /** Changes between the template file as loaded (a) and as edited (b), field by field. */
  function diff(a, b, path, out) {
    if (deepEqual(a, b)) return out;
    if (isPlainObject(a) && isPlainObject(b)) {
      for (const k of new Set(Object.keys(a).concat(Object.keys(b)))) {
        if (!(k in b)) out.push({ path: path.concat([k]), op: 'del', old: a[k] });
        else if (!(k in a)) out.push({ path: path.concat([k]), op: 'set', old: undefined, val: deepClone(b[k]) });
        else diff(a[k], b[k], path.concat([k]), out);
      }
      return out;
    }
    if (Array.isArray(a) && Array.isArray(b) && allIds(a) && allIds(b)) {
      // elements with an identity (cores by id, nodes by name, links and generators by source/destination...):
      // matched by identity, so removing core 6 removes core 6 in every case and keeps the other cores as they are
      const key = (v) => JSON.stringify(idOf(v));
      const inB = new Map(b.map((v, i) => [key(v), i]));
      const inA = new Set(a.map(key));
      a.forEach((v, i) => {
        if (inB.has(key(v))) diff(v, b[inB.get(key(v))], path.concat([{ i, id: idOf(v) }]), out);
        else out.push({ path, op: 'arrdel', id: idOf(v), old: deepClone(v) });
      });
      b.forEach((v, i) => { if (!inA.has(key(v))) out.push({ path, op: 'arradd', id: idOf(v), at: i, val: deepClone(v) }); });
      return out;
    }
    if (Array.isArray(a) && Array.isArray(b) && a.length === b.length) {
      for (let i = 0; i < a.length; i++) diff(a[i], b[i], path.concat([{ i, id: idOf(a[i]) }]), out);
      return out;
    }
    out.push({ path, op: 'set', old: deepClone(a), val: deepClone(b) });
    return out;
  }

  function templateChanges() {
    const s = st();
    const out = [];
    for (const f of FILES) {
      if (s.parseErrors[f]) continue;
      if (deepEqual(s.base[f], s.model[f])) continue;
      for (const c of diff(s.base[f], s.model[f], [], [])) out.push(Object.assign({ file: f }, c));
    }
    return out;
  }

  function segText(seg) {
    if (typeof seg !== 'object') return '.' + seg;
    if (!seg.id) return '[' + seg.i + ']';
    const id = seg.id;
    const parts = [];
    if (id.name !== undefined) parts.push(id.name);
    if (id.id !== undefined) parts.push('#' + id.id);
    if (id.source !== undefined || id.destination !== undefined) parts.push(id.source + '→' + id.destination);
    if (id.bitRate !== undefined) parts.push(App.fmtBitRate ? App.fmtBitRate(Number(id.bitRate)) : id.bitRate);
    return '[' + parts.join(', ') + ']';
  }
  const pathText = (c) => c.file + (c.path.length ? c.path.map(segText).join('').replace(/^\./, ':') : ' (arquivo inteiro)')
    + (c.op === 'arrdel' ? segText({ id: c.id }) + ' (remover)' : c.op === 'arradd' ? segText({ id: c.id }) + ' (adicionar)' : '');

  function shortVal(v) {
    if (v === undefined) return '—';
    const t = JSON.stringify(v);
    return t.length > 60 ? t.slice(0, 57) + '…' : t;
  }

  // ================================================================== applying to one case

  /** Finds the element of array arr addressed by seg: same position and id, otherwise the element with the id. */
  function findIndex(arr, seg) {
    if (!seg.id) return seg.i < arr.length ? seg.i : -1;
    const same = (v) => { const k = idOf(v); return k && deepEqual(k, seg.id); };
    if (seg.i < arr.length && same(arr[seg.i])) return seg.i;
    return arr.findIndex(same);
  }

  /** Applies the changes to deep copies of the files of one case. */
  function applyTo(models, changes) {
    const out = deepClone(models);
    const results = [];
    for (const c of changes) {
      const r = { c, status: 'na', cur: undefined, why: '' };
      results.push(r);
      if (!c.path.length) { // whole file (created or replaced)
        r.cur = out[c.file];
        r.status = out[c.file] === undefined ? 'cria' : deepEqual(out[c.file], c.val) ? 'igual' : 'substitui';
        out[c.file] = deepClone(c.val);
        continue;
      }
      if (out[c.file] === undefined) { r.why = 'arquivo ausente neste caso'; continue; }
      let node = out[c.file];
      let ok = true;
      const arrOp = c.op === 'arrdel' || c.op === 'arradd';
      const depth = arrOp ? c.path.length : c.path.length - 1;
      for (let k = 0; k < depth && ok; k++) {
        const seg = c.path[k];
        if (typeof seg === 'object') {
          if (!Array.isArray(node)) { ok = false; r.why = 'estrutura diferente'; break; }
          const i = findIndex(node, seg);
          if (i < 0) { ok = false; r.why = 'elemento ' + segText(seg) + ' não existe neste caso'; break; }
          node = node[i];
        } else {
          if (!isPlainObject(node)) { ok = false; r.why = 'estrutura diferente'; break; }
          if (node[seg] === undefined) {
            if (c.op === 'del') { ok = false; r.status = 'igual'; break; }
            node[seg] = {};
          }
          node = node[seg];
        }
      }
      if (!ok) continue;
      if (arrOp) {
        if (!Array.isArray(node)) { r.why = 'estrutura diferente'; continue; }
        const i = findIndex(node, { i: -1, id: c.id });
        if (c.op === 'arrdel') {
          if (i < 0) { r.status = 'igual'; continue; }
          r.cur = node[i];
          node.splice(i, 1);
          r.status = 'remove';
        } else if (i >= 0) {
          r.cur = node[i];
          if (deepEqual(node[i], c.val)) { r.status = 'igual'; continue; }
          node[i] = deepClone(c.val);
          r.status = 'difere';
        } else {
          node.splice(Math.min(c.at, node.length), 0, deepClone(c.val));
          r.status = 'adiciona';
        }
        continue;
      }
      const last = c.path[c.path.length - 1];
      let key = last;
      if (typeof last === 'object') {
        if (!Array.isArray(node)) { r.why = 'estrutura diferente'; continue; }
        key = findIndex(node, last);
        if (key < 0) { r.why = 'elemento ' + segText(last) + ' não existe neste caso'; continue; }
      } else if (!isPlainObject(node)) { r.why = 'estrutura diferente'; continue; }
      r.cur = node[key];
      if (c.op === 'del') {
        if (!(key in node)) { r.status = 'igual'; continue; }
        delete node[key];
        r.status = 'remove';
        continue;
      }
      if (deepEqual(r.cur, c.val)) { r.status = 'igual'; continue; }
      r.status = deepEqual(r.cur, c.old) ? 'muda' : 'difere';
      node[key] = deepClone(c.val);
    }
    return { models: out, results };
  }

  const EFFECTIVE = ['muda', 'difere', 'remove', 'cria', 'substitui', 'adiciona'];

  // ================================================================== open / reload

  /** Opens the batch editor for the given case paths (the first one is the template). */
  async function open(paths, template) {
    if (!paths.length) return;
    const s = st();
    if (App.dirtyFiles().length && !window.confirm('Há alterações não salvas no caso aberto. Descartá-las e abrir a edição em lote?')) return;
    const cases = {};
    const failed = [];
    await Promise.all(paths.map(async (p) => {
      try { cases[p] = await App.api.readCase(p); } catch (e) { failed.push(p + ': ' + e.message); }
    }));
    const ok = paths.filter((p) => cases[p]);
    if (failed.length) App.toast('Não foi possível ler: ' + failed.join('; '), 'err', 8000);
    if (!ok.length) return;
    const tpl = template && ok.includes(template) ? template : ok[0];
    const bulk = { paths: ok, cases, models: {}, errors: {}, include: new Set(ok), deleteRoutes: true, lastBatch: s.bulk && s.bulk.lastBatch };
    for (const p of ok) {
      bulk.models[p] = {};
      for (const f of FILES) {
        const text = cases[p].files[f];
        if (text === null || text === undefined) continue;
        try { bulk.models[p][f] = JSON.parse(text); } catch (e) { bulk.errors[p] = (bulk.errors[p] || []).concat([f]); }
      }
    }
    s.dirty = {};
    s.casePath = null;
    await App.openCase(tpl, s.tab && isBulkTab(s.tab) ? s.tab : 'overview', bulk, cases[tpl]);
  }

  const isBulkTab = (t) => !['review', 'run', 'sweep', 'results'].includes(t);

  async function reload() {
    const b = st().bulk;
    if (!b) return;
    if (App.dirtyFiles().length && !window.confirm('Descartar as alterações do lote e reler os casos do disco?')) return;
    st().dirty = {};
    await open(b.paths, st().casePath);
  }

  function exit() {
    if (App.dirtyFiles().length && !window.confirm('Há alterações do lote ainda não aplicadas. Sair mesmo assim?')) return;
    const tpl = st().casePath;
    st().dirty = {};
    st().casePath = null;
    App.openCase(tpl, 'overview');
  }

  // ================================================================== overview: what differs between the cases

  function caseSummary(m, data) {
    const net = m.network || {};
    const sim = m.simulation || {};
    const tr = App.traffic.summarize(m.traffic);
    const lp = Math.max(1, Number(sim.loadPoints) || 1);
    return {
      nodes: (net.nodes || []).length,
      links: (net.links || []).length,
      cores: (net.cores || []).length,
      load: fmtNum(tr.base, 0) + ' → ' + fmtNum(tr.base + tr.inc * (lp - 1), 0),
      points: sim.loadPoints, reps: sim.replications, req: sim.requests,
      routing: sim.routing, csa: sim.coreAndSpectrumAssignment || sim.spectrumAssignment,
      qkd: isPlainObject(net.qkd) && net.qkd.enabled ? 'sim' : 'não',
      csv: (data.csv || []).length,
    };
  }

  const COLS = [['nodes', 'Nós'], ['links', 'Enlaces'], ['cores', 'Núcleos'], ['load', 'Carga (Erlang)'], ['points', 'Pontos'],
    ['reps', 'Replic.'], ['req', 'Requisições'], ['routing', 'Roteamento'], ['csa', 'Núcleo/espectro'], ['qkd', 'QKD'], ['csv', 'CSVs']];

  function renderOverview() {
    const s = st();
    const b = s.bulk;
    const tpl = s.casePath;
    const root = el('div.stack');
    const tplSel = el('select.input', { style: { maxWidth: '420px' } }, b.paths.map((p) => el('option', { value: p }, p)));
    tplSel.value = tpl;
    tplSel.addEventListener('change', () => {
      if (App.dirtyFiles().length && !window.confirm('Trocar o caso modelo descarta as alterações já feitas no lote. Continuar?')) { tplSel.value = tpl; return; }
      s.dirty = {};
      open(b.paths, tplSel.value);
    });
    root.appendChild(el('section.panel',
      el('h2', 'Edição em lote · ' + b.paths.length + ' casos'),
      el('p.lead', 'As abas Tráfego, Rede, Camada física, Simulação, Métricas e Others mostram os valores do caso modelo. ',
        'Só os campos que você mudar serão aplicados aos casos marcados; o resto de cada caso fica como está. ',
        'Confira e grave em “Aplicar nos casos”.'),
      el('div.field', el('div.flabel', 'Caso modelo (valores mostrados nos formulários)'), tplSel)));

    const sums = {};
    for (const p of b.paths) sums[p] = caseSummary(b.models[p], b.cases[p]);
    const ref = sums[tpl];
    const tbody = el('tbody', b.paths.map((p) => {
      const sm = sums[p];
      return el('tr' + (p === tpl ? '.bulk-tpl' : ''),
        el('td.mono.small', p, p === tpl ? el('span.tag.info', { style: { marginLeft: '6px' } }, 'modelo') : null,
          b.errors[p] ? el('div.fwarn.small', 'JSON inválido em: ' + b.errors[p].join(', ')) : null),
        COLS.map(([k]) => el('td' + (String(sm[k]) !== String(ref[k]) ? '.bulk-diff' : ''), sm[k] === undefined ? '—' : String(sm[k]))),
        el('td', p === tpl ? null : el('button.btn.small.ghost', { type: 'button', title: 'Tirar este caso do lote', on: { click: () => {
          const rest = b.paths.filter((x) => x !== p);
          App.selection.set([p], false);
          open(rest, tpl);
        } } }, '✕')));
    }));
    root.appendChild(el('section.panel',
      el('h2', 'Casos do lote'),
      el('p.lead', 'Células destacadas são diferentes do caso modelo.'),
      el('div.table-wrap', el('table.grid.data',
        el('thead', el('tr', el('th', 'Caso'), COLS.map(([, l]) => el('th', l)), el('th', ''))), tbody))));
    return root;
  }

  // ================================================================== apply tab

  const STATUS_TEXT = {
    muda: 'muda', difere: 'muda (o caso tinha outro valor)', remove: 'remove', adiciona: 'adiciona', cria: 'cria o arquivo',
    substitui: 'substitui o arquivo', igual: 'já igual', na: 'não aplicável',
  };

  function renderApply() {
    const s = st();
    const b = s.bulk;
    const root = el('div.stack');
    const changes = templateChanges();
    const parseBad = FILES.filter((f) => s.parseErrors[f]);
    if (parseBad.length) root.appendChild(el('div.notice.err', 'JSON inválido no caso modelo (' + parseBad.join(', ') + '). Corrija antes de aplicar.'));
    if (!changes.length) {
      root.appendChild(el('section.panel', el('h2', 'Nada para aplicar'),
        el('p.lead', 'Nenhum campo foi alterado nas abas. Mude os valores (no caso modelo) e volte aqui para conferir e gravar nos casos marcados.')));
      if (b.lastBatch) root.appendChild(lastBatchNote(b.lastBatch));
      return root;
    }

    // what changed in the template
    const byFile = {};
    for (const c of changes) (byFile[c.file] = byFile[c.file] || []).push(c);
    root.appendChild(el('section.panel',
      el('h2', 'Alterações feitas no modelo (' + changes.length + ')'),
      el('div.stack-s', Object.entries(byFile).map(([f, list]) => el('details.group', { open: list.length <= 12 },
        el('summary', f, el('span.count', '· ' + list.length + ' campo(s)')),
        el('div.group-body', el('table.grid.data.small', el('tbody', list.slice(0, 300).map((c) => el('tr',
          el('td.mono', pathText(c)), el('td.mono', shortVal(c.old)), el('td', '→'), el('td.mono', c.op === 'del' || c.op === 'arrdel' ? '(removido)' : shortVal(c.val)))))),
          list.length > 300 ? el('div.muted.small', '… mais ' + (list.length - 300)) : null))))));

    // effect on each case
    const plans = b.paths.map((p) => {
      const bad = b.errors[p] || [];
      const r = applyTo(b.models[p], changes.filter((c) => !bad.includes(c.file)));
      const eff = r.results.filter((x) => EFFECTIVE.includes(x.status));
      const files = [...new Set(eff.map((x) => x.c.file))];
      const issues = App.validate.validateAll(r.models, s.catalog, s.info);
      const netChanged = eff.some((x) => x.c.file === 'network' && (x.c.path.length === 0 || ['nodes', 'links'].includes(x.c.path[0])));
      return { p, r, eff, files, bad, issues, netChanged, routes: !!b.cases[p].hasRoutesFile };
    });
    const tbody = el('tbody');
    for (const pl of plans) {
      const cnt = (k) => pl.r.results.filter((x) => x.status === k).length;
      const errs = pl.issues.filter((i) => i.level === 'err').length;
      const warns = pl.issues.filter((i) => i.level === 'warn').length;
      const cb = el('input', { type: 'checkbox', checked: b.include.has(pl.p), disabled: !pl.eff.length });
      cb.addEventListener('change', () => { if (cb.checked) b.include.add(pl.p); else b.include.delete(pl.p); App.renderTab(); });
      tbody.appendChild(el('tr',
        el('td', cb),
        el('td.mono.small', pl.p, pl.p === s.casePath ? el('span.tag.info', { style: { marginLeft: '6px' } }, 'modelo') : null),
        el('td.num', String(pl.eff.length)),
        el('td.num' + (cnt('difere') ? '.bulk-diff' : ''), String(cnt('difere'))),
        el('td.num' + (cnt('na') ? '.bulk-diff' : ''), String(cnt('na'))),
        el('td', pl.files.join(', ') || '—'),
        el('td', errs ? el('span.tag.err', errs + ' erro(s)') : warns ? el('span.tag.warn', warns + ' aviso(s)') : el('span.muted', 'ok'))));
    }
    const anyRoutes = plans.some((pl) => pl.netChanged && pl.routes);
    const routesCb = el('input', { type: 'checkbox', checked: b.deleteRoutes });
    routesCb.addEventListener('change', () => { b.deleteRoutes = routesCb.checked; });
    const chosen = plans.filter((pl) => b.include.has(pl.p) && pl.eff.length);
    const btn = el('button.btn.primary', { type: 'button', disabled: !chosen.length || parseBad.length > 0 },
      'Aplicar em ' + chosen.length + (chosen.length === 1 ? ' caso' : ' casos'));
    btn.addEventListener('click', () => applyAll(chosen, changes, btn));
    root.appendChild(el('section.panel',
      el('h2', 'Efeito em cada caso'),
      el('p.lead', '“Outro valor” conta os campos em que o caso tinha um valor diferente do modelo (será trocado pelo novo). ',
        '“Não aplicável” conta mudanças que não cabem no caso (arquivo ausente ou elemento que não existe nele, como um par de nós de outra topologia); elas são ignoradas.'),
      el('div.table-wrap', el('table.grid.data',
        el('thead', el('tr', el('th', ''), el('th', 'Caso'), el('th', 'Mudanças'), el('th', 'Outro valor'), el('th', 'Não aplicável'), el('th', 'Arquivos'), el('th', 'Validação'))),
        tbody)),
      anyRoutes ? el('label.check', { style: { marginTop: '10px' } }, routesCb,
        ' A topologia muda: tirar o kRoutesByPar.txt (rotas antigas) desses casos — ele vai para a cópia de segurança') : null,
      el('div.toolbar', { style: { marginTop: '12px', marginBottom: 0 } }, btn,
        el('button.btn', { type: 'button', on: { click: () => { st().dirty = {}; reload(); } } }, 'Descartar alterações'),
        el('span.small.muted', 'Antes de gravar, cada arquivo é copiado para simulations/backup_lote/.'))));

    // details per case
    root.appendChild(el('section.panel', el('h2', 'Detalhes por caso'),
      plans.map((pl) => el('details.group',
        el('summary', el('span.mono', pl.p), el('span.count', '· ' + pl.eff.length + ' mudança(s)')),
        el('div.group-body',
          pl.bad.length ? el('div.notice.warn', 'JSON inválido em ' + pl.bad.join(', ') + ': esses arquivos não serão alterados.') : null,
          el('table.grid.data.small', el('tbody', pl.r.results.slice(0, 400).map((x) => el('tr',
            el('td.mono', pathText(x.c)),
            el('td.mono', shortVal(x.cur)), el('td', '→'), el('td.mono', x.c.op === 'del' || x.c.op === 'arrdel' ? '(removido)' : shortVal(x.c.val)),
            el('td' + (x.status === 'difere' || x.status === 'na' ? '.fwarn' : ''), STATUS_TEXT[x.status] + (x.why ? ': ' + x.why : '')))))),
          pl.issues.length ? el('div', { style: { marginTop: '8px' } }, App.validate.issuesList(pl.issues)) : null)))));
    if (b.lastBatch) root.appendChild(lastBatchNote(b.lastBatch));
    return root;
  }

  function lastBatchNote(batch) {
    return el('div.notice.info', 'Último lote gravado: cópias dos arquivos anteriores em ',
      el('code', 'simulations/backup_lote/' + batch), '.');
  }

  function stamp() {
    const d = new Date();
    const z = (n) => String(n).padStart(2, '0');
    return 'lote_' + d.getFullYear() + z(d.getMonth() + 1) + z(d.getDate()) + '-' + z(d.getHours()) + z(d.getMinutes()) + z(d.getSeconds());
  }

  async function applyAll(plans, changes, btn) {
    const s = st();
    const b = s.bulk;
    const errs = plans.reduce((n, pl) => n + pl.issues.filter((i) => i.level === 'err').length, 0);
    if (errs && !window.confirm('Há ' + errs + ' erro(s) de validação nos casos marcados. Aplicar mesmo assim?')) return;
    if (!window.confirm('Gravar as mudanças em ' + plans.length + ' caso(s)? Os arquivos atuais vão para simulations/backup_lote/.')) return;
    btn.disabled = true;
    const batch = stamp();
    const done = [];
    const failed = [];
    for (const pl of plans) {
      try {
        for (const f of pl.files) {
          await App.api.writeFile(pl.p, f, App.serialize(f, pl.r.models[f], b.cases[pl.p].files.others), b.cases[pl.p].mtimes[f] || 0, batch);
        }
        if (b.deleteRoutes && pl.netChanged && pl.routes) await App.api.deleteRoutes(pl.p, batch);
        done.push(pl.p);
      } catch (e) {
        failed.push(pl.p + ': ' + e.message);
      }
    }
    if (failed.length) App.toast('Falhou em ' + failed.length + ' caso(s): ' + failed.join('; '), 'err', 12000);
    if (done.length) App.toast('Gravado em ' + done.length + ' caso(s). Cópias em simulations/backup_lote/' + batch, 'ok', 8000);
    b.lastBatch = batch;
    s.dirty = {};
    App.loadTree();
    await open(b.paths, s.casePath);
    App.selectTab('bulk');
  }

  App.bulk = { open, reload, exit, renderOverview, renderApply, selectionBar, isBulkTab, templateChanges, applyTo, diff };
})(window.App);
