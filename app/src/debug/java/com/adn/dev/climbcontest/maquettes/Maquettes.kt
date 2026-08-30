package com.adn.dev.climbcontest.maquettes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adn.dev.climbcontest.R

/**
 * Trois directions visuelles pour l'ecran du juge, cote a cote.
 *
 * ⚠️ **Code jetable, cantonne au source set `debug`.** Il n'entre dans aucun
 * APK de production, et disparaitra une fois la direction choisie. Il existe
 * pour une seule raison : Adrien a demande a VOIR les propositions plutot qu'a
 * les lire. Une maquette dessinee dans le vrai moteur de rendu ne peut pas
 * promettre ce que Compose ne saurait pas faire.
 *
 * Se lance avec :
 *
 *     adb shell am start -n com.adn.dev.climbcontest/.maquettes.MaquettesActivity \
 *       --es variante mur --ez rempli true
 */
class MaquettesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val variante = intent.getStringExtra("variante") ?: "mur"
        val rempli = intent.getBooleanExtra("rempli", true)
        // « vide », « grimpeur » (le grimpeur est scanne, pas le bloc), « rempli »
        val etape = intent.getStringExtra("etape") ?: if (rempli) "rempli" else "vide"
        val logo = intent.getBooleanExtra("logo", false)
        // « ok », « doute » ou « ko »
        val connexion = intent.getStringExtra("connexion") ?: "ok"

        setContent {
            when (variante) {
                "craie" -> MaquetteCraie(rempli)
                "relief" -> MaquetteRelief(rempli)
                else -> MaquetteMur(etape, logo, connexion)
            }
        }
    }
}

// --- Ce que les trois maquettes affichent -----------------------------------

private const val NOM = "Camille Bertrand"
private const val DOSSARD = "42"
private const val CATEGORIE = "U15 F"
private const val TAG_BLOC = "ZJ1"
private const val ZONE = "Zone jaune"
private const val EN_ATTENTE = 3

/**
 * Les dernieres validations.
 *
 * Elles ne sont pas la pour remplir : c'est la reponse a « est-ce que j'ai bien
 * envoye ? », et c'est ce qui empeche l'ecran d'etre un grand vide entre deux
 * scans. Un ecran d'application qui passe sa journee a moitie noir n'a pas
 * l'air fini, quelle que soit la qualite du reste.
 */
private data class Validation(
    val heure: String, val nom: String, val tag: String, val couleur: Color,
)

/**
 * Les six couleurs de circuit, retravaillees.
 *
 * Celles d'aujourd'hui sont **desaturees** — le « jaune » `#C8901F` tire sur le
 * brun. Elles avaient ete choisies pour rester lisibles en petits aplats ; des
 * qu'une couleur occupe une carte entiere, elle peut et doit chanter.
 */
private val Jaune = Color(0xFFF5B72E)
private val Vert = Color(0xFF34C56A)
private val Bleu = Color(0xFF3E8CF7)
private val Mauve = Color(0xFFA86CF0)
private val Rouge = Color(0xFFF0554A)
private val Craie = Color(0xFFE8EBF0)
private val CIRCUITS = listOf(Jaune, Vert, Bleu, Mauve, Rouge, Craie)

private val JOURNAL = listOf(
    Validation("10:42", "Noé Mercier", "ZB7", Bleu),
    Validation("10:41", "Lou Marchand", "ZV3", Vert),
    Validation("10:39", "Elsa Lecomte", "ZM9", Mauve),
    Validation("10:36", "Tom Marchand", "ZR2", Rouge),
)

/** La couleur du bloc scanne dans ces maquettes. */
private val COULEUR = Jaune

/**
 * Du texte lisible sur n'importe quelle couleur de circuit.
 *
 * Le jaune et le blanc craie demandent du texte sombre, le mauve et le rouge du
 * texte clair. Choisir a la main marcherait pour six couleurs, mais pas le jour
 * ou le club en ajoute une : on mesure la luminance.
 */
private fun encreSur(fond: Color): Color {
    val luminance = 0.2126f * fond.red + 0.7152f * fond.green + 0.0722f * fond.blue
    return if (luminance > 0.55f) Color(0xFF12140F) else Color(0xFFF7F9FC)
}

