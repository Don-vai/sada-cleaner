
package com.sadaregon.cleaner

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.graphics.Color
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

    private data class MediaItem(
        val uri: Uri,
        val name: String,
        val size: Long,
        val kind: String,
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
    private lateinit var deleteButton: Button

    private val worker = Executors.newSingleThreadExecutor()
    private val scanning = AtomicBoolean(false)
    private val candidates = mutableListOf<MediaItem>()

    companion object {
        private const val REQUEST_PERMISSIONS = 410
        private const val REQUEST_DELETE = 411
        private const val LARGE_FILE_BYTES = 100L * 1024L * 1024L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updateStorageInfo()

        scanButton.setOnClickListener {
            requestPermissionsAndScan()
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

    private fun matchWrap() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(12, 17, 30))
        }

        root.addView(
            makeText("SADA CLEANER", 25f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(16))
            },
            matchWrap()
        )

        storageText = makeText(
            "Checking storage…",
            15f,
            Color.rgb(150, 220, 255)
        )
        root.addView(storageText, matchWrap())

        statusText = makeText(
            "Scan accessible phone media.",
            14f,
            Color.LTGRAY
        ).apply {
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(statusText, matchWrap())

        progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
        }

        root.addView(
            progressBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
            )
        )

        progressText = makeText("Ready", 13f, Color.WHITE)
        root.addView(progressText, matchWrap())

        scanButton = makeButton("SCAN PHONE MEDIA")
        root.addView(scanButton, matchWrap())

        deleteButton = makeButton("DELETE SELECTED FILES")
        deleteButton.isEnabled = false
        root.addView(deleteButton, matchWrap())

        root.addView(
            makeText(
                "Review files before deleting",
                18f,
                Color.WHITE
            ).apply {
                setPadding(0, dp(18), 0, dp(8))
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
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
            )

            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )

            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun requestPermissionsAndScan() {
        if (scanning.get()) return

        val permissions = requiredPermissions()
        val missing = permissions.filterNot { hasPermission(it) }

        if (missing.isEmpty()) {
            startScan()
        } else {
            requestPermissions(
                missing.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }
    }

    @Deprecated("Uses Android's permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode != REQUEST_PERMISSIONS) return

        val canRead = if (Build.VERSION.SDK_INT >= 33) {
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
                hasPermission(Manifest.permission.READ_MEDIA_VIDEO) ||
                hasPermission(Manifest.permission.READ_MEDIA_AUDIO) ||
                (
                    Build.VERSION.SDK_INT >= 34 &&
                    hasPermission(
                        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
                    )
                )
        } else {
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (canRead) {
            startScan()
        } else {
            toast("Media permission nahi mili.")
        }
    }

    private fun startScan() {
        if (!scanning.compareAndSet(false, true)) return

        scanButton.isEnabled = false
        deleteButton.isEnabled = false
        resultsBox.removeAllViews()
        candidates.clear()
        progressBar.progress = 0
        progressText.text = "Preparing scan…"
        statusText.text = "Scanning accessible media files…"

        worker.execute {
            try {
                val files = queryAccessibleMedia()
                val total = files.size
                val found = LinkedHashMap<String, MediaItem>()

                // Identical content hashes identify exact duplicates.
                val firstByHash = HashMap<String, MediaItem>()

                files.forEachIndexed { index, item ->
                    if (Thread.currentThread().isInterrupted) {
                        return@forEachIndexed
                    }

                    if (item.size >= LARGE_FILE_BYTES) {
                        item.reason = addReason(
                            item.reason,
                            "Large file (100 MB+)"
                        )
                        found[item.uri.toString()] = item
                    }

                    val lower = item.name.lowercase(Locale.ROOT)

                    if (
                        lower.endsWith(".tmp") ||
                        lower.endsWith(".temp") ||
                        lower.endsWith(".bak") ||
                        lower.endsWith(".old") ||
                        lower.endsWith(".log") ||
                        lower.endsWith(".cache")
                    ) {
                        item.reason = addReason(
                            item.reason,
                            "Possible junk filename"
                        )
                        found[item.uri.toString()] = item
                    }

                    // Hashing may take time for large files.
                    val hash = try {
                        sha256(item.uri)
                    } catch (_: Exception) {
                        null
                    }

                    if (hash != null) {
                        val original = firstByHash[hash]

                        if (original == null) {
                            firstByHash[hash] = item
                        } else {
                            original.reason = addReason(
                                original.reason,
                                "Exact duplicate content"
                            )
                            item.reason = addReason(
                                item.reason,
                                "Exact duplicate content"
                            )

                            found[original.uri.toString()] = original
                            found[item.uri.toString()] = item
                        }
                    }

                    val done = index + 1
                    val percent = if (total > 0) {
                        done * 100 / total
                    } else {
                        100
                    }

                    runOnUiThread {
                        progressBar.progress = percent
                        progressText.text =
                            "Checked $done / $total files ($percent%)"
                    }
                }

                runOnUiThread {
                    candidates.clear()
                    candidates.addAll(found.values)

                    renderResults()
                    updateStorageInfo()

                    statusText.text =
                        "Scan complete: $total accessible files checked. " +
                        "${candidates.size} files need review."

                    progressBar.progress = 100
                    progressText.text = "100% complete"
                    scanButton.isEnabled = true
                    deleteButton.isEnabled = candidates.any {
                        it.checked
                    }
                    scanning.set(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text =
                        "Scan failed: ${e.message ?: "Unknown error"}"
                    progressText.text = "Scan stopped"
                    scanButton.isEnabled = true
                    scanning.set(false)
                }
            }
        }
    }

    private fun addReason(old: String, next: String): String {
        if (old.isBlank()) return next
        if (old.contains(next)) return old
        return "$old • $next"
    }

    private fun queryAccessibleMedia(): List<MediaItem> {
        val result = mutableListOf<MediaItem>()

        val imagePermission = Build.VERSION.SDK_INT < 33 ||
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
            (
                Build.VERSION.SDK_INT >= 34 &&
                hasPermission(
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
                )
            )

        val videoPermission = Build.VERSION.SDK_INT < 33 ||
            hasPermission(Manifest.permission.READ_MEDIA_VIDEO)

        val audioPermission = Build.VERSION.SDK_INT < 33 ||
            hasPermission(Manifest.permission.READ_MEDIA_AUDIO)

        if (imagePermission) {
            queryCollection(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image",
                result
            )
        }

        if (videoPermission) {
            queryCollection(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "video",
                result
            )
        }

        if (audioPermission) {
            queryCollection(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "audio",
                result
            )
        }

        return result
    }

    private fun queryCollection(
        collection: Uri,
        kind: String,
        output: MutableList<MediaItem>
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

                val idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns._ID
                )
                val nameColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.DISPLAY_NAME
                )
                val sizeColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.SIZE
                )

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)

                    output.add(
                        MediaItem(
                            uri = ContentUris.withAppendedId(
                                collection,
                                id
                            ),
                            name = cursor.getString(nameColumn)
                                ?: "Unknown file",
                            size = cursor.getLong(sizeColumn)
                                .coerceAtLeast(0L),
                            kind = kind
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Some media may not be accessible.
        } catch (_: Exception) {
            // Continue scanning other collections.
        }
    }

    private fun sha256(uri: Uri): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val stream: InputStream =
            contentResolver.openInputStream(uri) ?: return null

        stream.use { input ->
            val buffer = ByteArray(64 * 1024)

            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
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
                    "No matching files found in accessible media.",
                    14f,
                    Color.LTGRAY
                )
            )
            deleteButton.isEnabled = false
            return
        }

        candidates.forEach { item ->
            val check = CheckBox(this).apply {
                text = "${item.name}\n" +
                    "${formatBytes(item.size)} • ${item.kind}\n" +
                    item.reason

                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isChecked = false

                setOnCheckedChangeListener { _, checked ->
                    item.checked = checked
                    deleteButton.isEnabled =
                        candidates.any { it.checked }
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

        deleteButton.isEnabled = false
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
                "${selected.size} selected files delete karne ki " +
                "permission maangi jayegi. Delete karne se pehle " +
                "file names check kar lo."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                deleteSelected(selected)
            }
            .show()
    }

    private fun deleteSelected(selected: List<MediaItem>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val request = MediaStore.createDeleteRequest(
                    contentResolver,
                    selected.map { it.uri }
                )

                @Suppress("DEPRECATION")
                startIntentSenderForResult(
                    request.intentSender,
                    REQUEST_DELETE,
                    null,
                    0,
                    0,
                    0
                )
            } catch (e: Exception) {
                toast(
                    "Delete request failed: " +
                    (e.message ?: "Unknown error")
                )
            }
        } else {
            var deleted = 0

            selected.forEach { item ->
                try {
                    if (
                        contentResolver.delete(
                            item.uri,
                            null,
                            null
                        ) > 0
                    ) {
                        deleted++
                    }
                } catch (_: Exception) {
                    // Android can reject deletion of files not owned by this app.
                }
            }

            toast(
                "$deleted files deleted. Kuch files ke liye " +
                "Android permission required ho sakti hai."
            )

            startScan()
        }
    }

    @Deprecated("Uses Android's activity result callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_DELETE) {
            if (resultCode == RESULT_OK) {
                toast("Delete request approved.")
            } else {
                toast("Delete cancelled.")
            }

            startScan()
        }
    }

    private fun updateStorageInfo() {
        try {
            val stat = StatFs(
                Environment.getDataDirectory().path
            )

            val total =
                stat.blockCountLong * stat.blockSizeLong
            val free =
                stat.availableBlocksLong * stat.blockSizeLong
            val used = (total - free).coerceAtLeast(0L)

            storageText.text =
                "Device data storage\n" +
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
        } while (
            value >= 1024 &&
            unit < units.lastIndex
        )

        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            value,
            units[unit]
        )
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
