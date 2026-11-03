/*
 * Rendering engine of the chart studio (Estúdio de gráficos).
 * Pure functions: model + style -> standalone SVG element, with every visual
 * property taken from the style object (no CSS), so the exported SVG/PNG looks
 * exactly like the preview.
 *
 * Model kinds:
 *   line  : { series:[{key,name,style,points:[{x,mean,ci,n,label}]}], xCategories?:[...] }
 *   bars  : { categories:[{key,label}], series:[{key,name,style,values:{catKey:{mean,ci,n}}}], stacked, normalize }
 *   panels: { panels:[{title, model}] }    (small multiples of bars/line)
 *   cores : { diagrams:[{title, cores:[{id,x,y,util,block,unused}]}] }
 *   slots : { panels:[{title, from, to, rows:[{link, core, values:Float64Array}]}], range:{min,max}, legendTitle }
 *   ref   : { refLabel, mode, cols, sections:[{key, label, color, refVal, bars:[{key, label, val, diff, color}]}] } (1.18.3)
 */
(function (App) {
  'use strict';

  const NS = 'http://www.w3.org/2000/svg';
  function S(tag, attrs, text) {
    const n = document.createElementNS(NS, tag);
    if (attrs) for (const k in attrs) { const v = attrs[k]; if (v !== undefined && v !== null && v !== '') n.setAttribute(k, v); }
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }

  // ------------------------------------------------------------ palettes & presets

  const PALETTES = {
    office: ['#4472C4', '#ED7D31', '#A5A5A5', '#FFC000', '#5B9BD5', '#70AD47', '#264478', '#9E480E', '#636363', '#997300', '#255E91', '#43682B', '#698ED0', '#F1975A', '#B7B7B7', '#FFCD33', '#7CAFDD', '#8CC168', '#C00000', '#7030A0'],
    artigo: ['#E31A1C', '#33A02C', '#6A3D9A', '#1F78B4', '#FF7F00', '#B15928', '#A6CEE3', '#B2DF8A', '#FB9A99', '#FDBF6F', '#CAB2D6', '#FFD92F', '#000000', '#666666', '#1B9E77', '#D95F02', '#7570B3', '#E7298A', '#66A61E', '#E6AB02'],
    tableau: ['#4E79A7', '#F28E2B', '#E15759', '#76B7B2', '#59A14F', '#EDC948', '#B07AA1', '#FF9DA7', '#9C755F', '#BAB0AC', '#86BCB6', '#FABFD2', '#8CD17D', '#B6992D', '#499894', '#D37295', '#79706E', '#D4A6C8', '#A0CBE8', '#FFBE7D'],
    okabe: ['#0072B2', '#E69F00', '#009E73', '#CC79A7', '#56B4E9', '#D55E00', '#F0E442', '#000000', '#999999', '#004C7A', '#9A6B00', '#006B4E', '#8A527A', '#3B7AA0', '#8F3F00', '#A39B2F', '#555555', '#7FB8D8', '#F2CF80', '#7FCEB9'],
    cinza: ['#000000', '#555555', '#888888', '#AAAAAA', '#333333', '#777777', '#999999', '#BBBBBB', '#222222', '#666666', '#444444', '#CCCCCC'],
  };
  const PALETTE_NAMES = { office: 'Excel (Office)', artigo: 'Artigo (cores fortes)', tableau: 'Tableau', okabe: 'Okabe-Ito (daltonismo)', cinza: 'Tons de cinza (impressão)' };
  const MARKERS = ['circle', 'square', 'diamond', 'triangle', 'triangleDown', 'plus', 'x', 'star', 'none'];
  const MARKER_NAMES = { circle: 'Círculo', square: 'Quadrado', diamond: 'Losango', triangle: 'Triângulo', triangleDown: 'Triângulo invertido', plus: 'Mais (+)', x: 'Xis (×)', star: 'Estrela', none: 'Sem marcador' };
  const DASHES = { solid: 'Contínua', dashed: 'Tracejada', dotted: 'Pontilhada', dashdot: 'Traço-ponto', longdash: 'Traço longo' };
  const FONTS = ['Calibri, Carlito, Arial, sans-serif', 'Arial, Helvetica, sans-serif', '"Times New Roman", Times, serif', '"Segoe UI", system-ui, sans-serif', 'Helvetica, Arial, sans-serif', 'Georgia, serif', '"Courier New", monospace'];

  function defaultStyle() {
    return {
      width: 760, height: 440, bg: '#ffffff', plotBg: '#ffffff', border: false, borderColor: '#d9d9d9',
      font: FONTS[0], fontSize: 12, titleSize: 15, textColor: '#404040', titleColor: '#262626',
      title: '', showTitle: true, xTitle: '', yTitle: '', panelLabel: '', panelLabelSize: 16,
      grid: 'y', gridColor: '#d9d9d9', gridDash: 'solid', axisColor: '#808080', ticksOut: false,
      legend: 'bottom', legendCols: 0, legendBox: true, legendSize: 0, legendBg: '#ffffff',
      yMin: '', yMax: '', yLog: false, yFormat: 'auto', yDigits: 3, yStep: '', decimal: ',',
      xMin: '', xMax: '', xFormat: 'auto', xDigits: 0, xLabelRotate: 0, xAsCategories: true,
      errorBars: 'bars', capWidth: 4, errorWidth: 1.2,
      lineWidth: 2, markerSize: 7, palette: 'office',
      barGap: 0.35, seriesGap: 0.04, barValues: false, barValueDigits: 3, barOutline: false,
      // 1.16.0: x step, extra ticks, reference lines, values on line points, maximum bar width
      xStep: '', yTicksExtra: '', xTicksExtra: '', refLines: [], lineValues: false, lineValueDigits: 3, valueSize: 0, barMaxPx: '',
      // 1.17.0: look of the value labels (bars and line points); a series can override them in section 3
      valColor: 'auto', valBold: false, valBox: 'none', valBoxFill: '#ffffff', valBoxStroke: '#7f7f7f', valBoxOpacity: 0.85,
      valPos: 'auto', valStacked: 'center', valRotate: 'auto', valLinePos: 'above', valMin: '',
      panelCols: 1, panelGap: 18, panelHeight: 0,
      // cores diagram
      coreCols: 2, coreFiber: '#ededed', coreFiberStroke: '#9a9a9a', coreFill: '#ffffff', coreStroke: '#000000',
      coreBlockColor: '#f4b183', coreUtilColor: '#9dc3e6', coreShow: 'both', coreDigits: 6, coreHideUnused: true,
      coreMarkQuantum: true, coreMarkDisabled: true, coreQuantumColor: '#d9c6f5', coreDisabledColor: '#d32f2f',
      coreHeat: 'none', coreHeatColor: '#2f75b5', coreLegend: true, coreLabelSize: 0,
      // topology / link heat map
      topoCols: 3, topoNodeR: 11, topoLinkWidth: 5, topoDir: 'both', topoLabels: 'none', topoLegend: 'right',
      topoFormat: 'percent', topoDigits: 1, topoMin: '', topoMax: '', topoNodeFill: '#ffffff', topoNodeStroke: '#000000',
      topoNoData: '#bdbdbd', topoLabelColor: '#c00000', topoAutoHeight: true, topoPanelBorder: false,
      topoTrusted: true, topoTrustedFill: '#7030a0', topoTrustedR: 0,
      topoLabelStyle: 'plain', topoLabelFill: '#ffffff', topoLabelStroke: '', topoLabelSize: '', topoLabelDist: '', topoLabelBold: true,
      topoLabelAvoid: true, topoLabelOpacity: 0.92, topoKmUnit: false, topoLabelOffsets: {},
      slotCauseColors: {},
      // spectrum (slot) heat map
      slotScale: 'traffic', slotRange: 'fixed', slotMin: '', slotMax: '', slotRowH: 12, slotTickStep: '', slotLegend: 'right',
      // 1.18.3: chart with a reference case
      refRowH: 72, refBoxes: true, refOutline: true, refTicks: true, refShowRefValue: true, refShared: true, refDigits: '', refFormat: 'fixed', refTitleSize: '', refColorTitle: true,
      refNameBox: true, refValueBox: true, refTitleBox: true, refNameBold: true, refValueBold: true, refNameSize: '', refValueSize: '', refBarPx: '', refNameGap: 8, refNameDx: 12, refValueGap: 10,
      slotFormat: 'percent', slotDigits: 1, slotGridColor: '#000000', slotZero: 'scale', slotNoData: '#FFFFFF', slotCellGrid: true, slotLinkSep: true, slotPanelTitle: true,
    };
  }

  // ------------------------------------------------------------ number formatting

  function fmtNumber(v, mode, digits, dec, step) {
    if (v === null || v === undefined || !Number.isFinite(v)) return '';
    let t;
    if (mode === 'percent') {
      const d = Number.isFinite(digits) ? digits : 1;
      t = (v * 100).toFixed(d) + '%';
    } else if (mode === 'sci') {
      t = v === 0 ? '0' : v.toExponential(Number.isFinite(digits) ? digits : 2).replace('e', 'E').replace('E+', 'E+');
    } else if (mode === 'fixed') {
      t = v.toFixed(Number.isFinite(digits) ? digits : 3);
    } else {
      // auto: decimals taken from the tick step when available
      const a = Math.abs(v);
      if (a !== 0 && (a < 1e-4 || a >= 1e7)) t = v.toExponential(2).replace('e', 'E');
      else if (step) {
        t = v.toFixed(Math.min(12, stepDecimals(step)));
      } else {
        t = String(+v.toPrecision(Number.isFinite(digits) && digits > 0 ? digits : 4));
        if (/e/.test(t)) t = v.toExponential(2).replace('e', 'E');
      }
    }
    return dec === ',' ? t.replace('.', ',') : t;
  }

  function niceStep(span, count) {
    const raw = span / Math.max(1, count);
    const mag = Math.pow(10, Math.floor(Math.log10(raw)));
    const n = raw / mag;
    return (n < 1.5 ? 1 : n < 2.25 ? 2 : n < 3.5 ? 2.5 : n < 7.5 ? 5 : 10) * mag;
  }
  function stepDecimals(step) {
    const t = String(+step.toPrecision(6));
    if (/e-/.test(t)) return Math.min(12, Number(t.split('e-')[1]) + ((t.split('e')[0].split('.')[1] || '').length));
    return (t.split('.')[1] || '').length;
  }
  function linearTicks(min, max, count, fixedStep) {
    if (!(max > min)) { max = min + 1; }
    let step = fixedStep > 0 ? fixedStep : niceStep(max - min, count);
    if ((max - min) / step > 200) step = niceStep(max - min, count);
    const out = [];
    const start = Math.ceil(min / step - 1e-9) * step;
    for (let v = start; v <= max + step * 1e-6; v += step) out.push(+v.toPrecision(12));
    return { ticks: out, step };
  }

  /** 1.16.0: "0,05; 0,15" -> [0.05, 0.15] (extra ticks typed by the user). */
  function parseList(text) {
    return String(text || '').split(/[;\s]+/).map((t) => App.parseNumber(t)).filter(Number.isFinite);
  }
  function mergeTicks(ticks, extra, min, max) {
    const add = extra.filter((v) => v >= min - 1e-12 && v <= max + 1e-12 && !ticks.some((t) => Math.abs(t - v) <= Math.abs(v) * 1e-9 + 1e-15));
    return add.length ? ticks.concat(add).sort((a, b) => a - b) : ticks;
  }
  /** Reference lines with a valid value; axis 'y' or 'x'. */
  function refLinesOf(st, axis) {
    return (Array.isArray(st.refLines) ? st.refLines : []).filter((r) => r && r.show !== false && (r.axis || 'y') === axis && Number.isFinite(App.parseNumber(String(r.value))));
  }
  function drawRefLines(root, f, st, xPos) {
    const fs = st.fontSize;
    const g = S('g');
    const P = f.plot;
    for (const r of refLinesOf(st, 'y')) {
      const v = App.parseNumber(String(r.value));
      const y = f.ys(v);
      if (!(y >= P.y - 0.5 && y <= P.y + P.h + 0.5)) continue;
      const w = Number(r.width) > 0 ? Number(r.width) : 1.4;
      g.appendChild(S('line', { x1: P.x, x2: P.x + P.w, y1: y.toFixed(1), y2: y.toFixed(1), stroke: r.color || '#c00000', 'stroke-width': w, 'stroke-dasharray': dashArray(r.dash || 'dashed', w) }));
      if (r.label) g.appendChild(textEl(r.pos === 'left' ? P.x + 4 : P.x + P.w - 4, y - 4, r.label, { size: fs * 0.9, font: st.font, color: r.color || '#c00000', anchor: r.pos === 'left' ? 'start' : 'end' }));
    }
    if (xPos) {
      for (const r of refLinesOf(st, 'x')) {
        const x = xPos(App.parseNumber(String(r.value)));
        if (!(x >= P.x - 0.5 && x <= P.x + P.w + 0.5)) continue;
        const w = Number(r.width) > 0 ? Number(r.width) : 1.4;
        g.appendChild(S('line', { x1: x.toFixed(1), x2: x.toFixed(1), y1: P.y, y2: P.y + P.h, stroke: r.color || '#c00000', 'stroke-width': w, 'stroke-dasharray': dashArray(r.dash || 'dashed', w) }));
        if (r.label) g.appendChild(textEl(x + 4, P.y + fs, r.label, { size: fs * 0.9, font: st.font, color: r.color || '#c00000', anchor: 'start' }));
      }
    }
    root.appendChild(g);
  }

  // ------------------------------------------------------------ text measurement

  let measureCtx = null;
  function textWidth(text, size, font, weight) {
    if (!measureCtx) measureCtx = document.createElement('canvas').getContext('2d');
    measureCtx.font = (weight || 'normal') + ' ' + size + 'px ' + font;
    return measureCtx.measureText(String(text)).width;
  }

  // ------------------------------------------------------------ marks

  function dashArray(kind, w) {
    const k = Math.max(1, w);
    switch (kind) {
      case 'dashed': return (4 * k) + ' ' + (2.5 * k);
      case 'dotted': return '0.1 ' + (2.2 * k);
      case 'dashdot': return (4.5 * k) + ' ' + (2 * k) + ' 0.1 ' + (2 * k);
      case 'longdash': return (8 * k) + ' ' + (3 * k);
      default: return null;
    }
  }

  // ------------------------------------------------------------ 1.17.0: bar patterns (like Excel's pattern fill)

  const PATTERNS = {
    none: 'Sólida (sem padrão)', diagUp: 'Diagonal ／', diagDown: 'Diagonal ＼', cross: 'Diagonais cruzadas', horiz: 'Listras horizontais',
    vert: 'Listras verticais', grid: 'Grade', dots: 'Pontos', dotsDense: 'Pontos densos', brick: 'Tijolos', zigzag: 'Zigue-zague',
  };
  let patSeq = 0;

  function contrastColor(c) {
    const [r, g, b] = hexRgb(String(c || '#ffffff'));
    return 0.299 * r + 0.587 * g + 0.114 * b > 150 ? '#000000' : '#ffffff';
  }

  /** Fill of a bar: the plain colour, or url(#pattern) of a pattern drawn over the colour (defs added to parent). */
  function patternFill(parent, color, sty) {
    const kind = sty && sty.pattern;
    if (!kind || kind === 'none' || !PATTERNS[kind]) return color;
    const z = Math.max(3, Number(sty.patternSize) || 8);
    const fg = sty.patternColor || contrastColor(color);
    const sw = Number(sty.patternWidth) > 0 ? Number(sty.patternWidth) : Math.max(0.9, z / 7);
    const id = 'pat' + (++patSeq);
    const pat = S('pattern', { id, patternUnits: 'userSpaceOnUse', width: z, height: z });
    pat.appendChild(S('rect', { x: 0, y: 0, width: z, height: z, fill: color }));
    const line = (d) => pat.appendChild(S('path', { d, stroke: fg, 'stroke-width': sw, fill: 'none', 'stroke-linecap': 'square' }));
    const dot = (cx, cy, r) => pat.appendChild(S('circle', { cx, cy, r, fill: fg }));
    switch (kind) {
      case 'diagUp': line(`M0 ${z} L${z} 0 M${-z / 2} ${z / 2} L${z / 2} ${-z / 2} M${z / 2} ${z * 1.5} L${z * 1.5} ${z / 2}`); break;
      case 'diagDown': line(`M0 0 L${z} ${z} M${-z / 2} ${z / 2} L${z / 2} ${z * 1.5} M${z / 2} ${-z / 2} L${z * 1.5} ${z / 2}`); break;
      case 'cross': line(`M0 ${z} L${z} 0 M0 0 L${z} ${z}`); break;
      case 'horiz': line(`M0 ${z / 2} L${z} ${z / 2}`); break;
      case 'vert': line(`M${z / 2} 0 L${z / 2} ${z}`); break;
      case 'grid': line(`M0 ${z / 2} L${z} ${z / 2} M${z / 2} 0 L${z / 2} ${z}`); break;
      case 'dots': dot(z / 2, z / 2, Math.max(0.8, z / 6)); break;
      case 'dotsDense': dot(z / 4, z / 4, Math.max(0.6, z / 8)); dot(3 * z / 4, 3 * z / 4, Math.max(0.6, z / 8)); break;
      case 'brick': line(`M0 0.5 L${z} 0.5 M0 ${z / 2 + 0.5} L${z} ${z / 2 + 0.5} M0.5 0 L0.5 ${z / 2} M${z / 2 + 0.5} ${z / 2} L${z / 2 + 0.5} ${z}`); break;
      case 'zigzag': line(`M0 ${z * 0.75} L${z / 2} ${z * 0.25} L${z} ${z * 0.75}`); break;
      default: break;
    }
    const defs = S('defs');
    defs.appendChild(pat);
    parent.appendChild(defs);
    return `url(#${id})`;
  }

  // ------------------------------------------------------------ 1.17.0: value labels

  /** Look of the value labels of one series: the global style (section 5) with the overrides of the series. */
  function valueLook(st, sty, isLine) {
    const o = sty || {};
    const has = (k) => o[k] !== undefined && o[k] !== '' && o[k] !== 'inherit';
    const color = has('valColor') ? o.valColor : (st.valColor || 'auto');
    return {
      color: color === 'auto' ? (isLine ? 'series' : 'text') : color,
      bold: has('valBold') ? o.valBold === 'yes' || o.valBold === true : !!st.valBold,
      box: has('valBox') ? o.valBox : (st.valBox || 'none'),
      boxFill: has('valBoxFill') ? o.valBoxFill : (st.valBoxFill || '#ffffff'),
      boxStroke: has('valBoxStroke') ? o.valBoxStroke : (st.valBoxStroke || '#7f7f7f'),
      pos: has('valPos') ? o.valPos : (isLine ? (st.valLinePos || 'above') : (st.valPos || 'auto')),
      show: has('valShow') ? o.valShow !== 'hide' : true,
    };
  }

  function valueHidden(st, v) {
    const m = App.parseNumber(String(st.valMin === undefined ? '' : st.valMin));
    return Number.isFinite(m) && m > 0 && Math.abs(v) < m;
  }

  /**
   * Draws one value label. o: {size, font, anchor ('start'|'middle'|'end'), under (colour behind the text, for the
   * automatic contrast), series (colour of the series), rotate (degrees)}. y is the text baseline.
   */
  function valueText(parent, x, y, str, look, st, o) {
    const size = o.size;
    const weight = look.bold ? 700 : null;
    const tw = textWidth(str, size, st.font, weight || 'normal');
    const padX = 3, padY = 2;
    const boxed = look.box === 'box';
    const under = boxed ? look.boxFill : (o.under || st.plotBg || st.bg);
    const color = look.color === 'series' ? (o.series || st.textColor) : look.color === 'contrast' ? contrastColor(under)
      : look.color === 'text' ? st.textColor : look.color;
    const g = S('g', o.rotate ? { transform: `rotate(${o.rotate} ${x.toFixed(1)} ${y.toFixed(1)})` } : null);
    if (boxed) {
      const x0 = o.anchor === 'middle' ? x - tw / 2 - padX : o.anchor === 'end' ? x - tw - padX : x - padX;
      g.appendChild(S('rect', { x: x0.toFixed(1), y: (y - size * 0.85 - padY).toFixed(1), width: (tw + 2 * padX).toFixed(1), height: (size * 1.1 + 2 * padY).toFixed(1),
        rx: 2, fill: look.boxFill, 'fill-opacity': st.valBoxOpacity !== undefined && st.valBoxOpacity !== '' ? Number(st.valBoxOpacity) : 0.85, stroke: look.boxStroke, 'stroke-width': 0.6 }));
    }
    g.appendChild(textEl(x, y, str, { size, font: st.font, color, anchor: o.anchor || 'middle', weight }));
    parent.appendChild(g);
    return { w: tw + (boxed ? 2 * padX : 0), h: size * 1.1 + (boxed ? 2 * padY : 0) };
  }

  function marker(shape, x, y, size, color, hollow, bg, strokeW) {
    const r = size / 2;
    const fill = hollow ? bg : color;
    const sw = strokeW || Math.max(1.2, size / 6);
    const base = { fill, stroke: color, 'stroke-width': sw, 'stroke-linejoin': 'round' };
    const poly = (pts) => S('polygon', Object.assign({ points: pts.map((p) => (x + p[0]).toFixed(2) + ',' + (y + p[1]).toFixed(2)).join(' ') }, base));
    switch (shape) {
      case 'none': return null;
      case 'square': return S('rect', Object.assign({ x: (x - r * 0.88).toFixed(2), y: (y - r * 0.88).toFixed(2), width: (r * 1.76).toFixed(2), height: (r * 1.76).toFixed(2) }, base));
      case 'diamond': return poly([[0, -r * 1.15], [r * 1.15, 0], [0, r * 1.15], [-r * 1.15, 0]]);
      case 'triangle': return poly([[0, -r * 1.15], [r * 1.1, r * 0.8], [-r * 1.1, r * 0.8]]);
      case 'triangleDown': return poly([[0, r * 1.15], [r * 1.1, -r * 0.8], [-r * 1.1, -r * 0.8]]);
      case 'star': {
        const pts = [];
        for (let i = 0; i < 10; i++) {
          const a = -Math.PI / 2 + i * Math.PI / 5;
          const rr = i % 2 ? r * 0.5 : r * 1.2;
          pts.push([Math.cos(a) * rr, Math.sin(a) * rr]);
        }
        return poly(pts);
      }
      case 'plus': case 'x': {
        const g = S('g', { stroke: color, 'stroke-width': Math.max(1.6, size / 4.5), 'stroke-linecap': 'round' });
        const d = r * 1.05;
        if (shape === 'plus') {
          g.appendChild(S('line', { x1: x - d, x2: x + d, y1: y, y2: y }));
          g.appendChild(S('line', { x1: x, x2: x, y1: y - d, y2: y + d }));
        } else {
          const e = d * 0.8;
          g.appendChild(S('line', { x1: x - e, x2: x + e, y1: y - e, y2: y + e }));
          g.appendChild(S('line', { x1: x - e, x2: x + e, y1: y + e, y2: y - e }));
        }
        return g;
      }
      default: return S('circle', Object.assign({ cx: x.toFixed(2), cy: y.toFixed(2), r: r.toFixed(2) }, base));
    }
  }

  /** Native tooltip (shown on hover in the launcher). */
  function tip(name, p, st, share) {
    const f = (v) => fmtNumber(v, 'auto', 6, st.decimal);
    let t = name + ': ' + f(p.mean);
    if (Number.isFinite(p.ci)) t += ' ± ' + f(p.ci) + ' (IC 95 %)';
    if (p.n) t += ' · n = ' + p.n;
    if (share !== null && share !== undefined) t += ' · ' + (share * 100).toFixed(1) + ' %';
    if (p.x !== undefined && p.label) t += ' · ' + p.label;
    return S('title', null, t);
  }

  function textEl(x, y, str, o) {
    o = o || {};
    const t = S('text', {
      x: x.toFixed ? x.toFixed(1) : x, y: y.toFixed ? y.toFixed(1) : y,
      'font-size': o.size, 'font-family': o.font, fill: o.color, 'text-anchor': o.anchor || 'start',
      'font-weight': o.weight, 'dominant-baseline': o.baseline, transform: o.transform, 'font-style': o.italic ? 'italic' : null,
    }, str);
    return t;
  }

  // ------------------------------------------------------------ legend

  function legendItemSwatch(item, st, x, y) {
    const g = S('g');
    if (item.kind === 'bar') {
      const pat = item.pattern && item.pattern !== 'none';
      const sz = pat ? 13 : 11;
      g.appendChild(S('rect', { x, y: y - sz / 2, width: sz, height: sz, fill: patternFill(g, item.color, item), stroke: st.barOutline || pat ? '#000' : null, 'stroke-width': st.barOutline || pat ? 0.6 : null }));
      return { node: g, w: sz + 4 };
    }
    if (item.kind === 'box') {
      g.appendChild(S('rect', { x, y: y - 6, width: 22, height: 12, fill: item.color, stroke: '#000', 'stroke-width': 0.4 }));
      return { node: g, w: 26 };
    }
    const w = 30;
    if (item.line !== false) {
      g.appendChild(S('line', { x1: x, x2: x + w, y1: y, y2: y, stroke: item.color, 'stroke-width': item.width, 'stroke-dasharray': dashArray(item.dash, item.width), 'stroke-linecap': item.dash === 'dotted' ? 'round' : 'butt' }));
    }
    const m = marker(item.marker, x + w / 2, y, Math.min(item.markerSize, 10), item.color, item.hollow, st.legendBg || st.bg);
    if (m) g.appendChild(m);
    return { node: g, w: w + 5 };
  }

  /** Measures and builds a legend block; returns {w,h,draw(x,y)}. maxW limits the row width. */
  function buildLegend(items, st, maxW, forcedCols) {
    const fs = st.legendSize > 0 ? st.legendSize : Math.max(9, st.fontSize - 1);
    const rowH = Math.max(fs * 1.45, 15);
    const widths = items.map((it) => (it.kind === 'bar' ? (it.pattern && it.pattern !== 'none' ? 17 : 15) : it.kind === 'box' ? 26 : 35) + textWidth(it.name, fs, st.font) + 14);
    const colW = Math.max(...widths, 40);
    let cols = forcedCols || st.legendCols;
    if (!cols) cols = Math.max(1, Math.min(items.length, Math.floor((maxW - 12) / colW)));
    cols = Math.max(1, Math.min(cols, items.length));
    const rows = Math.ceil(items.length / cols);
    // column widths: max per column
    const cw = [];
    for (let c = 0; c < cols; c++) {
      let m = 0;
      for (let r = 0; r < rows; r++) { const i = r * cols + c; if (i < items.length) m = Math.max(m, widths[i]); }
      cw.push(m);
    }
    const pad = st.legendBox ? 8 : 2;
    const w = cw.reduce((a, b) => a + b, 0) + pad * 2 - 8;
    const h = rows * rowH + pad * 2 - (rowH - fs * 1.2) * 0.3;
    return {
      w, h,
      draw(x, y) {
        const g = S('g', { class: 'legend' });
        if (st.legendBox) g.appendChild(S('rect', { x, y, width: w, height: h, fill: st.legendBg || st.bg, stroke: '#bfbfbf', 'stroke-width': 0.8 }));
        else if (st.legendBg && st.legendBg !== st.bg) g.appendChild(S('rect', { x, y, width: w, height: h, fill: st.legendBg }));
        items.forEach((it, i) => {
          const r = Math.floor(i / cols), c = i % cols;
          const ix = x + pad + cw.slice(0, c).reduce((a, b) => a + b, 0);
          const iy = y + pad + r * rowH + rowH / 2;
          const sw = legendItemSwatch(it, st, ix, iy);
          g.appendChild(sw.node);
          g.appendChild(textEl(ix + sw.w, iy + fs * 0.35, it.name, { size: fs, font: st.font, color: st.textColor }));
        });
        return g;
      },
    };
  }

  // ------------------------------------------------------------ cartesian frame

  /**
   * Draws title, legend, axes and grid; returns the plotting rectangle and scales.
   * xSpec: {type:'num', min,max, ticks:[...]} | {type:'band', labels:[...]}
   */
  function frame(root, st, box, ySpec, xSpec, legendItems, opts) {
    opts = opts || {};
    const fs = st.fontSize;
    const font = st.font;
    let top = box.y + 8, bottom = box.y + box.h - 8, left = box.x + 8, right = box.x + box.w - 10;

    // panel label "(a)"
    if (st.panelLabel && !opts.noPanelLabel) {
      root.appendChild(textEl(box.x + 6, box.y + st.panelLabelSize + 2, st.panelLabel, { size: st.panelLabelSize, font, color: st.titleColor, weight: 700 }));
    }
    const title = opts.title !== undefined ? opts.title : st.title;
    if (st.showTitle && title) {
      const tfs = opts.titleSize || st.titleSize;
      root.appendChild(textEl(box.x + box.w / 2, top + tfs * 0.85, title, { size: tfs, font, color: st.titleColor, anchor: 'middle', weight: 700 }));
      top += tfs * 1.5;
    } else if (st.panelLabel && !opts.noPanelLabel) top += st.panelLabelSize * 0.6;

    // legend
    let legend = null;
    const lpos = opts.legend !== undefined ? opts.legend : st.legend;
    if (legendItems && legendItems.length && lpos !== 'none') {
      if (lpos === 'right') {
        legend = buildLegend(legendItems, st, box.w * 0.4, st.legendCols || 1);
        legend.x = right - legend.w; legend.y = top + Math.max(0, (bottom - top - legend.h) / 2);
        right -= legend.w + 12;
      } else if (lpos === 'top') {
        legend = buildLegend(legendItems, st, box.w - 20);
        legend.x = box.x + (box.w - legend.w) / 2; legend.y = top;
        top += legend.h + 8;
      } else if (lpos === 'bottom') {
        legend = buildLegend(legendItems, st, box.w - 20);
        legend.x = box.x + (box.w - legend.w) / 2; legend.y = bottom - legend.h;
        bottom -= legend.h + 10;
      } else {
        legend = buildLegend(legendItems, st, box.w * 0.5);
        legend.inside = lpos;
      }
    }

    // y axis
    const yTitle = opts.yTitle !== undefined ? opts.yTitle : st.yTitle;
    if (yTitle) left += fs * 1.6;
    const yInfo = ySpec;
    const yLabels = yInfo.ticks.map((t) => yInfo.fmt(t));
    const yLabW = Math.max(0, ...yLabels.map((l) => textWidth(l, fs, font)));
    left += yLabW + 8;

    // x axis labels height
    const xTitle = opts.xTitle !== undefined ? opts.xTitle : st.xTitle;
    if (xTitle) bottom -= fs * 1.7;
    const rot = Number(st.xLabelRotate) || 0;
    const xLabels = xSpec.type === 'band' ? xSpec.labels : xSpec.ticks.map((t) => xSpec.fmt(t));
    const xLabMax = Math.max(0, ...xLabels.map((l) => textWidth(l, fs, font)));
    const xLabH = rot === 0 ? fs * 1.35 : Math.abs(Math.sin(rot * Math.PI / 180)) * xLabMax + fs * 1.1;
    bottom -= xLabH + 4;
    if (rot === 0 && xLabels.length) right -= Math.min(20, textWidth(xLabels[xLabels.length - 1], fs, font) / 2);

    const plot = { x: left, y: top + 4, w: Math.max(30, right - left), h: Math.max(30, bottom - top - 4) };
    if (st.plotBg && st.plotBg !== st.bg) root.appendChild(S('rect', { x: plot.x, y: plot.y, width: plot.w, height: plot.h, fill: st.plotBg }));

    // scales
    const ys = yInfo.log
      ? (v) => plot.y + plot.h - (Math.log10(Math.max(v, yInfo.min)) - Math.log10(yInfo.min)) / (Math.log10(yInfo.max) - Math.log10(yInfo.min)) * plot.h
      : (v) => plot.y + plot.h - (v - yInfo.min) / (yInfo.max - yInfo.min || 1) * plot.h;
    let xs, band = null;
    if (xSpec.type === 'band') {
      const n = Math.max(1, xSpec.labels.length);
      const bw = plot.w / n;
      band = { bw, n };
      xs = (i) => plot.x + bw * (i + 0.5);
    } else {
      xs = (v) => plot.x + (v - xSpec.min) / (xSpec.max - xSpec.min || 1) * plot.w;
    }

    // grid
    const dashGrid = st.gridDash === 'solid' ? null : dashArray(st.gridDash, 0.8);
    const gridG = S('g');
    if (st.grid === 'y' || st.grid === 'xy') {
      for (const t of yInfo.ticks) {
        const y = ys(t);
        if (y < plot.y - 0.5 || y > plot.y + plot.h + 0.5) continue;
        gridG.appendChild(S('line', { x1: plot.x, x2: plot.x + plot.w, y1: y.toFixed(1), y2: y.toFixed(1), stroke: st.gridColor, 'stroke-width': 0.8, 'stroke-dasharray': dashGrid }));
      }
      if (yInfo.log && yInfo.minor) {
        for (const t of yInfo.minor) {
          const y = ys(t);
          gridG.appendChild(S('line', { x1: plot.x, x2: plot.x + plot.w, y1: y.toFixed(1), y2: y.toFixed(1), stroke: st.gridColor, 'stroke-width': 0.4, 'stroke-dasharray': '2 3' }));
        }
      }
    }
    if (st.grid === 'x' || st.grid === 'xy') {
      if (xSpec.type === 'band') {
        for (let i = 0; i <= band.n; i++) { const x = plot.x + i * band.bw; gridG.appendChild(S('line', { x1: x, x2: x, y1: plot.y, y2: plot.y + plot.h, stroke: st.gridColor, 'stroke-width': 0.8, 'stroke-dasharray': dashGrid })); }
      } else {
        for (const t of xSpec.ticks) { const x = xs(t); gridG.appendChild(S('line', { x1: x.toFixed(1), x2: x.toFixed(1), y1: plot.y, y2: plot.y + plot.h, stroke: st.gridColor, 'stroke-width': 0.8, 'stroke-dasharray': dashGrid })); }
      }
    }
    root.appendChild(gridG);

    // axes
    const ax = S('g');
    ax.appendChild(S('line', { x1: plot.x, x2: plot.x + plot.w, y1: plot.y + plot.h, y2: plot.y + plot.h, stroke: st.axisColor, 'stroke-width': 1 }));
    if (st.border) ax.appendChild(S('rect', { x: plot.x, y: plot.y, width: plot.w, height: plot.h, fill: 'none', stroke: st.axisColor, 'stroke-width': 1 }));
    else if (st.grid === 'none' || st.grid === 'x') ax.appendChild(S('line', { x1: plot.x, x2: plot.x, y1: plot.y, y2: plot.y + plot.h, stroke: st.axisColor, 'stroke-width': 1 }));
    yInfo.ticks.forEach((t, i) => {
      const y = ys(t);
      if (y < plot.y - 0.5 || y > plot.y + plot.h + 0.5) return;
      if (st.ticksOut) ax.appendChild(S('line', { x1: plot.x - 4, x2: plot.x, y1: y, y2: y, stroke: st.axisColor }));
      ax.appendChild(textEl(plot.x - 6, y + fs * 0.35, yLabels[i], { size: fs, font, color: st.textColor, anchor: 'end' }));
    });
    const xly = plot.y + plot.h + fs * 1.15 + 2;
    const xlab = (x, label) => {
      if (st.ticksOut) ax.appendChild(S('line', { x1: x, x2: x, y1: plot.y + plot.h, y2: plot.y + plot.h + 4, stroke: st.axisColor }));
      if (rot === 0) ax.appendChild(textEl(x, xly, label, { size: fs, font, color: st.textColor, anchor: 'middle' }));
      else ax.appendChild(textEl(x, plot.y + plot.h + 6, label, { size: fs, font, color: st.textColor, anchor: rot < 0 ? 'end' : 'start', transform: `rotate(${rot} ${x.toFixed(1)} ${(plot.y + plot.h + 6).toFixed(1)})`, baseline: 'middle' }));
    };
    if (xSpec.type === 'band') xSpec.labels.forEach((l, i) => xlab(xs(i), l));
    else xSpec.ticks.forEach((t, i) => { const x = xs(t); if (x >= plot.x - 0.5 && x <= plot.x + plot.w + 0.5) xlab(x, xLabels[i]); });
    if (xTitle) ax.appendChild(textEl(plot.x + plot.w / 2, plot.y + plot.h + xLabH + fs * 1.6 + 2, xTitle, { size: fs + 0.5, font, color: st.textColor, anchor: 'middle', weight: 600 }));
    if (yTitle) {
      const cx = box.x + 8 + fs * 0.9, cy = plot.y + plot.h / 2;
      ax.appendChild(textEl(cx, cy, yTitle, { size: fs + 0.5, font, color: st.textColor, anchor: 'middle', weight: 600, transform: `rotate(-90 ${cx.toFixed(1)} ${cy.toFixed(1)})` }));
    }
    root.appendChild(ax);

    return {
      plot, xs, ys, band,
      finish() {
        if (!legend) return;
        if (legend.inside) {
          const lx = legend.inside === 'inside-tl' || legend.inside === 'inside-bl' ? plot.x + 8 : plot.x + plot.w - legend.w - 8;
          const ly = legend.inside === 'inside-bl' || legend.inside === 'inside-br' ? plot.y + plot.h - legend.h - 8 : plot.y + 8;
          root.appendChild(legend.draw(lx, ly));
        } else root.appendChild(legend.draw(legend.x, legend.y));
      },
    };
  }

  function yScaleSpec(values, st, opts) {
    opts = opts || {};
    const fmtMode = st.yFormat, digits = Number(st.yDigits), dec = st.decimal;
    const userMin = App.parseNumber(String(st.yMin)), userMax = App.parseNumber(String(st.yMax));
    const finite = values.filter(Number.isFinite);
    if (st.yLog) {
      const pos = finite.filter((v) => v > 0);
      let min = Number.isFinite(userMin) && userMin > 0 ? userMin : pos.length ? Math.pow(10, Math.floor(Math.log10(Math.min(...pos)))) : 1e-4;
      let max = Number.isFinite(userMax) && userMax > min ? userMax : pos.length ? Math.pow(10, Math.ceil(Math.log10(Math.max(...pos)))) : 1;
      if (max <= min) max = min * 10;
      const ticks = [], minor = [];
      for (let e = Math.ceil(Math.log10(min) - 1e-9); e <= Math.log10(max) + 1e-9; e++) {
        ticks.push(Math.pow(10, e));
        for (let k = 2; k < 10; k++) { const v = k * Math.pow(10, e); if (v > min && v < max) minor.push(v); }
      }
      const autoLog = (v) => (v >= 1e-4 && v < 1e5 ? fmtNumber(v, 'fixed', Math.max(0, -Math.floor(Math.log10(v) + 1e-9)), dec) : fmtNumber(v, 'sci', 0, dec));
      const tk = mergeTicks(ticks, parseList(st.yTicksExtra).filter((v) => v > 0), min, max);
      return { log: true, min, max, ticks: tk, minor, fmt: (v) => (fmtMode === 'auto' ? autoLog(v) : fmtNumber(v, fmtMode, digits, dec)) };
    }
    let min = Number.isFinite(userMin) ? userMin : Math.min(0, ...finite);
    let max = Number.isFinite(userMax) ? userMax : (finite.length ? Math.max(...finite) : 1);
    if (opts.forceMax !== undefined && !Number.isFinite(userMax)) max = opts.forceMax;
    if (!(max > min)) max = min + (Math.abs(min) || 1);
    const stepUser = App.parseNumber(String(st.yStep));
    const t = linearTicks(min, max, opts.tickCount || 6, stepUser);
    if (!Number.isFinite(userMax) && opts.forceMax === undefined) {
      const last = t.ticks[t.ticks.length - 1];
      if (last < max - 1e-12) { t.ticks.push(+(last + t.step).toPrecision(12)); }
      max = t.ticks[t.ticks.length - 1];
    }
    if (!Number.isFinite(userMin) && t.ticks[0] > min) min = t.ticks[0];
    return { log: false, min, max, ticks: mergeTicks(t.ticks.filter((v) => v >= min - 1e-12 && v <= max + 1e-12), parseList(st.yTicksExtra), min, max), step: t.step, fmt: (v) => fmtNumber(v, fmtMode, digits, dec, t.step) };
  }

  // ------------------------------------------------------------ line chart

  function drawLine(root, model, st, box, opts) {
    opts = opts || {};
    const series = model.series.filter((s) => s.style.visible !== false);
    const pts = series.flatMap((s) => s.points.filter((p) => Number.isFinite(p.mean)));
    const showErr = st.errorBars !== 'none';
    const yvals = pts.flatMap((p) => showErr && Number.isFinite(p.ci) ? [p.mean - p.ci, p.mean + p.ci] : [p.mean])
      .concat(refLinesOf(st, 'y').map((r) => App.parseNumber(String(r.value))));
    const ySpec = yScaleSpec(yvals, st);

    // x axis
    let xSpec;
    const xsAll = [...new Set(pts.map((p) => p.x))].sort((a, b) => a - b);
    const catMode = st.xAsCategories && xsAll.length <= 40 && !model.xNumericOnly;
    if (catMode) {
      const labels = xsAll.map((v) => (model.xLabel ? model.xLabel(v) : fmtNumber(v, st.xFormat, Number(st.xDigits), st.decimal)));
      xSpec = { type: 'num', min: -0.5, max: xsAll.length - 0.5, ticks: xsAll.map((_, i) => i), fmt: (i) => labels[i] };
    } else {
      const uMin = App.parseNumber(String(st.xMin)), uMax = App.parseNumber(String(st.xMax));
      let mn = Number.isFinite(uMin) ? uMin : Math.min(...xsAll), mx = Number.isFinite(uMax) ? uMax : Math.max(...xsAll);
      if (!(mx > mn)) { mn -= 1; mx += 1; }
      const xStep = App.parseNumber(String(st.xStep));
      const t = xsAll.length <= 12 && !model.scatter && !Number.isFinite(uMin) && !Number.isFinite(uMax) && !(xStep > 0) ? { ticks: xsAll, step: 0 } : linearTicks(mn, mx, 8, xStep);
      const pad = Number.isFinite(uMin) || Number.isFinite(uMax) ? 0 : (mx - mn) * 0.03;
      xSpec = { type: 'num', min: mn - pad, max: mx + pad, ticks: mergeTicks(t.ticks, parseList(st.xTicksExtra), mn - pad, mx + pad), fmt: (v) => (model.xLabel ? model.xLabel(v) : fmtNumber(v, st.xFormat, Number(st.xDigits), st.decimal, t.step || undefined)) };
    }
    const xOf = (p) => (catMode ? xsAll.indexOf(p.x) : p.x);

    const legendItems = series.map((s) => ({ name: s.name, color: s.style.color, width: s.style.width, dash: s.style.dash, marker: s.style.marker, markerSize: s.style.markerSize, hollow: s.style.hollow, line: s.style.line !== false }));
    const f = frame(root, st, box, ySpec, xSpec, series.length > (opts.minLegend || 1) || st.legend !== 'auto' ? legendItems : null, opts);
    const clipId = 'clip' + Math.random().toString(36).slice(2, 8);
    const defs = S('defs');
    const cp = S('clipPath', { id: clipId });
    cp.appendChild(S('rect', { x: f.plot.x - 6, y: f.plot.y - 6, width: f.plot.w + 12, height: f.plot.h + 12 }));
    defs.appendChild(cp); root.appendChild(defs);
    const g = S('g', { 'clip-path': `url(#${clipId})` });

    for (const s of series) {
      const sty = s.style;
      const valid = s.points.filter((p) => Number.isFinite(p.mean) && (!ySpec.log || p.mean > 0)).sort((a, b) => xOf(a) - xOf(b));
      const sg = S('g');
      if (showErr && st.errorBars === 'band' && valid.length > 1) {
        const up = valid.map((p) => [f.xs(xOf(p)), f.ys(p.mean + (Number.isFinite(p.ci) ? p.ci : 0))]);
        const dn = valid.slice().reverse().map((p) => [f.xs(xOf(p)), f.ys(Math.max(ySpec.log ? ySpec.min : -Infinity, p.mean - (Number.isFinite(p.ci) ? p.ci : 0)))]);
        sg.appendChild(S('path', { d: 'M' + up.concat(dn).map((q) => q[0].toFixed(1) + ' ' + q[1].toFixed(1)).join(' L') + ' Z', fill: sty.color, 'fill-opacity': 0.15, stroke: 'none' }));
      }
      if (sty.line !== false && valid.length > 1) {
        sg.appendChild(S('path', {
          d: valid.map((p, i) => (i ? 'L' : 'M') + f.xs(xOf(p)).toFixed(1) + ' ' + f.ys(p.mean).toFixed(1)).join(' '),
          fill: 'none', stroke: sty.color, 'stroke-width': sty.width, 'stroke-linejoin': 'round',
          'stroke-linecap': sty.dash === 'dotted' ? 'round' : 'butt', 'stroke-dasharray': dashArray(sty.dash, sty.width),
        }));
      }
      if (showErr && st.errorBars === 'bars') {
        for (const p of valid) {
          if (!Number.isFinite(p.ci) || p.ci <= 0) continue;
          const x = f.xs(xOf(p));
          const y1 = f.ys(ySpec.log ? Math.max(p.mean - p.ci, ySpec.min) : p.mean - p.ci), y2 = f.ys(p.mean + p.ci);
          const cw = st.capWidth;
          const eg = S('g', { stroke: sty.errColor || sty.color, 'stroke-width': st.errorWidth });
          eg.appendChild(S('line', { x1: x.toFixed(1), x2: x.toFixed(1), y1: y1.toFixed(1), y2: y2.toFixed(1) }));
          if (cw > 0) {
            eg.appendChild(S('line', { x1: (x - cw).toFixed(1), x2: (x + cw).toFixed(1), y1: y1.toFixed(1), y2: y1.toFixed(1) }));
            eg.appendChild(S('line', { x1: (x - cw).toFixed(1), x2: (x + cw).toFixed(1), y1: y2.toFixed(1), y2: y2.toFixed(1) }));
          }
          sg.appendChild(eg);
        }
      }
      for (const p of valid) {
        const m = marker(sty.marker, f.xs(xOf(p)), f.ys(p.mean), sty.markerSize, sty.color, sty.hollow, st.plotBg || st.bg);
        if (m) { m.appendChild(tip(s.name, p, st)); sg.appendChild(m); }
      }
      const look = valueLook(st, sty, true);
      if (st.lineValues && look.show) {
        const vs = Number(st.valueSize) > 0 ? Number(st.valueSize) : st.fontSize * 0.8;
        const off = Math.max(4, (sty.marker === 'none' ? 0 : sty.markerSize / 2) + 3);
        for (const p of valid) {
          if (valueHidden(st, p.mean)) continue;
          const px = f.xs(xOf(p)), py = f.ys(p.mean);
          const txt = fmtNumber(p.mean, st.yFormat === 'auto' ? 'auto' : st.yFormat, Number(st.lineValueDigits), st.decimal);
          const pos = look.pos;
          const at = pos === 'below' ? [px, py + off + vs * 0.85, 'middle'] : pos === 'right' ? [px + off + 2, py + vs * 0.33, 'start']
            : pos === 'left' ? [px - off - 2, py + vs * 0.33, 'end'] : [px, py - off - (look.box === 'box' ? 2 : 0), 'middle'];
          valueText(sg, at[0], at[1], txt, look, st, { size: vs, anchor: at[2], series: sty.color });
        }
      }
      g.appendChild(sg);
    }
    root.appendChild(g);
    // 1.16.0: reference lines; on a uniform (category) x axis a value between two points is interpolated
    const xPos = catMode
      ? (v) => {
        if (!xsAll.length) return NaN;
        let i = xsAll.findIndex((x) => x >= v);
        if (i < 0) return v === xsAll[xsAll.length - 1] ? f.xs(xsAll.length - 1) : NaN;
        if (xsAll[i] === v || i === 0) return xsAll[i] === v ? f.xs(i) : NaN;
        return f.xs(i - 1 + (v - xsAll[i - 1]) / (xsAll[i] - xsAll[i - 1]));
      }
      : (v) => f.xs(v);
    drawRefLines(root, f, st, xPos);
    f.finish();
    return f;
  }

  // ------------------------------------------------------------ bar chart

  function drawBars(root, model, st, box, opts) {
    opts = opts || {};
    const series = model.series.filter((s) => s.style.visible !== false);
    const cats = model.categories;
    const showErr = st.errorBars !== 'none' && !model.stacked;
    const val = (s, c) => { const v = s.values[c.key]; return v && Number.isFinite(v.mean) ? v : null; };
    let yvals;
    let totals = null;
    if (model.stacked) {
      totals = cats.map((c) => series.reduce((a, s) => a + (val(s, c) ? Math.max(0, val(s, c).mean) : 0), 0));
      yvals = model.normalize ? [1] : totals;
    } else {
      yvals = series.flatMap((s) => cats.map((c) => val(s, c)).filter(Boolean).map((v) => (showErr && Number.isFinite(v.ci) ? v.mean + v.ci : v.mean)));
    }
    yvals = yvals.concat(refLinesOf(st, 'y').map((r) => App.parseNumber(String(r.value))));
    const stLocal = model.normalize && st.yFormat === 'auto' ? Object.assign({}, st, { yFormat: 'percent', yDigits: 0 }) : st;
    const ySpec = yScaleSpec(yvals, stLocal, model.normalize ? { forceMax: 1 } : {});
    const xSpec = { type: 'band', labels: cats.map((c) => c.label) };
    const legendItems = series.map((s) => ({ name: s.name, color: s.style.color, kind: 'bar', pattern: s.style.pattern, patternColor: s.style.patternColor, patternSize: s.style.patternSize, patternWidth: s.style.patternWidth }));
    const f = frame(root, stLocal, box, ySpec, xSpec, (series.length > 1 || model.forceLegend) ? legendItems : null, opts);
    // 1.17.0: one fill (colour or pattern) per series
    const fills = new Map();
    const fillOf = (s) => { if (!fills.has(s)) fills.set(s, patternFill(root, s.style.color, s.style)); return fills.get(s); };
    const outline = (s) => (st.barOutline || (s.style.pattern && s.style.pattern !== 'none') ? '#000' : null);
    const bw = f.band.bw;
    const maxPx = App.parseNumber(String(st.barMaxPx));
    let inner = bw * (1 - st.barGap);
    if (maxPx > 0 && model.stacked) inner = Math.min(inner, maxPx);
    const g = S('g');
    const y0 = f.ys(ySpec.log ? ySpec.min : Math.max(ySpec.min, 0));
    const fs = st.fontSize;
    const vfs = Number(st.valueSize) > 0 ? Number(st.valueSize) : 0;
    cats.forEach((c, ci) => {
      const cx = f.xs(ci);
      if (model.stacked) {
        let acc = 0;
        const tot = totals[ci] || 1;
        const side = [];
        const lsz = vfs || fs * 0.85;
        for (const s of series) {
          const v = val(s, c);
          if (!v || v.mean <= 0) continue;
          const a = model.normalize ? acc / tot : acc, b = model.normalize ? (acc + v.mean) / tot : acc + v.mean;
          acc += v.mean;
          const ya = f.ys(a), yb = f.ys(b);
          const ol = outline(s);
          const seg = S('rect', { x: (cx - inner / 2).toFixed(1), y: yb.toFixed(1), width: inner.toFixed(1), height: Math.max(0, ya - yb).toFixed(2), fill: fillOf(s), stroke: ol, 'stroke-width': ol ? 0.6 : null });
          seg.appendChild(tip(c.label + ' · ' + s.name, v, st, model.normalize ? v.mean / tot : null));
          g.appendChild(seg);
          const look = valueLook(st, s.style, false);
          if (!st.barValues || !look.show || valueHidden(st, model.normalize ? v.mean / tot : v.mean)) continue;
          const txt = fmtNumber(model.normalize ? (v.mean / tot) : v.mean, model.normalize ? 'percent' : st.yFormat === 'auto' ? 'auto' : st.yFormat, model.normalize ? 0 : Number(st.barValueDigits), st.decimal);
          // stacked bars: the series' own position (side/center/auto) wins; otherwise the stacked mode of section 5
          const ownPos = s.style && s.style.valPos;
          const mode = ['side', 'center', 'auto'].includes(ownPos) ? ownPos : ['side', 'center', 'auto'].includes(st.valStacked) ? st.valStacked : 'center';
          const fitsIn = ya - yb > lsz * 1.15 && inner >= textWidth(txt, lsz, st.font) + 2;
          if ((mode === 'center' && ya - yb > lsz * 1.1) || ((mode === 'auto' || mode === 'center') && fitsIn && mode !== 'side')) {
            valueText(g, cx, (ya + yb) / 2 + lsz * 0.35, txt, look, st, { size: lsz, anchor: 'middle', under: s.style.color, series: s.style.color });
          } else if (mode === 'side' || mode === 'auto') {
            side.push({ y: (ya + yb) / 2, txt, look, color: s.style.color });
          }
        }
        // labels that do not fit inside: to the right of the bar, pushed apart, with a leader line
        if (side.length) {
          side.sort((p, q) => p.y - q.y);
          const gapY = lsz * 1.25;
          const ys = side.map((q) => q.y);
          for (let k = 1; k < ys.length; k++) ys[k] = Math.max(ys[k], ys[k - 1] + gapY);
          const over = ys[ys.length - 1] - Math.max(...side.map((q) => q.y)) ;
          if (over > 0) for (let k = 0; k < ys.length; k++) ys[k] -= Math.min(over, ys[k] - (k ? ys[k - 1] + gapY : -Infinity)) * 0;
          const x0 = cx + inner / 2, xl = x0 + 10;
          side.forEach((q, k) => {
            g.appendChild(S('polyline', { points: `${x0.toFixed(1)},${q.y.toFixed(1)} ${(x0 + 4).toFixed(1)},${ys[k].toFixed(1)} ${(xl - 2).toFixed(1)},${ys[k].toFixed(1)}`, fill: 'none', stroke: q.look.color === 'series' ? q.color : '#7f7f7f', 'stroke-width': 0.7 }));
            valueText(g, xl, ys[k] + lsz * 0.35, q.txt, q.look, st, { size: lsz, anchor: 'start', series: q.color });
          });
        }
        if (st.barValues && !model.normalize) {
          const look = valueLook(st, null, false);
          valueText(g, cx, f.ys(totals[ci]) - 4 - (look.box === 'box' ? 2 : 0), fmtNumber(totals[ci], st.yFormat === 'auto' ? 'auto' : st.yFormat, Number(st.barValueDigits), st.decimal), look, st, { size: vfs || fs * 0.85, anchor: 'middle' });
        }
      } else {
        const n = series.length;
        const gap = inner * st.seriesGap;
        let w = n ? (inner - gap * (n - 1)) / n : inner;
        if (maxPx > 0) w = Math.min(w, maxPx);
        const groupW = n ? n * w + gap * (n - 1) : inner;
        series.forEach((s, si) => {
          const v = val(s, c);
          if (!v) return;
          const x = cx - groupW / 2 + si * (w + gap);
          const y = f.ys(ySpec.log ? Math.max(v.mean, ySpec.min) : v.mean);
          const ol = outline(s);
          const rect = S('rect', { x: x.toFixed(2), y: Math.min(y, y0).toFixed(2), width: Math.max(0.5, w).toFixed(2), height: Math.abs(y0 - y).toFixed(2), fill: fillOf(s), stroke: ol, 'stroke-width': ol ? 0.6 : null });
          rect.appendChild(tip(c.label + ' · ' + s.name, v, st));
          g.appendChild(rect);
          if (showErr && Number.isFinite(v.ci) && v.ci > 0) {
            const xm = x + w / 2, y1 = f.ys(Math.max(v.mean - v.ci, ySpec.log ? ySpec.min : -Infinity)), y2 = f.ys(v.mean + v.ci);
            const cw = Math.min(st.capWidth, w / 2);
            const eg = S('g', { stroke: '#000000', 'stroke-width': st.errorWidth });
            eg.appendChild(S('line', { x1: xm, x2: xm, y1, y2 }));
            eg.appendChild(S('line', { x1: xm - cw, x2: xm + cw, y1, y2: y1 }));
            eg.appendChild(S('line', { x1: xm - cw, x2: xm + cw, y1: y2, y2 }));
            g.appendChild(eg);
          }
          const look = valueLook(st, s.style, false);
          if (st.barValues && look.show && !valueHidden(st, v.mean)) {
            const lbl = fmtNumber(v.mean, st.yFormat === 'auto' ? 'auto' : st.yFormat, Number(st.barValueDigits), st.decimal);
            const lsz = vfs || fs * 0.8;
            const tw = textWidth(lbl, lsz, st.font, look.bold ? 700 : 'normal') + (look.box === 'box' ? 6 : 0);
            const top = Math.min(y, y0), h = Math.abs(y0 - y), tx = x + w / 2;
            const rot = st.valRotate === '-90' || (st.valRotate !== '0' && w < tw);
            // inside the bar only when the label fits (along the bar when rotated); otherwise above it
            const room = rot ? h >= tw + 6 : h >= lsz * 1.5 && w >= tw;
            const pos = look.pos === 'side' ? 'auto' : look.pos;
            if (pos !== 'auto' && pos !== 'outsideEnd' && room) {
              const under = s.style.color;
              if (rot) {
                const ty = pos === 'insideBase' ? y0 - 4 : pos === 'center' ? top + h / 2 + tw / 2 : top + 4 + tw;
                valueText(g, tx + lsz * 0.35, ty, lbl, look, st, { size: lsz, anchor: 'start', rotate: -90, under, series: s.style.color });
              } else {
                const ty = pos === 'insideBase' ? y0 - 5 : pos === 'center' ? top + h / 2 + lsz * 0.35 : top + lsz * 1.05;
                valueText(g, tx, ty, lbl, look, st, { size: lsz, anchor: 'middle', under, series: s.style.color });
              }
            } else if (w >= fs * 0.6 || rot) {
              const ty = top - 4 - (look.box === 'box' ? 2 : 0);
              valueText(g, rot ? tx + lsz * 0.35 : tx, ty, lbl, look, st, { size: lsz, anchor: rot ? 'start' : 'middle', rotate: rot ? -90 : 0, series: s.style.color });
            }
          }
        });
      }
    });
    root.appendChild(g);
    drawRefLines(root, f, stLocal, null);
    f.finish();
    return f;
  }

  // ------------------------------------------------------------ fiber cores diagram

  function drawCores(root, model, st, box) {
    const fs = st.coreLabelSize > 0 ? st.coreLabelSize : Math.max(8, st.fontSize - 2);
    let top = box.y + 6;
    if (st.panelLabel) root.appendChild(textEl(box.x + 6, box.y + st.panelLabelSize + 2, st.panelLabel, { size: st.panelLabelSize, font: st.font, color: st.titleColor, weight: 700 }));
    if (st.showTitle && st.title) {
      root.appendChild(textEl(box.x + box.w / 2, top + st.titleSize * 0.9, st.title, { size: st.titleSize, font: st.font, color: st.titleColor, anchor: 'middle', weight: 700 }));
      top += st.titleSize * 1.5;
    }
    const show = st.coreShow;
    if (st.coreLegend && show !== 'none') {
      const items = [];
      if (show === 'both' || show === 'block') items.push({ name: model.blockLabel || 'Bloqueio do núcleo', color: st.coreBlockColor, kind: 'box' });
      if (show === 'both' || show === 'util') items.push({ name: model.utilLabel || 'Uso do núcleo', color: st.coreUtilColor, kind: 'box' });
      const anyQ = model.diagrams.some((d) => d.cores.some((c) => c.quantum)) && st.coreMarkQuantum !== false;
      const anyD = model.diagrams.some((d) => d.cores.some((c) => c.disabled)) && st.coreMarkDisabled !== false;
      if (anyQ) items.push({ name: 'Núcleo quântico', color: st.coreQuantumColor || '#d9c6f5', kind: 'box' });
      if (anyD) items.push({ name: 'Núcleo desativado', color: st.coreDisabledColor || '#d32f2f', kind: 'box' });
      const lg = buildLegend(items, Object.assign({}, st, { legendBox: false }), box.w, 1);
      root.appendChild(lg.draw(box.x + (box.w - lg.w) / 2, top));
      top += lg.h + 4;
    }
    const diagrams = model.diagrams;
    const cols = Math.max(1, Math.min(Number(st.coreCols) || 2, diagrams.length || 1));
    const rows = Math.ceil(diagrams.length / cols);
    const cellW = box.w / cols, cellH = (box.y + box.h - top) / Math.max(1, rows);
    const nBoxes = show === 'both' ? 2 : show === 'none' ? 0 : 1;
    const boxW = nBoxes ? textWidth(fmtNumber(0.123456789, 'fixed', Number(st.coreDigits), st.decimal), fs, st.font) + 8 : -20;
    const boxH = nBoxes ? fs * 1.35 : 0;

    // heat scale
    let heatMax = 0;
    if (st.coreHeat !== 'none') for (const d of diagrams) for (const c of d.cores) { const v = st.coreHeat === 'util' ? c.util : c.block; if (Number.isFinite(v)) heatMax = Math.max(heatMax, v); }
    const mix = (hex, t) => {
      const h = hex.replace('#', '');
      const r = parseInt(h.slice(0, 2), 16), g = parseInt(h.slice(2, 4), 16), b = parseInt(h.slice(4, 6), 16);
      const m = (a) => Math.round(255 + (a - 255) * t);
      return `rgb(${m(r)},${m(g)},${m(b)})`;
    };

    diagrams.forEach((d, i) => {
      const cx0 = box.x + (i % cols) * cellW + cellW / 2;
      const cy0 = top + Math.floor(i / cols) * cellH;
      const capH = fs * 1.6;
      const avail = Math.min(cellW - 2 * (boxW + 26), cellH - capH - 2 * (nBoxes * boxH + 18));
      const R = Math.max(30, avail / 2);
      const cy = cy0 + (cellH - capH) / 2;
      const g = S('g');
      g.appendChild(S('circle', { cx: cx0, cy, r: R, fill: st.coreFiber, stroke: st.coreFiberStroke, 'stroke-width': 1.2 }));
      const ext = Math.max(...d.cores.map((c) => Math.hypot(c.x, c.y)), 0.01);
      let dmin = Infinity;
      for (let a = 0; a < d.cores.length; a++) for (let b = a + 1; b < d.cores.length; b++) dmin = Math.min(dmin, Math.hypot(d.cores[a].x - d.cores[b].x, d.cores[a].y - d.cores[b].y));
      if (!Number.isFinite(dmin) || dmin <= 0) dmin = 1;
      const scale = d.cores.length < 2 ? 0 : R / (ext + 0.42 * dmin);
      const coreR = d.cores.length < 2 ? R * 0.3 : Math.max(5, 0.25 * dmin * scale);
      for (const c of d.cores) {
        const x = cx0 + c.x * scale, y = cy + c.y * scale;
        let fill = st.coreFill;
        if (st.coreHeat !== 'none' && heatMax > 0 && !c.unused) {
          const v = st.coreHeat === 'util' ? c.util : c.block;
          if (Number.isFinite(v)) fill = mix(st.coreHeatColor, Math.min(1, v / heatMax));
        }
        if (c.quantum && st.coreMarkQuantum !== false) fill = st.coreQuantumColor || '#d9c6f5';
        if (c.disabled && st.coreMarkDisabled !== false) fill = st.coreDisabledColor || '#d32f2f';
        if (c.highlight) fill = c.highlight;
        const dis = c.disabled && st.coreMarkDisabled !== false;
        const hollow = c.unused && !dis && !c.quantum;
        g.appendChild(S('circle', { cx: x, cy: y, r: coreR, fill: hollow ? 'none' : fill, stroke: hollow ? '#9a9a9a' : st.coreStroke, 'stroke-width': 1.3, 'stroke-dasharray': hollow ? '3 2' : null }));
        g.appendChild(textEl(x, y + Math.min(coreR * 1.05, Math.max(fs * 1.7, coreR * 0.6)) * 0.36, String(c.id), { size: Math.min(coreR * 1.05, Math.max(fs * 1.7, coreR * 0.6)), font: st.font, color: dis ? '#ffffff' : hollow ? '#9a9a9a' : '#000000', anchor: 'middle', weight: 700 }));
        if (c.unused || !nBoxes) continue;
        // label position: radially outside the fiber; the centre core goes to the right
        let ang = Math.atan2(c.y, c.x);
        if (Math.hypot(c.x, c.y) < 1e-6) ang = 0;
        const lr = R + 10;
        let lx = cx0 + Math.cos(ang) * lr, ly = cy + Math.sin(ang) * lr;
        const right = Math.cos(ang) > 0.25, left = Math.cos(ang) < -0.25;
        const bx = right ? lx + 4 : left ? lx - boxW - 4 : lx - boxW / 2;
        const totalH = nBoxes * boxH;
        const by = Math.sin(ang) < -0.25 ? ly - totalH - 2 : Math.sin(ang) > 0.25 ? ly + 2 : ly - totalH / 2;
        // leader line from core edge to box
        const ex = x + Math.cos(ang) * coreR, ey = y + Math.sin(ang) * coreR;
        const tx = right ? bx : left ? bx + boxW : bx + boxW / 2;
        const ty = Math.sin(ang) < -0.25 ? by + totalH : Math.sin(ang) > 0.25 ? by : by + totalH / 2;
        g.appendChild(S('polyline', { points: `${ex.toFixed(1)},${ey.toFixed(1)} ${lx.toFixed(1)},${ly.toFixed(1)} ${tx.toFixed(1)},${ty.toFixed(1)}`, fill: 'none', stroke: '#000000', 'stroke-width': 0.7 }));
        const vals = [];
        if (show === 'both' || show === 'block') vals.push([c.block, st.coreBlockColor]);
        if (show === 'both' || show === 'util') vals.push([c.util, st.coreUtilColor]);
        vals.forEach(([v, col], k) => {
          const yy = by + k * boxH;
          g.appendChild(S('rect', { x: bx.toFixed(1), y: yy.toFixed(1), width: boxW.toFixed(1), height: boxH.toFixed(1), fill: col, stroke: '#ffffff', 'stroke-width': 0.6 }));
          g.appendChild(textEl(bx + boxW / 2, yy + boxH * 0.72, Number.isFinite(v) ? fmtNumber(v, 'fixed', Number(st.coreDigits), st.decimal) : '—', { size: fs, font: st.font, color: '#000000', anchor: 'middle' }));
        });
      }
      g.appendChild(textEl(cx0, cy0 + cellH - fs * 0.5, d.title, { size: Math.max(fs, st.fontSize - 1), font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
      root.appendChild(g);
    });
  }

  // ------------------------------------------------------------ topology (link heat map / network preview)

  let gradSeq = 0;

  /** Value of an undirected link according to the direction mode ('max' | 'mean' | 'ab' | 'ba'). */
  function linkValue(l, mode) {
    const v = [l.ab, l.ba].filter(Number.isFinite);
    if (!v.length) return NaN;
    if (mode === 'mean') return v.reduce((a, b) => a + b, 0) / v.length;
    return Math.max(...v);
  }

  function topoLayout(model, st, W) {
    const n = Math.max(1, model.panels.length);
    const cols = Math.max(1, Math.min(Number(st.topoCols) || 3, n));
    const rows = Math.ceil(n / cols);
    const legendW = model.range && st.topoLegend === 'right' ? 92 : 0;
    const legendH = model.range && st.topoLegend === 'bottom' ? 58 : 0;
    const titleH = st.showTitle && st.title ? st.titleSize * 1.6 : 4;
    return { n, cols, rows, legendW, legendH, titleH, cellW: (W - legendW) / cols };
  }

  /** Natural height of the drawing for a given width (keeps the aspect of the topology). */
  function topoHeight(model, st, W) {
    const L = topoLayout(model, st, W);
    const p = model.panels[0];
    let asp = 0.7;
    if (p && p.nodes.length > 1) {
      const xs = p.nodes.map((q) => q.x), ys = p.nodes.map((q) => q.y);
      const w = Math.max(1, Math.max(...xs) - Math.min(...xs)), h = Math.max(1, Math.max(...ys) - Math.min(...ys));
      asp = Math.min(1.6, Math.max(0.35, h / w));
    }
    const fs = Math.max(9, st.fontSize);
    const pad = (Number(st.topoNodeR) || 11) * 2 + 8;
    const cellH = (L.cellW - pad) * asp + pad + fs * 2;
    return Math.round(L.titleH + L.rows * cellH + L.legendH + 6);
  }

  function heatLegend(root, model, st, x, y, w, h, vertical) {
    const id = 'heat' + (++gradSeq);
    const defs = S('defs');
    const grad = S('linearGradient', vertical ? { id, x1: 0, y1: 1, x2: 0, y2: 0 } : { id, x1: 0, y1: 0, x2: 1, y2: 0 });
    for (const [t] of App.presets.HEAT_STOPS) grad.appendChild(S('stop', { offset: (t * 100).toFixed(1) + '%', 'stop-color': App.presets.heatColor(t) }));
    defs.appendChild(grad);
    root.appendChild(defs);
    const fs = Math.max(8, st.fontSize - 1);
    const { min, max } = model.range;
    const fmt = (v) => fmtNumber(v, st.topoFormat === 'percent' ? 'percent' : 'fixed', Number(st.topoDigits), st.decimal);
    const ticks = 5;
    if (vertical) {
      const lines = [];
      for (const word of String(model.legendTitle || '').split(/\s+/).filter(Boolean)) {
        const last = lines[lines.length - 1];
        if (last && textWidth(last + ' ' + word, fs, st.font, 600) <= w) lines[lines.length - 1] = last + ' ' + word;
        else lines.push(word);
      }
      lines.forEach((ln, k) => root.appendChild(textEl(x, y + fs * (1 + 1.2 * k), ln, { size: fs, font: st.font, color: st.textColor, weight: 600 })));
      const bw = 16, top = y + fs * (1.2 * lines.length + 1), bh = Math.max(40, h - fs * (1.2 * lines.length + 2));
      root.appendChild(S('rect', { x, y: top, width: bw, height: bh, fill: `url(#${id})`, stroke: st.axisColor, 'stroke-width': 0.6 }));
      for (let i = 0; i <= ticks; i++) {
        const v = min + (max - min) * i / ticks;
        const yy = top + bh - bh * i / ticks;
        root.appendChild(S('line', { x1: x + bw, x2: x + bw + 4, y1: yy, y2: yy, stroke: st.axisColor }));
        root.appendChild(textEl(x + bw + 6, yy + fs * 0.35, fmt(v), { size: fs, font: st.font, color: st.textColor }));
      }
    } else {
      const bh = 12, bw = Math.min(w, 420), bx = x + (w - bw) / 2;
      root.appendChild(textEl(x + w / 2, y + fs, model.legendTitle || '', { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
      root.appendChild(S('rect', { x: bx, y: y + fs * 1.5, width: bw, height: bh, fill: `url(#${id})`, stroke: st.axisColor, 'stroke-width': 0.6 }));
      for (let i = 0; i <= ticks; i++) {
        const v = min + (max - min) * i / ticks;
        const xx = bx + bw * i / ticks;
        root.appendChild(S('line', { x1: xx, x2: xx, y1: y + fs * 1.5 + bh, y2: y + fs * 1.5 + bh + 4, stroke: st.axisColor }));
        root.appendChild(textEl(xx, y + fs * 1.5 + bh + 5 + fs, fmt(v), { size: fs, font: st.font, color: st.textColor, anchor: 'middle' }));
      }
    }
  }

  function drawTopo(root, model, st, box) {
    const L = topoLayout(model, st, box.w);
    if (st.showTitle && st.title) root.appendChild(textEl(box.x + (box.w - L.legendW) / 2, box.y + st.titleSize * 1.1, st.title, { size: st.titleSize, font: st.font, color: st.titleColor, anchor: 'middle', weight: 700 }));
    const fs = Math.max(8, st.fontSize);
    const nodeR = Number(st.topoNodeR) || 11;
    const lw = Number(st.topoLinkWidth) || 4;
    const dir = st.topoDir || 'both';
    const range = model.range;
    const cellH = (box.h - L.titleH - L.legendH) / L.rows;
    const color = (v) => {
      if (!range) return st.topoLinkColor || '#5b6573';
      if (!Number.isFinite(v)) return st.topoNoData || '#bdbdbd';
      return App.presets.heatColor(range.max > range.min ? (v - range.min) / (range.max - range.min) : 0);
    };
    const fmtV = (v) => fmtNumber(v, st.topoFormat === 'percent' ? 'percent' : 'fixed', Number(st.topoDigits), st.decimal);
    const fmtP = (o) => (o && Number.isFinite(o.mean) ? fmtNumber(o.mean, 'percent', Number(st.topoDigits), st.decimal) : '—');
    const blkText = (o, long) => {
      if (!o) return '—';
      const has = (x) => x && Number.isFinite(x.mean);
      if (model.block && model.block.qkd && (has(o.qkd) || has(o.classical))) {
        const parts = [];
        if (has(o.classical)) parts.push((long ? 'clássicas ' : 'C ') + fmtP(o.classical));
        if (has(o.qkd)) parts.push((long ? 'protegidas QKD ' : 'Q ') + fmtP(o.qkd));
        if (long) parts.push('todas ' + fmtP(o.all));
        return parts.join(' · ');
      }
      return (long ? 'todas ' : 'B ') + fmtP(o.all);
    };
    model.panels.forEach((p, i) => {
      const cx = box.x + (i % L.cols) * L.cellW, cy = box.y + L.titleH + Math.floor(i / L.cols) * cellH;
      const capH = p.title ? fs * 1.8 : 0;
      const pad = nodeR + 6;
      const xs = p.nodes.map((q) => q.x), ys = p.nodes.map((q) => q.y);
      const x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys), y1 = Math.max(...ys);
      const aw = L.cellW - 2 * pad, ah = cellH - capH - 2 * pad;
      const sc = Math.min(aw / Math.max(1, x1 - x0), ah / Math.max(1, y1 - y0));
      const ox = cx + pad + (aw - (x1 - x0) * sc) / 2, oy = cy + pad + (ah - (y1 - y0) * sc) / 2;
      const P = new Map(p.nodes.map((q) => [String(q.id), { x: ox + (q.x - x0) * sc, y: oy + (q.y - y0) * sc }]));
      const g = S('g');
      const labs = [];
      if (st.topoPanelBorder) g.appendChild(S('rect', { x: cx + 2, y: cy + 2, width: L.cellW - 4, height: cellH - 4, fill: 'none', stroke: st.borderColor }));
      for (const l of p.links) {
        const A = P.get(String(l.a)), B = P.get(String(l.b));
        if (!A || !B) continue;
        const dx = B.x - A.x, dy = B.y - A.y, d = Math.max(1e-6, Math.hypot(dx, dy));
        const nx = -dy / d, ny = dx / d;
        const lines = [];
        if (range && dir === 'both') {
          const off = lw / 2 + 0.4;
          lines.push({ x1: A.x + nx * off, y1: A.y + ny * off, x2: B.x + nx * off, y2: B.y + ny * off, v: l.ab, t: l.a + '→' + l.b, blk: l.blkAB });
          lines.push({ x1: A.x - nx * off, y1: A.y - ny * off, x2: B.x - nx * off, y2: B.y - ny * off, v: l.ba, t: l.b + '→' + l.a, blk: l.blkBA });
        } else {
          const v = range ? linkValue(l, dir) : NaN;
          lines.push({ x1: A.x, y1: A.y, x2: B.x, y2: B.y, v, t: l.a + '–' + l.b });
        }
        for (const s of lines) {
          const ln = S('line', { x1: s.x1.toFixed(1), y1: s.y1.toFixed(1), x2: s.x2.toFixed(1), y2: s.y2.toFixed(1), stroke: color(s.v), 'stroke-width': range ? lw : Math.max(1.5, lw * 0.6), 'stroke-linecap': 'butt' });
          let tt = s.t + (Number.isFinite(l.km) ? ' · ' + fmtNumber(l.km, 'auto', 0, st.decimal) + ' km' : '') + (range ? ': ' + (Number.isFinite(s.v) ? fmtV(s.v) : 'sem dado') : '');
          if (model.block) {
            const parts = dir === 'both' ? [[s.t, s.blk]] : [[l.a + '→' + l.b, l.blkAB], [l.b + '→' + l.a, l.blkBA]];
            for (const [nm, o] of parts) if (o) tt += '\nBloqueio ' + nm + ': ' + blkText(o, true);
          }
          ln.appendChild(S('title', null, tt));
          g.appendChild(ln);
        }
        // 1.17.0: labels are collected here and placed after the nodes (style, boxes, callouts, collisions, drag)
        if (model.block && (st.topoLabels === 'block' || st.topoLabels === 'valueBlock')) {
          const txt = (v, o) => (st.topoLabels === 'valueBlock' ? 'U ' + (Number.isFinite(v) ? fmtV(v) : '—') + ' · ' : '') + (o ? blkText(o, false) : '—');
          const put = (sgn, text) => {
            // a→b label 40 % along the link on its own side, b→a label at 60 % on the other side
            const tpos = dir === 'both' ? (sgn > 0 ? 0.4 : 0.6) : 0.5;
            const off = range && dir === 'both' ? sgn * (lw / 2 + 0.4) : 0;
            labs.push({ key: l.a + '-' + l.b + (sgn < 0 ? '|r' : ''), text, sgn, nx, ny,
              ax: A.x + (B.x - A.x) * tpos + nx * off, ay: A.y + (B.y - A.y) * tpos + ny * off });
          };
          if (dir === 'both') { put(1, txt(l.ab, l.blkAB)); put(-1, txt(l.ba, l.blkBA)); }
          else put(1, txt(linkValue(l, dir), l.blkAB) + '  |  ' + (l.blkBA ? blkText(l.blkBA, false) : '—'));
          continue;
        }
        const lab = st.topoLabels === 'km' ? (Number.isFinite(l.km) ? fmtNumber(l.km, 'auto', 0, st.decimal) + (st.topoKmUnit ? ' km' : '') : '')
          : st.topoLabels === 'value' && range ? (dir === 'both' ? [l.ab, l.ba].map((v) => (Number.isFinite(v) ? fmtV(v) : '—')).join(' / ') : fmtV(linkValue(l, dir))) : '';
        if (lab) labs.push({ key: l.a + '-' + l.b, text: lab, sgn: 1, nx, ny, ax: (A.x + B.x) / 2, ay: (A.y + B.y) / 2 });
      }
      // Parte 27: trusted nodes every QTND km (from the source of each direction, as the QKD planner does)
      if ((p.qtnd > 0 || p.links.some((l) => l.qtndAB > 0 || l.qtndBA > 0)) && st.topoTrusted !== false) {
        const tr = Number(st.topoTrustedR) > 0 ? Number(st.topoTrustedR) : Math.max(2.5, nodeR * 0.36);
        const tg = S('g');
        for (const l of p.links) {
          const A = P.get(String(l.a)), B = P.get(String(l.b));
          if (!A || !B || !Number.isFinite(l.km) || l.km <= 0) continue;
          // Parte 39: QTND of each direction (per-link trusted nodes); falls back to the global value
          const qAB = l.qtndAB > 0 ? l.qtndAB : p.qtnd, qBA = l.qtndBA > 0 ? l.qtndBA : p.qtnd;
          const dx = B.x - A.x, dy = B.y - A.y, d = Math.max(1e-6, Math.hypot(dx, dy));
          const nx = -dy / d, ny = dx / d;
          const both = range && dir === 'both';
          const off = both ? lw / 2 + 0.4 : 0;
          const dirs = both ? [[A, B, 1, l.a + '→' + l.b, qAB], [B, A, -1, l.b + '→' + l.a, qBA]] : [[A, B, 0, l.a + '–' + l.b, qAB]];
          for (const [P0, P1, sg, name, qd] of dirs) {
            if (!(qd > 0)) continue;
            const n = Math.max(1, Math.ceil(l.km / qd - 1e-9));
            for (let k = 1; k < n; k++) {
              const f = Math.min(0.999, (k * qd) / l.km);
              const x = P0.x + (P1.x - P0.x) * f + nx * off * sg, y = P0.y + (P1.y - P0.y) * f + ny * off * sg;
              const c = S('circle', { cx: x.toFixed(1), cy: y.toFixed(1), r: tr.toFixed(1), fill: st.topoTrustedFill || '#7030a0', stroke: '#ffffff', 'stroke-width': 0.8 });
              c.appendChild(S('title', null, 'Nó confiável ' + k + ' de ' + (n - 1) + ' em ' + name + ' · ' + fmtNumber(k * qd, 'auto', 1, st.decimal) + ' km da origem (QTND ' + fmtNumber(qd, 'auto', 1, st.decimal) + ' km' + (qd !== p.qtnd ? ', regra própria do enlace' : '') + ')'));
              tg.appendChild(c);
            }
          }
        }
        g.appendChild(tg);
      }
      for (const q of p.nodes) {
        const c = P.get(String(q.id));
        const circ = S('circle', { cx: c.x.toFixed(1), cy: c.y.toFixed(1), r: nodeR, fill: q.fill || st.topoNodeFill || '#ffffff', stroke: st.topoNodeStroke || '#000000', 'stroke-width': 1.3 });
        circ.appendChild(S('title', null, 'Nó ' + q.id));
        g.appendChild(circ);
        g.appendChild(textEl(c.x, c.y + nodeR * 0.36, String(q.id), { size: Math.max(7, nodeR * (String(q.id).length > 2 ? 0.8 : 1.0)), font: st.font, color: '#000000', anchor: 'middle', weight: 700 }));
      }
      if (labs.length) placeTopoLabels(g, labs, st, fs, lw, nodeR, [...P.values()], { x0: cx, y0: cy, x1: cx + L.cellW, y1: cy + cellH - capH }, i);
      if (p.title) g.appendChild(textEl(cx + L.cellW / 2, cy + cellH - fs * 0.6, p.title, { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
      root.appendChild(g);
    });
    if (range && st.topoLegend === 'right') heatLegend(root, model, st, box.x + box.w - L.legendW + 8, box.y + L.titleH, L.legendW - 8, Math.min(320, box.h - L.titleH - 10), true);
    if (range && st.topoLegend === 'bottom') heatLegend(root, model, st, box.x, box.y + box.h - L.legendH, box.w, L.legendH, false);
  }

  /**
   * 1.17.0: link labels of the topology. Style plain (text), box (text in a box) or callout (box away from the link,
   * with a line pointing to it). With "avoid", a label that hits a node or another label is pushed away from the link.
   * st.topoLabelOffsets[key] = {dx, dy} moves one label by hand (drag in the preview); a moved label gets a leader line.
   */
  function placeTopoLabels(g, labs, st, fs, lw, nodeR, nodes, clip, panelIdx) {
    const size = Number(st.topoLabelSize) > 0 ? Number(st.topoLabelSize) : Math.max(7, fs - 2);
    const bold = st.topoLabelBold !== false;
    const weight = bold ? 600 : 'normal';
    const mode = st.topoLabelStyle || 'plain';
    const boxed = mode !== 'plain';
    const padX = boxed ? 3 : 1, padY = boxed ? 2 : 0;
    const color = st.topoLabelColor || '#c00000';
    const fill = st.topoLabelFill || '#ffffff', stroke = st.topoLabelStroke || color;
    const dist = Number(st.topoLabelDist) >= 0 && st.topoLabelDist !== '' && st.topoLabelDist !== undefined ? Number(st.topoLabelDist) : (mode === 'callout' ? 26 : 0);
    const offs = st.topoLabelOffsets && typeof st.topoLabelOffsets === 'object' ? st.topoLabelOffsets : {};
    const placed = [];
    const hits = (r) => placed.some((q) => r.x0 < q.x1 && r.x1 > q.x0 && r.y0 < q.y1 && r.y1 > q.y0)
      || nodes.some((c) => c.x > r.x0 - nodeR && c.x < r.x1 + nodeR && c.y > r.y0 - nodeR && c.y < r.y1 + nodeR);
    const out = [];
    for (const lb of labs) {
      const tw = textWidth(lb.text, size, st.font, weight);
      const w = tw + 2 * padX, h = size * 1.15 + 2 * padY;
      // distance from the link to the centre of the label, along the normal: half of its extent in that direction
      const half = Math.abs(lb.nx) * w / 2 + Math.abs(lb.ny) * h / 2;
      const base = lw / 2 + 2 + half + dist;
      const rectAt = (d, sg) => { const x = lb.ax + sg * lb.nx * d, y = lb.ay + sg * lb.ny * d; return { cx: x, cy: y, x0: x - w / 2, x1: x + w / 2, y0: y - h / 2, y1: y + h / 2 }; };
      let r = rectAt(base, lb.sgn);
      const own = offs[lb.key] || offs[panelIdx + ':' + lb.key];
      if (own && (Number(own.dx) || Number(own.dy))) {
        r = { cx: r.cx + Number(own.dx || 0), cy: r.cy + Number(own.dy || 0) };
        Object.assign(r, { x0: r.cx - w / 2, x1: r.cx + w / 2, y0: r.cy - h / 2, y1: r.cy + h / 2 });
      } else if (st.topoLabelAvoid !== false && hits(r)) {
        const step = Math.max(4, size * 0.7);
        let best = null;
        for (const sg of [lb.sgn, -lb.sgn]) {
          for (let k = 1; k <= 10 && !best; k++) { const t = rectAt(base + k * step, sg); if (!hits(t)) best = t; }
          if (best) break;
        }
        if (best) r = best;
      }
      // keep inside the panel
      const dxc = Math.max(0, clip.x0 + 2 - r.x0) - Math.max(0, r.x1 - (clip.x1 - 2));
      const dyc = Math.max(0, clip.y0 + 2 - r.y0) - Math.max(0, r.y1 - (clip.y1 - 2));
      if (dxc || dyc) r = { cx: r.cx + dxc, cy: r.cy + dyc, x0: r.x0 + dxc, x1: r.x1 + dxc, y0: r.y0 + dyc, y1: r.y1 + dyc };
      placed.push(r);
      out.push([lb, r, w, h]);
    }
    const lg = S('g');
    for (const [lb, r, w, h] of out) {
      const moved = Math.hypot(r.cx - lb.ax, r.cy - lb.ay) - (lw / 2 + 2 + Math.abs(lb.nx) * w / 2 + Math.abs(lb.ny) * h / 2) > size * 0.9;
      const gg = S('g', { 'data-topo-key': lb.key, style: 'cursor:move' });
      if (mode === 'callout' || moved) {
        // leader: from the point on the link to the nearest point of the label's border
        const ex = Math.max(r.x0, Math.min(lb.ax, r.x1)), ey = Math.max(r.y0, Math.min(lb.ay, r.y1));
        gg.appendChild(S('line', { x1: lb.ax.toFixed(1), y1: lb.ay.toFixed(1), x2: ex.toFixed(1), y2: ey.toFixed(1), stroke: stroke, 'stroke-width': Number(st.topoLeaderWidth) > 0 ? Number(st.topoLeaderWidth) : 0.9 }));
        gg.appendChild(S('circle', { cx: lb.ax.toFixed(1), cy: lb.ay.toFixed(1), r: 1.8, fill: stroke }));
      }
      if (boxed) gg.appendChild(S('rect', { x: r.x0.toFixed(1), y: r.y0.toFixed(1), width: w.toFixed(1), height: h.toFixed(1), rx: 2, fill, 'fill-opacity': st.topoLabelOpacity !== undefined && st.topoLabelOpacity !== '' ? Number(st.topoLabelOpacity) : 0.92, stroke, 'stroke-width': 0.8 }));
      gg.appendChild(textEl(r.cx, r.cy + size * 0.36, lb.text, { size, font: st.font, color, anchor: 'middle', weight }));
      gg.appendChild(S('title', null, 'Arraste para mover este rótulo (' + lb.key.replace('|r', ', sentido inverso') + ')'));
      lg.appendChild(gg);
    }
    g.appendChild(lg);
  }

  // ------------------------------------------------------------ spectrum heat map (slots × cores × links)

  const SLOT_SCALES = [
    { id: 'traffic', name: 'Verde → amarelo → laranja → vermelho', stops: [[0, '#00B050'], [1 / 3, '#FFFF00'], [2 / 3, '#FFC000'], [1, '#FF0000']] },
    { id: 'bins5', name: '5 faixas: verde, amarelo, laranja, marrom, vermelho', bins: ['#00B050', '#FFFF00', '#FFC000', '#C55A11', '#FF0000'] },
    { id: 'heat', name: 'Igual ao mapa da topologia', stops: [[0, '#00A651'], [1 / 3, '#FFDD00'], [2 / 3, '#FF8C00'], [1, '#D7191C']] },
    { id: 'viridis', name: 'Viridis (daltonismo e impressão)', stops: [[0, '#440154'], [0.25, '#3B528B'], [0.5, '#21918C'], [0.75, '#5EC962'], [1, '#FDE725']] },
    { id: 'blues', name: 'Branco → azul', stops: [[0, '#F7FBFF'], [0.5, '#6BAED6'], [1, '#08306B']] },
    { id: 'gray', name: 'Branco → preto', stops: [[0, '#FFFFFF'], [1, '#000000']] },
  ];

  function hexRgb(h) { const m = /^#?([0-9a-f]{6})$/i.exec(h); const n = m ? parseInt(m[1], 16) : 0; return [n >> 16 & 255, n >> 8 & 255, n & 255]; }

  /** Returns color(v) for the scale and range (continuous scales are quantized in 1 % steps). */
  function slotColorFn(st, range) {
    const sc = SLOT_SCALES.find((x) => x.id === st.slotScale) || SLOT_SCALES[0];
    const span = range.max - range.min;
    const cache = new Map();
    const zero = st.slotZero === 'white' ? '#FFFFFF' : st.slotZero === 'gray' ? '#E7E6E6' : null;
    const stops = sc.stops ? sc.stops.map(([t, c]) => [t, hexRgb(c)]) : null;
    return (v) => {
      if (!Number.isFinite(v)) return '#BDBDBD';
      if (zero && v <= 0) return zero;
      let t = span > 0 ? (v - range.min) / span : 0;
      t = Math.max(0, Math.min(1, t));
      if (sc.bins) return sc.bins[Math.min(sc.bins.length - 1, Math.floor(t * sc.bins.length))];
      const q = Math.round(t * 100);
      if (cache.has(q)) return cache.get(q);
      const tq = q / 100;
      let col = stops[stops.length - 1][1];
      for (let i = 1; i < stops.length; i++) {
        if (tq <= stops[i][0]) {
          const [t0, c0] = stops[i - 1], [t1, c1] = stops[i];
          const f = t1 > t0 ? (tq - t0) / (t1 - t0) : 0;
          col = c0.map((x, k) => Math.round(x + (c1[k] - x) * f));
          break;
        }
      }
      const out = '#' + col.map((x) => x.toString(16).padStart(2, '0')).join('');
      cache.set(q, out);
      return out;
    };
  }

  /** Value format of the spectrum maps: occupancy/ratio in % or decimal, counts as integers (Parte 29). */
  function slotFmt(model, st) {
    if (model.valueFormat === 'count') return (v) => fmtNumber(v, 'fixed', Math.abs(v - Math.round(v)) > 1e-9 ? 1 : 0, st.decimal);
    return (v) => fmtNumber(v, st.slotFormat === 'fixed' ? 'fixed' : 'percent', Number(st.slotDigits) || 0, st.decimal);
  }

  function slotGeometry(model, st, W) {
    const fs = Math.max(7, Number(st.fontSize) || 12);
    const rowH = Math.max(2, Number(st.slotRowH) || 12);
    const lfs = Math.max(8, fs - 1);
    const items = model.legendItems || null;
    let legendW = st.slotLegend === 'right' ? Math.max(110, textWidth(model.legendTitle || '', lfs, st.font, 600) + 14) : 0;
    if (items && legendW) legendW = Math.min(360, Math.max(textWidth(model.legendTitle || '', lfs, st.font, 600), ...items.map(([, t]) => 19 + textWidth(t, lfs, st.font))) + 16);
    let legendH = st.slotLegend === 'bottom' ? fs * 4 + 12 : 0;
    if (items && legendH) {
      const iw = Math.max(...items.map(([, t]) => 19 + textWidth(t, lfs, st.font) + 18));
      const per = Math.max(1, Math.floor(W / iw));
      legendH = lfs * 2.2 + Math.ceil(items.length / per) * lfs * 1.6 + 8;
    }
    // Parte 29: summary columns at the right of the map (e.g. blocked/attempts and %)
    const sumW = [];
    if (model.summaryHeads) {
      model.summaryHeads.forEach((h, i) => {
        let w = textWidth(h, fs, st.font, 700);
        for (const p of model.panels) for (const r of p.rows) if (r.summary) w = Math.max(w, textWidth(String(r.summary[i]), fs, st.font));
        sumW.push(w + 14);
      });
    }
    const titleH = st.showTitle && st.title ? st.titleSize * 1.7 : 4;
    const ptH = st.slotPanelTitle !== false ? fs * 1.9 : 0;
    const headH = fs * 2.4;
    const labels = [];
    for (const p of model.panels) for (const r of p.rows) labels.push(r.link);
    const linkColW = Math.max(textWidth('Enlace', fs, st.font, 700), ...labels.map((t) => textWidth(t, fs, st.font, 700))) + 18;
    const coreColW = textWidth('Núcleo', fs, st.font, 700) + 16;
    const panelH = model.panels.map((p) => ptH + headH + p.rows.length * rowH + 14);
    const H = Math.round(titleH + panelH.reduce((a, b) => a + b, 0) + legendH + 6);
    return { fs, rowH, legendW, legendH, titleH, ptH, headH, linkColW, coreColW, panelH, H, sumW, sumTot: sumW.reduce((a, b) => a + b, 0) };
  }

  function slotLegend(root, model, st, color, x, y, w, h, vertical) {
    const sc = SLOT_SCALES.find((q) => q.id === st.slotScale) || SLOT_SCALES[0];
    const fs = Math.max(8, (Number(st.fontSize) || 12) - 1);
    const { min, max } = model.range;
    const fmt = slotFmt(model, st);
    const title = model.legendTitle || '';
    if (sc.bins || model.legendItems) {
      const n = sc.bins ? sc.bins.length : 0;
      const items = model.legendItems || sc.bins.map((c, i) => [c, fmt(min + (max - min) * i / n) + ' – ' + fmt(min + (max - min) * (i + 1) / n)]).reverse();
      if (model.legendItems && !vertical) {
        root.appendChild(textEl(x + w / 2, y + fs, title, { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
        const iw = Math.max(...items.map(([, t]) => 19 + textWidth(t, fs, st.font) + 18));
        const per = Math.max(1, Math.floor(w / iw));
        const x0 = x + (w - iw * Math.min(per, items.length)) / 2;
        items.forEach(([c, t], i) => {
          const xx = x0 + (i % per) * iw, yy = y + fs * 1.6 + Math.floor(i / per) * fs * 1.6;
          root.appendChild(S('rect', { x: xx, y: yy, width: 14, height: fs * 1.1, fill: c, stroke: st.axisColor, 'stroke-width': 0.6 }));
          root.appendChild(textEl(xx + 19, yy + fs * 0.9, t, { size: fs, font: st.font, color: st.textColor }));
        });
        return;
      }
      if (vertical) {
        root.appendChild(textEl(x, y + fs, title, { size: fs, font: st.font, color: st.textColor, weight: 600 }));
        items.forEach(([c, t], i) => {
          const yy = y + fs * 1.8 + i * (fs * 1.6);
          root.appendChild(S('rect', { x, y: yy, width: 14, height: fs * 1.1, fill: c, stroke: st.axisColor, 'stroke-width': 0.6 }));
          root.appendChild(textEl(x + 19, yy + fs * 0.9, t, { size: fs, font: st.font, color: st.textColor }));
        });
      } else {
        root.appendChild(textEl(x + w / 2, y + fs, title, { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
        const iw = Math.min(170, w / n);
        const x0 = x + (w - iw * n) / 2;
        items.slice().reverse().forEach(([c, t], i) => {
          root.appendChild(S('rect', { x: x0 + i * iw, y: y + fs * 1.6, width: 14, height: fs * 1.1, fill: c, stroke: st.axisColor, 'stroke-width': 0.6 }));
          root.appendChild(textEl(x0 + i * iw + 19, y + fs * 2.5, t, { size: fs, font: st.font, color: st.textColor }));
        });
      }
      return;
    }
    const id = 'slotheat' + (++gradSeq);
    const defs = S('defs');
    const grad = S('linearGradient', vertical ? { id, x1: 0, y1: 1, x2: 0, y2: 0 } : { id, x1: 0, y1: 0, x2: 1, y2: 0 });
    for (let i = 0; i <= 20; i++) grad.appendChild(S('stop', { offset: (i * 5) + '%', 'stop-color': color(min + (max - min) * i / 20) }));
    defs.appendChild(grad);
    root.appendChild(defs);
    const ticks = 5;
    if (vertical) {
      root.appendChild(textEl(x, y + fs, title, { size: fs, font: st.font, color: st.textColor, weight: 600 }));
      const bw = 16, top = y + fs * 1.8, bh = Math.max(60, Math.min(280, h - fs * 2.5));
      root.appendChild(S('rect', { x, y: top, width: bw, height: bh, fill: `url(#${id})`, stroke: st.axisColor, 'stroke-width': 0.6 }));
      for (let i = 0; i <= ticks; i++) {
        const yy = top + bh - bh * i / ticks;
        root.appendChild(S('line', { x1: x + bw, x2: x + bw + 4, y1: yy, y2: yy, stroke: st.axisColor }));
        root.appendChild(textEl(x + bw + 6, yy + fs * 0.35, fmt(min + (max - min) * i / ticks), { size: fs, font: st.font, color: st.textColor }));
      }
    } else {
      const bh = 12, bw = Math.min(w, 420), bx = x + (w - bw) / 2;
      root.appendChild(textEl(x + w / 2, y + fs, title, { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 600 }));
      root.appendChild(S('rect', { x: bx, y: y + fs * 1.5, width: bw, height: bh, fill: `url(#${id})`, stroke: st.axisColor, 'stroke-width': 0.6 }));
      for (let i = 0; i <= ticks; i++) {
        const xx = bx + bw * i / ticks;
        root.appendChild(S('line', { x1: xx, x2: xx, y1: y + fs * 1.5 + bh, y2: y + fs * 1.5 + bh + 4, stroke: st.axisColor }));
        root.appendChild(textEl(xx, y + fs * 1.5 + bh + 5 + fs, fmt(min + (max - min) * i / ticks), { size: fs, font: st.font, color: st.textColor, anchor: 'middle' }));
      }
    }
  }

  function drawSlots(root, model, st, box) {
    const G = slotGeometry(model, st, box.w);
    const { fs, rowH } = G;
    const color = slotColorFn(st, model.range);
    const fmt = slotFmt(model, st);
    const gridC = st.slotGridColor || '#000000';
    const noData = model.noDataColor || null;
    const colorAt = (r, k) => r.colors ? (r.colors[k] || noData || '#FFFFFF') : (noData && !Number.isFinite(r.values[k]) ? noData : color(r.values[k]));
    if (st.showTitle && st.title) root.appendChild(textEl(box.x + (box.w - G.legendW) / 2, box.y + st.titleSize * 1.15, st.title, { size: st.titleSize, font: st.font, color: st.titleColor, anchor: 'middle', weight: 700 }));
    let y = box.y + G.titleH;
    const x0 = box.x + 6;
    const gx = x0 + G.linkColW + G.coreColW;
    const gw = Math.max(40, box.w - 6 - G.linkColW - G.coreColW - G.sumTot - G.legendW - 10);
    const sx = gx + gw; // left edge of the summary columns
    model.panels.forEach((p, pi) => {
      const n = p.to - p.from + 1;
      const cw = gw / n;
      const g = S('g');
      if (G.ptH) g.appendChild(textEl(x0, y + fs * 1.25, p.title, { size: fs, font: st.font, color: st.titleColor, weight: 700 }));
      let yy = y + G.ptH;
      // header: column titles and slot numbers
      const hb = yy + G.headH;
      g.appendChild(textEl(x0 + G.linkColW / 2, hb - fs * 0.55, 'Enlace', { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 700 }));
      g.appendChild(textEl(x0 + G.linkColW + G.coreColW / 2, hb - fs * 0.55, 'Núcleo', { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 700 }));
      let hx = sx;
      G.sumW.forEach((w, i) => { g.appendChild(textEl(hx + w / 2, hb - fs * 0.55, model.summaryHeads[i], { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 700 })); hx += w; });
      let step = Number(st.slotTickStep) || 0;
      if (!(step > 0)) {
        const need = (textWidth(String(p.to), fs - 1, st.font) + 8) / cw;
        step = [1, 2, 5, 10, 16, 20, 25, 32, 40, 50, 64, 80, 100, 160, 200].find((k) => k >= need) || Math.ceil(need);
      }
      const tfs = Math.max(7, fs - 1);
      for (let k = 0; k < n; k++) {
        const sn = p.from + k;
        if (sn !== p.from && sn % step !== 0) continue;
        const cx = gx + (k + 0.5) * cw;
        g.appendChild(textEl(cx, hb - fs * 0.55, String(sn), { size: tfs, font: st.font, color: st.textColor, anchor: 'middle' }));
        g.appendChild(S('line', { x1: cx.toFixed(2), x2: cx.toFixed(2), y1: hb - 3, y2: hb, stroke: st.axisColor, 'stroke-width': 0.8 }));
      }
      yy = hb;
      const cells = S('g', { 'shape-rendering': 'crispEdges' });
      const lines = S('g');
      const rows = p.rows;
      const top = yy;
      let groupStart = 0;
      for (let ri = 0; ri < rows.length; ri++) {
        const r = rows[ri];
        const ry = top + ri * rowH;
        // runs of equal colour
        let k = 0;
        while (k < n) {
          const c = colorAt(r, k);
          let e = k + 1, lo = r.values[k], hi = r.values[k];
          while (e < n && colorAt(r, e) === c) { const v = r.values[e]; if (v < lo) lo = v; if (v > hi) hi = v; e++; }
          const rect = S('rect', { x: (gx + k * cw).toFixed(2), y: ry.toFixed(2), width: ((e - k) * cw + 0.35).toFixed(2), height: (rowH + 0.35).toFixed(2), fill: c });
          const sl = e - k > 1 ? 'slots ' + (p.from + k) + '–' + (p.from + e - 1) : 'slot ' + (p.from + k);
          rect.appendChild(S('title', null, r.tip ? r.tip(k, e) : 'Enlace ' + r.link + ' · núcleo ' + r.core + ' · ' + sl + ': ' + (Math.abs(hi - lo) > 1e-9 ? fmt(lo) + ' a ' + fmt(hi) : fmt(lo))));
          cells.appendChild(rect);
          k = e;
        }
        g.appendChild(textEl(x0 + G.linkColW + G.coreColW / 2, ry + rowH / 2 + Math.min(fs, rowH) * 0.36, rowH >= 7 ? String(r.core) : '', { size: Math.min(fs, Math.max(6, rowH - 1)), font: st.font, color: r.quantum ? '#7b4fc9' : st.textColor, anchor: 'middle', weight: 700 }));
        if (r.summary && G.sumW.length) {
          let cx = sx;
          const sfs = Math.min(fs, Math.max(6, rowH - 1));
          G.sumW.forEach((w, i) => { if (rowH >= 7) g.appendChild(textEl(cx + w / 2, ry + rowH / 2 + sfs * 0.36, String(r.summary[i]), { size: sfs, font: st.font, color: st.textColor, anchor: 'middle' })); cx += w; });
        }
        lines.appendChild(S('line', { x1: x0 + G.linkColW, x2: sx + G.sumTot, y1: (ry + rowH).toFixed(2), y2: (ry + rowH).toFixed(2), stroke: gridC, 'stroke-width': rowH >= 6 ? 0.5 : 0, 'stroke-opacity': 0.7 }));
        const last = ri === rows.length - 1 || rows[ri + 1].link !== r.link;
        if (last) {
          const gy0 = top + groupStart * rowH, gy1 = ry + rowH;
          g.appendChild(textEl(x0 + G.linkColW / 2, (gy0 + gy1) / 2 + fs * 0.36, r.link, { size: fs, font: st.font, color: st.textColor, anchor: 'middle', weight: 700 }));
          lines.appendChild(S('line', { x1: x0, x2: sx + G.sumTot, y1: gy1.toFixed(2), y2: gy1.toFixed(2), stroke: gridC, 'stroke-width': st.slotLinkSep !== false ? 1.6 : 0.6 }));
          groupStart = ri + 1;
        }
      }
      const bottom = top + rows.length * rowH;
      if (st.slotCellGrid !== false && cw >= 5) {
        for (let k = 1; k < n; k++) { const xx = (gx + k * cw).toFixed(2); lines.appendChild(S('line', { x1: xx, x2: xx, y1: top, y2: bottom, stroke: gridC, 'stroke-width': 0.5, 'stroke-opacity': 0.8 })); }
      }
      // frame and column separators
      lines.appendChild(S('rect', { x: x0, y: top, width: (sx + G.sumTot - x0).toFixed(2), height: (bottom - top).toFixed(2), fill: 'none', stroke: gridC, 'stroke-width': 1.2 }));
      const seps = [x0 + G.linkColW, gx];
      let acc = sx;
      for (const w of G.sumW) { seps.push(acc); acc += w; }
      for (const xx of seps) lines.appendChild(S('line', { x1: xx, x2: xx, y1: top, y2: bottom, stroke: gridC, 'stroke-width': 1 }));
      g.appendChild(cells);
      g.appendChild(lines);
      root.appendChild(g);
      y += G.panelH[pi];
    });
    if (st.slotLegend === 'right') slotLegend(root, model, st, color, box.x + box.w - G.legendW + 8, box.y + G.titleH + G.ptH + G.headH, G.legendW - 10, 320, true);
    if (st.slotLegend === 'bottom') slotLegend(root, model, st, color, box.x, box.y + G.H - G.legendH, box.w, G.legendH, false);
  }

  // ------------------------------------------------------------ entry point

  // ------------------------------------------------------------ 1.18.3: comparison with a reference case

  /**
   * ref: { refLabel, mode ('abs'|'rel'), sections:[{ key, label, refVal, bars:[{ key, label, val, diff, color }] }],
   *        cols, shared }. One horizontal diverging chart per section: zero line = the reference case, bars to the
   * right = more than the reference, to the left = less. The geometry is fixed by the style (refRowH), so the
   * height is computed (refHeight).
   */
  /** Sizes and switches of the comparison chart (1.18.3: names, values and bars adjustable separately). */
  function refLook(st, fs) {
    const on = (k) => (st[k] === undefined || st[k] === '' ? st.refBoxes !== false : !!st[k]);
    const num = (k, d) => (st[k] !== undefined && st[k] !== '' && Number.isFinite(Number(st[k])) ? Number(st[k]) : d);
    const nameSize = num('refNameSize', fs * 0.95), valSize = num('refValueSize', fs);
    const nameBox = on('refNameBox'), valBox = on('refValueBox'), titleBox = on('refTitleBox');
    const nameH = nameSize * (nameBox ? 1.75 : 1.25);
    const barH = Math.max(2, num('refBarPx', Math.max(12, (Number(st.refRowH) || 72) * 0.42)));
    const nameGap = num('refNameGap', 8), nameDx = num('refNameDx', 12), valGap = num('refValueGap', 10);
    return { nameSize, valSize, nameBox, valBox, titleBox, nameH, barH, nameGap, nameDx, valGap,
      nameBold: st.refNameBold !== false, valBold: st.refValueBold !== false };
  }

  function refGeometry(model, st, W) {
    const fs = Number(st.fontSize) || 12;
    const lk = refLook(st, fs);
    const n = model.sections.length;
    const cols = Math.max(1, Math.min(model.cols || 1, n));
    const rows = Math.ceil(n / cols);
    const titleH = st.showTitle && st.title ? st.titleSize * 1.8 : 0;
    const rowH = Math.max(lk.nameH + lk.nameGap + lk.barH + 12, Number(st.refRowH) || 72);
    const headH = (Number(st.refTitleSize) || fs * 1.9) * 1.9 + (st.refShowRefValue !== false ? fs * 1.6 : 0) + 8;
    const ticksH = st.refTicks !== false ? fs * 2 : 6;
    const maxBars = Math.max(1, ...model.sections.map((s) => s.bars.length));
    const secH = headH + maxBars * rowH + ticksH + 10;
    const gap = Number(st.panelGap) || 18;
    return { fs, lk, cols, rows, titleH, rowH, headH, ticksH, secH, gap, secW: (W - gap * (cols - 1)) / cols, H: titleH + rows * secH + (rows - 1) * gap + 4 };
  }

  function refValueText(v, mode, st) {
    if (!Number.isFinite(v)) return 'n/d';
    const digits = Number.isFinite(Number(st.refDigits)) && st.refDigits !== '' ? Number(st.refDigits) : (mode === 'rel' ? 1 : 3);
    const sign = v > 0 ? '+' : v < 0 ? '−' : '';
    const a = Math.abs(v);
    const t = mode === 'rel' ? fmtNumber(a, 'fixed', digits, st.decimal) + ' %'
      : st.refFormat === 'sci' ? fmtNumber(a, 'sci', Math.max(1, digits - 1), st.decimal) : fmtNumber(a, 'fixed', digits, st.decimal);
    return sign + t;
  }

  function drawRef(root, model, st, box) {
    const g0 = refGeometry(model, st, box.w);
    const fs = g0.fs;
    const font = st.font;
    if (g0.titleH) root.appendChild(textEl(box.x + box.w / 2, box.y + st.titleSize * 1.2, st.title, { size: st.titleSize, font, color: st.titleColor, anchor: 'middle', weight: 700 }));
    const lk = g0.lk;
    const boxed = lk.titleBox;
    const outline = st.refOutline !== false;
    const nameSize = lk.nameSize, valSize = lk.valSize;
    const valStr = (b) => (b.valText !== undefined && b.valText !== '' ? b.valText : refValueText(b.diff, model.mode, st));
    const lineW = 1.2;
    const absMax = (s) => Math.max(0, ...s.bars.map((b) => (Number.isFinite(b.diff) ? Math.abs(b.diff) : 0)));
    const sharedMax = Math.max(0, ...model.sections.map(absMax));
    const valW = Math.max(...model.sections.flatMap((s) => s.bars.map((b) => textWidth(valStr(b), valSize, font, 700))), 20) + (lk.valBox ? valSize * 1.6 : 4) + lk.valGap;
    const labelBox = (parent, x, yMid, str, size, anchor, weight, opts) => {
      const bx0 = opts && opts.boxed !== undefined ? opts.boxed : boxed;
      const tw = textWidth(str, size, font, weight || 'normal');
      const padX = bx0 ? size * 0.8 : 0, h = size * 1.75;
      let x0 = anchor === 'end' ? x - tw - 2 * padX : anchor === 'middle' ? x - tw / 2 - padX : x;
      // keep the box inside its section
      if (opts && opts.lo !== undefined) {
        if (x0 + tw + 2 * padX > opts.hi) x0 = opts.hi - tw - 2 * padX;
        if (x0 < opts.lo) x0 = opts.lo;
      }
      if (bx0) parent.appendChild(S('rect', { x: x0.toFixed(1), y: (yMid - h / 2).toFixed(1), width: (tw + 2 * padX).toFixed(1), height: h.toFixed(1), fill: st.refBoxFill || '#ffffff', stroke: st.refBoxStroke || '#000000', 'stroke-width': (opts && opts.stroke) || 1.2 }));
      parent.appendChild(textEl(x0 + padX + tw / 2, yMid + size * 0.36, str, { size, font, color: (opts && opts.color) || st.titleColor || '#000000', anchor: 'middle', weight }));
      return { x0, x1: x0 + tw + 2 * padX, w: tw + 2 * padX };
    };
    model.sections.forEach((sec, i) => {
      const col = i % g0.cols, row = Math.floor(i / g0.cols);
      const bx = box.x + col * (g0.secW + g0.gap), by = box.y + g0.titleH + row * (g0.secH + g0.gap), bw = g0.secW;
      const g = S('g');
      root.appendChild(g);
      // header: reference case in a box (+ " - item")
      const suffix = sec.label ? ' - ' + sec.label : '';
      const tSize0 = Number(st.refTitleSize) || fs * 1.9;
      const wAt = (z) => textWidth(model.refLabel, z, font, 700) + (boxed ? z * 1.6 : 0) + (suffix ? textWidth(suffix, z, font, 700) + z * 0.3 : 0);
      // the title shrinks to fit the section (down to the text size)
      const tSize = Math.max(Math.min(fs, tSize0), Math.min(tSize0, tSize0 * (bw - 8) / Math.max(1, wAt(tSize0))));
      const hy = by + tSize0 * 0.95;
      const hx = Math.max(bx + 2, bx + bw / 2 - wAt(tSize) / 2);
      const hb = labelBox(g, hx, hy, model.refLabel, tSize, 'start', 700, { stroke: 1.5 });
      if (suffix) g.appendChild(textEl(hb.x1 + tSize * 0.3, hy + tSize * 0.36, suffix, { size: tSize, font, color: sec.color && st.refColorTitle !== false ? sec.color : (st.titleColor || '#000000'), weight: 700 }));
      let y = by + tSize0 * 1.9 + 4;
      if (st.refShowRefValue !== false) {
        g.appendChild(textEl(bx + bw / 2, y + fs * 0.9, 'Valor da referência: ' + (Number.isFinite(sec.refVal) ? fmtNumber(sec.refVal, 'auto', 4, st.decimal) : 'n/d'), { size: fs * 0.95, font, color: st.textColor, anchor: 'middle' }));
        y += fs * 1.6;
      }
      y += 4;
      const plotTop = y, plotH = g0.secH - (y - by) - g0.ticksH - 10;
      const cx = bx + bw / 2;
      const half = Math.max(20, bw / 2 - valW - 14);
      const M = (st.refShared !== false ? sharedMax : absMax(sec)) || 1;
      const scale = half / M;
      // grid (symmetric ticks) and zero line
      const t = linearTicks(-M, M, Math.max(4, Math.round(bw / 110)), 0);
      for (const v of t.ticks) {
        const x = cx + v * scale;
        if (Math.abs(v) < 1e-15) continue;
        g.appendChild(S('line', { x1: x.toFixed(1), x2: x.toFixed(1), y1: plotTop, y2: (plotTop + plotH).toFixed(1), stroke: st.gridColor || '#d9d9d9', 'stroke-width': 1 }));
        if (st.refTicks !== false) g.appendChild(textEl(x, plotTop + plotH + fs * 1.2, (v > 0 ? '+' : v < 0 ? '−' : '') + (model.mode === 'rel' ? fmtNumber(Math.abs(v), 'auto', 3, st.decimal, t.step) + ' %' : fmtNumber(Math.abs(v), 'auto', 3, st.decimal, t.step)), { size: fs * 0.85, font, color: st.textColor, anchor: 'middle' }));
      }
      // bars
      sec.bars.forEach((b, j) => {
        const ry = plotTop + j * g0.rowH;
        // name above the bar (gap = refNameGap), bar in the lower part of the row
        const barH = lk.barH;
        const barY = ry + g0.rowH - barH - 6;
        const nameY = barY - lk.nameGap - lk.nameH / 2;
        const d = Number.isFinite(b.diff) ? b.diff : 0;
        const x1 = cx + d * scale;
        const neg = d < 0;
        if (Number.isFinite(b.diff) && d !== 0) {
          const r = S('rect', { x: Math.min(cx, x1).toFixed(1), y: barY.toFixed(1), width: Math.abs(x1 - cx).toFixed(1), height: barH.toFixed(1), fill: b.color,
            stroke: outline ? (st.refOutlineColor || '#1f2d3a') : 'none', 'stroke-width': outline ? 2.5 : 0 });
          r.appendChild(S('title', null, b.label + ': ' + valStr(b) + ' (valor ' + fmtNumber(b.val, 'auto', 4, st.decimal) + ')'));
          g.appendChild(r);
        }
        const lim = { lo: bx + 2, hi: bx + bw - 2 };
        labelBox(g, neg ? cx - lk.nameDx : cx + lk.nameDx, nameY, b.label, nameSize, neg ? 'end' : 'start', lk.nameBold ? 700 : null, Object.assign({ boxed: lk.nameBox }, lim));
        const vx = neg ? Math.min(cx, x1) - lk.valGap : Math.max(cx, x1) + lk.valGap;
        labelBox(g, vx, barY + barH / 2, valStr(b), valSize, neg ? 'end' : 'start', lk.valBold ? 700 : null, Object.assign({ boxed: lk.valBox }, lim));
      });
      g.appendChild(S('line', { x1: cx.toFixed(1), x2: cx.toFixed(1), y1: plotTop, y2: (plotTop + plotH).toFixed(1), stroke: st.refZeroColor || '#000000', 'stroke-width': 3 }));
      if (st.border === 'panel' || st.refPanelBorder) g.appendChild(S('rect', { x: bx + 0.5, y: by + 0.5, width: bw - 1, height: g0.secH - 1, fill: 'none', stroke: st.borderColor, 'stroke-width': lineW }));
    });
  }

  function render(model, st) {
    const W = Math.max(200, Number(st.width) || 760);
    let H = Math.max(150, Number(st.height) || 440);
    if (model && model.kind === 'panels' && Number(st.panelHeight) > 0) {
      const n = model.panels.length, cols = Math.max(1, Math.min(Number(st.panelCols) || 1, n));
      const rows = Math.ceil(n / cols);
      H = Math.round((st.showTitle && st.title ? st.titleSize * 1.6 : 0) + rows * Number(st.panelHeight) + (rows - 1) * (Number(st.panelGap) || 0));
    }
    if (model && model.kind === 'topo' && st.topoAutoHeight !== false) H = Math.max(150, topoHeight(model, st, W));
    if (model && model.kind === 'slots') H = Math.max(150, slotGeometry(model, st, W).H);
    if (model && model.kind === 'ref') H = Math.max(150, Math.round(refGeometry(model, st, W).H));
    const root = S('svg', { xmlns: NS, width: W, height: H, viewBox: `0 0 ${W} ${H}`, 'font-family': st.font });
    root.appendChild(S('rect', { x: 0, y: 0, width: W, height: H, fill: st.bg }));
    if (st.border === 'outer') root.appendChild(S('rect', { x: 0.5, y: 0.5, width: W - 1, height: H - 1, fill: 'none', stroke: st.borderColor }));
    const box = { x: 0, y: 0, w: W, h: H };
    if (!model) return root;
    if (model.kind === 'line') drawLine(root, model, st, box);
    else if (model.kind === 'bars') drawBars(root, model, st, box);
    else if (model.kind === 'cores') drawCores(root, model, st, box);
    else if (model.kind === 'topo') drawTopo(root, model, st, box);
    else if (model.kind === 'slots') drawSlots(root, model, st, box);
    else if (model.kind === 'ref') drawRef(root, model, st, box);
    else if (model.kind === 'panels') {
      const n = model.panels.length;
      const cols = Math.max(1, Math.min(Number(st.panelCols) || 1, n));
      const rows = Math.ceil(n / cols);
      let top = 0;
      if (st.showTitle && st.title) {
        root.appendChild(textEl(W / 2, st.titleSize * 1.1, st.title, { size: st.titleSize, font: st.font, color: st.titleColor, anchor: 'middle', weight: 700 }));
        top = st.titleSize * 1.6;
      }
      const gap = Number(st.panelGap) || 0;
      const pw = (W - gap * (cols - 1)) / cols, ph = (H - top - gap * (rows - 1)) / rows;
      model.panels.forEach((p, i) => {
        const b = { x: (i % cols) * (pw + gap), y: top + Math.floor(i / cols) * (ph + gap), w: pw, h: ph };
        const sub = Object.assign({}, st, { panelLabel: p.label || '' });
        const opts = { title: p.title, titleSize: Math.max(10, st.titleSize - 2), noPanelLabel: !p.label };
        if (p.model.kind === 'bars') drawBars(root, p.model, sub, b, opts);
        else drawLine(root, p.model, sub, b, opts);
      });
    }
    return root;
  }

  function serialize(svgNode) {
    const clone = svgNode.cloneNode(true);
    clone.setAttribute('xmlns', NS);
    return '<?xml version="1.0" encoding="UTF-8"?>\n' + new XMLSerializer().serializeToString(clone);
  }

  /** Renders the SVG into a PNG data URL at the given scale (2 = double resolution). */
  function toPng(svgNode, scale) {
    return new Promise((resolve, reject) => {
      const text = serialize(svgNode);
      const img = new Image();
      const url = URL.createObjectURL(new Blob([text], { type: 'image/svg+xml;charset=utf-8' }));
      img.onload = () => {
        const w = Number(svgNode.getAttribute('width')), h = Number(svgNode.getAttribute('height'));
        const cv = document.createElement('canvas');
        cv.width = Math.round(w * scale); cv.height = Math.round(h * scale);
        const ctx = cv.getContext('2d');
        ctx.scale(scale, scale); ctx.drawImage(img, 0, 0, w, h);
        URL.revokeObjectURL(url);
        resolve(cv.toDataURL('image/png'));
      };
      img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Falha ao gerar PNG')); };
      img.src = url;
    });
  }

  App.figRender = { render, refValueText, serialize, toPng, defaultStyle, fmtNumber, marker, dashArray, PALETTES, PALETTE_NAMES, MARKERS, MARKER_NAMES, DASHES, FONTS, SLOT_SCALES, PATTERNS, patternFill, contrastColor };
})(window.App);
