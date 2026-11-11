# Atualização científica — Habibi híbrido com integral de Poggiolini (2026-11-08)

## 1. Resposta à origem das equações

A versão anterior do híbrido **já utilizava integração espectral numérica**, mas a implementação estava estruturada a partir das integrais EGN de Carena et al. (2014), **não como avaliação explícita da Eq. (3)** de Poggiolini e Jiang (2017). Os resultados anteriores não eram apenas as fórmulas fechadas de Habibi.

O artigo de Poggiolini e Jiang (2017) apresenta (i) a integral GN de referência na Eq. (3), (ii) a função de propagação e interferência entre spans na Eq. (4), (iii) a integração da PSD no filtro receptor na Eq. (2), (iv) a estrutura `EGN = GN − Gcorr` na Eq. (9), e (v) uma **aproximação assintótica de correção** na Eq. (14). O artigo **não reproduz todas as integrais explícitas de correção do EGN**; remete à literatura de EGN completo, incluindo Carena et al. (2014).

Esta atualização faz a combinação apropriada, sem substituir a correção integral completa por uma aproximação assintótica inadequada para canais estreitos e um único span.

## 2. Arquivos e implementação

- `src/main/java/network/PoggioliniGnIntegral.java` — implementação explícita de Eq. (3) GN, Eq. (4) fator de enlace, Eq. (2) média no filtro receptor, por quadratura Gauss-Legendre; fornece SCI, XCI e MCI **GN**, sem afirmar que GN é EGN.
- `src/main/java/network/Poggiolini2017EgnReference.java` — combina a GN integral com os termos de correção **integrais completos de Carena** (quarta/sexta ordem), conforme a Eq. (9) de Poggiolini; inclui a Eq. (14) apenas para diagnóstico, não para fallback.
- `src/main/java/network/HabibiEgnHybrid.java` — o fallback para canais estreitos ou resultado negativo passou a chamar a integração GN explícita de Poggiolini e as correções EGN de Carena, para SCI e XCI suportadas, preservando cache e comportamento antigo quando o fallback é desligado.
- `src/main/java/network/PhysicalLayer.java` — no cálculo de NLI da conexão e no caminho SCI isolado, a referência da correção híbrida passa a ser a nova combinação.
- `src/main/java/network/Poggiolini2017HybridVerification.java` — testes numéricos independentes, cruzamento do GN Eq. (3) com o GN da referência antiga, invariância do modelo legado e verificação da Eq. (14).
- `scripts/verify_habibi_egn.sh` e `.bat` — passam a executar os verificadores antigo e novo.
- `launcher/` — textos corrigidos para distinguir integral GN de Poggiolini, correções integrais de Carena e aproximação assintótica Eq. (14); Launcher **1.23.1** recompilado.

Não houve alteração dos nomes ou dos valores-padrão de campos JSON: `habibiEgnFallbackEnabled` permanece `true` e `habibiEgnNarrowbandGHz` permanece **25 GHz de largura física efetiva**. A configuração `false` ainda reproduz o Habibi legado, sem fallback.

## 3. Comparação — QPSK, 1 canal, SMF de 100 km, 1 span

Parâmetros de teste: atenuação 0,2 dB/km; não linearidade γ=1,3 (W km)^-1; β₂=−21,3 ps²/km; potência=1 mW; retangular Nyquist; quadratura de ordem 20 e receptor 8.

| Largura efetiva | Habibi bruto (W/Hz) | Habibi híbrido novo (W/Hz) | EGN integral Carena (W/Hz) | Fallback usado? |
| --- | ---: | ---: | ---: | --- |
| 12,5 GHz | −3,05248×10⁻¹⁷ | 9,46961×10⁻¹⁸ | 9,46961×10⁻¹⁸ | Sim, negativo e estreito |
| 25 GHz | 4,37762×10⁻¹⁸ | 3,56633×10⁻¹⁸ | 3,56633×10⁻¹⁸ | Sim, estreito |
| 32 GHz | 4,18566×10⁻¹⁸ | 4,18566×10⁻¹⁸ | 2,31829×10⁻¹⁸ | Não |
| 50 GHz | 2,18946×10⁻¹⁸ | 2,18946×10⁻¹⁸ | 9,98938×10⁻¹⁹ | Não |

