"use server";

import { redirect } from "next/navigation";
import { requireAdmin } from "@/lib/auth";
import { q } from "@/lib/db";
import { euro, findByCode, logEvent, normalizeCode, PLAN_LABEL, STATUS_LABEL, type Plan, type Status } from "@/lib/devices";

const STATUSES: Status[] = ["trial", "active", "free", "expired", "blocked"];

function back(code: string, msg: string): never {
  redirect(`/admin/apparaten?code=${encodeURIComponent(code)}&ok=${encodeURIComponent(msg)}`);
}

function fail(code: string, msg: string): never {
  redirect(`/admin/apparaten?code=${encodeURIComponent(code)}&fout=${encodeURIComponent(msg)}`);
}

async function target(form: FormData) {
  await requireAdmin();
  const code = String(form.get("code") ?? "");
  const device = await findByCode(code);
  if (!device) fail(code, "Apparaat niet gevonden");
  return device;
}

/** "2027-11-08" uit een datumveld → einde van die dag, of null bij leeg. */
function parseDate(v: FormDataEntryValue | null): string | null {
  const s = String(v ?? "").trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return null;
  return `${s}T23:59:59+01:00`;
}

export async function saveDevice(form: FormData) {
  const d = await target(form);
  const status = String(form.get("status")) as Status;
  if (!STATUSES.includes(status)) fail(d.code, "Onbekende status");
  const until = parseDate(form.get("until"));
  const note = String(form.get("note") ?? "").trim().slice(0, 500) || null;
  const email = String(form.get("email") ?? "").trim().slice(0, 200) || null;
  if (status === "trial") {
    await q("UPDATE devices SET status = $2, trial_ends = COALESCE($3::timestamptz, trial_ends), note = $4, email = $5 WHERE code = $1",
      [d.code, status, until, note, email]);
  } else {
    await q("UPDATE devices SET status = $2, valid_until = $3, note = $4, email = $5 WHERE code = $1",
      [d.code, status, until, note, email]);
  }
  if (status !== d.status) await logEvent(d.code, "admin", `Status op ${STATUS_LABEL[status].toLowerCase()} gezet door jou`);
  back(d.code, "Opgeslagen");
}

export async function extendYear(form: FormData) {
  const d = await target(form);
  await q(
    `UPDATE devices SET status = CASE WHEN status = 'free' THEN 'free' ELSE 'active' END,
       valid_until = GREATEST(now(), COALESCE(valid_until, now())) + interval '1 year' WHERE code = $1`,
    [d.code],
  );
  await logEvent(d.code, "admin", "Met een jaar verlengd door jou");
  back(d.code, "Een jaar verlengd");
}

export async function makeFree(form: FormData) {
  const d = await target(form);
  await q("UPDATE devices SET status = 'free', valid_until = NULL WHERE code = $1", [d.code]);
  await logEvent(d.code, "admin", "Gratis gezet door jou");
  back(d.code, "Gratis gezet");
}

export async function toggleBlock(form: FormData) {
  const d = await target(form);
  if (d.status === "blocked") {
    await q("UPDATE devices SET status = CASE WHEN plan IS NOT NULL THEN 'active' ELSE 'trial' END WHERE code = $1", [d.code]);
    await logEvent(d.code, "admin", "Blokkade opgeheven door jou");
    back(d.code, "Blokkade opgeheven");
  }
  await q("UPDATE devices SET status = 'blocked' WHERE code = $1", [d.code]);
  await logEvent(d.code, "admin", "Geblokkeerd door jou");
  back(d.code, "Geblokkeerd");
}

export async function registerPayment(form: FormData) {
  const d = await target(form);
  const plan = String(form.get("plan")) as Plan;
  if (plan !== "lifetime" && plan !== "yearly") fail(d.code, "Kies levenslang of per jaar");
  const amount = Math.round(Number(String(form.get("amount") ?? "").replace(",", ".")) * 100);
  if (!Number.isFinite(amount) || amount < 0) fail(d.code, "Bedrag klopt niet");
  const method = String(form.get("method") ?? "handmatig").trim().slice(0, 40) || "handmatig";

  await q("INSERT INTO payments (device_code, amount_cents, plan, method) VALUES ($1, $2, $3, $4)", [d.code, amount, plan, method]);
  if (plan === "lifetime") {
    await q("UPDATE devices SET status = 'active', plan = 'lifetime', valid_until = NULL, requested_plan = NULL, requested_at = NULL WHERE code = $1", [d.code]);
  } else {
    await q(
      `UPDATE devices SET status = 'active', plan = 'yearly', requested_plan = NULL, requested_at = NULL,
         valid_until = GREATEST(now(), COALESCE(valid_until, now())) + interval '1 year' WHERE code = $1`,
      [d.code],
    );
  }
  await logEvent(d.code, "payment", `Betaald ${euro(amount)} · ${PLAN_LABEL[plan].toLowerCase()}`);
  back(d.code, "Betaling geregistreerd, apparaat staat aan");
}

/** Nieuwe code (bijv. na herinstallatie) krijgt het abonnement van de oude; de oude wordt geblokkeerd. */
export async function linkOld(form: FormData) {
  const d = await target(form);
  const oldCode = normalizeCode(String(form.get("old") ?? ""));
  const old = await findByCode(oldCode);
  if (!old || old.code === d.code) fail(d.code, "Oude code niet gevonden");
  await q(
    `UPDATE devices SET status = $2, plan = $3, valid_until = $4, email = COALESCE(email, $5),
       note = COALESCE(note, $6), linked_from = $7 WHERE code = $1`,
    [d.code, old.status, old.plan, old.valid_until, old.email, old.note, old.code],
  );
  await q("UPDATE devices SET status = 'blocked', note = $2 WHERE code = $1", [old.code, `Vervangen door ${d.code}`]);
  await logEvent(d.code, "admin", `Abonnement overgenomen van ${old.code}`);
  await logEvent(old.code, "admin", `Vervangen door ${d.code}`);
  back(d.code, `Gekoppeld aan ${old.code}`);
}

export async function addDevice(form: FormData) {
  await requireAdmin();
  const code = normalizeCode(String(form.get("code") ?? ""));
  const d = await findByCode(code);
  if (!d) redirect(`/admin/apparaten?nieuw=1&fout=${encodeURIComponent("Code niet gevonden. Open eerst Gallo op die tv, dan verschijnt de code.")}`);
  const status = String(form.get("status")) === "active" ? "active" : "free";
  const until = parseDate(form.get("until"));
  const note = String(form.get("note") ?? "").trim().slice(0, 500) || null;
  await q("UPDATE devices SET status = $2, valid_until = $3, note = COALESCE($4, note) WHERE code = $1", [d.code, status, until, note]);
  await logEvent(d.code, "admin", status === "free" ? "Gratis gezet door jou" : "Aangezet door jou");
  back(d.code, "Apparaat aangezet");
}
