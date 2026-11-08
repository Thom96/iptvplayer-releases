import Link from "next/link";
import { q } from "@/lib/db";
import {
  ago, date, DEVICE_COLUMNS, EFFECTIVE_STATUS, findByCode, getSettings, PLAN_LABEL, STATUS_LABEL,
  type Device, type Status,
} from "@/lib/devices";
import { addDevice, extendYear, linkOld, makeFree, registerPayment, saveDevice, toggleBlock } from "../../actions";
import styles from "../admin.module.css";

type Search = Promise<{ q?: string; status?: string; code?: string; ok?: string; fout?: string; nieuw?: string }>;
const FILTERS: (Status | "alle")[] = ["alle", "active", "free", "trial", "expired", "blocked"];

function isoDay(v: string | null): string {
  if (!v) return "";
  return new Date(v).toLocaleDateString("sv-SE", { timeZone: "Europe/Amsterdam" });
}

export default async function Apparaten({ searchParams }: { searchParams: Search }) {
  const sp = await searchParams;
  const search = (sp.q ?? "").trim();
  const filter = FILTERS.includes(sp.status as Status) ? (sp.status as Status) : "alle";

  const where: string[] = [];
  const params: unknown[] = [];
  if (search) {
    params.push(`%${search}%`);
    where.push(`(d.code ILIKE $${params.length} OR d.email ILIKE $${params.length} OR d.note ILIKE $${params.length})`);
  }
  if (filter !== "alle") {
    params.push(filter);
    where.push(`${EFFECTIVE_STATUS} = $${params.length}`);
  }
  const [rows, counts, selected, settings] = await Promise.all([
    q<Device>(
      `SELECT ${DEVICE_COLUMNS} FROM devices d ${where.length ? "WHERE " + where.join(" AND ") : ""}
       ORDER BY d.requested_at DESC NULLS LAST, d.last_seen DESC LIMIT 200`,
      params,
    ),
    q<{ s: Status; n: number }>(`SELECT ${EFFECTIVE_STATUS} AS s, count(*)::int AS n FROM devices d GROUP BY 1`),
    sp.code ? findByCode(sp.code) : Promise.resolve(null),
    getSettings(),
  ]);
  const count = Object.fromEntries(counts.map((c) => [c.s, c.n])) as Record<string, number>;
  const total = counts.reduce((a, c) => a + c.n, 0);
  const log = selected
    ? await q<{ text: string; created_at: string }>(
        "SELECT text, created_at FROM events WHERE device_code = $1 ORDER BY created_at DESC LIMIT 12",
        [selected.code],
      )
    : [];

  const qs = (extra: Record<string, string>) => {
    const u = new URLSearchParams();
    if (search) u.set("q", search);
    if (filter !== "alle") u.set("status", filter);
    for (const [k, v] of Object.entries(extra)) u.set(k, v);
    const s = u.toString();
    return s ? `?${s}` : "";
  };

  const suggested = selected?.requested_plan === "yearly" ? settings.priceYearlyCents : settings.priceLifetimeCents;

  return (
    <>
      <div className={styles.head}>
        <h1>Apparaten</h1>
        <Link href={`/admin/apparaten${qs({ nieuw: "1" })}`} className="btn primary">+ Apparaat toevoegen</Link>
      </div>

      {sp.ok && <p className="notice">{sp.ok}</p>}
      {sp.fout && <p className="error">{sp.fout}</p>}

      <form method="get" className="row">
        {filter !== "alle" && <input type="hidden" name="status" value={filter} />}
        <label htmlFor="zoek" style={{ position: "absolute", left: -9999 }}>Zoeken</label>
        <input id="zoek" name="q" defaultValue={search} placeholder="Zoek op code, e-mail of notitie" style={{ flex: "1 1 260px", width: "auto", background: "var(--surface)" }} />
        <button>Zoeken</button>
      </form>
      <nav aria-label="Filter op status" className={styles.filters}>
        {FILTERS.map((f) => (
          <Link key={f} href={`/admin/apparaten${(() => { const u = new URLSearchParams(); if (search) u.set("q", search); if (f !== "alle") u.set("status", f); const s = u.toString(); return s ? `?${s}` : ""; })()}`}
            className={f === filter ? `${styles.filter} ${styles.filterOn}` : styles.filter}>
            {f === "alle" ? "Alle" : STATUS_LABEL[f]} {f === "alle" ? total : count[f] ?? 0}
          </Link>
        ))}
      </nav>

      <div className={styles.split}>
        <div className={styles.tableBox}>
          <table className={styles.table}>
            <thead>
              <tr><th>Code</th><th>Klant / notitie</th><th>Status</th><th>Geldig tot</th><th>Laatst gezien</th></tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr><td colSpan={5} className="muted">Geen apparaten gevonden.</td></tr>
              )}
              {rows.map((d) => (
                <tr key={d.id} className={selected?.code === d.code ? styles.sel : undefined}>
                  <td><Link className={`${styles.rowLink} mono`} href={`/admin/apparaten${qs({ code: d.code })}`}>{d.code}</Link></td>
                  <td>{[d.email, d.note].filter(Boolean).join(" · ") || "—"}</td>
                  <td>
                    <span className="row" style={{ gap: 6 }}>
                      <span className={`chip ${d.effective}`}>{STATUS_LABEL[d.effective]}</span>
                      {d.requested_at && (d.effective === "trial" || d.effective === "expired") && <span className="chip request">Wacht op betaling</span>}
                    </span>
                  </td>
                  <td>
                    {d.effective === "trial" ? date(d.trial_ends)
                      : d.effective === "active" || d.effective === "free" ? (d.valid_until ? date(d.valid_until) : "levenslang")
                      : d.valid_until ? date(d.valid_until) : "—"}
                  </td>
                  <td className="muted">{ago(d.last_seen)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {sp.nieuw && !selected && (
          <aside className={styles.side} aria-label="Apparaat toevoegen">
            <h2 style={{ margin: 0, fontSize: 18 }}>Apparaat toevoegen</h2>
            <p className="small muted" style={{ margin: 0, lineHeight: 1.45 }}>
              Laat de persoon Gallo openen op de tv. De code staat dan op het scherm.
            </p>
            <form action={addDevice} className="stack">
              <div className="field"><label htmlFor="ncode">Apparaatcode</label><input id="ncode" name="code" required placeholder="XXXX-XXXX" className="mono" /></div>
              <div className="field">
                <label htmlFor="nstatus">Status</label>
                <select id="nstatus" name="status" defaultValue="free"><option value="free">Gratis</option><option value="active">Actief</option></select>
              </div>
              <div className="field"><label htmlFor="nuntil">Geldig tot (leeg = altijd)</label><input id="nuntil" name="until" type="date" /></div>
              <div className="field"><label htmlFor="nnote">Notitie</label><input id="nnote" name="note" placeholder="bijv. neef Jan" /></div>
              <button className="primary">Toevoegen</button>
            </form>
          </aside>
        )}

        {selected && (
          <aside className={styles.side} aria-label="Apparaat bewerken">
            <div className="stack" style={{ gap: 4 }}>
              <span className="small muted" style={{ textTransform: "uppercase", letterSpacing: 1 }}>Apparaat</span>
              <span className="mono" style={{ fontSize: 22, color: "var(--accent)" }}>{selected.code}</span>
              <span className="small muted">
                {[selected.model, selected.app_version && `Gallo ${selected.app_version}`, `geregistreerd ${date(selected.created_at)}`].filter(Boolean).join(" · ")}
              </span>
              <span className="row" style={{ gap: 6, marginTop: 6 }}>
                <span className={`chip ${selected.effective}`}>{STATUS_LABEL[selected.effective]}</span>
                {selected.plan && <span className="small muted">{PLAN_LABEL[selected.plan]}</span>}
                {selected.linked_from && <span className="small muted">was {selected.linked_from}</span>}
              </span>
            </div>

            {selected.requested_at && (selected.effective === "trial" || selected.effective === "expired") && (
              <p className="notice" style={{ margin: 0, color: "var(--warn)", background: "rgba(232,144,122,0.14)" }}>
                Vroeg {selected.requested_plan ? PLAN_LABEL[selected.requested_plan].toLowerCase() : "een abonnement"} aan, {ago(selected.requested_at)}.
              </p>
            )}

            <form action={registerPayment} className="stack" style={{ gap: 10, paddingBottom: 14, borderBottom: "1px solid var(--line)" }}>
              <input type="hidden" name="code" value={selected.code} />
              <b style={{ fontSize: 15 }}>Betaling registreren</b>
              <div className="row" style={{ flexWrap: "nowrap" }}>
                <div className="field" style={{ flex: 1 }}>
                  <label htmlFor="amount">Bedrag (€)</label>
                  <input id="amount" name="amount" inputMode="decimal" required defaultValue={suggested ? (suggested / 100).toFixed(2).replace(".", ",") : ""} />
                </div>
                <div className="field" style={{ flex: 1 }}>
                  <label htmlFor="pplan">Abonnement</label>
                  <select id="pplan" name="plan" defaultValue={selected.requested_plan ?? "lifetime"}>
                    <option value="lifetime">Levenslang</option><option value="yearly">Per jaar</option>
                  </select>
                </div>
              </div>
              <div className="field"><label htmlFor="method">Via</label><input id="method" name="method" defaultValue="Tikkie" /></div>
              <button className="primary">Betaald, zet aan</button>
            </form>

            <form action={saveDevice} className="stack">
              <input type="hidden" name="code" value={selected.code} />
              <div className="field">
                <label htmlFor="st">Status</label>
                <select id="st" name="status" defaultValue={selected.status}>
                  {(["active", "free", "trial", "expired", "blocked"] as Status[]).map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
                </select>
              </div>
              <div className="field">
                <label htmlFor="until">Geldig tot (leeg = altijd; bij proef: einde proef)</label>
                <input id="until" name="until" type="date" defaultValue={isoDay(selected.status === "trial" ? selected.trial_ends : selected.valid_until)} />
              </div>
              <div className="field"><label htmlFor="email">E-mail</label><input id="email" name="email" type="email" defaultValue={selected.email ?? ""} /></div>
              <div className="field"><label htmlFor="note">Notitie (alleen voor jou)</label><textarea id="note" name="note" rows={2} defaultValue={selected.note ?? ""} /></div>
              <button className="primary">Opslaan</button>
            </form>

            <div className={styles.actions}>
              <form action={extendYear}><input type="hidden" name="code" value={selected.code} /><button style={{ width: "100%" }}>+1 jaar</button></form>
              <form action={makeFree}><input type="hidden" name="code" value={selected.code} /><button style={{ width: "100%" }}>Gratis maken</button></form>
              <form action={toggleBlock} style={{ gridColumn: "span 2" }}>
                <input type="hidden" name="code" value={selected.code} />
                <button className="danger" style={{ width: "100%" }}>{selected.status === "blocked" ? "Blokkade opheffen" : "Blokkeren"}</button>
              </form>
            </div>

            <form action={linkOld} className="stack" style={{ gap: 8 }}>
              <input type="hidden" name="code" value={selected.code} />
              <label htmlFor="old" style={{ marginBottom: 0 }}>Koppel aan oude code (na herinstallatie)</label>
              <div className="row" style={{ flexWrap: "nowrap" }}>
                <input id="old" name="old" placeholder="XXXX-XXXX" className="mono" />
                <button>Koppel</button>
              </div>
            </form>

            <div className="stack" style={{ gap: 8, paddingTop: 12, borderTop: "1px solid var(--line)", fontSize: 13 }}>
              <span className="muted">Geschiedenis · laatst gezien {ago(selected.last_seen)}</span>
              {log.map((l, i) => (
                <div key={i} className="row" style={{ justifyContent: "space-between", flexWrap: "nowrap" }}>
                  <span className="soft">{l.text}</span><span className="muted" style={{ whiteSpace: "nowrap" }}>{date(l.created_at)}</span>
                </div>
              ))}
            </div>
          </aside>
        )}
      </div>
    </>
  );
}
