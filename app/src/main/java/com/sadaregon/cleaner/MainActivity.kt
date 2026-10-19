package com.sadaregon.cleaner

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ContentUris
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val bg = Color.rgb(9, 12, 27)
    private val card = Color.rgb(23, 28, 49)
    private val purple = Color.rgb(139, 92, 246)
    private val cyan = Color.rgb(34, 211, 238)
    private val white = Color.rgb(246, 247, 255)
    private val muted = Color.rgb(159, 169, 195)
    private val green = Color.rgb(74, 222, 128)

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var scanButton: Button
    private lateinit var deleteButton: Button
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var summaryText: TextView
    private lateinit var resultList: LinearLayout

    private var scanning = false
    private var deleting = false

    private val items = mutableListOf<ScanItem>()
    private val selectedUris = linkedSetOf<String>()

    private val largeLimit = 100L * 1024L * 1024L

    private data class ScanItem(
        val uri: Uri,
        val name: String,
        val size: Long,
        val category: String,
        val junkCandidate: Boolean,
        val large: Boolean,
        val possibleDuplicate: Boolean
    )

    private data class ScanReport(
        val files: List<ScanItem>,
        val errors: Int,
        val totalBytes: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        buildDashboard()
    }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.rgb(22, 19, 48),
                    bg,
                    Color.rgb(8, 15, 30)
                )
            )
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        root.addView(label("SADA CLEANER", 27, white, true))
        root.addView(label("REAL STORAGE SCANNER", 12, cyan, true))
        root.addView(space(8))
        root.addView(
            label(
                "Scan accessible media, review large files and inspect cleanup candidates.",
                14,
                muted
            )
        )

        root.addView(space(22))

        val overview = makeCard()
        overview.addView(label("DEVICE OVERVIEW", 13, muted, true))
        overview.addView(space(10))

        statusText = label("READY TO SCAN", 14, green, true)
        overview.addView(statusText)

        summaryText = label(
            "No scan performed yet.",
            14,
            white
        )
        overview.addView(space(10))
        overview.addView(summaryText)
        root.addView(overview)

        root.addView(space(16))

        scanButton = Button(this).apply {
            text = "SCAN DEVICE"
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(purple, 16)
            setOnClickListener { beginScan() }
        }

        root.addView(
            scanButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )

        root.addView(space(12))

        val progressCard = makeCard()
        progressText = label("Waiting for scan...", 13, muted)
        progressCard.addView(progressText)
        progressCard.addView(space(10))

        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            progressTintList =
                android.content.res.ColorStateList.valueOf(cyan)
        }

        progressCard.addView(
            progress,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
            )
        )

        root.addView(progressCard)
        root.addView(space(20))

        root.addView(label("SCAN RESULTS", 15, white, true))
        root.addView(space(8))

        root.addView(
            label(
                "Select only the files you have reviewed. Large files are not automatically junk.",
                12,
                muted
            )
        )

        root.addView(space(10))

        deleteButton = Button(this).apply {
            text = "DELETE SELECTED FILES"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(170, 48, 65), 14)
            isEnabled = false
            setOnClickListener { confirmDelete() }
        }

        root.addView(
            deleteButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        root.addView(space(14))

        resultList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(resultList)

        root.addView(space(22))
        root.addView(
            label(
                "PRIVACY FIRST • REVIEW BEFORE DELETING",
                11,
                muted,
                true
            ).apply { gravity = Gravity.CENTER }
        )

        root.addView(space(8))
        root.addView(
            label(
                "DEVELOPED BY SADA REGON",
                11,
                muted
            ).apply { gravity = Gravity.CENTER }
        )

        setContentView(scroll)
    }

    private fun beginScan() {
        if (scanning || deleting) return

        val missing = requiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 100)
            return
        }

        startScan()
    }

    private fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

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

        if (requestCode == 100) {
            if (requiredPermissions().any {
                    checkSelfPermission(it) ==
                        PackageManager.PERMISSION_GRANTED
                }) {
                startScan()
            } else {
                statusText.text = "PERMISSION NEEDED"
                progressText.text =
                    "Allow media access to scan your files."
                Toast.makeText(
                    this,
                    "No media permission granted",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startScan() {
        if (scanning || deleting) return

        scanning = true
        scanButton.isEnabled = false
        deleteButton.isEnabled = false
        scanButton.text = "SCANNING..."
        statusText.text = "SCAN IN PROGRESS"
        statusText.setTextColor(cyan)
        progress.progress = 0
        progressText.text = "Preparing storage scan..."
        summaryText.text = "Reading accessible media..."
        resultList.removeAllViews()
        items.clear()
        selectedUris.clear()

        executor.execute {
            val report = performScan()

            runOnUiThread {
                scanning = false
                scanButton.isEnabled = true
                scanButton.text = "SCAN AGAIN"
                progress.progress = 100
                progressText.text = "Scan completed"
                statusText.text = "SCAN COMPLETE"
                statusText.setTextColor(green)

                items.clear()
                items.addAll(report.files)

                val largeCount = items.count { it.large }
                val junkCount = items.count { it.junkCandidate }
                val duplicateCount = items.count {
                    it.possibleDuplicate
                }

                summaryText.text =
                    "Accessible files: ${items.size}\n" +
                    "Media size total: ${formatBytes(report.totalBytes)}\n" +
                    "Large files (100 MB+): $largeCount\n" +
                    "Junk-name candidates: $junkCount\n" +
                    "Possible duplicate photos: $duplicateCount\n" +
                    "Read errors: ${report.errors}"

                if (items.isEmpty()) {
                    resultList.addView(
                        label(
                            "No accessible media files found. Check permissions or device storage.",
                            14,
                            muted
                        )
                    )
                } else {
                    showResults()
                }

                Toast.makeText(
                    this,
                    "Scan finished: ${items.size} files checked",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun performScan(): ScanReport {
        val found = mutableListOf<ScanItem>()
        var errors = 0
        var totalBytes = 0L

        val sources = mutableListOf<Pair<Uri, String>>()

        if (Build.VERSION.SDK_INT >= 33) {
            if (hasPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
                sources.add(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "Photo"
                )
            }
            if (hasPermission(Manifest.permission.READ_MEDIA_VIDEO)) {
                sources.add(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "Video"
                )
            }
            if (hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
                sources.add(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "Audio"
                )
            }
        } else if (
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        ) {
            sources.add(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "Photo"
            )
            sources.add(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "Video"
            )
            sources.add(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "Audio"
            )
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )

        for ((sourceUri, category) in sources) {
            try {
                contentResolver.query(
                    sourceUri,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(
                        MediaStore.MediaColumns._ID
                    )
                    val nameIndex = cursor.getColumnIndex(
                        MediaStore.MediaColumns.DISPLAY_NAME
                    )
                    val sizeIndex = cursor.getColumnIndex(
                        MediaStore.MediaColumns.SIZE
                    )

                    while (cursor.moveToNext()) {
                        if (idIndex < 0) continue

                        val id = cursor.getLong(idIndex)
                        val name = if (nameIndex >= 0) {
                            cursor.getString(nameIndex) ?: "Unnamed file"
                        } else {
                            "Unnamed file"
                        }

                        val size = if (sizeIndex >= 0) {
                            cursor.getLong(sizeIndex).coerceAtLeast(0L)
                        } else {
                            0L
                        }

                        val uri = ContentUris.withAppendedId(
                            sourceUri,
                            id
                        )

                        totalBytes += size

                        found.add(
                            ScanItem(
                                uri = uri,
                                name = name,
                                size = size,
                                category = category,
                                junkCandidate = isJunkName(name),
                                large = size >= largeLimit,
                                possibleDuplicate = false
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                errors++
            } catch (_: Exception) {
                errors++
            }
        }

        // Same-size images are only possible duplicates, not confirmed ones.
        val imageSizeCounts = found
            .filter { it.category == "Photo" && it.size > 0L }
            .groupingBy { it.size }
            .eachCount()

        val marked = found.map { item ->
            item.copy(
                possibleDuplicate =
                    item.category == "Photo" &&
                    item.size > 0L &&
                    (imageSizeCounts[item.size] ?: 0) > 1
            )
        }

        runOnUiThread {
            progressText.text =
                "Checked ${marked.size} accessible media files..."
        }

        return ScanReport(
            files = marked,
            errors = errors,
            totalBytes = totalBytes
        )
    }

    private fun showResults() {
        resultList.removeAllViews()
        selectedUris.clear()

        val reviewItems = items.filter {
            it.large || it.junkCandidate || it.possibleDuplicate
        }

        val displayItems = reviewItems.distinctBy { it.uri.toString() }

        if (displayItems.isEmpty()) {
            resultList.addView(
                label(
                    "No review candidates found. Your accessible media was scanned.",
                    14,
                    green
                )
            )
            deleteButton.isEnabled = false
            return
        }

        displayItems.forEach { item ->
            val itemCard = makeCard()
            itemCard.addView(
                label(item.name, 14, white, true)
            )
            itemCard.addView(space(5))

            val tags = mutableListOf<String>()
            tags.add(item.category)
            tags.add(formatBytes(item.size))
            if (item.large) tags.add("Large")
            if (item.junkCandidate) tags.add("Possible junk")
            if (item.possibleDuplicate) {
                tags.add("Possible duplicate size")
            }

            itemCard.addView(
                label(tags.joinToString(" • "), 11, cyan)
            )
            itemCard.addView(space(6))

            val checkBox = CheckBox(this).apply {
                text = "Select for deletion"
                textSize = 13f
                setTextColor(white)

                setOnCheckedChangeListener { _, checked ->
                    val key = item.uri.toString()
                    if (checked) {
                        selectedUris.add(key)
                    } else {
                        selectedUris.remove(key)
                    }
                    deleteButton.isEnabled = selectedUris.isNotEmpty()
                }
            }

            itemCard.addView(checkBox)
            resultList.addView(itemCard)
            resultList.addView(space(8))
        }

        resultList.addView(
            label(
                "Only review candidates are listed here. " +
                    "A large file or matching file size does not prove that it is junk or a duplicate.",
                12,
                muted
            )
        )
    }

    private fun confirmDelete() {
        if (selectedUris.isEmpty() || deleting) return

        android.app.AlertDialog.Builder(this)
            .setTitle("Delete selected files?")
            .setMessage(
                "You selected ${selectedUris.size} file(s). " +
                    "Deleted files may not be recoverable. Continue only if you checked each item."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                deleteSelected()
            }
            .show()
    }

    private fun deleteSelected() {
        if (deleting || selectedUris.isEmpty()) return

        val selected = items.filter {
            selectedUris.contains(it.uri.toString())
        }

        if (selected.isEmpty()) return

        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val pendingIntent: PendingIntent =
                    MediaStore.createDeleteRequest(
                        contentResolver,
                        selected.map { it.uri }
                    )

                deleting = true
                startIntentSenderForResult(
                    pendingIntent.intentSender,
                    200,
                    null,
                    0,
                    0,
                    0
                )
            } catch (_: Exception) {
                deleting = false
                Toast.makeText(
                    this,
                    "Could not request system delete approval.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            // Older Android versions may reject deletion of media
            // that this app does not own.
            executor.execute {
                var deleted = 0
                var failed = 0

                for (item in selected) {
                    try {
                        if (
                            contentResolver.delete(
                                item.uri,
                                null,
                                null
                            ) > 0
                        ) {
                            deleted++
                        } else {
                            failed++
                        }
                    } catch (_: Exception) {
                        failed++
                    }
                }

                runOnUiThread {
                    selectedUris.clear()
                    Toast.makeText(
                        this,
                        "Deleted: $deleted • Could not delete: $failed",
                        Toast.LENGTH_LONG
                    ).show()
                    startScan()
                }
            }
        }
    }

    @Deprecated("Deprecated by Android; retained for Android 9+ compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 200) {
            deleting = false
            selectedUris.clear()

            Toast.makeText(
                this,
                if (resultCode == RESULT_OK) {
                    "System approved the delete request."
                } else {
                    "Delete request cancelled."
                },
                Toast.LENGTH_LONG
            ).show()

            startScan()
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun isJunkName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)

        return lower.endsWith(".tmp") ||
            lower.endsWith(".temp") ||
            lower.endsWith(".bak") ||
            lower.endsWith(".old") ||
            lower.endsWith(".log") ||
            lower.endsWith(".cache")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"

        val kb = bytes / 1024.0
        if (kb < 1024.0) return "%.1f KB".format(kb)

        val mb = kb / 1024.0
        if (mb < 1024.0) return "%.1f MB".format(mb)

        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(card, 18)
        }
    }

    private fun label(
        value: String,
        size: Int,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(height)
            )
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
