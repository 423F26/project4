# Codebase Documentation: MSU Lift Tracker

Developer documentation for the MSU Lift Tracker Android app, the two-person Montana State University capstone by Gaige Bache and Garrett Miller. Start here if you are new to the team.

| | |
|---|---|
| **Code documented** | `App-0.8.0/` (folder name says **0.5.0**) |
| **Version in the build file** | `versionName = "0.8.0"`, `versionCode = 8` (`App-0.8.0/app/build.gradle.kts`) |
| **Date** | October 14, 2026 |
| **Maintained by** | Document Manager agent |
| **Source of truth** | The code. Where the README or the sprint records disagree with it, these docs describe what the code really does and list the difference below. |

---

## What each document covers

| Document | Covers | Read it when |
|---|---|---|
| [Architecture.md](Architecture.md) | Tech stack with exact versions (AGP, Gradle, KSP, SDK levels, every library), one line per source file, the UI → ViewModel → Repository → DAO → Room layers with a data-flow diagram, threading, how `MainActivity` hosts the three tabs, theming, fonts and the Poppins license. | You need to find where something lives or how the pieces connect. |
| [Database.md](Database.md) | The Room database `lift_database` (version 2), every column of `athletes` and `lift_entries`, the foreign key and cascade, `LiftWithAthlete`, every DAO query, the repository API, an ER diagram, and the data-integrity rules and where they are enforced. | You touch data, queries, or the schema. |
| [User Experience.md](User%20Experience.md) | Every tab and dialog, step-by-step flows, exact on-screen messages, empty states, the theme switcher, and the math behind every number (Epley e1RM, pound-for-pound, the progress chart) with worked examples. | You change a screen, a message, or a calculation. |
| [Setup and Testing.md](Setup%20and%20Testing.md) | Prerequisites (JDK 25 for Gradle, SDK 37), which folder to open, Windows command-line builds, every unit test by name, a manual smoke test, and troubleshooting (including the "gradle init" sync error). | You set up a machine, run tests, or the build breaks. |

**Path convention used in all docs:** Kotlin paths are relative to `App-0.8.0/app/src/main/java/com/example/liftingapp/`. Resource paths (`res/...`) are relative to `App-0.8.0/app/src/main/`.

**Other folders in `docs & agile`:**

- `Sprints/`: the team's dated sprint records (Product Backlog, Sprint Backlog, Sprint Goal, Burndown Chart, Timesheet).
- `Reviews/`: bug and security reviews written by separate agents.

---

## Quick facts

| | |
|---|---|
| Platform | Android, one module (`:app`), package `com.example.liftingapp` |
| UI | Android Views + ViewBinding, Material 3. One Activity with three tabs: Log, History, Leaderboard. |
| Data | Room 2.8.4 on the device only. No backend, no login, no network code. |
| Charts | MPAndroidChart v3.1.0 |
| Build | AGP 9.4.1, Gradle 9.6.0 wrapper, KSP 2.3.6, compileSdk 37, targetSdk 36, minSdk 24, Java 17 bytecode, Gradle daemon on Java 25 |
| Tests | 12 JVM unit tests (`StrengthTest`, `LeaderboardTest`, `ProgressTest`, `ExampleUnitTest`), plus one template instrumented test that doesn't compile yet |

---

## Docs vs code discrepancies

Each item names the document, what it says, and what the code at `App-0.8.0` actually does.

1. **Version number.** Resolved: the folder `App-0.8.0` and `versionName = "0.8.0"` (`versionCode = 8`) match.
2. **UI toolkit.** Sprint 1 Product Backlog item 2 and Sprint 1 Sprint Backlog items 7–9 describe a Jetpack Compose UI. The code uses Android Views with ViewBinding (`viewBinding = true`). There is no Compose dependency.
3. **Database names.** Sprint 1 Sprint Backlog item 2 and the Sprint 1 Timesheet describe a `LiftSet` entity and an `AppDatabase`. The code has `LiftEntry` (table `lift_entries`), `Athlete` (table `athletes`) and `LiftDatabase` (file `"lift_database"`, version 2).
4. **Exercise list.** Sprint 2 Sprint Backlog item 6 lists Bench, Squat, Deadlift and Power Clean. `EXERCISES` in `data/Strength.kt` also includes Pull-ups and Push-ups.
5. **App name.** The project README calls the app "MSU Lift Tracker". The launcher label and header show `app_name` = "LIFTING APP" (`res/values/strings.xml`), and the Gradle root project is `LIFTINGAPP`.
6. **What the chart plots.** Product Backlog item 5 (Sprints 1 and 2) describes a chart of weight (and reps) over time. The code plots each training day's best estimated 1RM, with the day's heaviest set as a dashed second line (`ui/ProgressChart.kt`, `data/Progress.kt`).
7. **Deleting athletes.** Sprint 2 Sprint Backlog item 3 and the 9/24 Timesheet entry say deleting an athlete cascades to their sets. The foreign key does have `onDelete = CASCADE`, but there is no DAO method or screen to delete (or edit) an athlete, so it never happens in the app.
8. **Features with no sprint record.** Editing and deleting sets with Undo, the 1M/3M/6M/1Y/All range buttons, the heaviest-set line and tap tooltip, the theme picker (Blue & Gold, Graphite, Daylight) and the Poppins font are all in the code but in no backlog or timesheet.
9. **Tests.** The Sprint 2 records, and the comment in `app/build.gradle.kts`, mention only `StrengthTest` and `LeaderboardTest`. The code also has `ProgressTest` (5 tests), the template `ExampleUnitTest`, and the template `ExampleInstrumentedTest`. The instrumented test can't compile because no AndroidX Test dependencies are declared.
10. **The `423-4` folder.** Sprint 2 Sprint Backlog item 1 and the 9/25 Timesheet entry say the old `423-4` directory was removed. The folder is back, but the sprint record wasn't wrong. On October 14, 2026, Android Studio was still pointing at the old `project4/423-4/LIFTINGAPP` path, and it re-created `423-4/LIFTINGAPP/gradle/.gradle/` when it tried to sync there. That sync is what caused the "run 'gradle init'" error. Once Android Studio opens `App-0.8.0` (see Setup and Testing.md), delete `423-4` again.
11. **README features.** The README promises different graph types (bench comparisons, pound-for-pound, overall heaviest weights) and tracking school records. The app has one per-athlete progress chart and a ranked leaderboard list. There is no choice of graph type and no records feature.
12. **Code comments.** `res/layout/item_leaderboard.xml`, `res/drawable/bg_rank_badge.xml` and `ui/LeaderboardTab.kt` describe the 1st-place badge as gold. The code sets both the badge fill and its number to `colorOnPrimary`, which is `#FF231900` (near-black) in the default Blue & Gold theme.

---

## Keeping these docs current

| When you change... | Update |
|---|---|
| `build.gradle.kts`, `settings.gradle.kts`, `gradle/` | Architecture.md section 2, Setup and Testing.md section 1 |
| Anything in `data/` (entities, DAO, database version) | Database.md, and the diagram in Architecture.md section 4 if the flow changes |
| `Strength.kt`, `Leaderboard.kt`, `Progress.kt` | User Experience.md section 8 and the test table in Setup and Testing.md |
| Layouts, dialogs, or messages in `ui/` | User Experience.md |
| Themes, colors, fonts | Architecture.md section 7, User Experience.md section 7 |
| Tests | Setup and Testing.md section 4 |
