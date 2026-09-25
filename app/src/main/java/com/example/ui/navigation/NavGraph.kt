package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.SupabaseClientProvider
import com.example.ui.auth.AuthScreen
import com.example.ui.add_edit_student.AddEditStudentScreen
import com.example.ui.home.HomeScreen
import com.example.ui.more.MoreScreen
import com.example.ui.profile.TeacherProfileScreen
import com.example.ui.reports.ReportsScreen
import com.example.ui.student_detail.StudentDetailScreen
import com.example.ui.students.StudentListScreen
import com.example.ui.students.StudentBarcodesScreen
import com.example.ui.attendance.FastAttendanceScreen
import com.example.ui.start_lesson.StartLessonScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun MainAppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route

    val sessionStatus by SupabaseClientProvider.client.auth.sessionStatus.collectAsState()

    val initialHasSession = remember {
        try {
            SupabaseClientProvider.client.auth.currentSessionOrNull() != null
        } catch (e: Exception) {
            false
        }
    }

    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.NotAuthenticated) {
            if (currentDestination != null && currentDestination != Screen.Auth.route) {
                navController.navigate(Screen.Auth.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val bottomTabs = listOf(
        BottomNavTab.HOME,
        BottomNavTab.STUDENTS,
        BottomNavTab.REPORTS,
        BottomNavTab.MORE
    )

    val isTopLevelDestination = currentDestination in listOf(
        Screen.Home.route,
        "students",
        "students?gradeId={gradeId}",
        Screen.Reports.route,
        Screen.More.route
    )

    Scaffold(
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = when (tab) {
                            BottomNavTab.HOME -> currentDestination == Screen.Home.route
                            BottomNavTab.STUDENTS -> currentDestination?.startsWith("students") == true
                            BottomNavTab.REPORTS -> currentDestination == Screen.Reports.route
                            BottomNavTab.MORE -> currentDestination == Screen.More.route
                        }

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.titleAr
                                )
                            },
                            label = {
                                Text(
                                    text = tab.titleAr,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (initialHasSession) Screen.Home.route else Screen.Auth.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            // Auth Destination
            composable(Screen.Auth.route) {
                AuthScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }

            // Home Destination
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToAddStudent = { gradeId ->
                        navController.navigate(Screen.AddStudent.createRoute(gradeId))
                    },
                    onNavigateToStudents = { gradeId ->
                        navController.navigate(Screen.Students.createRoute(gradeId))
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Students.createRoute(null))
                    },
                    onNavigateToReports = {
                        navController.navigate(Screen.Reports.route)
                    },
                    onNavigateToFastAttendance = { gradeId ->
                        navController.navigate(Screen.FastAttendance.createRoute(gradeId))
                    },
                    onNavigateToBarcodes = {
                        navController.navigate(Screen.StudentBarcodes.route)
                    },
                    onNavigateToStartLesson = {
                        navController.navigate(Screen.StartLesson.route)
                    },
                    onNavigateToStudentDetail = { studentId ->
                        navController.navigate(Screen.StudentDetail.createRoute(studentId))
                    }
                )
            }

            // Students Destination
            composable(
                route = Screen.Students.route,
                arguments = listOf(
                    navArgument("gradeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val gradeIdArg = backStackEntry.arguments?.getString("gradeId")
                StudentListScreen(
                    gradeIdArg = gradeIdArg,
                    onNavigateToStudentDetail = { studentId ->
                        navController.navigate(Screen.StudentDetail.createRoute(studentId))
                    },
                    onNavigateToAddStudent = { gradeId ->
                        navController.navigate(Screen.AddStudent.createRoute(gradeId))
                    },
                    onNavigateToFastAttendance = { gradeId ->
                        navController.navigate(Screen.FastAttendance.createRoute(gradeId))
                    },
                    onNavigateToBarcodes = {
                        navController.navigate(Screen.StudentBarcodes.route)
                    }
                )
            }

            // Fast Attendance Destination
            composable(
                route = Screen.FastAttendance.route,
                arguments = listOf(
                    navArgument("gradeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(300)
                    )
                }
            ) { backStackEntry ->
                val gradeIdArg = backStackEntry.arguments?.getString("gradeId")
                FastAttendanceScreen(
                    gradeIdArg = gradeIdArg,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Start Lesson Destination
            composable(Screen.StartLesson.route) {
                StartLessonScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Student Barcodes Destination
            composable(Screen.StudentBarcodes.route) {
                StudentBarcodesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Reports Destination
            composable(Screen.Reports.route) {
                ReportsScreen(
                    onNavigateToStudentDetail = { studentId ->
                        navController.navigate(Screen.StudentDetail.createRoute(studentId))
                    }
                )
            }

            // More / Settings Destination
            composable(Screen.More.route) {
                MoreScreen(
                    onNavigateToProfile = {
                        navController.navigate(Screen.TeacherProfile.route)
                    },
                    onLogoutSuccess = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Teacher Profile Destination
            composable(Screen.TeacherProfile.route) {
                TeacherProfileScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Add Student Destination
            composable(
                route = Screen.AddStudent.route,
                arguments = listOf(
                    navArgument("gradeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(300)
                    )
                }
            ) { backStackEntry ->
                val gradeIdArg = backStackEntry.arguments?.getString("gradeId")
                AddEditStudentScreen(
                    gradeIdArg = gradeIdArg,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStudentDetail = { studentId ->
                        navController.navigate(Screen.StudentDetail.createRoute(studentId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            // Edit Student Destination
            composable(
                route = Screen.EditStudent.route,
                arguments = listOf(
                    navArgument("studentId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val studentId = backStackEntry.arguments?.getString("studentId") ?: ""
                AddEditStudentScreen(
                    studentIdArg = studentId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStudentDetail = { id ->
                        navController.navigate(Screen.StudentDetail.createRoute(id)) {
                            popUpTo(Screen.StudentDetail.createRoute(id)) { inclusive = true }
                        }
                    }
                )
            }

            // Student Detail Destination
            composable(
                route = Screen.StudentDetail.route,
                arguments = listOf(
                    navArgument("studentId") {
                        type = NavType.StringType
                    }
                ),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(250)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(250)
                    )
                }
            ) { backStackEntry ->
                val studentId = backStackEntry.arguments?.getString("studentId") ?: ""
                StudentDetailScreen(
                    studentId = studentId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id ->
                        navController.navigate(Screen.EditStudent.createRoute(id))
                    }
                )
            }
        }
    }
}
