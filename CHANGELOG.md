# Registro de versões

## v3.2.0 — Multitransporte e revisão geral
- Novo seletor visual: carro, ônibus ou avião, sem perder os recursos existentes.
- Orçamento para passagem de ida e volta por viajante; volta incluída apenas se selecionada.
- Bagagem por viajante, traslados para o grupo, transporte local e taxas adicionais separadas.
- Resumo financeiro adaptado à modalidade escolhida, com exclusão de combustíveis/passagens não pertinentes.
- Pesquisa externa de voos no Google Flights e visualização de destino no Maps; ônibus no modo transporte público do Maps. Sem alegar disponibilidade nem cotação ao vivo.
- Entrada de `1.200` e `1.234,56` corrigida para padrões brasileiros; valores inválidos não somam.
- Correção de corrida na consulta de rota: resposta antiga não substitui uma nova origem, destino ou modalidade.
- Origem digitada prevalece sobre GPS já ativado ao calcular rota de carro.
- Formulário de despesa inválida permanece aberto para correção.
- Backups JSON de roteiro, despesas e viagens salvas, com restauração confirmada.
- Checklist adaptado por modalidade e categorias de despesa de passagens/traslados/bagagens.
- Testes locais adicionais de cálculo e workflow atualizado para Rota Certa 3.2.

## v3.1.0 — Destinos corrigidos
- Overpass atualizado, busca em múltiplos raios e fallback de servidor.
- GPS com tentativas alternativas, opção de pesquisar pela origem e coordenadas nos destinos.

## v3.0.0 — Orçamento inteligente
- Hospedagem informada pelo valor total, dias/noites automáticos e botão de refeições automáticas.
