# Security Review: MSU Lift Tracker v0.5.0

**Date:** October 14, 2026
**Reviewed by:** Code Security Agent
**Scope:** The whole app in `App-0.5.0/`. That covers the root Gradle files, the Gradle wrapper, the `app` module (Kotlin sources, manifest, layouts and resources, backup and data-extraction rules, keep rules), the unit tests, and the `.idea` and `local.properties` files in the folder. No git history or pull-request diff was available, so the full v0.5.0 app was reviewed as the change set. Gradle caches, build output, and `docs & agile/9.3.0` were skipped. All paths below are relative to `App-0.5.0/`.

**Bottom line:** There are no serious vulnerabilities. The app has no network code, no permissions, and no WebView, and every database query uses bound parameters. One issue is worth fixing this sprint: Android backup is on with empty rules, so athlete names and body weights get copied off the phone. Everything else is small hardening, plus some things to get right before the Sprint 3 auth and backend work.

---

## Summary

| # | Severity | File:line | Issue |
|---|---|---|---|
| 1 | Medium | `app/src/main/AndroidManifest.xml:6-8` | Backup is on and its rules are empty, so the athlete database goes to cloud backup and to new phones. |
| 2 | Low | `app/src/main/java/com/example/liftingapp/ui/LeaderboardTab.kt:95`, `.../data/LiftDao.kt:19-24` | Every athlete's body weight is shown on the leaderboard, and athletes can't be deleted. |
| 3 | Low | `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:45, 81, 83` | Text fields have no length limit and weights have no upper limit. A very long number is saved as `Infinity`. |
| 4 | Low | `settings.gradle.kts:12` | JitPack is trusted for every dependency, with no content filter and no checksum check. |
| 5 | Low | `gradle/wrapper/gradle-wrapper.properties:3` | No `distributionSha256Sum` line, and the committed wrapper JAR hasn't been checked against Gradle's published checksum. |
| 6 | Low | `local.properties:8`, `gradle/local.properties:8`, `.idea/workspace.xml:28, 54` | Machine-specific files with your Windows user path are in the project, and the project root has no `.gitignore`. |
| 7 | Info | `app/src/main/java/com/example/liftingapp/data/LiftDatabase.kt:25` | Destructive migration will wipe all athlete data at the next schema change. |
| 8 | Info | `app/src/main/java/com/example/liftingapp/data/LiftDatabase.kt:17-21` | The database isn't encrypted. That's fine for now, but revisit it before you store injury or health data. |
| 9 | Info | `app/build.gradle.kts:19-21` | The release build isn't shrunk or minified. |
| 10 | Info | `app/build.gradle.kts:47` | MPAndroidChart hasn't had a release since v3.1.0 (2019). |

**Count:** 1 Medium, 5 Low, 4 Info.

---

## 1. Medium: Backup copies athlete data off the device

**Risk**
The athlete database holds names, position groups, body weights and every logged lift. Android backup copies it off the phone to places you don't control.

**Where**
- `app/src/main/AndroidManifest.xml:6`: `android:allowBackup="true"`.
- `app/src/main/AndroidManifest.xml:7-8`: points to the two rules files below.
- `app/src/main/res/xml/data_extraction_rules.xml:7-12`: `<cloud-backup>` has no rules, so Android 12+ backs up everything. Lines 13-18 comment out `<device-transfer>`, so everything is also copied to a new phone.
- `app/src/main/res/xml/backup_rules.xml:8-13`: this file is empty, so Android 7-11 backs up everything. Your `minSdk` is 24, so those phones are supported. The template comment on line 6 says this file is ignored on older devices. That comment is backwards. Android 7-11 phones use this file, and Android 12+ phones use `data_extraction_rules.xml`.

**Scenario**
- A coach logs the team on their own phone. Android backs up `databases/lift_database` to the coach's personal Google account. If the phone has no screen lock, that backup isn't end-to-end encrypted. The roster now sits in an account the university doesn't manage. It can be restored onto any phone that signs in to that account.
- Someone replaces their phone. Device-to-device transfer quietly copies the whole roster to the new one.
- Someone gets an unlocked Android 7-11 phone with USB debugging on for a minute. They can run `adb backup` and pull the database.

This data isn't secret the way a password is. Still, names and body weights of student athletes are personal data. Where copies end up should be your decision, not a template default.

**Fix**
Turn backup off until you and the coach decide you want it. You need both changes below. On Android 12+, `allowBackup="false"` stops cloud backup but doesn't stop device-to-device transfer.

