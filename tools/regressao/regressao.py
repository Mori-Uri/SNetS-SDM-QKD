#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Teste de regressão do SNetS-SDM-QKD (golden master).

Garante que uma versão nova do simulador produz EXATAMENTE os mesmos resultados de uma versão de referência,
caso a caso, arquivo CSV a arquivo CSV. Serve para:

* provar que otimizações, save states e refatorações não mudaram nenhum resultado;
* provar que uma feature nova (ex.: multibanda) não altera os casos que não a usam;
* comparar o simulador QKD com o JAR clássico original (SNetS-SDM-SBRC26.jar) nos casos clássicos.

Cada caso em ``casos/`` é uma pasta de configuração do SNetS (network, simulation, traffic, physicalLayer, others,
qkdPhysicalLayer.json...) com semente fixa e tamanho reduzido. O script copia o caso para uma pasta temporária,
roda ``java -jar <jar> <caso>`` e calcula o SHA-256 de cada CSV gerado.

Uso (na pasta tools/regressao):

    python regressao.py gerar    --jar ../../simulations/SNetS-SDM-QKD.jar --referencia main
    python regressao.py comparar --jar ../../simulations/SNetS-SDM-QKD-exp.jar --referencia main
    python regressao.py determinismo --jar ../../simulations/SNetS-SDM-QKD.jar

Opções úteis: ``--casos "C0*"`` (filtro por nome), ``--paralelo 3`` (casos ao mesmo tempo), ``--java <caminho>``.

Arquivos:

* ``referencias/<nome>/manifesto.json``: SHA-256 de cada CSV de cada caso, JAR usado e data (vai para o git);
* ``referencias/<nome>/csv/<caso>/``: os CSVs de referência, para mostrar ONDE dois arquivos diferem (local).

Regras de comparação:

* um CSV da referência que falta na versão nova é FALHA;
* um CSV que só existe na versão nova (métrica nova) é informado como NOVO e não é falha;
* dois CSVs com hash diferente são EQUIVALENTES (não é falha) quando têm as mesmas linhas e:
  - os números diferem no máximo ``--tolerancia`` (relativa, padrão 1e-12): última casa decimal de somas em ponto
    flutuante feitas em outra ordem entre threads (ex.: ConsumedEnergy.csv); ou
  - as linhas estão em outra ordem (tabelas por par impressas a partir de um HashMap) ou um rótulo teve a grafia
    corrigida (lista ``RENOMEADOS``, ex.: "Numm Attempts" do SBRC26).
  Qualquer outra diferença é falha. ``--tolerancia 0`` desliga a tolerância numérica;
* um caso pode listar CSVs a ignorar em ``casos/<caso>/regressao.json`` (``{"ignorar": [...], "motivo": "..."}``),
  para métricas que já não se repetem no JAR da main; eles aparecem como IGNORADO;
* a opção ``--somente-comuns`` (padrão) compara só os arquivos presentes nas duas versões; útil contra o JAR
  clássico, que não gera os CSVs do addon QKD.

