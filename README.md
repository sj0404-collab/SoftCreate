# MobileForge 1.1.0

Нативная Android-студия 2D/3D-игр: Jetpack Compose, без WebView и без HTML-оболочки.

HTML больше не является интерфейсом. Опциональный **HTML preview** — только экспорт снимка сцены в файл для шаринга.

## Что внутри

- **Проекты** — создать / открыть / удалить / импорт / экспорт JSON
- **Studio** — дерево файлов, нативный редактор с **автодополнением** API, сцена и инспектор
- **Сцены** — 2D / 3D, `Scenes/*.scene.json`
- **Play** — нативный рантайм: гравитация, коллизии, стик, WASD, Jump; JS/C# исполняет встроенный интерпретатор
- **AI** — Zen / OpenRouter / MCP / Custom HTTPS → Review → Apply
- **Settings** — ключи в Android Keystore
- **SkyRunner demo** — монеты, шипы, ворота

## Сборка

JDK 17 + Android SDK 35:

```bash
./gradlew assembleDebug
```

CI: `.github/workflows/android.yml`

## Архитектура

```
Compose UI
  ├─ AppViewModel
  ├─ ProjectStore / SceneStore
  ├─ SecretStore + AiGateway
  ├─ GameRuntime + ScriptInterpreter
  └─ SceneRenderer (Canvas)
```

Скрипты Play: `onStart` / `onUpdate` / `onCollisionEnter` и `api.move`, `api.jump`, `api.input`, `Math.sin`, …

C# транспилируется в тот же API.

## Прототип

`legacy/MobileForge-0.2.1.apk` — старый WebView-прототип.

## Лицензия

MIT