```xml
<!-- app/src/main/AndroidManifest.xml -->
<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    ... >
```

```xml
<!-- app/src/main/res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="lift_database" />
        <exclude domain="database" path="lift_database-wal" />
        <exclude domain="database" path="lift_database-shm" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="lift_database" />
        <exclude domain="database" path="lift_database-wal" />
        <exclude domain="database" path="lift_database-shm" />
    </device-transfer>
</data-extraction-rules>
```

There is a trade-off. Until the backend exists, the phone holds the only copy of the data. If you'd rather keep backup as a safety net, leave `allowBackup="true"` and add `disableIfNoEncryptionCapabilities="true"` to `<cloud-backup>`. Backup then only runs when it can be end-to-end encrypted. Tell the coach where the data goes. If you turn backup on again later, add the same `<exclude>` lines to `backup_rules.xml` as well.

---

## 2. Low: Body weight is visible to anyone, and athletes can't be removed

**Risk**
This is a privacy problem for student athletes.

**Where**
- `app/src/main/java/com/example/liftingapp/ui/LeaderboardTab.kt:93-95`: each row shows the athlete's name, position and `BW ___ lb`.
- `app/src/main/java/com/example/liftingapp/data/LiftDao.kt:19-24`: athletes can be added, but there's no way to edit or delete one. `LiftEntry.kt:20` already cascades deletes, so most of the plumbing is in place.
- `app/src/main/java/com/example/liftingapp/data/Athlete.kt:14-16`: stores name, position group and body weight.

**Scenario**
The phone gets passed around the weight room. Anyone holding it can open the Leaderboard and see every teammate's body weight next to their name. Many athletes are sensitive about body weight.

If an athlete leaves the team or asks to be removed, the only option is clearing all app data, which deletes everyone. The app also has no lock. Anyone with the phone can add, edit or delete sets and change the rankings.

**Fix**
- Remove `BW ___ lb` from the leaderboard row, or show it only in a coach view. The pound-for-pound ratio plus the best set still lets someone work out body weight, so ask the coach whether athletes should see pound-for-pound rankings at all.
- Add a "Delete athlete" action with a confirmation dialog:
  ```kotlin
  // LiftDao.kt
  @Delete
  suspend fun deleteAthlete(athlete: Athlete)   // their sets are deleted too (CASCADE)
  ```
- Collect only what you need. Add one line to the add-athlete dialog saying what is stored and who can see it.
- **Needs confirmation:** Before real athletes are entered, ask your advisor or MSU Athletics compliance whether university policy covers this data, and possibly FERPA too. The code can't answer that.

---

## 3. Low: Inputs have no length or upper limits

**Risk**
Bad input can corrupt the leaderboard and probably crash the Log tab every time it opens. Long text later becomes a problem for the backend too.

**Where**
- `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:45`: lift weight is only checked for `> 0`.
- `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:81`: body weight is only checked for `> 0`.
- `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:83, 91`: name and position group are only checked for "not blank".
- `app/src/main/res/layout/dialog_add_athlete.xml:15-19, 28-32, 41-45` and `app/src/main/res/layout/dialog_log_set.xml:15-19`: no field sets `android:maxLength`.

**Scenario**
- **Weight is Infinity.** Someone types a 40-digit number, or a key sticks. `"1000...000".toFloatOrNull()` returns `Infinity`, and `Infinity > 0` is true, so it passes and gets saved. The chart then builds its axis from `Infinity` and `NaN` (`ProgressChart.kt:155-158`). The y-axis label formatter calls `roundToInt()` (`ProgressChart.kt:120`), which throws on `NaN`. This will probably crash the Log tab whenever that athlete and lift are shown, and the Log tab is the first screen. **Needs confirmation on a device.**
- **Silly but finite values.** A 99,999 lb bench passes and goes straight to #1 on the leaderboard.
- **Very long names.** There's no length limit, so a huge pasted block of text gets saved. Android's cursor can't read back a row bigger than about 2 MB, which would break the athlete list. **Needs confirmation.** It's unlikely to happen by accident.

**Fix**
Define the limits in one place and use them in the dialogs. Use the same limits on the server later.

