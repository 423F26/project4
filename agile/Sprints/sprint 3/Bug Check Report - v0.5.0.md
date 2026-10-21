
---

## Summary

| # | Severity | File:line | Issue |
|---|---|---|---|
| 1 | High | `app/src/main/java/com/example/liftingapp/ui/ProgressChart.kt:160` | The "Est. max" chart line uses `colorOnPrimary`, so it is nearly invisible in all three themes. |
| 2 | High | `app/src/main/res/layout/tab_log.xml:79` + `app/src/main/res/layout/tab_leaderboard.xml:9` | Two spinners share the ID `exerciseSpinner`. After a rotation or theme change, the Log tab switches to the Leaderboard's exercise without telling you. |
| 3 | Medium | `app/src/main/java/com/example/liftingapp/ui/LeaderboardTab.kt:70` | The 1st-place badge uses the same color for its fill and its text, so the "1" can't be seen. |
| 4 | Medium | `app/src/main/java/com/example/liftingapp/ui/ProgressChart.kt:227` | The "Since …" change is best minus first, so it never goes negative. A lifter who got weaker still shows "+0 lb". |
| 5 | Low | `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:45`, `:81` | A weight typed with a decimal comma ("102,5") is rejected, and no message says why. |
| 6 | Low | `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:32` | The edit dialog pre-fills a rounded weight, so pressing Save can change the stored weight. |
| 7 | Low (needs confirmation) | `app/src/main/java/com/example/liftingapp/data/Strength.kt:4` | The app offers Pull-ups and Push-ups, but the input rules (weight > 0, max 20 reps) and the e1RM math don't fit bodyweight lifts. |
| 8 | Low | `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:36`, `:73`; `.../ui/LogTab.kt:254` | Dialogs and the Undo snackbar disappear when the phone rotates. Typed input and the chance to undo are lost. |
| 9 | Low | `app/build.gradle.kts:10-16`, `:53-55` | The instrumented test can't compile or run (no test runner, no androidTest dependencies). The 20-rep cap and the database have no tests. |
| 10 | Low | `app/build.gradle.kts:14-15` | `versionName = "1.0"` and `versionCode = 1` don't match release 0.5.0. |

**Totals:** 2 High, 2 Medium, 6 Low.

---

## 1. High: The main chart line is almost invisible

**File:** `app/src/main/java/com/example/liftingapp/ui/ProgressChart.kt:160` (the color is used at lines 186, 189, 198, 200, 209 and 212)

**What's wrong**

```kotlin
val primary = themeColor(MaterialR.attr.colorOnPrimary)
```

The variable is called `primary`, but it reads `colorOnPrimary`. That color is meant for text and icons drawn *on top of* a primary-colored surface, like a button. It is not meant for drawing on a card. Everything that uses it is hard to see:

- the "Est. max" line and its circles
- its gradient fill
- the crosshair you get when you tap a point
- the dashed "Best ___" line and its label
- the "Est. max" swatch in the legend

I measured contrast against the chart card using the WCAG ratio. About 3:1 is the usual minimum for chart lines.

| Theme | Current: `colorOnPrimary` | Fix: `colorPrimary` |
|---|---|---|
| Blue & Gold | #231900 on navy card: **1.37:1** | gold #F2B535: 6.88:1 |
| Graphite | #2B2D30 on grey card: **2.05:1** | 5.17:1 |
| Daylight | #FFFFFF on white card: **1.02:1** | navy #1B3A6B: 11.08:1 |

In Daylight the line is white on white, and the circles are white with a white center.

**How to reproduce**

1. Log two or three Bench sets for one athlete on different days.
2. Look at the chart on the Log tab in each theme.
3. You mostly see the dashed "Heaviest set" line. The solid "Est. max" line and the "Best ___" label are very faint. In Daylight they are invisible.

**Why it happens**

`colorOnPrimary` is the "ink" color for things drawn on a `colorPrimary` background. The tab bar uses it correctly: in `activity_main.xml:81` it colors the text on the primary pill. The chart, though, sits on the card, not on a primary background.

**Suggested fix**

```kotlin
val primary = themeColor(MaterialR.attr.colorPrimary)
```

---

## 2. High: Rotating the phone or changing the theme changes the Log tab's exercise

**Files:** `app/src/main/res/layout/tab_log.xml:79` and `app/src/main/res/layout/tab_leaderboard.xml:9`. Listeners: `app/src/main/java/com/example/liftingapp/ui/LogTab.kt:64-65` and `app/src/main/java/com/example/liftingapp/ui/LeaderboardTab.kt:29-31`.

**What's wrong**

