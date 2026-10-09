# AIRI Pocket на Xiaomi HyperOS 2.0.209 — адаптация под Android

Этот репозиторий (Project AIRI / stage-pocket) адаптирован под Android-устройства
Xiaomi с оболочкой **HyperOS 2.0.209** (Android 15, API level 35).

## Что было сделано

### Нативный слой (`apps/stage-pocket/android`)

Новый пакет `ai.moeru.airi_pocket.hyperos`:

| Файл | Назначение |
|---|---|
| `HyperOsDetector.kt` | Определение HyperOS/MIUI по системным свойствам (`ro.miui.ui.version.name`, `ro.product.marketname`), в т.ч. различение HyperOS 1.x (V140+) и HyperOS 2.x (V160+, семейство 2.0.209). Точная сборка ROM (например `OS2.0.209.0.WCCCNXM`) читается из `ro.mi.os.version.incremental` (fallback: `ro.build.version.incremental`) и сопоставляется с целевым билдом **2.0.209** (`isHyperOsTargetBuild()`). Все чтения свойств защищены от SELinux-ограничений прошивки. |
| `HyperOsBatteryOptimizationHelper.kt` | Обход агрессивной заморозки фоновых процессов в HyperOS: стандартный запрос игнорирования оптимизации батареи + deep-link в «Автозапуск» (`com.miui.securitycenter`) и в ограничение энергопотребления приложения («Без ограничений», `com.miui.powerkeeper`). Перебираются все известные варианты имён активностей MIUI/HyperOS 1/HyperOS 2. |
| `HyperOsRefreshRateHelper.kt` | Диагностика троттлинга частоты обновления экрана в HyperOS 2 («умная» герцовка принудительно снижает WebView до 60 Гц → Live2D/MMD тормозят). Открывает настройки экрана, отдаёт веб-слою диагностический payload. |
| `HyperOsPlugin.kt` | Capacitor-плагин `HyperOs`, через который веб-слой (Vue) вызывает все возможности ROM-адаптации. |

Изменения в существующем коде:

- `MainActivity.kt`
  - регистрация плагина `HyperOsPlugin`;
  - `applyHyperOsWorkarounds()` — на HyperOS отключает принудительный тёмный режим
    WebView (`FORCE_DARK_OFF`), который HyperOS иначе навязывает всему контенту и
    ломает рендер персонажа; логирует распознанную прошивку.
- `AndroidManifest.xml` — добавлены разрешения для выживания на HyperOS:
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`,
  `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, а также `<queries>` для
  `com.miui.securitycenter` и `com.miui.powerkeeper` (без них Android 15 block
  intent resolution на HyperOS-настройки).

### Веб-слой (`apps/stage-pocket/src`)

- `modules/hyperos.ts` — типизированная JS-фасад над нативным плагином; no-op вне Android.
- `main.ts` — при старте на Android определяет HyperOS; если приложение не
  добавлено в исключения оптимизации батареи, запускает «мастер выживания»
  (диалог Doze-исключения → автозапуск → «Без ограничений»), чтобы native
  WebSocket-мост к stage-серверу не убивался прошивкой в фоне.

SDK-конфигурация проекта уже соответствует целевой платформе:
`minSdk 26`, `compileSdk/targetSdk 36` (см. `android/variables.gradle.kts`),
что покрывает HyperOS 2.0.209 (Android 15).

## Настройки телефона (рекомендация пользователю AIRI)

После первого запуска на HyperOS 2.0.209 приложение само откроет нужные экраны,
но вручную можно проверить:

1. **Настройки → Приложения → AIRI → Автозапуск** — разрешить.
2. **Настройки → Батарея → AIRI → Экономия энергии** — «Без ограничений».
3. **Настройки → Экран → Частота обновления** — выбрать вручную максимум
   (120/144 Гц), иначе HyperOS занижает герцовку для WebView и анимация
   персонажа будет дёргаться.
4. Отключить **«Усилитель памяти» / Memory extension** для AIRI — swap-сжатие
   в HyperOS 2 периодически приводит к GC-паузам в WebView-процессе.

## Сборка APK для устройства

```bash
pnpm install
pnpm --filter @proj-airi/stage-pocket build
pnpm --filter @proj-airi/stage-pocket exec npx cap sync android
cd apps/stage-pocket/android && ./gradlew assembleDebug
# установка: adb install app/build/outputs/apk/debug/app-debug.apk
```
