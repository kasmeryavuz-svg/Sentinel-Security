# Local QR provisioning configuration

This is **workstation tooling**, not Android production runtime. The
`:provisioning-qr` module is not an app dependency and is not packaged into
the Sentinel APK.

Sentinel itself does **not** host or upload the APK. QR provisioning requires
the final **signed** APK to be reachable by the target device at an HTTPS URL
during setup. The checksum is computed **after** signing, from the exact APK
file bytes.

## Required inputs

- `--apk` — path to the **signed** APK
- `--url` — HTTPS download location the device will fetch during setup
- `--admin` — optional; must be exactly
  `com.example.devicemanagement/.management.SentinelDeviceAdminReceiver`

Optional Wi-Fi extras are included only when supplied explicitly:

- `--wifi-ssid`
- `--wifi-security`
- `--wifi-password`

Do not pass Wi-Fi values unless the target device needs them during setup.
The generator does not log secrets. The JSON written to stdout is the
provisioning payload itself.

## Generate JSON

```bash
./gradlew :provisioning-qr:run --args="--apk /absolute/path/to/app-release.apk --url https://example.test/sentinel.apk"
```

The payload uses these public Android extras:

- `android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME`
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION`
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM`

The checksum is SHA-256 of the **signed APK file**, encoded as URL-safe
Base64 **without padding**. It pins that exact build. It is not a
signature-only checksum.

Before hashing, the generator cryptographically verifies the APK with
Android's apksig library (`ApkVerifier`). Verification must succeed and the
APK must have at least one valid signer. A ZIP that merely contains
`META-INF/*.RSA` or the text `APK Sig Block 42` is rejected. apksig is
declared only in this workstation module and is not an Android app
dependency.

The download URL is parsed as a URI. The scheme must be HTTPS
(case-insensitive), and a non-empty host is required. `http://`, `https://`
with no host, and other malformed or prefix-only strings fail closed.

HTTP URLs, missing APKs, unsigned, corrupted, or invalidly signed artifacts,
empty files, and a non-Sentinel admin component fail closed.

## Encode a QR image separately

This tool emits JSON only. A QR library is not added to the Android
production runtime or to this workstation module.

On a developer workstation, encode the JSON with existing local tooling, for
example:

```bash
./gradlew :provisioning-qr:run --args="--apk /absolute/path/to/app-release.apk --url https://example.test/sentinel.apk" \
  > provisioning.json
qrencode -o provisioning-qr.png -t PNG < provisioning.json
```

Any QR encoder that stores the exact JSON bytes is acceptable. Do not wrap,
pretty-print, or otherwise change the payload after generation.

## Signing and distribution boundary

1. Sign the release APK with the local Sentinel keystore (outside this
   repository).
2. Place that signed APK on an HTTPS server the device can reach.
3. Run the generator against that same signed file and the HTTPS URL.
4. Enroll from the device setup wizard. Sentinel never uploads the APK.

Never add keystore files, passwords, or private keys to GitHub or the
application. `assembleRelease` / `bundleRelease` in this repository do not
embed a production keystore.
