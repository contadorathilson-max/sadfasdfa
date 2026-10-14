# Ativar 30+ sugestões pelo Google Places

O aplicativo funciona **sem Google** usando OpenStreetMap. Esta configuração opcional ativa a busca de destinos diretamente pelo Google, respeitando as exigências de chave/billing e **sem expor a chave Google no APK**.

## 1. Google Cloud

1. No [Google Cloud Console](https://console.cloud.google.com/), crie/selecione projeto.
2. Ative faturamento, defina alertas de orçamento/limites apropriados e habilite **Places API (New)**.
3. Crie uma chave de API dedicada e **restrinja-a à Places API (New)**. Para serviços REST, mantenha a chave apenas do lado do servidor. Nunca inclua essa chave no repositório ou no APK.

## 2. Cloudflare Workers

1. No [Cloudflare Dashboard](https://dash.cloudflare.com/), crie um Worker e substitua seu código pelo arquivo `worker.js` desta pasta.
2. Configure os **Secrets** (não variáveis visíveis em código):
   - `GOOGLE_PLACES_API_KEY`: chave do passo 1.
   - `ROTA_APP_TOKEN`: token aleatório longo criado por você, diferente da chave Google.
3. Faça **Deploy** e copie a URL raiz `https://...workers.dev`.
4. Em **Rota Certa > Viagens > Configurar sugestões do Google**, informe essa URL e o mesmo `ROTA_APP_TOKEN`.
5. Volte à aba Roteiro, toque em **Descobrir destinos perto de mim** e permita acesso ao GPS.

O endpoint do Worker é `GET /destinos?lat=...&lon=...` com `Authorization: Bearer <ROTA_APP_TOKEN>`.

## Limitações e custos

- Cada busca consulta até **quatro** grupos de lugares via Places Text Search (New), sujeito a cobrança da Google Maps Platform; confira preços atuais na conta Google.
- O app exibe o provedor da lista. Se Google retornar menos de 30 lugares, tentará complementar com OSM. Se a região tiver poucos locais nomeados, pode retornar menos de 30.
- Este Worker é um modelo para **uso pessoal**, com token compartilhado e sem limite por usuário. Para publicar o aplicativo para outras pessoas, acrescente autenticação por usuário, rate limiting, proteção contra abuso e monitoramento de custos. A chave Google não deve ser colocada no app, nem no arquivo do GitHub.
- A localização precisa da pessoa é enviada para o seu Worker e ao Google ao buscar sugestões. O Worker não registra nem persiste as coordenadas por conta própria.
