# Étape 1 — Le domaine de `event-service`

> Phase 1 de la roadmap. Couche `domain/` uniquement : aucune base, aucun
> contrôleur, aucune annotation Spring.

## Objectif

Modéliser le cœur métier du catalogue et du stock de places, sous une forme
testable en JUnit pur et indépendante de toute technologie.

La règle critique du système entier tient en une phrase : **on ne vend jamais
plus de places qu'il n'en existe**. Tout le reste du projet (Kafka, saga,
verrouillage optimiste) n'existe que pour maintenir cette règle dans un
environnement distribué. Elle est donc portée par le domaine, à l'endroit le
plus protégé.

## Ce qui a été construit

```
domain/
├── model/
│   ├── EventId.java          identifiant typé (UUID)
│   ├── CategoryId.java       identifiant typé (UUID)
│   ├── Quantity.java         entier strictement positif
│   ├── Money.java            montant + devise
│   ├── TicketCategory.java   nom, prix, capacité, stock  ← porte la règle
│   └── Event.java            racine d'agrégat
└── exception/
    ├── DomainException.java              racine des erreurs métier
    ├── InsufficientSeatsException.java
    ├── CategoryNotFoundException.java
    └── EventNotFoundException.java
```

Tests : `TicketCategoryTest`, `EventTest`, `MoneyTest`, `QuantityTest` —
**36 tests, 0,9 s**, sans contexte Spring ni base de données.

## Les décisions, et pourquoi

### 1. Value objects plutôt que types primitifs

`Money` au lieu de `BigDecimal`, `Quantity` au lieu de `int`, `EventId` au lieu
de `UUID`.

Le bénéfice est double. D'abord le compilateur : `reserveSeats(CategoryId, Quantity)`
refuse qu'on inverse deux paramètres, ce qu'une signature `(UUID, int)` accepte
sans broncher. Ensuite la localisation des règles — la validation « une quantité
est positive » est écrite **une fois**, dans le constructeur de `Quantity`, au
lieu d'être répétée dans chaque méthode qui reçoit un `int`.

`Money` refuse d'additionner des euros et des dollars. C'est le cas d'école du
*primitive obsession* : deux `BigDecimal` s'additionnent toujours, même quand le
résultat n'a aucun sens.

### 2. Les invariants sont vérifiés dans le constructeur

Un objet mal construit ne doit pas exister. `TicketCategory` garantit en
permanence `0 <= availableSeats <= capacity` ; il est donc impossible d'obtenir
une catégorie dans un état incohérent, quelle que soit la séquence d'appels.

Conséquence pratique : le code appelant n'a jamais à revérifier. Pas de
`if (category != null && category.getAvailableSeats() >= 0)` disséminé partout.

### 3. Modèle riche, pas anémique

```java
category.reserve(Quantity.of(3));          // ✅ le domaine décide
category.setAvailableSeats(97);            // ❌ n'existe pas
```

`availableSeats` est privé et sans setter. La seule façon de le modifier est
`reserve()` ou `release()`, qui appliquent les règles. Un modèle anémique — des
getters/setters et toute la logique dans un « service » — laisserait n'importe
quel appelant écrire une valeur arbitraire.

### 4. `Event` est une racine d'agrégat

On ne manipule pas une `TicketCategory` isolée : on passe par l'événement qui la
contient (`event.reserveSeats(categoryId, quantity)`). C'est ce qui garantit
qu'une réservation ne peut pas viser une catégorie appartenant à un autre
événement — la méthode lève `CategoryNotFoundException` si l'identifiant n'est
pas dans l'agrégat.

`categories()` renvoie une vue **non modifiable** : impossible d'ajouter une
catégorie en contournant `addCategory()` et son contrôle de doublon.

### 5. `create()` et `rehydrate()` sont deux choses différentes

| | `create(...)` | `rehydrate(...)` |
|---|---|---|
| Usage | nouvel objet | rechargement depuis la base |
| Identifiant | généré | fourni |
| Stock | = capacité | tel qu'il était |
| Date passée | refusée | acceptée |

Sans cette distinction, on ne pourrait pas relire un événement terminé : la
règle « la date doit être dans le futur » n'a de sens qu'à la création.

