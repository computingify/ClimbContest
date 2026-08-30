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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.launch

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
    /** Un envoi demandé à la main est-il en train de partir ? */
    envoiEnCours: Boolean,
    /**
     * Le nombre de réussites validées depuis le lancement.
     *
     * Un **compteur** et non un événement : une rotation d'écran rejoue une
     * `SharedFlow` non consommée ou, pire, en perd une. Ici l'écran compare ce
     * qu'il a déjà fêté à ce que le compteur dit ; la question ne se pose pas.
     */
    validations: Int,
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

    // Ce que les cartes affichent — y compris pendant les 110 ms où elles
    // s'effacent. Voir `retenu`.
    val nomAffiche = retenu(grimpeur)
    val dossardAffiche = retenu(dossard)
    val blocAffiche = retenu(bloc)
    val circuitAffiche = retenu(couleurDuBloc)
    val teinteAffichee = couleurDeCircuit(circuitAffiche) ?: Encre

    // --- Le moment de validation ---------------------------------------------
    //
    // Le seul instant de la journée où l'écran s'autorise à parler fort. Il dure
    // 700 ms et ne bloque RIEN : un juge qui enchaîne peut rescanner pendant que
    // la coche est encore là. C'est la contrainte qui a écarté l'animation
    // plein écran d'une seconde : deux cents validations par jour, c'est trois
    // minutes passées à regarder une animation.
    val haptique = LocalHapticFeedback.current
    val moment = remember { Animatable(0f) }
    val rebond = remember { Animatable(0f) }
    var enConfirmation by remember { mutableStateOf(false) }
    var vues by remember { mutableIntStateOf(validations) }

    // ⚠️ La couleur est saisie AU CLIC. 500 ms après l'envoi le ViewModel a
    // tout effacé et `couleur` est revenue au neutre : la lire pendant
    // l'animation aurait fait blanchir la confirmation en cours de route.
    var couleurValidee by remember { mutableStateOf(Encre) }

    LaunchedEffect(validations) {
        // À la première composition — et après chaque rotation — le compteur
        // vaut déjà ce qu'il vaut : il n'y a rien à fêter.
        if (validations == vues) return@LaunchedEffect
        vues = validations
        haptique.performHapticFeedback(HapticFeedbackType.Confirm)
        enConfirmation = true
        moment.snapTo(0f)
        rebond.snapTo(0f)
        launch {
            rebond.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.42f,
                                       stiffness = Spring.StiffnessMedium),
            )
        }
        moment.animateTo(1f, tween(700, easing = LinearEasing))
        enConfirmation = false
    }

    val confirmation =
        if (enConfirmation) Confirmation(couleurValidee, moment.value, rebond.value)
        else null

    Box(Modifier.fillMaxSize().background(Fond)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(54.dp))

            Entete(
                enAttente = enAttente,
                refusees = refusees,
                envoiEnCours = envoiEnCours,
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
                    nomAffiche ?: dossardAffiche.orEmpty(),
                    fontFamily = Titre, fontSize = 30.sp, letterSpacing = (-0.6).sp,
                    color = Encre, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (nomAffiche != null && dossardAffiche != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        stringResource(R.string.numero_dossard, dossardAffiche),
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
                        blocAffiche.orEmpty(),
                        fontFamily = Titre, fontSize = 32.sp, letterSpacing = (-0.5).sp,
                        color = encreSur(teinteAffichee),
                        modifier = Modifier
                            .background(teinteAffichee, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    if (circuitAffiche != null) {
                        Spacer(Modifier.width(14.dp))
                        Text(
                            stringResource(R.string.circuit_x, circuitAffiche),
                            fontFamily = Moyen, fontSize = 16.sp,
                            color = teinteAffichee.copy(alpha = 0.9f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            BoutonEnvoyer(
                pret = grimpeurFait && blocFait,
                grimpeurFait = grimpeurFait,
                couleur = couleur,
                confirmation = confirmation,
                onClick = { couleurValidee = couleur; onEnvoyer() },
            )

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

        // Le voile : la couleur du circuit passe sur tout l'écran et s'efface.
        // Aucun `clickable` dessus — il ne capte donc aucun geste, et le juge peut
        // relancer un scan sans attendre la fin.
        if (confirmation != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        confirmation.couleur.copy(
                            alpha = impulsion(confirmation.progression) * 0.13f,
                        ),
                    ),
            )
        }
    }
}

/**
 * L'instant qui suit un envoi. `null` le reste du temps.
 *
 * @param couleur celle du circuit validé, figée au clic.
 * @param progression 0 → 1 linéairement sur 700 ms.
 * @param rebond 0 → 1 **avec dépassement** : c'est lui qui fait claquer la coche.
 */
private data class Confirmation(
    val couleur: Color,
    val progression: Float,
    val rebond: Float,
)

/**
 * Une impulsion : monte pendant `montee`, redescend sur tout le reste.
 *
 * Une décroissance simple aurait posé le voile à pleine intensité d'un seul
 * coup — un flash. Là il arrive en 85 ms, ce que l'œil lit comme un mouvement
 * et non comme une coupure de courant.
 */
private fun impulsion(p: Float, montee: Float = 0.12f): Float =
    if (p <= montee) p / montee else 1f - (p - montee) / (1f - montee)

// --- L'en-tête ---------------------------------------------------------------

/**
 * L'état de l'envoi, à gauche ; le voyant et le menu, à droite.
 *
 * ⚠️ La file est **dans** l'en-tête, et pas en bandeau au-dessus des cartes.
 * Posée en bandeau, elle poussait tout l'écran vers le bas dès qu'une réussite
 * attendait, et remontait tout dès qu'elle repartait : **la mise en page bougeait
 * sous le pouce du juge pendant qu'il scannait.** Ici, elle apparaît et disparaît
 * sans rien déplacer.
 *
 * ⚠️ Le mot `ANNONAY` occupait la gauche. Il ne disait rien à personne : le juge
 * sait dans quelle salle il est, et l'écran n'a qu'une seule application. La
 * place qu'il prenait revient aux pastilles, qui en ont besoin — ce sont elles
 * qu'on touche.
 */
@Composable
private fun Entete(
    enAttente: Int,
    refusees: Int,
    envoiEnCours: Boolean,
    serveurJoignable: Boolean?,
    onToutEnvoyer: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
                // Pendant l'aller-retour, la pastille le DIT. Le compteur seul
                // ne bougeait pas d'un pixel tant que le serveur n'avait pas
                // répondu : le juge appuyait une deuxième fois, puis une
                // troisième, puis renonçait.
                texte = if (envoiEnCours) stringResource(R.string.envoi_en_cours)
                        else stringResource(R.string.en_attente, enAttente),
                couleur = Attention,
                fleche = true,
                travaille = envoiEnCours,
                // Un appui force l'envoi immédiat : le geste de fin de
                // compétition, avant d'éteindre les téléphones.
                onClick = onToutEnvoyer,
            )
            Spacer(Modifier.width(10.dp))
        }

        Spacer(Modifier.weight(1f))

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
    travaille: Boolean = false,
    onClick: () -> Unit,
) {
    // La flèche monte et revient tant que l'envoi est en vol. C'est le même
    // signe, animé : rien de nouveau à comprendre.
    val vol = rememberInfiniteTransition(label = "vol")
    val montee by vol.animateFloat(
        initialValue = 0f, targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "montee",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            // ⚠️ 44 dp de haut, et non la hauteur du texte. La pastille faisait
            // 26 dp : sous les 48 dp recommandés, et surtout sous ce qu'un
            // pouce vise à côté d'un logo. Le fond, lui, ne grandit pas — c'est
            // la ZONE qui grandit, pas le dessin.
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(couleur.copy(alpha = if (travaille) 0.24f else 0.14f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        if (fleche) {
            // La flèche dit que la pastille est actionnable. Sans elle, un
            // compteur ressemble à un simple affichage.
            Icon(
                Icons.Filled.ArrowUpward, contentDescription = null,
                tint = couleur,
                modifier = Modifier
                    .size(13.dp)
                    .graphicsLayer { if (travaille) translationY = montee },
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

    // L'atterrissage. La carte **encaisse** le scan : elle se tasse d'un cheveu
    // puis revient en dépassant. Sans ce sursaut, les couleurs changeaient et
    // rien ne se passait — le scan n'avait aucun poids.
    val atterrissage = remember { Animatable(1f) }
    var etaitFait by remember { mutableStateOf(fait) }
    LaunchedEffect(fait) {
        if (fait == etaitFait) return@LaunchedEffect
        etaitFait = fait
        // Seulement à l'arrivée. Un « Effacer » remet trois cartes à zéro d'un
        // coup : les faire toutes rebondir ensemble serait du bruit.
        if (fait) {
            atterrissage.snapTo(0.96f)
            atterrissage.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.42f,
                                       stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    // La carte remplie est plus haute que la carte vide. C'était un saut sec.
    val hauteurMin by animateDpAsState(
        targetValue = if (fait) 132.dp else 116.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "hauteurCarte",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = hauteurMin)
            .graphicsLayer { scaleX = atterrissage.value; scaleY = atterrissage.value }
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

            // Le contenu ne se remplace pas, il ARRIVE : le nom du grimpeur
            // monte à sa place pendant que la consigne s'efface. Les deux se
            // croisent en 60 ms — assez pour voir le remplacement, pas assez
            // pour l'attendre.
            AnimatedContent(
                targetState = when {
                    fait -> EtatEtape.FAITE
                    active -> EtatEtape.ACTIVE
                    else -> EtatEtape.ATTENTE
                },
                transitionSpec = {
                    (fadeIn(tween(200, delayMillis = 60)) +
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = 0.7f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) { hauteur -> hauteur / 2 })
                        .togetherWith(fadeOut(tween(110)))
                },
                label = "contenuEtape",
            ) { etat ->
                // ⚠️ Le `Column`. `AnimatedContent` empile ses enfants comme
                // une `Box` : sans lui, le nom du grimpeur et son numéro de
                // dossard — deux `Text` frères dans `contenu` — se
                // superposaient au même endroit.
                Column {
                    when (etat) {
                        EtatEtape.FAITE -> contenu()
                        EtatEtape.ACTIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.QrCodeScanner, contentDescription = null,
                                tint = Encre, modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.toucher_pour_scanner),
                                 fontFamily = Moyen, fontSize = 21.sp, color = Encre)
                        }
                        EtatEtape.ATTENTE -> Text(stringResource(R.string.ensuite),
                                                  fontFamily = Normal,
                                                  fontSize = 21.sp, color = Encre3)
                    }
                }
            }
        }
    }
}

/**
 * La dernière valeur non nulle.
 *
 * ⚠️ Sans cela, la carte du bloc virait à la **tache blanche** pendant le fondu
 * de sortie. `AnimatedContent` garde le contenu sortant à l'écran le temps de
 * l'animation, et ce contenu est une lambda : il relit l'état courant à chaque
 * image. Or l'état vient d'être remis à zéro — la pastille perdait donc son tag
 * ET sa couleur, et se redessinait blanche et vide sous les yeux du juge.
 *
 * Ici elle s'efface en gardant « ZJ1 » et son jaune, ce qui est à la fois plus
 * joli et plus honnête : c'est bien ça qu'on vient d'envoyer.
 */
@Composable
private fun retenu(valeur: String?): String? {
    // Volontairement PAS un `mutableStateOf` : écrire dans un état observable
    // pendant la composition force un second passage pour rien. Ici l'écran est
    // de toute façon recomposé quand `valeur` change — c'est ce changement même
    // qui nous amène ici.
    val memoire = remember { Memoire(valeur) }
    if (valeur != null) memoire.valeur = valeur
    return memoire.valeur
}

private class Memoire(var valeur: String?)

/** Les trois états d'une étape. Une carte n'en a jamais deux à la fois. */
private enum class EtatEtape { FAITE, ACTIVE, ATTENTE }

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
    confirmation: Confirmation?,
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
    // Pendant la confirmation, le bouton garde SA couleur — celle du circuit
    // qui vient d'être validé — alors que le reste de l'écran s'est déjà remis
    // à zéro (le ViewModel efface 500 ms après l'envoi, l'animation en dure
    // 700). Sans cela, la coche apparaissait sur un bouton en train de blanchir.
    val enVol = confirmation != null
    val teinte = confirmation?.couleur ?: couleur

    val fond by animateColorAsState(
        targetValue = if (pret || enVol) teinte else CarteAttente,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fondBouton",
    )

    // La lueur qui respirait s'embrase le temps de la validation : 8 dp de
    // relief au repos, 48 dp au moment de l'impulsion.
    val relief = if (confirmation != null)
        8f + impulsion(confirmation.progression, 0.10f) * 40f else lueur
    val ampleur = if (confirmation != null)
        1f + impulsion(confirmation.progression, 0.10f) * 0.022f else echelle

    Box(
        Modifier
            .fillMaxWidth()
            // ⚠️ Hauteur FIXE, et non `heightIn(min = ...)`. Avec une hauteur qui
            // suit le contenu, « ENVOYER » tombait bas dans sa case : il n'y
            // avait pas d'espace en dessous à répartir, seulement la marge.
            .height(112.dp)
            .then(
                if (pret || enVol) Modifier
                    .graphicsLayer { scaleX = ampleur; scaleY = ampleur }
                    .shadow(relief.dp, RoundedCornerShape(28.dp),
                            ambientColor = teinte, spotColor = teinte)
                else Modifier,
            )
            .clip(RoundedCornerShape(28.dp))
            .background(fond)
            .then(
                if (pret || enVol) Modifier
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
                        if (pret || enVol) encreSur(teinte).copy(alpha = 0.18f)
                        else Color(0xFF1E252F),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("3", fontFamily = Titre, fontSize = 11.sp,
                     color = if (pret || enVol) encreSur(teinte) else Color(0xFF3C4652))
            }
            Spacer(Modifier.width(9.dp))
            Text(
                stringResource(
                    when {
                        enVol -> R.string.envoyee
                        pret -> R.string.pret
                        else -> R.string.send
                    },
                ).uppercase(),
                fontFamily = Fort, fontSize = 11.sp, letterSpacing = 2.2.sp,
                color = if (pret || enVol) encreSur(teinte).copy(alpha = 0.7f) else Encre3,
            )
        }

        if (confirmation != null) {
            // La coche. Elle entre en dépassant sa taille — c'est ce que l'œil
            // lit comme « c'est fait », là où un fondu se lit « ça charge ».
            Icon(
                Icons.Filled.Check, contentDescription = null,
                tint = encreSur(teinte),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(60.dp)
                    .graphicsLayer {
                        scaleX = confirmation.rebond
                        scaleY = confirmation.rebond
                        alpha = (confirmation.rebond * 2.2f).coerceAtMost(1f) *
                            // Elle s'efface sur les 150 dernières millisecondes,
                            // en même temps que le bouton retourne au gris.
                            (1f - (confirmation.progression - 0.78f) / 0.22f)
                                .coerceIn(0f, 1f)
                    },
            )
        } else if (pret) {
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
    // L'arrivée de la ligne du haut.
    //
    // ⚠️ Elle n'est PAS pilotée par la confirmation du bouton, bien que les
    // deux se déclenchent au même geste : la ligne est ajoutée au journal une
    // image avant que la confirmation ne démarre, et elle apparaissait donc en
    // clair le temps d'une image avant de repartir de zéro — un clignotement.
    //
    // Une `Animatable` recréée à 0 pour chaque nouvelle première ligne n'a pas
    // ce défaut : la toute première image la lit déjà transparente.
    val premiere = validations.firstOrNull()
    val cle = premiere?.let { "${'$'}{it.heure}|${'$'}{it.grimpeur}|${'$'}{it.bloc}" }
    val entree = remember(cle) { Animatable(0f) }
    LaunchedEffect(cle) {
        entree.animateTo(1f, spring(dampingRatio = 0.75f,
                                    stiffness = Spring.StiffnessMediumLow))
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.dernieres_validations).uppercase(),
            fontFamily = Fort, fontSize = 10.sp, letterSpacing = 2.2.sp,
            color = Encre2,
        )
        Spacer(Modifier.height(10.dp))
        validations.forEachIndexed { rang, v ->
            val couleur = couleurDeCircuit(v.couleur) ?: Encre2
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        // La ligne qu'on vient d'écrire descend se poser en
                        // tête de liste. C'est le reçu : le geste laisse une
                        // trace visible, pas seulement un bouton qui a clignoté.
                        if (rang == 0) Modifier.graphicsLayer {
                            alpha = entree.value
                            translationY = (1f - entree.value) * -34f
                        } else Modifier,
                    )
                    .padding(vertical = 7.dp),
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
