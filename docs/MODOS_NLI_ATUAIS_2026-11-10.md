# Modos de NLI — configuração única (10/11/2026)

As quatro opções booleanas legadas foram eliminadas da configuração do SNetS: `johannissonSciUseIntegral`, `johannissonXciUseIntegral`, `habibiEgnFallbackEnabled` e `habibiEgnNarrowbandGHz`. O alias `johannissonUseIntegral` também não é interpretado como parâmetro.

Os parâmetros atuais são `johannissonNliMode`, `habibiNliMode` (cada um aceita `approximate`, `integral` ou `adaptive`) e `nliNarrowbandGHz` (largura física do sinal em GHz). Em arquivos sem os campos atuais os modos assumem `adaptive` e a largura é 25.0 GHz. Não há conversão automática dos booleans antigos, para evitar interpretações silenciosas; migrar deliberadamente as campanhas antigas antes de reexecutar.

O modo integral de Habibi usa GN de Poggiolini para SCI/XCI elegíveis; não é a integral EGN completa. Os utilitários legados das fórmulas GN de baixo nível foram mantidos para testes científicos, mas não são parâmetros configuráveis da simulação.

A classe experimental `TrackedRequestList` não foi incorporada à v1: ela exige mudanças em `Circuit` e `RequestForConnection` e não pode ser compilada isoladamente; o script de limpeza realiza backup e remove apenas se não houver consumidores Java fora dela.
