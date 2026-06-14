package cc.dvitski.tabyproto

object Animations {

    // ── Play-once ──────────────────────────────────────────────────────────────

    val BASKETBALL_DUNK       = Animation.Once("basketball_dunk",       "Basketball Dunk",       RawAnimation.BASKETBALL_DUNK)
    val BASKETBALL_THROW      = Animation.Once("basketball_throw",      "Basketball Throw",      RawAnimation.BASKETBALL_THROW)
    val BLUSH                 = Animation.Once("blush",                 "Blush",                 RawAnimation.BLUSH)
    val BOXING                = Animation.Once("boxing",                "Boxing",                RawAnimation.BOXING)
    val BREAK_START           = Animation.Once("break_start",           "Break Start",           RawAnimation.BREAK_START)
    val CIRCLE                = Animation.Once("circle",                "Circle",                RawAnimation.CIRCLE)
    val CONFIRMATION          = Animation.Once("confirmation",          "Confirmation",          RawAnimation.CONFIRMATION)
    val COPY_PASTE            = Animation.Once("copy_paste",            "Copy Paste",            RawAnimation.COPY_PASTE)
    val DAY_PLANNED           = Animation.Once("day_planned",           "Day Planned",           RawAnimation.DAY_PLANNED)
    val DELETE_01             = Animation.Once("delete_01",             "Delete 01",             RawAnimation.DELETE_01)
    val DISAPPOINTED          = Animation.Once("disappointed",          "Disappointed",          RawAnimation.DISAPPOINTED)
    val DRINK_WATER           = Animation.Once("drink_water",           "Drink Water",           RawAnimation.DRINK_WATER)
    val F1_CAR                = Animation.Once("f1_car",                "F1 Car",                RawAnimation.F1_CAR)
    val FISHING_LONG          = Animation.Once("fishing_long",          "Fishing Long",          RawAnimation.FISHING_LONG)
    val FISHING_SHORT         = Animation.Once("fishing_short",         "Fishing Short",         RawAnimation.FISHING_SHORT)
    val FLOWER_GROW           = Animation.Once("flower_grow",           "Flower Grow",           RawAnimation.FLOWER_GROW)
    val HELLO_ANNOYED         = Animation.Once("hello_annoyed",         "Hello Annoyed",         RawAnimation.HELLO_ANNOYED)
    val HELLO_DISAPPOINTED    = Animation.Once("hello_disappointed",    "Hello Disappointed",    RawAnimation.HELLO_DISAPPOINTED)
    val LOCKIN                = Animation.Once("lockin",                "Lockin",                RawAnimation.LOCKIN)
    val LOVE_01               = Animation.Once("love_01",               "Love 01",               RawAnimation.LOVE_01)
    val NO                    = Animation.Once("no",                    "No",                    RawAnimation.NO)
    val PERFECT_DAY_01        = Animation.Once("perfect_day_01",        "Perfect Day 01",        RawAnimation.PERFECT_DAY_01)
    val PERFECT_DAY_01_SIMPLE = Animation.Once("perfect_day_01_simple", "Perfect Day 01 Simple", RawAnimation.PERFECT_DAY_01_SIMPLE)
    val PERFECT_DAY_02        = Animation.Once("perfect_day_02",        "Perfect Day 02",        RawAnimation.PERFECT_DAY_02)
    val PERFECT_DAY_03        = Animation.Once("perfect_day_03",        "Perfect Day 03",        RawAnimation.PERFECT_DAY_03)
    val POSTURE_CHECK         = Animation.Once("posture_check",         "Posture Check",         RawAnimation.POSTURE_CHECK)
    val REVIEW                = Animation.Once("review",                "Review",                RawAnimation.REVIEW)
    val SQUARE                = Animation.Once("square",                "Square",                RawAnimation.SQUARE)
    val STARTUP               = Animation.Once("startup",               "Startup",               RawAnimation.STARTUP)
    val STRETCHING            = Animation.Once("stretching",            "Stretching",            RawAnimation.STRETCHING)
    val TASK_COMPLETED        = Animation.Once("task_completed",        "Task Completed",        RawAnimation.TASK_COMPLETED)
    val TASK_CREATED          = Animation.Once("task_created",          "Task Created",          RawAnimation.TASK_CREATED)
    val TASK_PAGE             = Animation.Once("task_page",             "Task Page",             RawAnimation.TASK_PAGE)
    val THUMBS_UP             = Animation.Once("thumbs_up",             "Thumbs Up",             RawAnimation.THUMBS_UP)
    val TROPHY                = Animation.Once("trophy",                "Trophy",                RawAnimation.TROPHY)
    val TURN_OFF_REDDIT       = Animation.Once("turn_off_reddit",       "Turn Off Reddit",       RawAnimation.TURN_OFF_REDDIT)
    val TURN_OFF_SCROLL       = Animation.Once("turn_off_scroll",       "Turn Off Scroll",       RawAnimation.TURN_OFF_SCROLL)
    val TURN_OFF_TV           = Animation.Once("turn_off_tv",           "Turn Off Tv",           RawAnimation.TURN_OFF_TV)
    val WAITING_01            = Animation.Once("waiting_01",            "Waiting 01",            RawAnimation.WAITING_01)
    val WOW                   = Animation.Once("wow",                   "Wow",                   RawAnimation.WOW)
    val YEAH                  = Animation.Once("yeah",                  "Yeah",                  RawAnimation.YEAH)

