# Étape 2 — La persistance de `event-service`

> Phase 1 de la roadmap, suite. Migration Flyway, entités JPA, adaptateur du
> port sortant, tests d'intégration Testcontainers.

## Objectif

Rendre le domaine persistable **sans le contaminer**. À la fin de cette étape,
`Event` et `TicketCategory` n'ont toujours aucune annotation JPA : la traduction
est assurée par une couche d'infrastructure dédiée.

## Ce qui a été construit

```
domain/port/out/
└── EventRepository.java              ← le contrat, déclaré par le domaine

infrastructure/persistence/
├── EventJpaEntity.java               entité JPA (≠ Event)
├── TicketCategoryJpaEntity.java      entité JPA (≠ TicketCategory)
├── EventJpaRepository.java           Spring Data, package-private
├── EventMapper.java                  traduction domaine ⇄ JPA
└── EventPersistenceAdapter.java      implémente EventRepository

resources/db/migration/
└── V1__create_events.sql
```

Tests : `EventPersistenceAdapterIT` — **8 tests contre un vrai PostgreSQL 16**.

## Les décisions, et pourquoi

### 1. Le port est déclaré par le domaine, implémenté par l'infrastructure

```
domain/port/out/EventRepository        ← interface
        ▲
        │ implements
infrastructure/persistence/EventPersistenceAdapter
```

C'est l'inversion de dépendance (le **D** de SOLID) rendue concrète. Le domaine
énonce *ce dont il a besoin* — sauvegarder, retrouver — sans rien savoir de qui
le fournit. La flèche de dépendance pointe vers le domaine, jamais l'inverse.

Détail qui compte : le port expose la pagination en `int page, int size` et non
en `Pageable`. Ce type appartient à Spring Data ; l'importer dans le domaine
ferait entrer le framework par la porte de derrière.

### 2. Entités JPA distinctes des objets de domaine

Deux jeux de classes qui se ressemblent, c'est du travail en plus. Ce qu'on
achète en échange :

- `Event` n'a ni constructeur sans argument, ni setters, ni mutabilité imposée
  par Hibernate — il peut protéger ses invariants
- changer le schéma (colonne technique, stratégie de chargement, découpage de
  table) ne touche aucune ligne de logique métier
- les tests de domaine tournent sans JPA au classpath

L'alternative — annoter directement les objets de domaine — est plus rapide à
écrire et se paie quand le modèle grossit : le métier finit dicté par ce
qu'Hibernate accepte.

### 3. `save()` charge avant d'écrire

```java
EventJpaEntity entity = jpaRepository.findByIdWithCategories(id)
        .map(existing -> { EventMapper.applyTo(existing, event); return existing; })
        .orElseGet(() -> EventMapper.toJpa(event));
```

On reporte l'état du domaine sur l'entité **déjà gérée** par Hibernate, au lieu
d'en construire une neuve avec le même identifiant. Construire une nouvelle
instance ferait perdre à Hibernate son suivi des modifications et déclencherait,
via `orphanRemoval`, une suppression puis une réinsertion de toutes les
catégories à chaque sauvegarde.

### 4. `JOIN FETCH` contre le problème N+1

```java
@Query("SELECT e FROM EventJpaEntity e LEFT JOIN FETCH e.categories WHERE e.id = :id")
```

La collection `categories` est en `LAZY`. Sans `JOIN FETCH`, charger 20
événements déclenche 1 requête pour les événements puis 20 requêtes pour leurs
catégories — le N+1, la cause la plus fréquente de lenteur dans une application
JPA. Ici tout arrive en une requête.

### 5. Les contraintes sont dans la base **aussi**

```sql
CONSTRAINT ck_ticket_categories_seats_within_bounds
    CHECK (available_seats BETWEEN 0 AND capacity)
```

Ce n'est pas une redondance inutile avec l'invariant du domaine. Le domaine
protège la donnée **contre cette application** ; la base la protège contre tout
le reste — un script de correction manuel, une migration ratée, un futur service
mal écrit. Les deux niveaux ont des portées différentes.

L'index unique est posé sur `lower(name)` pour coller exactement à la règle du
domaine, qui compare les noms sans tenir compte de la casse.

