# Etapa 1 — PICh físico e coexistência intrabanda

## Escopo implementado

Esta etapa preserva explicitamente a regra estrutural do projeto: **um `QKDSpan` nunca atravessa um nó óptico do SNetS**. `QKDTopologyPlanner` e `QKDRoutePlanner` não foram alterados. Cada link físico continua sendo uma fronteira máxima para um span; QTND pode subdividir o link em vários spans menores.

### PICh físico por QKDSpan

- `QKDPichPool` reserva um PICh dedicado para cada `QKDSpan`.
- Cada PICh ocupa slots reais em um core clássico.
- `QKDPichCircuit` registra o PICh como fonte óptica auxiliar do core.
- A potência do PICh vem de `qkdPhysicalLayer.pich.launchPowerDbm`.
- O PICh é reservado **antes** da avaliação/alocação QCh, portanto o próprio PICh candidato participa do SKR/QBER da nova sessão.
- Em caso de falha de PICh, QCh, SKR ou commit clássico, o rollback libera os recursos temporários.

Nesta etapa o PICh é fonte de ruído para a camada QKD, mas não é inserido na lista clássica de `Circuit`s usada pelo QoT dos DChs. Essa integração PICh→QoT clássico fica para a próxima etapa.

## Interferência PICh → QCh

A `QKDPhysicalLayer` inclui PIChs físicos em:

- SpRS/Raman same-core;
- SpRS intercore no modo MBSDM, quando aplicável;
- NLI/GN no host core.

Como um PICh pertence a um span longitudinal específico, a camada física filtra `QKDPichCircuit` pelo `QKDSpan` atualmente avaliado. Assim um PICh de `span 1` não gera Raman/NLI artificialmente em `span 2`, mesmo que ambos estejam no mesmo `Link` SNetS.

A ocupação espectral permanece conservadora por link físico nesta etapa: dois PIChs de spans distintos do mesmo link não reutilizam o mesmo FS.

## Proteção de sessões existentes

`QKDExistingSessionImpactEvaluator` foi generalizado:

- IC-XT mantém a regra de core adjacente + sobreposição espectral;
- Raman same-core e NLI não exigem sobreposição de slots;
- Raman intercore em MBSDM não exige sobreposição de slots;
- após reservar temporariamente os PIChs da nova requisição, todas as sessões QKD ativas são reavaliadas antes do commit do DCh.

A aceitação continua atômica: `DCh + PICh(s) + QCh(s)` só é aceita se a nova conexão e as sessões existentes preservarem os requisitos de SKR/QBER.

## Coexistência intrabanda

O bloco opcional abaixo ativa uma distância espectral mínima entre QCh e fontes clássicas do mesmo host core:

```json
{
  "pich": {
    "launchPowerDbm": 0.0
  },
  "multiband": {
    "enabled": true,
    "architecture": "MBSDM",
    "qchCenterNm": 1550.0,
    "hostCoreId": 1,
    "ramanModel": "fisico",
    "nliModel": "gn",
    "intraband": {
      "enabled": true,
      "minimumQchClassicalSeparationGHz": 200.0
    }
  }
}
```

O valor de 200 GHz **não é hard-coded**. É um parâmetro experimental configurável, baseado no cenário de Moghaddam et al. (2021), que discute a região de Raman intensa e utiliza aproximadamente 200 GHz de separação no caso same-core. Quando a regra é ativada e uma fonte clássica same-core viola a distância mínima, o candidato fica fisicamente inviável pelo fluxo normal QBER/SKR.

Para um experimento `same fiber + same core + same band`, configure também `network.qkd.pichCorePolicy = "fixed"` e `pichCoreId = hostCoreId` se desejar que o PICh esteja fisicamente no mesmo core usado como host do QCh.

## Referências usadas no código

- **[MOGH21]** E. E. Moghaddam, H. Beyranvand, J. A. Salehi, *Resource Allocation in Space Division Multiplexed Elastic Optical Networks Secured With Quantum Key Distribution*, IEEE JSAC, 2021. Base para PICh/CCh físico, core/FS, Raman same-core, FWM, leakage e separação espectral de referência.
- **[MEHDI24]** P. Mehdizadeh et al., *Quantum-Classical Coexistence in Multi-Band Optical Networks: A Noise Analysis of QKD*, IEEE Communications Letters, 2024. Base para SpRS e NLI/GN em coexistência multi-banda e interpretação de DCh+PICh como sinais clássicos.
- **[DIBAJ24]** M. R. Dibaj et al., *Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in Multi-Band and Multi-Fiber Realms*, IWCIT, 2024. Base para PICh/QCh em trusted-node spans e CCh+PICh como fontes de background do QCh.
- **[ALIA22]** O. Alia et al., *DV-QKD Coexistence With 1.6 Tbps Classical Channels Over Hollow Core Fibre*, JLT, 2022. Suporte experimental para filtragem, Raman/FWM e posicionamento espectral.

## Fora desta etapa

Ainda não foram adicionados: PICh no QoT clássico dos DChs, ASE residual no QCh, leakage de filtro detalhado, perdas explícitas de MUX/DEMUX/bypass, GGN/ISRS completo e camada clássica multi-banda completa.
