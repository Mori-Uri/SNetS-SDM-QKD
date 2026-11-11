# Validação — Etapa 1 PICh físico / intrabanda

Data: 2026-11-03

## Verificações realizadas

1. `QKDTopologyPlanner.java`: comparação com a versão de entrada sem diferenças.
2. `QKDRoutePlanner.java`: comparação com a versão de entrada sem diferenças.
3. Portanto permanece a regra: um `QKDSpan` nunca atravessa um nó óptico SNetS.
4. Compilação das classes alteradas e suas dependências Java com:
   - `javac 21`, `-source 8 -target 8`;
   - encoding `ISO-8859-1`, compatível com arquivos legados multi-band da árvore;
   - stubs apenas para `Gson` e `OpenCSV`, pois Maven e os JARs externos não estão instalados no ambiente.
5. Resultado: **0 erros de compilação internos**, apenas warnings de `source/target 8` e operações unchecked já existentes.

## Limitação da validação

`mvn package` não pôde ser executado porque o comando Maven não está instalado neste ambiente. Nenhum stub foi incluído nos arquivos entregues; eles foram usados somente para validar a compilação local.
