# Étape 3 — Couche applicative et API REST de `event-service`

> Fin de la phase 1. Ports entrants, services applicatifs, DTO, gestion
> d'erreurs RFC 7807, OpenAPI.

## Objectif

Rendre le service utilisable de l'extérieur, sans qu'aucune règle métier ne
s'échappe du domaine. À la fin de cette étape, la phase 1 est validée.

## Ce qui a été construit

```
domain/port/in/
├── CreateEventUseCase.java     + CreateEventCommand
├── UpdateEventUseCase.java     + UpdateEventCommand
├── DeleteEventUseCase.java
└── FindEventsUseCase.java      + EventPage

application/service/
├── EventCommandService.java    @Transactional
└── EventQueryService.java      @Transactional(readOnly = true)

infrastructure/
├── config/ClockConfiguration.java
└── rest/
    ├── EventController.java
    ├── ApiExceptionHandler.java
    ├── dto/          6 records
    └── mapper/EventRestMapper.java
```

Tests : `EventControllerTest` — **11 tests MockMvc**. Total du service :
**48 tests unitaires + 8 d'intégration**.

## Le contrat d'API

| Méthode | Chemin | Réponse |
|---|---|---|
| `POST` | `/api/v1/events` | 201 + `Location` |
| `GET` | `/api/v1/events?page&size` | 200, page triée par date |
| `GET` | `/api/v1/events/{id}` | 200 / 404 |
| `PUT` | `/api/v1/events/{id}` | 200 / 404 |
| `DELETE` | `/api/v1/events/{id}` | 204 / 404 |
| `GET` | `/api/v1/events/{id}/categories` | 200 |
| `GET` | `/api/v1/events/{id}/categories/{cid}/availability` | 200 / 404 |

Swagger UI : `http://localhost:8081/swagger-ui.html`

## Les décisions, et pourquoi

### 1. Le flux traverse trois traductions

```
JSON  ──▶  CreateEventRequest  ──▶  CreateEventCommand  ──▶  Event
          (contrat HTTP)          (contrat du cas d'usage)   (domaine)
```

Chaque frontière a son type. Ça paraît verbeux, et c'est ce qui permet de faire
évoluer le contrat public sans toucher au domaine — renommer un champ JSON reste
une modification du mapper.

Le `record` de commande est déclaré **dans l'interface du port** : la commande
fait partie du contrat du cas d'usage, pas d'une couche technique.

### 2. `@Transactional` sur le service applicatif

Ni sur le contrôleur — il n'a pas à connaître l'existence d'une base — ni sur le
repository, dont la granularité serait trop fine : un cas d'usage qui charge,
modifie puis sauvegarde doit être atomique dans son ensemble.

Les lectures sont en `readOnly = true`. Ce n'est pas décoratif : Hibernate passe
la session en `FlushMode.MANUAL` et cesse de conserver un instantané de chaque
entité pour la détection de modifications. Moins de mémoire, moins de travail au
commit, et aucune écriture accidentelle possible.

### 3. Commandes et requêtes dans deux services

`EventCommandService` (écritures) et `EventQueryService` (lectures) sont séparés
parce que leurs sémantiques transactionnelles diffèrent et qu'ils évolueront
différemment — les lectures accueilleront des projections et du cache, sans que
les règles d'écriture bougent.

En revanche les trois écritures restent groupées : elles partagent une même
raison de changer, les règles d'écriture du catalogue. Trois classes d'une
méthode chacune seraient de la cérémonie, pas du SRP.

### 4. Le contrôleur dépend des ports, jamais des implémentations

```java
private final CreateEventUseCase createEvent;   // interface du domaine
```

`EventCommandService` est *package-private* : le contrôleur ne peut pas la voir,
même en le voulant. Le compilateur fait respecter l'architecture, pas la
discipline.

### 5. `ProblemDetail` (RFC 7807) plutôt qu'un format maison

```json
{
  "type": "https://eventflow.dev/problems/validation-error",
  "title": "Requête invalide",
  "status": 400,
  "detail": "Un ou plusieurs champs sont invalides",
  "instance": "/api/v1/events",
  "timestamp": "2026-08-09T01:54:38Z",
  "errors": {
    "title": "Le titre est obligatoire",
    "startsAt": "La date de début doit être dans le futur",
    "categories": "Au moins une catégorie de places est requise"
  }
}
```

Un standard, servi en `application/problem+json`, avec des extensions typées
(`errors`, `requested`, `available`). Le détail par champ permet à un formulaire
d'afficher l'erreur au bon endroit, au lieu d'un message global inexploitable.

Un seul `@RestControllerAdvice` par service : les contrôleurs n'ont aucun
`try/catch` et le format est homogène sur toute l'API.

### 6. Le choix des codes de statut

