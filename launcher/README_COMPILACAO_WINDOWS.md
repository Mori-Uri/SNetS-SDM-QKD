# Compilacao do SNetS Launcher no Windows sem jar.exe

## Causa do erro anterior

O script anterior procurava `javac.exe` **e** `jar.exe` no mesmo JDK.
Se `javac.exe` estava acessivel mas `jar.exe` nao, ele interrompia a compilacao
antes de tentar executar qualquer comando. Isso nao prova, por si so, que o JDK esteja ausente.

## Novo procedimento

O novo `build-launcher.bat` requer somente `javac.exe` e `java.exe`.
Ele compila para Java 8 e executa a classe `launcher.LauncherArchiveBuilder`,
que gera um JAR com `JarOutputStream`, valida o manifesto, verifica classes e
recursos (web e templates) e evita depender de `jar.exe`, Maven ou PowerShell.

1. Copie **ambos** `launcher/build-launcher.bat` e
   `launcher/src/launcher/LauncherArchiveBuilder.java` para o projeto.
2. Execute no `cmd.exe`:

   ```bat
   where java
   where javac
   echo %JAVA_HOME%
   cd /d C:\caminho\para\SNetS-SDM-qkd-v1\launcher
   build-launcher.bat
   ```

3. Opcionalmente, valide o JAR com:

   ```bat
   java -jar snets-launcher.jar --help
   ```

### Se javac.exe nao estiver instalado

E preciso um JDK, nao apenas um JRE. Configure a raiz do JDK real no seu computador
(ajuste o caminho no exemplo abaixo):

```bat
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
where javac
where java
```

O script usa `--release 8` com JDK 9+ e `-source/-target 8` com JDK 8.
Avisos sobre APIs depreciadas nao impedem a geracao do JAR.

O JAR anterior e salvo em `snets-launcher.jar.bak` antes da troca.

## Verificacoes efetuadas pelo assistente

O compilador foi testado em JDK 21 com `--release 8`. O JAR foi
**gerado sem chamar jar.exe**, verificado como arquivo ZIP/JAR e executado
com `java -jar ... --help`. A execucao deste BAT em Windows ainda precisa
ser confirmada no computador do usuario.


## Verificacoes QKD no Launcher 1.23.2

O comando `Gerar JAR` compila normalmente o simulador. A opcao `--verify` executa
os verificadores incluidos no JAR e considera um teste aprovado **somente se o
processo Java terminar com codigo de saida zero**. Nao depende mais da frase
`ALL CHECKS PASSED` (alguns testes imprimem apenas `PASS`).

Os logs completos e `resumo.csv` sao salvos em:

`simulations/verification-reports/AAAAMMDD_HHMMSS_mmm/`

Em um CMD na raiz do projeto, para repetir apenas um teste, sem recompilar:

```bat
"C:\Program Files\Java\jdk-23\bin\java.exe" -cp "launcher\snets-launcher.jar" launcher.JarBuilder --verify-jar "simulations\SNetS-SDM-QKD-exp.jar" --verify-only QKDSpanGranularityVerification
```

O argumento `--verify-only` aceita nomes separados por virgula. A opcao
`--verify-timeout-seconds 1800` controla o tempo maximo de cada teste.

O arquivo `scripts\retestar_qkd_verificacoes_criticas.bat` executa os tres
testes de alocacao mais relevantes deste diagnostico; adicione `full` ao comando
para executar tambem o teste extenso de artigo-base (`QKDNativeArticleBaseVerification`).

A falha de um teste de regressao (exit code 2) **nao** equivale a erro de
compilacao. Um novo JAR pode ter sido gerado, mas ainda nao esta certificado
para producao de resultados cientificos ate que os testes reprovados sejam analisados.
