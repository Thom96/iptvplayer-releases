# Gallo: IPTV-speler voor Android TV

Gallo ("GalloPlayer") is een eigen Android TV-app voor een Xtream Codes IPTV-abonnement, gemaakt voor
Chromecast met Google TV en de Google TV Streamer (1920×1080). Hij is gericht op makkelijk zoeken in films en series.

## Functies

- **Taal**: bij de eerste start kies je Engels, Duits, Nederlands, Spaans of Frans; later aan te passen in Instellingen.
- **Inloggen** met server-URL, gebruikersnaam en wachtwoord (Xtream Codes). Het wachtwoord wordt versleuteld bewaard.
  Server `demo` geeft een nepbibliotheek om de app te bekijken.
- **Zoeken** over films, series en zenders tegelijk: deelwoorden ("break bad"), geen last van accenten of
  labels als "NL -", "spiderman" vindt "Spider-Man", en één typfout per woord wordt opgevangen.
- **Home**: recent bekeken zenders, verder kijken, favorieten en nieuw toegevoegd.
- **Films en series** in rijen per categorie en genre, of als raster met sorteren op nieuwst, A-Z, beoordeling of jaar.
- **Series**: seizoenen en afleveringen, "Verder kijken: S2 E3", automatisch door naar de volgende aflevering.
- **Live TV** met tv-gids en voorbeeld; zappen met omhoog/omlaag of de kanaalknoppen binnen de gekozen categorie.
- **Terugkijken** van uitzendingen bij zenders met archief.
- **Favorieten** (houd OK ingedrukt op een poster) en een **taalfilter** voor het aanbod.
- Lijsten worden lokaal bewaard en op de achtergrond ververst, zodat de app snel opstart.

## Abonnement en activatie

De app meldt zich aan bij de eigen Gallo-server (`web/`, Next.js op Vercel:
https://gallo-heermoes-13.vercel.app). Elk apparaat krijgt een code (`XXXX-XXXX`) en begint met een proefperiode.

- Tijdens de proef toont de app bij het opstarten hoeveel dagen er nog over zijn.
- Daarna, of als het apparaat geblokkeerd is, toont de app een activatiescherm met een QR-code naar
  `/activeren?code=…`. Zodra er betaald is, gaat de app vanzelf verder.
- In het admin panel (`/admin`) beheer je apparaten, betalingen, het aantal proefdagen en de prijzen.

## Installeren op de tv

1. Installeer **Downloader** (AFTVnews) uit de Play Store.
2. Sta Downloader toe apps te installeren: Instellingen → Apps → Beveiliging en beperkingen → Onbekende bronnen.
3. Open Downloader, typ code **3370517** en kies **Go**.
4. Kies **Installeren** (bij een waarschuwing van Play Protect: *Toch installeren*).
5. Open **Gallo**, kies je taal en log in met je eigen IPTV-gegevens.

De code wijst naar de nieuwste versie op GitHub
(`https://github.com/Thom96/iptvplayer-releases/releases/latest/download/IPTVPlayer.apk`),
dus hij hoeft nooit aangepast te worden.

## Updates

De app werkt zichzelf bij: kort na het opstarten kijkt hij of er een nieuwe versie op GitHub staat en
vraagt dan of je wilt bijwerken. Handmatig kan via **Instellingen → Controleren op updates**. Login,
favorieten en kijkgeschiedenis blijven staan.

## Publiceren

De workflow `.github/workflows/publish.yml` bouwt de app, ondertekent hem met de eigen sleutel en zet
hem als release online. De knop **Controleren op updates** in de app ziet hem daarna meteen.

Starten kan alleen vanaf `main`: via **Actions → Publish → Run workflow** (vul bij "What's new" in wat er nieuw is;
die tekst komt in de app), of door een commit op `main` te pushen met in het bericht een regel
`[publiceren] Wat is er nieuw`. Zonder de secrets stopt de workflow in plaats van een APK met een andere
sleutel te publiceren.

De sleutel staat in **Settings → Secrets and variables → Actions** van deze repository:

| Secret | Waarde |
|---|---|
| `KEYSTORE_BASE64` | `release.jks` als base64 |
| `KEYSTORE_PASSWORD` | `storePassword` uit `keystore.properties` |
| `KEY_ALIAS` | `keyAlias` uit `keystore.properties` |
| `KEY_PASSWORD` | `keyPassword` uit `keystore.properties` |

**Bewaar `release.jks` en `keystore.properties` ook zelf goed (buiten GitHub).** Daarmee wordt elke versie
ondertekend; zonder deze sleutel kan de geïnstalleerde app niet meer bijgewerkt worden en moet je hem eerst
verwijderen (waarbij je login, favorieten en kijkgeschiedenis kwijtraakt).

## Bouwen en controleren

Elke pull request wordt door de workflow `.github/workflows/check.yml` gebouwd en getest. Zelf bouwen kan met
JDK 17+ en de Android SDK (`local.properties` wijst naar de SDK):

```bash
./gradlew assembleDebug testDebugUnitTest   # APK en testresultaten in /tmp/IPTVPlayer-build
```

De server: `cd web && npm ci && npx next build`.

## Bediening tijdens afspelen

| Knop | Films / series | Live TV |
|---|---|---|
| Links / rechts | 10 s terug / vooruit (vasthouden: 30 s, daarna 60 s per stap) | – |
| Spoelknoppen | 10 s terug / 30 s vooruit | – |
| Omhoog / omlaag | menu tonen | vorige / volgende zender |
| OK | menu (pauze, ondertiteling, audiotrack) | menu |
