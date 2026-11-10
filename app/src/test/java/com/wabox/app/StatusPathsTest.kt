package com.wabox.app

import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.MediaKind
import com.wabox.app.core.util.StatusPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPathsTest {
    @Test fun classifiesScopedStoragePackages() {
        assertEquals(AppKind.WHATSAPP, StatusPaths.classify("primary:Android/media/com.whatsapp"))
        assertEquals(AppKind.WHATSAPP, StatusPaths.classify("primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses"))
        assertEquals(AppKind.BUSINESS, StatusPaths.classify("primary:Android/media/com.whatsapp.w4b"))
        assertEquals(AppKind.BUSINESS, StatusPaths.classify("primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"))
    }

    @Test fun classifiesLegacyFolders() {
        assertEquals(AppKind.WHATSAPP, StatusPaths.classify("primary:WhatsApp"))
        assertEquals(AppKind.BUSINESS, StatusPaths.classify("primary:WhatsApp Business/Media/.Statuses"))
    }

    @Test fun unrelatedFoldersAreOther() {
        assertEquals(AppKind.OTHER, StatusPaths.classify("primary:DCIM/Camera"))
        assertEquals(AppKind.OTHER, StatusPaths.classify("1234-ABCD:Backups/whatsappish"))
    }

    @Test fun initialPickerTargetsAreGrantableMediaFolders() {
        assertEquals("primary:Android/media/com.whatsapp", StatusPaths.initialDocumentId(AppKind.WHATSAPP))
        assertEquals("primary:Android/media/com.whatsapp.w4b", StatusPaths.initialDocumentId(AppKind.BUSINESS))
        assertNull(StatusPaths.initialDocumentId(AppKind.OTHER))
    }

    @Test fun relativePathAndNesting() {
        val root = "primary:Android/media/com.whatsapp"
        val child = "primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
        assertEquals("WhatsApp/Media/.Statuses", StatusPaths.relativePath(root, child))
        assertTrue(StatusPaths.isWithin(child, root))
        assertTrue(StatusPaths.isWithin(root, root))
        assertFalse(StatusPaths.isWithin(root, child))
        assertFalse(StatusPaths.isWithin("primary:Android/media/com.whatsapp.w4b", root)) // prefix but not nested
        assertFalse(StatusPaths.isWithin("ABCD-1234:Android/media/com.whatsapp", root)) // other volume
    }

    @Test fun displayPathIsHumanReadable() {
        assertEquals("Internal storage › Android › media › com.whatsapp", StatusPaths.displayPath("primary:Android/media/com.whatsapp"))
        assertEquals("SD card › WhatsApp", StatusPaths.displayPath("1A2B-3C4D:WhatsApp"))
    }

    @Test fun discoverySkipsHeavyMediaFolders() {
        listOf("WhatsApp Images", "WhatsApp Video", "WhatsApp Business Images", "WhatsApp Voice Notes", "Databases", "Backups", ".Shared")
            .forEach { assertTrue(it, StatusPaths.shouldSkipDuringDiscovery(it)) }
        listOf("WhatsApp", "WhatsApp Business", "Media", "accounts", "1")
            .forEach { assertFalse(it, StatusPaths.shouldSkipDuringDiscovery(it)) }
    }

    @Test fun mediaFiltering() {
        assertEquals(MediaKind.IMAGE, StatusPaths.mediaKindOf("A1B2C3.jpg", "image/jpeg"))
        assertEquals(MediaKind.VIDEO, StatusPaths.mediaKindOf("A1B2C3.mp4", null))
        assertEquals(MediaKind.VIDEO, StatusPaths.mediaKindOf("clip.bin", "video/mp4"))
        assertNull(StatusPaths.mediaKindOf(".nomedia", "application/octet-stream"))
        assertNull(StatusPaths.mediaKindOf(".hidden.jpg", "image/jpeg"))
        assertNull(StatusPaths.mediaKindOf("notes.txt", "text/plain"))
        assertEquals("video/3gpp", StatusPaths.mimeFor("x.3gp", "application/octet-stream", MediaKind.VIDEO))
        assertEquals("image/webp", StatusPaths.mimeFor("x.webp", null, MediaKind.IMAGE))
    }

    @Test fun defaultLabelsNumberAdditionalFolders() {
        assertEquals("WhatsApp", StatusPaths.defaultLabel(AppKind.WHATSAPP, 0, "com.whatsapp"))
        assertEquals("WhatsApp 2", StatusPaths.defaultLabel(AppKind.WHATSAPP, 1, "com.whatsapp"))
        assertEquals("DualApp", StatusPaths.defaultLabel(AppKind.OTHER, 0, "DualApp"))
    }
}
