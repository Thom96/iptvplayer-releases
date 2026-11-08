# Gallo (IPTV Player voor Android TV)

Android TV-app (Kotlin, Jetpack Compose for TV) voor een Xtream Codes IPTV-abonnement. Merknaam: **Gallo**,
"GalloPlayer" op de banner. Het technische app-ID blijft `nl.iptvplayer.app`, en de releasebestanden heten
`IPTVPlayer.apk`. Verander die niet, anders werken updates en de Downloader-code niet meer.

## Afspraken met de eigenaar

- **Eerst ideeën, dan bouwen.** Kom bij een nieuw verzoek altijd eerst zelf met een paar ideeën of varianten
  (kort, met schets of schermafbeelding waar dat helpt) en laat de eigenaar kiezen voordat je iets doorvoert.
- **Akkoord = naar main.** Als de eigenaar akkoord geeft op een update of wijziging, zet je die zonder verder
  te vragen op `main`: open een pull request van je werkbranch naar `main` en voeg hem samen (merge commit) via de
  GitHub-koppeling. Een apart akkoord voor de pull request of het samenvoegen is niet nodig. Rechtstreeks naar
  `main` pushen kan niet vanuit een cloudsessie; gebruik altijd de pull request.
- Laat de eigenaar daarna kort weten dat het op `main` staat, met de link naar de pull request.
- **Nederlands praten, Engels werken.** De eigenaar vraagt alles in het Nederlands en jij antwoordt in het
  Nederlands. Het werk zelf is Engels: code (namen van klassen, functies, variabelen, bestanden en resources),
  commentaar, commitberichten, titels en beschrijvingen van pull requests, en branchnamen. Uitzonderingen: de
  teksten in de app staan per taal in `i18n/` (zie Talen), en de tekst na `[publiceren]` is voor de gebruiker.
  De namen in de code zijn al Engels. Ouder commentaar is nog deels Nederlands; vertaal het als je die code toch
  aanpast. Webadressen in `web/` (zoals `/activeren`) blijven zoals ze zijn: die staan in QR-codes en links.
- Publiceren als nieuwe versie (zie hieronder) is een aparte stap. Doe het als de eigenaar om een nieuwe versie
  of update op de tv vraagt.

## Publiceren

De workflow `.github/workflows/publish.yml` bouwt, ondertekent (sleutel uit de repository secrets) en zet
een release online. De knop "Controleren op updates" in de app leest `version.json` uit de nieuwste release.

- Publiceren kan alleen vanaf `main`. Zorg eerst dat de wijziging via een pull request op `main` staat.
- Starten vanuit een cloudsessie: start de workflow "Publish" (`publish.yml`) op `main` via de GitHub-koppeling
  (workflow_dispatch) met als invoer `notes` de tekst "wat is er nieuw" (die ziet de gebruiker in de app) en
  alleen bij een grote wijziging `version` (zie Versienummers). Vanaf een
  pc kan ook: een commit rechtstreeks op `main` pushen met een regel `[publiceren] <wat is er nieuw>` in het bericht.
- Zet `[publiceren]` nooit in de titel of tekst van een merge commit of pull request: dan publiceert het
  samenvoegen nog een keer.
- **Versienummers.** Elke release krijgt een nummer. Kleine wijzigingen (verbeteringen, fixes, een nieuwe knop of
  scherm): laat het nummer leeg, de workflow telt dan 0.1 op bij de vorige release (1.4 → 1.5). Grote wijzigingen
  (een nieuw design, een grote nieuwe functie of een andere opzet van de app): geef het volgende hoofdnummer op
  (1.7 → 2.0), via de invoer `version` van de workflow of `[publiceren 2.0] <wat is er nieuw>`. Twijfel je of iets
  groot is, vraag het de eigenaar. Lokale builds heten `dev (datum)`.
- De releases op GitHub zijn de releasenotes: schrijf de tekst "wat is er nieuw" dus als korte, begrijpelijke
  zin voor de gebruiker.
- Controleer na afloop dat de run geslaagd is en dat `releases/latest/download/version.json` de nieuwe versie
  geeft.

## Bouwen en controleren

- De workflow `.github/workflows/check.yml` ("Check") bouwt en test elke pull request. Voeg pas samen als die
  geslaagd is.

- `bash ./gradlew assembleDebug testDebugUnitTest` (de omgeving "iptvplayer-releases" installeert de Android SDK
  via het setup-script; `local.properties` wijst naar `/opt/android-sdk`). Build-uitvoer staat in
  `/tmp/IPTVPlayer-build`.
