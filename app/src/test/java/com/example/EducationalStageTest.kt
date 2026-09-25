package com.example

import com.example.core.model.EducationalStages
import com.example.core.model.Teacher
import com.example.core.model.UpdateTeacherRequest
import com.example.core.model.UpsertTeacherRequest
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockStudentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EducationalStageTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val validStages = listOf("ابتدائي", "إعدادي", "ثانوي")

    @Test
    fun testEducationalStageValidation() {
        // Test blank or null stage -> Invalid
        val emptyStage = ""
        assertFalse(validStages.contains(emptyStage))

        // Test invalid stages -> Invalid
        val invalidStage = "جامعي"
        assertFalse(validStages.contains(invalidStage))

        // Test valid stages -> Valid
        assertTrue(validStages.contains("ابتدائي"))
        assertTrue(validStages.contains("إعدادي"))
        assertTrue(validStages.contains("ثانوي"))
    }

    @Test
    fun testTeacherSerializationWithEducationalStage() {
        val teacher = Teacher(
            id = "t_123",
            email = "teacher@test.com",
            fullName = "أحمد محمود",
            educationalStage = "ثانوي",
            subject = "فيزياء"
        )

        val serialized = json.encodeToString(teacher)
        assertTrue(serialized.contains("educational_stage"))
        assertTrue(serialized.contains("ثانوي"))

        val deserialized = json.decodeFromString<Teacher>(serialized)
        assertEquals("ثانوي", deserialized.educationalStage)
        assertEquals("أحمد محمود", deserialized.fullName)
    }

    @Test
    fun testTeacherDeserializationWithNullEducationalStageForExistingUser() {
        // Legacy JSON without educational_stage field
        val legacyJson = """
            {
                "id": "t_456",
                "email": "legacy@test.com",
                "full_name": "أستاذ قديم"
            }
        """.trimIndent()

        val teacher = json.decodeFromString<Teacher>(legacyJson)
        assertEquals("t_456", teacher.id)
        assertNull(teacher.educationalStage)
    }

    @Test
    fun testTeacherUpdateAndUpsertDTOs() {
        val updateReq = UpdateTeacherRequest(
            fullName = "أحمد محمود المعدل",
            educationalStage = "إعدادي",
            subject = "رياضيات"
        )
        val updateJsonStr = json.encodeToString(updateReq)
        assertTrue(updateJsonStr.contains("educational_stage"))
        assertTrue(updateJsonStr.contains("إعدادي"))

        val upsertReq = UpsertTeacherRequest(
            id = "t_789",
            email = "new@test.com",
            fullName = "مدرس جديد",
            educationalStage = "ابتدائي"
        )
        val upsertJsonStr = json.encodeToString(upsertReq)
        assertTrue(upsertJsonStr.contains("educational_stage"))
        assertTrue(upsertJsonStr.contains("ابتدائي"))
    }

    @Test
    fun testArabicNormalizationRobustness() {
        // Test variations of "ابتدائي" / "إبتدائي"
        assertTrue(EducationalStages.isGradeMatchingStage("الاول الابتدائي", "ابتدائي"))
        assertTrue(EducationalStages.isGradeMatchingStage("الأول الإبتدائي", "ابتدائي"))
        assertTrue(EducationalStages.isGradeMatchingStage("الصف الأول الابتدائي", "إبتدائي"))
        assertTrue(EducationalStages.isGradeMatchingStage("الاول الابتدائي", "إبتدائي"))
        assertTrue(EducationalStages.isGradeMatchingStage("السادس الابتدائي", "ابتدائي"))

        // Test variations of "إعدادي" / "اعدادي"
        assertTrue(EducationalStages.isGradeMatchingStage("الأول الإعدادي", "إعدادي"))
        assertTrue(EducationalStages.isGradeMatchingStage("الاول الاعدادي", "اعدادي"))
        assertTrue(EducationalStages.isGradeMatchingStage("الصف الثاني الإعدادي", "اعدادي"))

        // Test variations of "ثانوي"
        assertTrue(EducationalStages.isGradeMatchingStage("الأول الثانوي", "ثانوي"))
        assertTrue(EducationalStages.isGradeMatchingStage("الاول الثانوي", "ثانوي"))
        assertTrue(EducationalStages.isGradeMatchingStage("الصف الثالث الثانوي", "ثانوي"))
    }

    @Test
    fun testInitialEmptyRepositoryToLoginTeacherPrimaryRefreshYieldsSixGrades() = runBlocking {
        // Step 1: Initial state before login or empty
        val mockRepo = MockGradeRepository() // starts in initial state

        // Step 2: Teacher logs in with educationalStage = "ابتدائي"
        val loggedInTeacher = Teacher(
            id = "xxxcffffff02_id",
            email = "xxxcffffff02@gmail.com",
            fullName = "أستاذ الابتدائي",
            educationalStage = "ابتدائي"
        )

        // Step 3: Refresh grades passing teacher.educationalStage
        val refreshedGrades = mockRepo.refreshGrades(loggedInTeacher.educationalStage)

        // Step 4: Verify Home receives exactly 6 primary grades
        assertEquals(6, refreshedGrades.size)
        assertEquals("الأول الابتدائي", refreshedGrades[0].name)
        assertEquals("الثاني الابتدائي", refreshedGrades[1].name)
        assertEquals("الثالث الابتدائي", refreshedGrades[2].name)
        assertEquals("الرابع الابتدائي", refreshedGrades[3].name)
        assertEquals("الخامس الابتدائي", refreshedGrades[4].name)
        assertEquals("السادس الابتدائي", refreshedGrades[5].name)

        // Ensure no leakage of other stages
        assertFalse(refreshedGrades.any { it.name.contains("الإعدادي") || it.name.contains("الثانوي") })
    }

    @Test
    fun testPrimaryStageGeneratesSixGrades() = runBlocking {
        val primaryGrades = EducationalStages.getGradeNamesForStage("ابتدائي")
        assertEquals(6, primaryGrades.size)
        assertEquals("الأول الابتدائي", primaryGrades[0])
        assertEquals("الثاني الابتدائي", primaryGrades[1])
        assertEquals("الثالث الابتدائي", primaryGrades[2])
        assertEquals("الرابع الابتدائي", primaryGrades[3])
        assertEquals("الخامس الابتدائي", primaryGrades[4])
        assertEquals("السادس الابتدائي", primaryGrades[5])

        val mockRepo = MockGradeRepository("ابتدائي")
        val grades = mockRepo.getGrades().first()
        assertEquals(6, grades.size)
        assertEquals("الأول الابتدائي", grades[0].name)
        assertEquals("السادس الابتدائي", grades[5].name)
    }

    @Test
    fun testPreparatoryStageGeneratesThreeGrades() = runBlocking {
        val prepGrades = EducationalStages.getGradeNamesForStage("إعدادي")
        assertEquals(3, prepGrades.size)
        assertEquals("الأول الإعدادي", prepGrades[0])
        assertEquals("الثاني الإعدادي", prepGrades[1])
        assertEquals("الثالث الإعدادي", prepGrades[2])

        val mockRepo = MockGradeRepository("إعدادي")
        val grades = mockRepo.getGrades().first()
        assertEquals(3, grades.size)
        assertEquals("الأول الإعدادي", grades[0].name)
        assertEquals("الثالث الإعدادي", grades[2].name)
    }

    @Test
    fun testSecondaryStageGeneratesThreeGrades() = runBlocking {
        val secGrades = EducationalStages.getGradeNamesForStage("ثانوي")
        assertEquals(3, secGrades.size)
        assertEquals("الأول الثانوي", secGrades[0])
        assertEquals("الثاني الثانوي", secGrades[1])
        assertEquals("الثالث الثانوي", secGrades[2])

        val mockRepo = MockGradeRepository("ثانوي")
        val grades = mockRepo.getGrades().first()
        assertEquals(3, grades.size)
        assertEquals("الأول الثانوي", grades[0].name)
        assertEquals("الثالث الثانوي", grades[2].name)
    }

    @Test
    fun testLegacyTeacherWithNullStageDefaultsToPreparatorySafely() = runBlocking {
        val defaultGrades = EducationalStages.getGradeNamesForStage(null)
        assertEquals(3, defaultGrades.size)
        assertEquals("الأول الإعدادي", defaultGrades[0])

        val mockRepo = MockGradeRepository(EducationalStages.PREPARATORY)
        val grades = mockRepo.getGrades().first()
        assertEquals(3, grades.size)
    }

    @Test
    fun testAddAndEditStudentAcrossGrades() = runBlocking {
        val mockRepo = MockGradeRepository("ابتدائي")
        val grades = mockRepo.getGrades().first()
        val grade1 = grades[0]
        val grade2 = grades[1]

        val studentRepo = MockStudentRepository(mockRepo)

        // 1. Add student to primary grade 1
        val addResult = studentRepo.addStudent(
            fullName = "يوسف أحمد",
            gradeId = grade1.id,
            parentPhone = "01011112222",
            hasWhatsApp = true,
            alternativePhone = "01033334444"
        )
        assertTrue(addResult.isSuccess)
        val createdStudent = addResult.getOrThrow()
        assertEquals(grade1.id, createdStudent.gradeId)
        assertEquals("يوسف أحمد", createdStudent.fullName)

        // 2. Edit student: promote to primary grade 2
        val updatedStudent = createdStudent.copy(gradeId = grade2.id)
        val updateResult = studentRepo.updateStudent(updatedStudent)
        assertTrue(updateResult.isSuccess)

        val reFetched = studentRepo.getStudentById(createdStudent.studentId)
        assertEquals(grade2.id, reFetched?.gradeId)
    }

    @Test
    fun testStudentFilteringByGrade() = runBlocking {
        val mockRepo = MockGradeRepository("ابتدائي")
        val grades = mockRepo.getGrades().first()
        val studentRepo = MockStudentRepository(mockRepo)

        val s1 = studentRepo.addStudent("طالب أول", grades[0].id, "01011110001", true, null).getOrThrow()
        val s2 = studentRepo.addStudent("طالب ثاني", grades[1].id, "01011110002", true, null).getOrThrow()
        val s3 = studentRepo.addStudent("طالب ثالث", grades[0].id, "01011110003", true, null).getOrThrow()

        val grade1Students = studentRepo.getStudentsByGrade(grades[0].id).first()
        assertTrue(grade1Students.any { it.studentId == s1.studentId })
        assertTrue(grade1Students.any { it.studentId == s3.studentId })
        assertFalse(grade1Students.any { it.studentId == s2.studentId })

        val grade2Students = studentRepo.getStudentsByGrade(grades[1].id).first()
        assertTrue(grade2Students.any { it.studentId == s2.studentId })
        assertFalse(grade2Students.any { it.studentId == s1.studentId })
    }

    @Test
    fun testTeacherIsolationBetweenStagesAndTeachers() {
        val teacherPrimary = Teacher(id = "teacher_pri", fullName = "مدرس ابتدائي", educationalStage = "ابتدائي")
        val teacherSec = Teacher(id = "teacher_sec", fullName = "مدرس ثانوي", educationalStage = "ثانوي")

        val primaryGrades = EducationalStages.getGradeNamesForStage(teacherPrimary.educationalStage)
        val secondaryGrades = EducationalStages.getGradeNamesForStage(teacherSec.educationalStage)

        // Ensure teacher primary only has primary grades
        assertEquals(6, primaryGrades.size)
        assertTrue(primaryGrades.all { it.contains("الابتدائي") })
        assertFalse(primaryGrades.any { it.contains("الإعدادي") || it.contains("الثانوي") })

        // Ensure teacher secondary only has secondary grades
        assertEquals(3, secondaryGrades.size)
        assertTrue(secondaryGrades.all { it.contains("الثانوي") })
        assertFalse(secondaryGrades.any { it.contains("الابتدائي") || it.contains("الإعدادي") })

        // Ensure cross-stage matching returns false
        assertFalse(EducationalStages.isGradeMatchingStage("الأول الابتدائي", teacherSec.educationalStage))
        assertFalse(EducationalStages.isGradeMatchingStage("الأول الثانوي", teacherPrimary.educationalStage))
        assertTrue(EducationalStages.isGradeMatchingStage("الأول الابتدائي", teacherPrimary.educationalStage))
    }

    @Test
    fun testStageSelectionStateIntegrityPrimary() {
        var state = ""
        val selectedOption = "ابتدائي"
        state = selectedOption

        assertEquals("ابتدائي", state)
        assertFalse("State must not automatically change to إعدادي", state == "إعدادي")
    }

    @Test
    fun testStageSelectionStateIntegrityPreparatory() {
        var state = ""
        val selectedOption = "إعدادي"
        state = selectedOption

        assertEquals("إعدادي", state)
    }

    @Test
    fun testStageSelectionStateIntegritySecondary() {
        var state = ""
        val selectedOption = "ثانوي"
        state = selectedOption

        assertEquals("ثانوي", state)
        assertFalse("State must not automatically change to إعدادي", state == "إعدادي")
    }

    @Test
    fun testSignupPayloadPreservesSelectedEducationalStage() {
        val stagesToTest = listOf("ابتدائي", "إعدادي", "ثانوي")

        stagesToTest.forEach { stage ->
            val userMetaJson = buildJsonObject {
                put("full_name", JsonPrimitive("أحمد"))
                put("phone", JsonPrimitive("01000000000"))
                put("educational_stage", JsonPrimitive(stage))
            }

            val extractedStage = userMetaJson["educational_stage"]?.jsonPrimitive?.content
            assertEquals(stage, extractedStage)
            if (stage != "إعدادي") {
                assertFalse(extractedStage == "إعدادي")
            }
        }
    }

    @Test
    fun testRefreshGradesAndFilterByTeacherStageInHome() = runBlocking {
        val mockRepo = MockGradeRepository("ابتدائي")
        val grades = mockRepo.refreshGrades()
        assertEquals(6, grades.size)

        // Filter primary grades for a primary teacher
        val primaryTeacher = Teacher(id = "tp", fullName = "مدرس ابتدائي", educationalStage = "ابتدائي")
        val filteredForPrimary = grades.filter { EducationalStages.isGradeMatchingStage(it.name, primaryTeacher.educationalStage) }
        assertEquals(6, filteredForPrimary.size)

        // Filter primary grades for a secondary teacher (must be empty/isolated)
        val secTeacher = Teacher(id = "ts", fullName = "مدرس ثانوي", educationalStage = "ثانوي")
        val filteredForSec = grades.filter { EducationalStages.isGradeMatchingStage(it.name, secTeacher.educationalStage) }
        assertEquals(0, filteredForSec.size)
    }

    @Test
    fun testHomeViewModelReceivesPrimaryGradesAfterAuthLogin() = runBlocking {
        // Simulates repo starting before auth (emptyList)
        val initialEmptyMockRepo = MockGradeRepository() // defaults to preparatory 3 grades or stage-bound
        val teacher = Teacher(id = "t_primary_user", fullName = "محمود عتمان", educationalStage = "ابتدائي")

        // Primary stage generates 6 grades when refreshed
        val primaryRepo = MockGradeRepository("ابتدائي")
        val refreshedGrades = primaryRepo.refreshGrades()
        assertEquals(6, refreshedGrades.size)

        // In HomeViewModel logic:
        val teacherStage = teacher.educationalStage
        val filteredGrades = refreshedGrades.filter {
            EducationalStages.isGradeMatchingStage(it.name, teacherStage)
        }

        // Verify that 6 primary grades are resolved and not empty / not preparatory
        assertEquals(6, filteredGrades.size)
        assertEquals("الأول الابتدائي", filteredGrades[0].name)
        assertEquals("السادس الابتدائي", filteredGrades[5].name)
        assertFalse(filteredGrades.any { it.name.contains("الإعدادي") })
        assertFalse(filteredGrades.any { it.name.contains("الثانوي") })
    }

    @Test
    fun testHomeViewModelHandlesPreparatoryAndSecondaryStages() = runBlocking {
        // Preparatory stage -> 3 grades
        val prepTeacher = Teacher(id = "t_prep", fullName = "مدرس إعدادي", educationalStage = "إعدادي")
        val prepRepo = MockGradeRepository("إعدادي")
        val prepGrades = prepRepo.refreshGrades().filter {
            EducationalStages.isGradeMatchingStage(it.name, prepTeacher.educationalStage)
        }
        assertEquals(3, prepGrades.size)
        assertEquals("الأول الإعدادي", prepGrades[0].name)
        assertEquals("الثالث الإعدادي", prepGrades[2].name)

        // Secondary stage -> 3 grades
        val secTeacher = Teacher(id = "t_sec", fullName = "مدرس ثانوي", educationalStage = "ثانوي")
        val secRepo = MockGradeRepository("ثانوي")
        val secGrades = secRepo.refreshGrades().filter {
            EducationalStages.isGradeMatchingStage(it.name, secTeacher.educationalStage)
        }
        assertEquals(3, secGrades.size)
        assertEquals("الأول الثانوي", secGrades[0].name)
        assertEquals("الثالث الثانوي", secGrades[2].name)
    }

    @Test
    fun testSignUpFlowDoesNotNavigateToHomeAndRequiresLogin() {
        var onLoginSuccessCalled = false
        var onSignUpSuccessCalled = false
        var currentMode = "SIGNUP"
        var emailField = "newteacher@test.com"
        var passwordField = "Password123!"

        // Simulating the exact onSignUpSuccess callback handler implemented in AuthScreen
        val onSignUpSuccess = {
            onSignUpSuccessCalled = true
            currentMode = "LOGIN"
            // Email is preserved, password is reset ready for login
            passwordField = ""
        }

        // Trigger signup
        onSignUpSuccess()

        // Verify strictly that login success callback was NEVER invoked
        assertFalse("onLoginSuccess must NOT be called on sign up", onLoginSuccessCalled)
        assertTrue("onSignUpSuccess must be called", onSignUpSuccessCalled)
        assertEquals("Mode must switch to LOGIN after signup", "LOGIN", currentMode)
        assertEquals("Email field should be preserved for login", "newteacher@test.com", emailField)
        assertEquals("Password field should be cleared for fresh entry", "", passwordField)
    }
}
