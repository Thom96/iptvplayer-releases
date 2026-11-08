"use server";

import { redirect } from "next/navigation";
import { q } from "@/lib/db";
import { findByCode, logEvent, PLAN_LABEL, type Plan } from "@/lib/devices";

export async function requestActivation(form: FormData) {
  const code = String(form.get("code") ?? "");
  const plan = String(form.get("plan") ?? "") as Plan;
  const email = String(form.get("email") ?? "").trim().slice(0, 200);
  const device = await findByCode(code);
  if (!device) redirect(`/activeren?code=${encodeURIComponent(code)}`);
  const back = `/activeren?code=${encodeURIComponent(device.code)}`;
  if (plan !== "lifetime" && plan !== "yearly") redirect(`${back}&fout=plan`);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) redirect(`${back}&fout=email`);

  await q("UPDATE devices SET email = $2, requested_plan = $3, requested_at = now() WHERE code = $1", [
    device.code,
    email,
    plan,
  ]);
  await logEvent(device.code, "request", `Aanvraag: ${PLAN_LABEL[plan].toLowerCase()} (${email})`);
  redirect(`${back}&klaar=1`);
}
