# Seleção vigente dos métodos GN/NLI

Após a remoção dos seletores legados, o arquivo physicalLayer expõe somente:

```json
{
  "johannissonNliMode": "adaptive",
  "habibiNliMode": "adaptive",
  "nliNarrowbandGHz": 25.0
}
```

`approximate`: aproximações analíticas, truncando valores não físicos sem integrar.
`integral`: Johannisson SCI/XCI numéricas ou GN integral de Poggiolini para as contribuições elegíveis de Habibi (não EGN integral).
`adaptive`: integral quando o kernel é inválido ou a largura física do sinal é menor ou igual à largura configurada.

Ausência dos campos significa `adaptive` e 25 GHz. Chaves obsoletas são rejeitadas na leitura do arquivo pela classe `SimulationFileManager`, e o Launcher as sinaliza. Para converter uma simulação antiga, defina explicitamente os modos pretendidos. Arquivos históricos de documentação permanecem disponíveis como registro, mas não definem a configuração vigente.
