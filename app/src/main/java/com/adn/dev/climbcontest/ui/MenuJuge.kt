package com.adn.dev.climbcontest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adn.dev.climbcontest.R
import com.adn.dev.climbcontest.ui.theme.Alerte
import com.adn.dev.climbcontest.ui.theme.Attention
import com.adn.dev.climbcontest.ui.theme.CarteFaite
import com.adn.dev.climbcontest.ui.theme.Encre
import com.adn.dev.climbcontest.ui.theme.Encre2
import com.adn.dev.climbcontest.ui.theme.Encre3
import com.adn.dev.climbcontest.ui.theme.Fort
import com.adn.dev.climbcontest.ui.theme.Normal

/**
 * Ce qui s'ouvre derrière le logo.
 *
 * Un **seul** point d'entrée vers tout ce qui n'est pas le geste principal.
 * Auparavant : un engrenage qui menait aux réglages, et « Mes scans » caché
 * dedans. Deux niveaux pour deux écrans, et rien qui laissait deviner que le
 * second existait.
 *
 * Les deux actions d'envoi sont **ici et pas dans les réglages** : ce sont des
 * gestes de compétition, pas des préférences. Un juge qui veut vider sa file
 * avant d'éteindre son téléphone ne doit pas passer par un écran de réglages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuJuge(
    enAttente: Int,
    refusees: Int,
    onFermer: () -> Unit,
    onMesScans: () -> Unit,
    onReglages: () -> Unit,
    onToutEnvoyer: () -> Unit,
    onRenvoyerRefusees: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onFermer,
        sheetState = rememberModalBottomSheetState(),
        containerColor = CarteFaite,
        contentColor = Encre,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {

            LigneMenu(
                icone = Icons.AutoMirrored.Filled.List,
                titre = stringResource(R.string.mes_scans),
                detail = stringResource(R.string.menu_scans_detail),
                onClick = { onFermer(); onMesScans() },
            )

            // Le rouge est réservé à ce qui demande une ACTION : une refusée ne
            // repartira pas toute seule, contrairement à une réussite en file.
            if (refusees > 0) {
                LigneMenu(
                    icone = Icons.Filled.Refresh,
                    titre = pluralStringResource(R.plurals.refusees_n, refusees, refusees),
                    detail = stringResource(R.string.reglages_refus_detail),
                    accent = Alerte,
                    onClick = { onFermer(); onRenvoyerRefusees() },
                )
            }

            LigneMenu(
                icone = Icons.Filled.ArrowUpward,
                titre = stringResource(R.string.tout_envoyer),
                detail = if (enAttente > 0)
                    stringResource(R.string.en_attente, enAttente)
                else stringResource(R.string.file_vide),
                accent = if (enAttente > 0) Attention else null,
                actif = enAttente > 0,
                onClick = { onFermer(); onToutEnvoyer() },
            )

            LigneMenu(
                icone = Icons.Filled.Settings,
                titre = stringResource(R.string.reglages),
                detail = stringResource(R.string.menu_reglages_detail),
                onClick = { onFermer(); onReglages() },
            )

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun LigneMenu(
    icone: ImageVector,
    titre: String,
    detail: String,
    accent: Color? = null,
    actif: Boolean = true,
    onClick: () -> Unit,
) {
    val encre = when {
        !actif -> Encre3
        accent != null -> accent
        else -> Encre
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = actif, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background((accent ?: Encre).copy(alpha = if (actif) 0.12f else 0.05f),
                            CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icone, contentDescription = null, tint = encre,
                 modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, fontFamily = Fort, fontSize = 16.sp, color = encre)
            Spacer(Modifier.height(2.dp))
            Text(
                detail, fontFamily = Normal, fontSize = 13.sp,
                color = if (actif) Encre2 else Encre3,
            )
        }
    }
}
