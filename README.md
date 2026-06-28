# InkVPN

Современный VPN-клиент для Android на нативном ядре **sing-box** (gomobile),
с импортом подписок по ссылке (`inkvpn://add/https://...` или просто `https://...`),
тёмной OLED-темой и glassmorphism-интерфейсом.

Репозиторий состоит из трёх частей:

| Папка | Что это |
|-------|---------|
| `app/` | Нативное Android-приложение (Kotlin + Jetpack Compose) с реальным VPN-туннелем через `VpnService` + sing-box |
| `telegramBot/` | Telegram-бот (Node.js + Telegraf): продажа подписок и импорт ключа в приложение |
| `api/` | Backend API (Node.js + Fastify): выдача подписки `/sub/{key}` + вебхуки оплаты |

## Android-приложение

### Возможности
- **Реальный туннель** через sing-box: VLESS (Reality/Vision), VMess, Trojan, Shadowsocks,
  Hysteria2, SOCKS5, WireGuard. Транспорты: tcp, ws, grpc, httpupgrade, http.
- **Импорт подписки по ссылке** — все 5 форматов тела (base64, plain, JSON-массив,
  JSON-объект xray, смешанный) + приоритет HTTP-заголовков (`profile-title`,
  `subscription-userinfo`, `announce`, `support-url`, `profile-update-interval`, …).
- **Deep links** `inkvpn://` — `connect`/`disconnect`/`toggle`/`add`/`import`,
  а также прямые `vless://…` и `happ://add/…` (совместимо с Happ).
- **HWID** — `SHA256(SHA256(hw) + salt)` (64 hex), отправляется в заголовках запроса подписки.
- Экраны: Splash, Onboarding, Главная (power-кнопка), Серверы, Подписка (трафик/срок), Настройки.

### Сборка
```bash
# 1. Собрать ядро sing-box (один раз):
export ANDROID_NDK_HOME=/path/to/ndk
./scripts/build_libbox.sh            # создаёт app/libs/libbox.aar

# 2. Собрать APK:
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```
> `app/libs/libbox.aar` уже закоммичен (arm64 + x86_64), поэтому шаг 1 нужен только для пересборки.

### Архитектура ядра
`ProtocolParser` разбирает share-ссылку в sing-box outbound JSON →
`BoxConfigBuilder` оборачивает его в полный конфиг (tun + dns + route) →
`InkVpnService` (`VpnService` + `PlatformInterface`) поднимает туннель через `libbox`.

## Ограничения
- iOS не входит в этот репозиторий (требует macOS + Network Extension + платный Apple Dev).
- Транспорт **xHTTP/SplitHTTP** ядром sing-box не поддерживается — такие ноды отображаются,
  но помечаются звёздочкой и недоступны для подключения.