O valor bruto negativo em 12,5 GHz era truncado a zero no Habibi sem correção. Na referência com correções EGN integrais ele é positivo. O novo resultado híbrido coincide com a referência anterior de Carena no domínio suportado, como esperado: trata-se de uma **verificação explícita da equivalência do núcleo GN integral**, não de uma nova equação física que necessariamente mude resultados já corretos.

Para o mesmo cenário em 12,5 GHz, a correção assintótica Eq. (14) de Poggiolini resulta em cerca de `5,90216×10⁻¹⁷ W/Hz`, maior que a integral GN de cerca de `2,42×10⁻¹⁷ W/Hz`. Usá-la isoladamente produziria novamente EGN negativa, portanto é inadequado utilizá-la como substituição irrestrita do EGN integral no caso de um span.

## 4. Testes executados

- **25 comparações antigas** Habibi, híbrido e Carena (1–3 canais e diferentes formatos), passaram.
- **15 novas comparações** da integral GN Eq. (3) com o GN da referência Carena, SCI/XCI/MCI e a estrutura Eq. (9), passaram (diferença numérica relativa inferior a `1×10⁻¹¹` nos cenários avaliados).
- Testes específicos dos caminhos de `PhysicalLayer` para QPSK 12,5 GHz: passaram; Habibi original produz zero após truncamento, híbrido produz `9,469613×10⁻¹⁸ W/Hz`.
- Testes de regressão `QKDThesisSNetSPhysicalLayerVerification`, `QKDLongitudinalGNVerification`, `QKDBoosterIntegratedVerification` e `QKDv25SharedNoiseFactorVerification`: passaram.
- Compilação dos arquivos Java novos e dos caminhos `PhysicalLayer`/`LongitudinalClassicalGN` em compatibilidade Java 8: passou, reutilizando dependências pré-compiladas.
- Teste de contrato da interface e compilação/partida do Launcher 1.23.1: passaram.

**Limitações:** não foi realizada compilação Maven limpa completa, regressão de simulação de rede com tráfego dinâmico nem validação experimental/SSFM. O EGN integral de Carena continua restrito a até 3 canais Nyquist com mesmo baudrate e hipótese homogênea (tripla simétrica para MCI). No fluxo híbrido de muitas conexões, são substituídas SCI e XCI por fonte, não todas as MCI cruzadas. As modulações 8/32-QAM permanecem fora do fallback EGN completo devido aos momentos estatísticos não mapeados na implementação. As correções do PICh permanecem oriundas do tratamento físico existente, não são automaticamente convertidas ao EGN integral. Não extrapolar as comparações acima para cenários fora dessas hipóteses.

## 5. Como reproduzir

No Windows 11, a partir da raiz do SNetS: `scripts\verify_habibi_egn.bat`.
Em shell POSIX: `bash scripts/verify_habibi_egn.sh`.
O segundo verificador gera `poggiolini2017_comparison.csv` e registra SCI/XCI/MCI, Habibi bruto, Habibi corrigido, EGN integral de referência e a correção Eq. (14).

## Referências

- Poggiolini, P.; Jiang, Y. (2017). *Recent Advances in the Modeling of the Impact of Nonlinear Fiber Propagation Effects on Uncompensated Coherent Transmission Systems*. Journal of Lightwave Technology 35(3), 458–480. Eqs. (2)–(4), (9), (14), Seção III.
- Carena et al. (2014). *EGN Model of Non-linear Fiber Propagation*. Optics Express 22.
- Lacerda Júnior, J. C. (2024). Tese, UFMA/UFPI, Seção 3.1.2, Eqs. 3.21–3.31.
