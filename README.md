# MobileForge 2.5

Вертикально — AI и код. Горизонтально — панели Unity (Hierarchy / Scene / Inspector / Project / Console) с живыми данными.

Сенсорного управления по умолчанию нет: стик и кнопки появляются только если AI (по вашей команде) создаст `UI/Controls.json`.

Сборка APK — **только GitHub Actions runner**.

Вы режиссёр: инспектор, ассеты, камера, свет, блоки, модели, `.cs`. Нейросеть **пишет только код** и ничего не применяет без вашей кнопки Apply.

## На телефоне

- Иерархия + сцена + инспектор (transform, mesh, material, light, camera, tag/layer, script)
- Asset Database: Scripts, Models, Materials, Prefabs, StudioPack
- Play-preview локально (без компиляции APK на девайсе)
- Cloud: несколько GitHub PAT/аккаунтов, список репо, создание репо, push проекта, `workflow_dispatch`
- MCP Workbench: локальные professional tools + HTTPS/localhost серверы
- AI: Create / Change / Delete / Explain → Review → Apply
- Модели: Zen free (без ключа), OpenRouter :free и paid, Orca, Gemini. Ключи только в Keystore.

## На runner

`./gradlew assembleDebug` генерирует `StudioPack` (~60–90 MB текстур/heightmaps/аудио) и пакует APK 50–100 MB.

```
workflow_dispatch / push main → artifact MobileForge-2.5.0-debug
```

## Безопасность

PAT хранятся в Android Keystore. В репозиторий токены не коммитятся.

## Лицензия

MIT
