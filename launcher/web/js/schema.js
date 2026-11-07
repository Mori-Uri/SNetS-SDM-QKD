/*
 * Presentation hints for the SNetS configuration files.
 *
 * These hints only decide labels, grouping and input types. They are NOT a
 * strict schema: any field present in a file but absent here is still shown
 * (under "Campos adicionais") and is always preserved when saving, so the
 * launcher keeps working while the simulator's configuration evolves.
 * Nested fields use dotted keys ("qkdReceiver.detectorGateWindowPs").
 */
(function (App) {
  'use strict';

  const S = {};

  S.simulation = {
    title: 'Simulação',
    file: 'simulation',
    exclude: ['activeMetrics'], // edited in the "Métricas" tab
    groups: [
      { title: 'Execução', keys: ['requests', 'loadPoints', 'replications', 'threads', 'trafficRandomSeed'] },
      {
        title: 'Algoritmos (GPRMCSA)',
        desc: 'As opções vêm das constantes do código-fonte do simulador (GPRMCSA.java e fábricas de algoritmos QKD).',
        keys: ['rmlsaType', 'networkType', 'routing', 'kRouting', 'integratedRmlsa', 'modulationSelection',
          'spectrumAssignment', 'coreAndSpectrumAssignment', 'powerAssignment', 'reallocation', 'grooming',
          'regeneratorAssignment'],
      },
      { title: 'QKD', keys: ['qkdProtectedRequestProbability', 'qkdProtectionRandomSeed', 'qkdKeyDemand', 'qkdWarmupFraction', 'qkdExactRequestCount'] },
      // 1.20.0 (Parte 45)
      { title: 'Save states (experimental)', desc: 'Ligados por padrão (Parte 52), mesmo sem este grupo no arquivo. O simulador grava o estado de cada simulação (carga × replicação) em savestate/ a cada intervalo, ao terminar e ao ser cancelado (botão Cancelar, Ctrl+C ou fechar a janela de console). Uma queda de energia não pode ser avisada: vale o último estado gravado. "Continuar execução" (no caso ou em Execuções) roda o caso de novo: as simulações concluídas não são refeitas e as interrompidas continuam do ponto salvo, com os mesmos resultados. O estado é ignorado se algum arquivo de entrada mudar (exceto threads) ou se o JAR for outro. A pasta savestate é apagada quando o caso termina.', keys: ['saveState'] },
    ],
    fields: {
      requests: { label: 'Requisições por replicação', type: 'int', min: 1 },
      loadPoints: { label: 'Pontos de carga', type: 'int', min: 1, help: 'Carga do ponto i = carga inicial + i × incremento (definidos no arquivo traffic).' },
      replications: { label: 'Replicações', type: 'int', min: 1, help: 'Usadas para média e intervalo de confiança.' },
      threads: { label: 'Threads', type: 'int', min: 1 },
      trafficRandomSeed: { label: 'Semente do tráfego', type: 'int' },
      rmlsaType: { label: 'Tipo de RMLSA', type: 'int', enum: { 0: '0 — sequencial (roteamento → modulação → espectro)', 1: '1 — integrado (integratedRmlsa)' } },
      networkType: { label: 'Tipo de rede', type: 'int', enum: { 0: '0 — transparente', 1: '1 — translúcida' } },
      routing: { label: 'Roteamento', catalog: 'routing' },
      kRouting: { label: 'Roteamento k-caminhos', catalog: 'kRouting' },
      integratedRmlsa: { label: 'RMLSA integrado', catalog: 'integratedRmlsa' },
      modulationSelection: { label: 'Seleção de modulação', catalog: 'modulationSelection' },
      spectrumAssignment: { label: 'Alocação de espectro', catalog: 'spectrumAssignment' },
      coreAndSpectrumAssignment: { label: 'Alocação de núcleo e espectro', catalog: 'coreAndSpectrumAssignment' },
      powerAssignment: { label: 'Atribuição de potência', catalog: 'powerAssignment' },
      reallocation: { label: 'Realocação', catalog: 'reallocation' },
      grooming: { label: 'Grooming', catalog: 'grooming' },
      regeneratorAssignment: { label: 'Atribuição de regeneradores', catalog: 'regeneratorAssignment' },
      qkdProtectedRequestProbability: { label: 'Fração de requisições protegidas por QKD', type: 'prob', default: 0.0, help: 'Probabilidade de cada requisição pedir também uma sessão QKD (canal quântico + chave secreta) além do circuito clássico. Vale para os geradores do traffic sem qkdProtectedProbability. 0 = nenhuma requisição protegida; 0,2 = em média 20 %.' },
      qkdProtectionRandomSeed: { label: 'Semente da classificação QKD', type: 'int' },
      qkdKeyDemand: { label: 'Demanda de chave QKD' },
      'qkdKeyDemand.keySizeBits': { label: 'Tamanho da chave', unit: 'bits', type: 'int', help: 'Demanda de chave sorteada = tamanho ÷ intervalo de renovação (ex.: 256 bits / 128 µs = 2 Mbit/s). Usada pelos geradores sem keyRateMbps.' },
      'qkdKeyDemand.updateIntervalsMicroseconds': { label: 'Intervalos de renovação da chave', unit: 'µs', help: 'Lista separada por vírgulas; cada requisição protegida sorteia um deles com igual probabilidade.' },
      'qkdKeyDemand.randomSeed': { label: 'Semente da demanda de chave', type: 'int' },
      qkdWarmupFraction: { label: 'Warm-up das métricas QKD', type: 'prob', default: 0.1, help: 'Fração das chegadas de cada replicação descartada nas estatísticas QKD (padrão 0,1, a mesma regra das métricas clássicas do SNetS). Use 0 em estudos semiestáticos.' },
      saveState: { label: 'Save states', default: { enabled: true, intervalMinutes: 10, keepAfterFinish: false }, absentEnabled: true },
      'saveState.enabled': { label: 'Gravar e retomar save states', default: true, help: 'Desligado: cancelar ou uma queda de energia perdem a execução inteira.' },
      'saveState.keepAfterFinish': { label: 'Manter a pasta savestate depois que o caso termina', default: false, help: 'Normalmente ela é apagada quando os resultados são gravados.' },
      'saveState.intervalMinutes': { label: 'Intervalo entre gravações', unit: 'min', default: 10, help: 'Cada gravação serializa a simulação inteira (rede, circuitos, eventos pendentes); em redes grandes leva de frações de segundo a poucos segundos. É o máximo que se perde numa queda de energia.' },
      qkdExactRequestCount: { label: 'Número exato de requisições', default: false, help: 'Processa exatamente "requests" chegadas por replicação. Sem isso o SNetS processa requests + (nº de geradores) chegadas, pois cada gerador já tem uma chegada agendada quando a geração para.' },
    },
  };

  S.physicalLayer = {
    title: 'Camada física',
    file: 'physicalLayer',
    groups: [
      {
        title: 'Modelos e ativação',
        keys: ['physicalLayerModel', 'crosstalkModel', 'typeOfTestQoT', 'activeQoT', 'activeQoTForOther', 'activeASE',
          'activeNLI', 'activeXT', 'activeXTForOther', 'rateOfFEC'],
      },
      { title: 'Potência', keys: ['power', 'fixedPowerSpectralDensity', 'referenceBandwidthForPowerSpectralDensity'] },
      {
        title: 'Fibra',
        keys: ['spanLength', 'fiberLoss', 'fiberNonlinearity', 'fiberDispersion', 'centerFrequency', 'constantOfPlanck',
          'polarizationModes'],
      },
      {
        title: 'Amplificadores e nós',
        keys: ['noiseFigureOfOpticalAmplifier', 'powerSaturationOfOpticalAmplifier', 'noiseFactorModelParameterA1',
          'noiseFactorModelParameterA2', 'typeOfAmplifierGain', 'amplificationFrequency', 'switchInsertionLoss'],
      },
      { title: 'Fibra multinúcleo (crosstalk entre núcleos)', keys: ['couplingCoefficient', 'propagationConstant', 'bendingRadius', 'corePitch'] },
      // 1.20.3 (Parte 49): QoT clássica por banda
      { title: 'QoT clássica por banda (experimental)', desc: 'Cada circuito usa a atenuação, a figura de ruído, γ e β2 da sua própria frequência, e o ISRS inclina a potência entre as bandas (aproximação triangular, Zirngibl 1998; Semrau et al. 2018). Sem este grupo, ou com ele desligado, nada muda. Para C+L use centerFrequency = 190,6 THz e 992 slots de 12,5 GHz (184,4–196,8 THz).', keys: ['multiBand'] },
      { title: 'QKD — formato antigo', desc: 'Casos antigos guardavam aqui os parâmetros QKD. Desde a Parte 26 eles ficam no arquivo próprio qkdPhysicalLayer (aba Camada física QKD); use o botão acima para movê-los.', keys: ['qkdReceiver', 'qkdPhysical'] },
    ],
    fields: {
      physicalLayerModel: { label: 'Modelo da camada física', type: 'int' },
      crosstalkModel: { label: 'Modelo de crosstalk', type: 'int' },
      typeOfTestQoT: { label: 'Tipo de teste de QoT', type: 'int' },
      activeQoT: { label: 'QoT do novo circuito (QoTN)' },
      activeQoTForOther: { label: 'QoT dos outros circuitos (QoTO)' },
      activeASE: { label: 'Ruído ASE' },
      activeNLI: { label: 'Interferência não linear (NLI)' },
      activeXT: { label: 'Crosstalk do novo circuito' },
      activeXTForOther: { label: 'Crosstalk nos outros circuitos' },
      rateOfFEC: { label: 'Taxa de FEC' },
      power: { label: 'Potência de lançamento', unit: 'dBm' },
      fixedPowerSpectralDensity: { label: 'Densidade espectral de potência fixa' },
      referenceBandwidthForPowerSpectralDensity: { label: 'Banda de referência da PSD', unit: 'Hz' },
      spanLength: { label: 'Comprimento do span', unit: 'km', min: 0 },
      fiberLoss: { label: 'Atenuação da fibra', unit: 'dB/km', min: 0 },
      fiberNonlinearity: { label: 'Coeficiente não linear (γ)' },
      fiberDispersion: { label: 'Dispersão (β2/D)' },
      centerFrequency: { label: 'Frequência central', unit: 'Hz' },
      constantOfPlanck: { label: 'Constante de Planck', unit: 'J·s' },
      polarizationModes: { label: 'Modos de polarização' },
      noiseFigureOfOpticalAmplifier: { label: 'Figura de ruído do amplificador', unit: 'dB' },
      powerSaturationOfOpticalAmplifier: { label: 'Potência de saturação do amplificador', unit: 'dBm' },
      noiseFactorModelParameterA1: { label: 'Parâmetro A1 do modelo de ruído' },
      noiseFactorModelParameterA2: { label: 'Parâmetro A2 do modelo de ruído' },
      typeOfAmplifierGain: { label: 'Tipo de ganho do amplificador', type: 'int' },
      amplificationFrequency: { label: 'Frequência de amplificação', unit: 'Hz' },
      switchInsertionLoss: { label: 'Perda de inserção do comutador', unit: 'dB' },
      couplingCoefficient: { label: 'Coeficiente de acoplamento (h)', unit: 'm⁻¹' },
      propagationConstant: { label: 'Constante de propagação (β)' },
      bendingRadius: { label: 'Raio de curvatura', unit: 'm' },
      corePitch: { label: 'Distância entre núcleos (core pitch)', unit: 'm' },
      multiBand: { label: 'QoT por banda', default: { enabled: true, attenuationModel: 'g652d', waterPeakDbPerKm: 0.0, noiseFigureDb: { S: 7.0, C: 5.0, L: 6.0 }, dispersionSlopePsNm2Km: 0.057, gammaScalesWithFrequency: true, isrs: true, ramanGainSlopePerWKmTHz: 0.028 } },
      'multiBand.enabled': { label: 'Ativar a QoT por banda', default: true },
      'multiBand.attenuationModel': { label: 'Atenuação por frequência', default: 'g652d', enum: {
        g652d: 'g652d — curva G.652.D (Ferrari et al., 2020): ~0,19 dB/km em C e L, maior em S',
        constante: 'constante — fiberLoss em todas as bandas' } },
      'multiBand.waterPeakDbPerKm': { label: 'Pico de OH em 1383 nm', unit: 'dB/km', default: 0.0 },
      'multiBand.noiseFigureDb': { label: 'Figura de ruído por banda', unit: 'dB', default: { S: 7.0, C: 5.0, L: 6.0 }, help: 'Chaves O, E, S, C, L, U. Banda ausente usa a figura de ruído do amplificador (grupo Amplificadores).' },
      'multiBand.dispersionSlopePsNm2Km': { label: 'Inclinação da dispersão (S)', unit: 'ps/(nm²·km)', default: 0.057, help: 'D(λ) = D + S·(λ − λ_ref), com λ_ref = centerFrequency.' },
      'multiBand.gammaScalesWithFrequency': { label: 'γ proporcional à frequência', default: true, help: 'γ(f) = γ·f/f_ref (área efetiva constante).' },
      'multiBand.isrs': { label: 'Espalhamento Raman estimulado entre canais (ISRS)', default: true, help: 'As frequências altas cedem potência às baixas; o amplificador do span repõe a potência de cada canal, e o ISRS entra como perda extra no ASE. A mudança do NLI pelo ISRS não é modelada.' },
      'multiBand.ramanGainSlopePerWKmTHz': { label: 'Inclinação do ganho Raman (C_r)', unit: '1/(W·km·THz)', default: 0.028, help: 'SSMF: 0,028 (Semrau et al., 2018). A verificação VMB-13e compara com as equações acopladas usando o ganho Raman medido do GNPy.' },
      qkdReceiver: { label: 'Receptor QKD (formato antigo)' },
      'qkdReceiver.detectorGateWindowPs': { label: 'Janela de detecção (T_g)', unit: 'ps' },
      'qkdReceiver.receiverOpticalEfficiency': { label: 'Eficiência óptica do receptor (η_f)', type: 'prob' },
      'qkdReceiver.receiverTransferModel': { label: 'Modelo de transferência ao receptor' },
      'qkdReceiver.intercoreCrosstalkEnabled': { label: 'Crosstalk intercore no receptor QKD', help: 'Desligado = p_b = p_dark (canais clássicos não perturbam a QKD, como no cenário multifibra).' },
      'qkdReceiver.receiverTransferModel': { label: 'Modelo de transferência ao receptor', enum: { receiver_referenced: 'receiver_referenced — P_XT referida ao receptor (T = 1)', distributed: 'distributed — atenuação distribuída ao longo do trecho' } },
      qkdPhysical: { label: 'BB84 decoy (formato antigo)' },
      'qkdPhysical.meanPhotonNumber': { label: 'Número médio de fótons (μ)' },
      'qkdPhysical.pulseIntervalPs': { label: 'Intervalo entre pulsos (T_s)', unit: 'ps' },
      'qkdPhysical.errorCorrectionInefficiency': { label: 'Ineficiência da correção de erros (η_ec)' },
      'qkdPhysical.misalignmentError': { label: 'Erro de alinhamento (e_d)', type: 'prob' },
      'qkdPhysical.detectorEfficiency': { label: 'Eficiência do detector (η_d)', type: 'prob' },
      'qkdPhysical.darkCountProbability': { label: 'Contagem escura (p_dark)', type: 'prob' },
      'qkdPhysical.qchBandwidthGHz': { label: 'Largura do QCh', unit: 'GHz' },
      'qkdPhysical.qchPowerDbm': { label: 'Potência do QCh', unit: 'dBm' },
      'qkdPhysical.attenuationFloorDbPerKm': { label: 'Atenuação mínima α₀', unit: 'dB/km' },
      'qkdPhysical.attenuationQuadraticDbPerKmNm2': { label: 'Coeficiente quadrático de α(λ)', unit: 'dB/km/nm²' },
      'qkdPhysical.attenuationCenterNm': { label: 'λ de mínima atenuação', unit: 'nm' },
    },
  };

  S.qkdPhysicalLayer = {
    title: 'Camada física QKD',
    file: 'qkdPhysicalLayer',
    groups: [
      { title: 'Fonte e canal quântico', desc: 'Laser atenuado do BB84 com estados decoy.', keys: ['source'] },
      { title: 'Protocolo (pós-processamento)', keys: ['protocol'] },
      { title: 'Detector e receptor', desc: 'p_b = 1 − (1 − p_dark)(1 − p_XT), com p_XT = 1 − exp(−η_d·η_f·P_XT·T_g/hν).', keys: ['detector'] },
      { title: 'Fibra (atenuação do QCh)', desc: 'α(λ) = α₀ + c·(λ − λ₀)².', keys: ['fiber'] },
      { title: 'Crosstalk dos núcleos clássicos no QCh', keys: ['crosstalk'] },
      // 1.19.0 (Parte 43): multibanda
      { title: 'Multibanda (experimental)', desc: 'Arquitetura MF, MB ou MB-SDM e o modelo de espalhamento Raman (SpRS). O núcleo QKD da rede passa a representar a banda do QCh (MB, MB-SDM) ou a fibra escura (MF); declare-o sem núcleos vizinhos. Sem este grupo, nada muda. p_b = 1 − (1 − p_dark)(1 − p_XT)(1 − p_SpRS).', keys: ['multiband'] },
    ],
    fields: {
      source: { label: 'Fonte', default: { meanPhotonNumber: 0.7, pulseIntervalPs: 250.0, qchPowerDbm: -80.0, qchBandwidthGHz: 12.5 } },
      'source.meanPhotonNumber': { label: 'Número médio de fótons (μ)', default: 0.7, help: 'Dibaj et al. (2024): 0,7; Moghaddam et al. (2021): 0,5.' },
      'source.pulseIntervalPs': { label: 'Intervalo entre pulsos (T_s)', unit: 'ps', default: 250.0 },
      'source.qchPowerDbm': { label: 'Potência do QCh (informativa)', unit: 'dBm', default: -80.0 },
      'source.qchBandwidthGHz': { label: 'Largura do QCh', unit: 'GHz', default: 12.5, help: 'Define quantos slots do núcleo QKD cada QCh ocupa.' },
      protocol: { label: 'Protocolo', default: { errorCorrectionInefficiency: 1.16, misalignmentError: 0.015 } },
      'protocol.errorCorrectionInefficiency': { label: 'Ineficiência da correção de erros (f)', default: 1.16 },
      'protocol.misalignmentError': { label: 'Erro de alinhamento (e_d)', type: 'prob', default: 0.015 },
      detector: { label: 'Detector', default: { detectorEfficiency: 0.3, darkCountProbability: 1e-8, detectorGateWindowPs: 100.0, receiverOpticalEfficiency: 1.0 } },
      'detector.detectorEfficiency': { label: 'Eficiência do detector (η_d)', type: 'prob', default: 0.3 },
      'detector.darkCountProbability': { label: 'Contagem escura por janela (p_dark)', type: 'prob', default: 1e-8 },
      'detector.detectorGateWindowPs': { label: 'Janela de detecção (T_g)', unit: 'ps', default: 100.0 },
      'detector.receiverOpticalEfficiency': { label: 'Eficiência óptica dos filtros do receptor (η_f)', type: 'prob', default: 1.0 },
      fiber: { label: 'Fibra', default: { attenuationFloorDbPerKm: 0.18, attenuationQuadraticDbPerKmNm2: 8e-6, attenuationCenterNm: 1550.0 } },
      'fiber.attenuationFloorDbPerKm': { label: 'Atenuação mínima α₀', unit: 'dB/km', default: 0.18 },
      'fiber.attenuationQuadraticDbPerKmNm2': { label: 'Coeficiente quadrático c', unit: 'dB/km/nm²', default: 8e-6, help: '0 = atenuação plana (como em Moghaddam et al., 2021, α = 0,21 dB/km).' },
      'fiber.attenuationCenterNm': { label: 'λ de mínima atenuação (λ₀)', unit: 'nm', default: 1550.0 },
      crosstalk: { label: 'Crosstalk', default: { intercoreCrosstalkEnabled: true, receiverTransferModel: 'receiver_referenced' } },
      'crosstalk.intercoreCrosstalkEnabled': { label: 'Crosstalk intercore no receptor QKD', default: true, help: 'Desligado = p_b = p_dark (os canais clássicos não perturbam o QKD, como no cenário multifibra).' },
      'crosstalk.receiverTransferModel': { label: 'Como a potência de XT do trecho chega ao receptor', default: 'receiver_referenced', enum: {
        receiver_referenced: 'receiver_referenced — P_XT = P·ISO·h·L já referida ao receptor (T = 1)',
        distributed: 'distributed — cada ponto do trecho sofre a atenuação restante até o receptor',
        attenuated: 'attenuated — P_XT·10^(−αL/10): o canal clássico se atenua junto com o QCh (Moghaddam et al., 2021, eq. 1)' } },
      multiband: { label: 'Multibanda', default: { enabled: true, architecture: 'MB', ramanModel: 'fisico', qchCenterNm: 1381.0, hostCoreId: 0, intercoreRaman: true, bidirectionalFiber: false, classicalBandStartThz: 191.3, attenuationModel: 'g652d', waterPeakDbPerKm: 0.0, temperatureK: 300.0, nliModel: 'nenhum' } },
      'multiband.enabled': { label: 'Ativar a camada física multibanda', default: true },
      'multiband.architecture': { label: 'Arquitetura', default: 'MB', enum: {
        MF: 'MF — QCh numa fibra escura dedicada (p_b = p_dark)',
        MB: 'MB — QCh em outra banda da fibra monomodo do tráfego clássico (SpRS do mesmo núcleo)',
        MBSDM: 'MB-SDM — QCh em outra banda de um núcleo da MCF (SpRS do núcleo e dos vizinhos)' } },
      'multiband.ramanModel': { label: 'Modelo de espalhamento Raman (SpRS)', default: 'fisico', enum: {
        nenhum: 'nenhum — SpRS desprezado (Dibaj et al., JLT 2025, banda O)',
        artigo: 'artigo — L·e^(−α_c L) e metade dos fótons detectados (Dibaj et al., JLT 2025, eq. 6; Mehdizadeh et al., 2024, conta todos os fótons: ver VALIDACAO_LITERATURA.md)',
        fisico: 'fisico — comprimento efetivo exato e todos os fótons do filtro detectados (Eraerds et al., 2010)' } },
      'multiband.qchCenterNm': { label: 'λ central do QCh', unit: 'nm', default: 1381.0, help: 'Centro da grade de QChs (wavelengthPolicy "grid") ou λ de todos os QChs ("fixed"). 1260–1460 nm = bandas O e E.' },
      'multiband.hostCoreId': { label: 'Núcleo clássico que divide a fibra/núcleo com o QCh', default: 0, help: 'MB: o núcleo da fibra monomodo. MB-SDM: o núcleo da MCF que carrega o QCh. Ignorado em MF.' },
      'multiband.bidirectionalFiber': { label: 'Os dois sentidos do enlace na mesma fibra', default: false, help: 'Ligado: os circuitos do enlace inverso (b→a) andam na mesma fibra em sentido contrário e somam SpRS backward (Eraerds et al., 2010, eq. 3; Cai et al., 2020, eq. 18). Desligado: um sentido por fibra (padrão do SNetS).' },
      'multiband.intercoreRaman': { label: 'SpRS dos núcleos vizinhos (MB-SDM)', default: true, help: 'Cai, Sun e Ji (2020), eqs. 11 e 18, com o h do crosstalk da camada física.' },
      'multiband.classicalBandStartThz': { label: 'Início da grade clássica (slot 1)', unit: 'THz', default: 191.3, help: 'Deve ser igual ao início da grade clássica da camada física: centerFrequency − slots × 12,5 GHz / 2. Banda C (centerFrequency 193,85 THz, 320 slots): 191,85. Comb C+L (centerFrequency 190,6 THz, 992 slots): 184,4.' },
      'multiband.attenuationModel': { label: 'Modelo de atenuação', default: 'g652d', enum: {
        g652d: 'g652d — G.652.D de 1260 a 1675 nm (Rayleigh + IR; Ferrari et al., 2020)',
        quadratica: 'quadratica — o ajuste do grupo Fibra (só banda C)' } },
      'multiband.waterPeakDbPerKm': { label: 'Pico de OH em 1383 nm', unit: 'dB/km', default: 0.0, help: '0 para fibra G.652.D de baixo pico d’água.' },
      'multiband.temperatureK': { label: 'Temperatura da fibra', unit: 'K', default: 300.0 },
      'multiband.antiStokesCorrection': { label: 'Correção anti-Stokes do SpRS (Parte 48)', default: 'nenhuma', enum: {
        nenhuma: 'nenhuma — perfil Raman com o fator de Bose-Einstein (padrão)',
        medida: 'medida — lado anti-Stokes ajustado às medidas em SMF-28 (Chapuran et al., 2009; Nweke et al., 2005): ×2,2 de 17 a 25 THz da bomba; não muda até 10 THz nem o lado Stokes' } },
      'multiband.nliModel': { label: 'Ruído não linear (NLI) no QCh', default: 'nenhum', enum: {
        nenhum: 'nenhum — sem NLI',
        gn: 'gn — modelo GN (todos os produtos de FWM) dos circuitos do núcleo hospedeiro; só pesa com o QCh perto da banda clássica (banda S/C)' } },
      'multiband.nliGammaPerWKm': { label: 'γ da fibra (NLI)', unit: '1/(W·km)', default: 1.2 },
      'multiband.nliBeta2Ps2PerKm': { label: 'β2 (NLI)', unit: 'ps²/km', default: -22.317 },
      'multiband.nliBeta3Ps3PerKm': { label: 'β3 (NLI)', unit: 'ps³/km', default: 0.1331 },
    },
  };

  S.network = {
    title: 'Rede',
    file: 'network',
    groups: [
      { title: 'Geral', keys: ['guardBand', 'bvtSpectralAmplitude'] },
      { title: 'Nós', keys: ['nodes'], open: false },
      { title: 'Enlaces', keys: ['links'], open: false },
      { title: 'Núcleos da fibra multinúcleo', keys: ['cores'], open: false },
      { title: 'Formatos de modulação', keys: ['modulations'], open: false },
      { title: 'QKD', keys: ['qkd'] },
    ],
    fields: {
      guardBand: { label: 'Banda de guarda', unit: 'slots', type: 'int', min: 0, help: 'Slots livres entre dois circuitos clássicos vizinhos no mesmo núcleo (cada lado). Vale também para o PICh, salvo network.qkd.pichGuardBand.' },
      bvtSpectralAmplitude: { label: 'Amplitude espectral do BVT', type: 'int' },
      nodes: { label: 'Nós', table: true },
      links: { label: 'Enlaces (direcionais)', table: true, help: 'Cada sentido é um enlace. size em km; spectrum = largura do slot em Hz.' },
      cores: { label: 'Núcleos', table: true, help: 'adjacentCores: lista de núcleos vizinhos (fonte de crosstalk).' },
      modulations: { label: 'Modulações', table: true, help: 'maxRange em km; SNR e XT são os limiares (dB).' },
      qkd: { label: 'QKD na rede', noToggle: true, default: { enabled: false, qtndKm: 30.0, quantumCoreId: 1, qchGranularity: 'route', qchSlotPolicy: 'abne', wavelengthPolicy: 'grid', alternativeRoutes: 2, pichSlots: 1, undirectedChannels: false, tdmSlots: 0 } },
      'qkd.alternativeRoutes': { label: 'Rotas QKD alternativas', type: 'int', min: 0, default: 0, help: 'Quantas rotas (k menores, além da rota do circuito clássico) são tentadas quando o QCh não cabe na rota clássica. 0 = só a rota clássica.' },
      'qkd.pichSlots': { label: 'Slots de cada PICh', type: 'int', min: 0, default: 0, help: 'Canal público de interação (discussão pública do BB84) num núcleo clássico. 0 = não modelado.' },
      'qkd.pichMode': { label: 'Modelo do PICh', default: 'segment', enum: { segment: 'segment — um PICh por enlace da rota QKD, terminando nos nós como o QCh e compartilhado pelas sessões (padrão)', route: 'route — um PICh dedicado por sessão, contínuo na rota inteira (Partes 23–25)' } },
      'qkd.pichSessionsPerChannel': { label: 'Sessões por PICh', type: 'int', min: 0, default: 0, help: 'Só no modo segment. 0 = ilimitado; N = abre outro PICh no enlace quando N sessões já o usam.' },
      'qkd.qchSharing': { label: 'Compartilhamento dos QChs (modos link/span)', default: 'shared', enum: { shared: 'shared — a sessão usa a taxa de chave que sobra num QCh ativo antes de abrir outro', dedicated: 'dedicated — cada sessão abre o seu próprio QCh (sem compartilhamento e sem TDM)' } },
      'qkd.qTransmittersPerNode': { label: 'QTx por nó', type: 'int', min: 0, default: 0, help: 'Transmissores quânticos em cada nó QKD (cada trecho de um QCh ativo usa um QTx na origem). 0 = ilimitado. Um nó pode ter valor próprio no campo qTransmitters da lista de nós.' },
      'qkd.qReceiversPerNode': { label: 'QRx por nó', type: 'int', min: 0, default: 0, help: 'Receptores quânticos em cada nó QKD (um por trecho de QCh ativo que termina no nó). 0 = ilimitado. Valor próprio: campo qReceivers do nó.' },
      'qkd.qTransmittersPerTrustedNode': { label: 'QTx por nó confiável intermediário', type: 'int', min: -1, default: -1, help: 'Nós confiáveis criados a cada QTND dentro dos enlaces. −1 = mesmo limite dos nós da rede; 0 = ilimitado.' },
      'qkd.qReceiversPerTrustedNode': { label: 'QRx por nó confiável intermediário', type: 'int', min: -1, default: -1 },
      'qkd.spanLanePlacement': { label: 'Posição dos QChs no núcleo QKD (modos span/link)', default: 'first', enum: { first: 'first — QCh k no slot k+1 (artigo-base)', wavelength: 'wavelength — QCh no slot do seu comprimento de onda (meio da banda primeiro)' }, help: 'Define onde cada QCh fica no espectro e, portanto, com quais canais clássicos dos núcleos vizinhos ele se sobrepõe (crosstalk). Com "first", a SKR usa o k-ésimo melhor λ da grade, mas o QCh ocupa o slot k+1.' },
      'qkd.qchSlotPolicy': { label: 'Ordem de escolha do slot do QCh', default: 'abne', enum: { abne: 'abne — regra do ABNE/ACINE para o núcleo QKD: centro medium fit, externo par last fit, externo ímpar first fit (padrão desde a Parte 40)', firstfit: 'firstfit — menor índice de slot primeiro', lowestloss: 'lowestloss — slot mais próximo do mínimo de atenuação (só granularidade route)', maxskr: 'maxskr — avalia todas as faixas livres e escolhe a de maior SKR mínima da rota (só granularidade route; com wavelengthPolicy grid escolhe o λ do QCh com menos ruído)' }, help: 'Vale nos modos route, link e span. Com o núcleo QKD 1 (ímpar), abne e firstfit dão o mesmo resultado.' },
      'qkd.qchGuardBand': { label: 'Banda de guarda entre QChs', unit: 'slots', type: 'int', min: 0, default: 0, help: 'Slots livres entre dois QChs do núcleo QKD. 0 = sem guarda (como até a Parte 39).' },
      'qkd.pichCorePolicy': { label: 'Núcleo do PICh', default: 'lowest', enum: { lowest: 'lowest — núcleo clássico de menor número (padrão)', fixed: 'fixed — o núcleo de pichCoreId (se estiver cheio, os outros)', leastUsed: 'leastUsed — núcleo clássico com menos slots ocupados no enlace' }, help: 'O núcleo QKD e os núcleos desativados nunca recebem PICh.' },
      'qkd.pichCoreId': { label: 'Núcleo fixo do PICh', type: 'int', min: 0, help: 'Usado com pichCorePolicy = fixed.' },
      'qkd.pichSlotPolicy': { label: 'Slot do PICh no núcleo', default: 'firstfit', enum: { firstfit: 'firstfit — menor slot livre (padrão)', lastfit: 'lastfit — maior slot livre', abne: 'abne — regra do ABNE/ACINE do núcleo escolhido' } },
      'qkd.pichGuardBand': { label: 'Banda de guarda do PICh', unit: 'slots', type: 'int', min: -1, default: -1, help: '−1 = a mesma dos circuitos clássicos (guardBand da rede).' },
      'qkd.enabled': { label: 'QKD habilitado' },
      'qkd.qtndKm': { label: 'QTND (distância entre nós confiáveis)', unit: 'km' },
      'qkd.quantumCoreId': { label: 'Núcleo reservado para QKD', type: 'int' },
      'qkd.qchGranularity': { label: 'Granularidade dos QChs', default: 'link', enum: { route: 'route — QCh dedicado com o mesmo slot em todos os trechos da rota QKD (qualificação)', link: 'link — um QCh cobre todos os trechos do enlace', span: 'span — um conjunto de QChs por trecho QTND (artigo-base)' } },
      'qkd.undirectedChannels': { label: 'QChs compartilhados pelos dois sentidos', default: false, help: 'Os dois sentidos de um par de fibras usam os mesmos QChs (a chave é simétrica).' },
      'qkd.tdmSlots': { label: 'Slots TDM por QCh', type: 'int', min: 0, default: 0, help: 'TDM = multiplexação no tempo: o tempo de cada QCh é dividido em N slots e cada sessão reserva ceil(d/C·N) slots de um único QCh (artigo-base: 256). 0 = TDM desligado: as sessões dividem a taxa de chave do QCh de forma contínua (ou não dividem, com qchSharing = dedicated).' },
      'qkd.wavelengthPolicy': { label: 'Comprimento de onda dos QChs', default: 'fixed', enum: { fixed: 'fixed — todos em λ de mínima atenuação', grid: 'grid — k-ésimo QCh no k-ésimo melhor λ da grade' } },
    },
  };

  S.others = {
    title: 'Others',
    file: 'others',
    groups: [
      { title: 'Variáveis dos algoritmos', keys: ['variables'], openObject: true },
      { title: 'Pesos do KBP', keys: ['kbpWeights'], openObject: true },
    ],
    fields: {
      variables: { label: 'variables', help: 'Valores lidos pelos algoritmos (ex.: k, alpha1). São gravados como texto, como no arquivo original.', openObject: true },
      kbpWeights: { label: 'kbpWeights', openObject: true },
    },
  };

  App.schema = S;
})(window.App);
