import type { Metadata } from "next";
import { hasDatabase } from "@/lib/db";
import { date, daysLeft, euro, findByCode, getSettings, normalizeCode } from "@/lib/devices";
import { requestActivation } from "./actions";
import styles from "./activeren.module.css";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Gallo activeren" };

type Search = Promise<{ code?: string; klaar?: string; fout?: string }>;

export default async function Activeren({ searchParams }: { searchParams: Search }) {
  const { code: rawCode, klaar, fout } = await searchParams;
  const code = rawCode ? normalizeCode(rawCode) : "";
  const ready = hasDatabase();
  const device = ready && code ? await findByCode(code) : null;
  const settings = ready ? await getSettings() : null;

  return (
    <main className={styles.page}>
      <a className="logo" href="/activeren">Gall<span>o</span></a>

      <div className="stack" style={{ gap: 6 }}>
        <h1 className={styles.title}>Activeer je tv</h1>
        <p className={styles.lead}>De code staat op je tv-scherm. Kwam je via de QR-code, dan is hij al ingevuld.</p>
      </div>

      {!ready && <p className="error">De server is nog niet klaar. Probeer het later nog eens.</p>}

      {klaar && device ? (
        <section className="stack">
          <p className="notice">Bedankt! Je aanvraag voor <b className="mono">{device.code}</b> is binnen.</p>
          <p className="soft" style={{ lineHeight: 1.5, whiteSpace: "pre-line" }}>
            {settings?.paymentInfo ||
              "Je krijgt een betaalverzoek op je e-mailadres. Zodra het betaald is, zetten we je tv aan. Gallo op de tv gaat dan vanzelf verder."}
          </p>
        </section>
      ) : (
        <>
          <form method="get" className="field">
            <label htmlFor="code">Apparaatcode</label>
            <input id="code" name="code" defaultValue={code} placeholder="XXXX-XXXX" autoComplete="off"
              autoCapitalize="characters" className={styles.code} />
            {code && !device && ready && <p className="error" style={{ marginTop: 8 }}>Deze code kennen we niet. Kijk nog even op je tv.</p>}
            {!device && <button className="primary" style={{ marginTop: 12 }}>Zoeken</button>}
          </form>

          {device && (device.effective === "active" || device.effective === "free") && (
            <p className="notice">
              Deze tv is al actief{device.valid_until ? ` tot ${date(device.valid_until)}` : ""}. Veel kijkplezier!
            </p>
          )}

          {device && device.effective === "blocked" && (
            <p className="error">Deze tv is geblokkeerd. Neem contact op als je denkt dat dit niet klopt.</p>
          )}

          {device && (device.effective === "trial" || device.effective === "expired") && settings && (
            <form action={requestActivation} className="stack">
              <input type="hidden" name="code" value={device.code} />
              <p className={styles.found}>
                {device.effective === "trial"
                  ? `Apparaat gevonden · proef nog ${daysLeft(device.trial_ends)} dagen`
                  : "Apparaat gevonden · proefperiode voorbij"}
              </p>

              <fieldset className={styles.plans}>
                <legend className="small muted">Kies een abonnement</legend>
                <label className={styles.plan}>
                  <input type="radio" name="plan" value="lifetime" defaultChecked={device.requested_plan !== "yearly"} />
                  <span className={styles.planText}><b>Levenslang</b><span className="muted small">Eén keer betalen, voor deze tv</span></span>
                  {settings.priceLifetimeCents > 0 && <span className={styles.price}>{euro(settings.priceLifetimeCents)}</span>}
                </label>
                <label className={styles.plan}>
                  <input type="radio" name="plan" value="yearly" defaultChecked={device.requested_plan === "yearly"} />
                  <span className={styles.planText}><b>Per jaar</b><span className="muted small">Verlengt niet automatisch</span></span>
                  {settings.priceYearlyCents > 0 && <span className={styles.price}>{euro(settings.priceYearlyCents)}</span>}
                </label>
              </fieldset>
              {fout === "plan" && <p className="error">Kies een abonnement.</p>}

              <div className="field">
                <label htmlFor="email">E-mailadres (voor je bevestiging)</label>
                <input id="email" name="email" type="email" required defaultValue={device.email ?? ""} placeholder="naam@voorbeeld.nl" autoComplete="email" />
              </div>
              {fout === "email" && <p className="error">Vul een geldig e-mailadres in.</p>}

              <button className="primary" style={{ padding: 15, fontSize: 17 }}>Doorgaan naar betalen</button>
              <p className="small muted" style={{ textAlign: "center", lineHeight: 1.45, margin: 0 }}>
                Je krijgt een betaalverzoek per e-mail. Na betaling gaat je tv vanzelf verder.
              </p>
            </form>
          )}
        </>
      )}
    </main>
  );
}