`rehydrate()` revalide malgré tout les bornes du stock — une donnée corrompue en
base doit échouer au chargement, pas plus tard au milieu d'une réservation.

### 6. Identifiants générés dans le domaine

`EventId.newId()` appelle `UUID.randomUUID()`, plutôt que de laisser la base
attribuer un identifiant auto-incrémenté.

Un agrégat est ainsi complet et valide **avant** de toucher la persistance : on
peut le construire, le tester, publier un événement Kafka le référençant, sans
avoir écrit en base. Avec une identité générée par la base, l'objet circule dans
un état incomplet jusqu'au `flush`.

### 7. L'horloge est injectée

```java
Event.create(title, venue, startsAt, clock)
```

La règle « la date de début est dans le futur » a besoin de connaître l'instant
présent. Passer une `Clock` plutôt que d'appeler `Instant.now()` rend le test
déterministe :

```java
Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
```

Sans ça, le test devrait calculer des dates relatives et pourrait échouer selon
le moment où il tourne.

### 8. Exceptions métier séparées des exceptions techniques

Toutes héritent de `DomainException`. Le `@RestControllerAdvice` de l'étape
suivante pourra traduire chaque type en statut HTTP porteur de sens
(`InsufficientSeatsException` → 409, `EventNotFoundException` → 404) et traiter
tout le reste en 500 sans jamais exposer de détail interne.

Les exceptions portent des données exploitables, pas seulement un message :
`InsufficientSeatsException.requested()` et `.available()` permettront de
remplir un `ProblemDetail` précis.

### 9. Ce qui n'a délibérément pas été fait

**Aucune protection contre la concurrence.** Deux threads peuvent lire le même
stock et réserver chacun : c'est de l'overbooking. C'est assumé à ce stade — la
phase 2 fera constater le problème en conditions réelles, la phase 3 le traitera
par verrouillage optimiste (`@Version`). Anticiper maintenant priverait la
démarche de sa démonstration.

**Pas de Lombok.** Les `record` couvrent l'essentiel de ce pour quoi on
l'utilisait, et du code explicite se relit mieux qu'une annotation qui génère
l'invisible.

## Vérifier

```bash
cd services/event-service
./mvnw test
```

Attendu : `Tests run: 36, Failures: 0, Errors: 0`, en moins d'une seconde.

Ce temps d'exécution est le bénéfice concret de l'architecture hexagonale : sans
contexte Spring ni base à démarrer, la boucle de retour est instantanée. C'est
ce qui rend viable d'avoir *beaucoup* de tests de domaine.

## Trois questions d'entretien sur cette étape

**« Pourquoi un `Money` plutôt qu'un `BigDecimal` ? »**
Parce qu'un `BigDecimal` ne porte pas sa devise : rien n'empêche d'additionner
10 € et 10 $ et d'obtenir 20 de rien du tout. `Money` rend cette erreur
impossible à la compilation pour le type, et à l'exécution pour la devise. C'est
aussi l'endroit unique où vit la règle d'arrondi — deux décimales, `HALF_UP` —
au lieu qu'elle soit dupliquée partout.

**« Qu'est-ce qu'une racine d'agrégat, concrètement ici ? »**
`Event` est la seule porte d'entrée vers ses `TicketCategory`. On ne charge ni ne
modifie une catégorie isolément. Ça garantit une frontière de cohérence : toute
règle impliquant plusieurs catégories du même événement — « l'événement est
complet si toutes ses catégories le sont » — peut être vérifiée sans risque de
lire un état partiel.

**« Pourquoi votre domaine n'a-t-il aucune annotation Spring ni JPA ? »**
Parce que les dépendances doivent pointer vers le domaine, jamais l'inverse. En
pratique ça donne trois choses : les tests tournent en millisecondes sans
contexte Spring, changer de mécanisme de persistance n'impacte pas une ligne de
logique métier, et la logique reste lisible sans connaître le framework. Le prix
à payer est un mapping explicite entre entités JPA et objets de domaine — c'est
l'étape suivante, et c'est un coût assumé.

## Suite

Étape 2 : la persistance — entités JPA, migration Flyway `V1`, adaptateur
implémentant le port `EventRepository`, et tests d'intégration Testcontainers
contre un vrai PostgreSQL 16.
