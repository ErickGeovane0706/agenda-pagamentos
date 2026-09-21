# 0010 — O nginx re-resolve o backend a cada requisição

## Contexto

O frontend é servido por nginx, que faz proxy de `/api` para o backend pela
rede privada da hospedagem. O IP do backend **muda a cada deploy dele**.

O nginx resolve nome de host uma única vez, no boot, quando o destino está
escrito literalmente no `proxy_pass`. Consequência: todo deploy do backend
deixava o `/api` respondendo `504` até alguém reiniciar o frontend à mão.

Esse é o tipo de falha que não parece falha de infraestrutura. O site abre, o
login some, e o diagnóstico vai para o lugar errado.

## Decisão

O destino do `proxy_pass` passa por uma **variável**. É a variável — e não a
diretiva `resolver` — que força nova resolução a cada requisição; a
`resolver` sozinha não muda nada, ela apenas passa a ser usada porque existe
uma variável para resolver.

O DNS interno apontado é o da rede da hospedagem, e não o `127.0.0.11` do
Docker, que não existe lá.

## Consequência

Deploy de backend deixou de exigir restart do frontend. Medido: redeploy do
backend com o `/api` de pé o tempo todo.

O custo é uma resolução de DNS por requisição, com TTL curto — desprezível
perto de um proxy quebrado depois de cada deploy.

O episódio tem um segundo ensinamento, fora da arquitetura: enquanto o `/api`
esteve em `504`, uma campanha de anúncios continuou comprando cliques para um
site que não conseguia cadastrar ninguém. Indisponibilidade parcial custa
dinheiro em lugares que não aparecem no log do servidor.
