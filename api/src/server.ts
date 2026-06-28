import "dotenv/config";
import Fastify from "fastify";
import { serverLinksForKey } from "./serverList.js";

const app = Fastify({ logger: true });

// Demo HWID device-limit tracking (replace with DB).
const hwidsByKey = new Map<string, Set<string>>();
const DEVICE_LIMIT = 5;

/** GET /sub/:key -> subscription body (base64) + InkVPN headers. */
app.get<{ Params: { key: string } }>("/sub/:key", async (req, reply) => {
  const { key } = req.params;
  const hwid = (req.headers["x-hwid"] as string) ?? "";
  if (hwid) {
    const set = hwidsByKey.get(key) ?? new Set<string>();
    if (!set.has(hwid) && set.size >= DEVICE_LIMIT) {
      return reply.code(403).send("device limit reached");
    }
    set.add(hwid);
    hwidsByKey.set(key, set);
  }

  const body = serverLinksForKey(key).join("\n");
  const expire = Math.floor(Date.now() / 1000) + 30 * 86400;

  reply
    .header("profile-title", "base64:" + Buffer.from("⚡ InkVPN").toString("base64"))
    .header("profile-update-interval", "12")
    .header("support-url", process.env.SUPPORT_URL ?? "https://t.me/InkVPNSupport")
    .header("profile-web-page-url", "https://inkvpn.com")
    .header("subscription-userinfo", `upload=0; download=0; total=${107374182400}; expire=${expire}`)
    .header("announce", "base64:" + Buffer.from("Добро пожаловать в InkVPN!").toString("base64"))
    .header("content-type", "text/plain; charset=utf-8");

  return Buffer.from(body).toString("base64");
});

/** GET /sub/:key/status -> JSON used by the Telegram bot's /account. */
app.get<{ Params: { key: string } }>("/sub/:key/status", async (req) => {
  const expire = Math.floor(Date.now() / 1000) + 30 * 86400;
  return { active: true, expires: expire, traffic_used: 0 };
});

/** POST /payment/webhook -> activate/extend a key after payment. */
app.post("/payment/webhook", async (req, reply) => {
  // TODO: verify provider signature, activate/extend subscription, notify the bot.
  app.log.info({ body: req.body }, "payment webhook");
  return reply.send({ ok: true });
});

/** POST /sub/:key/reset-hwid -> clear device bindings. */
app.post<{ Params: { key: string } }>("/sub/:key/reset-hwid", async (req) => {
  hwidsByKey.delete(req.params.key);
  return { ok: true };
});

const port = Number(process.env.PORT ?? 8080);
app.listen({ port, host: "0.0.0.0" }).then(() => app.log.info(`InkVPN API on :${port}`));
