package com.wabox.app

import com.wabox.app.core.model.MediaKind
import com.wabox.app.data.repo.ScannedFile
import com.wabox.app.data.repo.StatusDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusDiffTest {
    private fun file(name: String, size: Long = 100, modified: Long = 1) =
        ScannedFile("primary:x/.Statuses/$name", name, "image/jpeg", MediaKind.IMAGE, size, modified)

    private fun uri(doc: String) = "content://test/$doc"

    @Test fun firstScanInsertsEverything() {
        val r = StatusDiff.compute(1, emptyList(), listOf(file("a.jpg"), file("b.jpg")), now = 50, documentUri = ::uri)
        assertEquals(2, r.added.size)
        assertEquals(2, r.upserts.size)
        assertTrue(r.deletes.isEmpty())
        assertTrue(r.added.all { it.firstSeenAt == 50L })
    }

    @Test fun unchangedScanIsEmpty() {
        val first = StatusDiff.compute(1, emptyList(), listOf(file("a.jpg")), 50, ::uri)
        val second = StatusDiff.compute(1, first.upserts, listOf(file("a.jpg")), 99, ::uri)
        assertTrue(second.isEmpty)
    }

    @Test fun removedFilesAreDeletedAndChangedFilesKeepFirstSeen() {
        val first = StatusDiff.compute(1, emptyList(), listOf(file("a.jpg"), file("b.jpg")), 50, ::uri)
        val second = StatusDiff.compute(1, first.upserts, listOf(file("a.jpg", size = 200)), 99, ::uri)
        assertEquals(1, second.deletes.size)
        assertEquals(1, second.upserts.size)
        assertEquals(0, second.added.size)
        assertEquals(50L, second.upserts.single().firstSeenAt)
        assertEquals(200L, second.upserts.single().size)
    }

    @Test fun idsAreStableAndFolderScoped() {
        assertEquals(StatusDiff.idFor(1, "doc"), StatusDiff.idFor(1, "doc"))
        assertTrue(StatusDiff.idFor(1, "doc") != StatusDiff.idFor(2, "doc"))
    }

    @Test fun duplicateDocumentsInOneScanAreIgnored() {
        val r = StatusDiff.compute(1, emptyList(), listOf(file("a.jpg"), file("a.jpg")), 1, ::uri)
        assertEquals(1, r.added.size)
    }
}
