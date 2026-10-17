package com.ezygo.attendance

import com.ezygo.attendance.data.remote.dto.DetailedReportDto
import com.ezygo.attendance.data.remote.dto.ReportParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shape tolerance of the report decoder: keyed objects, empty arrays
 * standing in for empty objects, stringly-typed numbers and unknown fields.
 */
class ReportParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(raw: String) = ReportParser.parse(json.decodeFromString<DetailedReportDto>(raw))

    @Test
    fun `parses the documented report shape`() {
        val parsed = parse(
            """
            {
              "courses": {
                "96189": {
                  "id": 96189, "si_no": 1, "name": "Fundamentals of Data Science",
                  "code": "24-813-0502", "institution_id": 128,
                  "academic_year": "2026-27", "academic_semester": "odd",
                  "some_field_we_do_not_model": 7
                }
              },
              "sessions": [
                {"id": 3, "name": "III", "time_from": "11:00", "time_to": "11:55", "view_order": 3, "type": "forenoon"},
                {"id": 1, "name": "I", "time_from": "09:00", "time_to": "09:55", "view_order": 1, "type": "forenoon"}
              ],
              "attendanceTypes": {
                "1": {"id": 1, "name": "Present", "code": "P", "positive_report_value": 1},
                "2": {"id": 2, "name": "Absent", "code": "AB", "positive_report_value": 0}
              },
              "studentAttendanceData": {
                "20260819": {
                  "1": {"course": null, "attendance": null},
                  "3": {"course": 96189, "attendance": 1, "delivery_methods": "Lecture"}
                }
              },
              "attendanceDatesArray": {
                "20260819": {"key": "20260819", "date": "19.8.26", "day": "Wednesday"}
              }
            }
            """.trimIndent(),
        )

        assertEquals("Fundamentals of Data Science", parsed.courses[96189]?.name)
        assertEquals(listOf("I", "III"), parsed.sessions.map { it.name }) // sorted by view_order
        assertEquals(1, parsed.attendanceTypes[1]?.positiveReportValue)
        assertNull(parsed.attendanceData["20260819"]?.get(1)?.courseId)
        assertEquals(96189, parsed.attendanceData["20260819"]?.get(3)?.courseId)
        assertEquals("Wednesday", parsed.dates["20260819"]?.day)
    }

    @Test
    fun `empty arrays standing in for empty objects do not crash`() {
        val parsed = parse(
            """
            {"courses": [], "sessions": [], "attendanceTypes": [],
             "studentAttendanceData": [], "attendanceDatesArray": []}
            """.trimIndent(),
        )
        assertTrue(parsed.courses.isEmpty())
        assertTrue(parsed.attendanceData.isEmpty())
        assertTrue(parsed.dates.isEmpty())
    }

    @Test
    fun `missing sections decode to empty rather than failing`() {
        val parsed = parse("{}")
        assertTrue(parsed.courses.isEmpty())
        assertTrue(parsed.sessions.isEmpty())
        assertTrue(parsed.attendanceTypes.isEmpty())
    }

    @Test
    fun `numbers arriving as strings are still read`() {
        val parsed = parse(
            """
            {
              "attendanceTypes": {"1": {"id": "1", "name": "Present", "code": "P", "positive_report_value": "1"}},
              "studentAttendanceData": {"20260819": {"3": {"course": "96189", "attendance": "1"}}}
            }
            """.trimIndent(),
        )
        assertEquals(1, parsed.attendanceTypes[1]?.positiveReportValue)
        assertEquals(96189, parsed.attendanceData["20260819"]?.get(3)?.courseId)
    }

    @Test
    fun `courses keyed as an indexed array are still indexed by id`() {
        val parsed = parse(
            """
            {"courses": [{"id": 96190, "name": "Operating System", "code": "24-813-0503"}]}
            """.trimIndent(),
        )
        assertEquals("Operating System", parsed.courses[96190]?.name)
    }
}
