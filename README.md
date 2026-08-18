# MobileForge 2.0

Телефон — **Unity-like редактор и preview**. Сборка APK — **только GitHub Actions runner**, чтобы не засорять устройство.

Вы режиссёр: инспектор, ассеты, камера, свет, блоки, модели, `.cs`. Нейросеть **пишет только код** и ничего не применяет без вашей кнопки Apply.

## На телефоне

- Иерархия + сцена + инспектор (transform, mesh, material, light, camera, tag/layer, script)
- Asset Database: Scripts, Models, Materials, Prefabs, StudioPack
- Play-preview локально (без компиляции APK на девайсе)
- Cloud: несколько GitHub PAT/аккаунтов, список репо, создание репо, push проекта, `workflow_dispatch`
- MCP Workbench: локальные professional tools + HTTPS/localhost серверы
- AI: Create / Change / Delete / Explain → Review → Apply

## На runner

`./gradlew assembleDebug` генерирует `StudioPack` (~60–90 MB текстур/heightmaps/аудио) и пакует APK 50–100 MB.

```
workflow_dispatch / push main → artifact MobileForge-2.0.0-debug
```

## Безопасность

PAT хранятся в Android Keystore. В репозиторий токены не коммитятся.

## Лицензия

MIT