### 6. `Money` éclaté en deux colonnes

`price_amount NUMERIC(12,2)` + `price_currency VARCHAR(3)`.

`NUMERIC` et non `float`/`double` : les flottants binaires ne représentent pas
exactement les décimaux, et un centime perdu par arrondi sur une billetterie est
un bug comptable.

### 7. `ddl-auto=validate` a immédiatement attrapé une erreur

La première version de la migration déclarait `price_currency CHAR(3)`. Le
démarrage a échoué :

```
Schema validation: wrong column type encountered in column [price_currency]
found [bpchar (Types#CHAR)], but expecting [varchar(3) (Types#VARCHAR)]
```

PostgreSQL matérialise `CHAR(n)` sous le type interne `bpchar`, qu'Hibernate ne
considère pas équivalent à un `String` de longueur 3. Corrigé en `VARCHAR(3)`.

C'est exactement le service rendu par `validate` : l'incohérence entre le schéma
et le mapping est signalée **au démarrage**, pas au premier appel en production.
Avec `ddl-auto=update`, Hibernate aurait silencieusement tenté d'adapter la table.

### 8. Surefire et Failsafe séparés

| Plugin | Convention | Phase Maven | Docker requis |
|---|---|---|---|
| Surefire | `*Test` | `test` | non |
| Failsafe | `*IT` | `verify` | oui |

`./mvnw test` reste instantané pour la boucle de développement ; `./mvnw verify`
lance en plus les tests d'intégration. Le plugin Failsafe n'est pas branché par
défaut par `spring-boot-starter-parent`, il a fallu déclarer ses exécutions dans
le `pom.xml`.

### 9. Testcontainers plutôt qu'une base en mémoire

Le conteneur est épinglé sur `postgres:16-alpine`, la version de production.
`@ServiceConnection` injecte automatiquement l'URL, l'utilisateur et le mot de
passe du conteneur dans le contexte Spring — plus besoin de
`@DynamicPropertySource`.

Deux tests écrivent volontairement du SQL brut pour vérifier que les contraintes
de la base réagissent :

```
ERROR: new row for relation "ticket_categories" violates check constraint
       "ck_ticket_categories_seats_within_bounds"
ERROR: duplicate key value violates unique constraint
       "uq_ticket_categories_event_name"
```

Ce sont de vraies erreurs PostgreSQL. H2 n'aurait pas produit les mêmes, quand
il aurait accepté la syntaxe.

## Vérifier

```bash
cd services/event-service
./mvnw test      # 37 tests, < 1 s, sans Docker
./mvnw verify    # + 8 tests d'intégration sur PostgreSQL 16
```

## Trois questions d'entretien sur cette étape

**« Pourquoi dupliquer vos entités entre domaine et persistance ? »**
Parce que les deux répondent à des contraintes opposées. Hibernate exige un
constructeur sans argument et des champs modifiables ; un modèle de domaine
exige l'inverse pour protéger ses invariants. Les fusionner revient à laisser la
base dicter le métier. Le coût est un mapping explicite, localisé dans une seule
classe.

**« Comment évitez-vous le N+1 ? »**
Les collections sont en `LAZY` par défaut, et les requêtes qui ont besoin des
catégories les chargent explicitement en `JOIN FETCH`. Le principe : le
chargement paresseux est le bon défaut, et chaque cas d'usage déclare ce dont il
a réellement besoin. Passer tout en `EAGER` réglerait le N+1 en chargeant
systématiquement des données inutiles.

**« Pourquoi Testcontainers plutôt que H2 ? »**
Parce qu'un test doit exercer la vraie technologie. H2 n'a pas les mêmes types,
pas les mêmes contraintes, pas le même comportement transactionnel. Sur cette
étape précisément, deux tests vérifient des messages d'erreur PostgreSQL sur des
contraintes `CHECK` et un index unique fonctionnel sur `lower(name)` — H2 ne
reproduit ni l'un ni l'autre.

## Suite

Étape 3 : la couche applicative et l'API REST — ports entrants, use cases
transactionnels, DTO, `@RestControllerAdvice` traduisant les exceptions métier
en `ProblemDetail` (RFC 7807), et OpenAPI.