```kotlin
// data/Strength.kt
const val MAX_NAME_LENGTH = 60
const val MAX_POSITION_LENGTH = 6
val LIFT_WEIGHT_RANGE = 1f..1500f
val BODY_WEIGHT_RANGE = 50f..500f

// ui/Dialogs.kt:45
val weight = b.weightInput.text?.toString()?.toFloatOrNull()?.takeIf { it in LIFT_WEIGHT_RANGE }

// ui/Dialogs.kt:81
fun bodyWeight() = b.bodyWeightInput.text?.toString()?.toFloatOrNull()?.takeIf { it in BODY_WEIGHT_RANGE }
```

`it in 1f..1500f` is false for both `Infinity` and `NaN`, so this also prevents the crash.

In the layouts, set `android:maxLength="60"` on `nameInput`, `"6"` on `positionInput`, and `"6"` on both weight fields. Consider making position group a dropdown of fixed values (OL, DL, WR, ...). That also makes position-group benchmarking easier.

---

## 4. Low: JitPack is trusted for every dependency

**Risk**
This is a supply-chain risk: code from an unexpected source could end up in your build or app.

**Where**
- `settings.gradle.kts:12`: `maven { url = uri("https://jitpack.io") }` has no content filter.
- `app/build.gradle.kts:47`: MPAndroidChart is the only dependency that needs JitPack.

**Scenario**
JitPack builds libraries on demand from any public GitHub repo, and anyone can publish under `com.github.<their-username>`. Your JitPack entry has no filter, so Gradle asks JitPack for anything it can't find on Google or Maven Central. That includes a typo in a new dependency, or a coordinate copied from a tutorial. A look-alike package could then be downloaded and run in your build and your app.

Nothing records the checksum of the MPAndroidChart file either. Suppose JitPack served a different build of `v3.1.0` one day, because of a rebuild or a hijacked GitHub account. Your build wouldn't notice.

The good news: JitPack is listed last, so it can't replace AndroidX, Material or Room.

**Fix**
Allow only that one group from JitPack, and fetch that group only from JitPack:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io") } }
            filter { includeGroup("com.github.PhilJay") }
        }
    }
}
```

You can also turn on Gradle dependency verification, which records and checks the checksum of every file Gradle downloads. Run `./gradlew --write-verification-metadata sha256 help` and commit `gradle/verification-metadata.xml`. Run the same command again whenever you change dependencies.

---

## 5. Low: The Gradle wrapper download isn't checksum-verified

**Risk**
`./gradlew` downloads Gradle and runs it with your user account's permissions. If that download is tampered with, someone else's code runs on your laptop.

**Where**
- `gradle/wrapper/gradle-wrapper.properties:3`: `distributionUrl` has no matching `distributionSha256Sum` line.
- `gradle/wrapper/gradle-wrapper.jar`: a committed binary.

**Scenario**
`validateDistributionUrl=true` on line 5 is good. It only checks that the URL is valid and reachable, though. It doesn't prove the downloaded file is genuine; only a checksum does that.

Once the repo is on GitHub, a changed `distributionUrl` in a pull request, or a tampered download, would run untrusted code the next time either of you builds. The same applies to `gradle-wrapper.jar`, a binary that runs first and that you can't review in a diff. It looks like a normal Gradle wrapper. I couldn't compare it to Gradle's official checksum because network access was blocked from here (**Needs confirmation**). Its SHA-256 is:
`b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13`

**Fix**
1. Get the Gradle 9.6.0 checksums for the "Binary-only (-bin) ZIP" and the "Wrapper JAR" from https://gradle.org/release-checksums/.
2. Add the zip checksum to `gradle/wrapper/gradle-wrapper.properties`:
   ```properties
   distributionSha256Sum=<9.6.0 bin zip sha256>
   ```
   Or regenerate the wrapper: `./gradlew wrapper --gradle-version 9.6.0 --distribution-type bin --gradle-distribution-sha256-sum <sha256>`.
3. Compare the output of `sha256sum gradle/wrapper/gradle-wrapper.jar` with the Wrapper JAR checksum. Once you're on GitHub, add the `gradle/actions/wrapper-validation` action so every push gets checked.

---

## 6. Low: Machine-specific files are in the project, and there's no root `.gitignore`

**Risk**
These files show your local setup today. Later, they're where secrets tend to get committed by accident.

**Where**
- `local.properties:8`: `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`.
- `gradle/local.properties:8`: a stray copy of the same file. Gradle doesn't read `local.properties` from the `gradle/` folder.
- `.idea/workspace.xml:28` (emulator path under your user folder) and `.idea/workspace.xml:54` (the project's OneDrive Desktop path).
- The project root has no `.gitignore`. There's only `app/.gitignore`, which ignores `/build`, and `.idea/.gitignore`, which ignores `workspace.xml`.

**Scenario**
Your Sprint 1 notes plan a push to GitHub. When you push this folder, `local.properties` goes with it, and it shows a Windows username and folder layout. That alone is minor.

The bigger risk comes later. Android developers usually keep API keys and backend secrets in `local.properties` because it's normally not committed. Here it would be committed. Once a key is in git history, deleting the file doesn't remove the key.

**Fix**
Add a `.gitignore` inside `App-0.5.0/`, next to `settings.gradle.kts`. The paths that start with `/` are relative to that folder, so it has to go there, not in the `project4` repo root. This is Android Studio's standard one with a few additions:

```gitignore
*.iml
.gradle/
.kotlin/
/local.properties
/gradle/local.properties
/.idea/caches
/.idea/libraries
/.idea/modules.xml
/.idea/workspace.xml
/.idea/navEditor.xml
/.idea/assetWizardSettings.xml
/.idea/deviceManager.xml
/.idea/deploymentTargetSelector.xml
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
*.jks
*.keystore
```

Delete `gradle/local.properties`. If either file is already in a repo, run `git rm --cached local.properties gradle/local.properties` and commit.

---

## 7. Info: Destructive migration will wipe athlete data

**Risk**
Data loss. This is an availability problem, not a leak.

**Where**
- `app/src/main/java/com/example/liftingapp/data/LiftDatabase.kt:25`: `fallbackToDestructiveMigration(dropAllTables = true)`.
- `app/src/main/java/com/example/liftingapp/data/LiftDatabase.kt:8`: `exportSchema = false`.

**Scenario**
Your code comment already says this. It's listed here so it doesn't slip. The next schema change (for example, volume or muscle-group columns in Sprint 3) bumps the database version. Every phone that updates will then silently delete all athletes and lifts. With backup off (Finding 1), there's no other copy.

**Fix**
Before real athletes use the app, turn on schema export and write migrations:

```kotlin
// LiftDatabase.kt
@Database(entities = [Athlete::class, LiftEntry::class], version = 2, exportSchema = true)
```
```kotlin
// app/build.gradle.kts
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

