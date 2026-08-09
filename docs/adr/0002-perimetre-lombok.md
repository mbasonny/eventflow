# ADR 0002 — Périmètre d'usage de Lombok

*Statut : accepté — 2026-08-06*

> L'ADR 0001 (monorepo) reste à écrire.

## Contexte

Lombok est omniprésent dans les équipes Spring, et supprime un boilerplate bien
réel : les accesseurs des entités JPA représentaient à eux seuls une soixantaine
de lignes sans valeur dans `event-service`.

Mais ses annotations les plus utilisées entrent en conflit direct avec deux
choix structurants du projet :

**Avec le modèle de domaine riche.** `@Data` ou `@Setter` sur `TicketCategory`
génère un `setAvailableSeats(int)` public. L'invariant
`0 <= availableSeats <= capacity`, que tout le design protège, tombe alors en
une annotation. Un modèle riche et `@Data` sont contradictoires par construction.

**Avec le contrat JPA.** L'`equals`/`hashCode` généré par `@Data` couvre tous les
champs, y compris les collections `LAZY`. Conséquences connues : une
`LazyInitializationException` dès qu'une entité entre dans un `HashSet`, et un
`hashCode` qui change quand l'entité est modifiée — ce qui casse son appartenance
à toute collection de hachage.

Par ailleurs, le domaine ne doit dépendre d'aucune bibliothèque externe : c'est
ce qui lui permet d'être testé en JUnit pur, sans classpath applicatif.

## Décision

Lombok est adopté, avec un périmètre explicite.

**Autorisé — dans `infrastructure/` et `application/` uniquement :**

| Annotation | Usage |
|---|---|
| `@Getter(AccessLevel.PACKAGE)` | accesseurs des entités JPA |
| `@Setter(AccessLevel.PACKAGE)` | champs réellement mutables, au cas par cas |
| `@RequiredArgsConstructor` | composants Spring — reste de l'injection par constructeur |
| `@Builder` | fixtures de test |

**Interdit :**

- toute annotation Lombok sous `domain/`
- `@Data`, `@Setter` de classe, `@EqualsAndHashCode`, `@ToString` sur une entité JPA
- `@AllArgsConstructor` sur une entité — il contourne les constructeurs qui portent les règles

La dépendance est déclarée `<optional>true</optional>` : c'est un processeur
d'annotations, il ne doit fuir ni dans les dépendances transitives ni dans le
jar final. Vérifié — le jar exécutable ne contient aucune classe Lombok.

## Conséquences

**Positif**
- le boilerplate disparaît là où il n'apporte rien
- les invariants du domaine restent inatteignables depuis l'extérieur
- le domaine conserve zéro dépendance
- les accesseurs générés restent en visibilité *package* : rien ne fuit hors de
  `infrastructure.persistence`

**Négatif**
- une convention à tenir, que le compilateur ne fait pas respecter — une revue
  de code peut la relâcher
- Lombok modifie le bytecode à la compilation : il impose un greffon dans l'IDE
  et peut retarder l'adoption d'une future version de Java
- deux styles cohabitent dans la base de code — accesseurs générés côté
  infrastructure, écrits à la main côté domaine. C'est intentionnel, mais il
  faut savoir l'expliquer

**À dire en entretien**
La position défendable n'est ni « Lombok partout » ni « jamais de Lombok », mais
« Lombok là où il supprime du bruit, jamais là où il génère du code qui contredit
le design ». Concrètement : pas de `@Data` sur une entité JPA, et rien du tout
dans le domaine.

## Alternatives écartées

**Aucun Lombok.** Cohérent, mais paie un boilerplate réel dans l'infrastructure,
et se prive d'un outil que tout candidat Spring est supposé maîtriser.

**Lombok partout, `@Data` compris.** L'option la plus répandue, et celle qui
détruirait le modèle de domaine riche — soit précisément ce que ce projet cherche
à démontrer.
