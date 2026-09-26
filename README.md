# YulCaribe Android

Native Android client for YulCaribe Main, built with Kotlin and Jetpack Compose.

## Current build

- Home: selected main-airport chart context + airport search
- Airport flow: METAR → TAF with raw / decoded / explanation
- Map: ADS-B first by default, with Charts / NOTAM / WAFS controls
- Pilot Briefing: fixed preview map, fullscreen interactive map and model-wind guidance
- Settings: persistent main-airport and report display preferences
- Appearance follows Android system light / dark mode
- GitHub Actions debug APK build with stable debug signing

The Android client uses YulCaribe Main API v1, including the current `adsb.php` binCraft + zstd feed.
