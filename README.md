# YouTube Screen Chromium

Chromium (CEF 152) for **Minecraft 1.21.1 / NeoForge**, used by the YouTube Screen mod. A fork of
[MCEF](https://github.com/CinemaMod/mcef) by montoyo and the CinemaMod Group, built on
[CCBlueX's fork of java-cef](https://github.com/CCBlueX/java-cef).

## Install (players)

1. Put `ytscreen_chromium-neoforge-1.21.1-<version>.jar` from [Releases](https://github.com/Lumi-Kodo/ytscreen-chromium/releases)
   into the `mods` folder (client only; servers do not need it).
2. On the first start the mod downloads Chromium for your system (about 150–170 MB) into `<game>/ytscreen-chromium/natives`.
   The progress is shown at the bottom of the main menu.

Supported: Windows 10/11 (x64, ARM64), Linux (x64, ARM64), macOS 10.15+ (Intel; Apple Silicon once its natives are published).

## How it works

- **Natives.** `.github/workflows/natives.yml` builds java-cef (CEF/Chromium + JNI) from the commit pinned in the `java-cef`
  submodule for 6 platforms and publishes them as the release `natives-<java-cef commit>`.
- **Mod.** `.github/workflows/build.yml` builds the jar. The SHA-256 of every natives archive is written into the jar
  (`natives.sha256`); the mod only installs an archive whose checksum matches, then checks every unpacked file against the
  archive's own file list. Archives cannot write outside their folder.
- **Test.** The same workflow starts a real Minecraft client on a virtual display, lets the mod download and start Chromium,
  renders a test page and checks the picture and JavaScript.
- **API.** Same shape as MCEF (`MCEF`, `MCEFBrowser`, `MCEFClient`, `MCEFApp`) in the package `com.ytscreen.chromium`, so it can be
  installed next to the original MCEF.

Differences from MCEF: Chromium 152 instead of 116; downloads from this repository's releases with checksums pinned in the
jar; no audio capture handler (Chromium plays sound itself); no `--disable-web-security`; `--no-first-run` (Chromium 152 asks
for a EULA on Linux); `root_cache_path` set as CEF 120+ requires; no mixins (NeoForge events only).

Files: `<game>/ytscreen-chromium/` (natives, cache with cookies, `chromium.log`) and `config/ytscreen-chromium.properties`
(download address, user agent, extra Chromium switches).

## Updating Chromium

1. Point the `java-cef` submodule at a newer commit of CCBlueX/java-cef (or your own fork) and push.
2. The natives workflow builds and publishes `natives-<commit>`; the mod workflow then rebuilds the jar with the new checksums.
3. Raise `mod_version` in `gradle.properties` for a new release.

## Building

```
git submodule update --init
./gradlew build          # needs the natives release of the pinned commit (checksums)
./gradlew build -PskipNativeChecksums   # development only: the jar then refuses to download natives
```
`-Dytscreen.chromium.natives=<dir>` uses natives from a folder instead of downloading them.

## License

LGPL 2.1 or later, see [LICENSE](LICENSE). java-cef and CEF are under the BSD license.
