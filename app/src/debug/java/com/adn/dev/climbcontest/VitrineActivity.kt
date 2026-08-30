package com.adn.dev.climbcontest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.adn.dev.climbcontest.ui.EcranJuge
import com.adn.dev.climbcontest.ui.theme.ClimbContestTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La vitrine : l'ecran du juge pilote a la main, sans camera ni serveur.
 *
 * DEBUG UNIQUEMENT. Elle existe parce qu'un emulateur ne scanne pas de QR code
 * et qu'on ne peut donc pas VOIR le resultat d'un scan autrement. Elle rejoue le
 * meme enchainement que `Server.submit()`, delai de 500 ms compris.
 *
 *   adb shell am start -n com.adn.dev.climbcontest.debug/com.adn.dev.climbcontest.VitrineActivity
 */
class VitrineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ClimbContestTheme {
                val portee = rememberCoroutineScope()
                var dossard by remember { mutableStateOf<String?>(null) }
                var grimpeur by remember { mutableStateOf<String?>(null) }
                var bloc by remember { mutableStateOf<String?>(null) }
                var couleur by remember { mutableStateOf<String?>(null) }
                var validations by remember { mutableIntStateOf(0) }
                var journal by remember { mutableStateOf(listOf<Validation>()) }
                var rang by remember { mutableIntStateOf(0) }

                // Des noms VOLONTAIREMENT fictifs, empruntes au vocabulaire de
                // l'escalade. Le depot est public et le classeur du club
                // contient des noms de mineurs : un patronyme plausible tire au
                // hasard finirait un jour par designer quelqu'un de reel.
                val noms = listOf("Camille Réglette", "Yanis Bidoigt",
                                  "Lou Dülfer", "Noé Magnésie")
                val blocs = listOf("ZJ1" to "Jaune", "ZV3" to "Vert", "ZB2" to "Bleu",
                                   "ZM4" to "Mauve", "ZR1" to "Rouge", "ZN2" to "Noir")

                EcranJuge(
                    dossard = dossard,
                    grimpeur = grimpeur,
                    bloc = bloc,
                    couleurDuBloc = couleur,
                    enAttente = 3,
                    refusees = 1,
                    historique = journal,
                    validations = validations,
                    serveurJoignable = true,
                    onScanGrimpeur = {
                        dossard = (42 + rang).toString()
                        grimpeur = noms[rang % noms.size]
                    },
                    onScanBloc = {
                        val (tag, teinte) = blocs[rang % blocs.size]
                        bloc = tag
                        couleur = teinte
                    },
                    onEnvoyer = {
                        journal = (listOf(Validation(
                            grimpeur = grimpeur ?: "?",
                            bloc = bloc ?: "?",
                            heure = "1%d:0%d".format(rang % 10, rang % 6),
                            couleur = couleur,
                        )) + journal).take(5)
                        validations++
                        portee.launch {
                            delay(500)
                            rang++
                            dossard = null; grimpeur = null
                            bloc = null; couleur = null
                        }
                    },
                    onEffacer = { dossard = null; grimpeur = null; bloc = null; couleur = null },
                    onMenu = {},
                    onToutEnvoyer = {},
                )
            }
        }
    }
}
