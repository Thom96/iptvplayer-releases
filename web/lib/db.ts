import { neon, type NeonQueryFunction } from "@neondatabase/serverless";

let client: NeonQueryFunction<false, false> | null = null;
let schemaReady: Promise<void> | null = null;

function connectionString(): string {
  const url = process.env.DATABASE_URL || process.env.POSTGRES_URL;
  if (!url) throw new Error("DATABASE_URL ontbreekt: koppel een Neon-database aan dit Vercel-project.");
  return url;
}

function raw(): NeonQueryFunction<false, false> {
  if (!client) client = neon(connectionString());
  return client;
}

export function hasDatabase(): boolean {
  return Boolean(process.env.DATABASE_URL || process.env.POSTGRES_URL);
}

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS devices (
     id            TEXT PRIMARY KEY,
     code          TEXT NOT NULL UNIQUE,
     status        TEXT NOT NULL DEFAULT 'trial',
     plan          TEXT,
     trial_ends    TIMESTAMPTZ NOT NULL,
     valid_until   TIMESTAMPTZ,
     email         TEXT,
     note          TEXT,
     requested_plan TEXT,
     requested_at  TIMESTAMPTZ,
     app_version   TEXT,
     model         TEXT,
     linked_from   TEXT,
     created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
     last_seen     TIMESTAMPTZ NOT NULL DEFAULT now()
   )`,
  `CREATE TABLE IF NOT EXISTS payments (
     id            SERIAL PRIMARY KEY,
     device_code   TEXT NOT NULL,
     amount_cents  INTEGER NOT NULL,
     plan          TEXT NOT NULL,
     method        TEXT NOT NULL DEFAULT 'handmatig',
     note          TEXT,
     created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
   )`,
  `CREATE TABLE IF NOT EXISTS events (
     id            SERIAL PRIMARY KEY,
     device_code   TEXT,
     type          TEXT NOT NULL,
     text          TEXT NOT NULL,
     created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
   )`,
  `CREATE TABLE IF NOT EXISTS seen_days (
     device_code   TEXT NOT NULL,
     day           DATE NOT NULL,
     PRIMARY KEY (device_code, day)
   )`,
  `CREATE TABLE IF NOT EXISTS settings (
     key           TEXT PRIMARY KEY,
     value         TEXT NOT NULL
   )`,
  `CREATE INDEX IF NOT EXISTS devices_created ON devices (created_at)`,
  `CREATE INDEX IF NOT EXISTS payments_created ON payments (created_at)`,
  `CREATE INDEX IF NOT EXISTS events_created ON events (created_at)`,
];

async function ensureSchema(): Promise<void> {
  if (!schemaReady) {
    schemaReady = (async () => {
      for (const stmt of SCHEMA) await raw().query(stmt);
    })().catch((e) => {
      schemaReady = null;
      throw e;
    });
  }
  return schemaReady;
}

/** Voert een query uit met $1, $2… als parameters. */
export async function q<T = Record<string, unknown>>(text: string, params: unknown[] = []): Promise<T[]> {
  await ensureSchema();
  return (await raw().query(text, params)) as T[];
}

export async function one<T = Record<string, unknown>>(text: string, params: unknown[] = []): Promise<T | null> {
  const rows = await q<T>(text, params);
  return rows[0] ?? null;
}
