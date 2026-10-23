package be.tolly.ui

import be.tolly.core.model.Notices
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeTextsTest {
    /** Raising [Notices.LATEST] without writing the notice would show nothing to anyone. */
    @Test
    fun everyNoticeHasText() {
        for (id in 1..Notices.LATEST) assertTrue("Notice $id has no text in NoticeSheet", id in noticeTexts)
    }
}
