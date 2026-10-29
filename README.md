# MSU Lift Tracker

MSU Lift Tracker is an Android app that helps MSU sports teams track strength training.

- **Roster:** a coach or athlete signs in and builds a roster of athletes.
- **Logging:** they log each lift as exercise, weight and reps.
- **Progress:** a chart shows each athlete's estimated one-rep max over time.
- **Leaderboard:** teammates are ranked by raw strength or pound-for-pound.
- **Workouts:** coaches build workouts, or start from six premade ones, and send them to athletes by text or email.

The goal is to show who is strongest, who needs more work, and how each athlete is progressing.

**Status:** Beta (Beta Demo build, October 2026). The Android project lives in [`App-0.8.0/`](App-0.8.0/).

## Who it's for

- **Coaches and strength staff,** who log and compare their athletes' lifts
- **Athletes,** who track their own progress

## Team

| Name | Role |
|---|---|
| Garrett Miller | Developer, Scrum Master |
| Gaige Bache | Developer |

**Client:** MSU Football Strength & Conditioning staff
**Product Advisor:** Daniel DeFrance

This is a 16-week, two-person capstone project. Sprint records are in [`docs & agile/Sprints/`](docs%20%26%20agile/Sprints/).

## Documentation

| Guide | For | Link |
|---|---|---|
| **User Guide** | People using the app: getting started, logging lifts, fixing problems | [Published site](https://423f26.github.io/project4/) · [source](docs/user.html) |
| **Developer Guide** | Developers: setup, build and run, code tour, testing, troubleshooting | [docs/developer.md](docs/developer.md) |
| Sprint records | Backlogs, burndown charts, timesheets | [docs & agile/Sprints/](docs%20%26%20agile/Sprints/) |
| Code reviews | Bug Check and Security Review reports for v0.5.0 | [docs & agile/Sprints/sprint 3/](docs%20%26%20agile/Sprints/sprint%203/) |

## Working build

There's **no packaged build** yet: no Play Store listing and no release APK. You build the app from source and install it on an emulator or an Android phone (Android 7.0 or newer).

1. **Install the tools:** Android Studio (a release that supports Android Gradle Plugin 9.4.1), Android SDK Platform 37, and Git. Gradle downloads JDK 25 and every library on the first sync.
2. **Clone and open:** clone this repo, then in Android Studio choose **File → Open** and select the **`App-0.8.0`** folder. Don't select the repo root.
3. **Run:** pick the **app** run configuration and a device, then click **Run**.

From the command line, run this inside `App-0.8.0`:

```powershell
.\gradlew.bat installDebug
```

Use `./gradlew installDebug` on macOS or Linux.

**What success looks like:** the app opens on a **LIFTING APP** sign-in screen with Email and Password fields and a **Continue with Google** button. After you create an account, you land on the **Log** tab, which says "No athletes yet. Add one to start logging."

**Access requirements:**

- **Internet:** needed for the first Gradle sync and for signing in. Sign-in uses the team's Firebase project, which is already configured in the repo.
- **Email/password sign-in:** works for anyone.
- **Google sign-in on your own build:** a teammate must first register your debug key's SHA-1 in Firebase. See [Private access](docs/developer.md#private-access).

Full instructions are in the [Developer Guide](docs/developer.md#set-up), and so are fixes for common problems such as the "run 'gradle init'" sync error ([Troubleshoot](docs/developer.md#troubleshoot)).

## Contributing

### Report a bug or propose work

[Open an issue](https://github.com/423F26/project4/issues/new) and include:

- **Bugs:** what you did, what you expected, what happened, and your device or emulator with its Android version. Add a screenshot if you can.
- **New work:** what it's for, and how we'll know it's done.

Label the issue `bug` or `enhancement`. Items we plan to do also go into the sprint's Product Backlog in `docs & agile/Sprints/`.

### Make a change

1. **Branch from an up-to-date `main`.** Use the pattern `<your-name>/<issue-number>-<short-description>`:

   ```powershell
   git checkout main
   git pull
   git checkout -b garrett/12-fix-chart-color
   ```

2. **Commit in small steps** with messages that say what changed, for example `Fix chart line color in Daylight theme`. Never commit `local.properties`, keystores, passwords, or Gradle output folders (`build/`, `.gradle/`, `caches/`, `daemon/`).
3. **Run the required checks** from `App-0.8.0`:

   ```powershell
   .\gradlew.bat assembleDebug
   .\gradlew.bat :app:testDebugUnitTest
   ```

   Both must end with `BUILD SUCCESSFUL`. Also smoke-test the screens you changed ([checklist](docs/developer.md#manual-smoke-test)).
4. **Push and open a pull request into `main`:**

   ```powershell
   git push -u origin garrett/12-fix-chart-color
   ```

   In the PR description:
   - link the issue (`Closes #12`)
   - say what changed and how you tested it
   - confirm the checks passed
   - add before-and-after screenshots for UI changes

### Review and merge

- **The other teammate reviews every PR.** Nobody merges their own PR without an approving review.
- **What the reviewer does:** reads the diff, pulls the branch and runs the checks above, then tries the change on an emulator. They then **Approve** or **Request changes**.
- **Who merges:** after approval, the author clicks **Squash and merge** and deletes the branch.
- **Docs travel with the change.** Docs affected by a change (README, [Developer Guide](docs/developer.md), [User Guide](docs/user.html)) are updated in the same PR.
- **No automated checks yet.** There's no CI, so the checks above are run by hand. The author and the reviewer both confirm them in the PR.

## Repository layout

```text
App-0.8.0/       Android project (open this in Android Studio)
docs/            developer.md and user.html
docs & agile/    sprint records and earlier documentation
Agents/          prompts for the team's AI review agents
index.html       GitHub Pages homepage
```
