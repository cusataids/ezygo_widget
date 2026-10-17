package com.ezygo.attendance.ui

import android.net.Uri
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.ezygo.attendance.ui.courses.CourseInfoScreen
import com.ezygo.attendance.ui.courses.CourseInfoViewModel
import com.ezygo.attendance.ui.calendar.CalendarScreen
import com.ezygo.attendance.ui.courses.CoursesScreen
import com.ezygo.attendance.ui.home.HomeScreen
import com.ezygo.attendance.ui.miniapps.MiniApp
import com.ezygo.attendance.ui.miniapps.notes.NoteEditorScreen
import com.ezygo.attendance.ui.miniapps.notes.NoteEditorViewModel
import com.ezygo.attendance.ui.miniapps.notes.NotesScreen
import com.ezygo.attendance.ui.miniapps.sgpa.SgpaScreen
import com.ezygo.attendance.ui.miniapps.tasks.TasksScreen
import com.ezygo.attendance.ui.miniapps.whatif.WhatIfScreen
import com.ezygo.attendance.ui.samarth.CourseHistoryScreen
import com.ezygo.attendance.ui.samarth.GradeCardViewerScreen
import com.ezygo.attendance.ui.samarth.GradeCardsScreen
import com.ezygo.attendance.ui.samarth.SamarthProfileScreen
import com.ezygo.attendance.ui.notifications.NotificationsScreen
import com.ezygo.attendance.ui.profile.ProfileScreen
import com.ezygo.attendance.ui.settings.SettingsScreen
import com.ezygo.attendance.ui.subject.AbsenceDaysScreen
import com.ezygo.attendance.ui.subject.AllClassesScreen
import com.ezygo.attendance.ui.subject.AttendedDaysScreen
import com.ezygo.attendance.ui.subject.ClassStatsScreen
import com.ezygo.attendance.ui.subject.CourseTabsScreen
import com.ezygo.attendance.ui.subject.SubjectDetailScreen
import com.ezygo.attendance.ui.subject.SubjectDetailViewModel
import com.ezygo.attendance.ui.timetable.TimetableScreen
import java.time.LocalDate

private object Routes {
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val PROFILE = "profile"
    const val SUBJECT = "subject/{courseId}"
    const val COURSES = "courses"
    const val COURSE_INFO = "courseInfo/{courseId}"
    const val COURSE_TABS = "courses/tabs"
    const val CLASS_STATS = "courses/classstats"
    const val ALL_CLASSES = "courses/classstats/all"
    const val ABSENCE_DAYS = "courses/absencedays"
    const val ATTENDED_DAYS = "courses/attendeddays"
    const val NOTIFICATIONS = "notifications"
    /**
     * Both arguments are optional: the app-bar icon opens today, tapping an event on
     * the home screen opens the day it falls on, and a widget row names the event
     * itself so the calendar can open it rather than merely land near it.
     */
    const val CALENDAR = "calendar?date={date}&event={event}"
    const val ARG_DATE = "date"
    const val ARG_EVENT = "event"

    fun calendar(date: LocalDate? = null, eventId: String? = null): String {
        val query = listOfNotNull(
            date?.let { "date=$it" },
            // Feed identifiers are opaque and routinely carry @ and / characters,
            // which would otherwise end the argument early.
            eventId?.let { "event=" + Uri.encode(it) },
        )
        return if (query.isEmpty()) "calendar" else "calendar?" + query.joinToString("&")
    }

    /** A note that does not exist yet has no id; an existing one is opened by its. */
    const val NOTE_EDITOR = "miniapp/notes/edit?noteId={noteId}"

    fun noteEditor(noteId: String) = "miniapp/notes/edit?noteId=" + Uri.encode(noteId)

    const val NEW_NOTE = "miniapp/notes/edit"

    /** One downloaded grade card, identified by the portal's own term number. */
    const val GRADE_CARD = "samarth/gradecard/{term}"

    fun gradeCard(term: String) = "samarth/gradecard/" + Uri.encode(term)

    const val SAMARTH_PROFILE = "samarth/profile"
    const val SETTINGS = "settings"

    /** Inside the grades app, not mini apps of their own. */
    const val SGPA_ESTIMATOR = "miniapp/gradecards/sgpa"
    const val COURSE_HISTORY = "miniapp/gradecards/history"

    fun subject(courseId: Int) = "subject/$courseId"

    fun courseInfo(courseId: Int) = "courseInfo/$courseId"
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem(Routes.HOME, "Home", Icons.Default.Home),
    TabItem(Routes.TIMETABLE, "Timetable", Icons.Default.CalendarMonth),
    TabItem(Routes.PROFILE, "Profile", Icons.Default.Person),
)

