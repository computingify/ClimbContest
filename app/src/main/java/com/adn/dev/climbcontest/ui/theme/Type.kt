package com.adn.dev.climbcontest.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.adn.dev.climbcontest.R

/**
 * La typographie de l'application juge : **Archivo**, sous licence OFL.
 *
 * ⚠️ C'est le plus gros levier sur la qualité perçue, et le seul que la police
 * système ne pouvait pas donner. Ce que le juge lit — un nom de grimpeur, un
 * dossard, un tag de bloc — se lit **à un mètre, en trois dixièmes de seconde**,
 * dans une salle mal éclairée. Il fallait :
 *
 * - des graisses lourdes qui tiennent à 30 sp sans baver ;
 * - des chiffres à chasse fixe, pour qu'une colonne d'heures s'aligne ;
 * - une largeur réglable, pour resserrer un nom long sans le rétrécir.
 *
 * C'est une **variable font** : un seul fichier porte toutes les graisses, au
 * lieu des six qu'il aurait fallu embarquer séparément.
 *
 * Licence complète dans `res/raw/archivo_licence.txt`, versée avec la police
 * comme l'OFL le demande.
 */
@OptIn(ExperimentalTextApi::class)
private fun archivo(poids: Int, largeur: Int = 100) = FontFamily(
    Font(
        R.font.archivo,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(poids),
            FontVariation.width(largeur.toFloat()),
        ),
    ),
)

/** Ce qui doit sauter aux yeux : un nom, un tag, « ENVOYER ». */
val Titre = archivo(800)

/** Les étiquettes en capitales, les tags dans le journal. */
val Fort = archivo(700)

/** Le corps de texte qui compte : une consigne, un nom dans une liste. */
val Moyen = archivo(500)

/** Ce qui accompagne : une heure, une mention secondaire. */
val Normal = archivo(400)

/**
 * Le style par défaut.
 *
 * ⚠️ Il portait autrefois un `lineHeight = 24.sp` **fixe**. Comme les écrans
 * donnent leur taille au cas par cas — 13 sp pour une explication, 30 sp pour
 * « ENVOYER » —, cette hauteur ne suivait pas : un texte de 13 sp était
 * interligné à 24 sp, soit près du double de ce qu'il faut.
 *
 * Sans valeur explicite, Compose reprend l'interlignage naturel de la fonte,
 * qui est proportionnel à la taille.
 */
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Normal,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    )
)
