import { NextResponse } from "next/server";
import { hasDatabase } from "@/lib/db";
import { checkIn, daysLeft } from "@/lib/devices";

export const dynamic = "force-dynamic";

const ID = /^[A-Za-z0-9-]{16,64}$/;

function clip(v: unknown, max: number): string | null {
  return typeof v === "string" && v.trim() ? v.trim().slice(0, max) : null;
}

/**
 * De app meldt zich hier bij elke start (en af en toe tussendoor).
 * In:  { "deviceId": "<uuid die de app zelf bewaart>", "appVersion": "1.14", "model": "Chromecast" }
 * Uit: de apparaatcode, de status en tot wanneer die geldt.
 */
export async function POST(req: Request) {
  if (!hasDatabase()) return NextResponse.json({ error: "no_database" }, { status: 503 });
  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: "bad_json" }, { status: 400 });
  }
  const id = typeof body.deviceId === "string" ? body.deviceId : "";
  if (!ID.test(id)) return NextResponse.json({ error: "bad_device_id" }, { status: 400 });

  const d = await checkIn(id, clip(body.appVersion, 32), clip(body.model, 80));
  const origin = process.env.PUBLIC_URL || new URL(req.url).origin;
  const until = d.effective === "trial" ? d.trial_ends : d.valid_until;
  return NextResponse.json({
    code: d.code,
    status: d.effective,
    plan: d.plan,
    validUntil: until ? new Date(until).toISOString() : null,
    daysLeft: until ? daysLeft(until) : null,
    activateUrl: `${origin}/activeren?code=${encodeURIComponent(d.code)}`,
    serverTime: new Date().toISOString(),
  });
}
