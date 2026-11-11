/*
 * Consistency checks run in the browser before saving / running.
 * They are deliberately conservative: errors only for things that would
 * certainly break the simulation; everything else is a warning. The
 * simulator still runs its own NetworkConfigValidator at start-up.
 */
(function (App) {
  'use strict';
  const { isPlainObject } = App;

  function trafficLoad(traffic) {
    const gens = (traffic && Array.isArray(traffic.requestGenerators)) ? traffic.requestGenerators : [];
    let base = 0, inc = 0;
    for (const g of gens) {
      const mu = Number(g.holdRate) || 1;
      base += (Number(g.arrivalRate) || 0) / mu;
      inc += (Number(g.arrivalRateIncrease) || 0) / mu;
    }
    return { base, inc, generators: gens.length };
  }

  function validateAll(model, catalog, info) {
    const out = [];
    const add = (level, file, msg) => out.push({ level, file, msg });
    const net = model.network, sim = model.simulation, phy = model.physicalLayer, tr = model.traffic;

    for (const f of ['network', 'physicalLayer', 'simulation', 'traffic', 'others']) {
      if (App.state.parseErrors[f]) add('err', f, 'JSON inválido: ' + App.state.parseErrors[f]);
      else if (model[f] === null || model[f] === undefined) add(f === 'others' ? 'warn' : 'err', f, 'Arquivo ausente no caso.');
    }

    // ---------------- network
    const nodeNames = new Set();
    if (isPlainObject(net)) {
      const nodes = Array.isArray(net.nodes) ? net.nodes : [];
      if (!nodes.length) add('err', 'network', 'Nenhum nó definido.');
      for (const n of nodes) {
        const name = String(n && n.name);
        if (nodeNames.has(name)) add('err', 'network', 'Nó duplicado: ' + name);
        nodeNames.add(name);
      }
      const links = Array.isArray(net.links) ? net.links : [];
      if (!links.length) add('err', 'network', 'Nenhum enlace definido.');
      const linkSet = new Set();
      links.forEach((l, i) => {
        const s = String(l.source), d = String(l.destination);
        if (!nodeNames.has(s) || !nodeNames.has(d)) add('err', 'network', `Enlace #${i} (${s}→${d}) usa nó inexistente.`);
        if (s === d) add('err', 'network', `Enlace #${i} liga o nó ${s} a ele mesmo.`);
        const key = s + '→' + d;
        if (linkSet.has(key)) add('warn', 'network', 'Enlace duplicado: ' + key);
        linkSet.add(key);
        if (!(Number(l.slots) > 0) || !Number.isInteger(Number(l.slots))) add('err', 'network', `Enlace ${key}: slots deve ser inteiro > 0.`);
        if (!(Number(l.size) > 0)) add('err', 'network', `Enlace ${key}: size (km) deve ser > 0.`);
      });
      const oneWay = [...linkSet].filter((k) => { const [s, d] = k.split('→'); return !linkSet.has(d + '→' + s); });
      if (oneWay.length) add('warn', 'network', `${oneWay.length} enlace(s) sem o sentido inverso (ex.: ${oneWay.slice(0, 3).join(', ')}).`);
      const cores = Array.isArray(net.cores) ? net.cores : [];
      const ids = cores.map((c) => Number(c.id));
      const idSet = new Set(ids);
      if (cores.length && idSet.size !== cores.length) add('err', 'network', 'IDs de núcleo repetidos.');
      for (const c of cores) {
        for (const a of (c.adjacentCores || [])) {
          if (!idSet.has(Number(a))) add('err', 'network', `Núcleo ${c.id}: vizinho ${a} não existe.`);
          else {
            const other = cores.find((x) => Number(x.id) === Number(a));
            if (other && !(other.adjacentCores || []).map(Number).includes(Number(c.id))) {
              add('warn', 'network', `Adjacência não simétrica: ${c.id}→${a} existe, mas ${a}→${c.id} não.`);
            }
          }
        }
      }
      if (!Array.isArray(net.modulations) || !net.modulations.length) add('err', 'network', 'Nenhum formato de modulação definido.');
      for (const [i, mod] of (Array.isArray(net.modulations) ? net.modulations : []).entries()) {
        if (mod && Object.prototype.hasOwnProperty.call(mod, 'SNR') && Object.prototype.hasOwnProperty.call(mod, 'OSNR'))
          add('err', 'network', `Modulação ${i + 1}: não informe OSNR e SNR simultaneamente.`);
        const limit = mod && (mod.OSNR !== undefined ? mod.OSNR : mod.SNR);
        if (limit !== undefined && !Number.isFinite(Number(limit)))
          add('err', 'network', `Modulação ${i + 1}: limiar OSNR inválido.`);
      }
      if (isPlainObject(net.qkd) && Object.prototype.hasOwnProperty.call(net.qkd, 'pichSnrCheckEnabled')
          && Object.prototype.hasOwnProperty.call(net.qkd, 'pichOsnrCheckEnabled'))
        add('err', 'network', 'Não informe pichOsnrCheckEnabled e pichSnrCheckEnabled simultaneamente.');
      if (isPlainObject(net.qkd) && net.qkd.enabled) {
        if (net.qkd.quantumCoreId !== undefined && cores.length && !idSet.has(Number(net.qkd.quantumCoreId))) {
          add('err', 'network', `qkd.quantumCoreId = ${net.qkd.quantumCoreId} não corresponde a nenhum núcleo.`);
        }
        if (!(Number(net.qkd.qtndKm) > 0)) add('err', 'network', 'qkd.qtndKm deve ser > 0.');
        for (const k of ['alternativeRoutes', 'pichSlots', 'tdmSlots']) {
          if (net.qkd[k] !== undefined && !(Number.isInteger(Number(net.qkd[k])) && Number(net.qkd[k]) >= 0)) add('err', 'network', `qkd.${k} deve ser um inteiro ≥ 0.`);
        }
        if (net.qkd.qchGranularity !== undefined && !['route', 'link', 'span'].includes(String(net.qkd.qchGranularity).toLowerCase())) add('err', 'network', 'qkd.qchGranularity deve ser route, link ou span.');
        if (net.qkd.qchSlotPolicy !== undefined && !['abne', 'firstfit', 'lowestloss', 'maxskr'].includes(String(net.qkd.qchSlotPolicy).toLowerCase())) add('err', 'network', 'qkd.qchSlotPolicy deve ser abne, firstfit, lowestloss ou maxskr.');
        if (String(net.qkd.qchSlotPolicy || '').toLowerCase() === 'maxskr' && String(net.qkd.qchGranularity || '').toLowerCase() !== 'route') add('err', 'network', 'qkd.qchSlotPolicy=maxskr requer qchGranularity=route.');
        if (net.qkd.maximumQber !== undefined && !(Number(net.qkd.maximumQber) >= 0 && Number(net.qkd.maximumQber) <= 1)) add('err', 'network', 'qkd.maximumQber deve estar em [0,1].');
        if (net.qkd.qberThresholdEnabled && net.qkd.maximumQber === undefined) add('info', 'network', 'QBER independente ativo com máximo padrão 0,11 (11%).');
        if ((net.qkd.pichOsnrCheckEnabled === false || (net.qkd.pichOsnrCheckEnabled === undefined && net.qkd.pichSnrCheckEnabled === false))) add('warn', 'network', 'A QoT/OSNR do PICh foi desativada explicitamente; para experimentos finais ative-a.');
        // 1.18.0: QCh guard band and PICh placement
        if (net.qkd.qchGuardBand !== undefined && !(Number.isInteger(Number(net.qkd.qchGuardBand)) && Number(net.qkd.qchGuardBand) >= 0)) add('err', 'network', 'qkd.qchGuardBand deve ser um inteiro ≥ 0.');
        if (net.qkd.pichGuardBand !== undefined && !(Number.isInteger(Number(net.qkd.pichGuardBand)) && Number(net.qkd.pichGuardBand) >= -1)) add('err', 'network', 'qkd.pichGuardBand deve ser −1 (guardBand da rede) ou um inteiro ≥ 0.');
        const pcp = String(net.qkd.pichCorePolicy || 'lowest').toLowerCase();
        if (!['lowest', 'fixed', 'leastused'].includes(pcp)) add('err', 'network', 'qkd.pichCorePolicy deve ser lowest, fixed ou leastUsed.');
        if (pcp === 'fixed' && Number(net.qkd.pichSlots) > 0) {
          const pc = Number(net.qkd.pichCoreId);
          if (!idSet.has(pc) || pc === Number(net.qkd.quantumCoreId)) add('err', 'network', 'qkd.pichCoreId deve ser um núcleo clássico existente (pichCorePolicy = fixed).');
        }
        if (net.qkd.pichSlotPolicy !== undefined && !['firstfit', 'lastfit', 'abne'].includes(String(net.qkd.pichSlotPolicy).toLowerCase())) add('err', 'network', 'qkd.pichSlotPolicy deve ser firstfit, lastfit ou abne.');
        // Parte 39: per-link trusted nodes
        const tnm = String(net.qkd.trustedNodeMode || 'global').toLowerCase();
        if (!['global', 'perlink'].includes(tnm)) add('err', 'network', 'qkd.trustedNodeMode deve ser global ou perLink.');
        if (tnm === 'perlink') {
          const rules = Array.isArray(net.qkd.trustedNodesPerLink) ? net.qkd.trustedNodesPerLink : [];
          const linkSet = new Set((Array.isArray(net.links) ? net.links : []).map((l) => String(l.source) + '-' + String(l.destination)));
          for (const r of rules) {
            const nm = r && typeof r.link === 'string' ? r.link.replace(/\s+/g, '') : '';
            const [a, b] = nm.split('-');
            if (!a || !b || (!linkSet.has(a + '-' + b) && !linkSet.has(b + '-' + a))) { add('err', 'network', `qkd.trustedNodesPerLink: o enlace "${r && r.link}" não existe.`); continue; }
            const hasN = r.trustedNodes !== undefined && r.trustedNodes !== null;
            if (hasN && !(Number.isInteger(Number(r.trustedNodes)) && Number(r.trustedNodes) >= 0)) add('err', 'network', `qkd.trustedNodesPerLink ${r.link}: trustedNodes deve ser um inteiro ≥ 0.`);
            if (!hasN && !(Number(r.qtndKm) > 0)) add('err', 'network', `qkd.trustedNodesPerLink ${r.link}: informe trustedNodes (≥ 0) ou qtndKm (> 0).`);
          }
          if (!rules.length) add('info', 'network', 'qkd.trustedNodeMode = perLink sem regras: todos os enlaces usam o QTND global.');
        }
        if (Number(net.qkd.pichSlots) > 0 && String(net.qkd.qchGranularity || 'link').toLowerCase() !== 'route') add('info', 'network', 'O PICh é reservado em qualquer modo de QCh, mas o cenário da qualificação usa qchGranularity = route.');
      }
    }

    // ---------------- traffic
    if (isPlainObject(tr)) {
      const gens = Array.isArray(tr.requestGenerators) ? tr.requestGenerators : [];
      if (!gens.length) add('err', 'traffic', 'Nenhum gerador de requisições.');
      let badNode = 0, badRate = 0;
      const missing = new Set();
      for (const g of gens) {
        if (nodeNames.size && (!nodeNames.has(String(g.source)) || !nodeNames.has(String(g.destination)))) {
          badNode++; missing.add(nodeNames.has(String(g.source)) ? String(g.destination) : String(g.source));
        }
        if (!(Number(g.holdRate) > 0) || !(Number(g.arrivalRate) >= 0) || !(Number(g.bitRate) > 0)) badRate++;
      }
      if (badNode) add('err', 'traffic', `${badNode} gerador(es) usam nós que não existem na rede (ex.: ${[...missing].slice(0, 4).join(', ')}).`);
      if (badRate) add('err', 'traffic', `${badRate} gerador(es) com arrivalRate/holdRate/bitRate inválidos.`);
    }

    // ---------------- simulation
    if (isPlainObject(sim)) {
      for (const k of ['requests', 'loadPoints', 'replications', 'threads']) {
        if (k in sim && (!Number.isInteger(sim[k]) || sim[k] < 1)) add('err', 'simulation', `${k} deve ser inteiro ≥ 1.`);
      }
      if (isPlainObject(sim.qkdKeyDemand)) {
        const kd = sim.qkdKeyDemand;
        const profile = String(kd.profile || 'custom').toLowerCase();
        if (!['custom', 'moghaddam2021', 'dibaj2024'].includes(profile)) add('err', 'simulation', 'qkdKeyDemand.profile deve ser custom, moghaddam2021 ou dibaj2024.');
        if (Object.prototype.hasOwnProperty.call(kd, 'updateIntervalsMileseconds')) add('err', 'simulation', 'updateIntervalsMileseconds foi removido. Use updateIntervalsMiliseconds (milissegundos).');
        const intervalNames = ['updateIntervalSeconds', 'updateIntervalsMicroseconds', 'updateIntervalsMilliseconds', 'updateIntervalsMiliseconds'];
        const present = intervalNames.filter((k) => Object.prototype.hasOwnProperty.call(kd, k) && kd[k] !== null);
        if (present.length > 1) add('err', 'simulation', 'qkdKeyDemand: apenas UM campo de intervalo é permitido (' + present.join(', ') + ').');
        if (profile !== 'custom' && present.some((k) => k !== 'updateIntervalSeconds')) add('err', 'simulation', 'Perfis bibliográficos não aceitam campos legados de intervalo.');
        const factors = { updateIntervalSeconds: 1, updateIntervalsMicroseconds: 1e-6, updateIntervalsMilliseconds: 1e-3, updateIntervalsMiliseconds: 1e-3 };
        for (const k of present) {
          const a = kd[k];
          if (!Array.isArray(a) || !a.length || a.some((v) => typeof v !== 'number' || !Number.isFinite(v) || !(v * factors[k] > 0)))
            add('err', 'simulation', 'qkdKeyDemand.' + k + ': forneça intervalos finitos e positivos.');
        }
        if (present.length === 1 && profile === 'custom') {
          const k = present[0];
          const sample = Array.isArray(kd[k]) ? kd[k][0] * factors[k] : NaN;
          if (sample > 0 && Number.isFinite(sample)) add('info', 'simulation', 'QKD: campo ' + k + ' convertido para segundos; exemplo: ' + sample + ' s, ' + (Number(kd.keySizeBits) || 256) / sample / 1e6 + ' Mbit/s.');
        }

        if (profile !== 'custom' && kd.keySizeBits !== undefined && Number(kd.keySizeBits) !== 256) add('err', 'simulation', 'Perfis bibliográficos QKD exigem chaves de 256 bits.');
        if (kd.preferGeneratorFixedRate !== false && profile !== 'custom' && isPlainObject(tr) && (tr.requestGenerators || []).some((g) => g.keyRateMbps !== undefined))
          add('warn', 'simulation', 'traffic.keyRateMbps terá prioridade sobre o perfil ' + profile + ' (desative preferGeneratorFixedRate para reproduzir a demanda bibliográfica).');
      }
      if (sim.replications === 1) add('warn', 'simulation', 'Com 1 replicação não é possível calcular intervalo de confiança.');
      if (info && sim.threads > info.cpus) add('info', 'simulation', `threads = ${sim.threads}, mas esta máquina tem ${info.cpus} processadores lógicos.`);
      const opts = (catalog && catalog.options) || {};
      const schema = App.schema.simulation.fields;
      for (const [k, h] of Object.entries(schema)) {
        if (!h.catalog || !(k in sim)) continue;
        const list = opts[h.catalog];
        if (list && list.length && !list.includes(sim[k])) add('warn', 'simulation', `${k} = "${sim[k]}" não aparece nas constantes do código-fonte.`);
      }
      if ('qkdProtectedRequestProbability' in sim) {
        const p = sim.qkdProtectedRequestProbability;
        if (!(p >= 0 && p <= 1)) add('err', 'simulation', 'qkdProtectedRequestProbability deve estar em [0, 1].');
        const qkdOn = isPlainObject(net) && isPlainObject(net.qkd) && net.qkd.enabled;
        if (p > 0 && !qkdOn) add('warn', 'simulation', 'Há requisições protegidas por QKD, mas network.qkd.enabled não está ativo.');
      }
      if (isPlainObject(sim.activeMetrics) && !Object.values(sim.activeMetrics).some(Boolean)) {
        add('warn', 'simulation', 'Nenhuma métrica ativa: a simulação não vai gerar CSVs.');
      }
      if (sim.kRouting === 'kfixedroutes' || sim.routing === 'fixedroutes') {
        add('info', 'simulation', 'Roteamento por rotas fixas: o arquivo kRoutesByPar.txt/routesByPar.txt é lido como entrada e deve corresponder à topologia.');
      }
    }

    // ---------------- request count vs generators (tail of already scheduled arrivals)
    if (isPlainObject(sim) && isPlainObject(tr) && Array.isArray(tr.requestGenerators)) {
      const gens = tr.requestGenerators.length;
      const qkdOn = isPlainObject(net) && isPlainObject(net.qkd) && net.qkd.enabled;
      const exact = qkdOn && sim.qkdExactRequestCount === true;
      if (!exact && gens > 0 && sim.requests < 2 * gens) {
        add('warn', 'simulation', `requests = ${sim.requests}, mas o tráfego tem ${gens} geradores: cada gerador já tem uma chegada agendada quando a geração para, `
          + `então cada replicação processa cerca de ${sim.requests + gens} chegadas (${Math.round(100 * gens / (sim.requests + gens))}% vindas dessa cauda). `
          + 'Aumente requests' + (qkdOn ? ' ou ative qkdExactRequestCount.' : '.'));
      }
    }

    // ---------------- QKD addon algorithms
    if (isPlainObject(sim) && isPlainObject(net) && isPlainObject(net.qkd) && net.qkd.enabled) {
      const gens = isPlainObject(tr) && Array.isArray(tr.requestGenerators) ? tr.requestGenerators : [];
      const anyGen = gens.some((g) => Number(g.qkdProtectedProbability) > 0);
      if (!(Number(sim.qkdProtectedRequestProbability) > 0) && !anyGen) add('warn', 'traffic', 'Modo QKD sem requisições protegidas: defina qkdProtectedProbability nos geradores (aba Tráfego) ou a fração padrão na aba QKD.');
      for (const g of gens) {
        if (g.qkdProtectedProbability !== undefined && !(Number(g.qkdProtectedProbability) >= 0 && Number(g.qkdProtectedProbability) <= 1)) { add('err', 'traffic', 'qkdProtectedProbability deve estar em [0, 1] (gerador ' + g.source + '→' + g.destination + ').'); break; }
        if (g.keyRateMbps !== undefined && !(Number(g.keyRateMbps) > 0)) { add('err', 'traffic', 'keyRateMbps deve ser > 0 (gerador ' + g.source + '→' + g.destination + ').'); break; }
      }
      if (isPlainObject(sim.activeMetrics) && sim.activeMetrics.QKDMetrics === false) add('info', 'simulation', 'QKDMetrics desativada: só o QKDStatistics.csv (formato antigo) será gravado.');
    }
    if (isPlainObject(sim) && isPlainObject(net) && isPlainObject(net.qkd)) {
      const qkdOn = !!net.qkd.enabled;
      const qkdCsa = ['qabne', 'qfirstfit'];
      if (qkdOn && sim.coreAndSpectrumAssignment && !qkdCsa.includes(String(sim.coreAndSpectrumAssignment).toLowerCase())) {
        add('err', 'simulation', `Com QKD habilitado a alocação de núcleo e espectro precisa ser um algoritmo do addon (${qkdCsa.join(', ')}); "${sim.coreAndSpectrumAssignment}" não é aceito.`);
      }
      if (!qkdOn && qkdCsa.includes(String(sim.coreAndSpectrumAssignment).toLowerCase())) {
        add('err', 'simulation', `"${sim.coreAndSpectrumAssignment}" só existe no addon QKD; habilite network.qkd ou use um algoritmo clássico.`);
      }
      if (String(sim.integratedRmlsa).toLowerCase() === 'qrmsta' && (!qkdOn || sim.rmlsaType !== 1)) {
        add('err', 'simulation', '"qrmsta" é um RMLSA integrado do addon QKD: exige network.qkd.enabled = true e rmlsaType = 1.');
      }
      if (qkdOn && net.qkd.qchGranularity && !['route', 'link', 'span'].includes(net.qkd.qchGranularity)) add('err', 'network', 'qkd.qchGranularity deve ser route, link ou span.');
      if (qkdOn && net.qkd.wavelengthPolicy && !['fixed', 'grid'].includes(net.qkd.wavelengthPolicy)) add('err', 'network', 'qkd.wavelengthPolicy deve ser "fixed" ou "grid".');
      if (qkdOn && net.qkd.tdmSlots !== undefined && !(Number.isInteger(net.qkd.tdmSlots) && net.qkd.tdmSlots >= 0)) add('err', 'network', 'qkd.tdmSlots deve ser inteiro ≥ 0.');
    }

    // ---------------- physical layer
    if (isPlainObject(phy)) {
      for (const retired of ['johannissonSciUseIntegral', 'johannissonXciUseIntegral',
                             'habibiEgnFallbackEnabled', 'habibiEgnNarrowbandGHz', 'johannissonUseIntegral']) {
        if (Object.prototype.hasOwnProperty.call(phy, retired))
          add('err', 'physicalLayer', `Campo descontinuado: ${retired}. Utilize johannissonNliMode, habibiNliMode e nliNarrowbandGHz.`);
      }
      if ('spanLength' in phy && !(phy.spanLength > 0)) add('err', 'physicalLayer', 'spanLength deve ser > 0.');
      if ('fiberLoss' in phy && !(phy.fiberLoss >= 0)) add('err', 'physicalLayer', 'fiberLoss deve ser ≥ 0.');
      for (const modeKey of ['johannissonNliMode', 'habibiNliMode']) {
        if (phy[modeKey] !== undefined
            && !['approximate', 'integral', 'adaptive'].includes(String(phy[modeKey]).toLowerCase()))
          add('err', 'physicalLayer', modeKey + ' deve ser approximate, integral ou adaptive.');
      }
      if (phy.nliNarrowbandGHz !== undefined && !(typeof phy.nliNarrowbandGHz === 'number'
          && Number.isFinite(phy.nliNarrowbandGHz) && phy.nliNarrowbandGHz > 0))
        add('err', 'physicalLayer', 'nliNarrowbandGHz deve ser a largura física positiva, em GHz.');
      if ((phy.habibiNliMode === 'integral' || phy.johannissonNliMode === 'integral')
          && Number(phy.polarizationModes) === 1)
        add('warn', 'physicalLayer', 'A integral GN de Poggiolini usa o fator Manakov para 2 polarizações; sua utilização com polarizationModes=1 requer validação científica específica.');
      if (phy.typeOfTestQoT !== undefined && ![0, 1].includes(phy.typeOfTestQoT))
        add('err', 'physicalLayer', 'typeOfTestQoT deve ser 0 (limiar OSNR) ou 1 (BER calculada da OSNR).');
      if (phy.crosstalkModel !== undefined && ![0, 1].includes(phy.crosstalkModel))
        add('err', 'physicalLayer', 'crosstalkModel deve ser 0 (separado) ou 1 (junto).');
      if (phy.typeOfAmplifierGain !== undefined && ![0, 1].includes(phy.typeOfAmplifierGain))
        add('err', 'physicalLayer', 'typeOfAmplifierGain deve ser 0 (fixo) ou 1 (saturado).');
      if (phy.berModel !== undefined && !['jurandir', 'legacyRectangularAwgn'].includes(phy.berModel))
        add('err', 'physicalLayer', 'berModel deve ser jurandir ou legacyRectangularAwgn.');
      if (phy.berThreshold !== undefined && phy.berThreshold !== null
          && !(typeof phy.berThreshold === 'number' && Number.isFinite(phy.berThreshold)
            && phy.berThreshold > 0 && phy.berThreshold < 0.5))
        add('err', 'physicalLayer', 'berThreshold deve estar estritamente entre 0 e 0,5 (BER pré-FEC).');
      if (phy.typeOfTestQoT === 1 && phy.berThreshold === undefined)
        add('info', 'physicalLayer', 'Modo BER sem berThreshold: limite de BER derivado do limiar de OSNR da modulação; não adiciona margens.');
      if (phy.typeOfAmplifierGain === 1)
        add('warn', 'physicalLayer', 'O ganho saturado ainda não é integralmente validado para todos os modos de amplificação.');
      for (const key of ['muxInsertionLoss', 'demuxInsertionLoss', 'switchInsertionLoss']) {
        if (phy[key] !== undefined && !(typeof phy[key] === 'number' && Number.isFinite(phy[key]) && phy[key] >= 0))
          add('err', 'physicalLayer', key + ' deve ser um número finito em dB ≥ 0.');
      }
      if ((phy.muxInsertionLoss === undefined || phy.demuxInsertionLoss === undefined) && phy.switchInsertionLoss !== undefined)
        add('info', 'physicalLayer', 'MUX/DEMUX ausentes: o simulador usa switchInsertionLoss como fallback individual; utilize os novos campos para valores independentes.');
      const r = phy.qkdReceiver;
      if (isPlainObject(r) && 'receiverOpticalEfficiency' in r && !(r.receiverOpticalEfficiency >= 0 && r.receiverOpticalEfficiency <= 1)) {
        add('err', 'physicalLayer', 'qkdReceiver.receiverOpticalEfficiency deve estar em [0, 1].');
      }
    }
    const mb = isPlainObject(model.qkdPhysicalLayer) && model.qkdPhysicalLayer.multiband;
    if (isPlainObject(mb) && Object.prototype.hasOwnProperty.call(mb, 'bidirectionalFiber')) {
      add('err', 'qkdPhysicalLayer', 'Campo bidirectionalFiber descontinuado. O SNetS usa duas fibras fisicas independentes, uma para cada sentido. Remova essa propriedade do JSON.');
    }
    if (isPlainObject(mb)) {
      const correction = String(mb.antiStokesCorrection || 'nenhuma').toLowerCase();
      if (!['nenhuma', 'medida', 'none', 'measured'].includes(correction)) add('err', 'qkdPhysicalLayer', 'multiband.antiStokesCorrection deve ser nenhuma ou medida.');
      if (correction === 'medida' && String(mb.architecture || 'MB').toUpperCase() === 'MF') add('info', 'qkdPhysicalLayer', 'Correção Raman anti-Stokes em MF só produz efeito se houver fonte Raman ativa.');
      if (correction === 'medida') add('info', 'qkdPhysicalLayer', 'A correção anti-Stokes é empírica para SMF-28; não representa calibração universal de MCF.');
    }
    const qp = model.qkdPhysicalLayer;
    if (isPlainObject(qp) && isPlainObject(qp.opticalBudget)) {
      for (const key of ['txMuxLossDb', 'rxDemuxLossDb', 'bypassLossDb', 'residualAseReceiverW']) {
        if (qp.opticalBudget[key] !== undefined && !(typeof qp.opticalBudget[key] === 'number' && Number.isFinite(qp.opticalBudget[key]) && qp.opticalBudget[key] >= 0))
          add('err', 'qkdPhysicalLayer', 'opticalBudget.' + key + ' deve ser um número finito ≥ 0 (' + (key === 'residualAseReceiverW' ? 'W' : 'dB') + ').');
      }
    }
    if (isPlainObject(qp) && isPlainObject(qp.pich) && qp.pich.launchPowerDbm !== undefined &&
        !(typeof qp.pich.launchPowerDbm === 'number' && Number.isFinite(qp.pich.launchPowerDbm)))
      add('err', 'qkdPhysicalLayer', 'pich.launchPowerDbm deve ser numérico e finito (dBm).');
    if (isPlainObject(qp) && isPlainObject(qp.detector)) {
      for (const k of ['receiverOpticalEfficiency', 'detectorEfficiency', 'darkCountProbability']) {
        const v = qp.detector[k];
        if (v !== undefined && !(v >= 0 && v <= 1)) add('err', 'qkdPhysicalLayer', 'detector.' + k + ' deve estar em [0, 1].');
      }
    }
    if (isPlainObject(phy) && isPlainObject(qp) && (phy.qkdReceiver !== undefined || phy.qkdPhysical !== undefined)) {
      add('warn', 'physicalLayer', 'Há blocos QKD antigos no physicalLayer e também o arquivo qkdPhysicalLayer: o simulador usa só o qkdPhysicalLayer.');
    }
    return out;
  }

  function issuesList(issues, emptyText) {
    const { el } = App;
    if (!issues.length) return el('div.notice.ok', emptyText || 'Nenhum problema encontrado.');
    const order = { err: 0, warn: 1, info: 2 };
    const label = { err: 'erro', warn: 'aviso', info: 'info' };
    return el('ul.issues', issues.slice().sort((a, b) => order[a.level] - order[b.level]).map((i) =>
      el('li', el('span.tag.' + i.level, label[i.level]), el('span.tag.file', i.file), el('span', i.msg))));
  }

  App.validate = { validateAll, trafficLoad, issuesList };
})(window.App);
