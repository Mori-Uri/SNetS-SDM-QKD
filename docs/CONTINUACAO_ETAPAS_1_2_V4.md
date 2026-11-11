**ARQUIVO HISTÓRICO (v4): implementação bidirecional experimental RETIRADA da versão atual. NÃO seguir as instruções de contrapropagação deste documento. Na versão atual, A→B e B→A usam duas fibras físicas independentes. Consulte `VALIDACAO_FIBRAS_DIRECIONAIS_E_CENARIOS.md`.**

# SNetS-SDM-QKD v4 — Raman longitudinal bidirecional e limites físicos de PICh

## Escopo efetivamente implementado

A v4 continua a etapa 2 a partir dos fontes da v3, sem alterar o comportamento padrão quando `multiband.longitudinalRamanEnabled=false`.

1. **Raman contrapropagante:** `ClassicalPowerEvolution.backwardEffectiveLengthKm()` calcula a integral da potência da bomba clássica que caminha no sentido oposto ao QCh. A potência é referenciada à origem do enlace **reverso**. O sinal quântico segue somente atenuação, sem ganho de EDFA.
2. **Posições longitudinais absolutas:** um DCh reverso inicia no extremo físico oposto e pode atravessar atenuação/amplificadores antes de alcançar o `QKDSpan` analisado. O modelo anterior usava uma aproximação analítica por comprimento do span, sem esse percurso prévio.
3. **PICh com origem e fim reais:** a contribuição direta ou reversa só é integrada sobre a interseção entre o trecho físico do PICh e o `QKDSpan`. A conversão entre coordenadas direcionais é `z_forward = L - z_reverse`. É possível captar um PICh que cobre apenas parte do span observado; fontes sem interseção não contribuem.
4. **Preservação do modelo legado:** quando o perfil longitudinal está desabilitado, o cálculo de Raman mantém as fórmulas anteriores e o filtro pré-existente de PICh por identidade do span.
5. **Verificação automatizada:** a classe `QKDStages12ContinuationVerification` agora testa limites analíticos sem amplificadores, Raman intercore reverso, presença de ganho clássico, posicionamento dos nós confiáveis, fontes com sobreposição parcial, ausência de sobreposição e rejeição de geometria inválida.

## Habilitação

Os mesmos parâmetros da v3 habilitam também a integração no sentido contrapropagante (somente quando `bidirectionalFiber=true` e existe enlace reverso):

```json
{
  "longitudinalRamanEnabled": true,
  "amplifierSpacingKm": 80.0,
  "amplifierGainDb": 16.0
}
```

As chaves acima pertencem ao **objeto `multiband`** já existente de `qkdPhysicalLayer.json`, não substituem a configuração integral. Amplificadores ideais de ganho fixo são posicionados em múltiplos da distância configurada a partir da **origem de cada enlace direcionado**. Isso representa amplificação clássica, nunca amplificação de fótons quânticos.

## Limites e ressalvas

- A correção longitudinal de Raman vale somente para simulações que habilitem a opção. O modelo simplificado de saturação dos amplificadores clássicos da v3 **não** foi substituído por um modelo físico individual por EDFA.
- A NLI GN sobre o QCh ainda usa a hipótese anterior de potência e comprimento efetivos. Não corresponde a uma integral GN segmentada com ganho, nem considera *tilt*, ASE gerada por cada amplificador, NF, ISRS, gain flattening, dispersão dependente do comprimento de onda ou efeitos Raman estimulados.
- `QKDPhysicalLayer` considera fontes de Raman reverso apenas se `bidirectionalFiber=true` e a topologia possuir o enlace reverso. Ambos os sentidos precisam representar a **mesma fibra física** para essa hipótese ser válida.
- PIChs continuam com reserva espectral conservadora por enlace. A integração física de seu ruído por intervalo **não** implica que a reutilização espectral por trecho esteja habilitada. Também não modifica a reserva/rollback.
- A política de colocação de EDFAs periódicos é uma simplificação; o equipamento real pode estar em outras posições e possuir ganho espectral dependente da carga. O ganho de 16 dB é um exemplo, não um valor calibrado.
- Para resultados quantitativos publicáveis, validar o perfil e o Raman com medidas/dados de referência em múltiplos comprimentos de onda, comprimentos de span, potências e orientações de propagação.

## Validações executadas nesta entrega

Compilação **incremental** com `javac --release 8` contra classes históricas previamente compiladas; não houve execução de build Maven limpo. Testes executados: `QKDStages12ContinuationVerification`, `QKDStages12Verification`, `QKDPhysicalVerification`, `QKDCrosstalkPowerVerification`, `QKDReproducibilityVerification` e `MultiBandPhysicsVerification` (esta última mantém observações científicas já presentes na v3).

**Ainda pendente:** GN-NLI quântico com perfil longitudinal, amplificadores individuais com ganho dinâmico/NF e ASE física, teste de QoT/QKD ponta a ponta em topologias completas e reutilização espectral por subtrecho.
