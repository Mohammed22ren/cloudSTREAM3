package com.kodastream.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.kodastream.app.R
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream

/**
 * The download list: one JSON record per file, same persistence and same
 * in-memory-plus-background-write pattern as History. Each record carries
 * its own full file URI, so entries keep playing and keep being deletable
 * even after the download location changes under them.
 */
object DownloadStore {
    const val STATE_QUEUED = "queued"
    const val STATE_DOWNLOADING = "downloading"
    const val STATE_PAUSED = "paused"
    const val STATE_FAILED = "failed"
    const val STATE_DONE = "done"

    data class Download(
        /** idOf(): provider|details url|season|episode - matches the watched-store granularity. */
        val id: String,
        val apiName: String,
        val url: String,
        val title: String,
        val posterUrl: String? = null,
        val posterHeaders: Map<String, String>? = null,
        /** movie | series | anime */
        val type: String = "movie",
        val season: Int? = null,
        val episode: Int? = null,
        val episodeTitle: String? = null,
        val year: Int? = null,
        val linkUrl: String,
        val linkName: String = "",
        val quality: Int = -1,
        val headers: Map<String, String> = emptyMap(),
        /** direct | hls (dash is rejected before enqueue). */
        val streamType: String = "direct",
        val fileName: String,
        val fileUri: String? = null,
        val posterUri: String? = null,
        val state: String = STATE_QUEUED,
        val receivedBytes: Long = 0,
        val totalBytes: Long = 0,
        /** Where an interrupted HLS download continues from. */
        val hlsSegment: Int = 0,
        val hlsTotal: Int = 0,
        val error: String? = null,
        val createdAt: Long = 0,
    ) {
        val isHls: Boolean get() = streamType == "hls"
        /** 0f..1f while downloading, or -1 when the size is still unknown. */
        val progressFraction: Float
            get() = when {
                state == STATE_DONE -> 1f
                isHls -> if (hlsTotal > 0) hlsSegment.toFloat() / hlsTotal else -1f
                totalBytes > 0 -> receivedBytes.toFloat() / totalBytes
                else -> -1f
            }
    }

    private const val STORE_KEY = "downloads"
    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<Download>?>(null)
    val entries: StateFlow<List<Download>?> = _entries.asStateFlow()

    @Volatile
    private var loaded = false

    fun current(context: Context): List<Download> {
        if (!loaded) {
            loaded = true
            _entries.value = context.getKey<List<Download>>(STORE_KEY).orEmpty()
        }
        return _entries.value.orEmpty()
    }

    fun idOf(apiName: String, url: String, season: Int?, episode: Int?): String =
        "$apiName|$url|${season ?: -1}|${episode ?: -1}"

    fun find(context: Context, id: String): Download? = current(context).firstOrNull { it.id == id }

    /** False when this id is already in the list; nothing is overwritten. */
    fun enqueue(context: Context, download: Download): Boolean {
        if (current(context).any { it.id == download.id }) return false
        publish(context, (listOf(download) + current(context)).take(MAX_ENTRIES))
        return true
    }

    fun update(context: Context, id: String, transform: (Download) -> Download) {
        publish(context, current(context).map { if (it.id == id) transform(it) else it })
    }

    fun remove(context: Context, id: String) {
        publish(context, current(context).filter { it.id != id })
    }

    fun clear(context: Context) {
        publish(context, emptyList())
    }

    private fun publish(context: Context, next: List<Download>) {
        _entries.value = next
        scope.launch { context.setKey(STORE_KEY, next) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/**
 * Where downloads land and how to write there. Two modes from one setting:
 * the app's default folder inside app-specific external storage, or any
 * folder the user picked through SAF (tree URI + persistable permission).
 * No MANAGE_EXTERNAL_STORAGE, ever.
 */
object DownloadLocations {

    /** The app's own folder in app-specific external storage - no permission needed. */
    fun defaultDir(context: Context): File =
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            context.getString(R.string.app_name),
        ).apply { mkdirs() }

    /** The picked tree, if it still exists and is still writable; clears the pref when it is not. */
    fun validTree(context: Context): DocumentFile? {
        val uri = AppPrefs.downloadTreeUri(context) ?: return null
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(uri))
        if (tree == null || !tree.canWrite()) {
            AppPrefs.setDownloadTreeUri(context, null)
            return null
        }
        return tree
    }

    /** What the settings row shows: the picked folder's name, or the default path. */
    fun readableLocation(context: Context): String {
        val tree = AppPrefs.downloadTreeUri(context)?.let { Uri.parse(it) }
        return if (tree != null) {
            val name = DocumentFile.fromTreeUri(context, tree)?.name
                ?: DocumentsContract.getTreeDocumentId(tree)
            "📁 $name"
        } else {
            defaultDir(context).absolutePath
        }
    }

    /**
     * Creates (or re-uses for resume) the target file and returns its URI.
     * A fresh start deletes any earlier partial file of the same name first.
     */
    fun ensureTarget(context: Context, download: DownloadStore.Download, fresh: Boolean): String? {
        // Resuming: the record already points at a file that still exists.
        if (!fresh) {
            download.fileUri?.let { uri ->
                if (exists(context, uri)) return uri
            }
        }

        val mime = if (download.isHls) "video/mp2t" else "video/mp4"
        val tree = validTree(context)
        if (tree != null) {
            if (fresh) tree.findFile(download.fileName)?.delete()
            val created = tree.createFile(mime, download.fileName.substringBeforeLast('.'))
            return created?.uri?.toString()
        }
        val file = File(defaultDir(context), download.fileName)
        if (fresh) file.delete()
        file.parentFile?.mkdirs()
        return Uri.fromFile(file).toString()
    }

    fun exists(context: Context, uriString: String): Boolean = when {
        uriString.startsWith("content://") ->
            DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.exists() == true
        uriString.startsWith("file://") -> File(Uri.parse(uriString).path!!).exists()
        else -> false
    }

    fun fileLength(context: Context, uriString: String): Long = when {
        uriString.startsWith("content://") ->
            DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.length() ?: 0
        uriString.startsWith("file://") -> File(Uri.parse(uriString).path!!).length()
        else -> 0
    }

    fun openOutput(context: Context, uriString: String, append: Boolean): OutputStream = when {
        uriString.startsWith("content://") ->
            context.contentResolver.openOutputStream(Uri.parse(uriString), if (append) "wa" else "wt")
                ?: throw java.io.IOException("Cannot open $uriString")
        uriString.startsWith("file://") ->
            File(Uri.parse(uriString).path!!).outputStream(append)
        else -> throw java.io.IOException("Not a file URI: $uriString")
    }

    private fun File.outputStream(append: Boolean): OutputStream =
        if (append) java.io.FileOutputStream(this, true) else java.io.FileOutputStream(this)

    fun delete(context: Context, uriString: String?): Boolean {
        if (uriString == null) return false
        return when {
            uriString.startsWith("content://") ->
                DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.delete() == true
            uriString.startsWith("file://") -> File(Uri.parse(uriString).path!!).delete()
            else -> false
        }
    }

    /** Best-effort free space at the download location. */
    fun freeBytes(context: Context): Long =
        validTree(context)?.let { defaultDir(context).usableSpace } ?: defaultDir(context).usableSpace
}
