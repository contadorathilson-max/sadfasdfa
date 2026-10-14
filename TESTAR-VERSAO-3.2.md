# Checklist de validação Android — v3.2

**Carro:** escolha Carro, informe km/l e preço/l, calcule a rota e confira gasolina. Alterar a cidade de origem depois de iniciar o cálculo não deve usar a rota antiga.

**Avião:** escolha Avião, informe 2 viajantes, passagem ida R$ 450, volta R$ 600, bagagem R$ 80 por pessoa, traslado R$ 100 total e transporte local R$ 200 total. Com ida e volta ativadas, o transporte deverá mostrar R$ 2.560. Desative a volta: deverá mostrar R$ 1.360.

**Ônibus:** escolha Ônibus, informe 3 viajantes, ida R$ 110, volta R$ 130, traslado total R$ 60. Com volta ativada, o transporte deve ser R$ 780.

**Separação:** volte para Carro e confirme que passagens não entram no combustível nem no total. Retorne a Avião e confira que os valores de passagens permanecem disponíveis, sem serem somados duas vezes.

**Viagem:** altere datas, pessoas, refeições automáticas, hospedagem total, despesas realizadas e compartilhamento. Salve uma viagem e abra novamente para checar a modalidade.

**Backup:** exporte o JSON em Viagens, altere algum dado e importe o backup com confirmação. O dado anterior deve ser restaurado. Não compartilhe o JSON publicamente.

**Destinos próximos (3.1):** autorize localização e pesquise atrações. Se não houver GPS, informe a cidade em Origem e pesquise por ela. Confira coordenadas dos locais selecionados e calcule rota apenas em Carro.

**Atenção:** pesquisas de voos e ônibus abrem serviços externos; tarifas e disponibilidade não são importadas automaticamente.
