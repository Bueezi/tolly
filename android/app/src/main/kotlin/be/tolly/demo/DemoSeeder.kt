package be.tolly.demo

import be.tolly.core.data.download.DownloadFiles
import be.tolly.core.database.AnnouncementDao
import be.tolly.core.database.AnnouncementEntity
import be.tolly.core.database.CalendarDao
import be.tolly.core.database.CalendarItemEntity
import be.tolly.core.database.ContentDao
import be.tolly.core.database.ContentEntity
import be.tolly.core.database.CourseDao
import be.tolly.core.database.CourseEntity
import be.tolly.core.database.DownloadDao
import be.tolly.core.database.DownloadEntity
import be.tolly.core.database.GradeDao
import be.tolly.core.database.GradeEntity
import be.tolly.core.database.SyncStateDao
import be.tolly.core.database.SyncStateEntity
import be.tolly.core.model.ContentType
import be.tolly.core.model.CourseKind
import be.tolly.core.model.DownloadRequest
import be.tolly.core.model.DownloadStatus
import javax.inject.Inject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The made-up account behind the welcome screen's Demo button (and the debug `DemoActivity`):
 * courses, content, announcements, agenda and grades in the cache, for reviewers and anyone
 * trying the app without a KU Leuven login. The data is Dutch, like the real courses it imitates.
 */
