package com.adn.dev.climbcontest.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Les couleurs de l'application juge.
 *
 * Tirées des **six couleurs de circuit du club** — les mêmes que la page de
 * résultats et la console d'administration. Trois écrans, une seule identité.
 *
 * ⚠️ Ce ne sont pas des couleurs décoratives : dans cette application, la
 * couleur PORTE DE L'INFORMATION. Un juge lit la couleur d'une carte pour
 * vérifier qu'il est sur le bon circuit — ce que le tag seul (« ZJ1 ») ne dit
 * pas à quelqu'un qui ne connaît pas la convention de nommage par cœur.
 *
 * C'est aussi pour ça que le thème n'utilise pas les couleurs dynamiques
 * d'Android : elles reprennent le fond d'écran du téléphone, et l'application
 * aurait une apparence différente sur chacun des 25 appareils — sur certains,
 * le « vert » ne serait plus vert.
 */

// --- Les circuits, du plus facile au plus difficile -------------------------
//
// ⚠️ Retravaillées pour la refonte du 30/08. Les précédentes étaient
// **désaturées** — le « jaune » `#C8901F` tirait franchement sur le brun. Elles
// avaient été choisies pour rester lisibles en petits aplats de quelques
// millimètres ; dès qu'une couleur occupe une carte entière, elle peut et doit
// chanter.

val Jaune = Color(0xFFF5B72E)
val Vert = Color(0xFF34C56A)
val Bleu = Color(0xFF3E8CF7)
val Mauve = Color(0xFFA86CF0)
val Rouge = Color(0xFFF0554A)

/**
 * Le circuit « Noir ».
 *
 * Rendu en **craie** et non en noir : un aplat noir sur un fond presque noir ne
 * se voit pas, et le juge ne saurait pas s'il a scanné ou non. C'est la seule
 * couleur de circuit qui ne peut pas être elle-même à l'écran.
 */
val NoirCircuit = Color(0xFFE8EBF0)

/** Les six, dans l'ordre du club. */
val CIRCUITS = listOf(Jaune, Vert, Bleu, Mauve, Rouge, NoirCircuit)

/**
 * La couleur d'un circuit, telle que le serveur la nomme.
 *
 * Insensible à la casse et aux accents manquants — le classeur d'origine écrit
 * parfois « mauve », parfois « Mauve ». Rend `null` pour un nom inconnu, et
 * l'écran reste alors sur sa teinte neutre : un circuit dont on ne connaît pas
 * la couleur ne doit pas empêcher de valider une réussite.
 */
fun couleurDeCircuit(nom: String?): Color? = when (nom?.trim()?.lowercase()) {
    "jaune" -> Jaune
    "vert" -> Vert
    "bleu" -> Bleu
    "mauve", "violet" -> Mauve
    "rouge" -> Rouge
    "noir" -> NoirCircuit
    else -> null
}

/**
 * Du texte lisible sur n'importe quelle couleur de circuit.
 *
 * Le jaune et la craie demandent de l'encre sombre, le mauve et le rouge de
 * l'encre claire. Choisir à la main marcherait pour six couleurs, mais pas le
 * jour où le club en ajoute une : on mesure la luminance.
 */
fun encreSur(fond: Color): Color {
    val luminance = 0.2126f * fond.red + 0.7152f * fond.green + 0.0722f * fond.blue
    return if (luminance > 0.55f) Color(0xFF12140F) else Color(0xFFF7F9FC)
}

// --- L'écran ----------------------------------------------------------------
//
// Sombre : une salle d'escalade est mal éclairée, et un écran clair éblouit
// quand on lève les yeux vers le mur.

val Fond = Color(0xFF0B0D11)

/** Une carte remplie : légèrement plus claire, elle avance vers l'œil. */
val CarteFaite = Color(0xFF1B2432)

/** La carte de l'étape EN COURS. C'est elle que le juge doit voir en premier. */
val CarteActive = Color(0xFF161C26)

/** Une étape qui n'est pas encore la sienne. Elle s'efface. */
val CarteAttente = Color(0xFF101419)

val TraitFait = Color(0xFF2C3849)
val TraitActif = Color(0xFF3E4B5E)
val TraitAttente = Color(0xFF1A2029)

val Encre = Color(0xFFF2F5F9)
val Encre2 = Color(0xFF6B7688)

/** L'encre d'une étape en attente : présente, mais qui ne réclame rien. */
val Encre3 = Color(0xFF39424E)

/** Ce qui demande une action : une réussite refusée ne repartira pas seule. */
val Alerte = Rouge

/** Ce qui attend sans rien demander : des réussites encore en file. */
val Attention = Color(0xFFE5B44A)
