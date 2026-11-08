import Link from "next/link";
import { q } from "@/lib/db";
import { date, euro, PLAN_LABEL, type Plan } from "@/lib/devices";
import styles from "../admin.module.css";

export default async function Betalingen() {
  const rows = await q<{ id: number; device_code: string; amount_cents: number; plan: Plan; method: string; created_at: string }>(
    "SELECT * FROM payments ORDER BY created_at DESC LIMIT 300",
  );
  const total = rows.reduce((a, r) => a + r.amount_cents, 0);
  return (
    <>
      <div className={styles.head}>
        <h1>Betalingen</h1>
        <span className="muted">{rows.length} betalingen · {euro(total)}</span>
      </div>
      <div className={styles.tableBox}>
        <table className={styles.table}>
          <thead><tr><th>Datum</th><th>Apparaat</th><th>Abonnement</th><th>Via</th><th style={{ textAlign: "right" }}>Bedrag</th></tr></thead>
          <tbody>
            {rows.length === 0 && <tr><td colSpan={5} className="muted">Nog geen betalingen. Registreer er een bij een apparaat.</td></tr>}
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{date(r.created_at)}</td>
                <td><Link className={`${styles.rowLink} mono`} href={`/admin/apparaten?code=${r.device_code}`}>{r.device_code}</Link></td>
                <td>{PLAN_LABEL[r.plan] ?? r.plan}</td>
                <td>{r.method}</td>
                <td style={{ textAlign: "right", color: "var(--text)" }}>{euro(r.amount_cents)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
