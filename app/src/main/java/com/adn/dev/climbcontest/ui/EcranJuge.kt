package com.adn.dev.climbcontest.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adn.dev.climbcontest.R
import com.adn.dev.climbcontest.Validation
import com.adn.dev.climbcontest.ui.theme.Alerte
import com.adn.dev.climbcontest.ui.theme.Attention
import com.adn.dev.climbcontest.ui.theme.CarteActive
import com.adn.dev.climbcontest.ui.theme.CarteAttente
import com.adn.dev.climbcontest.ui.theme.CarteFaite
import com.adn.dev.climbcontest.ui.theme.Encre
import com.adn.dev.climbcontest.ui.theme.Encre2
import com.adn.dev.climbcontest.ui.theme.Encre3
import com.adn.dev.climbcontest.ui.theme.Fond
import com.adn.dev.climbcontest.ui.theme.Fort
import com.adn.dev.climbcontest.ui.theme.Moyen
import com.adn.dev.climbcontest.ui.theme.Normal
import com.adn.dev.climbcontest.ui.theme.Titre
import com.adn.dev.climbcontest.ui.theme.TraitActif
import com.adn.dev.climbcontest.ui.theme.TraitAttente
import com.adn.dev.climbcontest.ui.theme.TraitFait
import com.adn.dev.climbcontest.ui.theme.Vert
import com.adn.dev.climbcontest.ui.theme.couleurDeCircuit
import com.adn.dev.climbcontest.ui.theme.encreSur

/**
 * L'écran du juge : **trois étapes numérotées, une seule active à la fois**.
 *
 * ① scanner le grimpeur → ② scanner le bloc → ③ envoyer.
 *
 * C'est la structure qui fait tenir l'écran. Auparavant, deux cartes identiques
 * et un bouton posé à côté : rien ne disait par où commencer, et un bénévole qui
 * prend le téléphone pour la première fois de l'année ne devine pas qu'il faut
 * lire de haut en bas.
 *
 * **La couleur du circuit prend l'écran** dès que le bloc est scanné. Ce n'est
 * pas de la décoration : le juge vérifie d'un coup d'œil qu'il est sur le bon
 * circuit, ce que le tag « ZJ1 » ne dit pas à quelqu'un qui ne connaît pas la
 * convention de nommage par cœur.
 */
