-- Réservations.
--
-- Ce service ne connaît des événements que leurs identifiants : aucune clé
-- étrangère vers events_db, aucune jointure possible. C'est la règle « une base
-- par service », et c'est elle qui autorise à déployer les deux indépendamment.

CREATE TABLE bookings (
    id             UUID          PRIMARY KEY,
    reference      VARCHAR(16)   NOT NULL,
    user_id        VARCHAR(128)  NOT NULL,

    -- Identifiants étrangers, appartenant à event-service.
    event_id       UUID          NOT NULL,
    category_id    UUID          NOT NULL,

    quantity       INTEGER       NOT NULL,
    unit_amount    NUMERIC(12,2) NOT NULL,
    total_amount   NUMERIC(12,2) NOT NULL,
    currency       VARCHAR(3)    NOT NULL,

    status         VARCHAR(20)   NOT NULL,
    status_reason  VARCHAR(500),

    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uq_bookings_reference UNIQUE (reference),

    CONSTRAINT ck_bookings_quantity_positive
        CHECK (quantity > 0),

    CONSTRAINT ck_bookings_amounts_non_negative
        CHECK (unit_amount >= 0 AND total_amount >= 0),

    -- Le statut est stocké en texte, pas en ordinal : insérer un état au milieu
    -- de l'enum Java décalerait silencieusement toutes les lignes existantes.
    CONSTRAINT ck_bookings_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),

    -- Un état non initial doit toujours porter son motif.
    CONSTRAINT ck_bookings_reason_required
        CHECK (status NOT IN ('REJECTED', 'CANCELLED') OR status_reason IS NOT NULL)
);

-- Le cas d'usage le plus fréquent : « mes réservations », les plus récentes d'abord.
CREATE INDEX ix_bookings_user_created ON bookings (user_id, created_at DESC);
CREATE INDEX ix_bookings_event        ON bookings (event_id);
CREATE INDEX ix_bookings_status       ON bookings (status);
