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
| Leurre légendaire *(rare)* | Les 3 prochaines prises sont épiques ou mieux |
| Caisse de dynamite *(rare)* | Énorme explosion : 8 à 14 poissons |

Les objets peuvent aussi être pêchés (12 % des prises, réglable).

### Codex
`/cfish codex` (ou le livre dans le dernier slot en partie) : tous les poissons, grisés (`???`) tant qu'ils ne sont pas pêchés, puis nombre de prises, record de taille et date de première prise. Persistant d'une partie à l'autre.

## Objets de la salle d'attente (hotbar)

En rejoignant une partie, le joueur reçoit sa canne (slot 1) et son codex (slot 9). Dans la salle d'attente il a en plus :

| Slot | Objet | Clic droit |
|---|---|---|
| 2 | Tête de poisson « Choisir une île » | Liste des îles (libre / en cours / incomplète) ; cliquer sur une île rejoint ou **change d'île** tant que la partie n'a pas commencé |
| 3 | Cœur de la mer « Rôle & mode » | Choisir son rôle et voter pour le mode hardcore |
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

## Événements de journée

Deux fois par journée (réglable), un événement est annoncé par un titre à l'écran et affiché dans la barre du haut :

| Événement | Effet (par défaut) |
|---|---|
| Banc de poissons dorés | Chaque poisson vaut x2 pendant 60 s |
| Tempête | 90 s : ça mord 1,5x moins vite, mais 2x plus de poissons rares (pluie et tonnerre) |
| Brouillard | 90 s : vision réduite, poissons rares plus fréquents |
| Marée basse | 60 s : ça mord 2x plus vite et on pêche 2x plus d'objets |

Tout se règle dans la section `events` de `config.yml` (durée, poids, multiplicateurs, `enabled: false` pour en couper un).

## Boutique de Gertrude

- **5 lots** chaque journée : 4 courants, dont **un en promotion** (-30 %), et **un article rare** (`shop.rare-pool`).
- **Chaque lot ne s'achète qu'une fois** : acheté, il est « VENDU » pour toute l'équipe. Le même objet peut apparaître **jusqu'à 2 fois** dans l'étal du jour (2 lots d'appâts, 2 lots de TNT...). Réglages : `shop.single-purchase`, `shop.max-same-item`.
- **Faire changer l'étal** : 5 nouveaux articles contre une part de la cagnotte, de plus en plus cher dans la journée.
- **Améliorations permanentes** (pour toute la partie, propres au joueur qui les achète) :
  - Canne renforcée : ça mord plus vite et les poissons rares mordent plus souvent ;
  - Bottes de marin : vitesse permanente sur l'île ;
  - Grand sac : chance de remonter un poisson en plus à chaque prise.
  Deux niveaux chacune, prix dans `upgrades`.
- Gertrude parle : sa réplique change selon la situation (cagnotte maigre, palier atteint, fin de journée, événement...). Répliques modifiables dans `dialogue`.

## Niveaux et cartes de pouvoir