@Composable
fun EcranJuge(
    dossard: String?,
    grimpeur: String?,
    bloc: String?,
    couleurDuBloc: String?,
    enAttente: Int,
    refusees: Int,
    historique: List<Validation>,
    serveurJoignable: Boolean?,
    onScanGrimpeur: () -> Unit,
    onScanBloc: () -> Unit,
    onEnvoyer: () -> Unit,
    onEffacer: () -> Unit,
    onMenu: () -> Unit,
    onToutEnvoyer: () -> Unit,
) {
    val grimpeurFait = dossard != null
    val blocFait = bloc != null
    val couleur = couleurDeCircuit(couleurDuBloc) ?: Encre

    Column(
        Modifier
            .fillMaxSize()
            .background(Fond)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(54.dp))

        Entete(
            enAttente = enAttente,
            refusees = refusees,
            serveurJoignable = serveurJoignable,
            onToutEnvoyer = onToutEnvoyer,
            onMenu = onMenu,
        )

        Spacer(Modifier.height(30.dp))

        CarteEtape(
            numero = "1",
            etiquette = stringResource(R.string.climber).uppercase(),
            fait = grimpeurFait,
            active = !grimpeurFait,
            onClick = onScanGrimpeur,
        ) {
            Text(
                grimpeur ?: dossard.orEmpty(),
                fontFamily = Titre, fontSize = 30.sp, letterSpacing = (-0.6).sp,
                color = Encre, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (grimpeur != null && dossard != null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    stringResource(R.string.numero_dossard, dossard),
                    fontFamily = Moyen, fontSize = 14.sp,
                    letterSpacing = 0.8.sp, color = Encre2,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        CarteEtape(
            numero = "2",
            etiquette = stringResource(R.string.block).uppercase(),
            fait = blocFait,
            active = grimpeurFait && !blocFait,
            teinte = if (blocFait) couleur else null,
            onClick = onScanBloc,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Le tag dans une pastille pleine : c'est LUI que le juge
                // vérifie, pas la phrase à côté.
                Text(
                    bloc.orEmpty(),
                    fontFamily = Titre, fontSize = 32.sp, letterSpacing = (-0.5).sp,
                    color = encreSur(couleur),
                    modifier = Modifier
                        .background(couleur, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                if (couleurDuBloc != null) {
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.circuit_x, couleurDuBloc),
                        fontFamily = Moyen, fontSize = 16.sp,
                        color = couleur.copy(alpha = 0.9f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        BoutonEnvoyer(pret = grimpeurFait && blocFait, grimpeurFait = grimpeurFait,
                      couleur = couleur, onClick = onEnvoyer)

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            if (grimpeurFait) {
                Text(
                    stringResource(R.string.reset),
                    fontFamily = Normal, fontSize = 14.sp, color = Encre2,
                    modifier = Modifier.clickable(onClick = onEffacer).padding(10.dp),
                )
            } else {
                Spacer(Modifier.height(34.dp))
            }
        }

        if (historique.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(TraitAttente))
            Spacer(Modifier.height(16.dp))
            Journal(historique)
        }

        Spacer(Modifier.height(28.dp))
    }
}

// --- L'en-tête ---------------------------------------------------------------

/**
 * `ANNONAY`, l'état de l'envoi, et le menu.
 *
 * ⚠️ La file est **dans** l'en-tête, et pas en bandeau au-dessus des cartes.
 * Posée en bandeau, elle poussait tout l'écran vers le bas dès qu'une réussite
 * attendait, et remontait tout dès qu'elle repartait : **la mise en page bougeait
 * sous le pouce du juge pendant qu'il scannait.** Ici, elle apparaît et disparaît
 * sans rien déplacer.
 *
 * Le rangement se fait par la **proximité** : la pastille de file et le voyant
 * sont serrés l'un contre l'autre — ils disent tous deux « où en est l'envoi » —
 * et le logo-menu est séparé par un espace plus large. Pas de séparateur, pas de
 * cadre, et pourtant deux groupes lisibles.
 */
@Composable
private fun Entete(
    enAttente: Int,
    refusees: Int,
    serveurJoignable: Boolean?,
    onToutEnvoyer: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "ANNONAY",
            fontFamily = Fort, fontSize = 13.sp, letterSpacing = 2.4.sp,
            color = Encre2,
        )
        Spacer(Modifier.weight(1f))

        // Le rouge est réservé à ce qui demande une ACTION : une refusée ne
        // repartira pas seule, contrairement à une réussite en file.
        if (refusees > 0) {
            Pastille(
                texte = pluralStringResource(R.plurals.refusees_n, refusees, refusees),
                couleur = Alerte,
                onClick = onMenu,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (enAttente > 0) {
            Pastille(
                texte = stringResource(R.string.en_attente, enAttente),
                couleur = Attention,
                fleche = true,
                // Un appui force l'envoi immédiat : le geste de fin de
                // compétition, avant d'éteindre les téléphones.
                onClick = onToutEnvoyer,
            )
            Spacer(Modifier.width(10.dp))
        }

        Voyant(serveurJoignable)
        Spacer(Modifier.width(18.dp))
        Image(
            painter = painterResource(id = R.drawable.logo_rond),
            contentDescription = stringResource(R.string.menu),
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onMenu),
        )
    }
}

@Composable
private fun Pastille(
    texte: String,
    couleur: Color,
    fleche: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(couleur.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (fleche) {
            // La flèche dit que la pastille est actionnable. Sans elle, un
            // compteur ressemble à un simple affichage.
            Icon(
                Icons.Filled.ArrowUpward, contentDescription = null,
                tint = couleur, modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(texte, fontFamily = Moyen, fontSize = 12.sp,
             letterSpacing = 0.3.sp, color = couleur)
    }
}

/**
 * Le voyant de connexion, dans ses trois états.
 *
 * - **vert** : le serveur répond ;
 * - **ambre, qui bat** : vérification en cours, au retour au premier plan ;
 * - **rouge BARRÉ** : injoignable.
 *
 * Barré, et pas seulement rouge : environ 8 % des hommes distinguent mal ces
 * deux couleurs-là, et il y a des juges hommes. La forme doit porter
 * l'information autant que la couleur.
 */
@Composable
private fun Voyant(joignable: Boolean?) {
    val battement = rememberInfiniteTransition(label = "voyant")
    val opacite by battement.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "opacite",
    )
    val (icone, couleur, description) = when (joignable) {
        true -> Triple(R.drawable.ic_wifi, Vert, R.string.serveur_ok)
        false -> Triple(R.drawable.ic_wifi_off, Alerte, R.string.serveur_ko)
        null -> Triple(R.drawable.ic_wifi, Attention, R.string.serveur_inconnu)
    }
    Icon(
        painter = painterResource(id = icone),
        contentDescription = stringResource(description),
        tint = couleur,
        modifier = Modifier
            .size(21.dp)
            .graphicsLayer { alpha = if (joignable == null) opacite else 1f },
    )
}

// --- Les cartes d'étape ------------------------------------------------------

/**
 * Une étape, dans ses trois états : **faite**, **active**, **en attente**.
 *
 * « Active » veut dire : c'est celle-ci qu'il faut faire maintenant. Elle est
 * plus claire, cerclée d'un trait plus épais, numérotée en clair, et porte une
 * consigne explicite. Les autres s'effacent.
 *
 * Les changements d'état passent par des **ressorts** et non des durées fixes :
 * une carte qui se remplit arrive avec un léger dépassement, comme un objet qui
 * se pose. C'est ce que Material 3 Expressive appelle une transition physique,
 * et c'est ce qui distingue une application qui répond d'une application qui
 * s'affiche.
 */
@Composable
private fun CarteEtape(
    numero: String,
    etiquette: String,
    fait: Boolean,
    active: Boolean,
    teinte: Color? = null,
    onClick: () -> Unit,
    contenu: @Composable () -> Unit,
) {
    val fond by animateColorAsState(
        targetValue = when {
            fait -> CarteFaite
            active -> CarteActive
            else -> CarteAttente
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy,
                               stiffness = Spring.StiffnessMediumLow),
        label = "fond",
    )
    val bord by animateColorAsState(
        targetValue = when {
            teinte != null -> teinte.copy(alpha = 0.5f)
            fait -> TraitFait
            active -> TraitActif
            else -> TraitAttente
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bord",
    )
    val epaisseur by animateDpAsState(
        targetValue = if (active) 2.dp else 1.5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "epaisseur",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (fait) 132.dp else 116.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(fond)
            .then(
                // Une teinte légère POSÉE SUR la surface sombre, et non la
                // couleur à 14 % sur du noir : cette dernière virait au brun
                // sale dès que le circuit était jaune ou rouge.
                if (teinte != null) Modifier.background(teinte.copy(alpha = 0.10f))
                else Modifier,
            )
            .border(epaisseur, bord, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Le numéro dit l'ORDRE. Deux cartes identiques ne le disaient
                // pas.
                Box(
                    Modifier
                        .size(19.dp)
                        .background(
                            when {
                                teinte != null -> teinte
                                fait -> Color(0xFF3A4759)
                                active -> Encre
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
                            active -> Fond
                            fait -> Encre
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
                        active -> Encre
                        fait -> Encre2
                        else -> Encre3
                    },
                )
            }
            Spacer(Modifier.height(if (fait) 12.dp else 14.dp))

            when {
                fait -> contenu()
                active -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.QrCodeScanner, contentDescription = null,
                        tint = Encre, modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.toucher_pour_scanner),
                         fontFamily = Moyen, fontSize = 21.sp, color = Encre)
                }
                else -> Text(stringResource(R.string.ensuite), fontFamily = Normal,
                             fontSize = 21.sp, color = Encre3)
            }
        }
    }
}

// --- Étape 3 : envoyer -------------------------------------------------------

/**
 * Le bouton d'envoi, troisième étape de la séquence.
 *
 * **Tant que le juge n'a pas appuyé, il respire** : une pulsation très lente et
 * très faible — 1,5 % d'échelle sur 1,4 s — plus une lueur de la couleur du
 * circuit qui va et vient sous lui. Assez pour attirer l'œil dans une salle
 * bruyante, assez discret pour ne pas agacer quelqu'un qui valide deux cents
 * fois dans la journée.
 *
 * L'animation s'arrête dès que le bouton n'est plus l'étape en cours : une
 * animation qui tourne pour rien coûte de la batterie et perd son sens de signal.
 */
@Composable
private fun BoutonEnvoyer(
    pret: Boolean,
    grimpeurFait: Boolean,
    couleur: Color,
    onClick: () -> Unit,
) {
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
    val fond by animateColorAsState(
        targetValue = if (pret) couleur else CarteAttente,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fondBouton",
    )

    Box(
        Modifier
            .fillMaxWidth()
            // ⚠️ Hauteur FIXE, et non `heightIn(min = ...)`. Avec une hauteur qui
            // suit le contenu, « ENVOYER » tombait bas dans sa case : il n'y
            // avait pas d'espace en dessous à répartir, seulement la marge.
            .height(112.dp)
            .then(
                if (pret) Modifier
                    .graphicsLayer { scaleX = echelle; scaleY = echelle }
                    .shadow(lueur.dp, RoundedCornerShape(28.dp),
                            ambientColor = couleur, spotColor = couleur)
                else Modifier,
            )
            .clip(RoundedCornerShape(28.dp))
            .background(fond)
            .then(
                if (pret) Modifier
                else Modifier.border(1.5.dp, TraitAttente, RoundedCornerShape(28.dp)),
            )
            .clickable(enabled = pret, onClick = onClick)
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
                        if (pret) encreSur(couleur).copy(alpha = 0.18f)
                        else Color(0xFF1E252F),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("3", fontFamily = Titre, fontSize = 11.sp,
                     color = if (pret) encreSur(couleur) else Color(0xFF3C4652))
            }
            Spacer(Modifier.width(9.dp))
            Text(
                stringResource(if (pret) R.string.pret else R.string.send).uppercase(),
                fontFamily = Fort, fontSize = 11.sp, letterSpacing = 2.2.sp,
                color = if (pret) encreSur(couleur).copy(alpha = 0.7f) else Encre3,
            )
        }

        if (pret) {
            Text(
                stringResource(R.string.send).uppercase(),
                fontFamily = Titre, fontSize = 30.sp, letterSpacing = 3.sp,
                // Un interlignage collé à la taille : sinon la boîte du texte
                // réserve de la place pour des jambages qu'un mot en capitales
                // n'a pas, et le mot paraît posé trop bas.
                lineHeight = 30.sp,
                color = encreSur(couleur),
                modifier = Modifier
                    .align(Alignment.Center)
                    // La chasse ajoutée après la DERNIÈRE lettre entre dans la
                    // largeur mesurée et décale le mot vers la gauche. On rend
                    // les 3 sp au début.
                    .padding(start = 3.dp),
            )
        } else {
            Text(
                stringResource(
                    if (grimpeurFait) R.string.apres_le_bloc else R.string.apres_les_scans
                ),
                fontFamily = Normal, fontSize = 21.sp, lineHeight = 21.sp,
                color = Encre3,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

// --- Le journal --------------------------------------------------------------

/**
 * Les dernières validations.
 *
 * Deux raisons d'être là, et la seconde n'est pas cosmétique :
 *
 * 1. c'est la réponse à « est-ce que j'ai bien envoyé ? », qui n'en avait
 *    aucune ;
 * 2. sans lui, la moitié basse de l'écran restait noire toute la journée. Un
 *    écran à moitié vide n'a jamais l'air fini, quelle que soit la qualité du
 *    reste.
 */
@Composable
private fun Journal(validations: List<Validation>) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.dernieres_validations).uppercase(),
            fontFamily = Fort, fontSize = 10.sp, letterSpacing = 2.2.sp,
            color = Encre2,
        )
        Spacer(Modifier.height(10.dp))
        validations.forEach { v ->
            val couleur = couleurDeCircuit(v.couleur) ?: Encre2
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(couleur, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(v.heure, fontFamily = Moyen, fontSize = 13.sp, color = Encre2)
                Spacer(Modifier.width(12.dp))
                Text(
                    v.grimpeur, fontFamily = Moyen, fontSize = 15.sp, color = Encre,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    v.bloc, fontFamily = Fort, fontSize = 13.sp,
                    color = encreSur(couleur),
                    modifier = Modifier
                        .background(couleur, RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
