package com.ezygo.attendance

import com.ezygo.attendance.data.pdf.GradeCardReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader, against the text PDFBox actually extracts from a CUSAT grade card.
 *
 * Both fixtures are verbatim extractions of real cards, wrapped title and all. They
 * exist because the first version of this parser looked plausible and read nothing:
 * it assumed the credit came before the grade, when the card prints
 * `type · grade · grade point · credit · credit point`.
 */
class GradeCardReaderTest {

    private val reader = GradeCardReader()

    /** Semester IV, including the two-line title on 24-813-0402. */
    private val semesterFour = """
        COCHIN UNIVERSITY OF SCIENCE AND TECHNOLOGY
        KOCHI - 682 022, INDIA
        STATEMENT OF GRADES
        No. : 02034514 Date : 18-06-2026
        Section :
        Register No : 24100811
        Name AHMED SULTAN P
        Program Five Year Integrated M.Sc. in Computer Science (Artificial Intelligence and Data Science)
        Examination Semester IV , April 2026
        Department/School DEPARTMENT OF COMPUTER SCIENCE
        Faculty Faculty of Technology
        Course
        Code Course Title Type Grade Grade Point Credit Credit Point Month &Year
        24-751-VAC01 HEALTH AND FITNESS EDUCATION VAC A 9 3 27 Apr`26
        24-810-0405 STATISTICAL DATA ANALYSIS USING R – I SEC S 10 3 30 Apr`26
        24-813-0401 ADVANCED PROGRAMMING WITH JAVA MAJOR S 10 4 40 Apr`26
        24-813-0402 DIGITAL LOGIC AND COMPUTER
        ORGANIZATION MAJOR S 10 4 40 Apr`26
        24-813-0403 INTRODUCTION TO ARTIFICIAL INTELLIGENCE MAJOR B 8 4 32 Apr`26
        24-813-0404 DATABASE MANAGEMENT SYSTEMS MAJOR S 10 4 40 Apr`26
        Result : PASS Semester Credits : 22 Credits Earned: : 22 GPA : 9.50
        Range of Marks(%) Grade Grade Point
        0 - 49 F(FAIL) 0.0
        50-59 D(SATISFACTORY) 6.0
        60-69 C(GOOD) 7.0
        70-79 B(VERY GOOD) 8.0
        80-89 A(EXCELLENT) 9.0
        90-100 S(OUTSTANDING) 10.0
        GPA : (G1C1 + G2C2 + .....GnCn) / (C1+C2+ ....Cn) , G - Grade Point & C - Credit Value
        CONTROLLER OF EXAMINATIONS
    """.trimIndent()

    private val semesterOne = """
        COCHIN UNIVERSITY OF SCIENCE AND TECHNOLOGY
        STATEMENT OF GRADES
        Examination Semester I , December 2024
        Course
        Code Course Title Type Grade Grade Point Credit Credit Point Month &Year
        24-721-AEC03 MALAYALAM (LITERATURE AND SOCIETY) AEC B 8 3 24 Dec`24
        24-722-AEC01 ENGLISH FOR COMMUNICATION I AEC A 9 3 27 Dec`24
        24-809-0103 MATHEMATICAL METHODS I MDC D 6 3 18 Dec`24
        24-810-0101 INTRODUCTORY STATISTICS MINOR A 9 4 36 Dec`24
        24-810-0102 APPLIED STATISTICS MINOR B 8 4 32 Dec`24
        24-813-0101 COMPUTATIONAL THINKING WITH PYTHON MAJOR S 10 4 40 Dec`24
        Result : PASS Semester Credits : 21 Credits Earned: : 21 GPA : 8.43
        Range of Marks(%) Grade Grade Point
        0 - 49 F(FAIL) 0.0
        50-59 D(SATISFACTORY) 6.0
        90-100 S(OUTSTANDING) 10.0
        GPA : (G1C1 + G2C2 + .....GnCn) / (C1+C2+ ....Cn) , G - Grade Point & C - Credit Value
    """.trimIndent()

    @Test
    fun `reads the semester, the GPA and the credit total`() {
        assertEquals(4, reader.semesterOf(semesterFour))
        assertEquals(9.50, reader.sgpaOf(semesterFour)!!, 0.001)
        assertEquals(22, reader.creditsOf(semesterFour))

        assertEquals(1, reader.semesterOf(semesterOne))
        assertEquals(8.43, reader.sgpaOf(semesterOne)!!, 0.001)
        assertEquals(21, reader.creditsOf(semesterOne))
    }

    @Test
    fun `reads every course on the card`() {
        val courses = reader.coursesOf(semesterFour, semester = 4)

        assertEquals(6, courses.size)
        assertEquals(
            listOf(
                "24-751-VAC01",
                "24-810-0405",
                "24-813-0401",
                "24-813-0402",
                "24-813-0403",
                "24-813-0404",
            ),
            courses.map { it.code },
        )
        assertEquals(listOf("A", "S", "S", "S", "B", "S"), courses.map { it.grade })
        assertEquals(listOf(3.0, 3.0, 4.0, 4.0, 4.0, 4.0), courses.map { it.credits })
    }

    /** The credit column, not the grade point beside it or the credit point after it. */
    @Test
    fun `credits sum to the total the card prints`() {
        assertEquals(
            reader.creditsOf(semesterFour)!!.toDouble(),
            reader.coursesOf(semesterFour, 4).sumOf { it.credits ?: 0.0 },
            0.001,
        )
        assertEquals(
            reader.creditsOf(semesterOne)!!.toDouble(),
            reader.coursesOf(semesterOne, 1).sumOf { it.credits ?: 0.0 },
            0.001,
        )
    }

    /** A title broken across two lines is one course, not one course and one miss. */
    @Test
    fun `joins a title that wraps onto the next line`() {
        val wrapped = reader.coursesOf(semesterFour, 4).first { it.code == "24-813-0402" }
        assertEquals("DIGITAL LOGIC AND COMPUTER ORGANIZATION", wrapped.name)
        assertEquals(4.0, wrapped.credits!!, 0.001)
    }

    /** Titles keep their punctuation, their dashes and their trailing numerals. */
    @Test
    fun `keeps awkward titles intact`() {
        val four = reader.coursesOf(semesterFour, 4).associateBy { it.code }
        assertEquals("STATISTICAL DATA ANALYSIS USING R – I", four.getValue("24-810-0405").name)
        assertEquals("HEALTH AND FITNESS EDUCATION", four.getValue("24-751-VAC01").name)

        val one = reader.coursesOf(semesterOne, 1).associateBy { it.code }
        assertEquals("MALAYALAM (LITERATURE AND SOCIETY)", one.getValue("24-721-AEC03").name)
        assertEquals("ENGLISH FOR COMMUNICATION I", one.getValue("24-722-AEC01").name)
        assertEquals("MATHEMATICAL METHODS I", one.getValue("24-809-0103").name)
    }

    /**
     * The grade scale at the foot of every card reads a little like a course table.
     * It has no course code, so nothing there may reach the list.
     */
    @Test
    fun `ignores the grade scale and the headers`() {
        val codes = reader.coursesOf(semesterOne, 1).map { it.code }
        assertEquals(6, codes.size)
        assertTrue(codes.none { it.contains("49") || it.contains("100") })
    }

    @Test
    fun `reads nothing from a document with no course table`() {
        assertTrue(reader.coursesOf("Some other PDF entirely.\nNo grades here.", 1).isEmpty())
        assertNull(reader.sgpaOf("Some other PDF entirely."))
    }
}
