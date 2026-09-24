# HyDr0 Store

A small app store for Fire TV and Android TV. It shows a catalog of apps by category.
You pick one, and it installs, opens, updates or uninstalls it with the remote.

- Written in plain Kotlin with no extra libraries, so the APK is tiny (a few hundred KB).
- Catalog is one JSON file (`catalog/catalog.json`). Edit it on GitHub and every box picks up the change on the next **Refresh**. No rebuild needed.
- Works offline: it falls back to the last catalog it downloaded, then to the copy built into the APK.
- **Enter code**: type an app's short code (e.g. `102` for VLC) to jump straight to it.
- The store updates itself. Every build bumps `store_update` in the catalog, and installed stores show an **Update store** button.

## Security

- Only `https://` links are allowed for the catalog, icons and downloads, including every redirect along the way. Plain `http` is always refused.
- If an entry has a `sha256`, the APK is hashed after download and deleted when it doesn't match.
- Downloaded APKs sit in the app's private cache. They're shared with the system installer through a read-only provider that only serves `.apk` files from that folder.
- Android still shows its own install screen for every app, and the signature check still applies to every update.

## One-time setup

### 1. Create the repo
Create a new GitHub repo named `hydr0-store` under `Blkmaze` and upload everything in this folder. If you pick a different name, the catalog link follows the repo name automatically.

### 2. Create a signing key (once, keep it safe)
Every build has to be signed with the same key, or Android refuses to update the app. On any machine with Java:

```bash
keytool -genkeypair -v -keystore store.jks -alias hydr0store \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 store.jks > store.jks.b64      # macOS: base64 -i store.jks -o store.jks.b64
```

In the repo, go to **Settings > Secrets and variables > Actions** and add these secrets:

| Secret | Value |
|---|---|
| `STORE_KEYSTORE_B64` | contents of `store.jks.b64` |
| `STORE_KEYSTORE_PASS` | keystore password |
| `STORE_KEY_ALIAS` | `hydr0store` |
| `STORE_KEY_PASS` | key password (same as the keystore password unless you set a different one) |

Back up `store.jks` and the passwords offline. If you lose them, installed stores can't be updated in place.

### 3. Build
Go to **Actions > Build HyDr0 Store APK > Run workflow**. When it finishes you get:
- A numbered release (`v1.0.N`) and a `Store-latest` release, both holding `HyDr0-Store.apk`.
- A commit that updates `store_update` in the catalog so installed stores see the new version.

Stable download link for Downloader or a short link:
`https://github.com/Blkmaze/HyDr0-Store/releases/download/Store-latest/HyDr0-Store.apk`

### 4. Install on the Fire TV Cube
1. Install **Downloader** from the Amazon Appstore.
2. Go to Settings > My Fire TV > Developer options > Install unknown apps and turn on Downloader.
3. In Downloader, enter the link above (or your short link).
4. Open HyDr0 Store. The first time you install something, it sends you to turn on **Install unknown apps** for the store too.

## Catalog format

```json
{
  "id": "vlc",                       // unique, letters/numbers only
  "name": "VLC",
  "category": "Media Players",       // new categories appear automatically
  "package": "org.videolan.vlc",     // Android package name (used to detect installs)
  "version": "3.7.0",                // shown on the card (optional)
  "version_code": 0,                 // if higher than the installed one, the card says "Update available" (optional)
  "code": "102",                     // short code for "Enter code" (optional)
  "icon": "https://.../vlc.png",     // optional; a letter tile is drawn if missing
  "description": "Plays almost anything.",
  "source": { ... }
}
```

Source types:

| type | fields | what happens |
|---|---|---|
| `apk` | `url`, optional `sha256` | downloads the file and opens the installer |
| `github` | `repo`, `asset` (regex) | finds the newest release, downloads the first asset whose name matches |
| `store` | (none, uses `package`) | opens the Amazon Appstore on Fire TV, Google Play elsewhere |
| `web` | `url` | opens the page in a browser |

To get a file's SHA-256 before adding it: `sha256sum file.apk` (Linux) or `Get-FileHash file.apk` (PowerShell).

## Project layout

```
catalog/catalog.json                      the app list (also bundled into the APK)
app/src/main/java/tv/hydr0/store/
  MainActivity.kt   screen, dialogs, install/open/uninstall
  Catalog.kt        reads catalog.json
  Net.kt            downloads, redirects, SHA-256 check, GitHub lookup
  ApkProvider.kt    hands APKs to the system installer
  Packages.kt       installed / update-available checks
  AppAdapter.kt     app cards in the grid
  Icons.kt          icon loading and letter tiles
.github/workflows/build.yml               signed build + releases + catalog bump
```
