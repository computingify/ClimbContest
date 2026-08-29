package com.adn.dev.climbcontest.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Le style de texte par défaut.
 *
 * ⚠️ Il portait un `lineHeight = 24.sp` **fixe**. Comme les écrans donnent leur
 * taille au cas par cas (13 sp pour une explication, 34 sp pour « Envoyer »),
 * cette hauteur ne suivait pas : un texte de 13 sp était interligné à 24 sp,
 * soit près du double de ce qu'il faut. Les explications sous les réglages en
 * devenaient franchement aérées.
 *
 * Sans valeur explicite, Compose reprend l'interlignage naturel de la fonte,
 * qui est proportionnel à la taille — ce qu'on veut ici.
 */
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    )
)
