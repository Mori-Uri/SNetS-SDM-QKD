# Correção de BER clássica (SNetS v1, Launcher 1.24.4)

**Fonte científica:** Tese de Jurandir C. Lacerda Júnior, seção 3, pp. 47–50 impressas (páginas 48–51 do PDF).

## Problema identificado

Os campos legados `network.modulations[].SNR` e métodos `getSNRthreshold()` / `getSNRthresholdLinear()` representam na realidade **limiares de OSNR óptica**, e não Eb/N0. O caminho `computeSNRSegment()` também retorna uma razão óptica de densidades espectrais de potência sinal/ruído. Na implementação anterior, `PhysicalLayer.getBER(double SNR,double M)` interpretava a mesma razão como **SNR por símbolo**; `isAdmissible2()` acrescentava um escalonamento em 12,5 GHz sem consistência com a razão PSD já calculada.

A correção mantém os nomes de métodos antigos por compatibilidade binária com os consumidores Java e preserva todos os campos JSON existentes; adiciona apenas aliases explícitos `getOSNRthreshold*()` e a opção `berModel`.

## Método correto (modelo padrão: `jurandir`)

- Eq. (3.8): `OSNR_linear = Rb/(2*Rs) * SNR_bit_linear`.
- Eq. (3.9): `Rb = p*log2(M)*Rs`, com `p` a quantidade de polarizações.
- Eq. (3.10) para `p=2`: `SNR_bit = OSNR_linear/log2(M)`; com `p=1`: `SNR_bit=2*OSNR_linear/log2(M)`.
- Eq. (3.4), M=4/16/64/... quadrada: `BER = 2/log2(M) * (sqrt(M)-1)/sqrt(M) * erfc(sqrt(3*log2(M)*SNR_bit/[2*(M-1)]))`.
- Eq. (3.6), 8-QAM: `BER = 11/16 * erfc(sqrt(3*SNR_bit/(3+sqrt(3))))`.
- Eq. (3.7), 32-QAM: `BER = 91/240 * erfc(sqrt(SNR_bit/4))`.
- BPSK (M=2): preservada aproximação 0,5*erfc(sqrt(SNR_bit)), que **não pertence** ao conjunto das equações QAM da tese.
- Outros valores M com `log2(M)` ímpar e sem expressão na tese (ex.: 128-QAM): rejeitados com NaN em vez de pressupor uma constelação não especificada.

O cálculo usa uma aproximação AWGN pré-FEC; não reproduz FEC, DSP, transceptores experimentais nem uma simulação probabilística completa de bits. Como a tese define OSNR via PSD (Eq. 3.15), a razão sinal/ruído fornecida pelo SNetS é usada diretamente, sem conversão adicional de banda de referência. Esta convenção depende das hipóteses físicas da tese (em especial `Btot=Rs`) e requer validação adicional se essas hipóteses mudarem.

## Configuração

No arquivo `physicalLayer`, `{ "typeOfTestQoT": 1, "berModel": "jurandir", "berThreshold": 0.001 }` habilita BER pré-FEC de 1e-3 com as equações da tese.

`berModel` **ausente** equivale a `jurandir` (padrão). O valor `legacyRectangularAwgn` reativa a fórmula anterior de QAM retangular, preservando a possibilidade de reproduzir campanhas já realizadas.

`typeOfTestQoT=0` mantém a comparação direta `OSNR_calc >= network.modulations[].SNR` (em unidades lineares).

`typeOfTestQoT=1` com **`berThreshold` ausente** deriva `BER_limite = BER(OSNR_limiar_da_modulação)`; com BER monotônica, a decisão de admissão equivale à comparação por OSNR. A fórmula por BER só cria uma exigência nova quando há um limiar de BER explicitamente configurado.

A Eq. (3.12) (1 dB de margem operacional + 1 dB de penalidade de XT nos cenários numéricos da tese) foi usada **apenas para reproduzir as Tabelas 3/4 no teste**, não é somada automaticamente aos limiares da v1: o JSON pode já incorporar margens e o XT pode estar integrado ao denominador de ruído (`XTModel=1`), gerando risco de dupla penalização.

O método auxiliar `isAdmissible2(bitRate,modulation,OSNR)` preserva a assinatura e valida `bitRate>0`, mas não escala novamente a OSNR.

## Validação

Executar após recompilar o JAR da v1:

```powershell
cd scripts
.\verificar_qot_ber.bat
```

Verificadores: `ClassicalBerQotVerification` (admissão, polarização, regressão do modelo legado) e `JurandirBerThesisVerification` (20 valores da Tabela 2, 20 da Tabela 3, 5 da Tabela 4). A comparação de OSNR e BER utiliza apenas valores analíticos AWGN, não uma campanha de roteamento/QKD.

**Impacto científico:** resultados de QoT no modo BER com limiar explícito, sobretudo 8-QAM e 32-QAM, podem diferir da versão 1.24.3. Segmentar as campanhas por `berModel`, referência de BER e modo de teste é obrigatório para comparações válidas. Modos QoT=0 não foram modificados.
