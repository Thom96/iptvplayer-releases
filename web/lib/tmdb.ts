/**
 * Movie and series details from TMDB (themoviedb.org), for the detail screens in the app.
 * The key lives only here, in the Vercel environment variable TMDB_API_KEY (v3 API key) or
 * TMDB_READ_TOKEN (v4 read access token). Results are cached in `meta_cache` so each title is
 * fetched from TMDB once for all users.
 */
import { hasDatabase, one, q } from "@/lib/db";

export type Kind = "movie" | "tv";

export type Meta = {
  tmdbId: number;
  title: string;
  overview: string | null;
  poster: string | null;
  backdrop: string | null;
  rating: number | null;
  votes: number;
  year: number | null;
  genres: string[];
};

const API = "https://api.themoviedb.org/3";
const IMG = "https://image.tmdb.org/t/p";
const FOUND_TTL_DAYS = 7;
const MISSING_TTL_DAYS = 1;

export function hasTmdbKey(): boolean {
  return Boolean(process.env.TMDB_API_KEY || process.env.TMDB_READ_TOKEN);
}

async function tmdb(path: string, params: Record<string, string>): Promise<Record<string, unknown> | null> {
  const url = new URL(API + path);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  const headers: Record<string, string> = { accept: "application/json" };
  if (process.env.TMDB_READ_TOKEN) headers.authorization = `Bearer ${process.env.TMDB_READ_TOKEN}`;
  else url.searchParams.set("api_key", process.env.TMDB_API_KEY ?? "");
  const res = await fetch(url, { headers, cache: "no-store" });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`TMDB ${res.status}`);
  return (await res.json()) as Record<string, unknown>;
}

/** "The Matrix (1999) [4K]" → "The Matrix". The app already strips provider prefixes like "NL - ". */
export function cleanTitle(title: string): string {
  return title
    .replace(/\[[^\]]*\]/g, " ")
    .replace(/\((19|20)\d{2}\)/g, " ")
    .replace(/\b(4K|UHD|FHD|HD|SD|HEVC|H\.?265|MULTI|SUB|DUBBED)\b/gi, " ")
    .replace(/\s+/g, " ")
    .trim();
}

async function search(kind: Kind, title: string, year: number | null, lang: string): Promise<number | null> {
  const query = cleanTitle(title);
  if (!query) return null;
  const tries: Record<string, string>[] = [];
  const yearKey = kind === "movie" ? "year" : "first_air_date_year";
  if (year) tries.push({ query, language: lang, [yearKey]: String(year) });
  tries.push({ query, language: lang });
  for (const params of tries) {
    const r = await tmdb(`/search/${kind}`, params);
    const first = (r?.results as { id?: number }[] | undefined)?.[0];
    if (first?.id) return first.id;
  }
  return null;
}

function str(v: unknown): string | null {
  return typeof v === "string" && v.trim() ? v.trim() : null;
}

async function details(kind: Kind, id: number, lang: string): Promise<Meta | null> {
  const d = await tmdb(`/${kind}/${id}`, { language: lang });
  if (!d) return null;
  let overview = str(d.overview);
  // Not every title has a description in every language: fall back to English.
  if (!overview && !lang.startsWith("en")) overview = str((await tmdb(`/${kind}/${id}`, { language: "en-US" }))?.overview);
  const date = str(kind === "movie" ? d.release_date : d.first_air_date);
  const rating = typeof d.vote_average === "number" && d.vote_average > 0 ? Math.round(d.vote_average * 10) / 10 : null;
  const poster = str(d.poster_path);
  const backdrop = str(d.backdrop_path);
  return {
    tmdbId: id,
    title: str(kind === "movie" ? d.title : d.name) ?? "",
    overview,
    poster: poster ? `${IMG}/w500${poster}` : null,
    backdrop: backdrop ? `${IMG}/w1280${backdrop}` : null,
    rating,
    votes: typeof d.vote_count === "number" ? d.vote_count : 0,
    year: date ? Number(date.slice(0, 4)) || null : null,
    genres: Array.isArray(d.genres) ? (d.genres as { name?: string }[]).map((g) => g.name ?? "").filter(Boolean) : [],
  };
}

/** Looks a title up (by TMDB id if the provider gave one, otherwise by title and year), with caching. */
export async function lookup(kind: Kind, tmdbId: number | null, title: string, year: number | null, lang: string): Promise<Meta | null> {
  const key = tmdbId ? `${kind}:id:${tmdbId}:${lang}` : `${kind}:q:${cleanTitle(title).toLowerCase()}:${year ?? ""}:${lang}`;
  if (hasDatabase()) {
    const row = await one<{ data: string | null; fetched_at: string }>("SELECT data, fetched_at FROM meta_cache WHERE key = $1", [key]);
    if (row) {
      const ageDays = (Date.now() - new Date(row.fetched_at).getTime()) / 86_400_000;
      if (ageDays < (row.data ? FOUND_TTL_DAYS : MISSING_TTL_DAYS)) return row.data ? (JSON.parse(row.data) as Meta) : null;
    }
  }
  let id = tmdbId;
  let meta = id ? await details(kind, id, lang) : null;
  if (!meta) {
    id = await search(kind, title, year, lang);
    meta = id ? await details(kind, id, lang) : null;
  }
  if (hasDatabase()) {
    await q(
      `INSERT INTO meta_cache (key, data, fetched_at) VALUES ($1, $2, now())
       ON CONFLICT (key) DO UPDATE SET data = EXCLUDED.data, fetched_at = now()`,
      [key, meta ? JSON.stringify(meta) : null],
    );
  }
  return meta;
}
