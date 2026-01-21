package com.github.inxilpro.chronicle.toolWindow

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JPanel

class AudioWaveformPanel(
    private val silenceThreshold: Float
) : JPanel() {

    companion object {
        private const val MAX_SAMPLES = 200
        private const val BAR_WIDTH = 3
        private const val BAR_GAP = 1
    }

    private val samples = CopyOnWriteArrayList<SampleData>()

    data class SampleData(
        val rms: Float,
        val isSilence: Boolean,
        val silenceDurationMs: Long
    )

    init {
        preferredSize = Dimension(JBUI.scale(300), JBUI.scale(60))
        minimumSize = Dimension(JBUI.scale(100), JBUI.scale(40))
        background = JBColor.PanelBackground
        border = JBUI.Borders.customLine(JBColor.border(), 1)
    }

    fun addSample(rms: Float, isSilence: Boolean, silenceDurationMs: Long) {
        samples.add(SampleData(rms, isSilence, silenceDurationMs))
        while (samples.size > MAX_SAMPLES) {
            samples.removeAt(0)
        }
        repaint()
    }

    fun clear() {
        samples.clear()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val w = width
        val h = height
        val padding = JBUI.scale(4)
        val drawHeight = h - padding * 2

        // Draw background
        g2.color = JBColor.PanelBackground
        g2.fillRect(0, 0, w, h)

        // Calculate visible samples based on panel width
        val barTotalWidth = JBUI.scale(BAR_WIDTH + BAR_GAP)
        val maxVisibleSamples = (w - padding * 2) / barTotalWidth

        val visibleSamples = samples.takeLast(maxVisibleSamples)
        if (visibleSamples.isEmpty()) {
            drawEmptyState(g2, w, h)
            return
        }

        // Draw silence threshold line
        val thresholdY = h - padding - (silenceThreshold * drawHeight * 4).toInt().coerceIn(0, drawHeight)
        g2.color = JBColor(Color(255, 100, 100, 100), Color(255, 100, 100, 100))
        g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(4f), 0f)
        g2.drawLine(padding, thresholdY, w - padding, thresholdY)

        // Draw threshold label
        g2.font = JBUI.Fonts.smallFont()
        g2.color = JBColor(Color(255, 100, 100), Color(255, 150, 150))
        val thresholdLabel = "threshold"
        val labelWidth = g2.fontMetrics.stringWidth(thresholdLabel)
        g2.drawString(thresholdLabel, w - padding - labelWidth, thresholdY - JBUI.scale(2))

        // Draw waveform bars from right to left (newest on right)
        var x = w - padding - JBUI.scale(BAR_WIDTH)
        for (i in visibleSamples.indices.reversed()) {
            val sample = visibleSamples[i]
            val barHeight = (sample.rms * drawHeight * 4).toInt().coerceIn(JBUI.scale(2), drawHeight)

            // Color based on silence state
            g2.color = when {
                sample.silenceDurationMs >= 1500 -> JBColor(Color(100, 200, 100), Color(100, 200, 100))
                sample.isSilence -> JBColor(Color(200, 200, 100), Color(200, 200, 100))
                else -> JBColor(Color(100, 150, 255), Color(100, 150, 255))
            }

            val barY = h - padding - barHeight
            g2.fillRoundRect(x, barY, JBUI.scale(BAR_WIDTH), barHeight, JBUI.scale(2), JBUI.scale(2))

            x -= barTotalWidth
            if (x < padding) break
        }

        // Draw current state info
        val lastSample = visibleSamples.lastOrNull()
        if (lastSample != null) {
            drawStateInfo(g2, lastSample, padding)
        }
    }

    private fun drawEmptyState(g2: Graphics2D, w: Int, h: Int) {
        g2.color = JBColor.GRAY
        g2.font = JBUI.Fonts.smallFont()
        val text = "Waiting for audio..."
        val textWidth = g2.fontMetrics.stringWidth(text)
        g2.drawString(text, (w - textWidth) / 2, h / 2)
    }

    private fun drawStateInfo(g2: Graphics2D, sample: SampleData, padding: Int) {
        g2.font = JBUI.Fonts.smallFont()
        val rmsText = "RMS: %.4f".format(sample.rms)
        val silenceText = if (sample.isSilence) {
            "Silence: ${sample.silenceDurationMs}ms"
        } else {
            "Speaking"
        }

        g2.color = JBColor.GRAY
        g2.drawString(rmsText, padding, padding + g2.fontMetrics.ascent)

        g2.color = when {
            sample.silenceDurationMs >= 1500 -> JBColor(Color(100, 200, 100), Color(100, 200, 100))
            sample.isSilence -> JBColor(Color(200, 200, 100), Color(200, 200, 100))
            else -> JBColor(Color(100, 150, 255), Color(100, 150, 255))
        }
        g2.drawString(silenceText, padding, padding + g2.fontMetrics.ascent * 2 + JBUI.scale(2))
    }
}
