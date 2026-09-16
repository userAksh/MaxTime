package com.maxtime.app.data

/** Shared with FindWhenCustomScreen and CreateGroupScreen — one time vocabulary so a picked
 *  slot can be matched back against a real Session's startTime later. */
val TIME_SLOTS = listOf("7:00 AM", "9:00 AM", "11:00 AM", "4:00 PM", "6:00 PM", "8:00 PM")

/** Shared with FindWhenCustomScreen and CreateGroupScreen — (minutes, label) pairs. */
val DURATION_OPTIONS = listOf(30 to "30 min", 60 to "1 hr", 90 to "1.5 hr", 120 to "2 hr")

/** Activity categories — keep this structure, do not go sports-only. */
val ACTIVITY_CATEGORIES: LinkedHashMap<String, List<String>> = linkedMapOf(
    "Fitness" to listOf("Gym", "Yoga", "Pilates", "Boxing", "CrossFit", "Swimming", "Zumba"),
    "Sports" to listOf("Badminton", "Football", "Cricket", "Tennis", "Basketball", "Pickleball", "Table Tennis"),
    "Creative" to listOf("Pottery", "Painting", "Photography", "Music"),
    "Learning" to listOf("Cooking", "Languages", "Workshops", "Public Speaking"),
    "Outdoor" to listOf("Running", "Cycling", "Trekking", "Hiking", "Camping"),
    "Social" to listOf("Dinner", "Events", "Board Games")
)

/** Interests screen has one extra "Clubbing" option ahead of Dinner, per the prototype. */
val INTEREST_CATEGORIES: LinkedHashMap<String, List<String>> = linkedMapOf(
    "Fitness" to listOf("Gym", "Yoga", "Pilates", "Boxing", "CrossFit", "Swimming", "Zumba"),
    "Sports" to listOf("Badminton", "Football", "Cricket", "Tennis", "Basketball", "Pickleball", "Table Tennis"),
    "Creative" to listOf("Pottery", "Painting", "Photography", "Music"),
    "Learning" to listOf("Cooking", "Languages", "Workshops", "Public Speaking"),
    "Outdoor" to listOf("Running", "Cycling", "Trekking", "Hiking", "Camping"),
    "Social" to listOf("Clubbing", "Dinner", "Events", "Board Games")
)

val DEFAULT_SELECTED_INTERESTS: Map<String, Set<String>> = mapOf(
    "Fitness" to setOf("Yoga"),
    "Sports" to setOf("Badminton", "Football"),
    "Outdoor" to setOf("Running")
)

/** Keyed by activity name. Anything not present falls back to a generic-but-consistent entry. */
val ACTIVITY_DB: Map<String, ActivityInfo> = mapOf(
    "Badminton" to ActivityInfo("🏸", "Sports", "A fast-paced racket sport, usually played in doubles. Great for cardio and quick reflexes.", "Intermediate", 250),
    "Football" to ActivityInfo("⚽", "Sports", "A group sport that needs a full squad to be worth playing.", "Casual", 150),
    "Cricket" to ActivityInfo("🏏", "Sports", "Casual box-cricket sessions with a rotating squad.", "Casual", 200),
    "Tennis" to ActivityInfo("🎾", "Sports", "Singles and doubles on hard courts.", "Intermediate", 400),
    "Swimming" to ActivityInfo("🏊", "Fitness", "Lap swimming and technique sessions in a supervised pool.", "Beginner-friendly", 300),
    "Pilates" to ActivityInfo("🧘", "Fitness", "A low-impact strength and flexibility class.", "All levels", 450),
    "Yoga" to ActivityInfo("🧘", "Fitness", "A grounding, breath-led practice suitable for every level.", "All levels", 350),
    "Gym" to ActivityInfo("🏋", "Fitness", "A shared strength-training session with a spotter.", "All levels", 200),
    "Boxing" to ActivityInfo("🥊", "Fitness", "Pad work and conditioning in a small group format.", "Beginner-friendly", 500),
    "Pottery" to ActivityInfo("🏺", "Creative", "A hands-on wheel-throwing and hand-building session.", "No experience needed", 600),
    "Painting" to ActivityInfo("🎨", "Creative", "A guided acrylic painting session, materials included.", "No experience needed", 550),
    "Photography" to ActivityInfo("📷", "Creative", "A walking shoot session with a small group.", "Bring your own camera", 400),
    "Music" to ActivityInfo("🎸", "Creative", "An informal jam session, all instruments welcome.", "Any level", 300),
    "Cooking" to ActivityInfo("🍳", "Learning", "A hands-on cooking class making a full meal together.", "Beginner-friendly", 900),
    "Workshops" to ActivityInfo("🛠", "Learning", "A small-group hands-on workshop.", "All levels", 500),
    "Running" to ActivityInfo("🏃", "Outdoor", "A community run at an easy, conversational pace.", "5–8 km pace", 0),
    "Cycling" to ActivityInfo("🚴", "Outdoor", "A social group ride, regroup stops included.", "20–30 km ride", 0),
    "Trekking" to ActivityInfo("⛰", "Outdoor", "A day trek with a small group, transport arranged separately.", "Moderate fitness", 0),
    "Dinner" to ActivityInfo("🍽", "Social", "A casual group dinner meetup, split the bill.", "Just show up", 0),
    "Board Games" to ActivityInfo("🎲", "Social", "A relaxed evening of strategy and party games.", "Casual", 200)
)

fun getActivityInfo(name: String, areaFallback: String): ActivityInfo {
    ACTIVITY_DB[name]?.let { return it }
    val area = areaFallback.substringBefore(',').ifBlank { "your area" }
    return ActivityInfo(
        icon = "✨",
        category = "Activity",
        description = "A ${name.lowercase()} session with people nearby.",
        skillLabel = "All levels",
        price = 300
    )
}

/** Person x Activity eligibility. A person only shows up for an activity they actually do. */
val PEOPLE_DB: List<Person> = listOf(
    Person("Harsh", "3.4 km", 12, "96%", 4.8, connected = false, skills = mapOf("Badminton" to "Intermediate", "Football" to "Intermediate", "Running" to "Beginner")),
    Person("Karan", "1.8 km", 7, "91%", 4.6, connected = false, skills = mapOf("Pottery" to "Intermediate", "Board Games" to "Casual", "Badminton" to "Beginner")),
    Person("Ayesha", "2.1 km", 20, "98%", 4.9, connected = true, skills = mapOf("Badminton" to "Advanced", "Swimming" to "Intermediate", "Pilates" to "Intermediate")),
    Person("Neha", "2.6 km", 15, "93%", 4.7, connected = false, skills = mapOf("Music" to "Intermediate", "Cooking" to "Beginner", "Yoga" to "All levels"))
)

fun eligiblePeople(activity: String): List<Person> = PEOPLE_DB.filter { activity in it.skills }

fun getPerson(name: String): Person = PEOPLE_DB.firstOrNull { it.name == name } ?: PEOPLE_DB.first()

/** Home's discovery cards — derived from the same session inventory as everywhere else. */
val HOME_CANDIDATE_ACTIVITIES = listOf("Badminton", "Swimming", "Pottery", "Pilates", "Football", "Cooking", "Board Games")
