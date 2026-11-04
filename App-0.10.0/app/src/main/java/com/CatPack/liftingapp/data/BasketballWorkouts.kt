package com.example.liftingapp.data

/*
 * Basketball section of the Workouts tab (see ui/SportSections.kt), built on Pro Skills Basketball.
 *
 *  - The bodyweight drill circuit (5 paired sets, 2 sets each) and the light movement day are Pro Skills'
 *    published program, numbers exactly as written ("10 Best Youth Basketball Strength Drills").
 *  - The high school lifts are the ones Pro Skills names for ages 14-18 (trap bar deadlift, goblet squat,
 *    box jumps, rows, single-leg work, bench/push-ups, pull-ups). Pro Skills doesn't give sets/reps for
 *    those, so the sets/reps on them are standard high school numbers we chose.
 *  - Frequency follows Pro Skills: off-season 2-3 sessions a week with a rest day between; in-season
 *    1-2 short maintenance sessions with less volume, and don't stop lifting when the season starts.
 *
 * Sources:
 *  - proskillsbasketball.com/10-best-youth-basketball-strength-drills/ (Brendan Winters)
 *  - proskillsbasketball.com/basketball-strength-training/ (Dennis Velasco)
 *  - proskillsbasketball.com/summer-basketball-training-plan-youth-players/ (Brendan Winters)
 *
 * No weekly rotation (cycle is empty): every week is the same; add load as technique allows.
 * Plain hyphens and "x" only, so a texted workout stays in the cheapest SMS encoding.
 */

private const val PS_WARM_UP = "5 min dynamic warm-up first. No static stretching before lifting; stretch after."

// Pro Skills' five paired sets (2 sets each). Set numbers match their article.
private val PS_SET_1 = listOf(
    drill("Walking RDL", 2, "8-10 each side", notes = "Pro Skills set 1. Flat back, hinge at the hips"),
    drill("Glute Bridge", 2, "12-15", notes = "Drive through the heels, hold 2 sec at the top")
)
private val PS_SET_2 = listOf(
    drill("Single-Leg Jump to Land", 2, "5 each leg", notes = "Pro Skills set 2. Knee over toes, hips back, stick it 2 sec"),
    drill("Side-to-Side Hops", 2, "10-15 each side", notes = "Quiet, controlled landings")
)
private val PS_SET_3 = listOf(
    drill("Air Squat", 2, "10-12", notes = "Pro Skills set 3. Chest up, drive through heels"),
    drill("Squat to Lateral Slide", 2, "30 sec each way", notes = "Defensive stance, 3-5 shuffle steps")
)
private val PS_SET_4 = listOf(
    drill("Walking Overhead Lunge", 2, "8-10 each leg", notes = "Pro Skills set 4. Front knee over toes"),
    drill("Lateral Hop to Sprint", 2, "3 each side", notes = "3 lateral hops, then explode into a 10 yd sprint")
)
private val PS_SET_5 = listOf(
    drill("Shoulder Touches", 2, "6-8 each side", notes = "Pro Skills set 5. On all fours, hips stay still"),
    drill("Push-Ups", 2, "8-12", notes = "Straight body line. Hands elevated or knees if needed")
)

val BASKETBALL = SportSection(
    sport = "Basketball",
    seasons = listOf(
        Season(
            sport = "Basketball",
            name = "Off-season",
            cycle = emptyList(),
            about = "3 sessions a week with a rest day between. Skill work on Tue/Thu, game or open gym Sat, rest Sun. Add weight only when form is solid.",
            days = listOf(
                SportDay("Monday - High School Strength", "$PS_WARM_UP Pick a weight you can move with perfect form for every rep.", listOf(
                    drill("Trap Bar Deadlift", 3, "5", notes = "Learn the hip hinge before adding weight"),
                    drill("Goblet Squat", 3, "8"),
                    drill("Box Jumps", 3, "5", notes = "Step down, don't jump down"),
                    drill("Bench Press", 3, "8", notes = "Or push-ups if no coach is spotting"),
                    drill("Dumbbell Row", 3, "10 each arm", notes = "Match every push with a pull"),
                    drill("Reverse Lunge", 3, "8 each leg"),
                    drill("Plank", 3, "30-45 sec")
                )),
                SportDay("Wednesday - Light Movement", "$PS_WARM_UP Pro Skills sets 2 and 4 only, about 15 min.", PS_SET_2 + PS_SET_4),
                SportDay("Friday - Pro Skills Strength Circuit", "$PS_WARM_UP All 5 Pro Skills sets, about 25-35 min. Do both exercises in a set, then repeat.",
                    PS_SET_1 + PS_SET_2 + PS_SET_3 + PS_SET_4 + PS_SET_5 + listOf(
                        drill("Pull-Ups", 3, "AMRAP", notes = "High school add-on. Band-assisted if needed")
                    ))
            )
        ),
        Season(
            sport = "Basketball",
            name = "In-season",
            cycle = emptyList(),
            about = "1-2 short sessions a week, less volume than the off-season. Keep lifting all season; skip the day before a game.",
            days = listOf(
                SportDay("Monday - Maintenance Lift", "$PS_WARM_UP About 25 min. Lighter than the off-season; nothing to failure.", listOf(
                    drill("Trap Bar Deadlift", 2, "5"),
                    drill("Goblet Squat", 2, "8"),
                    drill("Push-Ups", 2, "8-12"),
                    drill("Dumbbell Row", 2, "10 each arm"),
                    drill("Glute Bridge", 2, "12-15")
                )),
                SportDay("Thursday - Pro Skills Strength Block", "$PS_WARM_UP Pro Skills sets 1, 3 and 5, about 15 min. Skip if there's a game Friday.",
                    PS_SET_1 + PS_SET_3 + PS_SET_5)
            )
        )
    ),
    source = "Pro Skills Basketball (drill circuit and schedule). High school lift sets/reps are our standard numbers."
)
