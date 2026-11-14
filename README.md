# Tensku Client (Fabric, Minecraft Java 26.3)

Client-side mod. Press **Right Shift** (rebind in Controls) to open the menu.

Tabs: Overview, Auction, Profit, Pay Confirm, Alerts, Themes, HUD, Leaderboard, P&L, Text, Cosmetics, **Spotify**.

## Build with GitHub
1. Upload this folder's contents to the repo root (keep `src`, `build.gradle`, `gradle.properties`, `settings.gradle`, `.github`).
2. Open the **Actions** tab -> "Build mod" -> wait for green.
3. Download the artifact (tensku-client-jar) -> unzip -> put the .jar in `.minecraft/mods`
   together with Fabric API 26.3 (Fabric Loader 0.19.5+, Java 25).

## Spotify (now playing + lyrics)
A card in the bottom right corner shows the song you are playing in Spotify, with a progress bar and the lyrics line by line.
Move or resize it in the HUD editor (menu > HUD).

One-time setup (Spotify's rules, not ours - you use your own free "app" so no passwords or secrets are shared):
1. Go to https://developer.spotify.com/dashboard and create an app (choose **Web API**). Spotify currently requires the account
   that owns the app to have Premium, and only accounts added under *User Management* can log in (up to 5).
2. In the app settings add this **Redirect URI** exactly: `http://127.0.0.1:8898/callback`
3. Copy the app's **Client ID**, open the menu (Right Shift) > **Spotify**, paste it and press **Connect Spotify**.
   Approve the login in the browser tab that opens. You stay logged in next time.

Lyrics come from lrclib.net (free, no key). Synced lyrics are used when available; otherwise plain lyrics are shown roughly in time.
If the lyrics feel early or late use the *Lyrics sync* buttons in the Spotify tab.
The login token is stored in `config/tenskugamblehud.json` - do not share that file.
