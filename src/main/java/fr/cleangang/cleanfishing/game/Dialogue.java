package fr.cleangang.cleanfishing.game;

import fr.cleangang.cleanfishing.CleanFishingPlugin;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Répliques de Gertrude et Francis selon la situation de la journée.
 * Modifiables dans config.yml (dialogue.&lt;pnj&gt;.&lt;situation&gt;) ; sinon ces textes par défaut sont utilisés.
 * Texte brut (pas de MiniMessage) : il est aussi affiché dans la bulle du resource pack.
 */
public final class Dialogue {

    private static final Map<String, List<String>> DEFAULTS = Map.ofEntries(
            Map.entry("gertrude.greet", List.of(
                    "Entre ! J'ai de quoi attraper les gros.",
                    "Du neuf sur l'étal aujourd'hui !",
                    "Dynamite, filets... tout pour la pêche !")),
            Map.entry("gertrude.poor", List.of(
                    "Les affaires sont calmes... Pêchez plus !",
                    "Cagnotte bien maigre, mon grand.")),
            Map.entry("gertrude.reached", List.of(
                    "Belle pêche ! Profites-en pour t'équiper.",
                    "Palier atteint ! Francis peut vous ramener.")),
            Map.entry("gertrude.hurry", List.of(
                    "Dépêche-toi, la journée se termine !",
                    "Plus le temps de flâner, mon grand !")),
            Map.entry("gertrude.intermission", List.of(
                    "Journée finie : c'est l'heure des achats !",
                    "Ce qui reste dans la cagnotte sera perdu.")),
            Map.entry("gertrude.event.golden-shoal", List.of("Un banc doré ! Qu'est-ce que tu fais ici ?")),
            Map.entry("gertrude.event.storm", List.of("Sale temps... les gros poissons adorent ça.")),
            Map.entry("gertrude.event.fog", List.of("On ne voit même plus le ponton...")),
            Map.entry("gertrude.event.low-tide", List.of("Marée basse ! Ça mord de partout, file !")),
            Map.entry("gertrude.hardcore", List.of("Le mode hardcore ? Vous êtes fous... j'adore.")),
            Map.entry("gertrude.thanks", List.of("Merci mon grand, bonne pêche !", "Bon choix ! Ramène-moi du gros.")),
            Map.entry("gertrude.broke", List.of("Reviens avec une cagnotte plus remplie.")),
            Map.entry("gertrude.reroll", List.of("Voyons ce que j'ai en réserve...")),
            Map.entry("gertrude.soldout", List.of("Celui-là est parti, désolée !")),
            Map.entry("francis.not-reached", List.of(
                    "Pas question de rentrer les mains vides !",
                    "Il manque encore du poisson, matelot.")),
            Map.entry("francis.reached", List.of(
                    "Palier atteint ! On rentre quand vous voulez.",
                    "Belle journée. On rentre, ou on continue ?")),
            Map.entry("francis.hurry", List.of("Le soleil se couche, faut se décider !")),
            Map.entry("francis.voted", List.of("J'attends les autres et on lève l'ancre.")));

    private Dialogue() {
    }

    /** Une réplique au hasard pour cette situation (ex. "gertrude.poor"). */
    public static String line(CleanFishingPlugin plugin, String situation, Random random) {
        List<String> lines = plugin.getConfig().getStringList("dialogue." + situation);
        if (lines.isEmpty()) {
            lines = DEFAULTS.getOrDefault(situation, List.of("..."));
        }
        return lines.get(random.nextInt(lines.size()));
    }
}
