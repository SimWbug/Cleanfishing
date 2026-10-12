# CleanFishing

Mini-jeu de pêche coopératif pour Paper 26.3 (Java 25), pensé pour le serveur CleanGang.

## Le jeu

- Une partie = des **journées** de 10 min. Tous les poissons pêchés alimentent la **cagnotte commune**.
- Chaque journée a un **palier** (500, 1500, 3000, 5000, 8000, puis croissance de 35 % par jour, modifiable).
- Palier non atteint à la fin du temps : **défaite**.
- Palier atteint : on peut continuer à pêcher (la cagnotte peut dépasser le palier) ou **finir la journée plus tôt en parlant à Francis** (vote de l'équipe).
- **Gertrude** (dans le cabanon) vend des objets avec la **cagnotte du jour** : chaque achat fait donc baisser la cagnotte, donc le risque de rater le palier. La boutique change à chaque journée.
- Entre deux journées réussies, une pause de 30 s laisse la boutique ouverte ; la cagnotte restante est perdue (réglable avec `carry-over-percent`).

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

## Commandes

| Commande | Rôle |
|---|---|
| `/cfish join [arène]` | Rejoindre une partie |
| `/cfish leave` | Quitter la partie |
| `/cfish codex` | Ouvrir le codex |
| `/cfish admin create <nom>` | Créer une arène |
| `/cfish admin setspawn <arène>` | Point d'apparition des joueurs (à la position du joueur) |
| `/cfish admin setlobby <arène>` | (option) salle d'attente |
| `/cfish admin setgertrude <arène>` | Position et orientation de Gertrude |
| `/cfish admin setfrancis <arène>` | Position et orientation de Francis |
| `/cfish admin start <arène>` | Forcer le lancement |
| `/cfish admin stop <arène>` | Forcer l'arrêt |
| `/cfish admin list / info / tp / delete / reload` | Gestion |

Permissions : `cleanfishing.play` (tout le monde), `cleanfishing.admin` (op).

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

## Poissons en 3D

`fish.yml` accepte pour chaque poisson un champ `item-model` (ex. `cleangang:fish/crabe`) qui pointe vers un modèle 3D de **ton resource pack**. Tant qu'il est vide, une icône vanilla est utilisée. Les modèles 3D eux-mêmes sont à créer (Blockbench) et à ajouter au pack ; ils ne sont pas fournis par le plugin.

## Cohabitation avec FindThePoulet

CleanFishing n'a aucun point commun avec les autres plugins : nom de plugin, commande `/cfish`, permissions, clés de données, dossier `plugins/CleanFishing/`, scoreboard par joueur, PNJ marqués par une clé propre. Tous les listeners vérifient d'abord que le joueur (ou l'entité) appartient à une partie CleanFishing et ne font rien sinon. Ne mets pas un même joueur dans deux mini-jeux en même temps.

Les inventaires sont sauvegardés sur disque avant la partie et rendus après, même après un crash ou une déconnexion.

## Compilation

Java 25 + Gradle. Le workflow `.github/workflows/build.yml` compile à chaque push ; le jar est dans les artefacts de l'action. En local : `gradle build` puis `build/libs/CleanFishing-1.0.0.jar` dans `plugins/`.
