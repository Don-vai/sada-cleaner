package com.sadaregon.cleaner

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var filesValue: TextView
    private lateinit var largeValue: TextView
    private lateinit var candidateValue: TextView
    private lateinit var resultText: TextView

    private var scanning = false

    data class ScanResult(
        val scanned: Int,
        val largeFiles: Int,
        val candidates: Int,
        val candidateNames: List<String>,
        val errors: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

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

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = TextView(this).apply {
            text = "S"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(purple, 16)
        }

        brandRow.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))

        val brandInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        brandInfo.addView(label("SADA CLEANER", 21, white, true))
        brandInfo.addView(label("SMART DEVICE CARE", 10, cyan, true))

        brandRow.addView(
            brandInfo,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val version = label("V1.0", 11, muted, true).apply {
            background = rounded(card, 20)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        brandRow.addView(version)
        root.addView(brandRow)

        root.addView(space(30))
        root.addView(label("Your device.", 30, white, true))
        root.addView(label("Cleaner. Smarter. Safer.", 22, cyan, true))
        root.addView(space(8))
        root.addView(
            label(
                "Scan accessible files and find items worth reviewing.",
                14,
                muted
            )
        )

        root.addView(space(24))

        val overview = makeCard()

        overview.addView(label("DEVICE OVERVIEW", 12, muted, true))
        overview.addView(space(14))

        statusText = label("READY TO SCAN", 13, green, true)
        overview.addView(statusText)
        overview.addView(space(18))

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        filesValue = label("0", 25, white, true)
        largeValue = label("0", 25, cyan, true)
        candidateValue = label("0", 25, purple, true)

        stats.addView(
            statColumn("FILES SCANNED", filesValue),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        stats.addView(
            statColumn("LARGE FILES", largeValue),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        stats.addView(
            statColumn("REVIEW ITEMS", candidateValue),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        overview.addView(stats)
        root.addView(overview)

        root.addView(space(18))

        scanButton = Button(this).apply {
            text = "SCAN DEVICE"
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(purple, 16)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                beginScan()
            }
        }

        root.addView(
            scanButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )

        root.addView(space(14))

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
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(43, 49, 72)
                )
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

        root.addView(label("CLEANUP TOOLS", 13, muted, true))
        root.addView(space(12))

        root.addView(
            featureCard(
                "01",
                "Junk & Cache",
                "Review accessible temporary files and app cache."
            )
        )
        root.addView(space(10))

        root.addView(
            featureCard(
                "02",
                "Large Files",
                "Identify files of 100 MB or more."
            )
        )
        root.addView(space(10))

        root.addView(
            featureCard(
                "03",
                "Safe Cleanup",
                "Review results before deciding what to remove."
            )
        )

        root.addView(space(20))

        val resultCard = makeCard()
        resultCard.addView(label("SCAN REPORT", 13, cyan, true))
        resultCard.addView(space(10))

        resultText = label(
            "Your scan results will appear here.",
            14,
            muted
        )

        resultCard.addView(resultText)
        root.addView(resultCard)

        root.addView(space(28))

        root.addView(
            label(
                "PRIVACY FIRST  •  YOU STAY IN CONTROL",
                11,
                muted,
                true
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        root.addView(space(8))

        root.addView(
            label("DEVELOPED BY SADA REGON", 11, muted).apply {
                gravity = Gravity.CENTER
            }
        )

        setContentView(scroll)
    }

    private fun beginScan() {
        if (scanning) return

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
        return when {
            Build.VERSION.SDK_INT >= 33 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )

            else -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
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
            startScan()
        }
    }

    private fun startScan() {
        if (scanning) return

        scanning = true
        scanButton.isEnabled = false
        scanButton.text = "SCANNING..."
        statusText.text = "SCAN IN PROGRESS"
        statusText.setTextColor(cyan)
        progress.progress = 10
        progressText.text = "Checking accessible storage..."
        resultText.text = "Please wait while files are checked."

        filesValue.text = "..."
        largeValue.text = "..."
        candidateValue.text = "..."

        executor.execute {
            val result = performScan()

            runOnUiThread {
                scanning = false
                scanButton.isEnabled = true
                scanButton.text = "SCAN AGAIN"

                progress.progress = 100
                progressText.text = "Scan completed"
                statusText.text = "SCAN COMPLETE"
                statusText.setTextColor(green)

                filesValue.text = result.scanned.toString()
                largeValue.text = result.largeFiles.toString()
                candidateValue.text = result.candidates.toString()

                val report = StringBuilder()
                report.append(
                    "Scanned ${result.scanned} accessible files.\n\n"
                )
                report.append(
                    "Large files (100 MB+): ${result.largeFiles}\n"
                )
                report.append(
                    "Review candidates: ${result.candidates}\n"
                )

                if (result.errors > 0) {
                    report.append(
                        "\nSome locations could not be read: " +
                            result.errors
                    )
                }

                if (result.candidateNames.isNotEmpty()) {
                    report.append("\n\nITEMS TO REVIEW\n")
                    result.candidateNames.forEach {
                        report.append("\n• $it")
                    }
                } else {
                    report.append(
                        "\n\nNo temporary-file candidates were found " +
                            "in the scanned locations."
                    )
                }

                resultText.text = report.toString()

                Toast.makeText(
                    this,
                    "Scan completed: ${result.scanned} files checked",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun performScan(): ScanResult {
        var scanned = 0
        var large = 0
        var errors = 0

        val candidates = linkedSetOf<String>()
        val mediaSources = mutableListOf<android.net.Uri>()

        if (
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ||
            Build.VERSION.SDK_INT >= 33
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                if (hasPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
                    mediaSources.add(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    )
                }

                if (hasPermission(Manifest.permission.READ_MEDIA_VIDEO)) {
                    mediaSources.add(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    )
                }

                if (hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
                    mediaSources.add(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    )
                }
            } else {
                mediaSources.add(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                )
                mediaSources.add(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                )
                mediaSources.add(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                )
            }
        }

        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )

        for (uri in mediaSources) {
            try {
                val cursor: Cursor? = contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    null
                )

                cursor?.use {
                    val nameIndex = it.getColumnIndex(
                        MediaStore.MediaColumns.DISPLAY_NAME
                    )
                    val sizeIndex = it.getColumnIndex(
                        MediaStore.MediaColumns.SIZE
                    )

                    while (it.moveToNext()) {
                        scanned++

                        val name = if (nameIndex >= 0) {
                            it.getString(nameIndex) ?: "Unnamed file"
                        } else {
                            "Unnamed file"
                        }

                        val size = if (sizeIndex >= 0) {
                            it.getLong(sizeIndex).coerceAtLeast(0L)
                        } else {
                            0L
                        }

                        if (size >= 100L * 1024 * 1024) {
                            large++
                        }

                        if (
                            isReviewCandidate(name) &&
                            candidates.size < 100
                        ) {
                            candidates.add(name)
                        }
                    }
                }
            } catch (_: Exception) {
                errors++
            }
        }

        // Scan only this app's own cache.
        val appDirs = listOfNotNull(
            cacheDir,
            externalCacheDir
        )

        for (dir in appDirs) {
            try {
                if (!dir.exists()) continue

                dir.walkTopDown()
                    .onEnter { it.canRead() }
                    .filter { it.isFile && it.canRead() }
                    .forEach { file ->
                        scanned++

                        if (file.length() >= 100L * 1024 * 1024) {
                            large++
                        }

                        if (
                            isReviewCandidate(file.name) &&
                            candidates.size < 100
                        ) {
                            candidates.add(
                                "${file.name} (app cache)"
                            )
                        }
                    }
            } catch (_: Exception) {
                errors++
            }
        }

        return ScanResult(
            scanned = scanned,
            largeFiles = large,
            candidates = candidates.size,
            candidateNames = candidates.take(12).toList(),
            errors = errors
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun isReviewCandidate(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)

        return lower.endsWith(".tmp") ||
            lower.endsWith(".temp") ||
            lower.endsWith(".bak") ||
            lower.endsWith(".old") ||
            lower.endsWith(".log") ||
            lower.endsWith(".cache")
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(card, 20)
        }
    }

    private fun statColumn(
        title: String,
        value: TextView
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(value)
            addView(space(5))
            addView(label(title, 9, muted, true))
        }
    }

    private fun featureCard(
        number: String,
        title: String,
        description: String
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(card, 18)
        }

        val numberView = TextView(this).apply {
            text = number
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(
                Color.rgb(32, 42, 69),
                12
            )
        }

        row.addView(
            numberView,
            LinearLayout.LayoutParams(dp(44), dp(44))
        )

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }

        details.addView(label(title, 15, white, true))
        details.addView(space(5))
        details.addView(label(description, 12, muted))

        row.addView(
            details,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        return row
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
            if (bold) {
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
        }
    }

    private fun space(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(heightDp)
            )
        }
    }

    private fun rounded(
        color: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
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
