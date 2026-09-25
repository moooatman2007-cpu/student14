package com.example.data.local.mapper

import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.Exam
import com.example.core.model.Grade
import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import com.example.core.model.LessonPayment
import com.example.core.model.Recitation
import com.example.core.model.Student
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.ExamEntity
import com.example.data.local.entity.GradeEntity
import com.example.data.local.entity.HomeworkEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.RecitationEntity
import com.example.data.local.entity.StudentEntity

// --- Grade Mapping ---
fun Grade.toEntity(): GradeEntity {
    return GradeEntity(
        gradeId = id,
        gradeName = name,
        displayOrder = displayOrder,
        studentCount = studentCount,
        teacherId = teacherId
    )
}

fun GradeEntity.toDomain(): Grade {
    return Grade(
        id = gradeId,
        name = gradeName,
        displayOrder = displayOrder,
        studentCount = studentCount,
        teacherId = teacherId
    )
}

// --- Student Mapping ---
fun Student.toEntity(): StudentEntity {
    return StudentEntity(
        studentId = studentId,
        studentCode = studentCode,
        fullName = fullName,
        gradeId = gradeId,
        parentPhone = parentPhone,
        hasWhatsApp = hasWhatsApp,
        alternativePhone = alternativePhone,
        teacherId = teacherId,
        deletedAt = deletedAt,
        createdAtRaw = createdAtRaw,
        updatedAtRaw = updatedAtRaw
    )
}

fun StudentEntity.toDomain(): Student {
    return Student(
        studentId = studentId,
        studentCode = studentCode,
        fullName = fullName,
        gradeId = gradeId,
        parentPhone = parentPhone,
        hasWhatsApp = hasWhatsApp,
        alternativePhone = alternativePhone,
        teacherId = teacherId,
        deletedAt = deletedAt,
        createdAtRaw = createdAtRaw,
        updatedAtRaw = updatedAtRaw
    )
}

// --- Attendance Mapping ---
fun Attendance.toEntity(): AttendanceEntity {
    return AttendanceEntity(
        attendanceId = attendanceId,
        studentId = studentId,
        teacherId = teacherId,
        date = date,
        status = status.name,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun AttendanceEntity.toDomain(): Attendance {
    return Attendance(
        attendanceId = attendanceId,
        studentId = studentId,
        teacherId = teacherId,
        date = date,
        status = AttendanceStatus.fromString(status),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// --- Recitation Mapping ---
fun Recitation.toEntity(): RecitationEntity {
    return RecitationEntity(
        recitationId = recitationId,
        studentId = studentId,
        teacherId = teacherId,
        date = date,
        title = title,
        content = content,
        score = score,
        maxScore = maxScore,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun RecitationEntity.toDomain(): Recitation {
    return Recitation(
        recitationId = recitationId,
        studentId = studentId,
        teacherId = teacherId,
        date = date,
        title = title,
        content = content,
        score = score,
        maxScore = maxScore,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// --- Exam Mapping ---
fun Exam.toEntity(): ExamEntity {
    return ExamEntity(
        examId = examId,
        studentId = studentId,
        teacherId = teacherId,
        date = date,
        examName = examName,
        subject = subject,
        score = score,
        maxScore = maxScore,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun ExamEntity.toDomain(): Exam {
    return Exam(
        examId = examId,
        studentId = studentId,
        teacherId = teacherId,
        date = date,
        examName = examName,
        subject = subject,
        score = score,
        maxScore = maxScore,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// --- Payment Mapping ---
fun LessonPayment.toEntity(): PaymentEntity {
    return PaymentEntity(
        paymentId = paymentId,
        studentId = studentId,
        teacherId = teacherId,
        year = year,
        month = month,
        amount = amount,
        isPaid = isPaid,
        paidAt = paidAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun PaymentEntity.toDomain(): LessonPayment {
    return LessonPayment(
        paymentId = paymentId,
        studentId = studentId,
        teacherId = teacherId,
        year = year,
        month = month,
        amount = amount,
        isPaid = isPaid,
        paidAt = paidAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// --- Homework Mapping ---
fun Homework.toEntity(): HomeworkEntity {
    return HomeworkEntity(
        homeworkId = homeworkId,
        studentId = studentId,
        teacherId = teacherId,
        date = date,
        title = title,
        status = status.name,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun HomeworkEntity.toDomain(): Homework {
    return Homework(
        homeworkId = homeworkId,
        studentId = studentId,
        teacherId = teacherId,
        date = date,
        title = title,
        status = HomeworkStatus.valueOf(status),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
