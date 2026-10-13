# CleanFishing

Mini-jeu de pêche coopératif pour Paper 26.3 (Java 25), pensé pour le serveur CleanGang.

## Le jeu

- Une partie = des **journées** de 10 min. Tous les poissons pêchés alimentent la **cagnotte commune**.
- Chaque journée a un **palier** (500, 1500, 3000, 5000, 8000, puis croissance de 35 % par jour, modifiable).
- Palier non atteint à la fin du temps : **défaite**.
- Palier atteint : on peut continuer à pêcher (la cagnotte peut dépasser le palier) ou **finir la journée plus tôt en parlant à Francis** (vote de l'équipe).
- **Gertrude** (dans le cabanon) vend des objets avec la **cagnotte du jour** : chaque achat fait donc baisser la cagnotte, donc le risque de rater le palier. La boutique change à chaque journée.
- La boutique s'ouvre en clic droit sur Gertrude : portrait de la tavernière (sa tête avec son skin) avec une bulle de dialogue, 5 articles sur un étal avec leur prix (vert = achetable, rouge = cagnotte insuffisante), et un bouton **Faire changer l'étal** qui pioche 5 nouveaux articles contre une part de la cagnotte (prix croissant à chaque changement, remis à zéro chaque journée ; réglages dans `shop.reroll` de `config.yml`). L'étal est partagé : tous les joueurs voient les changements en direct.

Entre deux journées réussies, une pause de 30 s laisse la boutique ouverte ; la cagnotte restante est perdue (réglable avec `carry-over-percent`).

### Poissons
15 poissons dans `fish.yml` : vanilla (morue, saumon, tropical, globe), géants, épiques (thon, requin-baleine, cœlacanthe) et **légendaires** (crabe colossal, pieuvre abyssale, carpe dorée, rejeton du Kraken). Chaque poisson a une rareté, une valeur, une taille (la valeur varie de 75 % à 125 % selon la taille).

### Objets
| Objet | Effet |
|---|---|
| Dynamite | Lancée dans l'eau, explose (aucun bloc détruit) et remonte 3 à 8 poissons |
| Filet | Visé sur l'eau, remonte 2 à 4 poissons (recharge 20 s) |
| Harpon | Visé sur l'eau, remonte un poisson **rare ou mieux** |
| Appât | Les 5 prochaines prises mordent plus vite |
| Porte-bonheur | Les 5 prochaines prises ont plus de chances d'être rares |
| Sablier | +60 s pour la journée en cours |

Les objets peuvent aussi être pêchés (12 % des prises, réglable).

### Codex
`/cfish codex` (ou le livre dans le dernier slot en partie) : tous les poissons, grisés (`???`) tant qu'ils ne sont pas pêchés, puis nombre de prises, record de taille et date de première prise. Persistant d'une partie à l'autre.

## Objets de la salle d'attente (hotbar)

En rejoignant une partie, le joueur reçoit sa canne (slot 1) et son codex (slot 9). Dans la salle d'attente il a en plus :

| Slot | Objet | Clic droit |
|---|---|---|
| 2 | Tête de poisson « Choisir une île » | Liste des îles (libre / en cours / incomplète) ; cliquer sur une île rejoint ou **change d'île** tant que la partie n'a pas commencé |
| 6 | Étoile du Nether « Outils admin » (admins seulement, en partie) ; hors partie, même accès via le bouton « Outils admin » de la liste des îles (tête de poisson) | Liste des arènes, puis panneau : lancer, arrêter, téléportation, définir spawn / lobby / Gertrude / Francis à sa position, supprimer (Shift + clic) |
| 8 | Colorant rouge « Quitter la partie » | Quitte la partie et rend l'inventaire |

La même tête est aussi donnée **hors partie** (connexion, respawn, retour de partie) au slot 2, sans jamais remplacer un autre objet, pour rejoindre le jeu depuis le hub. Réglages dans `hotbar.hub-item` (`enabled`, `worlds` pour la limiter à certains mondes). Elle ne tombe pas à la mort et ne peut pas être jetée.

La tête et le bouton Quitter disparaissent quand la première journée commence ; les outils admin restent.

Par défaut la « tête » est un poisson tropical. Pour une vraie tête de poisson, colle la valeur base64 (champ *Value* sur minecraft-heads.com) dans `hotbar.menu-item.texture` de `config.yml`, puis `/cfish admin reload`.

## Zone de jeu sans monstres

Chaque arène a une **zone de jeu** : un disque autour de son spawn (toutes hauteurs), de rayon réglable. Les monstres hostiles (zombies, creepers, squelettes, phantoms, slimes...) n'y apparaissent pas ; animaux, villageois et PNJ ne sont pas concernés. Les monstres déjà présents sont retirés à l'ouverture de l'arène et à chaque début de journée (ceux qui ont un nom personnalisé sont épargnés). Les apparitions voulues (commande, oeuf, spawn d'un autre plugin) restent possibles.

- Rayon par défaut : `zone.default-radius` dans `config.yml` (60 blocs).
- Rayon d'une arène : `/cfish admin setradius <arène> <rayon>` (5 à 500), ou le bouton tête de zombie dans les outils admin (clic gauche +5, clic droit -5, Shift = 1 bloc).
- `zone.block-hostile-always: true` protège la zone en permanence ; `false` seulement quand une partie existe sur l'arène.

