# 0011 — O Excel do relatório e as armadilhas que ele carrega

## Contexto

O cliente não sabia de qual loja era cada boleto no Excel. O arquivo foi refeito
num modelo aprovado com ele: uma tabela com a coluna Loja, cartões no topo e três
gráficos à direita, tudo seguindo o filtro que ele aplica no próprio Excel.

Cada detalhe abaixo já quebrou uma vez, e nenhum deles dá erro: o arquivo abre,
só que errado. É o tipo de coisa que uma "limpeza" desfaz sem perceber.

## Decisão

1. **Gráfico com âncora absoluta** (`DONT_MOVE_AND_RESIZE`, `editAs="absolute"`
   no XML). Preso às células, o gráfico encolhe quando o filtro esconde as linhas
   que ele cobre: filtrando uma loja com duas linhas, os gráficos ficavam
   achatados. O POI não relê esse atributo ao abrir o arquivo, então o teste
   confere no XML.

2. **Gráficos começam abaixo das linhas fixas** (linha 10 em diante). Começando
   dentro da área congelada, o gráfico "escorrega" por baixo dela ao rolar.

3. **`overlay = false` explícito** no título, na legenda e no título do eixo. Sem
   ele, o Excel desenha os nomes por cima das barras.

4. **Cartões e gráficos somam só as linhas visíveis**:
   `SUMPRODUCT(SUBTOTAL(109, OFFSET(...)) * (condição))`. `SUMIFS` ignora o
   filtro. Os dados dos gráficos ficam numa aba oculta, e por isso o gráfico
   precisa de `plotVisOnly = false`.

5. **Toda fórmula vai com o valor já calculado.** A pré-visualização do iPhone e
   do WhatsApp não recalcula: sem o valor gravado, mostra zero. Os gráficos
   também levam os valores em cache.

6. **Furo da rosca, espaço e sobreposição das barras são gravados direto no XML**
   (`valorCru`). Os setters do POI usam tipos de porcentagem que o
   `poi-ooxml-lite` não traz, e quebram em execução, não na compilação.

7. **Cancelado não entra em nenhum total**, igual à tela (ver o teste de
   integração que exige topo = soma das lojas). Fica num bloco à parte.

8. **Nenhuma letra de coluna escrita à mão.** As colunas Nº documento e
   NF/Pedido só entram quando algum boleto do arquivo tem um deles, e aí
   Situação e Valor andam duas casas. Cartões, total, filtro, dados dos gráficos
   e posição dos gráficos pegam a coluna do `record Colunas`. Um `"$F$"` fixo
   passa nos testes sem documento e zera os cartões com documento. A coluna
   Banco segue a mesma regra (aparece quando algum cheque tem banco).

9. **Banco do cheque por `LEFT JOIN`.** O banco é opcional no cheque;
   `c.banco.nome` direto na consulta vira join comum e os cheques sem banco
   somem do Excel sem erro. E as colunas extras de cada tipo (documento no
   boleto, banco no cheque) chegam nas mesmas posições: são lidas pelo tipo,
   nunca pela posição.

## Consequência

Testes que seguram cada item, em `RelatorioExcelServiceTest`:

| Item | Teste |
|---|---|
| 1, 2, 3, 4 (aba oculta) | `graficos_abaixoDaLinhaFixa_semTextoPorCimaDasBarras_eDadosNumaAbaOculta` |
| 4, 5 | `cartoesETotal_somamSoAsLinhasVisiveis_eBatemComOsValores`, `dadosDosGraficos_porLojaEPorSemana_batemComOsLancamentos` |
| 7 | `tabela_temLoja_...`, `cancelados_ficamNumBlocoAParte_...` |
| 8 | `comDocumentoOuNotaFiscal_ganhaDuasColunas_eAsFormulasAcompanham`, `documentoEBanco_juntos_naOrdemCerta` |
| 9 | `RelatorioPorLojaIntegrationTest.excel_chequeTrazBancoAgenciaEConta_eChequeSemBancoNaoSome`, `chequeComBanco_ganhaColunaBanco_numaLinhaSoEmDoisTons` |

O POI não calcula `SUBTOTAL(OFFSET(...))` dentro de `SUMPRODUCT`. O teste confere
o valor gravado e calcula a mesma fórmula sem a parte das linhas visíveis, como
`SUM` matricial. Isso prova as faixas e as condições, mas não o comportamento do
filtro, que só o Excel executa. **Ao mudar o layout, abra o arquivo no Excel,
filtre uma loja com poucas linhas e olhe os gráficos.**
