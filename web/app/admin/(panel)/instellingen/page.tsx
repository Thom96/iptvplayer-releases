import { redirect } from "next/navigation";
import { requireAdmin } from "@/lib/auth";
import { getSettings, saveSettings } from "@/lib/devices";
import styles from "../admin.module.css";

function cents(v: FormDataEntryValue | null): number {
  const n = Math.round(Number(String(v ?? "").replace(",", ".")) * 100);
  return Number.isFinite(n) && n >= 0 ? n : 0;
}

async function save(form: FormData) {
  "use server";
  await requireAdmin();
  const days = Math.min(90, Math.max(0, Math.round(Number(form.get("trialDays")) || 0)));
  await saveSettings({
    trialDays: days,
    priceLifetimeCents: cents(form.get("priceLifetime")),
    priceYearlyCents: cents(form.get("priceYearly")),
    paymentInfo: String(form.get("paymentInfo") ?? "").trim().slice(0, 1000),
  });
  redirect("/admin/instellingen?ok=1");
}

const fmt = (c: number) => (c ? (c / 100).toFixed(2).replace(".", ",") : "");

export default async function Instellingen({ searchParams }: { searchParams: Promise<{ ok?: string }> }) {
  const { ok } = await searchParams;
  const s = await getSettings();
  return (
    <>
      <div className={styles.head}><h1>Instellingen</h1></div>
      {ok && <p className="notice">Opgeslagen.</p>}
      <form action={save} className={`${styles.panel} stack`} style={{ maxWidth: 520 }}>
        <div className="field">
          <label htmlFor="trialDays">Proefperiode voor nieuwe apparaten (dagen)</label>
          <input id="trialDays" name="trialDays" type="number" min={0} max={90} defaultValue={s.trialDays} />
        </div>
        <div className="row" style={{ flexWrap: "nowrap" }}>
          <div className="field" style={{ flex: 1 }}>
            <label htmlFor="priceLifetime">Prijs levenslang (€)</label>
            <input id="priceLifetime" name="priceLifetime" inputMode="decimal" defaultValue={fmt(s.priceLifetimeCents)} />
          </div>
          <div className="field" style={{ flex: 1 }}>
            <label htmlFor="priceYearly">Prijs per jaar (€)</label>
            <input id="priceYearly" name="priceYearly" inputMode="decimal" defaultValue={fmt(s.priceYearlyCents)} />
          </div>
        </div>
        <div className="field">
          <label htmlFor="paymentInfo">Tekst na een aanvraag op de website</label>
          <textarea id="paymentInfo" name="paymentInfo" rows={4} defaultValue={s.paymentInfo}
            placeholder="Leeg = standaardtekst: je krijgt een betaalverzoek per e-mail…" />
        </div>
        <button className="primary">Opslaan</button>
      </form>
    </>
  );
}
