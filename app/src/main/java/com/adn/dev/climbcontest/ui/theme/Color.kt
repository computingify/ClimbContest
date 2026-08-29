package com.adn.dev.climbcontest.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Les couleurs de l'application juge.
 *
 * Tirées des **six couleurs de circuit du club** — les mêmes que la page de
 * résultats et la console d'administration. Trois écrans, une seule identité.
 *
 * ⚠️ Ce ne sont pas des couleurs décoratives : dans cette application, la
 * couleur PORTE DE L'INFORMATION. Un bouton vert veut dire « scanné », gris
 * veut dire « pas encore ». C'est pour ça que le thème n'utilise pas les
 * couleurs dynamiques d'Android : elles reprennent le fond d'écran du
 * téléphone, et l'application aurait une apparence différente sur chacun des
 * 25 appareils — sur certains, le « vert » ne serait plus vert.
 */

// Les circuits, du plus facile au plus difficile.
val Jaune = Color(0xFFC8901F)
val Vert = Color(0xFF3F8450)
val Bleu = Color(0xFF2F6BB0)
val Mauve = Color(0xFF7A4F99)
val Rouge = Color(0xFFB33F33)

// L'écran. Sombre : une salle d'escalade est mal éclairée, et un écran clair
// éblouit quand on lève les yeux vers le mur.
val Fond = Color(0xFF0E1116)
val Carte = Color(0xFF171B23)
val Carte2 = Color(0xFF1E232D)
val Trait = Color(0xFF2B313D)
val Encre = Color(0xFFEEF1F5)
val Encre2 = Color(0xFF9AA4B4)

// Les états. Vert et gris pour « fait / pas fait » — jamais vert et rouge :
// environ 8 % des hommes distinguent mal ces deux-là, et il y a des juges
// hommes. Le rouge est réservé à ce qui demande une action.
val EtatFait = Color(0xFF4E9A5A)
val EtatVide = Color(0xFF39404E)
val Alerte = Color(0xFFE0705F)
val Attention = Color(0xFFE5B44A)
