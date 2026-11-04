package com.example.liftingapp.data

/*
 * One exercise list for the whole app: the Log tab, the Leaderboard and the workout editor all
 * offer the same names, so a set logged as "Hex Bar Deadlift" from a football day shows up on the
 * same chart as one logged by hand. Plain Kotlin, so it's unit tested (see ExerciseCatalogTest).
 */
object ExerciseCatalog {

    /** Custom exercise names are capped here so they fit in dropdowns and on leaderboard rows. */
    const val MAX_NAME_LENGTH = 40

    /**
     * Every exercise the Workouts tab knows about: the editor's suggestions, the premade workouts
     * and every sport plan. Built once.
     */
    val builtIn: List<String> by lazy {
        val fromPremade = PREMADE_WORKOUTS.flatMap { workout -> workout.exercises.map { it.name } }
        val fromSports = SPORT_SECTIONS.flatMap { section ->
            section.seasons.flatMap { season -> season.days.flatMap { day -> day.exercises.map { it.name } } }
        }
        ordered(WORKOUT_EXERCISE_SUGGESTIONS + fromPremade + fromSports)
    }

    /**
     * The list to show: main lifts first (in their usual order), then everything else A to Z.
     * [custom] is the person's own additions; [extra] is anything else that should be pickable,
     * such as exercises in their saved workouts or ones teammates have logged.
     */
    fun build(custom: List<String>, extra: Collection<String> = emptyList()): List<String> =
        ordered(builtIn + custom + extra)

    /**
     * The spelling already in [catalog] for [name], ignoring case and extra spaces, so "push-Ups"
     * logs to the same chart as "Push-ups". Unknown names come back tidied but otherwise unchanged.
     */
    fun canonical(name: String, catalog: List<String>): String {
        val tidy = tidy(name)
        return catalog.firstOrNull { key(it) == key(tidy) } ?: tidy
    }

    /** Whether [catalog] already has [name] (ignoring case and spacing). */
    fun contains(catalog: List<String>, name: String): Boolean = catalog.any { key(it) == key(name) }

    /** A usable name for a new exercise, or null if it's blank or too long. */
    fun cleanNewName(raw: String): String? = tidy(raw).takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }

    /** Main lifts in their order, then the rest A to Z; duplicates (by [key]) keep their first spelling. */
    private fun ordered(names: List<String>): List<String> {
        val seen = HashSet<String>()
        val unique = names.map(::tidy).filter { it.isNotEmpty() && seen.add(key(it)) }
        val mainKeys = EXERCISES.map(::key)
        val main = EXERCISES.filter { name -> unique.any { key(it) == key(name) } }
        val rest = unique.filter { key(it) !in mainKeys }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        return main + rest
    }

    private fun tidy(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    private fun key(name: String): String = tidy(name).lowercase()
}
