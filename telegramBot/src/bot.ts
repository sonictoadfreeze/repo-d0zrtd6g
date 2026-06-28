import "dotenv/config";
import { Telegraf } from "telegraf";
import { mainKeyboard, tariffsKeyboard, importKeyboard } from "./keyboards.js";
import { generateSubscription, plan } from "./services/keyGenerator.js";
import { subscriptionUrl } from "./services/subscriptionUrl.js";

const token = process.env.BOT_TOKEN;
if (!token) throw new Error("BOT_TOKEN is required (see .env.example)");

const bot = new Telegraf(token);

// In-memory store — replace with PostgreSQL in production (see api/db/schema.sql).
const subsByUser = new Map<number, ReturnType<typeof generateSubscription>>();

bot.start((ctx) =>
  ctx.reply(
    "👋 Привет! Я InkVPN бот.\nБыстрый и безопасный VPN в пару нажатий.\nВыбери действие:",
    mainKeyboard(),
  ),
);

bot.action("buy", (ctx) => ctx.editMessageText("Выбери тариф:", tariffsKeyboard()));

bot.action(/^pay:(\w+)$/, async (ctx) => {
  const planId = ctx.match[1];
  const p = plan(planId);
  if (!p) return ctx.answerCbQuery("Неизвестный тариф");
  // TODO: real payment (Telegram Stars / YooKassa / Stripe / Crypto). Demo: instant issue.
  const sub = generateSubscription(ctx.from!.id, planId);
  subsByUser.set(ctx.from!.id, sub);
  await ctx.answerCbQuery("Оплачено ✅");
  await ctx.replyWithHTML(
    `✅ <b>Оплата прошла! Ваш VPN готов.</b>\n\n🔑 <b>Ключ подписки:</b>\n<code>${subscriptionUrl(sub.key)}</code>\n\n📅 Действует до: <b>${sub.expiresAt.toLocaleDateString("ru-RU")}</b>\n\nНажми кнопку ниже — приложение откроется и добавит VPN автоматически.`,
    importKeyboard(sub.key),
  );
});

bot.action("account", (ctx) => {
  const sub = subsByUser.get(ctx.from!.id);
  if (!sub) return ctx.answerCbQuery("Подписок нет — купи в меню");
  return ctx.replyWithHTML(
    `👤 <b>Аккаунт</b>\n🔑 Ключ: <code>${sub.key.slice(0, 12)}…</code>\n📅 До: ${sub.expiresAt.toLocaleDateString("ru-RU")}\n🌐 Устройств: до ${sub.deviceLimit}`,
    importKeyboard(sub.key),
  );
});

bot.action("app", (ctx) =>
  ctx.reply("📲 Скачать приложение: " + (process.env.APP_DOWNLOAD_URL ?? "https://inkvpn.com")),
);
bot.action("help", (ctx) => ctx.reply("🆘 Поддержка: " + (process.env.SUPPORT_URL ?? "https://t.me/InkVPNSupport")));

bot.launch().then(() => console.log("InkVPN bot started"));
process.once("SIGINT", () => bot.stop("SIGINT"));
process.once("SIGTERM", () => bot.stop("SIGTERM"));
