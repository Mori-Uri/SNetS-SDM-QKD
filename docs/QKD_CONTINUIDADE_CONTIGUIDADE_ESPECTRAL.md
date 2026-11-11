# Continuidade e contiguidade espectral QKD — QCh / PICh

## Objetivo

Os canais clássicos DCh já utilizam as regras de continuidade e contiguidade configuradas no SNetS. Esta mudança controla **de modo independente** se QCh e PICh podem *trocar o bloco contíguo de slots* ao atravessar os **nós físicos originais da rede óptica SNetS**. O comportamento padrão é estrito: nenhuma conversão de espectro na rota.

O planejamento QTND pode inserir nós confiáveis *virtuais* no meio de um enlace físico original para dividir uma troca quântica em `QKDSpan`s. **Nesses nós confiáveis a troca do bloco de slots continua proibida**, mesmo que a conversão esteja habilitada nos nós físicos da rede. Isso é uma restrição de modelagem solicitada para os experimentos; um trusted node físico poderia, em outros cenários, fazer retransmissão numa frequência diferente.

## Configuração

No objeto `qkd` do arquivo `network`:

```json
{
  "qkd": {
    "enabled": true,
    "qchSlotContinuityAtNetworkNodes": true,
    "pichSlotContinuityAtNetworkNodes": true
  }
}
```

Os campos são booleanos, opcionais e **independentes**; ambos têm padrão `true`, inclusive em arquivos JSON antigos.

| QCh | PICh | Regra |
| --- | --- | --- |
| true | true | Ambos mantêm um único bloco de slots contínuo em toda a rota. **Padrão.** |
| false | true | QCh pode trocar bloco ao atravessar um nó SNetS; PICh não. |
| true | false | PICh pode trocar bloco ao atravessar um nó SNetS; QCh não. |
| false | false | Ambos podem trocar bloco em nós SNetS; nunca nos nós QTND internos. |

**Contiguidade nunca é desativada:** cada faixa selecionada é um intervalo `[primeiroSlot,ultimoSlot]` de slots adjacentes. A chave `false` dispensa apenas a *continuidade entre enlaces físicos*, jamais permite criar um canal com dois intervalos separados no mesmo enlace. Representar canais espectralmente fragmentados exigiria alterar a representação de `Circuit`, `QKDChannelAllocation`, as validações QoT e a ocupação do espectro e **não está implementado**.

**Políticas de seleção** (`qchSlotPolicy`, incluindo `maxskr`, `firstfit` e `abne`) continuam definindo o bloco candidato *dentro de cada seção permitida*. O controle de continuidade pode impedir a admissão mesmo quando existam slots livres distintos em enlaces diferentes.

## Separação dos nós

- **Nó original SNetS:** interliga dois objetos `Link` no percurso `Route`; um novo bloco contíguo só pode ser selecionado se a chave correspondente estiver em `false`.
- **Nó confiável QTND interno:** é fronteira entre `QKDSpan`s dentro do **mesmo** objeto `Link`; ambos os canais retêm o mesmo núcleo e bloco de slots. Os `QKDSpan`s mantêm QCh/PICh ópticos independentes conforme a granularidade, mas a banda permanece fixa.
- A mudança não permite contrapropagação na mesma fibra nem altera os limites de QBER, SKR, ruídos, PICh QoT, saturação, GN/EGN ou as perdas de inserção.

## Reuso longitudinal e reserva de PICh

Os PIChs físicos continuam independentes, um por QKDSpan, inclusive quando dois trechos consecutivos utilizam a mesma banda. O núcleo clássico pode mudar em um nó SNetS; a restrição de continuidade incide sobre os slots, enquanto dentro do mesmo enlace físico conserva-se o núcleo e a banda. Para evitar rejeitar toda admissão de uma rota que contém vários spans no mesmo enlace físico, os PIChs **da própria requisição** podem referenciar a mesma reserva do espectro no enlace, pois seus trechos longitudinais são disjuntos. A potência óptica de cada PICh permanece registrada como fonte no seu respectivo QKDSpan.

O reuso entre requisições diferentes continua controlado por `pichLongitudinalReuse` e pelo modo `pichAllocation`. As reservas e fontes ópticas tentativas são revertidas quando a admissão falha.

## Granularidade dos QChs

`qchGranularity=route` realiza uma alocação em toda a rota ou por subrotas físicas se a conversão for permitida. Nos modos `link` e `span`, o provisionamento tenta conservar o bloco através de cada segmento, inclusive nos nós QTND internos. Uma requisição que precise dividir a demanda entre canais QCh com blocos diferentes na mesma seção será rejeitada porque um único canal óptico contínuo não pode representar essa composição. Isso pode aumentar a probabilidade de bloqueio e deve ser considerado ao comparar campanhas anteriores.

## Verificação no Windows 11

A partir da pasta `scripts`, em PowerShell:

```powershell
.\verificar_continuidade_espectral_qkd.bat
```

O script reconstrói `simulations/SNetS-SDM-QKD-exp.jar` e executa `QKDSlotContinuityVerification`, `QKDPichAdmissionSafetyVerification` e `QKDMaxSkrRouteVerification`. O relatório e os logs são gravados sob `simulations/verification-reports`.

**Limite de validação:** os testes de componentes executados no ambiente de desenvolvimento utilizam classes pré-compiladas da distribuição de referência. Antes de produzir resultados científicos, é necessária uma compilação completa no Windows usando o JAR base do usuário e execução da regressão ampliada da rede (coexistência, TDM, rollback, QBER, PICh QoT, saturação e roteamento).
