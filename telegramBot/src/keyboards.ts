import { Markup } from "telegraf";
import { generateImportDeepLink, fallbackInstallUrl } from "./services/subscriptionUrl.js";

export const mainKeyboard = () =>
  Markup.inlineKeyboard([
    [Markup.button.callback("🛒 Купить подписку", "buy"), Markup.button.callback("👤 Мой аккаунт", "account")],
    [Markup.button.callback("📱 Скачать приложение", "app"), Markup.button.callback("❓ Помощь", "help")],
  ]);

export const tariffsKeyboard = () =>
  Markup.inlineKeyboard([
    [Markup.button.callback("1 месяц · 199 ₽", "pay:m1")],
    [Markup.button.callback("3 месяца · 447 ₽ ⭐", "pay:m3")],
    [Markup.button.callback("1 год · 1188 ₽ 🏆", "pay:y1")],
  ]);

/** [🚀 Открыть в InkVPN] + [📋 Скопировать ключ]. */
export const importKeyboard = (key: string) =>
  Markup.inlineKeyboard([
    [Markup.button.url("🚀 Открыть в InkVPN", generateImportDeepLink(key))],
    [Markup.button.url("📦 Если приложения нет", fallbackInstallUrl(key))],
  ]);
