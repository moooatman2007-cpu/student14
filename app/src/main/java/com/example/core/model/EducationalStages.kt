package com.example.core.model

object EducationalStages {
    const val PRIMARY = "ابتدائي"
    const val PREPARATORY = "إعدادي"
    const val SECONDARY = "ثانوي"

    val ALL_STAGES = listOf(PRIMARY, PREPARATORY, SECONDARY)

    val PRIMARY_GRADES = listOf(
        "الأول الابتدائي",
        "الثاني الابتدائي",
        "الثالث الابتدائي",
        "الرابع الابتدائي",
        "الخامس الابتدائي",
        "السادس الابتدائي"
    )

    val PREPARATORY_GRADES = listOf(
        "الأول الإعدادي",
        "الثاني الإعدادي",
        "الثالث الإعدادي"
    )

    val SECONDARY_GRADES = listOf(
        "الأول الثانوي",
        "الثاني الثانوي",
        "الثالث الثانوي"
    )

    fun normalizeArabic(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.trim()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("\\s+"), " ")
    }

    fun getGradeNamesForStage(stage: String?): List<String> {
        val normStage = normalizeArabic(stage)
        return when {
            normStage.contains("ابتدائي") -> PRIMARY_GRADES
            normStage.contains("ثانوي") -> SECONDARY_GRADES
            else -> PREPARATORY_GRADES
        }
    }

    fun normalizeGradeName(name: String): String {
        return name.trim().removePrefix("الصف ").trim()
    }

    fun isGradeMatchingStage(gradeName: String, stage: String?): Boolean {
        if (stage.isNullOrBlank()) return true
        val normStage = normalizeArabic(stage)
        val normGrade = normalizeArabic(gradeName)

        // Direct stage substring match
        if (normStage.contains("ابتدائي") && normGrade.contains("ابتدائي")) return true
        if (normStage.contains("اعدادي") && normGrade.contains("اعدادي")) return true
        if (normStage.contains("ثانوي") && normGrade.contains("ثانوي")) return true

        val expected = getGradeNamesForStage(stage)
        val normalized = normalizeGradeName(gradeName)
        if (expected.contains(normalized) || expected.contains(gradeName.trim())) return true

        val normalizedExpected = expected.map { normalizeArabic(normalizeGradeName(it)) }
        return normalizedExpected.contains(normalizeArabic(normalized))
    }
}

