# Étape 5 — Persistance et appel synchrone résilient

> Phase 2, suite. `booking-service` sait désormais réserver — en appelant
> `event-service` en HTTP, avec Resilience4j.

## Objectif

Faire fonctionner la réservation de bout en bout **en synchrone**, et rendre
visibles les faiblesses de ce choix. Ce n'est pas une étape qu'on optimisera :
c'est celle qu'on remplacera en phase 3, une fois qu'on aura constaté pourquoi.

## Ce qui a été construit

```
domain/port/out/
├── BookingRepository.java      persistance
└── EventCatalogPort.java       ce qu'on attend du catalogue

application/service/
├── BookingCommandService.java  orchestration — PAS @Transactional
└── BookingQueryService.java    @Transactional(readOnly = true)

infrastructure/
├── client/EventServiceClient.java     RestClient + @Retry + @CircuitBreaker
├── config/RestClientConfiguration.java timeouts explicites
├── config/EventServiceProperties.java  @ConfigurationProperties validé
├── persistence/                        entité JPA, mapper, adaptateur
└── rest/                               contrôleur, DTO, ApiExceptionHandler

resources/db/migration/V1__create_bookings.sql
```

**40 tests** : 29 de domaine, 6 d'orchestration, 5 de traduction HTTP.

## Le contrat d'API

| Méthode | Chemin | Réponse |
|---|---|---|
| `POST` | `/api/v1/bookings` | 201, statut `CONFIRMED` ou `REJECTED` |
| `GET` | `/api/v1/bookings/{id}` | 200 / 404 |
| `GET` | `/api/v1/bookings/by-reference/{ref}` | 200 / 404 |
| `GET` | `/api/v1/bookings?userId=&page=&size=` | 200 |
| `POST` | `/api/v1/bookings/{id}/cancellation` | 200 / 404 / 409 |

## Les décisions, et pourquoi

### 1. Trois issues, pas deux

C'est **la** décision structurante de cette étape :

| Réponse d'event-service | Exception | Pour le disjoncteur |
|---|---|---|
| 404 | `CategoryUnavailableException` | ignorée |
| 409 | `SeatsUnavailableException` | **ignorée** |
| 5xx / timeout | `EventCatalogUnavailableException` | comptée comme échec |

```yaml
resilience4j.circuitbreaker.instances.event-service:
  ignore-exceptions:
    - com.eventflow.booking.domain.exception.SeatsUnavailableException
    - com.eventflow.booking.domain.exception.CategoryUnavailableException
```

Sans cette configuration, un concert complet génère des milliers de 409
parfaitement légitimes, le taux d'échec dépasse 50 %, et **le disjoncteur s'ouvre
sur un service en pleine santé** — coupant les réservations de tous les autres
concerts. C'est le piège classique de Resilience4j, et il vient d'une confusion
entre « le service a échoué » et « le service a répondu non ».

Même logique côté retry : réessayer un refus de stock est inutile, les places ne
réapparaîtront pas en 200 ms, et on triple la charge pour rien.

### 2. Le service applicatif n'est pas `@Transactional`

Il fait un appel réseau. Envelopper l'ensemble dans une transaction
maintiendrait une connexion PostgreSQL ouverte pendant tout l'appel HTTP —
jusqu'à 3 secondes. Sous charge, le pool se vide en attendant un service
distant, et la base devient le goulot d'une panne qui ne la concerne pas.

**Règle : jamais d'appel réseau dans une transaction base.** Chaque `save()` est
atomique de lui-même, Spring Data rendant `SimpleJpaRepository.save`
transactionnel.

> **Piège évité de justesse.** La première version déclarait une méthode
> `@Transactional protected persist(...)` appelée depuis `book()`. Ça ne marche
> pas : l'auto-invocation ne traverse pas le proxy Spring, l'annotation est
> purement décorative. On croit avoir une transaction, on n'en a aucune. Pour
> qu'une annotation transactionnelle s'applique, l'appel doit venir de
> l'extérieur du bean.

### 3. Les timeouts, avant même le disjoncteur

```java
HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
JdkClientHttpRequestFactory.setReadTimeout(properties.readTimeout())
```

Un client sans timeout est la panne la plus insidieuse du distribué : si le
serveur accepte la connexion puis ne répond jamais, l'appelant attend
indéfiniment, ses threads s'accumulent, son pool sature. Un service sain tombe à
cause d'un voisin lent — l'effet domino.

Les deux délais couvrent des pannes différentes : le premier protège de l'hôte
injoignable, le second du serveur qui accepte puis se tait.

### 4. Le repli ne doit pas avaler les exceptions métier

