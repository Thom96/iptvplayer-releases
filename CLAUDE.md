# Gallo (IPTV Player voor Android TV)

Android TV-app (Kotlin, Jetpack Compose for TV) voor een Xtream Codes IPTV-abonnement. Merknaam: **Gallo**,
"GalloPlayer" op de banner. Het technische app-ID blijft `nl.iptvplayer.app`, en de releasebestanden heten
`IPTVPlayer.apk`. Verander die niet, anders werken updates en de Downloader-code niet meer.

## Afspraken met de eigenaar

- **Akkoord = naar main.** Als de eigenaar akkoord geeft op een update of wijziging, zet je die zonder verder
  te vragen op `main`: open een pull request van je werkbranch naar `main` en voeg hem samen (merge commit) via de
  GitHub-koppeling. Een apart akkoord voor de pull request of het samenvoegen is niet nodig. Rechtstreeks naar
  `main` pushen kan niet vanuit een cloudsessie; gebruik altijd de pull request.
- Laat de eigenaar daarna kort weten dat het op `main` staat, met de link naar de pull request.
- Publiceren als nieuwe versie (zie hieronder) is een aparte stap. Doe het als de eigenaar om een nieuwe versie
  of update op de tv vraagt.

## Publiceren

De workflow `.github/workflows/publiceren.yml` bouwt, ondertekent (sleutel uit de repository secrets) en zet
een release online. De knop "Controleren op updates" in de app leest `version.json` uit de nieuwste release.

- Starten: push een commit waarvan het bericht een regel bevat die begint met `[publiceren] <wat is er nieuw>`.
  De tekst na de markering ziet de gebruiker in de app. Of: Actions → Publiceren → Run workflow (vanaf `main`).
- Zet `[publiceren]` nooit in de titel of tekst van een merge commit of pull request: dan publiceert het
  samenvoegen nog een keer.
- Controleer na afloop dat de run geslaagd is en dat `releases/latest/download/version.json` de nieuwe versie
  geeft.

## Bouwen en controleren

- `bash ./gradlew assembleDebug testDebugUnitTest` (de omgeving "iptvplayer-releases" installeert de Android SDK
  via het setup-script; `local.properties` wijst naar `/opt/android-sdk`). Build-uitvoer staat in
  `/tmp/IPTVPlayer-build`.
- Demomodus: inloggen met server `demo` geeft een nepbibliotheek, handig voor schermafbeeldingen.
- Doelscherm: 1920×1080 bij dichtheid 2, dus 960×540 dp. Alles moet daarbinnen passen.

## Design

Brandbook en schermontwerpen staan in het Claude-artifact "Gallo — ontwerp". Kleuren staan in `ui/Theme.kt`
(`Palette`): petrol-ondergrond, zilverwitte tekst, champagne `#E8CC94` voor focus, voortgang en sterren, en
hanenkamrood `#BC3E2E` alleen voor LIVE en de kam in het logo. Koppen in Young Serif, tekst in Outfit. Focus is
overal een champagne rand van 3 dp.