// --- La typographie ---------------------------------------------------------

/**
 * Archivo, une variable font sous licence OFL.
 *
 * ⚠️ C'est le plus gros levier sur la qualite percue, et le seul que la police
 * systeme ne peut pas donner : des chiffres a chasse fixe, des graisses lourdes
 * qui tiennent, et une largeur reglable. Un dossard et un tag de bloc sont
 * lus a un metre, en trois dixiemes de seconde.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun archivo(poids: Int, largeur: Int = 100) = FontFamily(
    Font(
        R.font.archivo,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(poids),
            FontVariation.width(largeur.toFloat()),
        ),
    ),
)

private val Titre = archivo(800)
private val Fort = archivo(700)
private val Moyen = archivo(500)
private val Normal = archivo(400)

// ═══════════════════════════════════════════════════════════════════════════
//  A — « Le mur » : la couleur du circuit prend l'ecran
// ═══════════════════════════════════════════════════════════════════════════

private val MurFond = Color(0xFF0B0D11)
private val MurSurface = Color(0xFF151A22)
private val MurTrait = Color(0xFF232A36)
private val MurEncre = Color(0xFFF2F5F9)
private val MurEncre2 = Color(0xFF6B7688)

/**
 * A — « Le mur », direction retenue par Adrien.
 *
 * @param etape  « vide », « grimpeur » ou « rempli »
 * @param logo   le logo du club dans l'en-tete, au lieu des six pastilles
 */
