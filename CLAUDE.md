# Tolly

Unofficial client for KU Leuven's Toledo (Blackboard Learn Ultra). One repository, a folder per
product:

- `android/`: the Android app (a Gradle project of its own). Kotlin, Jetpack Compose, Material 3,
  Hilt, Room, Retrofit. minSdk 26, targetSdk 36, compileSdk 37.
- `web/`: the website, a static Astro site in English and Dutch (see `web/README.md`), hosted on
  Cloudflare.
- `extension/`: a browser extension for Chrome and Firefox (planned).
- `brand/`: the logo sources, shared by all three, and `brand/store/`: `capture.mjs` retakes the
  demo screenshots (site and Play) on the emulator, `render.mjs` turns them into the Play graphics.
  Check a capture through its `contact-sheet.png`, one image, rather than each screenshot. `docs/`: research.

Features are in `README.md`; API and auth research is in `docs/research.md`.
Everything below is about the Android app unless it says otherwise.

## Build and run

Every Gradle command runs in `android/` and needs JDK 21 and the Android SDK (platform 37,
build-tools 37.0.0) with `ANDROID_HOME` set:

```bash
cd android
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew :feature:settings:compileDebugKotlin   # one module, fast feedback
```

- On the tablet (Termux): the dotfiles' `termux.sh` sets up the SDK (`ANDROID_HOME`) and Termux's
  aapt2; install the APK with `termux-open app/build/outputs/apk/debug/app-debug.apk`. Keep
  `compileSdk`/build-tools in step with `ANDROID_SDK` there, as with CI.
- With more than one device attached, pass `-s <serial>` to adb and `ANDROID_SERIAL=<serial>` to
  `./gradlew installDebug`.
- The app id and the code's package are both `be.tolly` (the app id is permanent once on Google
  Play). The debug app is `be.tolly.debug`; start it with
  `adb shell am start -n be.tolly.debug/be.tolly.MainActivity`.
- To see the UI without signing in, start the demo: `adb shell am start -n
  be.tolly.debug/be.tolly.DemoActivity`. The demo user lives in memory, so reinstalling
  signs it out again; start the demo again after each install.
- Never type a KU Leuven password: signing in with a real account is the user's job.
- A dex error about a file "outside the root directory" comes from stale build output after the
  project moved: run `./gradlew clean` once.

## Modules (`android/`)

`app` (activities, `TollyApp` shell, navigation), `core/model`, `core/auth`, `core/network`,
`core/database`, `core/data`, `core/designsystem` (theme, components, all strings), `core/ui`
(UI helpers shared by features), `feature/*` (login, courses, announcements, agenda, downloads,
settings). MVVM: ViewModels expose `StateFlow`s built from Room; refreshes go network → Room and
the UI only reads Room.

## Design system: two looks

`TollyTheme(glass = …)` switches between two complete looks; screens never branch on the look
themselves unless a component can't hide the difference.

- **Material 3 Expressive** (glass off), after RikkaHub, ObtainX and Morphe: grouped lists with
  22 dp outer and 6 dp inner corners and 2 dp gaps on a tinted screen, tonal icon circles,
  connected button groups, large flexible top bars, `LoadingIndicator`, dynamic colour.
- **Liquid Glass** (glass on), after iOS 26: inset grouped lists joined by hairline separators,
  iOS colours and type (bold 34 sp large titles, 17 sp body), and glass only on the navigation
  layer: the floating `TollyTabBar` with its draggable lens, glass circles/capsules for top-bar
  buttons, the blurred scroll edge under top bars, translucent sheets. Content itself is never
  glass.

Build screens from these, not from raw Material components:

- `TollyScaffold` + `TollyTopBar` (`large = false` for detail panes; `subtitle`, `bottom` for a
  search field or `TollySegmented` under the title). Lists take `scrollingContentPadding(padding)`
  so they scroll under the bars and leave room for the floating tab bar (`LocalBottomBarInset`).
- Lists: `Modifier.groupItem(ItemPosition(index, count), selected, onClick, …)` on a
  `TollyListItem`, under a `SectionHeader`. Pass `separatorStart =
  GroupDefaults.separatorStart(leadingWidth)` so iOS hairlines start at the text.
- Controls: `TollySwitch`, `TollySegmented` (instead of chips or tabs for one-of-n choices),
  `TollySlider` (stops along a line, e.g. how often),
  `IconTile` for settings icons, `TollyBackButton` (via `PaneNavigationIcon`), `TollySheet`,
  `LoadingState`, `MessageState`, `InfoBanner`, `TollyPullToRefresh` (the pull only; a refresh
  shows as a `RefreshBar`, or `TollyTopBar(refreshing = …)`), `SwipeActions` around a grouped row
  (with `highlightAction`/`readAction` from `core/ui`), `FileOptionsSheet` for a long press on a
  file, `FileTile`/`DownloadStatusIcon`/`downloadStatusText` for a file's row.
