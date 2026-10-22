<p align="center">
  <img src="docs/icon.png" alt="Tidy" width="128">
</p>

<p align="center">
  <a href="https://github.com/seolcu/tidy/releases/latest"><img src="https://img.shields.io/github/v/release/seolcu/tidy?label=release&color=2F6FEB" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/platform-Android%2010%2B-2F6FEB" alt="Android 10+">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/seolcu/tidy?color=2F6FEB" alt="MIT license"></a>
</p>

<p align="center">
  <b>English</b> · <a href="README.ko.md">한국어</a>
</p>

---

**Tidy sorts your Galaxy's apps screen into folders, in One UI itself.**
No new launcher to get used to: Tidy works on the real One UI apps screen,
moving icons into folders for you the way you would by hand.

- **Keep my folders**: apps outside folders go into the folder they belong to; your folders stay as they are
- **Leave it to Tidy**: ignores your folders and sorts every app from scratch
- **Preview first**: see every change, move apps or rename folders before anything happens; undo afterwards
- **New apps**: Tidy offers to put each newly installed app in its folder (or just does it)
- **Knows Korean apps**: Toss, Baemin, KakaoTalk and the rest, plus Samsung's own apps
- **On your phone only**: the app list is never sent anywhere
- **English and Korean**, following your phone's language

Needs a Galaxy phone with One UI Home, and an accessibility permission
(Tidy uses it only on the One UI home screen, only when organizing).

## Install

1. Download `tidy.apk` from the [latest release](https://github.com/seolcu/tidy/releases/latest/download/tidy.apk).
2. Open it. Tidy isn't on the Play Store, so the first time Android asks you to allow installing apps from your browser or file manager.
3. In Accessibility settings → Installed apps, turn on **Tidy**. If the switch is greyed out: App info → ⋮ → Allow restricted settings, then try again.
4. In Tidy, tap **Read the apps screen and organize**. Don't touch the screen while it works.

Tidy checks GitHub for new versions and offers to update itself (Settings → App updates).

## Building

See [AGENTS.md](AGENTS.md) for commands, architecture and the release flow.

## License

MIT. Pretendard font: SIL Open Font License (`third_party/pretendard/`).
