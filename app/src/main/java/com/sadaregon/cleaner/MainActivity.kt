package com.sadaregon.cleaner

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {

    private data class FileItem(
        val uri: Uri,
        val name: String,
        val size: Long,
        val kind: String,
        val isSaf: Boolean,
        var reason: String = "",
        var checked: Boolean = false
    )

    private lateinit var root: LinearLayout
    private lateinit var storageText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var resultsBox: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var folderButton: Button
    private lateinit var selectAllButton: Button
    private lateinit var deleteButton: Button

    private val worker = Executors.newSingleThreadExecutor()
    private val scanning = AtomicBoolean(false)
    private val candidates = mutableListOf<FileItem>()

    private var allSelected = false
    private var selectedFolder: Uri? = null

    companion object {
        private const val REQUEST_PERMISSIONS = 410
        private const val REQUEST_FOLDER = 412
        private const val REQUEST_DELETE = 411

        private const val LARGE_FILE_BYTES =
            100L * 1024L * 1024L

        private const val MAX_HASH_BYTES =
            100L * 1024L * 1024L

        private val JUNK_EXTENSIONS = setOf(
            "tmp", "temp", "bak", "old", "log",
            "cache", "dmp", "part", "crdownload",
            "download", "partial", "thumb"
        )

        private val INSTALLER_EXTENSIONS = setOf(
            "apk", "xapk", "apks"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()
        updateStorageInfo()

        scanButton.setOnClickListener {
            requestPermissionsAndScan()
        }

        folderButton.setOnClickListener {
            openFolderPicker()
        }

        selectAllButton.setOnClickListener {
            setAllSelected(!allSelected)
        }

        deleteButton.setOnClickListener {
            confirmDeleteSelected()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun makeText(
        value: String,
        size: Float,
        color: Int
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
    }

    private fun makeButton(value: String) =
        Button(this).apply {
            text = value
            isAllCaps = false
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.rgb(12, 17, 30))
        }

        root.addView(
            makeText("SADA CLEANER", 25f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(14))
            },
            matchWrap()
        )

        storageText = makeText(
            "Checking storage...", 14f,
            Color.rgb(150, 220, 255)
        )
        root.addView(storageText, matchWrap())

        statusText = makeText(
            "Scan media and choose folders for junk files.",
            13f, Color.LTGRAY
        ).apply {
            setPadding(0, dp(10), 0, dp(8))
        }
        root.addView(statusText, matchWrap())

        progressBar = ProgressBar(
            this, null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
        }

        root.addView(
            progressBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(7)
            )
        )

        progressText = makeText("Ready", 12f, Color.WHITE)
        root.addView(progressText, matchWrap())

        scanButton = makeButton("1. SCAN PHONE MEDIA")
        root.addView(scanButton, matchWrap())

        folderButton = makeButton("2. SCAN DOWNLOADS / FOLDER")
        root.addView(folderButton, matchWrap())

        selectAllButton = makeButton("SELECT ALL")
        root.addView(selectAllButton, matchWrap())

        deleteButton = makeButton("DELETE SELECTED FILES")
        deleteButton.isEnabled = false
        root.addView(deleteButton, matchWrap())

        root.addView(
            makeText("SCAN RESULTS", 17f, Color.WHITE).apply {
                setPadding(0, dp(12), 0, dp(6))
            },
            matchWrap()
        )

        resultsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(resultsBox)
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        )

        setContentView(root)
    }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun requestPermissionsAndScan() {
        if (scanning.get()) return

        val missing = requiredPermissions()
            .filterNot { hasPermission(it) }

        if (missing.isEmpty()) {
            startScan()
        } else {
            @Suppress("DEPRECATION")
            requestPermissions(
                missing.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }
    }

    @Deprecated("Uses Android permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode != REQUEST_PERMISSIONS) return

        val canRead = if (Build.VERSION.SDK_INT >= 33) {
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
                hasPermission(Manifest.permission.READ_MEDIA_VIDEO) ||
                hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            hasPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        if (canRead) {
            startScan()
        } else {
            toast("Media permission nahi mili. Folder picker try karo.")
        }
    }

    private fun openFolderPicker() {
        if (scanning.get()) {
            toast("Scan complete hone do.")
            return
        }

        try {
            val intent = Intent(
                Intent.ACTION_OPEN_DOCUMENT_TREE
            )

            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_FOLDER)
        } catch (e: Exception) {
            toast("Folder picker open nahi hua.")
        }
    }

    @Deprecated("Uses activity result callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_FOLDER &&
            resultCode == RESULT_OK
        ) {
            val uri = data?.data ?: return

            try {
                val flags = data.flags and (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                contentResolver.takePersistableUriPermission(
                    uri, flags
                )
            } catch (_: Exception) {
                // Temporary access may still be available.
            }

            selectedFolder = uri
            startScan()
        }

        if (requestCode == REQUEST_DELETE) {
            if (resultCode == RESULT_OK) {
                toast("Android ne delete request approve ki.")
            } else {
                toast("Delete cancel hua.")
            }

            startScan()
        }
    }

    private fun startScan() {
        if (!scanning.compareAndSet(false, true)) return

        scanButton.isEnabled = false
        folderButton.isEnabled = false
        selectAllButton.isEnabled = false
        deleteButton.isEnabled = false

        candidates.clear()
        resultsBox.removeAllViews()
        allSelected = false
        selectAllButton.text = "SELECT ALL"

        progressBar.progress = 0
        progressText.text = "Preparing..."
        statusText.text = "Scanning accessible files..."

        val folder = selectedFolder

        worker.execute {
            try {
                val all = LinkedHashMap<String, FileItem>()

                val media = queryAccessibleMedia()

                media.forEachIndexed { index, item ->
                    classify(item, all)

                    if (index % 20 == 0) {
                        publishProgress(
                            index + 1,
                            media.size,
                            "Scanning phone media"
                        )
                    }
                }

                if (folder != null) {
                    val docs = queryFolderTree(folder)

                    docs.forEachIndexed { index, item ->
                        classify(item, all)

                        if (index % 20 == 0) {
                            publishProgress(
                                index + 1,
                                docs.size,
                                "Scanning selected folder"
                            )
                        }
                    }
                }

                // Hash only files with matching sizes.
                // Skip very large files to improve scan speed.
                val eligible = all.values
                    .filter {
                        it.size > 0 &&
                            it.size <= MAX_HASH_BYTES
                    }
                    .groupBy { it.size }
                    .values
                    .filter { it.size > 1 }
                    .flatten()

                val firstByHash = HashMap<String, FileItem>()

                eligible.forEachIndexed { index, item ->
                    val hash = try {
                        sha256(item.uri)
                    } catch (_: Exception) {
                        null
                    }

                    if (hash != null) {
                        val key = "${item.size}:$hash"
                        val original = firstByHash[key]

                        if (original == null) {
                            firstByHash[key] = item
                        } else {
                            original.reason = addReason(
                                original.reason,
                                "Exact duplicate"
                            )
                            item.reason = addReason(
                                item.reason,
                                "Exact duplicate"
                            )
                        }
                    }

                    if (index % 10 == 0) {
                        publishProgress(
                            index + 1,
                            eligible.size,
                            "Checking duplicate files"
                        )
                    }
                }

                val found = all.values.filter {
                    it.reason.isNotBlank()
                }.sortedByDescending { it.size }

                runOnUiThread {
                    candidates.clear()
                    candidates.addAll(found)

                    renderResults()
                    updateStorageInfo()

                    statusText.text =
                        "Scan complete: ${all.size} files checked. " +
                        "${found.size} files need review."

                    progressBar.progress = 100
                    progressText.text = "Scan complete"
                    finishScanning()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text =
                        "Scan failed: ${e.message ?: "Unknown error"}"
                    finishScanning()
                }
            }
        }
    }

    private fun publishProgress(
        done: Int,
        total: Int,
        label: String
    ) {
        val percent = if (total > 0) done * 100 / total else 100

        runOnUiThread {
            progressBar.progress = percent
            progressText.text = "$label: $done / $total"
        }
    }

    private fun finishScanning() {
        scanning.set(false)
        scanButton.isEnabled = true
        folderButton.isEnabled = true
        selectAllButton.isEnabled = candidates.isNotEmpty()
        deleteButton.isEnabled = candidates.any { it.checked }
    }

    private fun classify(
        item: FileItem,
        output: MutableMap<String, FileItem>
    ) {
        val lower = item.name.lowercase(Locale.ROOT)
        val extension = lower.substringAfterLast(
            '.', ""
        )

        if (item.size >= LARGE_FILE_BYTES) {
            item.reason = addReason(
                item.reason, "Large file (100 MB+)"
            )
        }

        if (extension in JUNK_EXTENSIONS) {
            item.reason = addReason(
                item.reason, "Temporary / possible junk"
            )
        }

        if (extension in INSTALLER_EXTENSIONS) {
            item.reason = addReason(
                item.reason, "Installer file - review before deleting"
            )
        }

        if (item.reason.isNotBlank()) {
            output[item.uri.toString()] = item
        } else {
            // Keep all files temporarily for duplicate checking.
            output[item.uri.toString()] = item
        }
    }

    private fun addReason(old: String, next: String): String {
        if (old.isBlank()) return next
        if (old.split(" • ").contains(next)) return old
        return "$old • $next"
    }

    private fun queryAccessibleMedia(): List<FileItem> {
        val result = mutableListOf<FileItem>()

        val collections = listOf(
            Triple(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image",
                Manifest.permission.READ_MEDIA_IMAGES
            ),
            Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "video",
                Manifest.permission.READ_MEDIA_VIDEO
            ),
            Triple(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "audio",
                Manifest.permission.READ_MEDIA_AUDIO
            )
        )

        for ((uri, kind, permission) in collections) {
            val allowed = if (Build.VERSION.SDK_INT >= 33) {
                hasPermission(permission)
            } else {
                hasPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }

            if (allowed) {
                queryMediaCollection(uri, kind, result)
            }
        }

        return result
    }

    private fun queryMediaCollection(
        collection: Uri,
        kind: String,
        output: MutableList<FileItem>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )

        try {
            contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.SIZE} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns._ID
                )
                val nameCol = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.DISPLAY_NAME
                )
                val sizeCol = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.SIZE
                )

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)

                    output.add(
                        FileItem(
                            uri = ContentUris.withAppendedId(
                                collection, id
                            ),
                            name = cursor.getString(nameCol)
                                ?: "Unknown file",
                            size = cursor.getLong(sizeCol)
                                .coerceAtLeast(0L),
                            kind = kind,
                            isSaf = false
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Continue with the other collections.
        }
    }

    private fun queryFolderTree(treeUri: Uri): List<FileItem> {
        val result = mutableListOf<FileItem>()
        val visited = HashSet<String>()
        val queue = ArrayDeque<String>()

        try {
            val rootId = DocumentsContract.getTreeDocumentId(
                treeUri
            )
            queue.add(rootId)

            while (queue.isNotEmpty()) {
                val parentId = queue.removeFirst()

                if (!visited.add(parentId)) continue

                val childrenUri =
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri, parentId
                    )

                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

                contentResolver.query(
                    childrenUri,
                    projection,
                    null, null, null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    )
                    val nameCol = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )
                    val sizeCol = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_SIZE
                    )
                    val mimeCol = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    )

                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idCol) ?: continue
                        val name = cursor.getString(nameCol)
                            ?: "Unknown file"
                        val mime = cursor.getString(mimeCol) ?: ""

                        if (
                            mime == DocumentsContract.Document.MIME_TYPE_DIR
                        ) {
                            queue.add(id)
                        } else {
                            val size = if (sizeCol >= 0 &&
                                !cursor.isNull(sizeCol)
                            ) {
                                cursor.getLong(sizeCol).coerceAtLeast(0L)
                            } else {
                                0L
                            }

                            result.add(
                                FileItem(
                                    uri = DocumentsContract
                                        .buildDocumentUriUsingTree(
                                            treeUri, id
                                        ),
                                    name = name,
                                    size = size,
                                    kind = "folder file",
                                    isSaf = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // A restricted or unavailable directory may be skipped.
        }

        return result
    }

    private fun sha256(uri: Uri): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val stream: InputStream =
            contentResolver.openInputStream(uri) ?: return null

        stream.use { input ->
            val buffer = ByteArray(32 * 1024)

            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }

        return digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }

    private fun renderResults() {
        resultsBox.removeAllViews()

        if (candidates.isEmpty()) {
            resultsBox.addView(
                makeText(
                    "No matching files found. Try scanning a folder.",
                    14f, Color.LTGRAY
                )
            )
            selectAllButton.isEnabled = false
            deleteButton.isEnabled = false
            return
        }

        candidates.forEach { item ->
            val check = CheckBox(this).apply {
                text = "${item.name}\n" +
                    "${formatBytes(item.size)} • ${item.kind}\n" +
                    item.reason

                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(dp(4), dp(7), dp(4), dp(7))
                isChecked = item.checked

                setOnCheckedChangeListener { _, checked ->
                    item.checked = checked
                    refreshSelectionButtons()
                }
            }

            resultsBox.addView(check, matchWrap())

            resultsBox.addView(
                View(this).apply {
                    setBackgroundColor(Color.rgb(50, 60, 78))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(1)
                )
            )
        }

        refreshSelectionButtons()
    }

    private fun setAllSelected(select: Boolean) {
        allSelected = select

        candidates.forEach { it.checked = select }

        // Re-render checkboxes with the new checked state.
        renderResults()

        allSelected = select
        selectAllButton.text =
            if (select) "DESELECT ALL" else "SELECT ALL"

        refreshSelectionButtons()
    }

    private fun refreshSelectionButtons() {
        val selectedCount = candidates.count { it.checked }

        allSelected = candidates.isNotEmpty() &&
            selectedCount == candidates.size

        selectAllButton.text =
            if (allSelected) "DESELECT ALL" else "SELECT ALL"

        selectAllButton.isEnabled = candidates.isNotEmpty()
        deleteButton.isEnabled = selectedCount > 0
    }

    private fun confirmDeleteSelected() {
        val selected = candidates.filter { it.checked }

        if (selected.isEmpty()) {
            toast("Pehle files select karo.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete selected files?")
            .setMessage(
                "${selected.size} files selected hain.\n\n" +
                "Delete karne se pehle names check kar lo. " +
                "Important files ko select mat karna."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                deleteSelected(selected)
            }
            .show()
    }

    private fun deleteSelected(selected: List<FileItem>) {
        if (scanning.get()) return

        scanButton.isEnabled = false
        folderButton.isEnabled = false
        deleteButton.isEnabled = false

        worker.execute {
            var safDeleted = 0
            var failed = 0

            // Delete only user-selected files from the chosen SAF folder.
            selected.filter { it.isSaf }.forEach { item ->
                try {
                    if (
                        DocumentsContract.deleteDocument(
                            contentResolver, item.uri
                        )
                    ) {
                        safDeleted++
                    } else {
                        failed++
                    }
                } catch (_: Exception) {
                    failed++
                }
            }

            val mediaItems = selected.filterNot { it.isSaf }

            runOnUiThread {
                if (mediaItems.isEmpty()) {
                    toast(
                        "$safDeleted files deleted. Failed: $failed"
                    )
                    startScan()
                } else if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ) {
                    try {
                        val request = MediaStore.createDeleteRequest(
                            contentResolver,
                            mediaItems.map { it.uri }
                        )

                        @Suppress("DEPRECATION")
                        startIntentSenderForResult(
                            request.intentSender,
                            REQUEST_DELETE,
                            null, 0, 0, 0
                        )
                    } catch (e: Exception) {
                        toast(
                            "Media delete request failed: " +
                                (e.message ?: "Unknown error")
                        )
                        startScan()
                    }
                } else {
                    var mediaDeleted = 0
                    mediaItems.forEach { item ->
                        try {
                            if (
                                contentResolver.delete(
                                    item.uri, null, null
                                ) > 0
                            ) {
                                mediaDeleted++
                            } else {
                                failed++
                            }
                        } catch (_: Exception) {
                            failed++
                        }
                    }

                    toast(
                        "${safDeleted + mediaDeleted} files deleted. " +
                            "Failed: $failed"
                    )
                    startScan()
                }
            }
        }
    }

    private fun updateStorageInfo() {
        try {
            val stat = StatFs(
                Environment.getDataDirectory().path
            )

            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val used = (total - free).coerceAtLeast(0L)

            storageText.text =
                "Device storage\n" +
                    "Used: ${formatBytes(used)} / ${formatBytes(total)}\n" +
                    "Available: ${formatBytes(free)}"
        } catch (_: Exception) {
            storageText.text = "Storage information unavailable"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"

        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1

        do {
            value /= 1024.0
            unit++
        } while (value >= 1024 && unit < units.lastIndex)

        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            value, units[unit]
        )
    }

    private fun toast(message: String) {
        Toast.makeText(
            this, message, Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
