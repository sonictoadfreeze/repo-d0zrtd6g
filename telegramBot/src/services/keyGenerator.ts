import { randomUUID } from "node:crypto";

export interface SubscriptionRecord {
  key: string;
  userId: number;
  plan: string;
  expiresAt: Date;
  deviceLimit: number;
  createdAt: Date;
}

const PLANS: Record<string, { months: number; deviceLimit: number; priceRub: number }> = {
  m1: { months: 1, deviceLimit: 3, priceRub: 199 },
  m3: { months: 3, deviceLimit: 5, priceRub: 447 },
  y1: { months: 12, deviceLimit: 10, priceRub: 1188 },
};

export function plan(planId: string) {
  return PLANS[planId];
}

export function generateSubscription(userId: number, planId: string): SubscriptionRecord {
  const p = PLANS[planId];
  if (!p) throw new Error(`unknown plan ${planId}`);
  const expiresAt = new Date();
  expiresAt.setMonth(expiresAt.getMonth() + p.months);
  return {
    key: `sub_${randomUUID().replace(/-/g, "")}`,
    userId,
    plan: planId,
    expiresAt,
    deviceLimit: p.deviceLimit,
    createdAt: new Date(),
  };
}
