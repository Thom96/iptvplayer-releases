import { randomInt } from "node:crypto";
import { one, q } from "./db";

export type Status = "trial" | "active" | "free" | "expired" | "blocked";
export type Plan = "lifetime" | "yearly";

export const STATUS_LABEL: Record<Status, string> = {
  active: "Actief",
  free: "Gratis",
  trial: "Proef",
  expired: "Verlopen",
  blocked: "Geblokkeerd",
};

export const PLAN_LABEL: Record<Plan, string> = {
  lifetime: "Levenslang",
  yearly: "Per jaar",
};

/** Status zoals hij nu geldt: een verlopen einddatum maakt van actief/gratis/proef "verlopen". */
export const EFFECTIVE_STATUS = `CASE
  WHEN d.status = 'blocked' THEN 'blocked'
  WHEN d.status IN ('active','free') AND (d.valid_until IS NULL OR d.valid_until > now()) THEN d.status
  WHEN d.status = 'trial' AND d.trial_ends > now() THEN 'trial'
  ELSE 'expired' END`;

export type Device = {
  id: string;
  code: string;
  status: string;
  effective: Status;
  plan: Plan | null;
  trial_ends: string;
  valid_until: string | null;
  email: string | null;
  note: string | null;
  requested_plan: Plan | null;
  requested_at: string | null;
  app_version: string | null;
  model: string | null;
  linked_from: string | null;
  created_at: string;
  last_seen: string;
};

export const DEVICE_COLUMNS = `d.*, ${EFFECTIVE_STATUS} AS effective`;

// ---------- instellingen ----------

export type Settings = {
  trialDays: number;
  priceLifetimeCents: number;
  priceYearlyCents: number;
  paymentInfo: string;
};

const DEFAULTS: Settings = {
  trialDays: 7,
  priceLifetimeCents: 0,
  priceYearlyCents: 0,
  paymentInfo: "",
};

export async function getSettings(): Promise<Settings> {
  const rows = await q<{ key: string; value: string }>("SELECT key, value FROM settings");
  const map = Object.fromEntries(rows.map((r) => [r.key, r.value]));
  return {
    trialDays: map.trialDays ? Number(map.trialDays) : DEFAULTS.trialDays,
    priceLifetimeCents: map.priceLifetimeCents ? Number(map.priceLifetimeCents) : DEFAULTS.priceLifetimeCents,
    priceYearlyCents: map.priceYearlyCents ? Number(map.priceYearlyCents) : DEFAULTS.priceYearlyCents,
    paymentInfo: map.paymentInfo ?? DEFAULTS.paymentInfo,
  };
}

export async function saveSettings(s: Settings): Promise<void> {
  for (const [key, value] of Object.entries(s)) {
    await q(
      "INSERT INTO settings (key, value) VALUES ($1, $2) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
      [key, String(value)],
    );
  }
}

// ---------- codes ----------

// Zonder 0/O en 1/I/L, zodat de code op tv makkelijk over te nemen is.
const ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

function newCode(): string {
  let s = "";
  for (let i = 0; i < 8; i++) s += ALPHABET[randomInt(ALPHABET.length)];
  return `${s.slice(0, 4)}-${s.slice(4)}`;
}

/** Maakt een ingetypte code netjes: hoofdletters, zonder spaties, streepje in het midden. */
export function normalizeCode(input: string): string {
  const clean = input.toUpperCase().replace(/[^0-9A-Z]/g, "");
  if (clean.length !== 8) return clean;
  return `${clean.slice(0, 4)}-${clean.slice(4)}`;
}

export async function logEvent(code: string | null, type: string, text: string): Promise<void> {
  await q("INSERT INTO events (device_code, type, text) VALUES ($1, $2, $3)", [code, type, text]);
}

export async function findByCode(code: string): Promise<Device | null> {
  return one<Device>(`SELECT ${DEVICE_COLUMNS} FROM devices d WHERE d.code = $1`, [normalizeCode(code)]);
}

/** Aanmelden of status opvragen vanuit de app. Een onbekend apparaat krijgt een code en een proefperiode. */
export async function checkIn(id: string, appVersion: string | null, model: string | null): Promise<Device> {
  const existing = await one<Device>(
    `UPDATE devices d SET last_seen = now(),
       app_version = COALESCE($2, d.app_version), model = COALESCE($3, d.model)
     WHERE d.id = $1 RETURNING ${DEVICE_COLUMNS}`,
    [id, appVersion, model],
  );
  let device = existing;
  if (!device) {
    const { trialDays } = await getSettings();
    for (let attempt = 0; attempt < 5 && !device; attempt++) {
      device = await one<Device>(
        `INSERT INTO devices AS d (id, code, trial_ends, app_version, model)
         VALUES ($1, $2, now() + make_interval(days => $3), $4, $5)
         ON CONFLICT DO NOTHING RETURNING ${DEVICE_COLUMNS}`,
        [id, newCode(), trialDays, appVersion, model],
      );
      if (!device) {
        // Botsing op id (tegelijk aangemeld) of op code: probeer het bestaande apparaat opnieuw.
        device = await one<Device>(`SELECT ${DEVICE_COLUMNS} FROM devices d WHERE d.id = $1`, [id]);
      }
    }
    if (!device) throw new Error("Kon geen apparaatcode maken");
    if (!existing) await logEvent(device.code, "new", `Nieuw apparaat, proef van ${trialDays} dagen gestart`);
  }
  await q("INSERT INTO seen_days (device_code, day) VALUES ($1, CURRENT_DATE) ON CONFLICT DO NOTHING", [device.code]);
  return device;
}

// ---------- weergave ----------

export function euro(cents: number): string {
  return new Intl.NumberFormat("nl-NL", { style: "currency", currency: "EUR", maximumFractionDigits: cents % 100 ? 2 : 0 }).format(cents / 100);
}

export function date(value: string | Date | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("nl-NL", { day: "numeric", month: "short", year: "numeric", timeZone: "Europe/Amsterdam" }).format(new Date(value));
}

export function ago(value: string | Date): string {
  const s = Math.max(0, (Date.now() - new Date(value).getTime()) / 1000);
  if (s < 120) return "nu";
  if (s < 3600) return `${Math.round(s / 60)} min geleden`;
  if (s < 86400) return `${Math.round(s / 3600)} uur geleden`;
  const d = Math.round(s / 86400);
  return d === 1 ? "gisteren" : `${d} dagen geleden`;
}

export function daysLeft(value: string | null): number {
  if (!value) return 0;
  return Math.max(0, Math.ceil((new Date(value).getTime() - Date.now()) / 86400000));
}
