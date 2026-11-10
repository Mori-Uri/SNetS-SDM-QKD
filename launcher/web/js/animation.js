/*
 * Parte 59: animation of a figure of the graphics studio over the load points.
 *
 * Every frame is the figure that is open in the studio, with the same settings, drawn at one load point (or, for
 * the charts with the load on the x axis, up to that load point). The axes can stay fixed (limits of all the
 * frames) or follow the data of each frame. A title with the load point can be added. The result can be previewed
 * with the chosen time per frame and saved as GIF, MP4 or WebM, and the frames as PNG and/or SVG.
 */
(function (App) {
  'use strict';
  const { el, clear } = App;
  const R = () => App.figRender;
  const F = () => App.figures._anim;
  const NS = 'http://www.w3.org/2000/svg';

  const TIMES = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3, 4, 5];
  const TEXTS = [['n', 'Carga {n}'], ['erl', '{erl} E'], ['both', 'Carga {n} · {erl} E'], ['custom', 'Personalizado']];
  const POSITIONS = [['top', 'Acima do gráfico'], ['bottom', 'Abaixo do gráfico'], ['tl', 'Dentro, canto superior esquerdo'],
    ['tr', 'Dentro, canto superior direito'], ['bl', 'Dentro, canto inferior esquerdo'], ['br', 'Dentro, canto inferior direito']];
  const FORMATS = [['gif', 'GIF (abre em qualquer lugar e no PowerPoint)'], ['mp4', 'MP4 (PowerPoint e players)'], ['webm', 'WebM (navegador e players)'], ['none', 'Só os quadros separados']];

  function defaults() {
    return {
      lps: [], mode: 'cumulative', axes: 'fixed', dt: 1, holdLast: 1, loop: true,
      title: { show: true, text: 'both', custom: 'Carga {n} · {erl} E', pos: 'top', size: 0 },
      format: 'gif', scale: 1, frames: 'none', dest: 'download',
    };
  }

  function settings() {
    const spec = F().state.spec;
    const d = defaults();
    const a = Object.assign(d, spec.anim || {});
    a.title = Object.assign(defaults().title, (spec.anim && spec.anim.title) || {});
    spec.anim = a;
    return a;
  }

  /** True when the load is on the x axis: the frames can accumulate the points. */
  function canAccumulate(spec) {
    if (spec.type === 'line' && spec.line && spec.line.xMode !== 'param') return true;
    return spec.type === 'scatter' && spec.scatter && spec.scatter.by === 'LoadPoint';
  }

  /** The figure at one frame: same spec, with the load point (or the points up to it) of the frame. */
  function frameSpec(base, lp, upTo, cumulative) {
    const s = JSON.parse(JSON.stringify(base));
    delete s.anim;
    const pts = cumulative ? upTo.slice() : [lp];
    switch (s.type) {
      case 'line':
        if (s.line.xMode === 'param') s.line.paramLp = lp; else s.line.lps = pts;
        break;
      case 'scatter':
        if (s.scatter.by === 'LoadPoint') s.scatter.lps = pts; else s.scatter.lp = lp;
        break;
      case 'topo': s.topo.lps = [lp]; break;
      case 'cores': s.cores.lp = lp; break;
      case 'slots': case 'slotblock': case 'slotline': s.slots.lp = lp; break;
      default: s.bars.lp = lp; // bars, ref
    }
    return s;
  }

  function titleText(a, lp) {
    const S = F().state;
    const tpl = a.title.text === 'custom' ? a.title.custom : (TEXTS.find((t) => t[0] === a.title.text) || TEXTS[2])[1];
    const erl = S.lpErl && S.lpErl[lp] !== undefined ? App.fmtNum(S.lpErl[lp], 0) : '?';
    return String(tpl).replace(/\{n\}/g, String(lp + 1)).replace(/\{erl\}/g, erl);
  }

  /** A copy of the SVG with the title of the load point. */
  function withTitle(svg, text, a, st) {
    const out = svg.cloneNode(true);
    const W = Number(out.getAttribute('width')), H = Number(out.getAttribute('height'));
    const size = Number(a.title.size) > 0 ? Number(a.title.size) : Math.round(Number(st.titleSize) || 16);
    const color = st.titleColor || st.textColor || '#000000';
    const t = document.createElementNS(NS, 'text');
    t.setAttribute('font-size', size);
    t.setAttribute('font-family', st.font || 'sans-serif');
    t.setAttribute('font-weight', '700');
    t.setAttribute('fill', color);
    t.textContent = text;
    const pos = a.title.pos;
    if (pos === 'top' || pos === 'bottom') {
      const band = Math.round(size * 1.9);
      const g = document.createElementNS(NS, 'g');
      while (out.firstChild) g.appendChild(out.firstChild);
      if (pos === 'top') g.setAttribute('transform', 'translate(0,' + band + ')');
      const bg = document.createElementNS(NS, 'rect');
      bg.setAttribute('x', 0); bg.setAttribute('y', 0); bg.setAttribute('width', W); bg.setAttribute('height', H + band);
      bg.setAttribute('fill', st.bg || '#ffffff');
      out.appendChild(bg);
      out.appendChild(g);
      t.setAttribute('x', W / 2);
      t.setAttribute('y', pos === 'top' ? band * 0.68 : H + band * 0.68);
      t.setAttribute('text-anchor', 'middle');
      out.setAttribute('height', H + band);
      out.setAttribute('viewBox', '0 0 ' + W + ' ' + (H + band));
    } else {
      const pad = Math.round(size * 0.7);
      const left = pos === 'tl' || pos === 'bl', top = pos === 'tl' || pos === 'tr';
      t.setAttribute('x', left ? pad : W - pad);
      t.setAttribute('y', top ? pad + size : H - pad);
      t.setAttribute('text-anchor', left ? 'start' : 'end');
      t.setAttribute('stroke', st.bg || '#ffffff');
      t.setAttribute('stroke-width', Math.max(2, size / 6));
      t.setAttribute('paint-order', 'stroke');
    }
    out.appendChild(t);
    return out;
  }

  /** Builds all the frames: [{lp, svg}] (two passes when the axes are fixed). */
  async function buildFrames(a, onProgress) {
    const S = F().state;
    const base = S.spec;
    const lps = (S.lpOptions || []).filter((l) => !a.lps.length || a.lps.includes(l));
    if (!lps.length) throw new Error('Escolha ao menos um ponto de carga.');
    const cumulative = canAccumulate(base) && a.mode === 'cumulative';
    const specs = lps.map((lp, i) => ({ lp, spec: frameSpec(base, lp, lps.slice(0, i + 1), cumulative) }));
    let domains = null;
    if (a.axes === 'fixed') {
      const xs = [], ys = [], rs = [];
      for (let i = 0; i < specs.length; i++) {
        if (onProgress) onProgress('Calculando os eixos: quadro ' + (i + 1) + ' de ' + specs.length);
        const r = await F().renderFrame(specs[i].spec, null);
        if (r.model) { const d = F().modelDomain(r.model); xs.push(...d.x); ys.push(...d.y); rs.push(...d.range); }
      }
      const fin = ys.filter(Number.isFinite), rf = rs.filter(Number.isFinite);
      domains = { x: [...new Set(xs)], y: fin.length ? [Math.min(...fin), Math.max(...fin)] : [],
        range: rf.length ? [Math.min(...rf), Math.max(...rf)] : null };
    }
    const frames = [];
    for (let i = 0; i < specs.length; i++) {
      if (onProgress) onProgress('Desenhando o quadro ' + (i + 1) + ' de ' + specs.length);
      const r = await F().renderFrame(specs[i].spec, domains);
      if (!r.svg) throw new Error('Quadro ' + (i + 1) + ': ' + (r.empty || 'sem dados'));
      const svg = a.title.show ? withTitle(r.svg, titleText(a, specs[i].lp), a, base.style) : r.svg;
      frames.push({ lp: specs[i].lp, svg });
    }
    return frames;
  }

  // ---------------------------------------------------------------- raster

  function svgToCanvas(svg, scale, size) {
    return new Promise((resolve, reject) => {
      const text = R().serialize(svg);
      const img = new Image();
      const url = URL.createObjectURL(new Blob([text], { type: 'image/svg+xml;charset=utf-8' }));
      img.onload = () => {
        const w = Number(svg.getAttribute('width')), h = Number(svg.getAttribute('height'));
        const W = size ? size.w : Math.round(w * scale), H = size ? size.h : Math.round(h * scale);
        const cv = document.createElement('canvas');
        cv.width = W; cv.height = H;
        const ctx = cv.getContext('2d');
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, W, H);
        ctx.drawImage(img, 0, 0, w * scale, h * scale);
        URL.revokeObjectURL(url);
        resolve(cv);
      };
      img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Falha ao desenhar o quadro')); };
      img.src = url;
    });
  }

  /** Canvas size of the animation: the largest frame (frames of different sizes are drawn at the top left). */
  function canvasSize(frames, scale) {
    let w = 0, h = 0;
    for (const f of frames) {
      w = Math.max(w, Number(f.svg.getAttribute('width')));
      h = Math.max(h, Number(f.svg.getAttribute('height')));
    }
    w = Math.round(w * scale); h = Math.round(h * scale);
    return { w: w + (w % 2), h: h + (h % 2) }; // even sizes for the video encoders
  }

  // ---------------------------------------------------------------- GIF encoder (global palette, LZW)

  function buildPalette(images) {
    const count = new Float64Array(32768), sr = new Float64Array(32768), sg = new Float64Array(32768), sb = new Float64Array(32768);
    for (const im of images) {
      const d = im.data;
      for (let i = 0; i < d.length; i += 4) {
        const k = ((d[i] >> 3) << 10) | ((d[i + 1] >> 3) << 5) | (d[i + 2] >> 3);
        count[k]++; sr[k] += d[i]; sg[k] += d[i + 1]; sb[k] += d[i + 2];
      }
    }
    const keys = [];
    for (let k = 0; k < 32768; k++) if (count[k] > 0) keys.push(k);
    keys.sort((x, y) => count[y] - count[x]);
    const top = keys.slice(0, 256);
    const pal = new Uint8Array(256 * 3);
    top.forEach((k, i) => {
      pal[i * 3] = Math.round(sr[k] / count[k]); pal[i * 3 + 1] = Math.round(sg[k] / count[k]); pal[i * 3 + 2] = Math.round(sb[k] / count[k]);
    });
    const n = top.length;
    const map = new Int16Array(32768).fill(-1);
    const nearest = (k) => {
      let v = map[k];
      if (v >= 0) return v;
      const r = ((k >> 10) << 3) + 4, g = (((k >> 5) & 31) << 3) + 4, b = ((k & 31) << 3) + 4;
      let best = 0, bd = Infinity;
      for (let i = 0; i < n; i++) {
        const dr = pal[i * 3] - r, dg = pal[i * 3 + 1] - g, db = pal[i * 3 + 2] - b;
        const dd = dr * dr * 2 + dg * dg * 4 + db * db * 3;
        if (dd < bd) { bd = dd; best = i; }
      }
      map[k] = best;
      return best;
    };
    top.forEach((k, i) => { map[k] = i; });
    return { pal, nearest };
  }

  function lzw(indices, out) {
    const minCode = 8, clearCode = 256, eoi = 257;
    let codeSize = minCode + 1, next = eoi + 1;
    const table = new Int16Array(4096 * 256).fill(-1);
    let cur = 0, shift = 0;
    const block = [];
    const flushBlock = () => { if (block.length) { out.push(block.length, ...block); block.length = 0; } };
    const emit = (code) => {
      cur |= code << shift; shift += codeSize;
      while (shift >= 8) { block.push(cur & 0xff); cur >>>= 8; shift -= 8; if (block.length === 255) flushBlock(); }
    };
    out.push(minCode);
    emit(clearCode);
    let prefix = indices[0];
    for (let i = 1; i < indices.length; i++) {
      const k = indices[i];
      const key = (prefix << 8) | k;
      const v = table[key];
      if (v >= 0) { prefix = v; continue; }
      emit(prefix);
      if (next === 4096) {
        emit(clearCode);
        table.fill(-1);
        next = eoi + 1;
        codeSize = minCode + 1;
      } else {
        if (next >= (1 << codeSize)) codeSize++;
        table[key] = next++;
      }
      prefix = k;
    }
    emit(prefix);
    emit(eoi);
    if (shift > 0) block.push(cur & 0xff);
    flushBlock();
    out.push(0);
  }

  async function encodeGif(canvases, delaysCs, loop, onProgress) {
    const w = canvases[0].width, h = canvases[0].height;
    const images = canvases.map((cv) => cv.getContext('2d').getImageData(0, 0, w, h));
    const { pal, nearest } = buildPalette(images);
    const parts = [];
    const head = [];
    const str = (s) => { for (let i = 0; i < s.length; i++) head.push(s.charCodeAt(i)); };
    const u16 = (v, arr) => arr.push(v & 0xff, (v >> 8) & 0xff);
    str('GIF89a'); u16(w, head); u16(h, head); head.push(0xf7, 0, 0);
    for (let i = 0; i < pal.length; i++) head.push(pal[i]);
    if (loop) head.push(0x21, 0xff, 0x0b, ...'NETSCAPE2.0'.split('').map((c) => c.charCodeAt(0)), 3, 1, 0, 0, 0);
    parts.push(new Uint8Array(head));
    for (let f = 0; f < images.length; f++) {
      if (onProgress) onProgress('Codificando o GIF: quadro ' + (f + 1) + ' de ' + images.length);
      await new Promise((r) => setTimeout(r, 0)); // keeps the page responsive
      const d = images[f].data;
      const idx = new Uint8Array(w * h);
      for (let i = 0, p = 0; i < d.length; i += 4, p++) idx[p] = nearest(((d[i] >> 3) << 10) | ((d[i + 1] >> 3) << 5) | (d[i + 2] >> 3));
      const fr = [0x21, 0xf9, 4, 0x04];
      u16(Math.max(2, Math.round(delaysCs[f])), fr);
      fr.push(0, 0, 0x2c, 0, 0, 0, 0);
      u16(w, fr); u16(h, fr); fr.push(0);
      lzw(idx, fr);
      parts.push(new Uint8Array(fr));
    }
    parts.push(new Uint8Array([0x3b]));
    return new Blob(parts, { type: 'image/gif' });
  }

  // ---------------------------------------------------------------- video (MediaRecorder)

  function videoMime(fmt) {
    const c = fmt === 'mp4' ? ['video/mp4;codecs=avc1.42E01E', 'video/mp4;codecs=avc1', 'video/mp4']
      : ['video/webm;codecs=vp9', 'video/webm;codecs=vp8', 'video/webm'];
    if (typeof MediaRecorder === 'undefined') return null;
    return c.find((m) => MediaRecorder.isTypeSupported(m)) || null;
  }

  async function encodeVideo(canvases, durationsMs, fmt, onProgress) {
    const mime = videoMime(fmt);
    if (!mime) throw new Error('Este navegador não grava ' + fmt.toUpperCase() + '. Use ' + (fmt === 'mp4' ? 'WebM ou GIF' : 'GIF') + '.');
    const w = canvases[0].width, h = canvases[0].height;
    const cv = document.createElement('canvas');
    cv.width = w; cv.height = h;
    const ctx = cv.getContext('2d');
    ctx.drawImage(canvases[0], 0, 0);
    const stream = cv.captureStream(30);
    const rec = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: 8000000 });
    const chunks = [];
    rec.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data); };
    const done = new Promise((res) => { rec.onstop = res; });
    rec.start();
    for (let i = 0; i < canvases.length; i++) {
      if (onProgress) onProgress('Gravando o vídeo: quadro ' + (i + 1) + ' de ' + canvases.length + ' (em tempo real)');
      ctx.drawImage(canvases[i], 0, 0);
      await new Promise((r) => setTimeout(r, durationsMs[i]));
    }
    ctx.drawImage(canvases[canvases.length - 1], 0, 0); // closes the last frame with its full time
    await new Promise((r) => setTimeout(r, 120));
    rec.stop();
    await done;
    stream.getTracks().forEach((t) => t.stop());
    return new Blob(chunks, { type: mime.split(';')[0] });
  }

  // ---------------------------------------------------------------- output

  function blobToDataUrl(b) {
    return new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(r.result); r.onerror = () => rej(new Error('Falha ao ler o arquivo')); r.readAsDataURL(b); });
  }

  function download(href, name) {
    const a = el('a', { href, download: name });
    document.body.appendChild(a); a.click(); a.remove();
  }

  async function put(a, name, ext, blobOrText) {
    if (a.dest === 'folder') {
      const body = typeof blobOrText === 'string' ? blobOrText : await blobToDataUrl(blobOrText);
      const n = (await App.api.figureImageName(name, ext)).name;
      await App.api.saveFigureImage(n, ext, body);
      return n + '.' + ext;
    }
    const file = F().nextName(name, ext);
    if (typeof blobOrText === 'string') {
      if (ext === 'png') download(blobOrText, file);
      else App.downloadText(file, blobOrText, ext === 'svg' ? 'image/svg+xml' : 'text/plain');
    } else {
      const u = URL.createObjectURL(blobOrText);
      download(u, file);
      setTimeout(() => URL.revokeObjectURL(u), 10000);
    }
    return file;
  }

  function baseName() {
    const S = F().state;
    return ((S.savedName || S.spec.style.title || S.spec.template || 'figura') + '_animacao').replace(/[^\w\-. ()+À-ÿ]+/g, '_').slice(0, 80);
  }

  async function exportAll(a, frames, status) {
    const name = baseName();
    const saved = [];
    if (a.format !== 'none') {
      const size = canvasSize(frames, Number(a.scale) || 1);
      const canvases = [];
      for (let i = 0; i < frames.length; i++) {
        status('Desenhando a imagem do quadro ' + (i + 1) + ' de ' + frames.length);
        canvases.push(await svgToCanvas(frames[i].svg, Number(a.scale) || 1, size));
      }
      const ms = frames.map((f, i) => Number(a.dt) * 1000 * (i === frames.length - 1 ? Math.max(1, Number(a.holdLast) || 1) : 1));
      const blob = a.format === 'gif' ? await encodeGif(canvases, ms.map((m) => m / 10), a.loop, status)
        : await encodeVideo(canvases, ms, a.format, status);
      status('Gravando ' + a.format.toUpperCase());
      saved.push(await put(a, name, a.format, blob));
    }
    if (a.frames !== 'none') {
      for (let i = 0; i < frames.length; i++) {
        status('Gravando os quadros: ' + (i + 1) + ' de ' + frames.length);
        const n = name + '_carga' + (frames[i].lp + 1);
        if (a.frames === 'svg' || a.frames === 'both') saved.push(await put(a, n, 'svg', R().serialize(frames[i].svg)));
        if (a.frames === 'png' || a.frames === 'both') saved.push(await put(a, n, 'png', await R().toPng(frames[i].svg, Number(a.scale) || 1)));
      }
    }
    return saved;
  }

  // ---------------------------------------------------------------- dialog

  let dlg = null;

  function close() {
    if (!dlg) return;
    if (dlg.timer) clearTimeout(dlg.timer);
    dlg.root.remove();
    document.removeEventListener('keydown', dlg.onKey);
    dlg = null;
  }

  function open() {
    const S = F().state;
    if (!S.spec || !S.svg) { App.toast('Abra uma figura com dados antes de animar.', 'err'); return; }
    if (!(S.lpOptions || []).length) { App.toast('Esta figura não tem pontos de carga para animar.', 'err'); return; }
    close();
    const a = settings();
    const preview = el('div.anim-stage');
    const status = el('div.small.muted.anim-status');
    const counter = el('span.small.mono');
    const playBtn = el('button.btn.small.primary', { type: 'button' }, '▶ Reproduzir');
    const root = el('div.anim-overlay', el('div.anim-dialog', { role: 'dialog', 'aria-label': 'Animação da figura' },
      el('div.anim-head', el('h2', 'Animação da figura'), el('span.spacer'),
        el('button.btn.small.ghost', { type: 'button', title: 'Fechar (Esc)', on: { click: close } }, '✕')),
      el('div.anim-body', el('div.anim-controls'), el('div.anim-right', preview,
        el('div.toolbar.anim-player',
          el('button.btn.small', { type: 'button', title: 'Quadro anterior', on: { click: () => step(-1) } }, '◀'),
          playBtn,
          el('button.btn.small', { type: 'button', title: 'Próximo quadro', on: { click: () => step(1) } }, '▶|'),
          counter, el('span.spacer'),
          el('button.btn.small', { type: 'button', on: { click: () => rebuild() } }, 'Atualizar prévia')),
        status))));
    document.body.appendChild(root);
    dlg = { root, a, frames: [], i: 0, playing: false, timer: null };
    dlg.onKey = (e) => { if (e.key === 'Escape') close(); };
    document.addEventListener('keydown', dlg.onKey);

    const show = () => {
      clear(preview);
      const f = dlg.frames[dlg.i];
      if (!f) return;
      const svg = f.svg.cloneNode(true);
      svg.setAttribute('style', 'width:100%;height:auto;display:block');
      preview.appendChild(svg);
      counter.textContent = 'quadro ' + (dlg.i + 1) + ' de ' + dlg.frames.length;
    };
    const stop = () => { dlg.playing = false; if (dlg.timer) clearTimeout(dlg.timer); dlg.timer = null; playBtn.textContent = '▶ Reproduzir'; };
    const tick = () => {
      if (!dlg || !dlg.playing) return;
      const last = dlg.i === dlg.frames.length - 1;
      const ms = Number(a.dt) * 1000 * (last ? Math.max(1, Number(a.holdLast) || 1) : 1);
      dlg.timer = setTimeout(() => {
        if (!dlg || !dlg.playing) return;
        if (last && !a.loop) { stop(); return; }
        dlg.i = last ? 0 : dlg.i + 1;
        show(); tick();
      }, ms);
    };
    const step = (d) => { stop(); if (!dlg.frames.length) return; dlg.i = (dlg.i + d + dlg.frames.length) % dlg.frames.length; show(); };
    playBtn.addEventListener('click', () => {
      if (dlg.playing) { stop(); return; }
      if (!dlg.frames.length) return;
      dlg.playing = true; playBtn.textContent = '❚❚ Pausar'; tick();
    });
    const rebuild = async () => {
      stop();
      status.textContent = 'Preparando…';
      try {
        dlg.frames = await buildFrames(a, (t) => { status.textContent = t; });
        dlg.i = Math.min(dlg.i, dlg.frames.length - 1);
        status.textContent = dlg.frames.length + ' quadros prontos.';
        show();
      } catch (e) { status.textContent = ''; App.toast(e.message, 'err'); }
    };
    const changed = (needRebuild) => {
      try { F().persistDraft(); } catch (e) { /* ignore */ }
      drawControls();
      if (needRebuild) rebuild();
    };

    function drawControls() {
      const box = clear(root.querySelector('.anim-controls'));
      const lps = S.lpOptions || [];
      const lpText = (l) => 'Ponto ' + (l + 1) + (S.lpErl && S.lpErl[l] !== undefined ? ' · ' + App.fmtNum(S.lpErl[l], 0) + ' E' : '');
      const sel = (obj, key, opts, rebuildNeeded, num) => {
        const s = el('select.input', opts.map(([v, t]) => el('option', { value: String(v) }, t)));
        s.value = String(obj[key]);
        s.addEventListener('change', () => { obj[key] = num ? Number(s.value) : s.value; changed(rebuildNeeded); });
        return s;
      };
      const chk = (obj, key, label, rebuildNeeded) => {
        const c = el('input', { type: 'checkbox', checked: !!obj[key] });
        c.addEventListener('change', () => { obj[key] = c.checked; changed(rebuildNeeded); });
        return el('label.check', c, ' ' + label);
      };
      const fld = (label, input, help) => el('div.field', el('div.flabel', el('span', label)), input, help ? el('div.help', help) : null);

      // load points
      const chosen = a.lps.length ? lps.filter((l) => a.lps.includes(l)) : lps;
      const list = el('div.anim-lps', lps.map((l) => {
        const c = el('input', { type: 'checkbox', checked: chosen.includes(l) });
        c.addEventListener('change', () => {
          const now = lps.filter((x) => (x === l ? c.checked : (a.lps.length ? a.lps.includes(x) : true)));
          a.lps = now.length === lps.length ? [] : now;
          changed(true);
        });
        return el('label.check', c, ' ' + lpText(l));
      }));
      box.append(el('h3', 'Quadros'), fld('Pontos de carga (' + chosen.length + ' de ' + lps.length + ')', list));
      if (canAccumulate(S.spec)) {
        box.append(fld('Modo', sel(a, 'mode', [['cumulative', 'Acumulado: cada quadro acrescenta a próxima carga'], ['instant', 'Instantâneo: só a carga do quadro']], true)));
      } else {
        box.append(el('p.help.muted.small', 'Cada quadro mostra a figura num ponto de carga (o eixo x desta figura não é a carga).'));
      }
      box.append(fld('Eixos', sel(a, 'axes', [['fixed', 'Fixos: limites de todos os quadros'], ['adaptive', 'Adaptáveis: acompanham cada quadro']], true),
        'Limites definidos à mão na seção Eixos valem nos dois modos. Nos mapas, "fixos" mantém a mesma escala de cores em todos os quadros.'));

      box.append(el('h3', 'Tempo'));
      const tSel = el('select.input', TIMES.map((t) => el('option', { value: String(t) }, String(t).replace('.', ',') + ' s')).concat([el('option', { value: 'custom' }, 'Outro…')]));
      const tInp = el('input.input.num', { type: 'text', value: String(a.dt).replace('.', ','), style: { maxWidth: '90px' }, hidden: TIMES.includes(Number(a.dt)) });
      tSel.value = TIMES.includes(Number(a.dt)) ? String(a.dt) : 'custom';
      tSel.addEventListener('change', () => { if (tSel.value === 'custom') { tInp.hidden = false; tInp.focus(); return; } a.dt = Number(tSel.value); changed(false); });
      tInp.addEventListener('change', () => { const v = App.parseNumber(tInp.value); if (v > 0) { a.dt = v; changed(false); } });
      box.append(fld('Tempo por quadro', el('div.toolbar', { style: { margin: 0 } }, tSel, tInp)),
        fld('Último quadro', sel(a, 'holdLast', [[1, 'Mesmo tempo dos outros'], [2, '2 vezes mais tempo'], [3, '3 vezes mais tempo'], [4, '4 vezes mais tempo']], false, true)),
        el('div.checks', chk(a, 'loop', 'Repetir em ciclo (GIF e prévia)', false)));

      box.append(el('h3', 'Título da carga'), el('div.checks', chk(a.title, 'show', 'Mostrar o título com o ponto de carga', true)));
      if (a.title.show) {
        box.append(fld('Texto', sel(a.title, 'text', TEXTS.map(([v, t]) => [v, t === 'Personalizado' ? t : t.replace('{n}', '3').replace('{erl}', '1.200')]), true)));
        if (a.title.text === 'custom') {
          const inp = el('input.input', { type: 'text', value: a.title.custom });
          inp.addEventListener('change', () => { a.title.custom = inp.value; changed(true); });
          box.append(fld('Texto personalizado', inp, '{n} = número do ponto de carga, {erl} = carga em Erlangs.'));
        }
        const size = el('input.input.num', { type: 'text', value: a.title.size ? String(a.title.size) : '', placeholder: 'igual ao título', style: { maxWidth: '120px' } });
        size.addEventListener('change', () => { const v = App.parseNumber(size.value); a.title.size = v > 0 ? v : 0; changed(true); });
        box.append(fld('Posição', sel(a.title, 'pos', POSITIONS, true)), fld('Tamanho da fonte', size));
      }

      box.append(el('h3', 'Salvar'),
        fld('Formato', sel(a, 'format', FORMATS, false)),
        fld('Resolução', sel(a, 'scale', [[1, '1× (tamanho da figura)'], [2, '2×'], [3, '3×']], false, true)),
        fld('Quadros separados', sel(a, 'frames', [['none', 'Não'], ['png', 'PNG'], ['svg', 'SVG'], ['both', 'PNG e SVG']], false)),
        fld('Destino', sel(a, 'dest', [['download', 'Baixar pelo navegador'], ['folder', 'Gravar na pasta das imagens']], false)));
      if (a.dest === 'folder') {
        const cur = (App.state.info && App.state.info.settings && App.state.info.settings.imagesDir) || 'launcher/figures';
        box.append(el('div.toolbar', { style: { margin: 0 } }, el('span.small.mono.anim-folder', { title: cur }, cur),
          el('button.btn.small', { type: 'button', on: { click: async () => {
            const d = await App.pickFolder(cur, 'Pasta das imagens e animações');
            if (!d) return;
            try { await App.api.saveSettings({ imagesDir: d }); if (App.state.info) App.state.info.settings.imagesDir = d; drawControls(); } catch (e) { App.toast(e.message, 'err'); }
          } } }, 'Escolher pasta…')));
      }
      if ((a.format === 'mp4' || a.format === 'webm') && !videoMime(a.format)) {
        box.append(el('div.notice.warn', 'Este navegador não grava ' + a.format.toUpperCase() + '. Use ' + (a.format === 'mp4' ? 'WebM ou GIF.' : 'GIF.')));
      }
      if (a.format === 'mp4' || a.format === 'webm') box.append(el('p.help.muted.small', 'O vídeo é gravado em tempo real: leva o tempo da animação.'));
      const saveBtn = el('button.btn.primary', { type: 'button' }, 'Salvar animação');
      saveBtn.addEventListener('click', async () => {
        stop();
        saveBtn.disabled = true;
        try {
          if (!dlg.frames.length) await rebuild();
          const files = await exportAll(a, dlg.frames, (t) => { status.textContent = t; });
          status.textContent = 'Salvo: ' + files.join(', ');
          App.toast(a.dest === 'folder' ? 'Animação gravada na pasta das imagens' : 'Animação baixada', 'ok');
        } catch (e) { App.toast(e.message, 'err'); status.textContent = ''; }
        saveBtn.disabled = false;
      });
      box.append(el('div.toolbar', saveBtn));
    }

    drawControls();
    rebuild();
  }

  App.anim = { open, close, _test: { frameSpec, withTitle, encodeGif, lzw, buildPalette, canAccumulate } };
})(window.App);