    // ── Looping (no intro) ─────────────────────────────────────────────────────

    val ANGRY_01_LOOP              = Animation.Looping("angry_01_loop",              "Angry 01 Loop",              intro = null, body = RawAnimation.ANGRY_01_LOOP)
    val ANGRY_02_LOOP              = Animation.Looping("angry_02_loop",              "Angry 02 Loop",              intro = null, body = RawAnimation.ANGRY_02_LOOP)
    val BUSY_LOOP                  = Animation.Looping("busy_loop",                  "Busy Loop",                  intro = null, body = RawAnimation.BUSY_LOOP)
    val CREATING_TASK_LOOP         = Animation.Looping("creating_task_loop",         "Creating Task Loop",         intro = null, body = RawAnimation.CREATING_TASK_LOOP)
    val DIZZY_LOOP                 = Animation.Looping("dizzy_loop",                 "Dizzy Loop",                 intro = null, body = RawAnimation.DIZZY_LOOP)
    val IDLE_01_LOOP               = Animation.Looping("idle_01_loop",               "Idle 01 Loop",               intro = null, body = RawAnimation.IDLE_01_LOOP)
    val IDLE_02_LOOP               = Animation.Looping("idle_02_loop",               "Idle 02 Loop",               intro = null, body = RawAnimation.IDLE_02_LOOP)
    val IDLE_VARIATION_LOOP        = Animation.Looping("idle_variation_loop",        "Idle Variation Loop",        intro = null, body = RawAnimation.IDLE_VARIATION_LOOP)
    val LISTENING_MUSIC_LOOP       = Animation.Looping("listening_music_loop",       "Listening Music Loop",       intro = null, body = RawAnimation.LISTENING_MUSIC_LOOP)
    val RELAXING_01_LOOP           = Animation.Looping("relaxing_01_loop",           "Relaxing 01 Loop",           intro = null, body = RawAnimation.RELAXING_01_LOOP)
    val SLEEPING_LOOP              = Animation.Looping("sleeping_loop",              "Sleeping Loop",              intro = null, body = RawAnimation.SLEEPING_LOOP)
    val TALKING_DEFAULT_LOOP       = Animation.Looping("talking_default_loop",       "Talking Default Loop",       intro = null, body = RawAnimation.TALKING_DEFAULT_LOOP)
    val TALKING_MAN_LOOP           = Animation.Looping("talking_man_loop",           "Talking Man Loop",           intro = null, body = RawAnimation.TALKING_MAN_LOOP)
    val WORKING_LAPTOP_BORED_LOOP  = Animation.Looping("working_laptop_bored_loop",  "Working Laptop Bored Loop",  intro = null, body = RawAnimation.WORKING_LAPTOP_BORED_LOOP)
    val WORKING_LAPTOP_EXCITED_LOOP= Animation.Looping("working_laptop_excited_loop","Working Laptop Excited Loop",intro = null, body = RawAnimation.WORKING_LAPTOP_EXCITED_LOOP)
    val WORKING_LAPTOP_NORMAL_LOOP = Animation.Looping("working_laptop_normal_loop", "Working Laptop Normal Loop", intro = null, body = RawAnimation.WORKING_LAPTOP_NORMAL_LOOP)