`activity_main.xml:90-102` includes both tab layouts in the same screen. Both layouts have a Spinner with `android:id="@+id/exerciseSpinner"`. Android saves view state in one table, keyed by view ID. When two views share an ID, the second one saved (the Leaderboard spinner) overwrites the first (the Log spinner). On restore, *both* spinners get the Leaderboard's selection.

The Log spinner then fires `onItemSelected`, which calls `viewModel.setExercise(...)`. The Log tab, its chart and the next "Log set" all switch to the Leaderboard's exercise.

**How to reproduce**

1. On the Log tab, pick **Squat**. Leave the Leaderboard tab on **Bench**, the default.
2. Rotate the phone. Or tap the palette button and pick another theme, which calls `recreate()` at `MainActivity.kt:66`.
3. The Log tab now shows **Bench**. If you tap "Log set", the dialog says "Log Bench for …".

A coach who doesn't notice will save Squat numbers as Bench, and those sets will then appear on the Bench leaderboard. You only see the bug when the two tabs are on different exercises.

**Why it happens**

`LogTab` sets the spinner from the ViewModel during `onCreate` (line 64), which is correct. But `onRestoreInstanceState` runs *after* `onCreate`, and it overwrites that choice with the saved spinner state, which is the wrong one.

**Suggested fix** (either one works)

- Give the Leaderboard spinner its own ID. This keeps the original Log tab IDs from project4:

  ```xml
  <!-- tab_leaderboard.xml -->
  <Spinner
      android:id="@+id/leaderboardExerciseSpinner"
      ... />
  ```

  Then change `binding.exerciseSpinner` to `binding.leaderboardExerciseSpinner` in `LeaderboardTab.kt:29-31`.

- Or add `android:saveEnabled="false"` to both spinners. The ViewModel already remembers both selections.

Rule of thumb: IDs must be unique across all layouts included in the same screen.

---

## 3. Medium: The 1st-place rank number is invisible

**File:** `app/src/main/java/com/example/liftingapp/ui/LeaderboardTab.kt:70`

**What's wrong**

```kotlin
first = parent.themeColor(MaterialR.attr.colorOnPrimary) to parent.themeColor(MaterialR.attr.colorOnPrimary),
```

