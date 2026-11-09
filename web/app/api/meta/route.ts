import { NextResponse } from "next/server";
import { hasTmdbKey, lookup, type Kind } from "@/lib/tmdb";

export const dynamic = "force-dynamic";

const LANG = /^[a-z]{2}(-[A-Z]{2})?$/;

/**
 * Details of a movie or series from TMDB, for the detail screens in the app.
 * In:  ?kind=movie|tv&tmdb=<id, optional>&title=<title>&year=<optional>&lang=nl
 * Out: { found: true, ...Meta } or { found: false }. Without a TMDB key: 503, and the app keeps the provider's data.
 */
export async function GET(req: Request) {
  if (!hasTmdbKey()) return NextResponse.json({ error: "no_tmdb_key" }, { status: 503 });
  const p = new URL(req.url).searchParams;
  const kind = p.get("kind");
  if (kind !== "movie" && kind !== "tv") return NextResponse.json({ error: "bad_kind" }, { status: 400 });
  const tmdb = Number(p.get("tmdb")) || null;
  const title = (p.get("title") ?? "").slice(0, 200);
  const year = Number(p.get("year")) || null;
  const lang = p.get("lang") ?? "en";
  if (!LANG.test(lang)) return NextResponse.json({ error: "bad_lang" }, { status: 400 });
  if (!tmdb && !title.trim()) return NextResponse.json({ error: "no_title" }, { status: 400 });
  try {
    const meta = await lookup(kind as Kind, tmdb, title, year && year > 1880 && year < 2100 ? year : null, lang);
    return NextResponse.json(meta ? { found: true, ...meta } : { found: false });
  } catch {
    return NextResponse.json({ error: "tmdb_unavailable" }, { status: 502 });
  }
}
