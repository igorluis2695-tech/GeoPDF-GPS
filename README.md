# GeoPDF GPS — Android

Aplicativo Android simples para abrir um PDF georreferenciado (GeoPDF) e mostrar a posição GPS do celular sobre o mapa.

## Gerar o APK online pelo GitHub

1. Crie um repositório novo no GitHub.
2. Envie **todo o conteúdo desta pasta** para o repositório, inclusive a pasta `.github`.
3. Abra a aba **Actions**.
4. Abra **Build APK**.
5. Clique em **Run workflow** e depois em **Run workflow** novamente.
6. Quando aparecer o símbolo verde de concluído, abra a execução.
7. Em **Artifacts**, baixe **GeoPDF-GPS-APK**.
8. Descompacte o arquivo baixado. Dentro estará `app-debug.apk`.
9. Passe `app-debug.apk` para o celular Android e instale.

> No Android, pode ser necessário autorizar "Instalar apps desconhecidos" para o navegador/gerenciador de arquivos usado na instalação.

## Como usar

1. Abra o app e permita acesso à localização.
2. Toque em **ABRIR GEOPDF**.
3. Selecione seu PDF georreferenciado.
4. O GPS aparecerá sobre o mapa quando o PDF usar o padrão GPTS/LPTS suportado nesta versão.

## Observação importante

GeoPDFs podem armazenar georreferenciamento de maneiras diferentes. Esta primeira versão lê GeoPDF com `GPTS/LPTS`. Se um PDF específico não abrir corretamente, o parser precisa ser adaptado ao formato desse arquivo.
