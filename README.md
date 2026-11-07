# TV Home

A custom home screen for Android TV, built from the **TV Home Launcher** design: a big clock over a photo wallpaper, a glass dock of your main apps, and All apps one press below. It runs on Clive's Sharp SWTV-20AE-PRO, but it works on any Android TV.

It adapts to whatever TV it's on. Each time it opens it reads from the TV:

- **Every installed app.** Brands from the design (YouTube, Netflix, Spotify, DStv, Prime Video, Disney+, Plex and more) get the design's tiles. Other apps use their own Android TV banner. If an app has no banner, its tile shows the icon with the app's name, so nothing is ambiguous. New installs and uninstalls appear straight away.
- **Inputs** (HDMI 1, HDMI 2, AV and so on) that the TV exposes. They appear as tiles and switch the TV to that source.
- **Network**: the connected Wi-Fi name, nearby networks and the TV's IP address.
- **Model, Android version and screen size**, shown under Settings → About.

## Using it with the remote

| Where | Key | Does |
|---|---|---|
| Home | ← → | Move along the dock |
| Home | ↓ | Open All apps |
| Home | ↑ | Wi-Fi and Settings buttons |
| All apps | ↑ on top row, or Back | Back to Home |
| Any tile | OK | Open the app or switch to the input |
| Anywhere | Menu | Apps on Home (choose and order the 5 dock apps) |
| Settings | OK | Wallpaper and Apps on Home open here. Network, Display, Sound, Accessibility and About open the TV's own settings |

## Install on the TV

You need the TV's Google Play Store and either Wi-Fi or a USB stick.

**Option A, over Wi-Fi (no cables)**
1. On the TV, install **Downloader** (by AFTVnews) from Google Play.
2. Go to Settings → Device Preferences → Security & restrictions → **Unknown sources**, and allow Downloader.
3. Open Downloader and enter the APK link:
   `https://github.com/<you>/tv-home/releases/latest/download/tv-home.apk`
4. Install it, then open **TV Home**.

**Option B, USB stick**
1. Download `tv-home.apk` (from the release page, or the build artifact) to your Mac and copy it to a USB stick.
2. Plug the stick into the TV and open the APK with a file manager, for example *File Commander* or *X-plore* from Google Play. Allow that app under Unknown sources.

## Make it the Home button

The first time TV Home opens, it goes to **Settings → Home button**. Press OK there:
1. On Android 10 and later, the TV is asked to make TV Home the Home app. Confirm on screen.
2. If the TV has no such choice, TV Home opens **Button Mapper** in Google Play. Install it and map the remote's **Home** button to **TV Home**.

TV Home doesn't include its own accessibility service: Google Play Protect blocks sideloaded apps that ask for one.

## Build

The APK is built by GitHub Actions on every push to `main` (`.github/workflows/build.yml`) and published to the `latest` release. To build locally, open the folder in Android Studio and run *Build → Build APK*, or run `gradle :app:assembleRelease`.

Signing: the key is never committed. The workflow creates a key once and keeps it in the repo's private Actions cache, so each build installs over the last one. GitHub drops caches that go unused for 7 days. To pin the key permanently, add repository secrets `SIGNING_KEY_B64` (the keystore, base64-encoded, alias `tvhome`) and `SIGNING_PASSWORD`.

## Layout

Home and All apps are native Android views, so they stay smooth on TV hardware. The Settings screens and the Wi-Fi panel are a web page from the design. It opens over Home only when needed and closes when you leave.

- `java/.../MainActivity.java`: Home and All apps (dock, clock, glass, grid, remote navigation), laid out on the design's 1920×1080 grid and scaled to the screen.
- `java/.../TileView.java`: one app tile. It shows the design's brand tile, the app's TV banner, its icon with its name, or an input name. Focus animates on the GPU only.
- `ClockView.java`, `GlassView.java`: the gradient clock and the glass dock (a pre-blurred wallpaper crop, not a live blur).
- `Catalog.java`: reads the installed apps, the TV's inputs and the saved dock order.
- `SettingsActivity.java` + `Bridge.java`: host the Settings and Wi-Fi web screens (`assets/index.html#mode=settings` or `#mode=wifi`).
- `assets/brands/`: the design's brand tiles, pre-rendered to images. `assets/fonts/`: Manrope and Nunito.

Open `app/src/main/assets/index.html` in Chrome to preview the design and the Settings screens.
