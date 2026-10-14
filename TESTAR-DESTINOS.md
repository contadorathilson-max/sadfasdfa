# Como testar a busca por destinos — Rota Certa 3.1

1. Instale o novo `app-debug.apk` do GitHub Actions.
2. Ligue Localização e Internet no Android. Nas permissões do Rota Certa, escolha Permitir localização ao usar o app. Preferencialmente, habilite localização precisa.
3. Na aba **Roteiro**, toque em **Descobrir destinos perto de mim**. Espere a resposta do GPS e da busca (alguns segundos; um servidor comunitário pode demorar).
4. Se o GPS não funcionar, informe `Camaçari, BA` em Origem e toque em **Buscar destinos pela cidade de origem**.
5. Marque até 8 lugares e adicione ao roteiro. Toque em Calcular rota e Abrir Maps.
6. Os lugares são retornados pelo OpenStreetMap quando o proxy Google não está configurado. A lista pode conter menos de 30 resultados conforme a cobertura local.
7. Caso apareça **Destinos indisponíveis**, envie uma captura da mensagem completa. O erro HTTP ou de conexão ajudará a localizar a falha.

O uso da API Google Places exige configurar o proxy privado e a cobrança Google Cloud. Não há resultados Google Places pré-instalados.
