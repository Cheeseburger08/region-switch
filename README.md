# Region Switch

**A vibe-coded Android app for getting around SIM-based country restrictions, one app at a time.**

I built this with OpenAI Codex because I wanted Samsung Health Monitor and Galaxy Store to work with a different region on my own phone. It started as a UK-only switch and grew into a picker with **202 countries and territories**. I'm new to this, and this is a personal experiment that I'm sharing because it might help someone else.

## What does it bypass?

Region Switch makes selected apps see a different SIM country, network country, and mobile operator code. It also overrides the cached country/network values read by Galaxy Store.

The original UK hooks got Samsung Health Monitor past its **“not available in your current location”** gate on my S22. Galaxy Store uses an additional profile for its cached region checks. That is the kind of restriction this project targets; selecting a country does not guarantee every app or feature will become available.

| Check | What Region Switch does |
| --- | --- |
| SIM country | Overrides `TelephonyManager.getSimCountryIso()` inside selected apps. |
| Network country | Overrides `getNetworkCountryIso()`. |
| Mobile network/operator (MCC/MNC) | Overrides `getSimOperator()` and `getNetworkOperator()` with a representative network for the selected country. |
| Galaxy Store cached region | Overrides reads of `real_country_code`, `SelectedMcc`, `mcc_for_xml_cache_init`, and `mnc_for_xml_cache_init`. |
| IP address, GPS, Samsung account country, server-side eligibility | Unchanged. These can still prevent access. |
| Root detection, Knox, Play Integrity, bootloader checks | Not bypassed by this app. |

Your actual SIM, mobile plan, device identity, and system-wide region stay unchanged. This changes the values returned inside the selected app's process.

## What is included?

- A country choice and on/off switch for each app.
- A searchable country picker with flags, alphabetical sections, and the current selection highlighted.
- Enable/pause controls and manual status refresh. Status is also checked once when the control screen is created; there is no UI refresh timer.
- A root-only background controller that keeps hooks available after the control app closes.
- A small test app for checking the country/network values it sees.

Switching a profile closes the affected app; reopen it afterward. Hooks stop at reboot. Saved selections remain, but root must still be available before you enable hooks again.

## Requirements and current limits

**Already-working root is required.** This project does not root the phone.

The current engine is an **arm64 build tested on a Galaxy S22 running Android 16 with KernelSU**, with SELinux enforcing. It uses a KernelSU-specific label for its own Frida agent file. Other root managers, devices, and Android versions are unverified. The UI's minimum Android version is 8; that is not a promise that the engine works on every Android 8+ device.

Hooks currently target the selected app's main process. An app that checks location elsewhere, uses another process or native APIs, or changes its implementation may behave differently. The controller uses Frida spawn gating, so it is experimental software running with substantial privileges. There is no TCP control server in this controller.

## Install

Download and extract the complete **arm64 bundle** from [Releases](https://github.com/Cheeseburger08/region-switch/releases). The APK by itself needs the separate engine installed first.

With Android platform-tools installed and the intended phone connected through authorized USB debugging:

```sh
adb devices
adb shell su -c id
adb push region-switch-1.3 /data/local/tmp/
adb shell "su -c 'sh /data/local/tmp/region-switch-1.3/install-engine.sh'"
adb install -r region-switch-1.3/RegionSwitch-1.3.apk
```

If multiple devices are connected, add `-s YOUR_DEVICE_SERIAL` after `adb` on every command. Allow root for **Shell** and **Region Switch** in KernelSU when needed.

Open Region Switch, choose countries, and enable the apps you want. Then open those apps. New app profiles default to the UK. Long-press an app card to remove it. **Pause all hooks** stops the controller and closes the selected apps so their next launch uses their real values.

The installer preserves an existing app-target configuration and leaves the controller stopped until you enable it. It does not install a boot service or modify firmware. Release APKs are development/debug-signed builds, not Play Store releases.

## Build from source

### Android control and test apps

Use JDK 17 and Android SDK 35. Set `ANDROID_HOME`, or let Android Studio create your local SDK configuration.

```sh
./gradlew :app:assembleDebug :probe:assembleDebug
```

On Windows, use `gradlew.bat`. Output APKs are in each module's `build/outputs/apk/debug/` directory.

### Hook script

Use Python 3 and Node.js/npm:

```sh
python -m pip install -r requirements-build.txt
npm ci
python build-script.py
```

This compiles `hooks/region-switch.js` to `region.js`, using Frida 17.9.11 and frida-java-bridge 7.0.13.

### Native engine

On Linux or WSL, install Android NDK r29 and extract the official [Frida 17.9.11 Android arm64 core devkit](https://github.com/frida/frida/releases/tag/17.9.11). Point these variables at the extracted directories:

```sh
export ANDROID_NDK_HOME=/path/to/android-ndk-r29
export FRIDA_CORE_DEVKIT=/path/to/frida-core-devkit-17.9.11-android-arm64
./build-native.sh
```

The output is `controller`. Package it with `region.js`, `ctl.sh`, `install-engine.sh`, the APK, and the notices in `licenses/`.

### Checks

```sh
node test-region-script.cjs
python country-data/build_catalog.py
mkdir -p build/catalog-test
javac -d build/catalog-test app/src/main/java/local/hessam/ukhooks/CountryProfile.java CountryCatalogCheck.java
java -cp build/catalog-test CountryCatalogCheck app/src/main/assets/countries.tsv
```

The script tests use mocked Android APIs. The country catalog check validates all 202 profiles. On the original phone, separate probe runs verified UK, US, Germany, and Japan profiles and restoration of the real values after disabling hooks. These checks do not establish compatibility with every third-party app.

## How it works

`app/` writes per-app choices to `/data/adb/uk-hook-switch/targets.conf` through `su`. `ctl.sh` manages `controller.c`, which uses Frida to attach the configured Java hooks before selected apps resume. The controller and its files are root-only. The original `local.hessam.ukhooks` package name and engine directory are retained so existing installations keep their settings.

Configuration uses one tab-separated line per app: `package`, lowercase ISO2 country, uppercase ISO3 country, and MCC/MNC operator code. Older package-only lines still mean UK.

Country/network profiles are derived from the Android Open Source Project's MCC and carrier tables. Each country has one representative network, not an exhaustive carrier list. Source snapshots, hashes, and the generator are in `country-data/`.

## License and credits

Original project code is under the [MIT License](LICENSE). Frida and frida-java-bridge have their own licenses; AOSP data is Apache-2.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Built with Codex; powered by [Frida](https://frida.re/) and [Android Open Source Project](https://source.android.com/) data. This is an independent project, with no Samsung affiliation.
