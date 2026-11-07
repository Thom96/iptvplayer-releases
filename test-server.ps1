# Test welke User-Agent je IPTV-server accepteert. Inloggegevens blijven op deze pc.
# Gebruik:  powershell -ExecutionPolicy Bypass -File test-server.ps1
$server = (Read-Host "Server-URL (bijv. http://provider.tv:8080)").Trim().TrimEnd('/')
if ($server -notmatch '^https?://') { $server = "http://$server" }
$user = Read-Host "Gebruikersnaam"
$secure = Read-Host "Wachtwoord" -AsSecureString
$pass = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
$url = "$server/player_api.php?username=$([uri]::EscapeDataString($user))&password=$([uri]::EscapeDataString($pass))"

$agents = [ordered]@{
    "VLC (huidige app)"  = "VLC/3.0.20 LibVLC/3.0.20"
    "Geen / okhttp"      = "okhttp/4.12.0"
    "Android standaard"  = "Dalvik/2.1.0 (Linux; U; Android 12; Chromecast Build/STTE.230319.008)"
    "IPTV Smarters"      = "IPTVSmartersPro"
    "TiviMate"           = "TiviMate/5.1.6 (Android 12)"
    "Chrome browser"     = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36"
}

foreach ($name in $agents.Keys) {
    try {
        $r = Invoke-WebRequest $url -UserAgent $agents[$name] -UseBasicParsing -TimeoutSec 20
        $auth = if ($r.Content -match '"auth"\s*:\s*1') { "inloggen OK" } else { "antwoord ontvangen, maar geen geldige login" }
        "{0,-20} -> {1} ({2})" -f $name, [int]$r.StatusCode, $auth
    } catch {
        $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { "geen verbinding: $($_.Exception.Message)" }
        "{0,-20} -> {1}" -f $name, $code
    }
}