@Composable
fun MaquetteMur(etape: String, logo: Boolean = false, connexion: String = "ok") {
    val grimpeurFait = etape != "vide"
    val blocFait = etape == "rempli"

    Column(
        Modifier
            .fillMaxSize()
            .background(MurFond)
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(54.dp))

        // Le logo est A DROITE, et il tient lieu de bouton de menu : c'est le
        // seul point d'entree vers les reglages, les scans, la file. Un endroit
        // unique ou aller chercher « tout le reste », plutot qu'un engrenage
        // aujourd'hui et trois icones l'annee prochaine.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ANNONAY",
                fontFamily = Fort, fontSize = 13.sp,
                letterSpacing = 2.4.sp, color = MurEncre2,
            )
            Spacer(Modifier.weight(1f))

            // ⚠️ La file est DANS l'en-tete, et pas au-dessus des cartes.
            //
            // Posee en bandeau, elle poussait tout l'ecran vers le bas des
            // qu'une reussite attendait — et remontait tout des qu'elle
            // repartait. La mise en page bougeait sous le pouce du juge pendant
            // qu'il scannait. Ici, elle apparait et disparait sans rien deplacer.
            //
            // Elle est CLIQUABLE : un appui force l'envoi immediat. C'est le
            // geste de fin de competition, avant d'eteindre les telephones.
            if (blocFait) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0x22E5B44A), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Icon(
                        Icons.Filled.ArrowUpward, contentDescription = null,
                        tint = Color(0xFFE5B44A), modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "3 en attente",
                        fontFamily = Moyen, fontSize = 12.sp, letterSpacing = 0.3.sp,
                        color = Color(0xFFE5B44A),
                    )
                }
                Spacer(Modifier.width(10.dp))
            }

            Voyant(connexion)
            Spacer(Modifier.width(18.dp))
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.logo_rond),
                    contentDescription = "Menu",
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                )
            }
        }

        // La meme hauteur dans TOUS les etats : rien ne bouge sous le pouce.
        Spacer(Modifier.height(30.dp))

        // --- 1 · Le grimpeur -------------------------------------------------
        CarteMur(
            numero = "1",
            etiquette = "GRIMPEUR",
            fait = grimpeurFait,
            // « Active » = c'est ELLE qu'il faut scanner maintenant. Au repos,
            // les deux cartes se ressemblaient et rien ne disait par ou
            // commencer : l'ecran etait lisible mais muet.
            active = !grimpeurFait,
        ) {
            if (grimpeurFait) {
                Text(NOM, fontFamily = Titre, fontSize = 30.sp,
                     letterSpacing = (-0.6).sp, color = MurEncre)
                Spacer(Modifier.height(5.dp))
                Text("N° $DOSSARD  ·  $CATEGORIE",
                     fontFamily = Moyen, fontSize = 14.sp,
                     letterSpacing = 0.8.sp, color = MurEncre2)
            }
        }

        Spacer(Modifier.height(14.dp))

        // --- 2 · Le bloc, qui porte la couleur du circuit ---------------------
        CarteMur(
            numero = "2",
            etiquette = "BLOC",
            fait = blocFait,
            active = grimpeurFait && !blocFait,
            teinte = if (blocFait) COULEUR else null,
        ) {
            if (blocFait) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        TAG_BLOC,
                        fontFamily = Titre, fontSize = 34.sp,
                        letterSpacing = (-0.5).sp, color = encreSur(COULEUR),
                        modifier = Modifier
                            .background(COULEUR, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(ZONE, fontFamily = Moyen, fontSize = 17.sp,
                         color = COULEUR.copy(alpha = 0.9f))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // --- 3 · Envoyer -----------------------------------------------------
        //
        // La MEME logique que les deux cartes, et c'est ce qui fait tenir
        // l'ecran : trois etapes numerotees, une seule active a la fois. Sans
        // le 3, le bouton flottait a part et la sequence s'arretait a deux.
        // ⚠️ Hauteur FIXE, et non `heightIn(min = ...)`.
        //
        // Avec une hauteur qui suit le contenu, « ENVOYER » tombait bas dans sa
        // case : il n'y avait pas d'espace en dessous a repartir, seulement la
        // marge. En fixant la hauteur, le mot se centre vraiment dans la place
        // qui reste sous l'etiquette — et on peut le remonter de quelques points
        // pour compenser l'interlignage, qui ajoute du vide sous les lettres.
        // Tant que le juge n'a pas appuye, le bouton RESPIRE.
        //
        // Une pulsation tres lente et tres faible — 1,5 % d'echelle sur 1,4 s —
        // plus une lueur de la couleur du circuit qui va et vient sous lui.
        // Assez pour attirer l'oeil dans une salle bruyante, assez discret pour
        // ne pas agacer quelqu'un qui valide deux cents fois dans la journee.
        //
        // Elle s'arrete des que le bouton n'est plus l'etape en cours : une
        // animation qui tourne pour rien coute de la batterie et perd son sens
        // de signal.
        val souffle = rememberInfiniteTransition(label = "souffle")
        val echelle by souffle.animateFloat(
            initialValue = 1f, targetValue = 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "echelle",
        )
        val lueur by souffle.animateFloat(
            initialValue = 6f, targetValue = 22f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "lueur",
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .then(
                    if (blocFait) Modifier
                        .graphicsLayer { scaleX = echelle; scaleY = echelle }
                        .shadow(
                            lueur.dp, RoundedCornerShape(28.dp),
                            ambientColor = COULEUR, spotColor = COULEUR,
                        )
                    else Modifier,
                )
                .clip(RoundedCornerShape(28.dp))
                .background(if (blocFait) COULEUR else Color(0xFF101419))
                .border(
                    if (blocFait) 0.dp else 1.5.dp,
                    if (blocFait) Color.Transparent else Color(0xFF1A2029),
                    RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 22.dp),
        ) {
            Row(
                Modifier.align(Alignment.TopStart).padding(top = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(19.dp)
                        .background(
                            if (blocFait) encreSur(COULEUR).copy(alpha = 0.18f)
                            else Color(0xFF1E252F),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("3", fontFamily = Titre, fontSize = 11.sp,
                         color = if (blocFait) encreSur(COULEUR) else Color(0xFF3C4652))
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    if (blocFait) "PRÊT" else "ENVOYER",
                    fontFamily = Fort, fontSize = 11.sp, letterSpacing = 2.2.sp,
                    color = if (blocFait) encreSur(COULEUR).copy(alpha = 0.7f)
                            else Color(0xFF39424E),
                )
            }

            if (blocFait) {
                Text(
                    "ENVOYER",
                    fontFamily = Titre, fontSize = 30.sp, letterSpacing = 3.sp,
                    // Un interlignage colle a la taille : sinon la boite du
                    // texte reserve de la place pour des jambages qu'un mot en
                    // capitales n'a pas, et le mot parait pose trop bas.
                    lineHeight = 30.sp,
                    color = encreSur(COULEUR),
                    modifier = Modifier
                        // Centre dans TOUTE la case, et non dans la place qui
                        // reste sous l'etiquette. Cette derniere est une petite
                        // mention posee dans le coin ; centrer le mot en
                        // dessous d'elle le faisait tomber bas et le bouton ne
                        // se lisait plus comme un bouton.
                        .align(Alignment.Center)
                        // La chasse ajoutee apres la DERNIERE lettre entre dans
                        // la largeur mesuree et decale le mot vers la gauche.
                        // On rend les 3 sp au debut.
                        .padding(start = 3.dp),
                )
            } else {
                Text(
                    if (grimpeurFait) "Après le bloc" else "Après les deux scans",
                    fontFamily = Normal, fontSize = 21.sp, lineHeight = 21.sp,
                    color = Color(0xFF39424E),
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                if (blocFait) "Effacer le scan" else "",
                fontFamily = Normal, fontSize = 14.sp, color = MurEncre2,
            )
        }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MurTrait))
        Spacer(Modifier.height(16.dp))
        Text("DERNIÈRES VALIDATIONS", fontFamily = Fort, fontSize = 10.sp,
             letterSpacing = 2.2.sp, color = MurEncre2)
        Spacer(Modifier.height(10.dp))
        JOURNAL.forEach { v ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(v.couleur, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(v.heure, fontFamily = Moyen, fontSize = 13.sp, color = MurEncre2)
                Spacer(Modifier.width(12.dp))
                Text(v.nom, fontFamily = Moyen, fontSize = 15.sp, color = MurEncre,
                     modifier = Modifier.weight(1f))
                Text(
                    v.tag, fontFamily = Fort, fontSize = 13.sp,
                    color = encreSur(v.couleur),
                    modifier = Modifier
                        .background(v.couleur, RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * Le voyant de connexion, dans ses trois etats.
 *
 * ⚠️ Je l'avais remplace par un caractere « ◗ » dans une premiere maquette :
 * illisible, et sans les trois etats. C'etait un recul sur ce qui marchait.
 *
 * - **vert** : le serveur repond ;
 * - **ambre, qui bat** : verification en cours, au retour au premier plan ;
 * - **rouge BARRE** : injoignable.
 *
 * Barre, et pas seulement rouge : environ 8 % des hommes distinguent mal le
 * vert du rouge, et il y a des juges hommes. La forme doit porter
 * l'information autant que la couleur.
 *
 * Sa PLACE compte aussi. Il forme un groupe avec la pastille de file — deux
 * choses qui disent « ou en est l'envoi » — et le logo-menu reste separe par un
 * espace plus large. La proximite fait le rangement, sans avoir besoin d'un
 * cadre autour.
 */
@Composable
private fun Voyant(etat: String) {
    val battement = rememberInfiniteTransition(label = "voyant")
    val opacite by battement.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "opacite",
    )
    val (icone, couleur, description) = when (etat) {
        "ko" -> Triple(R.drawable.ic_wifi_off, Rouge, "Serveur injoignable")
        "doute" -> Triple(R.drawable.ic_wifi, Color(0xFFE5B44A), "Connexion en cours")
        else -> Triple(R.drawable.ic_wifi, Vert, "Serveur joignable")
    }
    Icon(
        painter = painterResource(id = icone),
        contentDescription = description,
        tint = couleur,
        modifier = Modifier
            .size(21.dp)
            .graphicsLayer { alpha = if (etat == "doute") opacite else 1f },
    )
}

/**
 * Une carte de scan, dans ses trois etats.
 *
 * ⚠️ « active » est ce qui manquait. Au repos, les deux cartes se ressemblaient
 * et rien ne disait par ou commencer : l'ecran etait lisible mais muet. Celle
 * qu'il faut scanner maintenant est plus claire, cerclee, numerotee, et porte
 * une consigne. L'autre s'efface.
 */
@Composable
private fun CarteMur(
    numero: String,
    etiquette: String,
    fait: Boolean,
    active: Boolean,
    teinte: Color? = null,
    contenu: @Composable () -> Unit,
) {
    val fond = when {
        fait -> if (teinte != null) MurSurface else Color(0xFF1B2432)
        active -> Color(0xFF161C26)
        else -> Color(0xFF101419)
    }
    val bord = when {
        teinte != null -> teinte.copy(alpha = 0.5f)
        fait -> Color(0xFF2C3849)
        active -> Color(0xFF3E4B5E)
        else -> Color(0xFF1A2029)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (fait) 132.dp else 116.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(fond)
            .then(if (teinte != null) Modifier.background(teinte.copy(alpha = 0.10f))
                  else Modifier)
            .border(if (active) 2.dp else 1.5.dp, bord, RoundedCornerShape(28.dp))
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Le numero dit l'ORDRE. Deux cartes identiques ne le disaient
                // pas, et un benevole qui prend le telephone pour la premiere
                // fois de l'annee ne devine pas qu'il faut commencer en haut.
                Box(
                    Modifier
                        .size(19.dp)
                        .background(
                            when {
                                teinte != null -> teinte
                                fait -> Color(0xFF3A4759)
                                active -> MurEncre
                                else -> Color(0xFF1E252F)
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        numero, fontFamily = Titre, fontSize = 11.sp,
                        color = when {
                            teinte != null -> encreSur(teinte)
                            active -> Color(0xFF0B0D11)
                            fait -> MurEncre
                            else -> Color(0xFF3C4652)
                        },
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    etiquette, fontFamily = Fort, fontSize = 11.sp,
                    letterSpacing = 2.2.sp,
                    color = when {
                        teinte != null -> teinte
                        active -> MurEncre
                        fait -> MurEncre2
                        else -> Color(0xFF39424E)
                    },
                )
            }
            Spacer(Modifier.height(if (fait) 12.dp else 14.dp))

            if (fait) {
                contenu()
            } else if (active) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.QrCodeScanner, contentDescription = null,
                        tint = MurEncre, modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Toucher pour scanner", fontFamily = Moyen,
                         fontSize = 21.sp, color = MurEncre)
                }
            } else {
                Text("Ensuite", fontFamily = Normal, fontSize = 21.sp,
                     color = Color(0xFF39424E))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  B — « Nuit et craie » : editorial, sobre, une seule couleur d'accent
// ═══════════════════════════════════════════════════════════════════════════

private val CraieFond = Color(0xFF0A0A0B)
private val CraieEncre = Color(0xFFF2EFE8)
private val CraieEncre2 = Color(0xFF7A7568)
private val CraieTrait = Color(0xFF24231F)
// L'or des cornes de la chevre du club. Une couleur qui vient de l'identite
// existante plutot que d'une palette generique.
private val Or = Color(0xFFC9973F)

@Composable
fun MaquetteCraie(rempli: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CraieFond)
            .padding(horizontal = 26.dp),
    ) {
        Spacer(Modifier.height(56.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ANNONAY ESCALADE", fontFamily = Fort, fontSize = 12.sp,
                     letterSpacing = 3.2.sp, color = CraieEncre2)
                Spacer(Modifier.height(3.dp))
                Text("Compétition de bloc · novembre",
                     fontFamily = Normal, fontSize = 12.sp, color = Or)
            }
            Text("⚙", fontFamily = Normal, fontSize = 17.sp, color = CraieEncre2)
        }

        Spacer(Modifier.height(44.dp))

        SectionCraie(
            numero = "01", titre = "GRIMPEUR",
            valeur = if (rempli) NOM else "—",
            meta = if (rempli) "Dossard $DOSSARD · $CATEGORIE" else "En attente du scan",
            rempli = rempli, taille = 38.sp,
        )

        Spacer(Modifier.height(38.dp))

        SectionCraie(
            numero = "02", titre = "BLOC",
            valeur = if (rempli) TAG_BLOC else "—",
            meta = if (rempli) ZONE else "En attente du scan",
            rempli = rempli, taille = 44.sp,
        )

        Spacer(Modifier.height(40.dp))

        // Un bouton plein, malgre le parti pris editorial : un filet ne fait
        // pas une cible pour un pouce, et c'est le geste le plus repete de la
        // journee.
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(999.dp))
                .then(
                    if (rempli) Modifier.background(CraieEncre)
                    else Modifier.border(1.dp, CraieTrait, RoundedCornerShape(999.dp)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Envoyer",
                fontFamily = Fort, fontSize = 21.sp, letterSpacing = 0.3.sp,
                color = if (rempli) CraieFond else Color(0xFF3A382F),
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                if (rempli) "Effacer le scan" else "Scannez le grimpeur puis le bloc",
                fontFamily = Normal, fontSize = 13.sp, color = CraieEncre2,
            )
        }

        Spacer(Modifier.height(38.dp))
        Text("DERNIÈRES VALIDATIONS", fontFamily = Fort, fontSize = 10.sp,
             letterSpacing = 2.6.sp, color = CraieEncre2)
        Spacer(Modifier.height(4.dp))
        JOURNAL.forEach { v ->
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(v.heure, fontFamily = Moyen, fontSize = 13.sp,
                         color = CraieEncre2, modifier = Modifier.width(46.dp))
                    Text(v.nom, fontFamily = Moyen, fontSize = 15.sp,
                         color = CraieEncre, modifier = Modifier.weight(1f))
                    // Une seule couleur d'accent dans cette direction : le tag
                    // est en or, pas en couleur de circuit. La sobriete se tient
                    // jusqu'au bout, sinon ce n'est plus une direction.
                    Text(v.tag, fontFamily = Fort, fontSize = 13.sp,
                         letterSpacing = 0.8.sp, color = Or)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(CraieTrait))
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SectionCraie(
    numero: String, titre: String, valeur: String, meta: String,
    rempli: Boolean, taille: androidx.compose.ui.unit.TextUnit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(numero, fontFamily = Fort, fontSize = 11.sp,
                 letterSpacing = 1.6.sp, color = Or)
            Spacer(Modifier.width(9.dp))
            Text("—", fontFamily = Normal, fontSize = 11.sp, color = CraieTrait)
            Spacer(Modifier.width(9.dp))
            Text(titre, fontFamily = Fort, fontSize = 11.sp,
                 letterSpacing = 2.6.sp, color = CraieEncre2)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            valeur,
            fontFamily = archivo(600), fontSize = taille,
            letterSpacing = (-1).sp, lineHeight = taille * 1.05f,
            color = if (rempli) CraieEncre else Color(0xFF302F29),
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(CraieTrait))
        Spacer(Modifier.height(10.dp))
        Text(meta, fontFamily = Normal, fontSize = 13.sp, color = CraieEncre2)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  C — « Relief » : profondeur, surfaces flottantes
// ═══════════════════════════════════════════════════════════════════════════

private val ReliefHaut = Color(0xFF161E2B)
private val ReliefBas = Color(0xFF090B10)
private val ReliefCarte = Color(0xFF1B2432)
private val ReliefEncre = Color(0xFFEFF3F8)
private val ReliefEncre2 = Color(0xFF7C889C)

@Composable
fun MaquetteRelief(rempli: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ReliefHaut, ReliefBas))),
    ) {
        // La lueur du circuit, derriere les cartes. Elle donne sa couleur a
        // l'ambiance sans peindre une seule surface.
        if (rempli) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(COULEUR.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(540f, 1180f),
                            radius = 900f,
                        ),
                    ),
            )
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(54.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(
                    if (rempli) Vert else ReliefEncre2, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text("ANNONAY", fontFamily = Fort, fontSize = 13.sp,
                     letterSpacing = 2.4.sp, color = ReliefEncre2)
                Spacer(Modifier.weight(1f))
                Text("⚙", fontFamily = Normal, fontSize = 17.sp, color = ReliefEncre2)
            }

            Spacer(Modifier.height(30.dp))

            CarteRelief(rempli) {
                Text("GRIMPEUR", fontFamily = Fort, fontSize = 11.sp,
                     letterSpacing = 2.2.sp, color = ReliefEncre2)
                Spacer(Modifier.height(10.dp))
                if (rempli) {
                    Text(NOM, fontFamily = Titre, fontSize = 28.sp,
                         letterSpacing = (-0.5).sp, color = ReliefEncre)
                    Spacer(Modifier.height(4.dp))
                    Text("N° $DOSSARD  ·  $CATEGORIE", fontFamily = Moyen,
                         fontSize = 14.sp, color = ReliefEncre2)
                } else {
                    Text("À scanner", fontFamily = Normal, fontSize = 25.sp,
                         color = Color(0xFF4A566B))
                }
            }

            Spacer(Modifier.height(16.dp))

            CarteRelief(rempli, teinte = if (rempli) COULEUR else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("BLOC", fontFamily = Fort, fontSize = 11.sp,
                         letterSpacing = 2.2.sp, color = ReliefEncre2)
                    Spacer(Modifier.weight(1f))
                    if (rempli) {
                        Box(Modifier.size(11.dp).background(COULEUR, CircleShape))
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (rempli) {
                    Text(TAG_BLOC, fontFamily = Titre, fontSize = 36.sp,
                         letterSpacing = (-0.5).sp, color = ReliefEncre)
                    Spacer(Modifier.height(2.dp))
                    Text(ZONE, fontFamily = Moyen, fontSize = 15.sp, color = COULEUR)
                } else {
                    Text("À scanner", fontFamily = Normal, fontSize = 25.sp,
                         color = Color(0xFF4A566B))
                }
            }

            Spacer(Modifier.height(26.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .then(
                        if (rempli) Modifier.shadow(
                            26.dp, RoundedCornerShape(999.dp),
                            ambientColor = COULEUR, spotColor = COULEUR,
                        ) else Modifier,
                    )
                    .clip(RoundedCornerShape(999.dp))
                    .then(
                        if (rempli) Modifier.background(COULEUR)
                        else Modifier.background(Color(0xFF141B27)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Envoyer",
                    fontFamily = Titre, fontSize = 24.sp, letterSpacing = 0.5.sp,
                    color = if (rempli) encreSur(COULEUR) else Color(0xFF465165),
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    if (rempli) "Effacer le scan" else "Scannez le grimpeur puis le bloc",
                    fontFamily = Normal, fontSize = 13.sp, color = ReliefEncre2,
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("DERNIÈRES VALIDATIONS", fontFamily = Fort, fontSize = 10.sp,
                 letterSpacing = 2.2.sp, color = ReliefEncre2,
                 modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x99131A26))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column {
                    JOURNAL.forEach { v ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(v.couleur, CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text(v.heure, fontFamily = Moyen, fontSize = 13.sp,
                                 color = ReliefEncre2)
                            Spacer(Modifier.width(12.dp))
                            Text(v.nom, fontFamily = Moyen, fontSize = 15.sp,
                                 color = ReliefEncre, modifier = Modifier.weight(1f))
                            Text(v.tag, fontFamily = Fort, fontSize = 13.sp,
                                 letterSpacing = 0.5.sp, color = v.couleur)
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun CarteRelief(
    rempli: Boolean,
    teinte: Color? = null,
    contenu: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 126.dp)
            .shadow(
                if (rempli) 18.dp else 8.dp,
                RoundedCornerShape(30.dp),
                ambientColor = teinte ?: Color.Black,
                spotColor = teinte ?: Color.Black,
            )
            .clip(RoundedCornerShape(30.dp))
            .background(ReliefCarte)
            // Un filet clair sur l'arete du haut : c'est ce qui fait qu'une
            // surface a l'air eclairee plutot que posee a plat.
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color(0x1AFFFFFF), Color(0x05FFFFFF)),
                ),
                RoundedCornerShape(30.dp),
            )
            .padding(22.dp),
    ) {
        Column(content = contenu)
    }
}
