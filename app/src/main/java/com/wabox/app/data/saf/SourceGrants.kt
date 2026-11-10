package com.wabox.app.data.saf

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.util.StatusPaths

/** Everything about SAF tree permissions: picker targets, persisting, checking, releasing. */
class SourceGrants(private val context: Context) {
    private val resolver = context.contentResolver

    /** URI the system picker should open at when connecting [kind]. */
    fun initialUriFor(kind: AppKind): Uri? = StatusPaths.initialDocumentId(kind)?.let {
        DocumentsContract.buildDocumentUri(StatusPaths.EXTERNAL_STORAGE_AUTHORITY, it)
    }

    fun initialUriForDocument(documentId: String): Uri =
        DocumentsContract.buildDocumentUri(StatusPaths.EXTERNAL_STORAGE_AUTHORITY, documentId)

    /** @return false if the provider refused a persistable grant. */
    fun persist(treeUri: Uri): Boolean = try {
        resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        false
    }

    fun release(treeUri: Uri) {
        try {
            resolver.releasePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Already gone; nothing to release.
        }
    }

    fun isGranted(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    fun grantedCount(): Int = resolver.persistedUriPermissions.count { it.isReadPermission }

    fun isInstalled(kind: AppKind): Boolean {
        val pkg = packageOf(kind) ?: return false
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launchIntent(kind: AppKind): Intent? =
        packageOf(kind)?.let { context.packageManager.getLaunchIntentForPackage(it) }

    private fun packageOf(kind: AppKind): String? = when (kind) {
        AppKind.WHATSAPP -> StatusPaths.WHATSAPP_PACKAGE
        AppKind.BUSINESS -> StatusPaths.BUSINESS_PACKAGE
        AppKind.OTHER -> null
    }
}
