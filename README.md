# MobileForge 1.0.0

Мобильная студия 2D/3D-игр: проекты на диске, редактор исходников, сцены, Play-режим и AI-агент.

Репозиторий [sj0404-collab/SoftCreate](https://github.com/sj0404-collab/SoftCreate). Версия **0.2.1** была прототипом: HTML-preview с заглушками и неподключённым `AiGateway`. **1.0.0** — полный рабочий контур без «Cycle 2».

## Что работает

- **Проекты** — создать / открыть / переименовать / удалить / импорт / экспорт JSON-бандла
- **Studio** — дерево файлов, редактор, сохранение, сцена и инспектор объектов
- **Сцены** — 2D и 3D, `Scenes/*.scene.json` формата `mobileforge.scene.v1`
- **Play** — реальный рантайм: гравитация, коллизии, WASD / стик / прыжок, JS и C# (транспиляция)
- **AI** — Zen Direct, OpenRouter, Local MCP (`127.0.0.1:8765`), Custom HTTPS  
  Generate → Review (diff) → Apply пишет файлы
- **Settings** — ключи в Android Keystore (AES-GCM), проверка MCP
- **SkyRunner demo** — арена с монетами, шипами и финишными воротами

## Сборка APK

Нужны JDK 17 и Android SDK 35.

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

CI: `.github/workflows/android.yml` собирает debug-APK на каждый push в `main`.

## Архитектура

```
WebView IDE (assets/)
    JS bridge  ──►  AndroidBridge
                       ├─ ProjectStore   filesDir/projects
                       ├─ SceneStore     *.scene.json
                       ├─ SecretStore    AndroidKeyStore
                       └─ AiGateway      Zen / OpenRouter / MCP / Custom
```

Нативный мост:

| Метод | Назначение |
| --- | --- |
| `projects` / `createProjectTyped` / `deleteProject` / `renameProject` | проекты |
| `listFiles` / `readFile` / `writeFile` / `createFile` / `deleteFile` | исходники |
| `scenes` / `createScene` / `saveScene` / `deleteScene` | сцены |
| `generate` / `generateAsync` | AI proposal |
| `saveProvider` / `providerConfig` / `checkMcp` | секреты |
| `exportProject` / `importProject` / `seedDemo` | обмен и демо |

Без Android HTML поднимается в браузере: файловая система в `localStorage` (AI и MCP только в APK).

## Скрипты Play

JavaScript:

```js
function onStart(api) {}
function onUpdate(api, dt) {
  api.move(api.input.x * 6 * dt, 0, api.input.y * 6 * dt);
  if (api.input.jump) api.jump(7);
}
function onCollisionEnter(api, other) {}
```

C# того же API транспилируется (`Move`, `Jump`, `input.horizontal`, `OnCollisionEnter`).

## Прототип

Исходный APK 0.2.1 лежит в `legacy/`.

## Лицензия

MIT