Commit `app/schemas/`. When you move to version 3, replace the fallback with `.addMigrations(MIGRATION_2_3)`.

---

## 8. Info: The database isn't encrypted

**Risk**
Low today.

**Where**
`app/src/main/java/com/example/liftingapp/data/LiftDatabase.kt:17-21`. This is a plain Room/SQLite file called `lift_database` in app-private storage.

**Scenario**
Other apps can't read app-private storage, and modern phones encrypt it while locked. For names, body weights and lifts, that's enough for now. The data is only exposed on a rooted phone or through backups (Finding 1).

**Fix**
No change needed now. Revisit when the backlog's overtraining and return-to-baseline features (items 8 and 11) arrive, because that data is closer to health data. At that point, the better option is keeping it on the server behind auth. If it has to live on the phone, consider SQLCipher with the key held in the Android Keystore.

---

## 9. Info: The release build isn't shrunk or minified

**Risk**
Low. The release APK ships unused library code.

**Where**
- `app/build.gradle.kts:19-21`: `isMinifyEnabled = false`.
- `app/src/main/keepRules/rules.keep`: contains only comments.

**Scenario**
Obfuscation wouldn't protect much here. The app has no secrets, and the source will be on GitHub anyway. The useful part is shrinking: R8 removes unused code from your dependencies, so the app is smaller and contains less code that could have bugs. Release builds are already non-debuggable by default, and that matters more.

