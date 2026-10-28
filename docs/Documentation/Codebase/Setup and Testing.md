# Setup and Testing

How to get MSU Lift Tracker building on your machine, how to run it and its tests, and how to fix the most common setup problems.

---

## 1. Prerequisites

| Tool | What this project expects | Where that comes from |
|---|---|---|
| Android Studio | A release that supports **Android Gradle Plugin 9.4.1** and **compileSdk 37**. If Studio says the plugin version is newer than it supports, update Studio. | `App-0.7.0/build.gradle.kts`, `App-0.7.0/app/build.gradle.kts` |
| JDK for Gradle | **Java 25**. The Gradle daemon is pinned to `toolchainVersion=25`, and the Android Studio project JDK is `jbr-25`. If no JDK 25 is installed, Gradle can download one from the URLs listed in the same file (needs internet). | `App-0.7.0/gradle/gradle-daemon-jvm.properties`, `App-0.7.0/.idea/misc.xml` |
| Java level of the app code | Compiled for **Java 17** (`sourceCompatibility` / `targetCompatibility`). You don't need a separate JDK 17 for this. | `App-0.7.0/app/build.gradle.kts` |
| Gradle | Nothing to install. The wrapper downloads **Gradle 9.6.0**. Gradle 9 itself needs Java 17 or newer to start, so `JAVA_HOME` (or `java` on your `PATH`) must point to at least that when you use the command line. | `App-0.7.0/gradle/wrapper/gradle-wrapper.properties` |
| Android SDK | **SDK Platform 37** (install it from Studio's SDK Manager) and Platform-Tools. | `compileSdk = 37` |
| Device or emulator | **Android 7.0 (API 24)** or newer. The app targets API 36. | `minSdk = 24`, `targetSdk = 36` |
| Internet on first sync | Google Maven, Maven Central, and **JitPack** (`https://jitpack.io`, needed for MPAndroidChart). | `App-0.7.0/settings.gradle.kts` |

---

## 2. Open the project correctly

The repository has several folders, and only one of them is the Android project.

```text
project4/                     repo root (README.md). Do NOT open this in Android Studio.
├── App-0.7.0/                OPEN THIS ONE. It contains settings.gradle.kts.
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradlew, gradlew.bat
│   ├── gradle/               wrapper files only. Do NOT open this.
│   ├── app/                  the app module. Do NOT open this on its own.
│   └── third_party/          Poppins font license
├── docs & agile/             sprint records and these docs
├── Agents/                   agent prompts
└── 423-4/                    leftover folder, not part of the build (see section 5.1)
```

Steps:

1. In Android Studio choose **File → Open**.
2. Select the **`App-0.7.0`** folder, the one that contains `settings.gradle.kts`. Do not select the `project4` root, the `gradle` subfolder, or `app`.
3. Trust the project if asked, then wait for the Gradle sync to finish.
4. Pick the **app** run configuration and a device or emulator, then press **Run**.

**`local.properties`.** `App-0.7.0/local.properties` is committed with `sdk.dir=C\:\\Users\\jeepg\\AppData\\Local\\Android\\Sdk`, which is one teammate's machine. The file's own header says it must not be checked into version control. Android Studio normally rewrites it on sync. If the sync complains about the SDK location, point `sdk.dir` at your own SDK folder, or delete the file and let Studio create it again.

---

## 3. Build and test from the command line (Windows)

Always run Gradle from **inside `App-0.7.0`**, next to `gradlew.bat`. In PowerShell:

```powershell
cd "C:\path\to\project4\App-0.7.0"
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

In `cmd.exe`, the `.\` prefix is optional: `gradlew.bat assembleDebug`.

| Command (run inside `App-0.7.0`) | What it does |
|---|---|
| `.\gradlew.bat assembleDebug` | Builds the debug APK into `app\build\outputs\apk\debug\`. |
| `.\gradlew.bat installDebug` | Builds and installs the debug APK on the connected device or emulator. |
| `.\gradlew.bat test` | Runs every local unit test, for both the debug and release variants. |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.liftingapp.data.LeaderboardTest"` | Runs one test class. This is the same command as the saved "LeaderboardTest" run configuration in `.idea/workspace.xml`. |
| `.\gradlew.bat buildEnvironment` | Lists the build plugins and their versions, including the Kotlin version that AGP brings in. |

Test results are written as an HTML report under `app\build\reports\tests\` (for example `testDebugUnitTest\index.html`).

**macOS or Linux:** use `./gradlew` instead of `.\gradlew.bat`. If you get "permission denied" (common after unzipping a copy made on Windows), run `chmod +x gradlew` once, or call it as `sh gradlew`.

---

## 4. Tests

### 4.1 Local unit tests

Location: `App-0.7.0/app/src/test/java/com/example/liftingapp/`. These run on your computer's JVM (no emulator) with JUnit 4.13.2. They cover the plain-Kotlin logic in `data/`. There are **12 tests** in 4 files.

| File | Test | What it checks |
|---|---|---|
| `ExampleUnitTest.kt` | `addition_isCorrect` | Android Studio template (2 + 2 = 4). Proves the test setup runs. |
| `data/StrengthTest.kt` | `singleRepReturnsTheWeight` | 315 × 1 gives an e1RM of 315. |
| | `epleyForFiveReps` | 225 × 5 gives 262.5 (Epley). |
| | `zeroRepsIsRejected` | 0 reps throws `IllegalArgumentException`. |
| `data/LeaderboardTest.kt` | `eachAthleteAppearsOnceWithTheirBestSet` | Two athletes give two rows, and the lineman's best set is 315 × 3 (e1RM 346.5), not the heavier 335 × 1. |
| | `rawMaxRanksBiggerLifterFirst` | Ranking by estimated max puts the 300 lb lineman first. |
| | `poundForPoundFlipsTheOrder` | Ranking pound-for-pound puts the 180 lb DB first (1.48× vs 1.155× body weight). |
| `data/ProgressTest.kt` | `oneBestPerDayOldestFirst` | `dailyBests()` makes one point per day, oldest first. 225 × 5 (262.5) beats 245 × 1 as the day's best, while the heaviest weight (245) is tracked separately. |
| | `allRangeCoversAtLeastTwoWeeks` | With one session, the All window still spans `MIN_SPAN_DAYS` (14 days). |
| | `allRangeStartsAtFirstSessionWhenOlder` | The All window starts at the first session when it is more than 14 days old. |
| | `monthRangeDropsOlderDays` | The 1M window starts 29 days before today and drops older sessions. |
| | `localDayFollowsTheTimeZone` | 03:00 UTC is the previous calendar day in `America/Denver`. |

### 4.2 Instrumented test

Location: `App-0.7.0/app/src/androidTest/java/com/example/liftingapp/ExampleInstrumentedTest.kt`.

- One template test, `useAppContext`, which checks that the package name is `com.example.liftingapp`.
- **It cannot build as the project stands.** It imports `androidx.test.platform.app.InstrumentationRegistry` and `androidx.test.ext.junit.runners.AndroidJUnit4`, but `app/build.gradle.kts` declares no `androidTestImplementation` dependencies, and `defaultConfig` sets no `testInstrumentationRunner`.
- `assembleDebug` and `test` don't compile this source set, so they are not affected. `connectedAndroidTest` will fail until the AndroidX Test libraries (`androidx.test.ext:junit`, `androidx.test:runner`) are added and `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` is set.

### 4.3 What the tests do not cover

There are no tests yet for:

- the Room DAO queries and the cascade delete,
- `LiftViewModel` and its Flow wiring,
- the dialog validation in `ui/Dialogs.kt`,
- the formatting helpers in `ui/Format.kt`,
- chart drawing in `ui/ProgressChart.kt`,
- theme saving in `ui/AppTheme.kt`.

Sprint 2 Product Backlog item 14 also lists multi-device testing as still to do. Until those exist, use the manual check below.

### 4.4 Manual smoke test

1. Fresh install: the Log tab shows "No athletes yet. Add one to start logging." and the chart says "Add an athlete to start logging."
2. Tap **Add athlete**. Check that **Add** stays disabled until name, position and a body weight above 0 are filled in. Add the athlete and check that they become selected.
3. Tap **Log set**. Type 25 reps and check for "Enter 1–20 reps" and a disabled Save. Then enter 225 × 5, check for "Estimated 1RM: 263 lb", and save.
4. Check that the chart shows a point, History lists the set, and the Leaderboard ranks the athlete.
5. Tap the chart point, then **Edit or delete set from …**, edit the set, and use **Undo**. Repeat with **Delete** and **Undo**.
6. Try each range button and both leaderboard toggles.
7. Switch to each theme. Check that the tab, pickers and chart range are kept.
8. Rotate the phone and check that nothing resets.

---

## 5. Troubleshooting

### 5.1 "To create a new Gradle build in this directory run 'gradle init'"

Android Studio is **syncing the wrong folder**: one without a `settings.gradle.kts`, such as the `project4` root, `App-0.7.0/gradle`, `App-0.7.0/app`, or an old path from before the project moved.

**This happened on October 14, 2026.** The Gradle daemon logs showed Android Studio building in `project4\423-4\LIFTINGAPP`, the project's old location. That folder has no settings file, so Gradle printed the `gradle init` message. Because there was no wrapper there either, Studio fell back to Gradle 9.3.0 instead of the project's 9.6.0. Separately, Studio's "Gradle user home" setting was pointing at `App-0.7.0`, so Gradle filled the project folder with its own `caches`, `daemon`, `wrapper`, `native` and `.tmp` folders (about 150 MB).

To fix it:

1. **File → Close Project**.
2. **File → Open** and select **`App-0.7.0`**, the folder that contains `settings.gradle.kts`.
3. Open **Settings → Build, Execution, Deployment → Build Tools → Gradle** and check **"Gradle user home"**. It should be **blank (the default, `C:\Users\<you>\.gradle`)**, not a folder inside the project.
4. Sync again with **File → Sync Project with Gradle Files**.
5. If it still builds in the wrong place, open the **Gradle** tool window (elephant icon, right edge). Right-click any linked project that points at `423-4` or another old path and choose **Unlink**. Then click **+** and pick `App-0.7.0/settings.gradle.kts`.

Leftovers from wrong-folder syncs. None of these are part of the build. Close Android Studio, then delete them, and don't commit them:

- `project4/423-4/`. Android Studio re-created it on October 14, 2026 while syncing the old path, so it came back even though the Sprint 2 records say it was removed.
- In `App-0.7.0/`: the `caches`, `daemon`, `native` and `.tmp` folders, and the `wrapper` folder that sits directly inside `App-0.7.0`. These came from the wrong "Gradle user home" setting. **Keep `App-0.7.0/gradle/wrapper/`.** That's the real wrapper `gradlew.bat` needs.
- `App-0.7.0/gradle/.gradle/` and `App-0.7.0/gradle/local.properties`, from a time (late September) when Studio treated the `gradle` subfolder as the project. Gradle only reads the `local.properties` next to `settings.gradle.kts`.
- `docs & agile/9.3.0/`, which holds Gradle cache files (`checksums`, `executionHistory`, `fileHashes` and so on) from Gradle 9.3.0. The wrapper uses 9.6.0, so this folder is a leftover, not documentation.

### 5.2 "SDK location not found" or a wrong SDK path

`local.properties` points at another person's SDK folder. Fix `sdk.dir`, or delete the file and sync again (see section 2). Then install **SDK Platform 37** from **Tools → SDK Manager**.

### 5.3 The sync can't find a Java 25 JDK

The Gradle daemon asks for Java 25 (`gradle/gradle-daemon-jvm.properties`). Install a JDK 25, or let Gradle download one (needs internet). Also check **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM**. If you see errors about class-file versions, the JDK running Gradle is too old.

### 5.4 MPAndroidChart can't be resolved

`com.github.PhilJay:MPAndroidChart:v3.1.0` is only on JitPack. Make sure `maven { url = uri("https://jitpack.io") }` is still in `settings.gradle.kts`, and that your network or proxy allows `jitpack.io`.

### 5.5 My logged data disappeared after installing a new build

This is expected during development. `LiftDatabase` uses `fallbackToDestructiveMigration(dropAllTables = true)`, so any change to the database version wipes all tables. See [Database.md](Database.md#what-destructive-migration-means-for-you).

### 5.6 `connectedAndroidTest` fails to compile

See section 4.2. The AndroidX Test dependencies and the instrumentation runner are not declared yet.