Só usa a biblioteca padrão do Python 3.8+.
"""
from __future__ import annotations

import argparse
import concurrent.futures as futures
import datetime as dt
import fnmatch
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

AQUI = Path(__file__).resolve().parent
CASOS = AQUI / "casos"
REFERENCIAS = AQUI / "referencias"


# ----------------------------------------------------------------------------------------------- execução


def sha256(caminho: Path) -> str:
    h = hashlib.sha256()
    with open(caminho, "rb") as f:
        for bloco in iter(lambda: f.read(1 << 20), b""):
            h.update(bloco)
    return h.hexdigest()


def listar_casos(filtro: str | None) -> list[str]:
    nomes = sorted(p.name for p in CASOS.iterdir() if p.is_dir() and (p / "simulation").exists())
    if filtro:
        nomes = [n for n in nomes if any(fnmatch.fnmatch(n, f.strip()) for f in filtro.split(","))]
    return nomes


def rodar_caso(nome: str, jar: Path, java: str, pasta_saida: Path, tempo_max: int) -> dict:
    """Roda um caso numa cópia temporária e devolve {arquivo: sha256}, o tempo e o log."""
    with tempfile.TemporaryDirectory(prefix="snets_reg_") as tmp:
        trabalho = Path(tmp) / nome
        shutil.copytree(CASOS / nome, trabalho)
        inicio = time.time()
        proc = subprocess.run([java, "-jar", str(jar), nome], cwd=tmp, capture_output=True, text=True,
                              timeout=tempo_max, encoding="utf-8", errors="replace")
        duracao = time.time() - inicio
        csvs = sorted(trabalho.glob(nome + "_*.csv"))
        destino = pasta_saida / nome
        if destino.exists():
            shutil.rmtree(destino)
        destino.mkdir(parents=True)
        resultado = {}
        for c in csvs:
            curto = c.name[len(nome) + 1:]          # sem o prefixo do caso
            shutil.copy2(c, destino / curto)
            resultado[curto] = sha256(c)
        (destino / "_execucao.log").write_text(proc.stdout[-20000:] + "\n--- stderr ---\n" + proc.stderr[-20000:],
                                               encoding="utf-8")
        return {"arquivos": resultado, "segundos": round(duracao, 1), "codigo": proc.returncode,
                "erro": "" if (proc.returncode == 0 and csvs) else (proc.stderr.strip().splitlines() or ["sem CSV"])[-1]}


def rodar_todos(nomes: list[str], jar: Path, java: str, pasta_saida: Path, paralelo: int, tempo_max: int) -> dict:
    res = {}
    with futures.ThreadPoolExecutor(max_workers=max(1, paralelo)) as ex:
        tarefas = {ex.submit(rodar_caso, n, jar, java, pasta_saida, tempo_max): n for n in nomes}
        for t in futures.as_completed(tarefas):
            n = tarefas[t]
            try:
                res[n] = t.result()
            except Exception as e:                   # timeout, java ausente...
                res[n] = {"arquivos": {}, "segundos": 0, "codigo": -1, "erro": str(e)}
            r = res[n]
            print(f"  {n:<36} {'ok' if not r['erro'] else 'ERRO: ' + r['erro'][:80]}  ({r['segundos']} s, "
                  f"{len(r['arquivos'])} CSV)", flush=True)
    return dict(sorted(res.items()))


# ----------------------------------------------------------------------------------------------- diferenças


# Rótulos corrigidos no simulador QKD em relação ao SNetS-SDM-SBRC26 (só grafia; o valor é o mesmo).
RENOMEADOS = {"Numm Attempts": "Num Attempts"}


def _linhas_iguais(x: str, y: str, tol: float) -> bool:
    if x == y:
        return True
    cx, cy = x.split(","), y.split(",")
    if len(cx) != len(cy):
        return False
    for u, v in zip(cx, cy):
        if u == v:
            continue
        try:
            fu, fv = float(u), float(v)
        except ValueError:
            return False
        if abs(fu - fv) > tol * max(abs(fu), abs(fv)):
            return False
    return True


def _normalizar(linhas: list[str]) -> list[str]:
    out = []
    for l in linhas:
        for antigo, novo in RENOMEADOS.items():
            l = l.replace(antigo, novo)
        out.append(l)
    return out


def equivalentes(a: Path, b: Path, tol: float) -> str | None:
    """Motivo da equivalência, ou None se os CSVs diferem de fato.

    Aceita, nesta ordem: (1) mesmas linhas com números iguais até ``tol`` (relativa); (2) as mesmas linhas em outra
    ordem (tabelas por par/enlace impressas a partir de um HashMap) e/ou com rótulos de ``RENOMEADOS`` corrigidos.
    """
    if not (a.exists() and b.exists()):
        return None
    la = a.read_text(encoding="utf-8", errors="replace").splitlines()
    lb = b.read_text(encoding="utf-8", errors="replace").splitlines()
    if len(la) != len(lb):
        return None
    if tol > 0 and all(_linhas_iguais(x, y, tol) for x, y in zip(la, lb)):
        return f"números iguais até {tol:g} relativa (arredondamento)"
    na, nb = _normalizar(la), _normalizar(lb)
    if na == nb:
        return "só rótulos corrigidos (" + ", ".join(RENOMEADOS) + ")"
    sa, sb = sorted(na), sorted(nb)
    if all(_linhas_iguais(x, y, tol) for x, y in zip(sa, sb)):
        return "mesmas linhas em outra ordem" + (" e rótulos corrigidos" if na != la else "")
    return None


def primeira_diferenca(a: Path, b: Path) -> str:
    """Primeira linha diferente entre dois CSVs, com o maior desvio numérico encontrado no arquivo."""
    la = a.read_text(encoding="utf-8", errors="replace").splitlines()
    lb = b.read_text(encoding="utf-8", errors="replace").splitlines()
    primeira, maior = None, 0.0
    for i, (x, y) in enumerate(zip(la, lb)):
        if x == y:
            continue
        if primeira is None:
            primeira = f"linha {i + 1}:\n        ref: {x[:160]}\n        nova: {y[:160]}"
        for u, v in zip(x.split(","), y.split(",")):
            try:
                fu, fv = float(u), float(v)
                maior = max(maior, abs(fu - fv) / max(abs(fu), abs(fv), 1e-300))
            except ValueError:
                pass
    if primeira is None:
        primeira = f"tamanhos diferentes ({len(la)} contra {len(lb)} linhas)"
    return primeira + f"\n        maior diferença relativa nos números: {maior:.3g}"


def comparar(ref: dict, nova: dict, ref_csv: Path, nova_csv: Path, somente_comuns: bool,
             tol: float = 1e-12) -> tuple[int, list[str]]:
    falhas, linhas = 0, []
    for caso in sorted(set(ref) | set(nova)):
        r, n = ref.get(caso), nova.get(caso)
        if r is None:
            linhas.append(f"[NOVO]  {caso}: caso sem referência")
            continue
        if n is None or n.get("erro"):
            falhas += 1
            linhas.append(f"[FALHA] {caso}: não rodou ({n.get('erro') if n else 'ausente'})")
            continue
        ra, na = r["arquivos"], n["arquivos"]
        iguais, equiv, dif = 0, 0, []
        ignorar, motivo = ignorados(caso)
        for arq in sorted(set(ra) | set(na)):
            if arq in ignorar:
                dif.append(f"    IGNORADO ({motivo}): {arq}")
                continue
            if arq not in na:
                if not somente_comuns:
                    dif.append(f"    ausente na versão nova: {arq}")
                continue
            if arq not in ra:
                dif.append(f"    NOVO (só na versão nova, não é falha): {arq}")
                continue
            if ra[arq] == na[arq]:
                iguais += 1
            elif (razao := equivalentes(ref_csv / caso / arq, nova_csv / caso / arq, tol)):
                equiv += 1
                dif.append(f"    EQUIVALENTE ({razao}): {arq}")
            else:
                dif.append(f"    DIFERENTE: {arq}\n      " + primeira_diferenca(ref_csv / caso / arq, nova_csv / caso / arq))
        problemas = [d for d in dif if "NOVO" not in d and "EQUIVALENTE" not in d and "IGNORADO" not in d]
        if problemas:
            falhas += 1
        linhas.append(f"[{'FALHA' if problemas else 'OK'}{']    ' if not problemas else '] '}{caso}: {iguais} CSV idênticos"
                      + (f", {equiv} equivalentes" if equiv else "")
                      + (f", {len(problemas)} com diferença" if problemas else ""))
        linhas.extend(dif)
    return falhas, linhas


def ignorados(caso: str) -> tuple[set, str]:
    """CSVs que o caso manda ignorar (casos/<caso>/regressao.json: {"ignorar": [...], "motivo": "..."})."""
    f = CASOS / caso / "regressao.json"
    if not f.exists():
        return set(), ""
    d = json.loads(f.read_text(encoding="utf-8"))
    return set(d.get("ignorar", [])), d.get("motivo", "não determinístico")


# ----------------------------------------------------------------------------------------------- comandos


def info_jar(jar: Path) -> dict:
    return {"jar": jar.name, "sha256": sha256(jar), "tamanho": jar.stat().st_size}


def cmd_gerar(a) -> int:
    jar = Path(a.jar).resolve()
    pasta = REFERENCIAS / a.referencia
    nomes = listar_casos(a.casos)
    print(f"Gerando a referência '{a.referencia}' com {jar.name} ({len(nomes)} casos)")
    res = rodar_todos(nomes, jar, a.java, pasta / "csv", a.paralelo, a.tempo_max)
    antigo = {}
    m = pasta / "manifesto.json"
    if m.exists() and a.casos:                     # gerar só alguns casos mantém os demais
        antigo = json.loads(m.read_text(encoding="utf-8")).get("casos", {})
    antigo.update(res)
    m.write_text(json.dumps({"gerado_em": dt.datetime.now().isoformat(timespec="seconds"), **info_jar(jar),
                             "casos": antigo}, indent=1, ensure_ascii=False), encoding="utf-8")
    erros = [n for n, r in res.items() if r["erro"]]
    print(f"Manifesto: {m}" + (f"\nCasos com erro: {', '.join(erros)}" if erros else ""))
    return 1 if erros else 0


def cmd_comparar(a) -> int:
    jar = Path(a.jar).resolve()
    pasta = REFERENCIAS / a.referencia
    manifesto = json.loads((pasta / "manifesto.json").read_text(encoding="utf-8"))
    nomes = [n for n in listar_casos(a.casos) if n in manifesto["casos"]]
    print(f"Comparando {jar.name} com a referência '{a.referencia}' ({manifesto['jar']}, {manifesto['gerado_em']}), "
          f"{len(nomes)} casos")
    saida = Path(tempfile.mkdtemp(prefix="snets_nova_"))
    nova = rodar_todos(nomes, jar, a.java, saida, a.paralelo, a.tempo_max)
    falhas, linhas = comparar({n: manifesto["casos"][n] for n in nomes}, nova, pasta / "csv", saida, a.somente_comuns,
                            a.tolerancia)
    print("\n".join(["", *linhas, ""]))
    print("RESULTADO: " + ("todos os casos idênticos (ou equivalentes) à referência" if falhas == 0 else f"{falhas} caso(s) com diferença"))
    print(f"CSVs da versão nova em: {saida}")
    return 1 if falhas else 0


def cmd_determinismo(a) -> int:
    jar = Path(a.jar).resolve()
    nomes = listar_casos(a.casos)
    print(f"Rodando cada caso duas vezes com {jar.name} ({len(nomes)} casos)")
    s1, s2 = Path(tempfile.mkdtemp(prefix="snets_d1_")), Path(tempfile.mkdtemp(prefix="snets_d2_"))
    r1 = rodar_todos(nomes, jar, a.java, s1, a.paralelo, a.tempo_max)
    r2 = rodar_todos(nomes, jar, a.java, s2, a.paralelo, a.tempo_max)
    falhas, linhas = comparar(r1, r2, s1, s2, False, a.tolerancia)
    print("\n".join(["", *linhas, ""]))
    print("RESULTADO: " + ("execuções repetidas idênticas" if falhas == 0 else f"{falhas} caso(s) não determinísticos"))
    return 1 if falhas else 0


def main() -> int:
    p = argparse.ArgumentParser(description="Teste de regressão do SNetS-SDM-QKD (resultados idênticos à referência)")
    sub = p.add_subparsers(dest="cmd", required=True)
    for nome, ajuda in [("gerar", "roda os casos e grava a referência"), ("comparar", "compara um JAR com a referência"),
                        ("determinismo", "roda duas vezes o mesmo JAR e compara")]:
        s = sub.add_parser(nome, help=ajuda)
        s.add_argument("--jar", required=True, help="JAR do simulador")
        if nome != "determinismo":
            s.add_argument("--referencia", default="main", help="nome da referência (pasta em referencias/)")
        s.add_argument("--casos", help='filtro por nome, ex.: "C0*,Q01*"')
        s.add_argument("--java", default=os.environ.get("SNETS_JAVA", "java"), help="executável java")
        s.add_argument("--paralelo", type=int, default=2, help="casos rodando ao mesmo tempo")
        s.add_argument("--tempo-max", type=int, default=1800, help="segundos por caso")
        s.add_argument("--somente-comuns", action=argparse.BooleanOptionalAction, default=True,
                       help="compara só os CSVs presentes nas duas versões (padrão)")
        if nome != "gerar":
            s.add_argument("--tolerancia", type=float, default=1e-12,
                       help="diferença relativa aceita nos números (padrão 1e-12; 0 = bytes idênticos)")
    a = p.parse_args()
    return {"gerar": cmd_gerar, "comparar": cmd_comparar, "determinismo": cmd_determinismo}[a.cmd](a)


if __name__ == "__main__":
    sys.exit(main())
