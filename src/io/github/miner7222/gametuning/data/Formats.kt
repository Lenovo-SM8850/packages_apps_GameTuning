// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import androidx.core.text.util.LocalePreferences
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Temperature values are stored in degrees Celsius and shown in the user's unit. */
class TemperatureFormat(context: Context) {
    val fahrenheit: Boolean =
        LocalePreferences.getTemperatureUnit() == LocalePreferences.TemperatureUnit.FAHRENHEIT
    private val unit = if (fahrenheit) MeasureUnit.FAHRENHEIT else MeasureUnit.CELSIUS
    private val locale = ULocale.forLocale(Locale.getDefault())
    private val numbers = NumberFormat.getInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    private val measure = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.NARROW, numbers)

    /** Converts an absolute temperature. */
    fun convert(celsius: Double): Double = if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius

    /** Converts a temperature difference. */
    fun convertDelta(celsius: Double): Double = if (fahrenheit) celsius * 9.0 / 5.0 else celsius

    fun absolute(celsius: Double): String = measure.format(Measure(convert(celsius), unit))
    fun absoluteMilli(milliC: Int): String = absolute(milliC / 1000.0)
    fun delta(celsius: Double): String = measure.format(Measure(convertDelta(celsius), unit))

    /** A number in the user's unit with the unit symbol on a separate call, for slider ends. */
    fun absoluteRounded(celsius: Double): String =
        measure.format(Measure(convert(celsius).roundToInt(), unit))
}

object Freq {
    private val locale = Locale.getDefault()

    /** CPU frequency in kHz as gigahertz text without a unit, for example 1.44. */
    fun ghzNumber(kHz: Int): String = String.format(locale, "%.2f", kHz / 1_000_000.0)

    fun ghz(kHz: Int): String = ghzNumber(kHz) + " GHz"

    /** GPU frequency in Hz as megahertz text with grouping, for example 1,000. */
    fun mhzNumber(hz: Int): String =
        java.text.NumberFormat.getIntegerInstance(locale).format((hz / 1_000_000.0).roundToInt())

    fun mhz(hz: Int): String = mhzNumber(hz) + " MHz"

    fun cpuRange(minKHz: Int, maxKHz: Int): String =
        if (minKHz == maxKHz) ghz(maxKHz) else ghzNumber(minKHz) + "–" + ghz(maxKHz)

    fun gpuRange(minHz: Int, maxHz: Int): String =
        if (minHz == maxHz) mhz(maxHz) else mhzNumber(minHz) + "–" + mhz(maxHz)

    fun range(resource: Int, min: Int, max: Int): String =
        if (resource == Resource.GPU) gpuRange(min, max) else cpuRange(min, max)

    fun one(resource: Int, value: Int): String =
        if (resource == Resource.GPU) mhz(value) else ghz(value)
}

object Resource {
    const val GOLD = 0
    const val PRIME = 1
    const val GPU = 2
}

fun formatSeconds(ms: Int): String =
    String.format(Locale.getDefault(), "%.1f s", ms / 1000.0)

fun formatTenth(value: Double): String =
    String.format(Locale.getDefault(), "%.1f", value)

fun formatPower(milliWatt: Int): String =
    String.format(Locale.getDefault(), "%.1f W", milliWatt / 1000.0)

fun formatFloat1(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)

fun formatScale(v: Float): String = String.format(Locale.getDefault(), "%.2f×", v)

fun sameFloat(a: Float, b: Float) = abs(a - b) < 0.0001f
