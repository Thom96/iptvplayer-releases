import Link from "next/link";
import { one, q } from "@/lib/db";
import { ago, EFFECTIVE_STATUS, euro, PLAN_LABEL, STATUS_LABEL, type Plan, type Status } from "@/lib/devices";
import styles from "./admin.module.css";

const PERIODS = {
  "7d": { label: "7 dagen", unit: "day", span: "6 days", interval: "7 days", buckets: "per dag", months: 6 },
  "30d": { label: "30 dagen", unit: "day", span: "29 days", interval: "30 days", buckets: "per dag", months: 6 },
  "12m": { label: "12 maanden", unit: "week", span: "51 weeks", interval: "1 year", buckets: "per week", months: 12 },
} as const;
type PeriodKey = keyof typeof PERIODS;

const STATUS_COLOR: Record<Status, string> = {
  active: "var(--accent)",
  free: "var(--ok)",
  trial: "var(--trial)",
  expired: "var(--expired)",
  blocked: "var(--live)",
};

type Point = { b: string; n: number };

function Bars({ points, label, money }: { points: Point[]; label: string; money?: boolean }) {
  const max = Math.max(1, ...points.map((p) => p.n));
  return (
    <div className={styles.bars} role="img" aria-label={label}>
      {points.map((p) => (
        <div key={p.b} className={styles.bar} title={`${new Date(p.b).toLocaleDateString("nl-NL")}: ${money ? euro(p.n) : p.n}`}
          style={{ height: `${(p.n / max) * 100}%` }} />
      ))}
    </div>
  );
}

function Delta({ now, before, money }: { now: number; before: number; money?: boolean }) {
  const diff = now - before;
  if (diff === 0) return <span className="small muted">gelijk aan vorige periode</span>;
  const text = money ? euro(Math.abs(diff)) : Math.abs(diff);
  return (
    <span className="small" style={{ color: diff > 0 ? "var(--ok)" : "var(--warn)" }}>
      {diff > 0 ? "+" : "−"}{text} t.o.v. vorige periode
    </span>
  );
}

