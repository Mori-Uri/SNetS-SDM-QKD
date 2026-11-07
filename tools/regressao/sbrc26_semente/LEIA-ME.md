# SBRC26 com semente fixa (só para teste)

O `SNetS-SDM-SBRC26.jar` original sorteia a semente do tráfego pelo relógio (`new RandGenerator()` em
`network/Mesh.java`), então duas execuções do mesmo caso dão resultados diferentes. Para comparar o SNetS-SDM-QKD com
ele byte a byte, o teste usa uma cópia do SBRC26 com **uma única mudança**: a semente lida de `trafficRandomSeed` no
arquivo `simulation`, com o mesmo deslocamento do SNetS-SDM-QKD (semente + 100000 × carga + replicação).
Sem `trafficRandomSeed` no caso, o comportamento é o original.

`sbrc26_semente.patch` é o diff dessa mudança (`Mesh.java` e `SimulationManagement.java` do SBRC26). Este JAR de
teste **não** é versionado e não deve ser usado em estudos.

Para refazer (Java 17, na pasta do SBRC26 original):

```bat
:: aplicar o patch numa cópia de src/main/java, compilar só os dois arquivos contra o JAR original
javac -encoding ISO-8859-1 --release 17 -cp SNetS-SDM-SBRC26.jar -d cls network\Mesh.java simulationControl\SimulationManagement.java
copy SNetS-SDM-SBRC26.jar SNetS-SDM-SBRC26-twin.jar
cd cls && jar uf ..\SNetS-SDM-SBRC26-twin.jar .
```