class DemoSeeder @Inject constructor(
    private val courseDao: CourseDao,
    private val contentDao: ContentDao,
    private val announcementDao: AnnouncementDao,
    private val syncStateDao: SyncStateDao,
    private val calendarDao: CalendarDao,
    private val gradeDao: GradeDao,
    private val downloadDao: DownloadDao,
    private val downloadFiles: DownloadFiles,
) {
    /** Replaces the cache with the made-up account: courses, content, announcements, agenda, grades, a download. */
    suspend fun seed() {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val courses = listOf(
            course("_1_1", "ULTRA-B-KUL-G0X10a-2627", "Logica voor informatici", "G0X10a", 1),
            course("_2_1", "ULTRA-B-KUL-G0X20a-2627", "Beginselen van programmeren", "G0X20a", 0),
            course("_3_1", "ULTRA-B-KUL-H0X30a-2627", "Gegevensbanken", "H0X30a", 2),
            course("_4_1", "ULTRA-B-KUL-G0X40a-2627", "Wiskunde I", "G0X40a", 0),
            course("_5_1", "ULTRA-C-1000001-K", "Bibliotheek Wetenschap & Technologie", null, 0, community = true),
            course("_6_1", "ULTRA-B-KUL-G0X50a-2526", "Informatiewerktuigen", "G0X50a", 0, year = "2526"),
        )
        courseDao.replaceAll(courses)
        seedPlanning()
        seedDownload()
        syncStateDao.upsert(SyncStateEntity("courses", now))
        syncStateDao.upsert(SyncStateEntity("announcements", now))

        val c = "_1_1"
        contentDao.replaceChildren(
            c, null,
            listOf(
                content("_10_1", c, null, "Cursusmateriaal", "FOLDER", 0, isPage = true, hasChildren = true),
                content("_11_1", c, null, "Hoorcolleges", "LESSON", 1, hasChildren = true),
                content("_12_1", c, null, "Oefenzittingen", "FOLDER", 2, hasChildren = true),
                content("_13_1", c, null, "Syllabus 2026-2027.pdf", "FILE", 3, fileName = "syllabus.pdf", mime = "application/pdf", modified = now - 50 * hour),
                content("_14_1", c, null, "Lesopnames", "LTI", 4),
                content("_15_1", c, null, "ECTS-fiche", "LINK", 5, url = "https://onderwijsaanbod.kuleuven.be/"),
            ),
        )
        contentDao.replaceChildren(
            c, "_10_1",
            listOf(content("_16_1", c, "_10_1", "ultraDocumentBody", "DOCUMENT", 0, body = DEMO_PAGE)),
        )
        contentDao.replaceChildren(
            c, "_11_1",
            listOf(
                content("_20_1", c, "_11_1", "Les 1 – Propositielogica.pdf", "FILE", 0, fileName = "les1.pdf", mime = "application/pdf", modified = now - 400 * hour),
                content("_21_1", c, "_11_1", "Les 2 – Natuurlijke deductie.pptx", "FILE", 1, fileName = "les2.pptx", mime = "application/vnd.openxmlformats-officedocument.presentationml.presentation", modified = now - 200 * hour),
                content("_22_1", c, "_11_1", "Les 3 – Predikaatlogica", "DOCUMENT", 2, body = "<p>Slides volgen na de les.</p>"),
                content("_23_1", c, "_11_1", "pc-sessie1.lean", "FILE", 3, fileName = "pc-sessie1.lean", mime = "application/octet-stream", modified = now - 30 * hour),
            ),
        )
        // A page of only files, and one with a single HTML file (shown in place).
        contentDao.replaceChildren(
            c, "_12_1",
            listOf(
                content("_24_1", c, "_12_1", "Practicum 1 - Opgave", "DOCUMENT", 0, body = DEMO_FILES_ONLY, modified = now - 20 * hour),
                content("_25_1", c, "_12_1", "Overzicht lessen en examens '26-'27", "DOCUMENT", 1, body = DEMO_HTML_FILE, modified = now - 5 * hour),
            ),
        )
        announcementDao.replaceForCourse(
            c,
            listOf(
                announcement("_30_1", c, "Studiebegeleidingssessie dinsdag om 9u", DEMO_ANNOUNCEMENT, now - 26 * hour, read = false),
                announcement("_31_1", c, "Lokaalwijziging oefenzitting", "<p>De oefenzitting van donderdag gaat door in <b>200A 00.144</b>.</p>", now - 120 * hour, read = true),
            ),
        )
        announcementDao.replaceForCourse(
            "_3_1",
            listOf(
                announcement("_32_1", "_3_1", "Tussentijdse toets: praktische info", "<p>De toets duurt 90 minuten. Breng je studentenkaart mee.</p><ul><li>Geen rekenmachine</li><li>Gesloten boek</li></ul>", now - 3 * hour, read = false),
                announcement("_33_1", "_3_1", "Nieuwe oefeningen online", "<p>Reeks 4 staat online onder <i>Oefenzittingen</i>.</p>", now - 70 * hour, read = false),
            ),
        )
        announcementDao.replaceForCourse(
            "_2_1",
            listOf(announcement("_34_1", "_2_1", "Welkom!", "<p>Welkom bij Beginselen van programmeren.</p>", now - 900 * hour, read = true)),
        )
    }

    /** Finished downloads (a small text file, an HTML page), to try opening, sharing and saving copies. */
    private suspend fun seedDownload() {
        seedFile(
            DownloadRequest(
                courseId = "_1_1",
                courseName = "Logica voor informatici",
                contentId = "_25_1",
                title = DEMO_HTML_NAME,
                fileName = DEMO_HTML_NAME,
                mimeType = "text/html",
                sourceUrl = "https://ultra.edu.kuleuven.cloud/demo",
                folderPath = "Oefenzittingen",
            ),
            DEMO_HTML_CONTENT,
        )
        val request = DownloadRequest(
            courseId = "_1_1",
            courseName = "Logica voor informatici",
            contentId = "_demo_file_1",
            title = "Oefeningen week 1",
            fileName = "Oefeningen week 1.txt",
            mimeType = "text/plain",
            sourceUrl = "https://ultra.edu.kuleuven.cloud/demo",
            folderPath = "Oefenzittingen",
        )
        seedFile(request, "Oefening 1: bewijs dat p ∨ ¬p een tautologie is.\n")
    }

    private suspend fun seedFile(request: DownloadRequest, text: String) {
        val file = downloadFiles.fileFor(request.courseId, request.key, request.fileName)
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
        downloadDao.upsert(
            DownloadEntity(
                key = request.key,
                courseId = request.courseId,
                courseName = request.courseName,
                contentId = request.contentId,
                title = request.title,
                fileName = request.fileName,
                mimeType = request.mimeType,
                sourceUrl = request.sourceUrl,
                totalBytes = file.length(),
                downloadedBytes = file.length(),
                status = DownloadStatus.DONE,
                localPath = file.path,
                error = null,
                updatedAt = System.currentTimeMillis(),
                folderPath = request.folderPath,
            ),
        )
    }

    private suspend fun seedPlanning() {
        val zone = ZoneId.systemDefault()
        // Days count weekdays only, as classes and deadlines do: "in 1 day" on a Friday is Monday.
        fun schoolDay(weekdaysFromToday: Long): LocalDate {
            var date = LocalDate.now(zone)
            repeat(weekdaysFromToday.toInt()) {
                do date = date.plusDays(1) while (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY)
            }
            return date
        }
        fun at(daysFromToday: Long, hour: Int, minute: Int = 0) =
            schoolDay(daysFromToday).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        fun event(id: String, course: String, name: String, title: String, start: Long, end: Long?, location: String? = null, gradable: Boolean = false) =
            CalendarItemEntity(id, if (gradable) "GradebookColumn" else "Course", course, name, title, null, location, start, end, gradable)
        val today = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        calendarDao.replaceBetween(
            today, today + 60L * 86_400_000L,
            listOf(
                event("_c1", "_1_1", "Logica voor informatici", "Hoorcollege 5: Predikaatlogica", at(0, 10, 30), at(0, 12, 30), "200C 00.03"),
                event("_c2", "_4_1", "Wiskunde I", "Oefenzitting reeks 4", at(0, 14), at(0, 16), "200A 00.144"),
                event("_c3", "_2_1", "Beginselen van programmeren", "Project deel 1", at(1, 23, 59), null, gradable = true),
                event("_c4", "_3_1", "Gegevensbanken", "Tussentijdse toets", at(2, 9), at(2, 10, 30), "Aula Pieter De Somer"),
                event("_c5", "_3_1", "Gegevensbanken", "Opdracht SQL", at(4, 18), null, gradable = true),
                event("_c6", "_1_1", "Logica voor informatici", "Hoorcollege 6: Natuurlijke deductie", at(5, 10, 30), at(5, 12, 30), "200C 00.03"),
                CalendarItemEntity("lesson:1", "Lesson", "_3_1", null, "Gegevensbanken", null, "200M 00.07", at(0, 8, 30), at(0, 10, 30), false),
                CalendarItemEntity("lesson:2", "Lesson", "_2_1", null, "Beginselen van programmeren", null, "200S 00.225", at(1, 13), at(1, 15), false),
                // Going on right now, so the course list highlights Wiskunde I.
                CalendarItemEntity(
                    "lesson:now", "Lesson", "_4_1", null, "Wiskunde I", null, "200C 01.02",
                    System.currentTimeMillis() - 30 * 60_000L, System.currentTimeMillis() + 90 * 60_000L, false,
                ),
                // And the next class today, so Gegevensbanken says when it starts.
                CalendarItemEntity(
                    "lesson:next", "Lesson", "_3_1", null, "Gegevensbanken: oefeningen", null, "200M 00.07",
                    System.currentTimeMillis() + 40 * 60_000L, System.currentTimeMillis() + 160 * 60_000L, false,
                ),
            ),
        )
        syncStateDao.upsert(SyncStateEntity("calendar", System.currentTimeMillis()))
        gradeDao.replaceForCourse(
            "_3_1",
            listOf(
                GradeEntity("_3_1", "_g1", "Opdracht 1: ER-diagram", "17/20", 17.0, 20.0, "Graded", "Goed uitgewerkt!", null, 0),
                GradeEntity("_3_1", "_g2", "Opdracht 2: Normalisatie", null, null, 20.0, "NeedsGrading", null, null, 1),
                GradeEntity("_3_1", "_g3", "Opdracht SQL", null, null, 20.0, null, null, at(4, 18), 2),
            ),
        )
    }

    private fun course(
        id: String,
        externalId: String,
        name: String,
        code: String?,
        unread: Int,
        community: Boolean = false,
        year: String? = if (community) null else "2627",
    ) = CourseEntity(
        id = id, externalId = externalId, name = name, code = code,
        kind = if (community) CourseKind.COMMUNITY else CourseKind.COURSE, available = true, academicYear = year,
        webUrl = "https://ultra.edu.kuleuven.cloud/ultra/courses/$id/outline", lastAccessed = null,
        unreadAnnouncements = unread, sortIndex = id.substring(1, 2).toInt(),
    )

    private fun content(
        id: String, courseId: String, parentId: String?, title: String, type: String, position: Int,
        isPage: Boolean = false, hasChildren: Boolean = false, body: String? = null, url: String? = null,
        fileName: String? = null, mime: String? = null, modified: Long? = null,
    ) = ContentEntity(
        id = id, courseId = courseId, parentId = parentId, title = title, description = null, type = ContentType.valueOf(type),
        isPage = isPage, position = position, hasChildren = hasChildren, body = body, url = url, fileName = fileName,
        mimeType = mime, webUrl = "https://ultra.edu.kuleuven.cloud/ultra/courses/$courseId/outline", modified = modified,
        available = true,
    )

    private fun announcement(id: String, courseId: String, title: String, body: String, created: Long, read: Boolean) =
        AnnouncementEntity(id, courseId, title, body, created, null, readRemote = read, readLocal = false)

    private companion object {
        const val DEMO_HTML_NAME = "UNSAFE_HTML_20261016170512.html"
        const val DEMO_FILES_ONLY = """
            <p><a href="https://ultra.edu.kuleuven.cloud/bbcswebdav/xid-5_1?demo=1" data-bbfile="{&quot;displayName&quot;:&quot;opgave.pdf&quot;,&quot;mimeType&quot;:&quot;application/pdf&quot;}">opgave.pdf</a></p>
            <p><a href="https://ultra.edu.kuleuven.cloud/bbcswebdav/xid-6_1?demo=1" data-bbfile="{&quot;displayName&quot;:&quot;password_checker.py&quot;,&quot;mimeType&quot;:&quot;text/x-python&quot;}">password_checker.py</a></p>
            <p><a href="https://ultra.edu.kuleuven.cloud/bbcswebdav/xid-7_1?demo=1" data-bbfile="{&quot;displayName&quot;:&quot;test_password_checker.py&quot;,&quot;mimeType&quot;:&quot;text/x-python&quot;}">test_password_checker.py</a></p>
            <p>&nbsp;</p>
        """
        const val DEMO_HTML_FILE = """
            <p><a href="https://ultra.edu.kuleuven.cloud/bbcswebdav/xid-8_1?demo=1" data-bbfile="{&quot;displayName&quot;:&quot;UNSAFE_HTML_20261016170512.html&quot;,&quot;mimeType&quot;:&quot;text/html&quot;}">UNSAFE_HTML_20261016170512.html</a></p>
        """
        const val DEMO_HTML_CONTENT = """<!DOCTYPE html><html><head><meta charset="utf-8"><title>Overzicht</title>
            <style>table { border-collapse: collapse; } th { text-align: left; }</style><script>document.write('no')</script></head>
            <body style="background: white"><h2>Overzicht lessen en examens</h2>
            <p>Alle hoorcolleges gaan door in <b>200C 00.03</b>, de oefenzittingen in de pc-klassen.</p>
            <table><tr><th>Week</th><th>Hoorcollege</th><th>Oefenzitting</th></tr>
            <tr><td>1</td><td>Propositielogica</td><td>Waarheidstabellen</td></tr>
            <tr><td>2</td><td>Natuurlijke deductie</td><td>Bewijzen in Lean</td></tr>
            <tr><td>3</td><td>Predikaatlogica</td><td>Kwantoren</td></tr></table>
            <h3>Examen</h3><p>Schriftelijk, gesloten boek. Datum volgt via de examenregeling.</p></body></html>"""
        const val DEMO_PAGE = """
            <p><strong>Cursustekst "Logic and Proof"</strong></p>
            <p>We gebruiken de open cursustekst. Een gedrukte versie is verkrijgbaar bij de cursusdienst.</p>
            <p><a href="https://ultra.edu.kuleuven.cloud/bbcswebdav/xid-1_1?demo=1" data-bbfile="{&quot;displayName&quot;:&quot;cursustekst-logica.pdf&quot;,&quot;mimeType&quot;:&quot;application/pdf&quot;}">cursustekst-logica.pdf</a></p>
            <p><a href="https://ultra.edu.kuleuven.cloud/bbcswebdav/xid-2_1?demo=1" data-bbfile="{&quot;displayName&quot;:&quot;addendum.pdf&quot;,&quot;mimeType&quot;:&quot;application/pdf&quot;}">addendum.pdf</a></p>
            <h3>Planning</h3>
            <table><tr><th>Week</th><th>Onderwerp</th></tr><tr><td>1</td><td>Propositielogica</td></tr><tr><td>2</td><td>Natuurlijke deductie</td></tr></table>
            <p style="color: #0f1818">Tekst met een hard-gecodeerde kleur blijft leesbaar in het donker thema.</p>
        """
        const val DEMO_ANNOUNCEMENT = """
            <p>Beste student,</p><br>
            <p>Dinsdag, van 9u tot 10u30, staat er een studiebegeleidingssessie gepland in jullie agenda.</p>
            <ul><li>algemene studie- en motivatietips</li><li>technische subtiliteiten in de leerstof</li><li>tijd voor jullie vragen</li></ul>
            <p>Meer info op <a href="https://www.kuleuven.be">kuleuven.be</a>.</p>
        """
    }
}