- Refreshing: ViewModels run network → Room refreshes through a `Refresher` (core/ui), which keeps
  refreshing/error/loaded. `RefreshErrorBanner` shows a failed refresh above content that is still
  there; `RefreshableList` is a tab's whole list (banner, refresh bar, pull, loading/empty state).
  `TollyScaffold(snackbarHostState = …)` places snackbars above the floating tab bar.
- List-detail tabs use `TollyListDetailScaffold` with `TollyPane` (core/ui), not the library's
  navigable scaffold: it previews back gestures all the way and resizes/folds panes.
- Colours: `MaterialTheme.colorScheme` plus `Tolly.colors` (`screen`, `group`, `groupSelected`,
  `separator`); `Tolly.glass` tells which look is on.

Glass rendering uses Kyant's backdrop library (`io.github.kyant0:backdrop`): a `LayerBackdrop`
records content and `drawBackdrop` draws it blurred and refracted. A backdrop can't contain what
draws it, so `TollyApp` records everything but the tab bar (`glassBackdropSource()`), and each
`TollyScaffold` records its own content for its top bar (`LocalChromeBackdrop`). Lens refraction
needs Android 13 (`GlassTier`); keep the Android 12 (blur) and older (tint) fallbacks working.

`material3` and `material3-adaptive-navigation-suite` are pinned ahead of the Compose BOM (in
`gradle/libs.versions.toml`) for the Expressive components; bump them together.

## Conventions

- All user-visible strings live in `android/core/designsystem/src/main/res`, in English (`values`) and
  Dutch (`values-nl`); add both.
- KDoc explains why and what a component is for, in plain sentences; match the existing comment
  density rather than commenting every line.
- Haptics go through `withHaptic(...)` or `LocalHapticFeedback` (segment ticks for choices,
  toggle on/off for switches, confirm for destructive actions).
- Check UI changes on a device or emulator in both looks and both themes (screenshots with
  `adb exec-out screencap -p`).

## Releases

CI (`.github/workflows/ci.yml`) builds the debug APK and runs unit tests on every push. A release
is a tag, and **only the user releases**: Tolly has real users, so a release by accident reaches
them. Claude never creates or pushes a `v*` tag, never runs the Release workflow
(`gh workflow run`, `workflow_dispatch`), never approves a waiting release run (the `release`
environment needs the user's approval in the Actions tab), and never uploads to Play, even when a
step below or a note says to.

1. Commit and push to `main` (pushing is not a release).
2. Check CI on that commit (`gh run list --commit <sha>`); never suggest releasing a red build.
3. When the user wants a release, suggest the version (minor bump for features, patch for fixes
   only) and give them the command to run themselves, in its own `bash` block:
   `git tag vX.Y.Z && git push origin vX.Y.Z`.
4. `release.yml` (about 10 min) builds the signed APK and AAB, publishes `tolly-X.Y.Z.apk` as a
   GitHub release with generated notes, and uploads the AAB to Play's internal track once the
   `PLAY_SERVICE_ACCOUNT_JSON` secret is set. The versionCode comes from the version (1.2.3 →
   1002003).

Signing: one key signs GitHub's APKs and Play's, so they update each other:
`android/keys/tolly-release.jks` (alias `tolly`, made 2026-10-23, certificate SHA-256
`f3b78f1f…b936735c`), with its password in `android/keys/tolly-release.properties`, copied to
`android/keystore.properties` for local release builds; `android/keys/` is git-ignored. CI gets
the same values from the `TOLLY_KEYSTORE_BASE64`, `TOLLY_KEYSTORE_PASSWORD`, `TOLLY_KEY_ALIAS`
and `TOLLY_KEY_PASSWORD` secrets. The paper backup comes from `android/scripts/key-backup.sh
print` (QR codes of the keystore and its password; `restore` turns scans back into the file).
Play App Signing uses this key too (uploaded with PEPK); never let Play generate one, and never
regenerate or replace it. (`~/keys/` holds the old key, which signed 0.14 and earlier.)
The license is AGPL-3.0: `LICENSE` and the app's `res/raw/license.txt` (same text,
unwrapped) stay in step.

## Brand

The logo is the cap-T (a T whose crossbar is a mortarboard, with a tassel) in white on an indigo
gradient (#5B5BF0 → #3B2FB8; seed #4B45D6). `brand/icon.svg` is its source; the launcher drawables,
the Play icon and the website follow it. The app's fallback colour scheme (dynamic colour off)
and the website use the same indigo. The Liquid Glass look keeps iOS blue.
