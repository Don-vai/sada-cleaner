package com.sadaregon.cleaner

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(10, 13, 28)
    private val cardColor = Color.rgb(24, 29, 51)
    private val accentColor = Color.rgb(139, 92, 246)
    private val textColor = Color.rgb(245, 247, 255)
    private val mutedColor = Color.rgb(169, 177, 201)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(backgroundColor)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            addView(root)
        }

        root.addView(label("SADA CLEANER", 28, textColor, true))
        root.addView(label("Smart cleaning. Simple control.", 14, mutedColor))
        root.addView(space(28))

        val storageCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            setBackgroundColor(cardColor)
        }

        storageCard.addView(label("DEVICE OVERVIEW", 12, mutedColor, true))
        storageCard.addView(space(12))
        storageCard.addView(label("Your device, under control", 20, textColor, true))
        storageCard.addView(space(8))
        storageCard.addView(
            label(
                "Run a scan to check the files this app can safely inspect.",
                14,
                mutedColor
            )
        )
        root.addView(storageCard)
        root.addView(space(18))

        val scanButton = Button(this).apply {
            text = "SCAN DEVICE"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(accentColor)
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Step 1 complete: scanner will be added next.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        root.addView(
            scanButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )
        root.addView(space(24))

        addFeature(root, "Junk & Temporary Files", "Scan accessible app cache and temporary data")
        addFeature(root, "Large Files", "Find large files you choose to review")
        addFeature(root, "Safe Cleanup", "Review items before requesting deletion")
        addFeature(root, "Privacy First", "No storage access requested in this starter build")

        root.addView(space(28))
        root.addView(
            label(
                "DEVELOPED BY SADA REGON",
                12,
                mutedColor,
                true
            ).apply { gravity = Gravity.CENTER }
        )

        setContentView(scroll)
    }

    private fun addFeature(parent: LinearLayout, title: String, description: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(cardColor)
        }

        card.addView(label(title, 16, textColor, true))
        card.addView(space(6))
        card.addView(label(description, 13, mutedColor))

        parent.addView(card)
        parent.addView(space(12))
    }

    private fun label(
        value: String,
        size: Int,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun space(heightDp: Int) = TextView(this).apply {
        height = dp(heightDp)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