The pair holds the badge fill color and the text color. Both are `colorOnPrimary`, so the "1" is drawn in the same color as its circle. The comments (`LeaderboardTab.kt:55`, `item_leaderboard.xml:21`) say 1st place should be gold. In Blue & Gold, `colorOnPrimary` is dark brown (#231900), not gold.

**How to reproduce**

Log a set for any athlete and open the Leaderboard. The top row's badge is a plain circle (dark, or white in Daylight) with no visible number. Rows 2 and below look fine.

**Why it happens**

It's the same mix-up as #1: `colorOnPrimary` is used where `colorPrimary` was meant.

**Suggested fix**

```kotlin
first = parent.themeColor(MaterialR.attr.colorPrimary) to parent.themeColor(MaterialR.attr.colorOnPrimary),
```

This gives a gold circle with dark text in Blue & Gold, and a navy circle with white text in Daylight.

---

## 4. Medium: The "Since …" change can never show a drop

**File:** `app/src/main/java/com/example/liftingapp/ui/ProgressChart.kt:227` (shown on screen at `app/src/main/java/com/example/liftingapp/ui/LogTab.kt:175`)

**What's wrong**

```kotlin
val best = shown.maxBy { it.bestE1rm }   // line 206
val first = shown.first()                // line 224
gain = if (shown.size >= 2) best.bestE1rm - first.bestE1rm else null   // line 227
```

`best` is the highest day in view, so `best - first` is always 0 or more. `LogTab.kt:175` has a "−" branch for negative values, so the plan was clearly to show drops too. That branch can never run.

**How to reproduce**

Log Bench for one athlete on three different days: 300×1, then 280×1, then 270×1. The card says "Since <first day>  +0 lb". The athlete actually dropped 30 lb. In my scratch check, the code gives 0 and "last minus first" gives −30.

**Why it happens**

"Change since a date" should compare the latest day with the first day. The code compares the best day with the first day.

**Suggested fix**

```kotlin
gain = if (shown.size >= 2) shown.last().bestE1rm - first.bestE1rm else null
```

If "best improvement" is what you actually want, rename the label (for example, "Best gain since …") and delete the "−" branch, since it can never run.

---

## 5. Low: A decimal comma is rejected with no message

**File:** `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:45` (set weight) and `:81` (body weight)

**What's wrong**

`"102,5".toFloatOrNull()` returns `null`. The Save or Add button just stays greyed out, and nothing tells the user why.

**How to reproduce**

Set the phone's language to German, French or Spanish. Open "Log set" and type 102,5 for weight and 5 for reps. Save stays disabled.

*Needs confirmation on a device:* on Android 8 and later, a `numberDecimal` field follows the device language. In those languages the keyboard may offer only ",", so "." can't be typed at all. If so, these users can't enter fractional weights like 227.5 at all.

**Why it happens**

Kotlin's `toFloatOrNull()` only accepts "." as the decimal point.

**Suggested fix**

Use one shared parser in both dialogs:

```kotlin
fun String.toPoundsOrNull(): Float? =
    trim().replace(',', '.').toFloatOrNull()?.takeIf { it > 0f && it.isFinite() }
```

Also show an error on the weight field, the way the reps field already does, instead of only disabling Save.

---

## 6. Low: Editing a set can quietly change its weight

**File:** `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:32`. It uses `Float.lb()` from `app/src/main/java/com/example/liftingapp/ui/Format.kt:10`. The "nothing changed" check is at `app/src/main/java/com/example/liftingapp/ui/LogTab.kt:234`.

**What's wrong**

The weight field accepts any number of decimal places. But the edit dialog pre-fills `initial.weight.lb()`, which rounds to one decimal place.

**How to reproduce**

1. Log 220.46 × 5 (that's 100 kg in pounds). The app stores 220.46 but shows "220.5".
2. Tap that day on the chart, then tap Edit. The field shows 220.5.
3. Press Save without changing anything. The check `weight == original.weight` is false, so the set is updated to 220.5. A "Changed to 220.5 × 5" Undo snackbar appears even though you changed nothing.

**Why it happens**

The dialog pre-fills the display text, not the stored value.

**Suggested fix**

Pre-fill the exact value, or limit input to one decimal place:

```kotlin
b.weightInput.setText(initial.weight.toString().removeSuffix(".0"))
```

---

## 7. Low (Needs confirmation): Pull-ups and Push-ups don't fit the input rules

**Files:** `app/src/main/java/com/example/liftingapp/data/Strength.kt:4` and `:7`; `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:45-46`

**What's wrong**

`EXERCISES` includes "Pull-ups" and "Push-ups". The Sprint 2 backlog lists only Bench, Squat, Deadlift and Power Clean. The log dialog requires a weight above 0 and 1–20 reps, and every screen ranks by Epley e1RM. That causes three problems:

- A bodyweight push-up or pull-up set with no added weight can't be saved, because the weight must be above 0.
- A 30-rep push-up set can't be saved, because of the 20-rep cap.
- If one coach enters only the added weight (25 lb) and another enters body weight plus added weight, the leaderboard compares numbers that mean different things.

**How to reproduce**

Pick Push-ups, tap Log set, and enter weight 0 and reps 30. Save stays disabled.

**Suggested fix**

The team needs to decide. Either remove these two lifts until they have their own rules, or treat them as bodyweight lifts:

- allow 0 added weight
- use body weight plus added weight for e1RM
- allow a higher rep cap

---

## 8. Low: Dialogs and the Undo snackbar don't survive rotation

**Files:** `app/src/main/java/com/example/liftingapp/ui/Dialogs.kt:36` and `:73`; `app/src/main/java/com/example/liftingapp/ui/LogTab.kt:210`, `:222` and `:254`

**What's wrong**

All the dialogs are plain `MaterialAlertDialogBuilder(...).show()` dialogs owned by the Activity. When the Activity is recreated, Android destroys them and logs a "WindowLeaked" error. They are not shown again. The Undo snackbar disappears too.

**How to reproduce**

- Open "Log set", type 225 and 5, then rotate the phone. The dialog is gone and nothing was saved.
- Or delete a set and rotate before tapping Undo. The Undo option is gone.

**Why it happens**

Android only recreates a dialog after a configuration change if it is a `DialogFragment`, or if you keep its state in the ViewModel yourself.

**Suggested fix**

Move the log-set and add-athlete dialogs into `DialogFragment`s. The alternative is to keep "dialog is open, plus the typed text" in the ViewModel and show the dialog again in `onCreate`. It's low priority, but phones in a gym get rotated a lot.

---

## 9. Low: The instrumented test can't build, and key rules have no tests

**Files:** `app/build.gradle.kts:10-16` and `:53-55`; `app/src/androidTest/java/com/example/liftingapp/ExampleInstrumentedTest.kt`

**What's wrong**

- `ExampleInstrumentedTest` imports `androidx.test.ext.junit` and `androidx.test.platform`. But the build file has no `androidTestImplementation` dependencies, and `defaultConfig` has no `testInstrumentationRunner`. Instrumented tests fail to compile.
- The Sprint 2 backlog says "reps capped at 20 … `StrengthTest` covers it". `StrengthTest` only tests 1, 5 and 0 reps. The 20-rep cap exists only in the dialog (`Dialogs.kt:46`), and no test covers it.
- Nothing tests the Room layer: the queries, the sort order, or the foreign-key cascade.

**How to reproduce**

Run `./gradlew connectedAndroidTest`, or right-click `ExampleInstrumentedTest` and choose Run. The build fails with unresolved `androidx.test` references.

**Suggested fix**

```kotlin
defaultConfig {
    // ...existing lines...
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

dependencies {
    // ...existing lines...
    androidTestImplementation("androidx.test.ext:junit:1.3.0")   // or newer
    androidTestImplementation("androidx.test:runner:1.7.0")      // or newer
}
```

Then add an in-memory Room test that:

1. inserts an athlete and two sets,
2. checks that `getProgress` returns them oldest first,
3. deletes the athlete row and checks that the sets are gone.

---

## 10. Low: Version numbers don't match 0.5.0

**File:** `app/build.gradle.kts:14-15`

**What's wrong**

The folder and the release are 0.5.0, but the app reports `versionName = "1.0"` and `versionCode = 1`.

**How to reproduce**

Install the app, then open Settings > Apps > LIFTING APP. It shows version 1.0.

**Why it matters**

- Testers and your Product Advisor see "1.0" on every build and can't tell builds apart.
- Because `versionCode` never goes up, Android lets you install any build over any other.
- The database uses `fallbackToDestructiveMigration` (`app/src/main/java/com/example/liftingapp/data/LiftDatabase.kt:25`). So if you mix builds that have different database versions, the local data can be wiped without warning.

**Suggested fix**

```kotlin
versionCode = 5          // raise this on every release
versionName = "0.5.0"
```

Before real athletes' data goes in, also add a real Room `Migration`. The code comment already says to do this.

---

## Checked and looks fine

- **Epley math** (`Strength.kt`):
  - It uses float division (`reps / 30f`), not integer division.
  - It rejects 0 reps and treats a single rep as the weight itself.
  - Results: 225×5 = 262.5, 315×3 = 346.5, 250×2 = 266.7. These match the test comments.
- **Leaderboard logic** (`Leaderboard.kt`):
  - There is one row per athlete, using their best set by e1RM.
  - Pound-for-pound can't divide by zero, because body weight must be above 0 (`Dialogs.kt:81`).
  - The test expectations (1.48× vs 1.155× BW) are correct.
- **Grouping sets by day** (`Progress.kt`):
  - `localDay` uses the time zone offset at that moment, so daylight saving time is handled.
  - Chart labels format day numbers in UTC, so the offset isn't applied twice.
  - The 30, 91, 182 and 365-day ranges include their end day, and "All" covers at least 14 days. `ProgressTest` checks these correctly.
- **Chart axes**: the Y range always contains the data, because the est. max is never below the heaviest set. Only the est. max line reacts to taps. The X labels land on the right day.
- **Room setup**:
  - The foreign key uses `CASCADE`, and `athleteId` has an index. Room turns on SQLite foreign keys by itself.
  - Both `@Relation` queries are marked `@Transaction`.
  - `@Update` and `@Delete` match by primary key.
  - Undo puts the set back with the same id and date.
- **Threading**: every write is a `suspend` call in `viewModelScope`, and reads go Flow → LiveData. No database work runs on the main thread.
- **LiveData and lifecycle**:
  - Each tab registers its observers once per Activity, with the Activity as owner, so there are no duplicate observers after a recreate.
  - The ViewModel keeps the selected athlete, exercise, chart range and ranking mode.
  - `flatMapLatest` correctly drops the old query when the athlete or exercise changes.
- **Dialog validation**: blank, non-numeric, zero and negative values are rejected (the input type blocks "-"). Reps are limited to 1–20, and Save stays disabled until the input is valid.
- **Resource and ID wiring**:
  - Every ViewBinding field, and every `R.id`, `R.layout`, `R.font`, `R.style` and `R.attr` used in Kotlin, exists in the layouts and resources.
  - Apart from #2, IDs are unique where it matters: toggle buttons and RecyclerViews.
- **Tab selection**: the selected tab is saved and restored correctly across rotation and theme changes.
- **Gradle basics**: compileSdk 37 ≥ targetSdk 36 ≥ minSdk 24. Java 17, KSP for the Room compiler, and ViewBinding are all set up. JUnit is available for unit tests.
- **Note (not a bug):** the sprint notes say deleting an athlete also deletes their sets. The schema handles this correctly, but there is no DAO method or screen to delete (or edit) an athlete yet. So a mistyped body weight can't be fixed today, and it skews the pound-for-pound rankings.
