# Étape 4 — Le domaine de `booking-service`

> Début de la phase 2. Nouveau service, machine à états de la réservation, et
> l'endpoint de réservation manquant côté `event-service`.

## Objectif

Poser le deuxième service et modéliser le cycle de vie d'une réservation. Le
cœur de l'étape est la **machine à états** : c'est elle qui rendra la saga
fiable en phase 4, et c'est l'artefact le plus parlant en entretien.

## Ce qui a été construit

### Côté `event-service` — l'endpoint qui manquait

`booking-service` doit pouvoir demander des places. Ajouté :

```
ReserveSeatsUseCase                                   port entrant
POST /api/v1/events/{id}/categories/{cid}/reservations
     → 200 disponibilité mise à jour
     → 404 événement ou catégorie inconnu
     → 409 stock insuffisant
```

Le service applicatif se contente d'orchestrer : charger l'agrégat, appeler
`event.reserveSeats(...)`, sauvegarder. Aucune comparaison de stock dans le
service — c'est `TicketCategory.reserve()` qui décide.

Point à retenir : ce port ne changera pas en phase 3. Seul l'adaptateur entrant
change — un contrôleur REST aujourd'hui, un consumer Kafka demain. C'est le
bénéfice concret de l'hexagonal, et il se voit ici pour la première fois.

### Côté `booking-service` — le domaine

```
domain/model/
├── BookingStatus.java      la machine à états
├── Booking.java            racine d'agrégat
├── BookingId.java          identifiant propre
├── BookingReference.java   référence lisible : EF-7K2M9QX4
├── UserId.java
├── EventId.java            ← identifiant ÉTRANGER
├── CategoryId.java         ← identifiant ÉTRANGER
├── Money.java              value objects redéfinis
└── Quantity.java
domain/exception/
├── DomainException.java
└── InvalidBookingTransitionException.java
```

Tests : `BookingTest` + `BookingStatusTest` — **29 tests, sans Spring**.

## Les décisions, et pourquoi

### 1. La machine à états est déclarative

```java
ALLOWED.put(PENDING,   EnumSet.of(CONFIRMED, REJECTED, CANCELLED));
ALLOWED.put(CONFIRMED, EnumSet.of(CANCELLED));
ALLOWED.put(REJECTED,  EnumSet.noneOf(BookingStatus.class));
ALLOWED.put(CANCELLED, EnumSet.noneOf(BookingStatus.class));
```

Le graphe complet tient en quatre lignes lisibles d'un coup d'œil, au lieu d'être
dispersé en `if` dans le code appelant. Toute transition non déclarée est refusée
par défaut — le comportement sûr.

Il n'existe **aucun `setStatus()`**. Confirmer, rejeter, annuler sont des
opérations métier, et toutes passent par un point unique :

```java
private void transitionTo(BookingStatus target, String reason, Clock clock) {
    if (!status.canTransitionTo(target)) {
        throw new InvalidBookingTransitionException(status, target);
    }
    ...
}
```

Concentrer la vérification là garantit qu'une transition ajoutée plus tard ne
pourra pas l'oublier.

### 2. Pourquoi ça compte vraiment : Kafka livre au moins une fois

Ce n'est pas une précaution théorique. En phase 3, un consumer qui traite un
message puis crashe avant de committer son offset **retraitera le même message**.
Un `payment.succeeded` rejoué tentera de confirmer une réservation déjà annulée.

Ici, cette tentative lève une exception au lieu de corrompre l'état. Le test qui
le prouve :

```java
should_reject_confirming_a_cancelled_booking()
```

C'est la première brique de l'idempotence, avant même d'avoir écrit une ligne de
Kafka.

### 3. Le montant total est calculé, jamais reçu

```java
unitPrice.times(quantity)
```

Le client envoie une quantité, pas un prix. Accepter un montant venu de
l'extérieur laisserait n'importe qui décider de ce qu'il paie — la faille est
classique et la parade est de calculer côté serveur, à partir du prix officiel
récupéré auprès d'`event-service`.

### 4. Référence lisible ≠ identifiant technique

| | Usage |
|---|---|
| `BookingId` | UUID, clé technique, jointures |
| `BookingReference` | `EF-7K2M9QX4`, imprimé sur le billet, dicté au téléphone |

L'alphabet exclut `I`, `O`, `0` et `1` : ces caractères se confondent à la
lecture, et une référence sert précisément à être lue par un humain.

### 5. Identifiants étrangers, sans fabrique

`EventId` et `CategoryId` existent dans `booking-service`, mais **sans**
`newId()`. Ce service ne crée jamais d'événement, il ne fait que le désigner.
Retirer la fabrique rend l'erreur impossible plutôt qu'improbable.

### 6. Les value objects sont dupliqués, délibérément

`Money`, `Quantity`, `EventId` existent dans les deux services. Une bibliothèque
partagée supprimerait la duplication — et recouplerait deux services qu'on a
justement séparés : changer `Money` obligerait à redéployer les deux, ce qui
détruit la déployabilité indépendante, seul critère qui définit un microservice.

La duplication est le prix assumé de l'autonomie. C'est un arbitrage à savoir
défendre : quelques dizaines de lignes recopiées contre un monolithe distribué.

### 7. La résilience est configurée avant d'être utilisée

`spring-cloud-starter-circuitbreaker-resilience4j` **5.0.2**, via le BOM Spring
Cloud **2025.1.2** — la ligne alignée sur Boot 4. La ligne
`resilience4j-spring-boot3` seule n'a pas de version ciblant Boot 4 ; le starter
Spring Cloud la ramène en transitif, ce qui rend les annotations disponibles sur
une combinaison officiellement supportée.

Le disjoncteur utilise une fenêtre **par nombre d'appels** et non temporelle :
un test qui envoie 10 requêtes obtient un résultat déterministe, là où une
fenêtre de 10 secondes rendrait le test dépendant de la vitesse de la machine.

## Vérifier

```bash
cd services/booking-service
./mvnw test      # 29 tests, sans Docker
```

## Trois questions d'entretien sur cette étape

**« Pourquoi une machine à états plutôt qu'un simple champ statut ? »**
Parce que le champ seul n'interdit rien. Avec un `setStatus()`, n'importe quel
appelant peut faire passer une réservation annulée à confirmée. Le graphe rend
les transitions invalides impossibles, et le fait au seul endroit qui les
connaisse. C'est ce qui sauve la saga quand Kafka rejoue un message.

**« Vous dupliquez `Money` entre deux services, ce n'est pas du mauvais code ? »**
C'est un arbitrage explicite. En factoriser dans une bibliothèque partagée
recouple les cycles de déploiement : une modification de `Money` impose de
rebuilder et redéployer les deux services. On perd la déployabilité
indépendante, qui est la définition même d'un microservice. Quelques dizaines de
lignes dupliquées coûtent moins cher qu'un monolithe distribué. La ligne rouge
serait de partager de la *logique métier*, pas des types techniques stables.

**« Pourquoi calculer le montant côté serveur ? »**
Parce que tout ce qui vient du client est une donnée hostile jusqu'à preuve du
contraire. Si le montant total transitait dans la requête, un client pourrait
réserver trois places à un centime. Le serveur reçoit une quantité, récupère le
prix officiel auprès du service propriétaire du catalogue, et multiplie.

## Suite

Étape 5 : la persistance, l'appel synchrone vers `event-service` avec
`RestClient`, et Resilience4j en action — timeout, retry, disjoncteur, repli.
Puis les deux expériences de la phase 2 : couper `event-service` pour voir le
circuit s'ouvrir, et lancer 50 requêtes concurrentes sur 10 places pour
constater l'overbooking.