| Situation | Code | Raison |
|---|---|---|
| Validation échouée | 400 | la requête est mal formée |
| JSON illisible | 400 | erreur du client |
| Paramètre du mauvais type | 400 | ex. UUID invalide |
| Ressource absente | 404 | — |
| **Stock insuffisant** | **409** | la requête est valide, c'est l'état courant qui l'empêche |
| Inattendu | 500 | journalisé, jamais détaillé au client |

Le 409 est le point à savoir défendre : rien n'est mal formé dans la requête, la
même requête pourrait réussir plus tard si des places se libèrent. C'est un
conflit d'état, pas une erreur de syntaxe.

### 7. Un bug trouvé par le test manuel, pas par les tests automatiques

En appelant l'API réelle, un corps JSON malformé renvoyait **500**. Les tests
MockMvc ne l'avaient pas vu : ils envoyaient tous du JSON valide.

`HttpMessageNotReadableException` tombait dans le catch-all `Exception` → 500.
Un serveur qui s'accuse d'une faute commise par le client, et une alerte de
supervision déclenchée pour rien.

Corrigé par deux gestionnaires dédiés — `HttpMessageNotReadableException` et
`MethodArgumentTypeMismatchException` — et deux tests de non-régression.

La leçon vaut d'être retenue : **une suite de tests verte prouve que ce qu'on a
pensé à tester fonctionne**. Lancer l'application et l'appeler pour de vrai
reste indispensable.

### 8. L'horloge est un bean

`ClockConfiguration` expose `Clock.systemUTC()`. Le service applicatif la reçoit
par injection et la transmet au domaine. En test, un `Clock.fixed(...)` rend les
règles temporelles déterministes.

### 9. Pagination : enveloppe maison

`PagedResponse<T>` plutôt que le `Page` de Spring Data. Renvoyer un `Page`
exposerait une structure interne au framework, dont la sérialisation JSON n'est
pas stable entre versions majeures — le contrat public d'une API ne doit pas
dépendre d'un détail de bibliothèque.

## Vérifier

```bash
docker compose up -d postgres
cd services/event-service
./mvnw verify                     # 48 tests unitaires + 8 d'intégration
./mvnw spring-boot:run
```

Puis `http://localhost:8081/swagger-ui.html`.

Vérifications faites en réel sur l'API démarrée :

| Cas | Attendu | Obtenu |
|---|---|---|
| POST valide | 201 + `Location` | ✅ |
| POST invalide | 400 détaillé par champ | ✅ |
| JSON malformé | 400 | ✅ |
| UUID invalide | 400 | ✅ |
| Événement inconnu | 404 | ✅ |
| Disponibilité | 200 | ✅ |
| Liste paginée | 200 | ✅ |

## Trois questions d'entretien sur cette étape

**« Pourquoi un DTO plutôt que l'entité JPA dans le contrôleur ? »**
Trois raisons. Le couplage : le contrat public suivrait le schéma de la base, et
renommer une colonne casserait les clients. La sécurité : la désérialisation
directe dans une entité ouvre le *mass assignment*, où un client renseigne un
champ qu'on n'attendait pas. La sérialisation : une entité porte des
associations paresseuses qui déclenchent des requêtes, voire une exception, au
moment du rendu JSON.

**« Où placez-vous `@Transactional`, et pourquoi ? »**
Sur le service applicatif, jamais sur le contrôleur ni le repository. Le cas
d'usage est l'unité d'atomicité : charger, appliquer la règle, sauvegarder doit
réussir ou échouer d'un bloc. Sur le repository, la granularité serait trop
fine ; sur le contrôleur, elle ferait entrer une préoccupation de persistance
dans la couche web et maintiendrait la transaction ouverte pendant la
sérialisation.

**« Pourquoi 409 et pas 400 pour un stock insuffisant ? »**
Parce que la requête est parfaitement valide — syntaxe correcte, champs
conformes. Ce qui empêche de la satisfaire, c'est l'état courant de la
ressource, et cet état peut changer : la même requête réussirait si des places
se libéraient. 400 dirait au client « corrige ta requête », alors que le message
juste est « réessaie, ou demande moins de places ».

## Phase 1 — critères de validation

| Critère | État |
|---|---|
| Swagger UI liste tous les endpoints | ✅ 7 opérations |
| `mvn verify` vert | ✅ 48 + 8 tests |
| POST invalide → 400 structuré | ✅ `ProblemDetail` détaillé par champ |
| Aucun import Spring ni JPA sous `domain/` | ✅ |

## Suite

Phase 2 : `booking-service`, et l'appel synchrone vers `event-service` — dont on
constatera les limites (couplage de disponibilité, overbooking sous concurrence)
avant de passer à l'événementiel en phase 3.
