# Atualizar o repositório para Rota Certa 3.2

1. Faça o download do ZIP desta versão e **extraia** os arquivos.
2. Se o repositório existente tiver dados importantes na branch atual, mantenha uma cópia dos arquivos anteriores.
3. Substitua na raiz do seu repositório as pastas `app/`, `tools/`, `.github/`, `google-places-proxy/` e os arquivos de configuração (`build.gradle`, `settings.gradle`, `gradle.properties`, etc.). **Não envie somente o ZIP** nem deixe os arquivos dentro de uma pasta adicional.
4. Confirme que `.github/workflows/android-apk.yml` foi atualizado.
5. Faça commit em `main` (ou `master`). O GitHub Actions executará **Gerar APK - Rota Certa 3.2** automaticamente, ou abra Actions → Run workflow.
6. Baixe `RotaCerta-3.2-APK` > `app-debug.apk` na execução concluída.

**Cuidado com os dados do celular:** esta compilação é debug e pode ter uma assinatura diferente da anterior; se precisar desinstalar o APK antigo, seus dados locais serão apagados. A versão 3.2 permite exportar backup JSON em Viagens. Se ainda está na 3.1, não desinstale antes de copiar manualmente os dados que deseja preservar.

O app permanece compatível com o mesmo identificador `br.com.rotacerta.app`. A compilação final requer confirmação no GitHub Actions.
