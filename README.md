# Gallo: IPTV Player voor Chromecast met Google TV

Gallo is een eigen Android TV-app voor een Xtream Codes IPTV-abonnement, gericht op makkelijk zoeken in films en series.

## Functies

- **Inloggen** met server-URL, gebruikersnaam en wachtwoord (Xtream Codes).
- **Zoeken** over films, series en zenders tegelijk: deelwoorden ("break bad"), geen last van accenten of
  labels als "NL -", "spiderman" vindt "Spider-Man", en één typfout per woord wordt opgevangen.
- **Bladeren** per categorie met sorteren op nieuwst, A-Z, beoordeling of jaar.
- **Series**: seizoenen en afleveringen, "Verder: S2 E3", automatisch door naar de volgende aflevering.
- **Verder kijken** voor films en afleveringen, en **favorieten** (houd OK ingedrukt op een poster).
- **Live TV**: zappen met omhoog/omlaag of de kanaalknoppen binnen de gekozen categorie.
- Lijsten worden lokaal bewaard en elke 12 uur op de achtergrond ververst, zodat de app snel opstart.

## Installeren op de Chromecast

1. Op de Chromecast: **Instellingen → Systeem → Over → Android TV OS-build**: 7 keer op OK drukken
   (ontwikkelaarsopties worden ingeschakeld).
2. **Instellingen → Systeem → Ontwikkelaarsopties → USB-foutopsporing** aanzetten.
3. Zoek het IP-adres op via **Instellingen → Netwerk en internet → (je wifi-netwerk)**.
4. Op de pc, in deze map:
   ```powershell
   powershell -ExecutionPolicy Bypass -File install.ps1 192.168.1.50
   ```
   Bevestig op de tv de melding "USB-foutopsporing toestaan" (vink "altijd toestaan" aan).
5. De app staat daarna in de lijst met apps op de Chromecast (eventueel via **Apps → Alle apps bekijken**).

Werkt `adb connect` niet (bijvoorbeeld op de Google TV Streamer)? Gebruik dan
**Ontwikkelaarsopties → Draadloze foutopsporing → Apparaat koppelen met koppelingscode** en op de pc
`adb pair IP:POORT`, gevolgd door het script met `IP:POORT` van het scherm "Draadloze foutopsporing".

## Installeren voor anderen (zonder pc)

1. Installeer **Downloader** (AFTVnews) uit de Play Store.
2. Sta Downloader toe apps te installeren: Instellingen → Apps → Beveiliging en beperkingen → Onbekende bronnen.
3. Open Downloader, typ code **3370517** en kies **Go**.
4. Kies **Installeren** (bij een waarschuwing van Play Protect: *Toch installeren*).
5. Open **Gallo** en log in met je eigen IPTV-gegevens.

De code wijst naar de nieuwste versie op GitHub
(`https://github.com/Thom96/iptvplayer-releases/releases/latest/download/IPTVPlayer.apk`),
dus hij hoeft nooit aangepast te worden. Daarna werkt de app zichzelf bij.

## Publiceren via GitHub (zonder pc)

De workflow `.github/workflows/publish.yml` bouwt de app, ondertekent hem met de eigen sleutel en zet
hem als release online. De knop **Controleren op updates** in de app ziet hem daarna meteen.

Eenmalig instellen, onder **Settings → Secrets and variables → Actions** van deze repository:

| Secret | Waarde |
|---|---|
| `KEYSTORE_BASE64` | `release.jks` als base64 (PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) \| Set-Clipboard`) |
| `KEYSTORE_PASSWORD` | `storePassword` uit `keystore.properties` |
| `KEY_ALIAS` | `keyAlias` uit `keystore.properties` |
| `KEY_PASSWORD` | `keyPassword` uit `keystore.properties` |

Starten kan alleen vanaf `main`: via **Actions → Publish → Run workflow** (vul bij "What's new" in wat er nieuw is;
die tekst komt in de app), of door een commit op `main` te pushen met in het bericht een regel
`[publiceren] Wat is er nieuw`. Zonder de secrets stopt de
workflow in plaats van een APK met een andere sleutel te publiceren.

## Updates

De app werkt zichzelf bij: kort na het opstarten kijkt hij of er een nieuwe versie op GitHub staat en
vraagt dan of je wilt bijwerken. Handmatig kan via **Instellingen → Controleren op updates**. Login,
favorieten en kijkgeschiedenis blijven staan. Met een pc kan een nieuwe versie ook nog met
`install.ps1` worden geïnstalleerd.

**Bewaar `release.jks` en `keystore.properties` goed.** Daarmee wordt elke versie ondertekend; zonder
deze sleutel kan de geïnstalleerde app niet meer bijgewerkt worden en moet je hem eerst verwijderen
(waarbij je login, favorieten en kijkgeschiedenis kwijtraakt).

## Bouwen

Vereist JDK 17+ en de Android SDK (`local.properties` wijst naar de SDK).

```powershell
./gradlew assembleRelease     # APK in %LOCALAPPDATA%\IPTVPlayer-build\app\outputs\apk\release
./gradlew testDebugUnitTest   # unit tests
```

Build-uitvoer staat bewust buiten Google Drive (`%LOCALAPPDATA%\IPTVPlayer-build`).

## Bediening tijdens afspelen

| Knop | Films / series | Live TV |
|---|---|---|
| Links / rechts | 10 s terug / vooruit (vasthouden: 30 s, daarna 60 s per stap) | – |
| Spoelknoppen | 10 s terug / 30 s vooruit | – |
| Omhoog / omlaag | menu tonen | vorige / volgende zender |
| OK | menu (pauze, ondertiteling, audiotrack) | menu |