```java
private CategorySnapshot rethrowOrDegrade(Throwable cause) {
    if (cause instanceof SeatsUnavailableException e) throw e;
    if (cause instanceof CategoryUnavailableException e) throw e;
    throw new EventCatalogUnavailableException("...");
}
```

Resilience4j route vers le repli **toute** exception non ignorée. Un repli qui
transformerait un 409 en « service indisponible » ferait recevoir au client un
503 au lieu d'un 409 — et il réessaierait indéfiniment une requête qui ne peut
pas aboutir.

### 5. La trace est persistée avant l'appel qui modifie le stock

```
1. findCategory()          prix officiel
2. save(PENDING)           trace persistée
3. reserveSeats()          modifie le stock distant
4. confirm() / reject()
5. save(issue)
```

Si l'étape 3 échoue, il reste une réservation `PENDING` exploitable pour un
diagnostic, plutôt qu'aucune trace.

**Mais la faille demeure**, et elle est volontairement visible : entre 3 et 5, un
crash laisse des places retenues pour une réservation restée `PENDING`. Personne
ne les libérera. Il n'existe pas de transaction ACID entre deux services — c'est
exactement ce que le Transactional Outbox traitera en phase 4.

Un test le documente : `should_leave_the_booking_pending_when_reservation_call_fails`.

### 6. 201 même pour un `REJECTED`

La ressource « réservation » a bien été créée et reste consultable ; c'est son
*statut* qui porte l'issue métier. Renvoyer 409 confondrait « je n'ai rien créé »
avec « j'ai créé une trace de ton refus ».

En revanche, un catalogue injoignable renvoie **503 avec `Retry-After`** : la
requête était correcte, le service est momentanément incapable. Répondre 500
laisserait croire à un bug de `booking-service` alors que la panne est ailleurs.

### 7. `EnumType.STRING`, jamais `ORDINAL`

Avec l'ordinal, insérer un état au milieu de l'énumération Java réinterpréterait
silencieusement toutes les lignes déjà écrites. Une `CANCELLED` deviendrait
`CONFIRMED` sans qu'aucune erreur ne soit levée. La contrainte `CHECK` en base
double la protection.

### 8. Un bug dans mes tests, pas dans le code

`ArgumentCaptor` capturait la même instance de `Booking` deux fois : le service
mute l'objet entre les deux `save()`, si bien que les deux valeurs capturées
désignaient le même objet dans son état final. Le test affirmait voir `PENDING`
puis `REJECTED` — il voyait `REJECTED` deux fois.

Corrigé en enregistrant le statut **au moment** de l'appel, via un
`willAnswer(...)`. À retenir : un captor capture des *références*, pas des
instantanés.

## Vérifier

```bash
cd services/booking-service
./mvnw test      # 40 tests
```

## Trois questions d'entretien sur cette étape

**« Pourquoi ne pas mettre `@Transactional` sur la méthode qui réserve ? »**
Parce qu'elle appelle un service distant. La transaction garderait une connexion
base ouverte pendant l'appel HTTP ; sous charge, le pool se vide en attendant un
tiers, et la base tombe à cause d'une panne réseau. Les écritures sont donc
découpées en transactions courtes qui n'englobent aucun appel réseau. Et
attention à l'auto-invocation : une méthode `@Transactional` appelée depuis la
même classe ne passe pas par le proxy, l'annotation ne fait rien.

**« Comment évitez-vous que le disjoncteur s'ouvre à tort ? »**
En distinguant un refus métier d'une panne. Un 409 « plus de places » signifie
que le service distant fonctionne parfaitement — il répond, et il répond non. Si
on le compte comme un échec, un concert complet suffit à ouvrir le circuit et à
couper les réservations de tous les autres. Ces exceptions sont donc déclarées
en `ignore-exceptions`, côté disjoncteur comme côté retry.

**« Qu'est-ce qui ne va pas dans cette architecture ? »**
Deux choses. Le couplage de disponibilité : si `event-service` tombe,
`booking-service` ne peut plus rien faire alors qu'il est parfaitement sain —
Resilience4j traite le symptôme, pas la cause. Et l'absence d'atomicité : entre
la réservation distante et l'enregistrement local, un crash laisse des places
retenues que personne ne libérera. L'asynchrone supprime le premier problème,
l'Outbox le second.

## Suite

Étape 6 — les deux expériences de la phase 2, à faire tourner pour de vrai :

1. **Couper `event-service`** et observer le disjoncteur s'ouvrir via
   `/actuator/circuitbreakers`, puis se refermer au redémarrage.
2. **50 requêtes concurrentes sur 10 places** et constater l'overbooking — la
   démonstration qui justifie le verrouillage optimiste de la phase 3.
