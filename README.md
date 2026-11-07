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

Android TV usually keeps its own launcher on the Home button. Try in this order:
1. Press Home after installing. Some TVs then ask which launcher to use. Pick **TV Home → Always**.
2. If no prompt appears, install **Button Mapper** from Google Play. Map the remote's **Home** button to open **TV Home**.

Until then, TV Home opens like any other app from the TV's app list.

## Build

The APK is built by GitHub Actions on every push to `main` (`.github/workflows/build.yml`) and published to the `latest` release. To build locally, open the folder in Android Studio and run *Build → Build APK*, or run `gradle :app:assembleRelease`.

Signing: the key is never committed. Add two repository secrets (Settings → Secrets and variables → Actions):
`SIGNING_KEY_B64` (the keystore, base64-encoded, alias `tvhome`) and `SIGNING_PASSWORD`. With them, every build updates the installed app in place. Without them, the workflow signs with a throwaway key, so the next build needs an uninstall first.

## Layout

- `app/src/main/assets/`: the interface. `index.html`, `app.css` and `app.js` are the port of the design, and `brands.js` holds the design's brand marks. Fonts are Manrope and Nunito. The wallpapers folder has the photo, frost, glass and soft versions of each wallpaper.
- `app/src/main/java/com/clive/tvhome/MainActivity.java`: the full-screen host. It routes Home, Back and Menu keys to the page.
- `app/src/main/java/com/clive/tvhome/Bridge.java`: reads apps, inputs, network and device details from the TV, and launches apps, inputs and system settings.

You can preview the interface by opening `app/src/main/assets/index.html` in Chrome with sample data. Add `?view=apps`, `?wifi=1`, `?screen=settings`, `?screen=wallpaper` or `?screen=apps` to the address to open a specific screen.
