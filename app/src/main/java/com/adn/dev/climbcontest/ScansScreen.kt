package com.adn.dev.climbcontest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adn.dev.climbcontest.ui.theme.Alerte
import com.adn.dev.climbcontest.ui.theme.Attention
import com.adn.dev.climbcontest.ui.theme.CarteFaite
import com.adn.dev.climbcontest.ui.theme.Encre
import com.adn.dev.climbcontest.ui.theme.Fond
import com.adn.dev.climbcontest.ui.theme.Encre2
import com.adn.dev.climbcontest.ui.theme.Vert

/**
 * Tout ce que ce téléphone a scanné, et où ça en est.
 *
 * L'écran répond à une question posée après coup, souvent à la fin de la
 * journée : « le scan de la grimpeuse 42 sur le bloc vert 3, il est bien
 * parti ? » Jusqu'ici, rien n'y répondait — le téléphone efface sa file dès que
 * tout est acquitté, donc un téléphone qui a tout envoyé n'avait plus rien à
 * montrer.
 *
 * Le nom du grimpeur n'est pas stocké : il est retrouvé dans le catalogue
 * courant, et manque donc pour un scan d'une compétition passée. C'est
 * volontaire — voir [HistoriqueScans]. Le dossard, lui, est toujours là, et
 * c'est ce qui identifie la ligne.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScansScreen(
    scans: List<ScanJournalise>,
    catalogue: Catalogue,
    /**
     * La liste s'ouvre-t-elle déjà filtrée sur ce qui n'est pas arrivé ?
     *
     * `true` quand on vient du menu par « N en attente » : la question posée
     * était « lesquelles ne sont pas parties ? », l'écran doit y répondre tout
     * de suite. Le filtre reste visible et se retire d'un doigt.
     */
    filtreInitial: Boolean = false,
    onBack: () -> Unit,
) {
    var seulementNonArrives by remember { mutableStateOf(filtreInitial) }

    BackHandler { onBack() }

    val affiches = remember(scans, seulementNonArrives) {
        val choisis = if (seulementNonArrives) scans.filter { it.etat != EtatScan.PARTIE }
                      else scans
        choisis.asReversed()
    }
    val nonArrives = remember(scans) { scans.count { it.etat != EtatScan.PARTIE } }

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
            title = { Text(stringResource(R.string.mes_scans), fontSize = 20.sp) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = pluralStringResource(R.plurals.scans_n, scans.size, scans.size),
                fontSize = 14.sp,
                color = Encre2,
            )
            // Un filtre, pas un onglet : il n'y a qu'une liste, et on veut voir
            // d'un coup d'oeil qu'on n'en regarde qu'une partie.
            FilterChip(
                selected = seulementNonArrives,
                onClick = { seulementNonArrives = !seulementNonArrives },
                label = { Text(stringResource(R.string.filtre_non_arrives, nonArrives)) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = Encre2,
                    selectedContainerColor = Attention.copy(alpha = 0.18f),
                    selectedLabelColor = Attention,
                ),
            )
        }

        if (affiches.isEmpty()) {
            Vide(seulementNonArrives)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                items(affiches, key = { it.ref }) { scan ->
                    LigneScan(scan, catalogue)
                }
            }
        }
    }
}

/** Ce qu'on montre quand il n'y a rien à montrer. Jamais une page blanche. */
@Composable
private fun Vide(filtreActif: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = stringResource(
                if (filtreActif) R.string.scans_tout_est_parti else R.string.scans_aucun
            ),
            fontSize = 15.sp,
            color = Encre2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LigneScan(scan: ScanJournalise, catalogue: Catalogue) {
    val (couleur, libelle) = when (scan.etat) {
        EtatScan.PARTIE -> Vert to stringResource(R.string.scan_partie)
        EtatScan.EN_ATTENTE -> Attention to stringResource(R.string.scan_en_attente)
        EtatScan.REFUSEE -> Alerte to stringResource(R.string.scan_refusee)
    }
    val grimpeur = catalogue.grimpeur(scan.dossard)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(CarteFaite, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).background(couleur, CircleShape))
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = grimpeur ?: stringResource(R.string.dossard_n, scan.dossard),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Encre,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(scan.bloc, fontSize = 14.sp, color = Encre2)
                Text("  ·  ", fontSize = 14.sp, color = Encre2)
                Text(heureDe(scan.scanneLe), fontSize = 14.sp, color = Encre2)
            }
            // Le motif d'un refus est ce qui dit quoi faire : « dossard
            // inconnu » veut dire « demande a l'organisateur de l'ajouter ».
            scan.motif?.takeIf { scan.etat == EtatScan.REFUSEE }?.let {
                Text(it, fontSize = 13.sp, color = Alerte)
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(libelle, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = couleur)
            // La reference courte : ce que le juge lit a voix haute quand
            // l'organisateur la cherche dans la console.
            Text(
                text = scan.refCourte(),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Encre2.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * « 2026-11-08T09:42:03Z » devient « 10:42 ».
 *
 * ⚠️ L'heure est stockée en **UTC** — c'est ce que le serveur attend. La couper
 * à la main donnerait 09:42 à un juge qui a scanné à 10:42 : en novembre, la
 * France est à UTC+1. On repasse donc explicitement en heure locale.
 *
 * Une heure illisible s'affiche telle quelle : mieux vaut une chaîne étrange
 * qu'une exception au milieu d'une liste.
 */
private fun heureDe(iso: String): String = try {
    java.time.Instant.parse(iso)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
} catch (e: Exception) {
    iso
}
