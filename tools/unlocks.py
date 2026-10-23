"""Edita a linha de unlocks (3391526480) do save de carreira.

Regra descoberta em 23/10/2026: PRESENCA = desbloqueado. O valor do par
(id, valor) nao controla nada visivel; anexar o id libera o item e remove-lo
bloqueia de novo. Por isso: so anexar, nunca substituir.

IDs:
  - performance / visual / body: literais uint (tools/unlock_ids.json).
  - carros: Bin_Hash do nome interno (tambem no json).
  - NAO anexar carro na mao: o jogo faz lookup e crasha
    ("Call to invalid or unregistered function at guest address 0x0").

O bloco de carreira tem 32768 B cheios. Crescer e permitido: o dec do
container passa a ser o tamanho exato do texto (provado in-game com 33268 B),
o campo +0x08 vira 0x18+len(comprimido) e o arquivo so cresce se o payload
nao couber (ha ~58 KB de zeros depois do ultimo container).

Uso:
  python3 tools/unlocks.py SAVE.device --list
  python3 tools/unlocks.py SAVE.device OUT.device --add performance visual
"""

import json
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import jdlz_opt
import savefmt as F
import setcash as S

ROTULO = b'3391526480:'
LISTAS = json.load(open(os.path.join(AQUI, 'unlock_ids.json')))


def abrir(caminho):
    d = bytearray(open(caminho, 'rb').read())
    off, dec, clen = list(S.walk(bytes(d), S.be32(d, 0x20) + 0x2C))[2][:3]
    texto = S.jdlz_decompress(bytes(d[off + 0x18:off + clen]))
    return d, off, clen, texto


def tokens(texto):
    for linha in texto.split(b'\n'):
        if linha.startswith(ROTULO):
            return linha.split(b':', 1)[1].split(b'\t')
    raise SystemExit('linha de unlocks nao encontrada')


def ids(texto):
    t = tokens(texto)
    return [t[i].decode() for i in range(5, len(t) - 1, 2)]


def categorias(nomes):
    novos = []
    for nome in nomes:
        if nome not in LISTAS or not isinstance(LISTAS[nome], list):
            raise SystemExit('categoria invalida: %s (use %s)' % (
                nome, ', '.join(k for k, v in LISTAS.items() if isinstance(v, list))))
        novos += [str(x) for x in LISTAS[nome]]
    return novos


def gravar(d, off, clen, texto, saida):
    comp = jdlz_opt.emit(jdlz_opt.jdlz_compress_opt(texto), texto)
    payload = clen - 0x18
    if len(comp) > payload:
        d += b'\x00' * (len(comp) - payload)
        payload = len(comp)
        F.put32(d, 0x04, len(d))
        F.put32(d, 0x0C, len(d) - 0x2C)
    d[off + 0x18:off + 0x18 + payload] = comp + b'\x00' * (payload - len(comp))
    F.put32(d, off + 0x04, len(texto))
    F.put32(d, off + 0x08, 0x18 + len(comp))
    F.update_profile_md5(d)
    F.update_hashes(d)
    open(saida, 'wb').write(bytes(d))
    return len(comp)


def adicionar(caminho, saida, nomes):
    d, off, clen, texto = abrir(caminho)
    t = tokens(texto)
    existentes = {t[i].decode() for i in range(5, len(t) - 1, 2)}
    novos = [x for x in categorias(nomes) if x not in existentes]
    vistos = set()
    unicos = []
    for x in novos:
        if x not in vistos:
            vistos.add(x)
            unicos.append(x)
    linha = ROTULO + b'\t' + b'\t'.join(t)
    for x in unicos:
        linha += b'\t' + x.encode() + b'\t1'
    t2 = linha.split(b':', 1)[1].split(b'\t')
    t2[1] = str((len(t2) - 5) // 2).encode()
    nova = ROTULO + b'\t' + b'\t'.join(t2)
    texto = texto.replace(ROTULO + b'\t' + b'\t'.join(t), nova, 1)
    comp = gravar(d, off, clen, texto, saida)
    return len(unicos), (len(t2) - 5) // 2, len(texto), comp


def main(argv):
    if len(argv) < 3:
        raise SystemExit(__doc__)
    if argv[2] == '--list':
        _, _, _, texto = abrir(argv[1])
        lista = ids(texto)
        print('pares: %d' % len(lista))
        print('primeiros: %s' % ', '.join(lista[:8]))
        return
    if len(argv) < 5 or argv[3] != '--add':
        raise SystemExit(__doc__)
    n, total, tam, comp = adicionar(argv[1], argv[2], argv[4:])
    print('adicionados: %d | pares: %d | texto: %d B | comprimido: %d B' % (
        n, total, tam, comp))


if __name__ == '__main__':
    main(sys.argv)
