package com.myphotocalendar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.myphotocalendar.R

/** A font you can pick for the text over your photos. */
data class FontChoice(val id: String, val name: String, val family: FontFamily)

/** Fonts with several weights built in, so bold looks right. */
private fun variable(res: Int) = FontFamily(
    Font(res, FontWeight.Normal),
    Font(res, FontWeight.Medium),
    Font(res, FontWeight.Bold),
)

/** Fonts with a single weight; bold is drawn a little heavier by Android. */
private fun single(res: Int) = FontFamily(Font(res))

val FontChoices = listOf(
    FontChoice("system", "Standard", FontFamily.Default),
    FontChoice("serif", "Classic serif", FontFamily.Serif),
    FontChoice("montserrat", "Montserrat", variable(R.font.montserrat)),
    FontChoice("oswald", "Oswald", variable(R.font.oswald)),
    FontChoice("bebas", "Bebas Neue", single(R.font.bebas_neue)),
    FontChoice("comfortaa", "Comfortaa", variable(R.font.comfortaa)),
    FontChoice("playfair", "Playfair Display", variable(R.font.playfair_display)),
    FontChoice("lora", "Lora", variable(R.font.lora)),
    FontChoice("abril", "Abril Fatface", single(R.font.abril_fatface)),
    FontChoice("lobster", "Lobster", single(R.font.lobster)),
    FontChoice("pacifico", "Pacifico", single(R.font.pacifico)),
    FontChoice("dancing", "Dancing Script", variable(R.font.dancing_script)),
    FontChoice("caveat", "Caveat", variable(R.font.caveat)),
    FontChoice("greatvibes", "Great Vibes", single(R.font.great_vibes)),
)

fun fontFamilyFor(id: String): FontFamily =
    FontChoices.firstOrNull { it.id == id }?.family ?: FontFamily.Default

fun fontNameFor(id: String): String =
    FontChoices.firstOrNull { it.id == id }?.name ?: "Standard"

/** A ready-made combination of font, color, size and effects. */
data class StyleTemplate(
    val name: String,
    val fontId: String,
    val color: Color,
    val scale: Float,
    val bold: Boolean,
    val shadow: Boolean,
)

val StyleTemplates = listOf(
    StyleTemplate("Classic", "system", Color(0xFFFFFFFF), 1.0f, bold = true, shadow = true),
    StyleTemplate("Modern", "montserrat", Color(0xFFFFFFFF), 1.0f, bold = true, shadow = true),
    StyleTemplate("Poster", "bebas", Color(0xFFFFFFFF), 1.3f, bold = false, shadow = true),
    StyleTemplate("Elegant", "playfair", Color(0xFFFFF3D6), 1.05f, bold = false, shadow = true),
    StyleTemplate("Magazine", "abril", Color(0xFFFFFFFF), 1.1f, bold = false, shadow = true),
    StyleTemplate("Storybook", "lora", Color(0xFFFFF3D6), 1.05f, bold = true, shadow = true),
    StyleTemplate("Handwritten", "caveat", Color(0xFFFFFFFF), 1.3f, bold = true, shadow = true),
    StyleTemplate("Script", "dancing", Color(0xFFFFF3D6), 1.2f, bold = true, shadow = true),
    StyleTemplate("Retro", "lobster", Color(0xFFFFD54A), 1.1f, bold = false, shadow = true),
    StyleTemplate("Beach", "pacifico", Color(0xFFFFFFFF), 1.0f, bold = false, shadow = true),
    StyleTemplate("Soft", "comfortaa", Color(0xFFFFFFFF), 1.0f, bold = true, shadow = true),
    StyleTemplate("Romantic", "greatvibes", Color(0xFFFFB3C7), 1.35f, bold = false, shadow = true),
    StyleTemplate("Condensed", "oswald", Color(0xFFFFFFFF), 1.05f, bold = false, shadow = true),
    StyleTemplate("Light photo", "montserrat", Color(0xFF1B1B1F), 1.0f, bold = true, shadow = false),
)
