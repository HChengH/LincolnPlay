package com.shilapi.xcertplay

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.shilapi.xcertplay.host.R

/**
 * Full-screen crash report shown in the :crash process. The text is selectable so it can be
 * copied from the car screen and pasted into a message instead of being retyped by hand.
 */
class CrashReportActivity : Activity() {
    private lateinit var reportText: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reportText = intent.getStringExtra(EXTRA_REPORT) ?: "No crash details are available."
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildContentView())
    }

    private fun buildContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 17, 27))
            setPadding(40, 48, 40, 24)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.crash_report_title)
            textSize = 26f
            setTextColor(Color.rgb(241, 245, 252))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.crash_report_hint)
            textSize = 14f
            setTextColor(Color.rgb(168, 182, 202))
            setPadding(0, 20, 0, 20)
        })
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(22, 30, 44))
        }
        scroll.addView(TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(214, 226, 240))
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            text = reportText
            setPadding(28, 24, 28, 24)
        })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 12)
        }
        buttons.addView(button(getString(R.string.crash_report_copy)) { copyReport() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 16 })
        buttons.addView(button(getString(R.string.crash_report_restart), primary = true) { restartDiPlay() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 16 })
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = getString(R.string.crash_report_close_hint)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(120, 134, 156))
            setPadding(0, 8, 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    private fun button(title: String, primary: Boolean = false, click: () -> Unit): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 16f
            setTextColor(if (primary) Color.rgb(12, 17, 27) else Color.rgb(214, 226, 240))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setOnClickListener { click() }
        }

    private fun copyReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_report_title), reportText))
        Toast.makeText(this, getString(R.string.crash_report_copied), Toast.LENGTH_SHORT).show()
    }

    private fun restartDiPlay() {
        runCatching {
            startActivity(
                Intent().setClassName(packageName, "com.shilapi.xcertplay.DiPlayActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_REPORT = "report"
    }
}
