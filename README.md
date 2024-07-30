# Ello messenger for Android

## Compilation Guide

- This repo contains dummy `google-services.json` and filled variables inside `BuildVars.java`. Before publishing your own APKs please make sure to replace all these files with your own.
- Fill out Google Maps key in `AndroidManifest.xml`.
- Fill out `static_maps_key` in `res/values/strings.xml`.
- Fill out following values in app module `build.gradle` according to server configuration:
  - `MESSENGER_ENDPOINT`
  - `MESSENGER_PORT`
  - `AI_BOT_ID`
  - `SUPPORT_BOT_ID`
  - `NOTIFICATIONS_BOT_ID`
  - `PHOENIX_BOT_ID`
  - `BUSINESS_BOT_ID`
  - `CANCER_BOT_ID`
  - `USER_TIPS_USER`
  - `DATACENTER_PUBLIC_KEY`
  - `SERVER_PUBLIC_KEY`
  - `SERVER_PUBLIC_KEY_FINGERPRINT`
