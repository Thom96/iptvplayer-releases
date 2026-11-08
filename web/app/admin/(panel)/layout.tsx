import type { Metadata } from "next";
import { endSession, requireAdmin } from "@/lib/auth";
import { hasDatabase } from "@/lib/db";
import { redirect } from "next/navigation";
import Nav from "./nav";
import styles from "./admin.module.css";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Gallo admin" };

async function logout() {
  "use server";
  await endSession();
  redirect("/admin/login");
}

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  await requireAdmin();
  return (
    <div className={styles.shell}>
      <nav aria-label="Admin" className={styles.nav}>
        <div className="logo" style={{ padding: "0 10px 18px" }}>
          Gall<span>o</span> <span className="small muted" style={{ fontFamily: "Outfit" }}>admin</span>
        </div>
        <Nav />
        <form action={logout} style={{ marginTop: "auto" }}>
          <button className={styles.logout}>Uitloggen</button>
        </form>
      </nav>
      <main className={styles.main}>
        {hasDatabase() ? children : (
          <p className="error">
            Er is nog geen database gekoppeld. Voeg in Vercel bij dit project onder Storage een Neon-database toe en deploy opnieuw.
          </p>
        )}
      </main>
    </div>
  );
}
