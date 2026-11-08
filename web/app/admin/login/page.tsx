import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { adminConfigured, checkPassword, isAdmin, startSession } from "@/lib/auth";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Gallo admin" };

async function login(form: FormData) {
  "use server";
  // Even wachten maakt wachtwoorden raden traag.
  await new Promise((r) => setTimeout(r, 600));
  if (!checkPassword(String(form.get("password") ?? ""))) redirect("/admin/login?fout=1");
  await startSession();
  redirect("/admin");
}

export default async function Login({ searchParams }: { searchParams: Promise<{ fout?: string }> }) {
  if (await isAdmin()) redirect("/admin");
  const { fout } = await searchParams;
  return (
    <main style={{ maxWidth: 360, margin: "12vh auto", padding: 20 }} className="stack">
      <div className="logo">Gall<span>o</span> <span className="small muted" style={{ fontFamily: "Outfit" }}>admin</span></div>
      {!adminConfigured() ? (
        <p className="error">
          Er is nog geen wachtwoord ingesteld. Zet in Vercel bij dit project de omgevingsvariabele
          <b> ADMIN_PASSWORD</b> en deploy opnieuw.
        </p>
      ) : (
        <form action={login} className="stack">
          <div className="field">
            <label htmlFor="password">Wachtwoord</label>
            <input id="password" name="password" type="password" autoFocus required autoComplete="current-password" />
          </div>
          {fout && <p className="error">Dat wachtwoord klopt niet.</p>}
          <button className="primary">Inloggen</button>
        </form>
      )}
    </main>
  );
}
