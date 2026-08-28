package com.adn.dev.climbcontest

import java.io.File
import java.io.FileOutputStream

/**
 * Les trois seules opérations de fichier dont la file a besoin.
 *
 * Isolées ici pour une raison précise : ce sont elles qui décident si une
 * réussite survit à une batterie vide. Le reste de [FileDeReussites] est de la
 * logique de liste ; c'est ici que se joue la durabilité.
 *
 * Aucune dépendance Android — juste `java.io`. Les tests tournent donc sur la
 * JVM avec un dossier temporaire, sans émulateur.
 *
 * **Deux opérations, et une seule interdite.**
 *
 * - `ajouter` écrit à la fin puis force l'écriture sur le disque. Un `append`
 *   ne peut pas laisser le fichier dans un état intermédiaire : soit la ligne
 *   est là, soit elle ne l'est pas.
 * - `remplacer` écrit un fichier voisin puis le renomme. Le renommage est
 *   atomique : à tout instant, le fichier visible est l'ancien **ou** le
 *   nouveau, jamais un mélange.
 *
 * Ce qu'on ne fait **jamais** : réécrire un fichier en place. Une interruption
 * au milieu perdrait des réussites déjà validées, et c'est précisément le
 * scénario qu'on cherche à rendre impossible.
 */
class StockageFichier(private val fichier: File) {

    /**
     * Ajoute une ligne et **attend qu'elle soit sur le disque**.
     *
     * Le `fsync` n'est pas une précaution de principe : sans lui, la ligne vit
     * dans le cache du système, et une batterie qui lâche l'emporte. C'est
     * quelques millisecondes payées une fois par réussite — à comparer aux
     * ~200 ms d'un aller-retour réseau qu'on vient justement de supprimer.
     */
    fun ajouter(ligne: String) {
        fichier.parentFile?.mkdirs()
        val prefixe = if (finitParUnSautDeLigne()) "" else "\n"
        FileOutputStream(fichier, true).use { flux ->
            flux.write((prefixe + ligne + "\n").toByteArray(Charsets.UTF_8))
            flux.flush()
            flux.fd.sync()
        }
    }

    /**
     * Le fichier se termine-t-il proprement ?
     *
     * Ce test n'est pas de la coquetterie, et il a été trouvé par un test qui
     * échouait. Une coupure d'alimentation en pleine écriture laisse une ligne
     * **tronquée, sans saut de ligne final**. Sans cette vérification, l'ajout
     * suivant se collerait à ce fragment :
     *
     *     {"ref":"r2","bib":"2"{"ref":"r3","bib":"3",...}
     *
     * Une seule ligne, illisible : on perdrait non seulement la réussite
     * interrompue — c'est inévitable — mais aussi **la suivante, parfaitement
     * valide**. Un octet lu suffit à l'éviter.
     */
    private fun finitParUnSautDeLigne(): Boolean {
        if (!fichier.exists() || fichier.length() == 0L) return true
        return java.io.RandomAccessFile(fichier, "r").use { f ->
            f.seek(f.length() - 1)
            f.read() == '\n'.code
        }
    }

    /** Les lignes non vides, dans l'ordre d'écriture. Un fichier absent en donne zéro. */
    fun lire(): List<String> =
        if (!fichier.exists()) emptyList()
        else fichier.readLines(Charsets.UTF_8).filter { it.isNotBlank() }

    /**
     * Remplace tout le contenu, de façon atomique.
     *
     * Écrit dans un fichier voisin, le synchronise, puis le renomme par-dessus.
     * Une coupure avant le renommage laisse l'ancien fichier intact ; une
     * coupure après laisse le nouveau. Il n'y a pas de troisième état.
     */
    fun remplacer(lignes: List<String>) {
        fichier.parentFile?.mkdirs()
        val provisoire = File(fichier.parentFile, "${fichier.name}.tmp")
        FileOutputStream(provisoire).use { flux ->
            if (lignes.isNotEmpty()) {
                flux.write(lignes.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
            }
            flux.flush()
            flux.fd.sync()
        }
        if (!provisoire.renameTo(fichier)) {
            // Sur certains systèmes de fichiers, renameTo échoue si la cible
            // existe. On ne se rabat PAS sur « supprimer puis renommer » : ça
            // ouvrirait exactement la fenêtre qu'on cherche à fermer.
            throw java.io.IOException("remplacement impossible de ${fichier.name}")
        }
    }

    /** Taille en octets, pour l'indicateur et les tests. */
    fun taille(): Long = if (fichier.exists()) fichier.length() else 0L
}