- Geeft Maven Central "429 Too Many Requests", voeg dan `--init-script tools/maven-mirror.gradle` toe aan het
  gradlew-commando: dan komt alles van Google's kopie van Maven Central.
- Demomodus: inloggen met server `demo` geeft een nepbibliotheek, handig voor schermafbeeldingen.
- Doelscherm: 1920×1080 bij dichtheid 2, dus 960×540 dp. Alles moet daarbinnen passen.

## Talen

Alle teksten van de interface staan in `app/src/main/java/nl/iptvplayer/app/i18n/`: `Strings.kt` (de lijst met
teksten en `AppLanguage`), en per taal één bestand (`En.kt`, `De.kt`, `Nl.kt`, `Es.kt`, `Fr.kt`). Engels is de
standaard. Bij de eerste start kiest de gebruiker een taal; in Instellingen kan dat later.

- Nieuwe tekst: voeg hem toe aan `Strings` en vul hem in elke taal in (de compiler meldt ontbrekende talen).
  Gebruik in de code `S.<naam>`, nooit een vaste tekst.
- Nieuwe taal: kopieer `En.kt`, vertaal alles en voeg één regel toe aan `AppLanguage` met de naam in die taal zelf.

## Server en admin panel (`web/`)

Next.js-app op Vercel (team "Heermoes 13", project `gallo`, root directory `web`), met een Neon-database
(`DATABASE_URL`) en het admin-wachtwoord in `ADMIN_PASSWORD`. Vercel deployt `main` vanzelf naar productie:
https://gallo-heermoes-13.vercel.app.

- `/api/device` (POST): de app meldt zich aan met een eigen apparaat-ID en krijgt een code (`XXXX-XXXX`), de status
  (`trial`, `active`, `free`, `expired`, `blocked`) en tot wanneer die geldt.
- `/activeren?code=…`: klantpagina (de QR-code op de tv wijst hierheen).
- `/admin`: dashboard, apparaten, betalingen en instellingen (proefdagen, prijzen).
- Tabellen worden bij de eerste aanvraag zelf aangemaakt (`web/lib/db.ts`).
- Controleren: `cd web && npm ci && npx next build`.

## Design

Brandbook en schermontwerpen staan in het Claude-artifact "Gallo — ontwerp". Kleuren staan in `ui/Theme.kt`
(`Palette`): petrol-ondergrond, zilverwitte tekst, champagne `#E8CC94` voor focus, voortgang en sterren, en
hanenkamrood `#BC3E2E` alleen voor LIVE en de kam in het logo. Koppen in Young Serif, tekst in Outfit. Focus is
overal een champagne rand van 3 dp.

## Branchnamen

Werk komt op GitHub meteen onder een korte Engelse naam die bij de vraag past, in de vorm
`claude/<omschrijving>`: kleine letters en koppeltekens, bijvoorbeeld `claude/login-screen`, `claude/language-picker`
of `claude/smooth-scrolling`.

- De cloudsessie geeft je bij de start een automatische branchnaam (zoals `claude/kind-cray-7fk90x`). Push daar
  niet naartoe. De eigenaar geeft hierbij toestemming om in plaats daarvan naar `claude/<omschrijving>` te pushen:
  `git push -u origin HEAD:claude/<omschrijving>`. Zo komt de automatische naam nooit op GitHub.
- Kies de naam bij de eerste push, als duidelijk is waar de vraag over gaat. Kijk eerst of hij al bestaat
  (`git ls-remote --heads origin claude/<omschrijving>`); zo ja, voeg een volgnummer toe (`claude/login-screen-2`).
- Gebruik dezelfde naam voor alle volgende pushes en voor de pull request naar `main`.
- Werk niet verder op een samengevoegde branch: nieuw werk begint op een nieuwe branch vanaf de nieuwste `main`,
  met een nieuwe naam.
- Staat er toch werk onder een automatische naam (bijvoorbeeld uit een oudere sessie), zet dan in de beschrijving
  van de pull request een regel `Branch name: claude/<omschrijving>`. Na het samenvoegen hernoemt de workflow
  `.github/workflows/rename-branch.yml` de branch. Hernoem niet zelf met `git push`: verwijderen wordt vanuit
  een cloudsessie geweigerd (HTTP 403) en dan blijven er dubbele branches staan.
