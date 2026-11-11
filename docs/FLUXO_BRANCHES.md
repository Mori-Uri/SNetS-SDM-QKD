# Fluxo de trabalho: main × experimental

## Duas pastas, dois launchers

| Pasta (em `Documentos\Simulador`) | Branch | JAR do simulador | Launcher |
|---|---|---|---|
| `SNetS-SDM-QKD` | `main` | `simulations\SNetS-SDM-QKD.jar` | `launcher\SNetS-Launcher.bat`, http://localhost:8765 |
| `SNetS-SDM-QKD_ex` | `experimental` | `simulations\SNetS-SDM-QKD-exp.jar` | `launcher\SNetS-Launcher.bat`, http://localhost:8766, selo **EXPERIMENTAL** |

- Cada pasta é um repositório git completo, ligado ao mesmo GitHub (`Mori-Uri/SNetS-SDM-QKD`).
- No GitHub Desktop as duas aparecem como repositórios separados. Deixe a primeira sempre em `main` e a segunda sempre em
  `experimental`, e evite trocar de branch dentro delas.
- Os dois launchers podem ficar abertos juntos. Cada um usa a própria pasta `simulations`, o próprio JAR e a própria
  porta.
- A pasta `simulations` de cada uma é local (fora do git, exceto os exemplos). Estudos da main ficam na pasta da main;
  estudos multibanda ficam na `_ex`.

## Sentido das mudanças

```
main ──────●──────────●──────────●──────►   (versão estável: estudos da dissertação)
            \          \ merge    \ merge
experimental ●───●──────●───●──────●──────►   (multibanda, save states, otimização, ...)
```

- **Mudança na main** (correção, recurso pedido para os estudos atuais):
  1. é feita e commitada na pasta `SNetS-SDM-QKD` (branch `main`);
  2. depois é levada para a experimental por **merge da main na experimental**.
- **Mudança na experimental** fica só na experimental até ela estar madura. Nada da experimental vai para a main antes
  disso.
- **No fim**, um único merge da `experimental` na `main` leva tudo. Como a experimental já contém a main, o merge final
  quase não tem conflitos.

## Como levar a main para a experimental (GitHub Desktop)

1. Na pasta da main, faça o commit e o **Push origin** da mudança.
2. Troque o repositório para `SNetS-SDM-QKD_ex` (branch `experimental`) e clique em **Fetch origin**.
3. Vá em **Branch → Merge into current branch…**, escolha **`origin/main`** e clique em **Create a merge commit**.
4. Se o Desktop mostrar conflitos, pare e me avise com a lista de arquivos; eu resolvo a adaptação.
5. Depois do merge, gere o JAR experimental de novo (launcher da `_ex` → **Gerar JAR**, saída
   `SNetS-SDM-QKD-exp.jar`) e rode o teste de regressão:
   - `tools\regressao`: `python regressao.py comparar --jar ..\..\simulations\SNetS-SDM-QKD-exp.jar --referencia main --casos "C*,Q*"`;
   - e `--referencia experimental --casos "M*"`.
6. Clique em **Push origin**.

Quando a mudança na main altera resultados de propósito, a referência `main` do teste de regressão é refeita com o JAR
novo da main (`python regressao.py gerar --jar <JAR da main> --referencia main`).

## Arquivos que só existem na experimental

Eles não devem ser copiados para a main antes do merge final:

- `src/main/java/qkd/multiband/`;
- `MultiBand*Verification`;
- `tools/regressao/`;
- `docs/multibanda/`;
- `simulations/QKD_Multibanda/`;
- `simulations/SNetS-SDM-QKD-exp.jar`;
- `launcher/src/launcher/Edition.java`.

No merge final, troque em `Edition.java` o nome (`""`), a porta (8765) e o JAR (`SNetS-SDM-QKD.jar`), ou apague o
arquivo e volte os três usos em `LauncherServer.java`.
