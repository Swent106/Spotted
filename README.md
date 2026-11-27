<p align="center">
  <img src="app/src/main/res/drawable/spotted_logo.png" width="140" alt="Spotted logo">
</p>

<h1 align="center">Spotted</h1>

<p align="center">Find a lost pet with the people around you.</p>
<p align="center">
  <a href="https://github.com/Swent106/Spotted/actions/workflows/ci.yml">
    <img src="https://github.com/Swent106/Spotted/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI">
  </a>
  <a href="https://sonarcloud.io/summary/new_code?id=Swent106_Spotted">
    <img src="https://sonarcloud.io/api/project_badges/measure?project=Swent106_Spotted&metric=coverage" alt="Coverage">
  </a>
</p>
---

## About

When a pet goes missing, its owner usually searches alone: walking around the block, asking neighbours, taping posters to lampposts. Yet most lost pets do not go far, and someone nearby has often already seen them without knowing anyone was looking.

Spotted turns the people around you into a search network. The owner posts an alert, users nearby are notified, and anyone who sees the animal reports it with a photo and the place they saw it. As sightings come in, the app shows where the pet is most likely to be.

## What you can do

**If your pet is missing**
- Post an alert with its photo, description and the place you last saw it
- Follow sightings as they come in on a map, and confirm or dismiss each one
- See the area where your pet most likely is, based on where it has been seen
- Close the alert when it comes home

**If you want to help**
- Get notified when a pet goes missing close to you, within the radius you choose
- Report a sighting by taking a photo, the place and time are filled in for you
- Report an animal that looks lost even when no alert matches it
- Keep track of the pets you helped bring home, and earn badges along the way

## Status

| Feature | Status                             |
|---|------------------------------------|
| Google sign-in and onboarding (profile, pets) | ✅ Done                             |
| Home: your pets and lost pets near you | ✅ Done                             |
| Report your pet missing | ✅ Done                             |
| Report a sighting | ✅ Done                             |
| Sightings map and likely area | ✅ Done                             |
| Push notifications | 📋 Planned (needs Cloud Functions) |
| Badges | ✅ Done                             |

## Getting started

**Requirements:** Android Studio, JDK 21, Node.js (for the Firebase emulators), Firebase CLI.

1. Clone the repo and open it in Android Studio.
2. Get `google-services.json` from the team (never commit it) and put it in `app/`.
3. Make sure Gradle uses JDK 21 (Settings → Build Tools → Gradle → Gradle JDK).

```bash
./gradlew assembleDebug                      # build
./gradlew testDebugUnitTest                  # unit tests
firebase emulators:start                     # start Auth + Firestore emulators
./gradlew connectedDebugAndroidTest          # UI and emulator tests (emulator running)
./gradlew jacocoTestReport                   # coverage report
./gradlew ktfmtFormat                        # format the code
```

### Shared alert links (Firebase Hosting)

A shared alert is a link like `https://spotted-fd9bf.web.app/alert/<alertId>`. On a phone with
the app, Android opens it in the app. Otherwise the browser shows a static page from
`firebase/hosting/` that reads no data.

- **Deploy the page** after changing anything in `firebase/hosting/`:
  `firebase deploy --only hosting --project spotted-fd9bf`.
- **Update the fingerprint** if the app is signed with another key. Android only opens links in
  apps whose key is listed in `firebase/hosting/.well-known/assetlinks.json`. Get the key's
  SHA-256 with `keytool -list -v -keystore app/debug.keystore -alias androiddebugkey -storepass android`,
  add it to `sha256_cert_fingerprints`, then deploy.
- **Check it on a phone:** install the app, then run
  `adb shell pm get-app-links com.android.spotted`. The domain should be `verified`. If it isn't,
  run `adb shell pm verify-app-links --re-verify com.android.spotted`.

## Tech stack

| Area | Technologies                                                                                          |
|---|-------------------------------------------------------------------------------------------------------|
| Language | Kotlin 2.4                                                                                            |
| UI | Jetpack Compose, Material 3, Navigation Compose, Coil (images) Osmdroid                               |
| Architecture | MVVM: Composable views, ViewModels exposing `StateFlow`, repositories behind interfaces               |
| Concurrency | Kotlin Coroutines, `viewModelScope`                                                                   |
| Authentication | Firebase Authentication with Google Sign-In (Credential Manager)                                      |
| Backend | Cloud Firestore (data), Firebase Storage (photos), Firestore security rules                           |
| Testing | JUnit 4, kotlinx-coroutines-test, Compose UI tests, Kaspresso, Robolectric, Mockito (+ mockito-kotlin) |
| Firebase testing | Firebase Emulator Suite (Auth, Firestore), `@firebase/rules-unit-testing` for security rules          |
| CI / quality | GitHub Actions, JaCoCo (coverage), SonarCloud, ktfmt (formatting)                                     |
| Build | Gradle 9.8, Android Gradle Plugin 9.4, JDK 21                                                         |
| Android | minSdk 28, targetSdk 34                                                                               |
| Design & process | Figma, GitHub Projects (Scrum board), Discord                                                         |

## Links

- Mockups: https://www.figma.com/design/Bb3hpy0YXe1jnjbBdt3Puk/Spotted-%E2%80%94-App-Screens?node-id=0-1&m=dev&t=wCctH9Uyp2n25q7d-1)
- Architecture diagram: [`docs/architecture/Full Diagram.svg`](docs/architecture/Full%20Diagram.svg).
  Its editable source is [`architecture.excalidraw`](docs/architecture/architecture.excalidraw):
  open it in https://excalidraw.com, update it when a feature is merged, then export the SVG again.


---

Coursework for CS-311 Software Enterprise (SwEnt), EPFL.