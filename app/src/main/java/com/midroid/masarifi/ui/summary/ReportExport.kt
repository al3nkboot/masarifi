package com.midroid.masarifi.ui.summary

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

/** Printable report: simple blocks drawn right-to-left with android.graphics (same output for PNG and PDF). */
sealed interface ReportLine {
    data class Title(val text: String) : ReportLine
    data class Header(val text: String) : ReportLine
    /** Table row; [cells] in reading order (first = right-most in RTL); [weights] relative widths. */
    data class Row(val cells: List<String>, val weights: List<Float>, val bold: Boolean = false, val color: Int? = null) : ReportLine
    data class Para(val text: String, val color: Int? = null) : ReportLine
    data class Bars(val items: List<Triple<String, Long, Long>>) : ReportLine
}

object ReportExport {
    private const val BG = 0xFF121212.toInt()
    private const val FG = 0xFFEDEDED.toInt()
    private const val MUTED = 0xFFA0A0A0.toInt()
    private const val ACCENT = 0xFFFF6B5B.toInt()
    const val GREEN = 0xFF4CAF50.toInt()
    const val RED = 0xFFEF5350.toInt()
    private const val INCOME = 0xFF4FA3FF.toInt()

    private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; isFakeBoldText = bold
    }

    private fun layout(text: String, p: TextPaint, width: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.coerceAtLeast(1))
            .setAlignment(align).setTextDirection(TextDirectionHeuristics.ANYRTL_LTR).setIncludePad(false).build()

    /** Height of [line] at content width [w] (scale s). */
    private fun measure(line: ReportLine, w: Int, s: Float): Int = when (line) {
        is ReportLine.Title -> layout(line.text, paint(26 * s, FG, true), w).height + (18 * s).toInt()
        is ReportLine.Header -> layout(line.text, paint(18 * s, ACCENT, true), w).height + (16 * s).toInt()
        is ReportLine.Para -> layout(line.text, paint(14 * s, line.color ?: FG), w).height + (8 * s).toInt()
        is ReportLine.Row -> {
            val tw = line.weights.sum()
            line.cells.indices.maxOf { i -> layout(line.cells[i], paint(13 * s, FG, line.bold), (w * line.weights[i] / tw).toInt() - (6 * s).toInt()).height } + (8 * s).toInt()
        }
        is ReportLine.Bars -> (150 * s).toInt()
    }

    /** Draws [line] with its top at [y] inside [left, left+w]; returns the height used. */
    private fun draw(c: Canvas, line: ReportLine, left: Float, y: Float, w: Int, s: Float): Int {
        fun at(l: StaticLayout, x: Float, yy: Float) { c.save(); c.translate(x, yy); l.draw(c); c.restore() }
        when (line) {
            is ReportLine.Title -> at(layout(line.text, paint(26 * s, FG, true), w), left, y)
            is ReportLine.Header -> at(layout(line.text, paint(18 * s, ACCENT, true), w), left, y + 8 * s)
            is ReportLine.Para -> at(layout(line.text, paint(14 * s, line.color ?: FG), w), left, y)
            is ReportLine.Row -> {
                val tw = line.weights.sum()
                var right = left + w
                line.cells.forEachIndexed { i, cell ->
                    val cw = (w * line.weights[i] / tw).toInt()
                    val col = if (i == line.cells.lastIndex) (line.color ?: FG) else FG
                    // first cell right-aligned text (RTL), numbers keep their LTR order
                    at(layout(cell, paint(13 * s, if (i == 0) FG else col, line.bold), cw - (6 * s).toInt()), right - cw, y)
                    right -= cw
                }
                val p = Paint().apply { color = 0x33FFFFFF; strokeWidth = 1f }
                c.drawLine(left, y + measure(line, w, s) - 3 * s, left + w, y + measure(line, w, s) - 3 * s, p)
            }
            is ReportLine.Bars -> {
                val h = 120 * s
                val max = (line.items.maxOfOrNull { maxOf(it.second, it.third) } ?: 1L).coerceAtLeast(1L).toFloat()
                val n = line.items.size.coerceAtLeast(1)
                val slot = w / n.toFloat()
                val bw = (slot / 4).coerceAtMost(28 * s)
                val lp = paint(11 * s, MUTED).apply { textAlign = Paint.Align.CENTER }
                // RTL: first month at the right
                line.items.forEachIndexed { i, (label, inc, exp) ->
                    val cx = left + w - slot * i - slot / 2
                    val pi = Paint().apply { color = INCOME }; val pe = Paint().apply { color = ACCENT }
                    c.drawRect(cx + 2 * s, y + h - h * inc / max, cx + 2 * s + bw, y + h, pi)
                    c.drawRect(cx - 2 * s - bw, y + h - h * exp / max, cx - 2 * s, y + h, pe)
                    c.drawText(label, cx, y + h + 18 * s, lp)
                }
            }
        }
        return measure(line, w, s)
    }

    fun renderBitmap(lines: List<ReportLine>, width: Int = 1080): Bitmap {
        val s = width / 540f
        val pad = (24 * s).toInt()
        val w = width - 2 * pad
        val h = pad * 2 + lines.sumOf { measure(it, w, s) }
        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)
        var y = pad.toFloat()
        for (l in lines) y += draw(c, l, pad.toFloat(), y, w, s)
        return bmp
    }

    /** A4 PDF, dark pages, paginated by blocks. */
    fun renderPdf(lines: List<ReportLine>, out: File) {
        val doc = PdfDocument()
        val pw = 595; val ph = 842; val pad = 32; val s = 0.95f
        val w = pw - 2 * pad
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
        page.canvas.drawColor(BG)
        var y = pad.toFloat()
        for (l in lines) {
            val h = measure(l, w, s)
            if (y + h > ph - pad && y > pad) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, ++pageNo).create())
                page.canvas.drawColor(BG)
                y = pad.toFloat()
            }
            y += draw(page.canvas, l, pad.toFloat(), y, w, s)
        }
        doc.finishPage(page)
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    fun shareFile(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun exportDir(context: Context) = File(context.cacheDir, "reports").apply { mkdirs() }

    @Suppress("unused") private val unusedColor = Color.WHITE
}
