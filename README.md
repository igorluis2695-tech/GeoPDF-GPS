# GeoPDF GPS — Android

Aplicativo Android simples para abrir um PDF georreferenciado (GeoPDF) e mostrar a posição GPS do celular sobre o mapa.

## Correção de georreferência

Esta versão considera o **Viewport (/VP e /BBox)** do GeoPDF. Isso é necessário em PDFs exportados pelo ArcMap, porque `LPTS` é relativo somente à área do mapa dentro da página e não à folha inteira (que também contém legenda, escala e quadro de informações).

## Gerar o APK online pelo GitHub

1. Envie todo o conteúdo desta pasta para o repositório, inclusive `.github`.
2. Abra **Actions**.
3. Abra **Build APK**.
4. Clique **Run workflow**.
5. Quando concluir com ✓ verde, abra a execução.
6. Em **Artifacts**, baixe **GeoPDF-GPS-APK**.
7. Descompacte e instale `app-debug.apk` no Android.

## Como usar

1. Abra o app e permita localização precisa.
2. Toque em **ABRIR GEOPDF**.
3. Selecione o PDF georreferenciado.
4. Aguarde o GPS estabilizar; a precisão aparece no topo.

## Versão 1.1 — Biblioteca de mapas
- Tela inicial "Meus Mapas".
- Importação de vários GeoPDFs para o armazenamento interno do aplicativo.
- Mapas continuam disponíveis offline dentro do app.
- Abrir e excluir mapas pela biblioteca.
- Novo layout de mapa com barra superior, zoom +/− e centralização no GPS.

## Importação de vários mapas

Esta versão permite selecionar e importar vários GeoPDFs de uma vez. O aplicativo mostra o progresso da importação e mantém todos os PDFs disponíveis offline em **Meus Mapas**.
