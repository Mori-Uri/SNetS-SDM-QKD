/* Thin client for the launcher's local HTTP API. */
(function (App) {
  'use strict';

  async function call(method, path, params, body) {
    const qs = params ? '?' + new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== undefined && v !== null)
    ).toString() : '';
    let res;
    try {
      res = await fetch('/api/' + path + qs, {
        method,
        headers: body !== undefined ? { 'Content-Type': 'text/plain; charset=utf-8' } : undefined,
        body,
      });
    } catch (e) {
      throw new Error('Sem conexão com o launcher. A janela do servidor foi fechada?');
    }
    let data;
    try { data = await res.json(); } catch (e) { throw new Error('Resposta inválida do servidor (' + res.status + ')'); }
    if (!res.ok || (data && data.ok === false && data.error)) {
      throw new Error((data && data.error) || ('Erro HTTP ' + res.status));
    }
    return data;
  }

  App.api = {
    info: () => call('GET', 'info'),
    saveSettings: (s) => call('POST', 'settings', null, new URLSearchParams(s).toString()),
    // Parte 59: application (window, tray, close policy, updates)
    appExit: (mode) => call('POST', 'app/exit', { mode }),
    updateCheck: () => call('GET', 'app/update-check'),
    updateDownload: (asset) => call('POST', 'app/update-download', { asset }),
    tree: () => call('GET', 'tree'),
    catalog: () => call('GET', 'catalog'),
    readCase: (path) => call('GET', 'case', { path }),
    writeFile: (path, name, content, mtime, backup) => call('POST', 'file', { path, name, mtime, backup }, content),
    deleteRoutes: (path, backup) => call('POST', 'routes/delete', { path, backup }),
    csv: (path, name) => call('GET', 'csv', { path, name }),
    createCase: (parent, name, from, copyRoutes) => call('POST', 'case/create', { parent, name, from, copyRoutes }),
    createFolder: (parent, name) => call('POST', 'folder/create', { parent, name }),
    writeScript: (name, content, overwrite) => call('POST', 'script', { name, overwrite }, content),
    // Parte 52: how = 'fresh' (discard the saved progress first) or 'resume' (continue it)
    run: (path, jar, jvmArgs, threads, consoleWin, how) => call('POST', 'run', { path, jar, jvmArgs, threads: threads || null, console: consoleWin === undefined ? null : String(!!consoleWin), fresh: how === 'fresh' ? '1' : null, resume: how === 'resume' ? '1' : null }),
    batch: (cases, mode, jar, jvmArgs, threads, consoleWin, how) => call('POST', 'run/batch', { mode, jar, jvmArgs, threads: threads || null, console: consoleWin === undefined ? null : String(!!consoleWin), fresh: how === 'fresh' ? '1' : null, resume: how === 'resume' ? '1' : null }, cases.join('\n')),
    discardSaved: (path) => call('POST', 'savestate/discard', { path }),
    runs: () => call('GET', 'runs'),
    // Parte 55: 'run/output' em vez de 'run/log' (extensões de bloqueio de anúncio barram URLs com "/log?")
    runLog: (id, from) => call('GET', 'run/output', { id, from }),
    cancelRun: (id) => call('POST', 'run/cancel', { id }),
    openFolder: (path) => call('POST', 'open-folder', { path }),
    queue: (cases, jar, jvmArgs) => call('POST', 'queue', { jar, jvmArgs }, cases.join('\n')),
    clearQueue: () => call('POST', 'queue/clear'),
    tools: (jar) => call('GET', 'tools', { jar }),
    jarInfo: () => call('GET', 'jar/info'),
    buildJar: (base, out, verify) => call('POST', 'jar/build', { base, out, verify: verify ? 'true' : null }),
    runTool: (id, jar, queued, jvmArgs) => call('POST', 'tool/run', { id, jar, queued: queued ? 'true' : null, jvmArgs }),
    text: (path, optional) => call('GET', 'text', { path, optional: optional ? 'true' : null }),
    files: (path) => call('GET', 'files', { path }),
    figures: () => call('GET', 'figures'),
    figure: (name) => call('GET', 'figure', { name }),
    saveFigure: (name, json) => call('POST', 'figure', { name }, json),
    // Parte 55: padrão de aparência por tipo de gráfico
    figureStyles: () => call('GET', 'figure-styles'),
    saveFigureStyles: (json) => call('POST', 'figure-styles', null, json),
    saveFigureImage: (name, ext, body) => call('POST', 'figure/image', { name, ext }, body),
    figureImageName: (name, exts) => call('GET', 'figure/image-name', { name, exts }),
    deleteFigure: (name) => call('POST', 'figure/delete', { name }),
    openFiguresFolder: () => call('POST', 'figures/open-folder'),
  };
})(window.App);