    // ── Looping (with intro) ───────────────────────────────────────────────────

    val CALENDAR_LOOP            = Animation.Looping("calendar_loop",            "Calendar Loop",            intro = RawAnimation.CALENDAR_IN,           body = RawAnimation.CALENDAR_LOOP)
    val CLAUDE_LOOP              = Animation.Looping("claude_loop",              "Claude Loop",              intro = RawAnimation.CLAUDE_IN,             body = RawAnimation.CLAUDE_LOOP)
    val CODEX_LOOP               = Animation.Looping("codex_loop",              "Codex Loop",               intro = RawAnimation.CODEX_IN,              body = RawAnimation.CODEX_LOOP)
    val LISTENING_LOOP           = Animation.Looping("listening_loop",           "Listening Loop",           intro = RawAnimation.LISTENING_IN,          body = RawAnimation.LISTENING_LOOP)
    val RELAXING_COUCH_LOOP      = Animation.Looping("relaxing_couch_loop",      "Relaxing Couch Loop",      intro = RawAnimation.RELAXING_COUCH_IN,     body = RawAnimation.RELAXING_COUCH_LOOP)
    val TABY_RESPONSE_READY_LOOP = Animation.Looping("taby_response_ready_loop", "Taby Response Ready Loop", intro = RawAnimation.TABY_RESPONSE_READY_IN,body = RawAnimation.TABY_RESPONSE_READY_LOOP)
    val WORKING_LOOP             = Animation.Looping("working_loop",             "Working Loop",             intro = RawAnimation.WORKING_IN,            body = RawAnimation.WORKING_LOOP)
    val WORKING_LAPTOP_LOOP      = Animation.Looping("working_laptop_loop",      "Working Laptop Loop",      intro = RawAnimation.WORKING_LAPTOP_IN,     body = RawAnimation.WORKING_LAPTOP_LOOP)
    val WORKSPACES_LOOP          = Animation.Looping("workspaces_loop",          "Folders Loop",             intro = RawAnimation.WORKSPACES_IN,         body = RawAnimation.WORKSPACES_LOOP)

    // ── Registry ───────────────────────────────────────────────────────────────

    val all: List<Animation> = listOf(
        BASKETBALL_DUNK, BASKETBALL_THROW, BLUSH, BOXING, BREAK_START, CIRCLE,
        CONFIRMATION, COPY_PASTE, DAY_PLANNED, DELETE_01, DISAPPOINTED, DRINK_WATER,
        F1_CAR, FISHING_LONG, FISHING_SHORT, FLOWER_GROW, HELLO_ANNOYED, HELLO_DISAPPOINTED,
        LOCKIN, LOVE_01, NO, PERFECT_DAY_01, PERFECT_DAY_01_SIMPLE, PERFECT_DAY_02,
        PERFECT_DAY_03, POSTURE_CHECK, REVIEW, SQUARE, STARTUP, STRETCHING,
        TASK_COMPLETED, TASK_CREATED, TASK_PAGE, THUMBS_UP, TROPHY,
        TURN_OFF_REDDIT, TURN_OFF_SCROLL, TURN_OFF_TV, WAITING_01, WOW, YEAH,
        ANGRY_01_LOOP, ANGRY_02_LOOP, BUSY_LOOP, CREATING_TASK_LOOP, DIZZY_LOOP,
        IDLE_01_LOOP, IDLE_02_LOOP, IDLE_VARIATION_LOOP, LISTENING_MUSIC_LOOP,
        RELAXING_01_LOOP, SLEEPING_LOOP, TALKING_DEFAULT_LOOP, TALKING_MAN_LOOP,
        WORKING_LAPTOP_BORED_LOOP, WORKING_LAPTOP_EXCITED_LOOP, WORKING_LAPTOP_NORMAL_LOOP,
        CALENDAR_LOOP, CLAUDE_LOOP, CODEX_LOOP, LISTENING_LOOP, RELAXING_COUCH_LOOP,
        TABY_RESPONSE_READY_LOOP, WORKING_LOOP, WORKING_LAPTOP_LOOP, WORKSPACES_LOOP,
    )

    private val byIdMap: Map<String, Animation> = all.associateBy { it.id }

    fun byId(id: String): Animation? = byIdMap[id]
}
