# Rota Certa 3.2 — Android

Planejador de viagens Android nativo (Java 17, Android 8.0+), com orçamento por **carro, ônibus e avião** e busca dinâmica de atrações locais da versão 3.1.

## Novidades 3.2

| Modalidade | O que o app calcula | Informações inseridas |
|---|---|---|
| **Carro** | Combustível com km ÷ km/l × preço por litro | Veículo, consumo, preço por litro, rota calculada ou km manual |
| **Ônibus** | Passagens por pessoa e trecho; bagagem; traslados e transporte local | Passagem de ida por pessoa, passagem de volta por pessoa, outros custos |
| **Avião** | Passagens por pessoa e trecho; bagagem; traslados e transporte local | Tarifa de ida e volta por pessoa e custos complementares |

**O aplicativo não consulta preços, horários nem disponibilidade de companhias aéreas e rodoviárias.** Eles devem ser obtidos externamente e informados no orçamento. Para pesquisar voos, abre o Google Flights; para ônibus, abre o Google Maps no modo transporte público. Este último não substitui um serviço de vendas de passagens intermunicipais ou interestaduais.

Para ônibus e avião, **ida e volta** é controlada pela chave no Roteiro; apenas a volta é excluída do cálculo quando desativada. Valores de passagens e bagagens são multiplicados pelo número de viajantes; custos de traslados, transporte local e taxas são inseridos como **total para o grupo** (sem multiplicação).

## Recursos preservados

- Viagens, pessoas, datas de ida/volta e dias/noites calculados automaticamente.
- Hospedagem: valor total da estadia e médias por noite e por quarto/noite.
- Alimentação: botão que preenche quantidades a partir dos dias/noites e permite editar os números.
- GPS e roteamento rodoviário com OSRM/OpenStreetMap **somente para carro**. Não há cálculo automático de rota aérea, tarifa rodoviária ou trânsito ao vivo.
- Descoberta de destinos perto do GPS e por cidade de origem (da **versão 3.1**). Google Places opcional por proxy, com fallback OpenStreetMap/Overpass; tenta encontrar 30 locais quando disponíveis, sem inventar atrações.
- Orçamento vivo, reserva para imprevistos, despesas reais, resumo por pessoa, compartilhamento WhatsApp/Android e viagens salvas localmente.
- Backup JSON: **Viagens → Exportar backup** e **Restaurar backup**. O backup contém roteiro, gastos e planejamentos salvos, mas **não** contém a chave ou token do serviço Google.
- App inicia sem veículo, consumo histórico, preço de passagem ou destinos previamente cadastrados.

## Cálculos

**Carro:** combustível = distância (km) / consumo (km/l) × R$/litro.

**Avião e ônibus:** passagens = viajantes × (tarifa ida por pessoa + tarifa de volta por pessoa, quando a volta está ativada). Bagagens = viajantes × preço de bagagem por pessoa (total da viagem). Traslados, transporte no destino e taxas são custos **totais do grupo**.

**Orçamento:** transporte + hospedagem + alimentação + passeios + estacionamento + pedágios + outros + imprevistos. Imprevistos = percentual sobre o subtotal (limitado a 100%). O app mantém separadas as despesas do transporte selecionado, evitando carregar tarifa aérea quando o modo carro é escolhido.

**Importante:** preços de passagens e hospedagem não são orçamentos gerados por fornecedores. O valor total depende do que você digitar. Confira taxas, bagagens, regras de passageiros (crianças, descontos) e conexões antes de reservar.

## Gerar APK pelo GitHub (sem Android Studio)

1. No repositório, envie o **conteúdo extraído** deste pacote para a raiz. Devem existir `app/build.gradle`, `settings.gradle`, `.github/workflows/android-apk.yml`, `tools/` e `google-places-proxy/`.
2. No GitHub, abra **Actions → Gerar APK - Rota Certa 3.2**. O push em `main`/`master` também aciona o workflow.
3. Após a execução verde, baixe o artefato `RotaCerta-3.2-APK` e extraia o `app-debug.apk`.

O workflow utiliza Ubuntu 24.04, Java 17, Gradle 8.11.1 e Android SDK 35 pré-instalado, evitando o `android-actions/setup-android@v3` que falhou anteriormente.

### Atualizações e dados

O APK do workflow é uma **versão debug**, com assinatura do runner do GitHub; **ela pode mudar entre builds** e exigir desinstalação do aplicativo anterior. Desinstalar apaga dados locais. **Exporte um backup na tela Viagens antes de trocar de APK** e restaure no novo aplicativo. A primeira migração de uma versão que não tinha backup exige cuidado, pois ela não tem essa função. Para distribuição estável, configure no futuro uma assinatura de release persistente armazenada em GitHub Secrets.

## Verificações executadas localmente

```bash
bash tools/testar-motor.sh
```

Valida cálculo de carro, passagens ida/volta, bagagem, custos de grupo, reserva percentual, datas/noites/refeições e valores numéricos com vírgula/ponto. Os testes unitários JUnit são executados no workflow junto com `assembleDebug`. Sintaxe Java verificada com parser JDK; **compilação Android integral e testes em aparelho permanecem pendentes do GitHub Actions**.

## API de destinos e privacidade

A busca de destinos usa serviços públicos do OpenStreetMap, sujeitos à disponibilidade; não garante 30 atrações em qualquer cidade. A consulta Google exige configurar proxy seguro (veja `google-places-proxy/COMO-CONFIGURAR.md`) e pode ser cobrada. O aplicativo não embute chave Google.

Seus gastos, roteiros e backups permanecem sob seu controle. A localização é enviada aos serviços externos quando você aciona GPS/busca/rotas. **Não coloque chaves privadas dentro do repositório.**
