# Notes for Claude (and anyone else working on CatPack)

- **Keep the iPhone app in sync.** CatPack has a native iOS version in `~/StudioProjects/CatPack-iOS`
  (SwiftUI, same Firebase project). Whenever the Android app (`App-x.y.z/`) changes, make the same
  change in `CatPack-iOS` where that screen exists there, and say which parts have no iOS screen yet
  (see the feature table and "Not on iPhone" list in `CatPack-iOS/README.md`). The iPhone screens copy the Android layouts; colors and type are in `CatPack/Views/Theme.swift` (from `res/values/colors.xml` and `type.xml`). Android and iOS must read and write the
  same Firestore documents, so data changes go into both `data/` (Kotlin) and `CatPack/Cloud/` (Swift).
- **Versions move together:** Android `versionName` / `versionCode` (`App-x.y.z/app/build.gradle.kts`,
  with the folder renamed to match) and iOS `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION`
  (`CatPack-iOS/project.yml`).
- Firestore security rules live in `firestore.rules` here and cover both apps.