/**
 * Three persistent tabs with Home as the landing screen. Subject detail is pushed
 * above the bar so the tabs stay reachable everywhere else.
 *
 * The tabs share one app bar, which is where the calendar and notifications
 * actions live; the pushed screens each supply their own, so both bars step
 * aside for them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    navController: NavHostController = rememberNavController(),
    /** Subject the app was launched into from a home-screen widget, if any. */
    requestedCourseId: Int? = null,
    onCourseHandled: () -> Unit = {},
    /** Calendar event a widget row asked to open, if any. */
    requestedEventId: String? = null,
    onEventHandled: () -> Unit = {},
) {
    // Consumed once: without clearing it, coming back from the subject screen would
    // immediately push it again and the back button would appear broken.
    LaunchedEffect(requestedCourseId) {
        val courseId = requestedCourseId ?: return@LaunchedEffect
        navController.navigate(Routes.subject(courseId)) { launchSingleTop = true }
        onCourseHandled()
    }

    LaunchedEffect(requestedEventId) {
        val eventId = requestedEventId ?: return@LaunchedEffect
        navController.navigate(Routes.calendar(eventId = eventId)) { launchSingleTop = true }
        onEventHandled()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Null on the pushed screens, which bring their own app bar and hide the tabs.
    val currentTab = TABS.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        topBar = {
            currentTab?.let { tab ->
                TopAppBar(
                    title = { Text(tab.label) },
                    actions = {
                        IconButton(
                            onClick = {
                                navController.navigate(Routes.calendar()) {
                                    launchSingleTop = true
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Academic calendar",
                            )
                        }
                        IconButton(
                            onClick = {
                                navController.navigate(Routes.NOTIFICATIONS) {
                                    launchSingleTop = true
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notifications",
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = {
                                navController.navigate(tab.route) {
                                    // Standard Android tab behaviour: one entry per tab,
                                    // state preserved when switching back and forth.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // This Scaffold has already inset the content for the status bar and
                // the tab bar, but it does not tell its children so. Without this,
                // every pushed screen that draws its own TopAppBar applies the
                // status-bar inset a second time and opens a blank strip above its
                // title. Declaring the padding as consumed makes those bars sit flush.
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onCourseClick = { navController.navigate(Routes.subject(it)) },
                    onOpenCalendar = { date -> navController.navigate(Routes.calendar(date)) },
                    onOpenMiniApp = { navController.navigate(it.route) },
                    onOpenCourseTabs = { navController.navigate(Routes.COURSE_TABS) },
                )
            }
            composable(Routes.COURSE_TABS) {
                CourseTabsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCourse = { navController.navigate(Routes.subject(it)) },
                    onOpenClassStats = { navController.navigate(Routes.CLASS_STATS) },
                    onOpenAbsenceDays = { navController.navigate(Routes.ABSENCE_DAYS) },
                    onOpenAttendedDays = { navController.navigate(Routes.ATTENDED_DAYS) },
                )
            }
            composable(Routes.CLASS_STATS) {
                ClassStatsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAllClasses = { navController.navigate(Routes.ALL_CLASSES) },
                )
            }
            composable(Routes.ALL_CLASSES) {
                AllClassesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABSENCE_DAYS) {
                AbsenceDaysScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ATTENDED_DAYS) {
                AttendedDaysScreen(onBack = { navController.popBackStack() })
            }

            // One registration per MiniApp entry. Adding a tool is a constant in that
            // enum and a block here; nothing else needs to know about it.
            composable(MiniApp.WHAT_IF.route) {
                WhatIfScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.NOTE_EDITOR,
                arguments = listOf(
                    navArgument(NoteEditorViewModel.ARG_NOTE_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                NoteEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(MiniApp.TASKS.route) {
                TasksScreen(onBack = { navController.popBackStack() })
            }
            composable(MiniApp.NOTES.route) {
                NotesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.noteEditor(it)) },
                    onCreateNote = { navController.navigate(Routes.NEW_NOTE) },
                )
            }
            composable(MiniApp.GRADE_CARDS.route) {
                GradeCardsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCard = { navController.navigate(Routes.gradeCard(it)) },
                    onOpenEstimator = { navController.navigate(Routes.SGPA_ESTIMATOR) },
                    onOpenHistory = { navController.navigate(Routes.COURSE_HISTORY) },
                )
            }
            composable(Routes.SGPA_ESTIMATOR) {
                SgpaScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.COURSE_HISTORY) {
                CourseHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.GRADE_CARD,
                arguments = listOf(navArgument("term") { type = NavType.StringType }),
            ) { entry ->
                GradeCardViewerScreen(
                    term = entry.arguments?.getString("term").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SAMARTH_PROFILE) {
                SamarthProfileScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TIMETABLE) { TimetableScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenCourses = { navController.navigate(Routes.COURSES) },
                    onOpenSamarthProfile = { navController.navigate(Routes.SAMARTH_PROFILE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.COURSES) {
                CoursesScreen(
                    onBack = { navController.popBackStack() },
                    onCourseClick = { navController.navigate(Routes.courseInfo(it)) },
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CALENDAR,
                arguments = listOf(
                    navArgument(Routes.ARG_DATE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Routes.ARG_EVENT) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                // A route argument is text, and an unparseable one should open the
                // calendar on today rather than fail to open it at all.
                val date = entry.arguments?.getString(Routes.ARG_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                CalendarScreen(
                    onBack = { navController.popBackStack() },
                    initialDate = date,
                    initialEventId = entry.arguments?.getString(Routes.ARG_EVENT),
                )
            }
            composable(
                route = Routes.COURSE_INFO,
                arguments = listOf(
                    navArgument(CourseInfoViewModel.ARG_COURSE_ID) { type = NavType.IntType },
                ),
            ) {
                CourseInfoScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.SUBJECT,
                arguments = listOf(
                    navArgument(SubjectDetailViewModel.ARG_COURSE_ID) { type = NavType.IntType },
                ),
            ) {
                SubjectDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCourseInfo = { navController.navigate(Routes.courseInfo(it)) },
                )
            }
        }
    }
}
