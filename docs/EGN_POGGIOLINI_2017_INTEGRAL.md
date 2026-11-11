> **DOCUMENTO HISTORICO (ate 10/11/2026).** A referencia de integrais
> EGN Carena (`EgnFullReference`) e a classe
> `Poggiolini2017EgnReference` foram removidas da versao atual.
> O fallback agora calcula **GN** pela classe `Poggiolini2017GnIntegral`.
> **As informacoes de implementacao abaixo descrevem uma versao antiga.**
> Consulte `docs/INTEGRAIS_JOHANNISSON_HABIBI_SEM_EGN_FULL.md`.

# Modelo Habibi híbrido — vínculo explícito às integrais de Poggiolini e Jiang (2017)

## O que existia antes

A implementação anterior do híbrido **já realizava integração espectral numérica** das expressões EGN de **Carena et al. (2014)** (SCI, XCI e correções estatísticas até sexta ordem), conforme discutido na revisão de **Poggiolini e Jiang (2017)**. Não havia, entretanto, um componente separado que identificasse e verificasse numericamente a fórmula integral GN apresentada diretamente como Equação (3) no artigo de Poggiolini.

## O que foi acrescentado

- `network/Poggiolini2017EgnReference.java` (m�todo `evaluateGnIntegral`): implementa explicitamente a **dupla integral de frequência da Eq. (3)** de Poggiolini e Jiang (2017), com função de propagação e fator de interferência coerente entre spans da **Eq. (4)**, e integração sobre a banda do receptor da **Eq. (2)**. Cálculo GN; **não é EGN por si só**.
- `network/Poggiolini2017EgnReference.java`: calcula `EGN = GN − correção`, conforme a **Eq. (9)**. Os termos de correção dependentes dos momentos estatísticos de quarta e sexta ordem são obtidos por integração de **Carena et al. (2014)**, porque Poggiolini e Jiang **não reproduzem explicitamente as integrais completas de correção EGN** em seu artigo.
- `network/PhysicalLayer.java`: o fallback automático de Habibi usa a GN integral explícita da Equação (3) e as correções EGN integradas para SCI e XCI nos casos suportados; continua acionado quando a largura física do CUT é <= `habibiEgnNarrowbandGHz` ou o Habibi bruto é negativo.
- `network/PhysicalLayer.java`: o cálculo SCI-only usado na otimização de potência também chama a referência explícita Poggiolini + Carena.
- `network/Poggiolini2017EgnReference.java` (m�todo `main`): testes cruzados com o EGN integrado anterior, comparação da versão Habibi original, do híbrido e da referência completa e aviso para a Eq. (14).

## Por que NÃO substituímos o EGN por Eq. (14)

A Eq. (14) de Poggiolini e Jiang (2017) é uma **aproximação assintótica fechada para a correção EGN**, não uma integral rigorosa. O próprio artigo mostra que a aproximação é menos precisa nos primeiros spans. Para os parâmetros SMF QPSK de um span, o componente `Poggiolini2017EgnReference.asymptoticCorrectionEq14` demonstra que a subtração `GN(Eq.3) − correção(Eq.14)` pode fornecer NLI negativa em 12,5 GHz. Portanto, no fallback usamos **as integrais EGN completas da formulação de Carena**, em lugar da expressão assintótica.

## Domínio científico dos resultados

O fallback EGN completo permanece limitado a CUT e até dois interferentes retangulares Nyquist com mesma largura, sem sobreposição, fibra homogênea, spans idênticos e modulações cujos momentos completos foram identificados (QPSK, 16-QAM e 64-QAM). No híbrido de rede, a SCI e as XCI são substituídas individualmente; **a interação MCI entre vários interferentes não é inserida automaticamente**. O verificador completo EGN permite validar MCI somente na configuração de três canais simétricos.

Para modulações 8-QAM e 32-QAM, cuja parametrização completa de momentos de sexto grau não é fornecida pelo mapeamento atual, não se atribui um momento inventado. O fallback registrado como não suportado preserva a formulação de Habibi; seu truncamento negativo continua um limite conhecido, não uma equivalência numérica com o EGN rigoroso.

A integral GN da Eq. (3) pode ser utilizada separadamente em até 16 canais de larguras distintas para **verificação**, mas a correção EGN completa não é validada nesse domínio. Para cenários multibanda com PICh e amplificadores saturados, o híbrido mantém contribuições já existentes desses mecanismos e não afirma ser um modelo EGN longitudinal completo.

## Reproduzir

Windows 11: `scripts\\verify_habibi_egn.bat`.
Linux/macOS: `bash scripts/verify_habibi_egn.sh`.

A verificação numérica é executável com um JDK 8+, independentemente de Maven. A compilação limpa de toda a aplicação e a validação por SSFM/Manakov continuam pendentes.

Referências: P. Poggiolini e Y. Jiang, *JLT* 35(3), 2017, Eqs. (2)–(4), (9), (14), Seção III; A. Carena et al., *Optics Express*, 22, 2014; Jurandir Lacerda Júnior, tese UFMA/UFPI, 2024, Eqs. 3.21–3.31.
