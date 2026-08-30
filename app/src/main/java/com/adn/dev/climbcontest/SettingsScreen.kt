import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adn.dev.climbcontest.BuildConfig
import com.adn.dev.climbcontest.IdentiteAppareil
import com.adn.dev.climbcontest.MainViewModel
import com.adn.dev.climbcontest.R
import com.adn.dev.climbcontest.ui.theme.Alerte
import com.adn.dev.climbcontest.ui.theme.Attention
import com.adn.dev.climbcontest.ui.theme.CarteFaite
import com.adn.dev.climbcontest.ui.theme.Encre
import com.adn.dev.climbcontest.ui.theme.Fond
import com.adn.dev.climbcontest.ui.theme.Encre2
import com.adn.dev.climbcontest.ui.theme.Vert
import com.adn.dev.climbcontest.ui.theme.CarteAttente

/**
 * Les réglages, et l'état de ce qui n'est pas encore parti.
 *
 * L'écran n'avait ni titre ni flèche de retour : on n'en sortait que par le
 * geste système, et rien ne disait où on était. Il ouvrait aussi sur le numéro
 * de version — l'information la moins utile de l'écran, en haut à gauche.
 *
 * L'ordre suit ce qu'un bénévole vient y chercher : régler la saisie, vider la
 * file en fin de compétition, vérifier à quel serveur le téléphone parle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    mainViewModel: MainViewModel,
    context: Context,
    onToutEnvoyer: () -> Unit = {},
    onRenvoyerRefusees: () -> Unit = {},
    identite: IdentiteAppareil? = null,
    onRenommer: (String) -> Unit = {},
    onVoirLesScans: () -> Unit = {},
) {
    var checked by remember { mutableStateOf(mainViewModel.autoEval) }
    var nom by remember(identite?.id) { mutableStateOf(identite?.nom.orEmpty()) }
    val enAttente by mainViewModel.enAttente.collectAsState()
    val refusees by mainViewModel.refusees.collectAsState()
    val serveurJoignable by mainViewModel.serveurJoignable.collectAsState()

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Fond,
                titleContentColor = Encre,
            ),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Encre,
                    )
                }
            },
            title = { Text(stringResource(R.string.reglages), fontSize = 20.sp) },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            Section(stringResource(R.string.reglages_saisie)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.auto_evaluate),
                            fontSize = 16.sp,
                            color = Encre,
                        )
                        // Le nom seul ne dit pas ce que ça fait. Un bénévole qui
                        // ouvre les réglages le jour J n'a pas le temps
                        // d'essayer pour voir.
                        Text(
                            text = stringResource(R.string.auto_evaluate_detail),
                            fontSize = 13.sp,
                            color = Encre2,
                        )
                    }
                    // L'interrupteur par defaut de Material 3 en sombre est
                    // presque invisible eteint : sa piste est `surfaceVariant`,
                    // a deux points de gris de la carte qui la porte. Or c'est
                    // exactement l'etat qu'un benevole doit pouvoir lire d'un
                    // coup d'oeil. On reprend les memes couleurs d'etat que le
                    // reste de l'application : vert = actif, gris = pas actif.
                    Switch(
                        checked = checked,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Encre,
                            checkedTrackColor = Vert,
                            uncheckedThumbColor = Encre2,
                            uncheckedTrackColor = CarteAttente,
                            uncheckedBorderColor = CarteAttente,
                        ),
                        onCheckedChange = {
                            checked = it
                            if (it) mainViewModel.enableAutoEval()
                            else mainViewModel.disableAutoEval()
                            mainViewModel.reset()
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Ce qui identifie ce telephone dans la console (spec 011). Le nom
            // designe un POSTE — « Mur jaune » — et pas un benevole : les
            // telephones changent de main dans la journee.
            if (identite != null) {
                Section(stringResource(R.string.reglages_telephone)) {
                    OutlinedTextField(
                        value = nom,
                        onValueChange = {
                            // On coupe a la saisie plutot que d'accepter puis
                            // tronquer en silence : le juge voit ce qui sera
                            // garde.
                            nom = it.take(IdentiteAppareil.LONGUEUR_NOM)
                            onRenommer(nom)
                        },
                        label = { Text(stringResource(R.string.nom_telephone)) },
                        placeholder = { Text(stringResource(R.string.nom_telephone_exemple)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.nom_telephone_detail),
                        fontSize = 13.sp,
                        color = Encre2,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        // Les huit premiers caracteres suffisent a distinguer
                        // vingt-cinq telephones, et se lisent a voix haute.
                        text = stringResource(R.string.identifiant_telephone,
                                              identite.id.take(8)),
                        fontSize = 12.sp,
                        color = Encre2,
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(14.dp))
                    TextButton(
                        onClick = onVoirLesScans,
                        modifier = Modifier.align(Alignment.Start),
                    ) {
                        Text(stringResource(R.string.voir_mes_scans))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Fin de compétition : s'assurer que rien ne traîne avant d'éteindre
            // les téléphones. Le bouton ne contourne pas le retrait exponentiel
            // — appuyer en boucle sur un serveur éteint ne sert à rien.
            Section(stringResource(R.string.reglages_file)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (enAttente > 0)
                            stringResource(R.string.en_attente, enAttente)
                        else stringResource(R.string.file_vide),
                        fontSize = 16.sp,
                        color = if (enAttente > 0) Attention else Encre2,
                        fontWeight = if (enAttente > 0) FontWeight.SemiBold
                                     else FontWeight.Normal,
                    )
                    Button(onClick = onToutEnvoyer, enabled = enAttente > 0) {
                        Text(stringResource(R.string.tout_envoyer))
                    }
                }

                // Les refusées. Presque toujours « ce dossard n'existe pas
                // ENCORE » : l'organisateur ajoute le participant, le juge
                // appuie ici, et la réussite repart. Sans ce bouton, elle serait
                // perdue.
                if (refusees > 0) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.refusees_n, refusees, refusees),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Alerte,
                        )
                        Button(onClick = onRenvoyerRefusees) {
                            Text(stringResource(R.string.renvoyer_refusees))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.reglages_refus_detail),
                        fontSize = 13.sp,
                        color = Encre2,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // À quel serveur ce téléphone parle. Sur place, quand un juge dit
            // « ça ne marche pas », c'est la première question qu'on lui pose —
            // et il fallait jusqu'ici démonter l'APK pour y répondre.
            Section(stringResource(R.string.reglages_serveur)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (pastille, texte) = when (serveurJoignable) {
                        true -> Vert to stringResource(R.string.serveur_ok)
                        false -> Alerte to stringResource(R.string.serveur_ko)
                        null -> Encre2 to stringResource(R.string.serveur_inconnu)
                    }
                    Box(Modifier.size(9.dp).background(pastille, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(texte, fontSize = 16.sp, color = Encre)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = BuildConfig.SERVER_URL,
                    fontSize = 13.sp,
                    color = Encre2,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.version, versionName ?: "?"),
                color = Encre2,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Un bloc de réglages : un intitulé discret, puis une carte. */
@Composable
private fun Section(titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    Text(
        text = titre.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.SemiBold,
        color = Encre2,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CarteFaite,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = contenu)
    }
}
