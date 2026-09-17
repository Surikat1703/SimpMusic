# SimpMusic — personal fork

This repository is a personal fork of
[maxrave-dev/SimpMusic](https://github.com/maxrave-dev/SimpMusic), maintained by
[Surikat1703](https://github.com/Surikat1703). It is the same app under the same GPL-3.0 licence; only
the changes listed below are ours. Everything upstream ships — the YouTube Music scraper, the player,
the Wrapped story, the analytics, the lyrics renderers — is unchanged and keeps arriving through
regular merges of `upstream/dev`.

## Builds

Builds are published to the **Releases** of this repository, and only there:

https://github.com/Surikat1703/SimpMusic/releases

Each release is built from `dev` by GitHub Actions (`Build fork APK (FOSS, arm64)`), which runs on
manual dispatch, signs with a permanent keystore and publishes one asset,
`SimpMusic-FOSS-arm64.apk`. The APK is **arm64-v8a only** and it is an **unsigned-by-maxrave** build:
it is signed by the fork's own key, so it can only update over a build signed with the same key.

## What is different

**Build**

- FOSS variant only: `isFullBuild=false`, so no Sentry, no Google Cast and no Last.fm.
- arm64-v8a only.
- Built and signed by this repository's own CI with a permanent key.

**My Mix tab** — the third tab (the upstream "Mix for you" grid) is now a Yandex-Music-shaped page:

- A full-screen animated background. On Android 13+ it is an AGSL fragment shader (procedural gradient
  noise, domain-warped, merged with a metaball field) fed from the transport state; below Android 13,
  and on Desktop, the same parameters drive a Canvas-based blob field instead.
- A mood row built from the account's own "Mixed for you" shelf — no invented recommendations. Names
  are de-numbered, de-duplicated and stripped of the words "микс"/"mix" and of the personal
  "Мой супермикс" entry, and a "Любимые треки" entry plays the app's liked songs.
- Picking a mood starts it immediately.
- An in-tab player (artwork, title, artist, seekable progress pill, add-to-liked, queue, transport),
  which is why the stock mini player is hidden on this tab. Opening the artwork opens the ordinary Now
  Playing screen.
- Auto-cache of the personal mix: a WorkManager worker keeps the configured number of tracks offline,
  refreshed on a schedule and on demand, with played or skipped tracks replaced first.
- A header button still opens the original "Mix for you" grid.

**Settings**

- **Default tab** — the app can open on Home, Mix, Analytics, Library or Search (User interface).
- The upstream update checker is removed: it pointed at the upstream repository, and this fork is
  updated by installing a new release by hand.

**Liked songs and the network**

- The Liked Music screen shows the locally liked and downloaded tracks immediately and only then syncs
  with YouTube, and an empty or geo-blocked response can no longer overwrite the cached track list.
  Two-way YouTube sync and "Send back to Google" are untouched.
- Local listening analytics is enabled once on first launch, because the Analytics tab is hidden
  without it and that read as a missing feature. It can be switched off again in
  Settings → Listening history.

## Merging upstream

```bash
git fetch upstream
git merge upstream/dev
```

`origin` is this fork; `upstream` is `maxrave-dev/SimpMusic`. All fork changes are confined to
`composeApp/` and `androidApp/` — except `core/`, which is no longer upstream's submodule but this
fork's own (`Surikat1703/core`, branched for the download-state write guard that fixes GUI lag while
downloading). Merging upstream therefore means merging `maxrave-dev/core` into `Surikat1703/core`
first, then updating the pin here.
