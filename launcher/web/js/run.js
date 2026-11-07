/* Execution tab (run now + .bat generation) and the global runs drawer. */
(function (App) {
  'use strict';
  const { el, clear, fmtNum, fmtDuration, fmtDate, fmtBytes } = App;

  const R = { list: [], logPoll: null, listPoll: null, consoleRunId: null, consoleNext: 0 };

  function defaultJar() {
    const jars = (App.state.info && App.state.info.jars) || [];
    const pref = App.state.info && App.state.info.settings.defaultJar;
    const own = App.state.info && App.state.info.simulatorJar; // Parte 44: JAR desta edição (experimental: SNetS-SDM-QKD-exp.jar)
    return (jars.find((j) => j.path === pref) || jars.find((j) => own && j.name === own && j.location === 'simulations')
      || jars.find((j) => /qkd/i.test(j.name) && j.location === 'simulations')
      || jars.find((j) => j.location === 'simulations') || jars[0] || {}).path || '';
  }

  function jarSelect(value) {
    const jars = (App.state.info && App.state.info.jars) || [];
    const sel = el('select.input', jars.map((j) => el('option', { value: j.path },
      `${j.name}  ·  ${j.location}  ·  ${fmtBytes(j.size)}  ·  ${fmtDate(j.mtime)}`)));
    sel.value = value || defaultJar();
    return sel;
  }

  // ------------------------------------------------------------------ tab

  function render() {
    const st = App.state;
    const root = el('div.stack');
    const sim = st.model.simulation || {};
    const dirty = App.dirtyFiles();
    const jars = (st.info && st.info.jars) || [];

    const jarSel = jarSelect();
    const jvm = el('input.input.mono', { type: 'text', value: (st.info && st.info.settings.jvmArgs) || '', placeholder: 'ex.: -Xmx8g' });
    const saved = App.savedInfo(st.casePath); // Parte 52
    const runBtn = el('button.btn' + (saved && !saved.active ? '' : '.primary'), { type: 'button', disabled: !!dirty.length || !jars.length },
      saved && !saved.active ? 'Executar do zero' : 'Executar simulação');
    const nSims = (Number(sim.loadPoints) || 0) * (Number(sim.replications) || 0);
    const cpus = (st.info && st.info.cpus) || 1;
    const threadsIn = el('input.input.num', { type: 'number', min: 1, max: 512, placeholder: String(sim.threads || '') });
    const settings = (st.info && st.info.settings) || {};
    const consoleCb = el('input', { type: 'checkbox', checked: !!settings.runConsole, disabled: !settings.consoleSupported });
    const savedNow = saved && !saved.active;
    if (savedNow) {
      const sj = App.savedJar(saved);
      if (sj.jar) jarSel.value = sj.jar.path;
    }
    runBtn.addEventListener('click', async () => {
      if (savedNow && !window.confirm('Executar do zero? O progresso salvo (' + App.savedText(saved) + ') será descartado.')) return;
      runBtn.disabled = true;
      try {
        const r = await App.api.run(st.casePath, jarSel.value, jvm.value, Number(threadsIn.value) || 0, consoleCb.checked, savedNow ? 'fresh' : undefined);
        App.toast('Simulação iniciada (' + r.id + ')', 'ok');
        attachConsole(r.id);
        pollList();
      } catch (e) { App.toast(e.message, 'err'); }
      finally { runBtn.disabled = !!App.dirtyFiles().length; }
    });

    const runPanel = el('section.panel',
      el('h2', 'Executar agora'),
      el('p.lead', 'Roda o simulador como processo separado, do mesmo jeito que os .bat existentes: ',
        el('code', 'java -jar <jar> ' + st.casePath.replace(/\//g, '\\')), ', a partir da pasta simulations.'),
      dirty.length ? el('div.notice.warn', { style: { marginBottom: '12px' } },
        el('span', 'Há alterações não salvas (' + dirty.join(', ') + '). O simulador lê os arquivos do disco — salve antes de executar. '),
        el('button.btn.small', { type: 'button', on: { click: () => App.selectTab('review') } }, 'Revisar e salvar')) : null,
      !jars.length ? el('div.notice.err', { style: { marginBottom: '12px' } }, 'Nenhum .jar encontrado em simulations/ nem em target/. Gere o jar do simulador ou ajuste as configurações.') : null,
      el('div.fields',
        el('div.field.wide', el('div.flabel', 'JAR do simulador'), jarSel),
        el('div.field', el('div.flabel', 'Argumentos da JVM', el('span.unit', '(opcional)')), jvm),
        el('div.field', el('div.flabel', 'Threads desta execução', el('span.unit', '(vazio = ' + (sim.threads || '?') + ', do arquivo simulation; máquina: ' + cpus + ')')), threadsIn),
        el('div.field', el('div.flabel', 'Tamanho da execução'),
          el('div', el('b', fmtNum(nSims) + ' simulações'), el('span.muted', ` (${sim.loadPoints} pontos × ${sim.replications} replicações, ${fmtNum(sim.requests)} requisições cada, ${sim.threads} threads)`)))),
      el('label.check', { style: { marginTop: '10px' } }, consoleCb, ' Abrir janela de console com o progresso (fechar a janela cancela a execução)'),
      el('div.notice.info', { style: { marginTop: '10px' } },
        'A execução roda no processo do launcher: fechar ou recarregar o navegador NÃO a interrompe — reabra http://localhost e acompanhe em "Execuções". Ela termina se você cancelar aqui, fechar a janela de console dela (se aberta) ou fechar a janela principal do launcher.'
        + (settings.consoleSupported ? '' : ' (Janelas de console por execução só existem no Windows.)')),
      el('div.toolbar', { style: { marginTop: '14px', marginBottom: 0 } }, savedNow ? resumeBtn() : null, runBtn));
    function resumeBtn() {
      const b = el('button.btn.primary', { type: 'button', disabled: !!dirty.length || !jars.length }, 'Continuar execução');
      b.addEventListener('click', async () => {
        b.disabled = true;
        try {
          const r = await App.api.run(st.casePath, jarSel.value, jvm.value, Number(threadsIn.value) || 0, consoleCb.checked, 'resume');
          App.toast('Execução retomada (' + r.id + ')', 'ok');
          attachConsole(r.id);
          await pollList();
          App.renderTab();
        } catch (e) { App.toast(e.message, 'err'); b.disabled = false; }
      });
      return b;
    }
    if (savedNow) root.appendChild(savedPanel(saved));
    root.appendChild(runPanel);

    const consolePanel = el('section.panel#consolePanel', el('h2', 'Saída da execução'));
    root.appendChild(consolePanel);
    root.appendChild(batPanel());

    // attach to the latest run of this case, if any
    const last = R.list.filter((r) => r.case === st.casePath && r.state !== 'queued').slice(-1)[0];
    setTimeout(() => {
      if (last) attachConsole(last.id);
      else clear(consolePanel).append(el('h2', 'Saída da execução'), el('p.muted', 'Nenhuma execução deste caso nesta sessão.'));
    }, 0);
    return root;
  }

  /** Parte 52: what is saved for this case: one cell per simulation (load point × replication). */
  function savedPanel(s) {
    const sims = (s.p.simulations || []).slice().sort((a, b) => a.loadPoint - b.loadPoint || a.replication - b.replication);
    const lps = [...new Set(sims.map((x) => x.loadPoint))];
    const reps = [...new Set(sims.map((x) => x.replication))].sort((a, b) => a - b);
    const per = Number(s.p.requestsPerSimulation) || 0;
    const cell = (x) => {
      if (!x) return el('td', '');
      const f = x.state === 'finished' ? 1 : per > 0 ? Math.min(1, (Number(x.requests) || 0) / per) : 0;
      const cls = x.state === 'finished' ? 'done' : x.state === 'partial' ? 'part' : 'none';
      return el('td.saved-cell.' + cls, { title: 'Carga ' + (x.loadPoint + 1) + ', replicação ' + (x.replication + 1) + ': '
        + (x.state === 'finished' ? 'concluída' : x.state === 'partial' ? fmtNum(f * 100, 1) + ' % das requisições (gravado ' + fmtDate(x.savedAt) + ')' : 'não começou') },
        x.state === 'finished' ? '✓' : x.state === 'partial' ? fmtNum(f * 100, 0) + ' %' : '–');
    };
    const sj = App.savedJar(s);
    const warn = [];
    if (s.changed.length) warn.push(el('div.notice.warn', 'Arquivos do caso mudaram depois da interrupção (' + s.changed.join(', ') + '). Se o conteúdo mudou, o simulador ignora o progresso e começa do zero.'));
    if (!sj.same) warn.push(el('div.notice.warn', sj.jar ? 'O JAR ' + sj.jar.name + ' não é o mesmo que gravou o progresso: o progresso será ignorado.' : 'O JAR que gravou o progresso (' + s.jar + ') não foi encontrado: escolha o mesmo JAR, ou o progresso será ignorado.'));
    return el('section.panel.saved-panel',
      el('h2', '⏸ Execução interrompida'),
      el('p.lead', (s.crashed ? 'A execução parou sem aviso (queda de energia ou processo encerrado); vale o último estado gravado, de até ' + fmtNum(Number(s.p.intervalMinutes) || 10, (Number(s.p.intervalMinutes) || 10) < 1 ? 1 : 0) + ' min antes. '
        : 'A execução foi cancelada depois de gravar o estado de cada simulação. ') + App.savedText(s) + '.'),
      ...warn,
      el('div.table-wrap', { style: { maxHeight: '260px' } }, el('table.grid.saved-grid',
        el('thead', el('tr', el('th', 'Carga'), reps.map((r) => el('th', 'Rep. ' + (r + 1))))),
        el('tbody', lps.map((lp) => el('tr', el('th', String(lp + 1)), reps.map((r) => cell(sims.find((x) => x.loadPoint === lp && x.replication === r)))))))),
      el('p.small.muted', '"Continuar execução" (abaixo) roda o caso de novo: as simulações concluídas (✓) não são refeitas e as em andamento continuam do ponto gravado, com os mesmos resultados de uma execução sem interrupção. Use o mesmo JAR e não altere os arquivos do caso.'),
      el('div.toolbar', { style: { margin: 0 } },
        el('button.btn.small.ghost', { type: 'button', on: { click: async () => { try { if (await App.discardSavedCase(s.path)) App.renderTab(); } catch (e) { App.toast(e.message, 'err'); } } } }, 'Descartar progresso')));
  }

  function attachConsole(id) {
    R.consoleRunId = id;
    R.consoleNext = 0;
    const panel = document.getElementById('consolePanel');
    if (!panel) return;
    const meta = el('div.run-meta');
    const bar = el('div.progress', el('div'));
    const pre = el('div.console');
    const cancel = el('button.btn.small.danger', { type: 'button' }, 'Cancelar');
    cancel.addEventListener('click', async () => {
      if (!window.confirm('Cancelar esta simulação? O simulador grava o estado de cada simulação antes de parar (pode levar alguns segundos) e você pode continuar depois.')) return;
      try { await App.api.cancelRun(id); } catch (e) { App.toast(e.message, 'err'); }
    });
    clear(panel).append(el('div.toolbar', { style: { marginBottom: '4px' } }, el('h2', 'Saída da execução'), el('span.spacer'), cancel), meta, bar, pre);
    clearInterval(R.logPoll);
    let finishedHandled = false;
    const tick = async () => {
      if (R.consoleRunId !== id || !document.body.contains(pre)) { clearInterval(R.logPoll); return; }
      let d;
      try { d = await App.api.runLog(id, R.consoleNext); } catch (e) { return; }
      const atBottom = pre.scrollTop + pre.clientHeight >= pre.scrollHeight - 30;
      for (const line of d.lines) {
        const cls = line.startsWith('> ') ? 'cmd' : /exception|error|erro/i.test(line) ? 'er' : line.startsWith('[launcher]') ? 'lw' : null;
        pre.appendChild(cls ? el('span.' + cls, line + '\n') : document.createTextNode(line + '\n'));
      }
      R.consoleNext = d.next;
      if (atBottom) pre.scrollTop = pre.scrollHeight;
      const end = d.end || Date.now();
      clear(meta).append(
        el('span.state.' + (d.savedStop && d.state === 'cancelled' ? 'saved' : d.state), runLabel(d)),
        el('span', 'Progresso ', el('b', fmtNum(d.progress * 100, 1) + ' %')),
        el('span', 'Tempo ', el('b', fmtDuration(end - d.start))),
        el('span', 'JAR ', el('b', d.jar)),
        d.exitCode !== null ? el('span', 'Código de saída ', el('b', String(d.exitCode))) : null);
      bar.firstChild.style.width = (d.progress * 100).toFixed(1) + '%';
      cancel.hidden = d.state !== 'running';
      if (d.state !== 'running') {
        clearInterval(R.logPoll);
        if (!finishedHandled) {
          finishedHandled = true;
          if (d.state === 'finished' && d.case === App.state.casePath) {
            App.refreshCaseFiles();
            panel.appendChild(el('div.notice.ok', { style: { marginTop: '10px' } },
              el('span', 'Simulação concluída. '),
              el('button.btn.small', { type: 'button', on: { click: () => App.selectTab('results') } }, 'Ver resultados')));
          }
        }
      }
    };
    tick();
    R.logPoll = setInterval(tick, 1000);
  }

  /** Parte 52: a cancelled run whose state was saved is "interrompida (progresso salvo)". */
  function runLabel(r) {
    return r.state === 'cancelled' && r.savedStop ? 'interrompida · progresso salvo' : stateLabel(r.state) + (r.resumed && r.state === 'running' ? ' (continuação)' : '');
  }
  function isActive(path) {
    return R.list.some((r) => r.case === path && (r.state === 'running' || r.state === 'queued'));
  }

  function stateLabel(s) {
    return { queued: 'na fila', running: 'executando', finished: 'concluída', failed: 'falhou', cancelled: 'cancelada' }[s] || s;
  }

  // ------------------------------------------------------------------ .bat

  function batPanel() {
    const st = App.state;
    const cases = App.allCases();
    const picked = new Set([st.casePath]);
    const filter = el('input.input', { type: 'search', placeholder: 'Filtrar casos…' });
    const list = el('div.table-wrap', { style: { maxHeight: '220px' } });
    const jarSel = jarSelect();
    const jvm = el('input.input.mono', { type: 'text', value: (st.info && st.info.settings.jvmArgs) || '', placeholder: 'ex.: -Xmx8g' });
    const pause = el('input', { type: 'checkbox', checked: true });
    const kind = el('select.input', el('option', { value: 'bat' }, '.bat (Windows)'), el('option', { value: 'sh' }, '.sh (Linux/macOS)'));
    const name = el('input.input.mono', { type: 'text', value: 'run_' + App.caseNameOf(st.casePath) + '.bat' });
    const ext = () => '.' + kind.value;
    kind.addEventListener('change', () => { name.value = name.value.replace(/\.(bat|sh)$/i, '') + ext(); regen(); });
    const preview = el('textarea.input', { rows: 18, spellcheck: false, wrap: 'off' });
    let edited = false;
    preview.addEventListener('input', () => { edited = true; });

    const drawList = () => {
      clear(list);
      const q = filter.value.trim().toLowerCase();
      const tbody = el('tbody');
      for (const c of cases) {
        if (q && !c.path.toLowerCase().includes(q)) continue;
        const cb = el('input', { type: 'checkbox', checked: picked.has(c.path) });
        cb.addEventListener('change', () => {
          if (cb.checked) picked.add(c.path); else picked.delete(c.path);
          updatePlan();
          if (!name.dataset.touched) name.value = (picked.size === 1 ? 'run_' + App.caseNameOf([...picked][0]) : 'run_lote_' + new Date().toISOString().slice(0, 10)) + ext();
          regen();
        });
        tbody.appendChild(el('tr', el('td', { style: { width: '1%' } }, el('label.check', { style: { padding: '4px 8px' } }, cb)), el('td', { style: { padding: '4px 8px' } }, el('span.mono', c.path))));
      }
      list.appendChild(el('table.grid', tbody));
    };
    name.addEventListener('input', () => { name.dataset.touched = '1'; });

    const regen = () => {
      if (edited && !window.confirm('Descartar as edições manuais do script?')) return;
      edited = false;
      const ordered = cases.map((c) => c.path).filter((p) => picked.has(p));
      preview.value = kind.value === 'sh' ? buildSh(ordered, jarSel.value, jvm.value)
        : buildBat(ordered, jarSel.value, jvm.value, pause.checked);
    };
    filter.addEventListener('input', drawList);
    jarSel.addEventListener('change', regen);
    jvm.addEventListener('input', App.debounce(regen, 300));
    pause.addEventListener('change', regen);

    const save = el('button.btn', { type: 'button' }, 'Salvar script em simulations/');
    const cpus = (st.info && st.info.cpus) || 1;
    const settings = (st.info && st.info.settings) || {};
    const mode = el('select.input',
      el('option', { value: 'sequential' }, 'Uma após a outra (fila)'),
      el('option', { value: 'parallel' }, 'Em paralelo (threads divididas igualmente)'));
    const totalThreads = el('input.input.num', { type: 'number', min: 1, max: 512 });
    const consoleCb = el('input', { type: 'checkbox', checked: !!settings.runConsole, disabled: !settings.consoleSupported });
    const plan = el('div.small.muted');
    const freshCb = el('input', { type: 'checkbox' }); // Parte 52
    const freshLbl = el('label.check', freshCb, ' Começar do zero os casos com execução interrompida (descarta o progresso salvo)');
    const updatePlan = () => {
      const n = picked.size;
      const nSaved = [...picked].filter((p) => App.savedInfo(p)).length;
      freshLbl.hidden = !nSaved;
      const par = mode.value === 'parallel';
      totalThreads.placeholder = par ? String(cpus) : 'do arquivo de cada caso';
      const T = Number(totalThreads.value) || (par ? cpus : 0);
      if (!n) { plan.textContent = 'Selecione os casos.'; return; }
      if (par) {
        const total = Math.max(n, T);
        const per = [...Array(n)].map((_, i) => Math.floor(total / n) + (i < total % n ? 1 : 0));
        plan.textContent = n + ' caso(s) ao mesmo tempo; threads por caso: ' + [...new Set(per)].join(' ou ') + ' (total ' + total + ', máquina ' + cpus + ').'
          + (n > cpus ? ' Atenção: mais casos que processadores — cada um terá 1 thread e todos disputam a CPU e a memória.' : '');
      } else {
        plan.textContent = n + ' caso(s) em sequência; cada um usa ' + (T ? T + ' threads' : 'as threads do seu arquivo simulation') + '.';
      }
      if (nSaved) plan.textContent += ' ' + nSaved + ' deles têm execução interrompida e ' + (freshCb.checked ? 'começam do zero.' : 'continuam de onde pararam.');
    };
    mode.addEventListener('change', updatePlan);
    freshCb.addEventListener('change', updatePlan);
    totalThreads.addEventListener('input', updatePlan);
    const runBatch = el('button.btn.primary', { type: 'button' }, 'Executar os casos selecionados');
    runBatch.addEventListener('click', async () => {
      if (!picked.size) { App.toast('Selecione ao menos um caso.', 'err'); return; }
      if (App.dirtyFiles().length && picked.has(st.casePath)) { App.toast('Salve as alterações deste caso antes de executar.', 'err'); return; }
      const ordered = cases.map((c) => c.path).filter((p) => picked.has(p));
      try {
        const out = await App.api.batch(ordered, mode.value, jarSel.value, jvm.value, Number(totalThreads.value) || 0, consoleCb.checked, freshCb.checked ? 'fresh' : undefined);
        App.toast(out.length + ' caso(s) ' + (mode.value === 'parallel' ? 'iniciados em paralelo' : 'na fila de execução'), 'ok');
        pollList(); openDrawer();
      } catch (e) { App.toast(e.message, 'err'); }
    });
    const origDraw = () => updatePlan();
    save.addEventListener('click', async () => {
      if (!picked.size) { App.toast('Selecione ao menos um caso.', 'err'); return; }
      try {
        let r = await App.api.writeScript(name.value.trim(), preview.value, false);
        if (r.exists) {
          if (!window.confirm(name.value + ' já existe. Sobrescrever?')) return;
          r = await App.api.writeScript(name.value.trim(), preview.value, true);
        }
        App.toast('Script salvo: ' + r.file, 'ok', 6000);
      } catch (e) { App.toast(e.message, 'err'); }
    });

    drawList();
    regen();
    origDraw();
    return el('section.panel',
      el('h2', 'Vários casos'),
      el('p.lead', 'Executa vários casos direto no launcher — em fila (um após o outro) ou em paralelo, dividindo as threads entre eles — ou gera um script .bat/.sh no padrão dos run_*.bat.'),
      el('div.two-col',
        el('div.stack-s', { style: { alignSelf: 'start' } }, el('div.flabel', 'Casos'), filter,
          App.allNoneButtons(() => {
            const q = filter.value.trim().toLowerCase();
            for (const c of cases) if (!q || c.path.toLowerCase().includes(q)) picked.add(c.path);
            drawList(); updatePlan(); regen();
          }, () => { picked.clear(); drawList(); updatePlan(); regen(); }),
          list,
          el('div.muted.small', 'A ordem de execução segue a ordem da lista.')),
        el('div.form-grid',
          el('div.field', el('div.flabel', 'JAR'), jarSel),
          el('div.field', el('div.flabel', 'Argumentos da JVM'), jvm),
          el('div.field', el('div.flabel', 'Modo de execução'), mode),
          el('div.field', el('div.flabel', 'Threads', el('span.unit', '(fila: por caso; paralelo: total a dividir)')), totalThreads),
          el('label.check', consoleCb, ' Uma janela de console por caso (fechá-la cancela aquele caso)'),
          freshLbl,
          plan,
          el('div.toolbar', { style: { margin: 0 } }, runBatch))),
      el('details.group', { style: { marginTop: '14px' } }, el('summary', 'Gerar script .bat / .sh'),
        el('div.group-body',
          el('div.fields',
            el('div.field', el('div.flabel', 'Tipo de script'), kind),
            el('div.field', el('div.flabel', 'Nome do arquivo'), name)),
          el('label.check', pause, 'Pausar no final (.bat: manter a janela aberta)'),
          el('div.field', { style: { marginTop: '10px' } }, el('div.flabel', 'Conteúdo (pode ser editado antes de salvar)'), preview),
          el('div.toolbar', { style: { marginTop: '12px', marginBottom: 0 } }, save,
            el('button.btn', { type: 'button', on: { click: () => App.copyText(preview.value) } }, 'Copiar')))));
  }

  function jarRef(jarPath) {
    const j = ((App.state.info && App.state.info.jars) || []).find((x) => x.path === jarPath);
    if (!j) return jarPath;
    return j.location === 'simulations' ? '%ROOT%' + j.name : '%ROOT%..\\target\\' + j.name;
  }

  function buildBat(cases, jarPath, jvmArgs, pause) {
    const lines = [
      '@echo off',
      'setlocal',
      'REM Gerado pelo SNetS Launcher em ' + new Date().toLocaleString('pt-BR'),
      'REM Casos: ' + cases.length,
      'set "ROOT=%~dp0"',
      'set "JAR=' + jarRef(jarPath) + '"',
      'set "JAVA_OPTS=' + (jvmArgs || '').trim() + '"',
      'set "FAILED=0"',
      '',
      'if not exist "%JAR%" (',
      '  echo ERRO: %JAR% nao encontrado.',
      pause ? '  pause' : null,
      '  exit /b 1',
      ')',
      '',
      'pushd "%ROOT%"',
    ].filter((x) => x !== null);
    for (const c of cases) lines.push('call :run "' + c.replace(/\//g, '\\') + '"');
    lines.push(
      'popd',
      '',
      'echo ============================================',
      'if "%FAILED%"=="0" (',
      '  echo Todas as simulacoes terminaram com sucesso.',
      ') else (',
      '  echo Simulacoes com falha: %FAILED%',
      ')',
    );
    if (pause) lines.push('pause');
    lines.push(
      'exit /b %FAILED%',
      '',
      ':run',
      'echo ============================================',
      'echo Simulacao: %~1',
      'echo Inicio: %DATE% %TIME%',
      'if not exist "%~1\\simulation" (',
      '  echo ERRO: caso "%~1" nao encontrado.',
      '  set /a FAILED+=1',
      '  exit /b 0',
      ')',
      'java %JAVA_OPTS% -jar "%JAR%" "%~1"',
      'if errorlevel 1 (',
      '  echo ERRO: a simulacao "%~1" terminou com codigo %ERRORLEVEL%.',
      '  set /a FAILED+=1',
      ')',
      'echo Fim: %DATE% %TIME%',
      'exit /b 0',
      '');
    return lines.join('\n');
  }

  function buildSh(cases, jarPath, jvmArgs) {
    const j = ((App.state.info && App.state.info.jars) || []).find((x) => x.path === jarPath);
    const jarRel = !j ? jarPath : j.location === 'simulations' ? '$ROOT/' + j.name : '$ROOT/../target/' + j.name;
    const lines = [
      '#!/usr/bin/env bash',
      '# Gerado pelo SNetS Launcher em ' + new Date().toLocaleString('pt-BR'),
      'ROOT="$(cd "$(dirname "$0")" && pwd)"',
      'JAR="' + jarRel + '"',
      'JAVA_OPTS="' + (jvmArgs || '').trim() + '"',
      'FAILED=0',
      'cd "$ROOT" || exit 1',
      '[ -f "$JAR" ] || { echo "ERRO: $JAR nao encontrado"; exit 1; }',
      'run_case() {',
      '  echo "============================================"',
      '  echo "Simulacao: $1  ($(date))"',
      '  if [ ! -f "$1/simulation" ]; then echo "ERRO: caso $1 nao encontrado"; FAILED=$((FAILED+1)); return; fi',
      '  java $JAVA_OPTS -jar "$JAR" "$1" || { echo "ERRO: $1 terminou com codigo $?"; FAILED=$((FAILED+1)); }',
      '}',
    ];
    for (const c of cases) lines.push('run_case "' + c + '"');
    lines.push('echo "============================================"',
      'if [ "$FAILED" -eq 0 ]; then echo "Todas as simulacoes terminaram com sucesso."; else echo "Simulacoes com falha: $FAILED"; fi',
      'exit $FAILED', '');
    return lines.join('\n');
  }

  // ------------------------------------------------------------------ drawer / header indicator

  async function pollList() {
    try { R.list = await App.api.runs(); } catch (e) { return; }
    const running = R.list.filter((r) => r.state === 'running');
    const queued = R.list.filter((r) => r.state === 'queued');
    document.getElementById('runsDot').className = 'dot' + (running.length ? ' running' : '');
    document.getElementById('runsLabel').textContent = running.length || queued.length
      ? `Executando (${running.length}${queued.length ? ' + ' + queued.length + ' na fila' : ''})` : 'Execuções';
    const prev = R.prevRunning || new Set();
    for (const r of R.list) {
      if (prev.has(r.id) && r.state !== 'running') {
        if (r.kind !== 'tool') {
          App.toast(`${App.caseNameOf(r.case)}: ${runLabel(r)}`, r.state === 'finished' ? 'ok' : r.savedStop ? 'warn' : 'err', 6000);
          App.loadTree();
          if (r.state === 'finished' && r.case === App.state.casePath) App.refreshCaseFiles();
          if (r.case === App.state.casePath && App.state.tab === 'run') setTimeout(() => App.loadTree().then(() => { if (App.state.tab === 'run') App.renderTab(); }), 600);
        }
      }
    }
    const startedNow = running.some((r) => !prev.has(r.id));
    R.prevRunning = new Set(running.map((r) => r.id));
    if (startedNow && App.drawSavedBanner) App.drawSavedBanner();
    if (!document.getElementById('runsDrawer').hidden) drawDrawer();
    clearTimeout(R.listPoll);
    R.listPoll = setTimeout(pollList, running.length || queued.length ? 2000 : 8000);
  }

  /** Parte 52: every case with an interrupted execution (this session or earlier: power cut, closed launcher). */
  R.pickSaved = new Set();
  function savedSection() {
    const all = App.savedCases ? App.savedCases() : [];
    if (!all.length) return null;
    for (const p of [...R.pickSaved]) if (!all.find((s) => s.path === p)) R.pickSaved.delete(p);
    const mode = el('select.input', { style: { width: 'auto' } },
      el('option', { value: 'sequential' }, 'em fila (uma após a outra)'),
      el('option', { value: 'parallel' }, 'em paralelo (threads divididas)'));
    mode.value = R.savedMode || 'sequential';
    mode.addEventListener('change', () => { R.savedMode = mode.value; });
    const go = el('button.btn.small.primary', { type: 'button', disabled: !R.pickSaved.size }, 'Continuar selecionadas (' + R.pickSaved.size + ')');
    go.addEventListener('click', async () => {
      go.disabled = true;
      try { await App.resumeCases(all.map((s) => s.path).filter((p) => R.pickSaved.has(p)), mode.value); R.pickSaved.clear(); } catch (e) { App.toast(e.message, 'err'); }
      drawDrawer();
    });
    const drop = el('button.btn.small.ghost', { type: 'button', disabled: !R.pickSaved.size }, 'Descartar o progresso');
    drop.addEventListener('click', async () => {
      if (!window.confirm('Descartar o progresso salvo de ' + R.pickSaved.size + ' caso(s)? As próximas execuções começam do zero.')) return;
      try { for (const p of R.pickSaved) await App.api.discardSaved(p); R.pickSaved.clear(); await App.loadTree(); } catch (e) { App.toast(e.message, 'err'); }
      drawDrawer();
    });
    const allBox = el('input', { type: 'checkbox', checked: R.pickSaved.size === all.length, title: 'Marcar todas' });
    allBox.addEventListener('change', () => { if (allBox.checked) all.forEach((s) => R.pickSaved.add(s.path)); else R.pickSaved.clear(); drawDrawer(); });
    const rows = all.sort((a, b) => b.updated - a.updated).map((s) => {
      const cb = el('input', { type: 'checkbox', checked: R.pickSaved.has(s.path) });
      cb.addEventListener('change', () => { if (cb.checked) R.pickSaved.add(s.path); else R.pickSaved.delete(s.path); drawDrawer(); });
      const sj = App.savedJar(s);
      const problem = s.changed.length ? 'arquivos do caso alterados' : !sj.same ? 'JAR diferente' : '';
      return el('div.run-card.saved-card',
        el('div.top', el('label.check', cb, el('span.path', s.path)), el('span.state.saved', s.crashed ? 'interrompida sem aviso' : 'interrompida')),
        el('div.progress', el('div', { style: { width: (s.fraction * 100).toFixed(1) + '%' } })),
        el('div.sub', App.savedText(s) + (problem ? ' · atenção: ' + problem + ' (o progresso será ignorado)' : '')),
        el('div.toolbar', { style: { margin: 0 } },
          el('button.btn.small', { type: 'button', on: { click: async () => { try { await App.resumeCases([s.path]); } catch (e) { App.toast(e.message, 'err'); } drawDrawer(); } } }, 'Continuar'),
          el('button.btn.small.ghost', { type: 'button', on: { click: () => { closeDrawer(); App.openCase(s.path, 'run'); } } }, 'Abrir caso')));
    });
    return el('div.saved-section',
      el('div.toolbar', { style: { margin: '0 0 6px' } }, el('h3', { style: { margin: 0 } }, '⏸ Interrompidas (' + all.length + ')'), el('span.spacer'),
        el('label.check.small', allBox, ' todas')),
      el('div.small.muted', { style: { marginBottom: '6px' } }, 'Casos com progresso salvo que não estão rodando: canceladas aqui, numa fila ou execução paralela, ou paradas por queda de energia.'),
      ...rows,
      el('div.toolbar', { style: { marginTop: '6px' } }, el('span.small', 'Continuar'), mode, go, drop));
  }

  function drawDrawer() {
    const box = clear(document.getElementById('runsList'));
    const sv = savedSection();
    if (sv) box.appendChild(sv);
    if (!R.list.length) { box.appendChild(el('p.muted', 'Nenhuma execução iniciada nesta sessão do launcher.')); return; }
    const queued = R.list.filter((r) => r.state === 'queued').length;
    if (queued) {
      box.appendChild(el('div.toolbar', el('span.small', queued + ' na fila'), el('span.spacer'),
        el('button.btn.small.danger', { type: 'button', on: { click: async () => { await App.api.clearQueue(); pollList(); } } }, 'Esvaziar fila')));
    }
    for (const r of R.list.slice().reverse()) {
      const end = r.end || Date.now();
      const isTool = r.kind === 'tool';
      box.appendChild(el('div.run-card',
        el('div.top', el('span.path', isTool ? '⚙ ' + r.label : r.case), el('span.state.' + (r.savedStop && r.state === 'cancelled' ? 'saved' : r.state), runLabel(r))),
        el('div.progress', el('div', { style: { width: (r.progress * 100).toFixed(1) + '%' } })),
        el('div.sub', (r.state === 'queued' ? r.jar + ' · aguardando' : `${r.jar} · início ${fmtDate(r.start)} · ${fmtDuration(end - r.start)} · ${fmtNum(r.progress * 100, 1)} %`)
          + (r.threads ? ' · ' + r.threads + ' threads' : '') + (r.group ? ' · lote ' + r.group : '') + (r.console && r.state === 'running' ? (r.consoleAttached ? ' · janela de console aberta' : ' · abrindo console') : '')),
        el('div.toolbar', { style: { margin: 0 } },
          isTool ? el('button.btn.small', { type: 'button', on: { click: () => showToolLog(r) } }, 'Ver saída')
            : r.state === 'queued' ? null
              : el('button.btn.small', { type: 'button', on: { click: () => { closeDrawer(); App.openCase(r.case, 'run').then(() => attachConsole(r.id)); } } }, 'Abrir saída'),
          r.state === 'queued' || r.state === 'running'
            ? el('button.btn.small.ghost', { type: 'button', on: { click: async () => { await App.api.cancelRun(r.id); pollList(); } } }, r.state === 'queued' ? 'Remover da fila' : 'Cancelar') : null,
          // Parte 52: an interrupted run with saved progress continues from here
          !isTool && (r.state === 'cancelled' || r.state === 'failed') && App.savedInfo && App.savedInfo(r.case) && !isActive(r.case)
            ? el('button.btn.small.primary', { type: 'button', on: { click: async () => { try { await App.resumeCases([r.case]); } catch (e) { App.toast(e.message, 'err'); } } } }, 'Continuar') : null)));
    }
  }
  async function showToolLog(r) {
    try {
      const d = await App.api.runLog(r.id, 0);
      App.modal(r.label, el('div.console', { style: { height: '440px' } }, d.lines.join('\n')), null);
    } catch (e) { App.toast(e.message, 'err'); }
  }

  function openDrawer() { document.getElementById('runsDrawer').hidden = false; drawDrawer(); if (App.loadTree) App.loadTree().then(() => { if (!document.getElementById('runsDrawer').hidden) drawDrawer(); }); }
  function closeDrawer() { document.getElementById('runsDrawer').hidden = true; }

  App.run = { render, pollList, openDrawer, closeDrawer, attachConsole, buildBat, buildSh, defaultJar, isActive };
})(window.App);