Chaque partie, les joueurs montent de niveau en pêchant (barre d'expérience vanilla : le chiffre est le niveau). Commun 10 XP, peu commun 18, rare 35, épique 70, légendaire 150, objet 6 ; chaque niveau demande 25 % d'XP de plus.

Au niveau 2, puis tous les 3 puis 2 niveaux (5, 7, 10, 12...), **3 cartes holographiques en 3D** apparaissent devant le joueur. Pas d'inventaire : ce sont des cartes qui flottent dans le monde, visibles seulement par lui, qui se retournent une à une et le suivent s'il bouge. Il **vise une carte** (elle grossit, son icône tourne et brille, sa description s'affiche) et **clique** (gauche ou droit) pour la prendre. La carte choisie s'envole vers lui, les autres disparaissent. Le jeu continue pendant le choix ; au bout de 30 s, la carte visée (ou une au hasard) est prise. Avec le resource pack, les cartes ont un cadre illustré selon leur rareté.

| Carte | Rareté | Effet |
|---|---|---|
| Œil de lynx | Rare | +25 % de poissons rares (cumulable x4) |
| Main leste | Commune | Ça mord 20 % plus vite (x3) |
| Double paie | Épique | 15 % de chances qu'un poisson vaille x2 (x3) |
| Fièvre de l'or | Épique | Tous tes poissons x2 pendant 60 s |
| Érudit | Commune | +30 % d'XP (x3) |
| Double hameçon | Rare | 12 % de chances de remonter 2 poissons (x3) |
| Chasseur de trésors | Commune | +50 % d'objets pêchés (x3) |
| Sablier de poche | Commune | +45 s à la journée pour toute l'équipe |
| Pourboire | Commune | +8 % du palier dans la cagnotte |
| Ami de Gertrude | Rare | -15 % sur tes achats (x3) |
| Mains d'artificier | Commune | Filet et harpon rechargés 30 % plus vite (x2) |
| Appel des abysses | Rare | Tes 2 prochaines prises sont épiques ou mieux |

`/cfish pouvoirs` affiche ton niveau et tes cartes. Réglages : `levels` et `cards` dans `config.yml` (nombre de cartes, rythme, délai, pouvoirs désactivables).

## Rôles, hardcore et difficulté

Dans la salle d'attente, l'objet **Rôle & mode** ouvre le choix du rôle (sinon il est tiré au sort au départ) :

| Rôle | Bonus |
|---|---|
| Harponneur | 1 harpon offert chaque journée, harpon rechargé 2x plus vite |
| Fileteur | 1 filet offert chaque journée, ses filets remontent +1 poisson et sèchent plus vite |
| Artificier | 1 dynamite offerte chaque journée, ses explosions remontent +2 poissons |

Le même menu permet de voter pour le **mode hardcore** (majorité des joueurs) : paliers x1,5, journées 20 % plus courtes, pas de report de cagnotte, poissons payés x1,25, classement à part.

À partir de la **journée 5**, les paliers montent plus vite (x1,15 de plus chaque jour) mais les poissons rapportent aussi plus (+5 % par jour). Réglages : `difficulty` et `hardcore`.

## Succès, titres et classement

- 13 succès : compléter chaque rareté du codex puis le codex entier, premier légendaire, poisson à taille maximale, record du serveur, 3 journées sans rien acheter, 5 et 10 journées, 5 journées en hardcore...
- Chaque succès débloque un **titre** affiché devant le pseudo (au-dessus de la tête et dans la liste des joueurs) pendant les parties. `/cfish titres` (ou le bouton dans le codex) pour le choisir.
- **Classement** : `/cfish top`, ou un panneau flottant placé par un admin (`/cfish admin setboard <arène>` ou le bouton dans les outils admin) : meilleures journées, hardcore, plus grosses prises. Données dans `leaderboard.yml`.

## Ambiance et fin de partie

- Barre du haut : temps, cagnotte/palier, ce qu'il manque (« encore 300$ ») ou le bonus, l'événement en cours. Titre et son quand le palier est atteint, alerte si un achat refait passer la cagnotte dessous.
- Corne de brume au début de journée, cloche à la fin, mouettes et vagues, musique (`ambience`, toutes les clés de son sont modifiables, y compris par des sons de ton resource pack).
- Fin de partie : récapitulatif (classement des joueurs, MVP, plus grosse prise) et feu d'artifice.

## Resource pack : interfaces illustrées

Avec le pack, la boutique de Gertrude et l'interface de Francis ont un fond illustré : **portrait 3D** du PNJ, **bulle de dialogue** avec ses répliques, étal en bois, tableau d'infos chez Francis. Les joueurs sans le pack gardent les interfaces classiques (le plugin le détecte tout seul).

**Activer (le plugin héberge le pack lui-même)** dans `config.yml` :

```yaml
resource-pack:
  enabled: true
  self-host:
    port: 8164                 # à ouvrir en TCP sur ta box / chez ton hébergeur
    public-address: "cle4nergang.fr"
```

puis `/cfish admin reload`. Le pack est envoyé quand un joueur rejoint une partie (`send-on: join` pour l'envoyer à la connexion). Il s'**ajoute** aux autres packs du serveur, il ne les remplace pas.
Si tu ne peux pas ouvrir de port : téléverse `plugins/CleanFishing/CleanFishing-pack.zip` (lien de téléchargement direct) et mets ce lien dans `resource-pack.url`. Si tu fusionnes le pack dans le pack principal de ton serveur, mets `assume-loaded: true`.

**Modifier les images** : au premier démarrage, le pack est copié dans `plugins/CleanFishing/pack/`. Les fonds sont dans `assets/cleanfishing/textures/gui/` (`shop.png`, `francis.png`, 176 x 222 pixels, la taille exacte de l'interface). Retouche-les dans n'importe quel logiciel de dessin en gardant les cases aux mêmes endroits (voir `pack-src/preview/*_guide.png` dans le projet : cases cliquables en rose, lignes de texte en bleu), puis `/cfish admin reload` : le pack est re-zippé et renvoyé.
Pour repartir des images d'origine d'une nouvelle version du plugin, supprime le dossier `plugins/CleanFishing/pack/`.

Les portraits livrés sont rendus à partir des vrais skins de Gertrude et Francis (`pack-src/skins/`).

**Régénérer les portraits avec d'autres skins** (dans le projet, avec Python 3 et Pillow) : mets le skin de Gertrude dans `pack-src/skins/gertrude.png` et celui de Francis dans `pack-src/skins/francis.png` (PNG 64x64, téléchargé sur namemc.com par exemple), puis `python pack-src/build_pack.py`. Le portrait 3D est recalculé à partir du skin, et le dossier `pack/` est mis à jour (il est embarqué dans le plugin au prochain build).

## Commandes

| Commande | Rôle |
|---|---|
| `/cfish join [arène]` | Rejoindre une partie |
| `/cfish leave` | Quitter la partie |
| `/cfish codex` | Ouvrir le codex |
| `/cfish menu` | Choisir une île (utile hors partie, ou depuis ton plugin hub) |
| `/cfish titres` | Succès et choix du titre |
| `/cfish top` | Classement du serveur |
| `/cfish pouvoirs` | Ton niveau et tes cartes de pouvoir |
| `/cfish admin menu` | Ouvrir les outils admin en interface |
| `/cfish admin create <nom>` | Créer une arène |
| `/cfish admin setspawn <arène>` | Point d'apparition des joueurs (à la position du joueur) |
| `/cfish admin setlobby <arène>` | (option) salle d'attente |
| `/cfish admin setgertrude <arène> [nohut]` | Place Gertrude **et construit son cabanon** (voir plus bas) ; `nohut` = Gertrude seule |
| `/cfish admin removehut <arène>` | Retire le cabanon et rend le terrain d'origine |
| `/cfish admin setfrancis <arène>` | Position et orientation de Francis |
| `/cfish admin setradius <arène> <rayon>` | Rayon de la zone sans monstres autour du spawn |
| `/cfish admin setboard <arène>` / `removeboard <arène>` | Panneau de classement flottant au-dessus de toi |
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

Java 25 + Gradle. Le workflow `.github/workflows/build.yml` compile à chaque push ; le jar (et le resource pack seul, `CleanFishing-pack`) sont dans les artefacts de l'action. Le dossier `pack/` est zippé et embarqué dans le jar.

Mise à jour depuis une version précédente : les nouveaux réglages ont des valeurs par défaut, ton `config.yml` existant continue de fonctionner. Pour voir et modifier les nouvelles sections (`events`, `upgrades`, `hardcore`, `resource-pack`...), copie-les depuis le `config.yml` du projet ou supprime le tien pour qu'il soit régénéré. En local : `gradle build` puis `build/libs/CleanFishing-1.0.0.jar` dans `plugins/`.
