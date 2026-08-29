package com.adn.dev.climbcontest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Un thème FIXE, identique sur tous les téléphones.
 *
 * La version précédente activait `dynamicColor` : Android reprend alors les
 * couleurs du fond d'écran de l'appareil. Sur 25 téléphones de bénévoles, ça
 * donne 25 apparences différentes — et surtout, la couleur qui signale « ce
 * grimpeur est scanné » n'est plus la même d'un juge à l'autre.
 *
 * Elle suivait aussi le mode clair/sombre du système. Ici, l'écran est
 * toujours sombre : une salle d'escalade est mal éclairée, et un écran clair
 * éblouit quand on lève les yeux vers le mur.
 */
private val Schema = darkColorScheme(
    primary = Bleu,
    onPrimary = Encre,
    secondary = Mauve,
    background = Fond,
    onBackground = Encre,
    surface = Carte,
    onSurface = Encre,
    surfaceVariant = Carte2,
    onSurfaceVariant = Encre2,
    outline = Trait,
    error = Alerte,
    onError = Fond,
)

@Composable
fun ClimbContestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Schema, typography = Typography, content = content)
}
