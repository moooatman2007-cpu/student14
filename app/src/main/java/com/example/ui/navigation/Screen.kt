package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Home : Screen("home")
    data object Students : Screen("students?gradeId={gradeId}") {
        fun createRoute(gradeId: String? = null): String {
            return if (gradeId != null) "students?gradeId=$gradeId" else "students"
        }
    }
    data object Reports : Screen("reports")
    data object More : Screen("more")
    data object TeacherProfile : Screen("teacher_profile")
    data object AddStudent : Screen("add_student?gradeId={gradeId}") {
        fun createRoute(gradeId: String? = null): String {
            return if (gradeId != null) "add_student?gradeId=$gradeId" else "add_student"
        }
    }
    data object EditStudent : Screen("edit_student/{studentId}") {
        fun createRoute(studentId: String): String = "edit_student/$studentId"
    }
    data object StudentDetail : Screen("student_detail/{studentId}") {
        fun createRoute(studentId: String): String = "student_detail/$studentId"
    }
    data object FastAttendance : Screen("fast_attendance?gradeId={gradeId}&groupId={groupId}&groupName={groupName}") {
        fun createRoute(gradeId: String? = null, groupId: String? = null, groupName: String? = null): String {
            val params = mutableListOf<String>()
            if (gradeId != null) params.add("gradeId=$gradeId")
            if (groupId != null) params.add("groupId=$groupId")
            if (groupName != null) {
                val encodedName = java.net.URLEncoder.encode(groupName, "UTF-8")
                params.add("groupName=$encodedName")
            }
            return if (params.isEmpty()) "fast_attendance" else "fast_attendance?" + params.joinToString("&")
        }
    }
    data object StudentBarcodes : Screen("student_barcodes")
    data object StartLesson : Screen("start_lesson")
    data object Groups : Screen("groups")
    data object AddGroup : Screen("add_group")
    data object EditGroup : Screen("edit_group/{groupId}") {
        fun createRoute(groupId: String): String = "edit_group/$groupId"
    }
}

enum class BottomNavTab(
    val route: String,
    val titleAr: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "الرئيسية", Icons.Filled.Home, Icons.Outlined.Home),
    STUDENTS("students", "الطلاب", Icons.Filled.Groups, Icons.Outlined.Groups),
    REPORTS("reports", "التقارير", Icons.Filled.Assessment, Icons.Outlined.Assessment),
    MORE("more", "المزيد", Icons.Filled.Menu, Icons.Outlined.Menu)
}