Choisis un rayon qui couvre l'île et l'eau autour : un monstre venu de l'extérieur de la zone peut s'y aventurer.

## Commandes

| Commande | Rôle |
|---|---|
| `/cfish join [arène]` | Rejoindre une partie |
| `/cfish leave` | Quitter la partie |
| `/cfish codex` | Ouvrir le codex |
| `/cfish menu` | Choisir une île (utile hors partie, ou depuis ton plugin hub) |
| `/cfish admin menu` | Ouvrir les outils admin en interface |
| `/cfish admin create <nom>` | Créer une arène |
| `/cfish admin setspawn <arène>` | Point d'apparition des joueurs (à la position du joueur) |
| `/cfish admin setlobby <arène>` | (option) salle d'attente |
| `/cfish admin setgertrude <arène> [nohut]` | Place Gertrude **et construit son cabanon** (voir plus bas) ; `nohut` = Gertrude seule |
| `/cfish admin removehut <arène>` | Retire le cabanon et rend le terrain d'origine |
| `/cfish admin setfrancis <arène>` | Position et orientation de Francis |
| `/cfish admin setradius <arène> <rayon>` | Rayon de la zone sans monstres autour du spawn |
| `/cfish admin start <arène>` | Forcer le lancement |
| `/cfish admin stop <arène>` | Forcer l'arrêt |
| `/cfish admin list / info / tp / delete / reload` | Gestion |

Permissions : `cleanfishing.play` (tout le monde), `cleanfishing.admin` (op).

## Cabanon généré automatiquement

Dans les outils admin, le bouton **Placer Gertrude + cabanon** (ou `/cfish admin setgertrude <arène>`) construit un petit cabanon de pêcheur (7×7, toit à deux pans, fenêtres, comptoir, lanternes, tonneaux, tapis) autour de ta position :

- tu te places là où Gertrude doit se tenir, **face à la direction de la porte** (l'orientation est arrondie aux 4 points cardinaux) ;
- Gertrude se retrouve derrière le comptoir, face à l'entrée ; **Francis** se place ensuite dehors avec « Placer Francis » ;
- choisis un sol plat avec ~9×9 blocs de dégagement : le volume du cabanon est vidé (arbres, plantes...) et le vide sous le sol est comblé en pierre (8 blocs max) ;
- si un coffre, panneau ou autre bloc à données gêne, rien n'est construit et le message indique où ;
- les blocs remplacés sont sauvegardés dans `plugins/CleanFishing/huts/<arène>.yml` : **Retirer le cabanon** (ou `removehut`) rend le terrain d'origine. Replacer Gertrude remplace l'ancien cabanon. Supprimer l'arène oublie la sauvegarde mais laisse le cabanon ;
- Shift + clic (ou `nohut`) place Gertrude sans rien construire. Matériaux réglables dans `hut:` de `config.yml` (`auto-build: false` pour désactiver) ;
- impossible pendant une partie sur l'arène. Aucune intégration WorldGuard : construis dans une zone où tu as le droit de modifier.

## PNJ et skins

Gertrude et Francis sont des **Mannequins** (entité à skin de joueur de Minecraft). Dans `config.yml` :

```yaml
npc:
  gertrude:
    skin-name: "UnPseudoMinecraft"   # skin résolu par le serveur
    # ou une texture exacte (mineskin.org) :
    skin-texture: "<valeur base64>"
    skin-signature: "<signature>"
```

Choisis un skin de tavernière pour Gertrude et un skin de marin / pêcheur pour Francis.

Clic droit sur Gertrude : ouvre la boutique (pendant une journée ou la pause entre deux journées). Clic droit sur Francis : ouvre son interface avec l'état de la journée (cagnotte, palier, temps restant, votes) et deux boutons, « Finir la journée » (vote, possible seulement palier atteint) et « Continuer à pêcher » (retire ton vote). Si un clic sur un PNJ ne fait rien, mets `debug.npc-clicks: true` dans `config.yml`, redémarre, clique et regarde la console du serveur.

## Poissons en 3D

`fish.yml` accepte pour chaque poisson un champ `item-model` (ex. `cleangang:fish/crabe`) qui pointe vers un modèle 3D de **ton resource pack**. Tant qu'il est vide, une icône vanilla est utilisée. Les modèles 3D eux-mêmes sont à créer (Blockbench) et à ajouter au pack ; ils ne sont pas fournis par le plugin.

## Cohabitation avec FindThePoulet

CleanFishing n'a aucun point commun avec les autres plugins : nom de plugin, commande `/cfish`, permissions, clés de données, dossier `plugins/CleanFishing/`, scoreboard par joueur, PNJ marqués par une clé propre. Tous les listeners vérifient d'abord que le joueur (ou l'entité) appartient à une partie CleanFishing et ne font rien sinon. Ne mets pas un même joueur dans deux mini-jeux en même temps.

Les inventaires sont sauvegardés sur disque avant la partie et rendus après, même après un crash ou une déconnexion.

## Compilation

Java 25 + Gradle. Le workflow `.github/workflows/build.yml` compile à chaque push ; le jar est dans les artefacts de l'action. En local : `gradle build` puis `build/libs/CleanFishing-1.0.0.jar` dans `plugins/`.