**Fix**
When you start making release builds:

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
}
```

Then build and test the release version, especially the chart and Room. Add keep rules to `rules.keep` only if something breaks. R8 doesn't hide API keys; anything inside the APK can be pulled out.

---

## 10. Info: MPAndroidChart is unmaintained

**Risk**
Low. If a bug turns up, no fix will come.

**Where**
`app/build.gradle.kts:47`: `com.github.PhilJay:MPAndroidChart:v3.1.0`.

**Scenario**
The project hasn't published a release since v3.1.0 in 2019. I don't know of any published vulnerabilities in it, but check for advisories. Exposure is small: it only draws numbers your own app gives it, with no network access and no file parsing. Keeping it also keeps you tied to JitPack (Finding 4).

**Fix**
Keep it for now. If you rework the chart later, look at a maintained library published on Maven Central, such as Vico. That would also let you remove JitPack.

---

## Before adding auth and a backend (Sprint 3, future work)

This is a short checklist for the planned work. None of it applies to v0.5.0 yet.

- [ ] **Don't build your own password system.** Use a proven provider: MSU single sign-on if IT allows it, or Firebase Auth, Supabase or Auth0. If you do store passwords, hash them with Argon2id or bcrypt and rate-limit logins.
- [ ] **Check access on the server on every request.** An athlete can only read and write their own sets, and a coach only sees their own team. Never trust an `athleteId` sent by the app. Hiding a button in the UI isn't access control.
- [ ] **HTTPS only.** Add only the `INTERNET` permission. Don't add `usesCleartextTraffic="true"` or a network security config that allows HTTP.
- [ ] **Keep secrets out of the APK.** That means code, `strings.xml`, `BuildConfig` and Gradle properties that get built into the app. Anything in the APK can be extracted. Keep keys that matter on the server.
- [ ] **Tokens:** store them in app-private storage, exclude them from backup and device transfer in `data_extraction_rules.xml`, clear them on logout, and keep them short-lived.
- [ ] **Validate on the server too,** using the same limits as Finding 3. Use parameterized queries or an ORM. Escape athlete names in any web dashboard to prevent XSS.
- [ ] **No tokens or personal data in logs.** The app doesn't log anything today. Keep it that way.
- [ ] **Login redirects:** if you use OAuth, use Custom Tabs with PKCE and verified App Links. Set every new activity to `android:exported="false"` unless outside apps need to open it.
- [ ] **Privacy sign-off:** agree with the coach on what you collect, who sees it, how long you keep it, and how an athlete gets their data deleted. Check MSU policy too (Needs confirmation).
- [ ] **Migrations first:** replace the destructive migration (Finding 7) before real data goes in.
- [ ] **Release signing:** keep the keystore and its passwords out of git and out of OneDrive-synced folders. Back them up somewhere safe.
- [ ] **Once you're on GitHub:** turn on Dependabot (or Renovate), secret scanning, and the wrapper-validation action.

---

## Checked and looks fine

- **Exported components:** the only component is `MainActivity` (`app/src/main/AndroidManifest.xml:14-25`). It's exported because it's the launcher, which Android requires. It reads no intent extras or data, so other apps can't send it input.
- **Permissions and network:** there's no `<uses-permission>`, no network code and no `usesCleartextTraffic`.
- **Debuggable:** not set in the manifest. Release builds are non-debuggable by default.
- **SQL injection:** every Room query uses bound parameters (`app/src/main/java/com/example/liftingapp/data/LiftDao.kt:23, 38, 42, 46`). There's no `rawQuery`, `execSQL` or `@RawQuery`.
- **Logging:** app code has no `Log.*`, `println` or `printStackTrace`.
- **WebView and JavaScript:** none.
- **Local storage:** the database is in app-private storage, not external storage. SharedPreferences use `MODE_PRIVATE` and only store the theme name. Unknown values fall back to the default theme (`app/src/main/java/com/example/liftingapp/ui/AppTheme.kt:21-28`).
- **Input basics:** number fields use number-only keyboards that filter out letters and minus signs. Reps must be 1-20 and weights must be above 0. Save stays disabled until the input is valid (`app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:44-66`). Exercise names come from a fixed list (`app/src/main/java/com/example/liftingapp/data/Strength.kt:4`), not free text. Because body weight must be above 0, the pound-for-pound math can't divide by zero (`app/src/main/java/com/example/liftingapp/data/Leaderboard.kt:28`).
- **Dependencies:** every version is pinned exactly, with no `+` or `latest`. Google and Maven Central are checked before JitPack. JUnit 4.13.2 is only used in tests and already includes the fix for CVE-2020-15250.
- **Gradle setup:** the distribution is downloaded over HTTPS with `validateDistributionUrl=true`. `gradle.properties` contains no secrets. `gradle/gradle-daemon-jvm.properties` is Gradle's standard generated toolchain file (HTTPS, foojay.io). `gradlew` and `gradlew.bat` are the standard scripts.
- **Secrets:** no API keys, passwords or tokens anywhere in the source, resources or build files.
- **Data model:** deleting an athlete will also delete their sets (`app/src/main/java/com/example/liftingapp/data/LiftEntry.kt:15-22`), so a future delete feature won't leave orphaned data behind.
