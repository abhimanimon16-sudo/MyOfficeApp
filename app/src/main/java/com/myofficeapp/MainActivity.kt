package com.myofficeapp

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WorkRecord(val date: String, val start: String, val end: String, val minutes: Int)

class MainActivity : Activity() {
private val records = mutableListOf<WorkRecord>()
private lateinit var dateText: TextView
private lateinit var startText: TextView
private lateinit var endText: TextView
private lateinit var list: LinearLayout
private var selectedDate = ""
private var startHour = -1
private var startMinute = -1
private var endHour = -1
private var endMinute = -1

private val store by lazy {
    getSharedPreferences("office_records", Context.MODE_PRIVATE)
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    loadRecords()
    showScreen()
}

private fun showScreen() {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 24, 24, 24)
        setBackgroundColor(Color.rgb(245, 247, 250))
    }
    val scroll = ScrollView(this)
    scroll.addView(root)
    setContentView(scroll)

    root.addView(label("MyOfficeApp", 28, true))
    root.addView(label("Office Time Tracker", 16, false))

    dateText = label("", 18, true)
    root.addView(dateText)
    button(root, "Choose Date") { chooseDate() }

    startText = label("Start time: Not selected", 16, false)
    root.addView(startText)
    button(root, "Select Start Time") {
        chooseTime(true)
    }

    endText = label("End time: Not selected", 16, false)
    root.addView(endText)
    button(root, "Select End Time") {
        chooseTime(false)
    }

    button(root, "Save Record") { saveRecord() }
    button(root, "Monthly Summary") { showMonthlySummary() }
    button(root, "Share Daily Report") { shareReport(false) }
    button(root, "Share Monthly Report") { shareReport(true) }

    root.addView(label("Saved Records", 21, true))
    list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(list)
    refreshScreen()
}

private fun label(text: String, size: Int, bold: Boolean): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(Color.rgb(30, 45, 65))
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 12, 0, 12)
    }

private fun button(parent: LinearLayout, text: String, action: () -> Unit) {
    val b = Button(this)
    b.text = text
    b.setOnClickListener { action() }
    parent.addView(b, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}

private fun chooseDate() {
    val c = Calendar.getInstance()
    try {
        val parts = selectedDate.split("-")
        c.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    } catch (_: Exception) { }
    DatePickerDialog(this, { _, y, m, d ->
        selectedDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
        refreshScreen()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

private fun chooseTime(isStart: Boolean) {
    val c = Calendar.getInstance()
    val h = if (isStart && startHour >= 0) startHour else c.get(Calendar.HOUR_OF_DAY)
    val m = if (isStart && startMinute >= 0) startMinute else c.get(Calendar.MINUTE)
    val initialH = if (!isStart && endHour >= 0) endHour else h
    val initialM = if (!isStart && endMinute >= 0) endMinute else m

    TimePickerDialog(this, { _, hour, minute ->
        if (isStart) {
            startHour = hour
            startMinute = minute
        } else {
            endHour = hour
            endMinute = minute
        }
        refreshScreen()
    }, initialH, initialM, true).show()
}

private fun saveRecord() {
    if (startHour < 0 || endHour < 0) {
        toast("Please select both start and end times.")
        return
    }
    val start = startHour * 60 + startMinute
    val end = endHour * 60 + endMinute
    if (end <= start) {
        toast("End time must be later than start time.")
        return
    }
    records.removeAll { it.date == selectedDate }
    records.add(WorkRecord(selectedDate, fmt(startHour, startMinute), fmt(endHour, endMinute), end - start))
    persistRecords()
    refreshScreen()
    toast("Record saved.")
}

private fun fmt(h: Int, m: Int) = String.format(Locale.US, "%02d:%02d", h, m)

private fun persistRecords() {
    val encoded = records.joinToString("\n") {
        "${it.date},${it.start},${it.end},${it.minutes}"
    }
    store.edit().putString("records", encoded).apply()
}

private fun loadRecords() {
    records.clear()
    store.getString("records", "")?.lines()?.forEach { line ->
        val p = line.split(",")
        if (p.size == 4) {
            try {
                records.add(WorkRecord(p[0], p[1], p[2], p[3].toInt()))
            } catch (_: Exception) { }
        }
    }
}

private fun refreshScreen() {
    dateText.text = "Date: $selectedDate"
    startText.text = "Start time: " +
        if (startHour >= 0) fmt(startHour, startMinute) else "Not selected"
    endText.text = "End time: " +
        if (endHour >= 0) fmt(endHour, endMinute) else "Not selected"

    if (!::list.isInitialized) return
    list.removeAllViews()
    records.sortedByDescending { it.date }.forEach { r ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        row.addView(label(
            "${r.date} | ${r.start} - ${r.end} | ${r.minutes / 60}h ${r.minutes % 60}m",
            15, false
        ))
        button(row, "Delete") {
            records.remove(r)
            persistRecords()
            refreshScreen()
        }
        list.addView(row)
    }
}

private fun showMonthlySummary() {
    val month = selectedDate.substring(0, 7)
    val monthly = records.filter { it.date.startsWith(month) }
    val total = monthly.sumOf { it.minutes }
    val msg = "Month: $month\nDays recorded: ${monthly.size}\nTotal hours: ${total / 60}h ${total % 60}m"
    android.app.AlertDialog.Builder(this)
        .setTitle("Monthly Summary")
        .setMessage(msg)
        .setPositiveButton("OK", null)
        .show()
}

private fun shareReport(monthly: Boolean) {
    val month = selectedDate.substring(0, 7)
    val chosen = records.filter {
        if (monthly) it.date.startsWith(month) else it.date == selectedDate
    }
    if (chosen.isEmpty()) {
        toast("No records to share.")
        return
    }
    val total = chosen.sumOf { it.minutes }
    val heading = if (monthly) "Monthly Report: $month" else "Daily Report: $selectedDate"
    val body = buildString {
        appendLine(heading)
        chosen.sortedBy { it.date }.forEach {
            appendLine("${it.date}: ${it.start} - ${it.end} (${it.minutes / 60}h ${it.minutes % 60}m)")
        }
        appendLine("Total: ${total / 60}h ${total % 60}m")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, heading)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    startActivity(Intent.createChooser(intent, "Share report"))
}

private fun toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

}
