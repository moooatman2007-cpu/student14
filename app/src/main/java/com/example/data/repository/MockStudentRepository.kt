package com.example.data.repository

import com.example.core.model.Student
import com.example.core.model.TeacherStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class MockStudentRepository(
    private val gradeRepository: MockGradeRepository? = null
) : StudentRepository {

    private val lastCodeIndex = AtomicInteger(86)

    private val initialStudents: List<Student> = listOf(
        // الصف الأول الإعدادي (30 طالب) - grade_1
        Student("s_01", "ST-00001", "أحمد محمد علي السيد", "grade_1", "01012345678", true, "01198765432"),
        Student("s_02", "ST-00002", "محمود إبراهيم خليل حسن", "grade_1", "01123456789", true, null),
        Student("s_03", "ST-00003", "يوسف عمر عبد الرحمن", "grade_1", "01234567890", false, "01511223344"),
        Student("s_04", "ST-00004", "عمر خالد مصطفى كمال", "grade_1", "01545678901", true, null),
        Student("s_05", "ST-00005", "كريم عادل طارق الشناوي", "grade_1", "01098765432", true, "01011223344"),
        Student("s_06", "ST-00006", "زياد هاني سامي غانم", "grade_1", "01187654321", false, null),
        Student("s_07", "ST-00007", "حمزة سامي نبيل عثمان", "grade_1", "01276543210", true, "01299887766"),
        Student("s_08", "ST-00008", "آدم حسام فتحي البدري", "grade_1", "01565432109", true, null),
        Student("s_09", "ST-00009", "سيف الدين وائل ماهر النجار", "grade_1", "01033445566", true, "01144556677"),
        Student("s_10", "ST-00010", "مروان تامر أحمد العوضي", "grade_1", "01155667788", false, null),
        Student("s_11", "ST-00011", "بلال شريف فاروق الجيار", "grade_1", "01222334455", true, null),
        Student("s_12", "ST-00012", "ياسين علاء الدين رجب", "grade_1", "01577889900", true, "01099881122"),
        Student("s_13", "ST-00013", "عبد الله مدحت سعيد القاضي", "grade_1", "01066778899", false, null),
        Student("s_14", "ST-00014", "أنس حازم صبحي الهواري", "grade_1", "01177889911", true, "01233445566"),
        Student("s_15", "ST-00015", "علي وليد عيسى مطاوع", "grade_1", "01288990022", true, null),
        Student("s_16", "ST-00016", "سارة أحمد محمود البنا", "grade_1", "01023456789", true, "01533221100"),
        Student("s_17", "ST-00017", "مريم خالد إبراهيم الشريف", "grade_1", "01134567890", true, null),
        Student("s_18", "ST-00018", "نور حسام الدين زهران", "grade_1", "01245678901", false, "01177665544"),
        Student("s_19", "ST-00019", "جنى ياسر عبد الله الصياد", "grade_1", "01556789012", true, null),
        Student("s_20", "ST-00020", "ملك تامر صبري عفيفي", "grade_1", "01087654321", true, "01055443322"),
        Student("s_21", "ST-00021", "فاطمة محمد رفعت العريان", "grade_1", "01198765430", false, null),
        Student("s_22", "ST-00022", "خديجة أيمن عبد الوهاب", "grade_1", "01209876543", true, null),
        Student("s_23", "ST-00023", "حبيبة ماجد عبد العظيم", "grade_1", "01510987654", true, "01088776655"),
        Student("s_24", "ST-00024", "شهد عمرو فؤاد درويش", "grade_1", "01044556677", true, null),
        Student("s_25", "ST-00025", "يارا شادي وجيه البابلي", "grade_1", "01166778800", false, "01244332211"),
        Student("s_26", "ST-00026", "سلمى إيهاب كمال البحيري", "grade_1", "01288776655", true, null),
        Student("s_27", "ST-00027", "ندى طارق توفيق رسلان", "grade_1", "01599887766", true, "01122334455"),
        Student("s_28", "ST-00028", "فريدة مصطفى نبيل بركات", "grade_1", "01011335577", true, null),
        Student("s_29", "ST-00029", "ريناد عصام كامل الألفي", "grade_1", "01122446688", false, null),
        Student("s_30", "ST-00030", "حلا أشرف عبد المنعم", "grade_1", "01233557799", true, "01066554433"),

        // الصف الثاني الإعدادي (28 طالب) - grade_2
        Student("s_31", "ST-00031", "إبراهيم ماجد عبد الرحمن", "grade_2", "01055667788", true, "01111223344"),
        Student("s_32", "ST-00032", "حسن وائل السيد الباز", "grade_2", "01144556677", true, null),
        Student("s_33", "ST-00033", "خالد يحيى زكريا المهدي", "grade_2", "01233445566", false, null),
        Student("s_34", "ST-00034", "طارق شريف جلال عسكر", "grade_2", "01522334455", true, "01044332211"),
        Student("s_35", "ST-00035", "مصطفى سامر فكري الديب", "grade_2", "01011223344", true, null),
        Student("s_36", "ST-00036", "صالح عماد الدين منصور", "grade_2", "01199887766", false, "01255667788"),
        Student("s_37", "ST-00037", "عبد الرحمن أشرف متولي", "grade_2", "01288776655", true, null),
        Student("s_38", "ST-00038", "باسم نادر شكري الجوهري", "grade_2", "01577665544", true, "01188990011"),
        Student("s_39", "ST-00039", "مازن عصام عاطف زايد", "grade_2", "01066554433", true, null),
        Student("s_40", "ST-00040", "حميد مجدي لطفي رضوان", "grade_2", "01155443322", false, null),
        Student("s_41", "ST-00041", "رامي هاني رمزي الفقي", "grade_2", "01244332211", true, "01022334455"),
        Student("s_42", "ST-00042", "سامح أكرم توفيق خضر", "grade_2", "01533221100", true, null),
        Student("s_43", "ST-00043", "أيمن صبحي غريب النحاس", "grade_2", "01022110099", false, "01133445566"),
        Student("s_44", "ST-00044", "هيثم فادي سمير قطب", "grade_2", "01111009988", true, null),
        Student("s_45", "ST-00045", "يحيى صفوت رشاد والي", "grade_2", "01200998877", true, "01211223344"),
        Student("s_46", "ST-00046", "آية حسام الدين مهران", "grade_2", "01599887700", true, null),
        Student("s_47", "ST-00047", "بسملة إسلام عادل غريب", "grade_2", "01088776611", false, "01099887766"),
        Student("s_48", "ST-00048", "تقى أسامة نصر الدين", "grade_2", "01177665522", true, null),
        Student("s_49", "ST-00049", "جومانة كريم عبد الشافي", "grade_2", "01266554433", true, "01555443322"),
        Student("s_50", "ST-00050", "دارين شريف ماهر الشامي", "grade_2", "01555443344", true, null),
        Student("s_51", "ST-00051", "رنا معتز صفوان الباز", "grade_2", "01044332255", false, null),
        Student("s_52", "ST-00052", "زينة حاتم يوسف حنفي", "grade_2", "01133221166", true, "01177889900"),
        Student("s_53", "ST-00053", "سندس باسل ضياء الأزهري", "grade_2", "01222110077", true, null),
        Student("s_54", "ST-00054", "ضحى مدحت كامل الشرقاوي", "grade_2", "01511009988", false, "01011998877"),
        Student("s_55", "ST-00055", "غادة ممدوح سمير عنان", "grade_2", "01000998899", true, null),
        Student("s_56", "ST-00056", "لجين وائل زكي السعدني", "grade_2", "01199880011", true, "01266778899"),
        Student("s_57", "ST-00057", "ميار هشام غانم صقر", "grade_2", "01288771122", true, null),
        Student("s_58", "ST-00058", "هاجر طلال مسعود عمار", "grade_2", "01577662233", false, null),

        // الصف الثالث الإعدادي (28 طالب) - grade_3
        Student("s_59", "ST-00059", "أحمد علاء عبد الباسط", "grade_3", "01033221144", true, "01055667799"),
        Student("s_60", "ST-00060", "إسلام رفعت صبحي جاد", "grade_3", "01122110055", true, null),
        Student("s_61", "ST-00061", "بدر مصطفى أمين الشهاوي", "grade_3", "01211009966", false, "01144332211"),
        Student("s_62", "ST-00062", "تامر سعيد حامد الغندور", "grade_3", "01500998877", true, null),
        Student("s_63", "ST-00063", "جلال وفيق مرسي النمر", "grade_3", "01099887788", true, "01233221100"),
        Student("s_64", "ST-00064", "حاتم نشأت شاكر المنسي", "grade_3", "01188776699", false, null),
        Student("s_65", "ST-00065", "داود مروان عبد الحي", "grade_3", "01277665500", true, null),
        Student("s_66", "ST-00066", "راشد نزار فؤاد الباشا", "grade_3", "01566554411", true, "01511224466"),
        Student("s_67", "ST-00067", "سليمان يحيى شلبي قنديل", "grade_3", "01055443322", true, null),
        Student("s_68", "ST-00068", "صهيب أنور كامل المراكبي", "grade_3", "01144332233", false, "01077889900"),
        Student("s_69", "ST-00069", "عاصم بهاء الدين المليجي", "grade_3", "01233221144", true, null),
        Student("s_70", "ST-00070", "فارس جابر خيري البياع", "grade_3", "01522110055", true, "01166554433"),
        Student("s_71", "ST-00071", "قاسم خالد حمدي الألفي", "grade_3", "01011009966", false, null),
        Student("s_72", "ST-00072", "لؤي مأمون رشدي الكردي", "grade_3", "01100998877", true, null),
        Student("s_73", "ST-00073", "مؤمن نظمي عبد الحميد", "grade_3", "01299887788", true, "01288991100"),
        Student("s_74", "ST-00074", "أروى سامي رضوان الدمرداش", "grade_3", "01588776699", true, null),
        Student("s_75", "ST-00075", "تسنيم عماد لطفي عبد ربه", "grade_3", "01077665500", false, "01033224411"),
        Student("s_76", "ST-00076", "جميلة وليد حيدر البشري", "grade_3", "01166554411", true, null),
        Student("s_77", "ST-00077", "حنين شريف نبيل البكري", "grade_3", "01255443322", true, "01544332211"),
        Student("s_78", "ST-00078", "دعاء صلاح الدين الهادي", "grade_3", "01544332233", false, null),
        Student("s_79", "ST-00079", "رغد طارق غازي الصيرفي", "grade_3", "01033221155", true, null),
        Student("s_80", "ST-00080", "سما هيثم فتح الله عيسى", "grade_3", "01122110066", true, "01199001122"),
        Student("s_81", "ST-00081", "شروق وفيق عبد المجيد", "grade_3", "01211009977", false, null),
        Student("s_82", "ST-00082", "عائشة ياسر رفيق الحناوي", "grade_3", "01500998888", true, "01011332244"),
        Student("s_83", "ST-00083", "كنزي ناصر عبد الجليل", "grade_3", "01099887799", true, null),
        Student("s_84", "ST-00084", "مرام يوسف طلعت عبد العال", "grade_3", "01188776600", false, null),
        Student("s_85", "ST-00085", "نور الهدى عادل الفيشاوي", "grade_3", "01277665511", true, "01255446677"),
        Student("s_86", "ST-00086", "يسرا إيهاب مختار علام", "grade_3", "01566554422", true, null)
    )

    private val _students = MutableStateFlow(initialStudents)

    init {
        syncGradeCounts()
    }

    private fun syncGradeCounts() {
        val counts = _students.value.groupBy { it.gradeId }.mapValues { it.value.size }
        gradeRepository?.updateStudentCounts(counts)
    }

    override fun getStudents(): Flow<List<Student>> = _students.asStateFlow()

    override fun getStudentsByGrade(gradeId: String): Flow<List<Student>> {
        return _students.map { list ->
            list.filter { it.gradeId == gradeId }
        }
    }

    override suspend fun getStudentById(studentId: String): Student? {
        return _students.value.find { it.studentId == studentId }
    }

    override suspend fun getStudentByCode(studentCode: String): Student? {
        val normalizedCode = studentCode.trim().uppercase()
        return _students.value.find { it.studentCode.uppercase() == normalizedCode }
    }

    override suspend fun addStudent(
        fullName: String,
        gradeId: String,
        parentPhone: String,
        hasWhatsApp: Boolean,
        alternativePhone: String?
    ): Result<Student> {
        val nextIdx = lastCodeIndex.incrementAndGet()
        val generatedCode = "ST-" + String.format("%05d", nextIdx)
        val newStudent = Student(
            studentId = "s_" + UUID.randomUUID().toString().take(8),
            studentCode = generatedCode,
            fullName = fullName.trim(),
            gradeId = gradeId,
            parentPhone = parentPhone.trim(),
            hasWhatsApp = hasWhatsApp,
            alternativePhone = alternativePhone?.trim()?.ifBlank { null },
            createdAtRaw = java.time.OffsetDateTime.now().toString(),
            updatedAtRaw = java.time.OffsetDateTime.now().toString()
        )
        _students.update { current ->
            listOf(newStudent) + current
        }
        syncGradeCounts()
        return Result.success(newStudent)
    }

    override suspend fun updateStudent(student: Student): Result<Student> {
        var found = false
        _students.update { current ->
            current.map {
                if (it.studentId == student.studentId) {
                    found = true
                    // Student code is strictly preserved and cannot be overwritten
                    student.copy(
                        studentCode = it.studentCode,
                        updatedAtRaw = java.time.OffsetDateTime.now().toString()
                    )
                } else {
                    it
                }
            }
        }
        syncGradeCounts()
        return if (found) Result.success(student) else Result.failure(NoSuchElementException("Student not found"))
    }

    override suspend fun deleteStudent(studentId: String): Result<Unit> {
        _students.update { current ->
            current.filterNot { it.studentId == studentId }
        }
        syncGradeCounts()
        return Result.success(Unit)
    }

    override fun searchStudents(query: String, gradeId: String?): Flow<List<Student>> {
        return _students.map { list ->
            val filteredByGrade = if (gradeId.isNullOrBlank() || gradeId == "all") {
                list
            } else {
                list.filter { it.gradeId == gradeId }
            }
            if (query.isBlank()) {
                filteredByGrade
            } else {
                val cleanedQuery = normalizeArabic(query.trim())
                val queryDigits = query.filter { it.isDigit() }

                filteredByGrade.filter { student ->
                    val normalizedName = normalizeArabic(student.fullName)
                    val codeMatch = student.studentCode.contains(query.trim(), ignoreCase = true) ||
                            (queryDigits.isNotEmpty() && student.studentCode.contains(queryDigits))
                    val nameMatch = normalizedName.contains(cleanedQuery, ignoreCase = true)
                    val phoneMatch = student.parentPhone.contains(query.trim()) ||
                            (student.alternativePhone?.contains(query.trim()) == true)

                    codeMatch || nameMatch || phoneMatch
                }
            }
        }
    }

    override fun getStats(): Flow<TeacherStats> {
        return _students.map { list ->
            val gradeCounts = list.groupBy { it.gradeId }.mapValues { it.value.size }
            val whatsappCount = list.count { it.hasWhatsApp }
            val altPhoneCount = list.count { !it.alternativePhone.isNullOrBlank() }
            TeacherStats(
                totalStudents = list.size,
                gradeCounts = gradeCounts,
                whatsappEnabledCount = whatsappCount,
                hasAlternativePhoneCount = altPhoneCount
            )
        }
    }

    private fun normalizeArabic(text: String): String {
        return text
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace('ئ', 'ي')
            .replace('ؤ', 'و')
    }
}