export default async function Dashboard({ searchParams }: { searchParams: Promise<{ p?: string }> }) {
  const { p } = await searchParams;
  const key: PeriodKey = p && p in PERIODS ? (p as PeriodKey) : "30d";
  const P = PERIODS[key];

  const [kpi, newSeries, activeSeries, revenue, statuses, conversion, requests, activity] = await Promise.all([
    one<Record<string, number>>(
      `SELECT
         (SELECT count(*) FROM devices d WHERE ${EFFECTIVE_STATUS} IN ('active','free'))::int AS subs,
         (SELECT count(*) FROM devices d WHERE ${EFFECTIVE_STATUS} = 'free')::int AS free,
         (SELECT count(*) FROM devices WHERE created_at >= now() - $1::interval)::int AS new_now,
         (SELECT count(*) FROM devices WHERE created_at >= now() - 2 * $1::interval AND created_at < now() - $1::interval)::int AS new_before,
         (SELECT count(DISTINCT device_code) FROM seen_days WHERE day > CURRENT_DATE - $1::interval)::int AS seen_now,
         (SELECT count(DISTINCT device_code) FROM seen_days WHERE day > CURRENT_DATE - 2 * $1::interval AND day <= CURRENT_DATE - $1::interval)::int AS seen_before,
         (SELECT COALESCE(sum(amount_cents),0) FROM payments WHERE created_at >= now() - $1::interval)::int AS rev_now,
         (SELECT COALESCE(sum(amount_cents),0) FROM payments WHERE created_at >= now() - 2 * $1::interval AND created_at < now() - $1::interval)::int AS rev_before,
         (SELECT count(*) FROM devices d WHERE ${EFFECTIVE_STATUS} = 'trial')::int AS trial`,
      [P.interval],
    ),
    q<Point>(
      `WITH b AS (SELECT generate_series(date_trunc($1, now() - $2::interval), date_trunc($1, now()), ('1 ' || $1)::interval) AS b)
       SELECT b.b, count(d.id)::int AS n FROM b LEFT JOIN devices d ON date_trunc($1, d.created_at) = b.b GROUP BY b.b ORDER BY b.b`,
      [P.unit, P.span],
    ),
    q<Point>(
      `WITH b AS (SELECT generate_series(date_trunc($1, now() - $2::interval), date_trunc($1, now()), ('1 ' || $1)::interval) AS b)
       SELECT b.b, count(DISTINCT s.device_code)::int AS n FROM b LEFT JOIN seen_days s ON date_trunc($1, s.day::timestamptz) = b.b
       GROUP BY b.b ORDER BY b.b`,
      [P.unit, P.span],
    ),
    q<Point>(
      `WITH b AS (SELECT generate_series(date_trunc('month', now()) - make_interval(months => $1 - 1), date_trunc('month', now()), '1 month') AS b)
       SELECT b.b, COALESCE(sum(p.amount_cents),0)::int AS n FROM b LEFT JOIN payments p ON date_trunc('month', p.created_at) = b.b
       GROUP BY b.b ORDER BY b.b`,
      [P.months],
    ),
    q<{ s: Status; n: number }>(`SELECT ${EFFECTIVE_STATUS} AS s, count(*)::int AS n FROM devices d GROUP BY 1`),
    one<{ paid: number; done: number; expiring: number }>(
      `SELECT
         (SELECT count(DISTINCT device_code) FROM payments)::int AS paid,
         (SELECT count(*) FROM devices d WHERE ${EFFECTIVE_STATUS} <> 'trial')::int AS done,
         (SELECT count(*) FROM devices d WHERE ${EFFECTIVE_STATUS} = 'active' AND d.valid_until BETWEEN now() AND now() + interval '30 days')::int AS expiring`,
    ),
    q<{ code: string; requested_plan: Plan; email: string; requested_at: string }>(
      `SELECT d.code, d.requested_plan, d.email, d.requested_at FROM devices d
       WHERE d.requested_at IS NOT NULL AND ${EFFECTIVE_STATUS} IN ('trial','expired')
       ORDER BY d.requested_at DESC LIMIT 8`,
    ),
    q<{ device_code: string | null; text: string; created_at: string }>(
      "SELECT device_code, text, created_at FROM events ORDER BY created_at DESC LIMIT 8",
    ),
  ]);

  const k = kpi ?? {};
  const counts = Object.fromEntries(statuses.map((s) => [s.s, s.n])) as Partial<Record<Status, number>>;
  const order: Status[] = ["active", "free", "trial", "expired", "blocked"];
  const totalDevices = order.reduce((a, s) => a + (counts[s] ?? 0), 0);
  const newTotal = newSeries.reduce((a, x) => a + x.n, 0);
  const revYear = revenue.reduce((a, x) => a + x.n, 0);
  const conv = conversion && conversion.done > 0 ? Math.round((conversion.paid / conversion.done) * 100) : null;

  return (
    <>
      <div className={styles.head}>
        <div className="stack" style={{ gap: 4 }}>
          <h1>Dashboard</h1>
          <span className="small muted">{totalDevices} apparaten in totaal</span>
        </div>
        <nav aria-label="Periode" className={styles.pills}>
          {(Object.keys(PERIODS) as PeriodKey[]).map((id) => (
            <Link key={id} href={`/admin?p=${id}`} className={id === key ? `${styles.pill} ${styles.pillOn}` : styles.pill}>
              {PERIODS[id].label}
            </Link>
          ))}
        </nav>
      </div>

      <section className={styles.kpis}>
        <div className={styles.kpi}>
          <span className="small muted">Actieve abonnementen</span>
          <span className={styles.kpiValue}>{k.subs ?? 0}</span>
          <span className="small muted">waarvan {k.free ?? 0} gratis · {k.trial ?? 0} in proef</span>
        </div>
        <div className={styles.kpi}>
          <span className="small muted">Nieuwe apparaten</span>
          <span className={styles.kpiValue}>{k.new_now ?? 0}</span>
          <Delta now={k.new_now ?? 0} before={k.new_before ?? 0} />
        </div>
        <div className={styles.kpi}>
          <span className="small muted">Actieve gebruikers</span>
          <span className={styles.kpiValue}>{k.seen_now ?? 0}</span>
          <Delta now={k.seen_now ?? 0} before={k.seen_before ?? 0} />
        </div>
        <div className={styles.kpi}>
          <span className="small muted">Omzet</span>
          <span className={styles.kpiValue}>{euro(k.rev_now ?? 0)}</span>
          <Delta now={k.rev_now ?? 0} before={k.rev_before ?? 0} money />
        </div>
      </section>

      <section className={styles.grid2}>
        <div className={styles.panel}>
          <div className={styles.panelHead}>
            <h2>Nieuwe apparaten {P.buckets}</h2>
            <span className="small muted">{newTotal} in {P.label}</span>
          </div>
          <Bars points={newSeries} label={`Nieuwe apparaten ${P.buckets}`} />
          <div className={styles.axis}><span>{P.label} geleden</span><span>nu</span></div>
        </div>
        <div className={styles.panel}>
          <div className={styles.panelHead}>
            <h2>Actieve gebruikers {P.buckets}</h2>
            <span className="small muted">apparaten die Gallo openden</span>
          </div>
          <Bars points={activeSeries} label={`Actieve gebruikers ${P.buckets}`} />
          <div className={styles.axis}><span>{P.label} geleden</span><span>nu</span></div>
        </div>
      </section>

      <section className={styles.grid2}>
        <div className={styles.panel}>
          <div className={styles.panelHead}>
            <h2>Omzet per maand</h2>
            <span className="small muted">{euro(revYear)} in {P.months} maanden</span>
          </div>
          <div className={styles.bars} style={{ gap: 8 }} role="img" aria-label="Omzet per maand">
            {revenue.map((r, i) => {
              const max = Math.max(1, ...revenue.map((x) => x.n));
              return (
                <div key={r.b} className={styles.barCol}>
                  {r.n > 0 && <span className="small soft" style={{ fontSize: 11 }}>{euro(r.n)}</span>}
                  <div className={styles.bar} style={{ width: "100%", height: `${(r.n / max) * 80}%`, background: i === revenue.length - 1 ? "var(--accent)" : "#7f8f8f" }} />
                </div>
              );
            })}
          </div>
          <div className={styles.axis}>
            {revenue.map((r) => (
              <span key={r.b} style={{ flex: "1 1 0", textAlign: "center" }}>
                {new Date(r.b).toLocaleDateString("nl-NL", { month: "short" }).replace(".", "")}
              </span>
            ))}
          </div>
        </div>

        <div className={styles.panel}>
          <h2>Apparaten per status</h2>
          <div className={styles.stackBar} role="img" aria-label="Verdeling van apparaten per status">
            {order.filter((s) => counts[s]).map((s) => (
              <div key={s} style={{ flex: `${counts[s]} 1 0`, background: STATUS_COLOR[s] }} />
            ))}
          </div>
          <div className="stack" style={{ gap: 10 }}>
            {order.map((s) => (
              <Link key={s} href={`/admin/apparaten?status=${s}`} className={styles.legend} style={{ color: "inherit", textDecoration: "none" }}>
                <span className={styles.swatch} style={{ background: STATUS_COLOR[s] }} />
                <span className="soft" style={{ flexGrow: 1 }}>{STATUS_LABEL[s]}</span>
                <b>{counts[s] ?? 0}</b>
              </Link>
            ))}
          </div>
          <div className="row" style={{ gap: 28, paddingTop: 8, borderTop: "1px solid var(--line)" }}>
            <div className="stack" style={{ gap: 2 }}>
              <span className="small muted">Proef → betaald</span>
              <b style={{ fontSize: 22 }}>{conv === null ? "—" : `${conv}%`}</b>
            </div>
            <div className="stack" style={{ gap: 2 }}>
              <span className="small muted">Verloopt binnen 30 dagen</span>
              <b style={{ fontSize: 22 }}>{conversion?.expiring ?? 0}</b>
            </div>
          </div>
        </div>
      </section>

      <section className={styles.grid2}>
        <div className={styles.panel}>
          <div className={styles.panelHead}>
            <h2>Wacht op betaling</h2>
            <span className="small muted">aanvragen via de website</span>
          </div>
          {requests.length === 0 && <span className="small muted">Geen openstaande aanvragen.</span>}
          {requests.map((r) => (
            <Link key={r.code} href={`/admin/apparaten?code=${r.code}`} className={styles.activity} style={{ color: "inherit", textDecoration: "none" }}>
              <span className="mono" style={{ color: "var(--accent)", minWidth: 92 }}>{r.code}</span>
              <span className="soft" style={{ flexGrow: 1, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis" }}>
                {PLAN_LABEL[r.requested_plan]} · {r.email}
              </span>
              <span className="small muted">{ago(r.requested_at)}</span>
            </Link>
          ))}
        </div>
        <div className={styles.panel}>
          <div className={styles.panelHead}>
            <h2>Laatste activiteit</h2>
            <Link href="/admin/apparaten" className="small">Alle apparaten</Link>
          </div>
          {activity.length === 0 && <span className="small muted">Nog niets gebeurd.</span>}
          {activity.map((a, i) => (
            <div key={i} className={styles.activity}>
              <span className="mono" style={{ color: "var(--accent)", minWidth: 92 }}>{a.device_code ?? "—"}</span>
              <span className="soft" style={{ flexGrow: 1 }}>{a.text}</span>
              <span className="small muted">{ago(a.created_at)}</span>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
