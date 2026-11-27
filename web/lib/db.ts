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
  `CREATE TABLE IF NOT EXISTS meta_cache (
     key           TEXT PRIMARY KEY,
     data          TEXT,
     fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now()
   )`,
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
