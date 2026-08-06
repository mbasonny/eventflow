-- Catalogue des événements et stock de places.
--
-- Les contraintes CHECK doublent les invariants du domaine. Ce n'est pas
-- redondant : le domaine protège le code de cette application, la base protège
-- la donnée contre tout le reste — migration ratée, script manuel, bug futur.

CREATE TABLE events (
    id         UUID         PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    venue      VARCHAR(200) NOT NULL,
    starts_at  TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ticket_categories (
    id              UUID          PRIMARY KEY,
    event_id        UUID          NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    price_amount    NUMERIC(12,2) NOT NULL,
    -- VARCHAR et non CHAR : PostgreSQL rend CHAR(3) sous le type `bpchar`, que
    -- Hibernate refuse au démarrage face à un mapping String de longueur 3.
    price_currency  VARCHAR(3)    NOT NULL,
    capacity        INTEGER       NOT NULL,
    available_seats INTEGER       NOT NULL,

    CONSTRAINT fk_ticket_categories_event
        FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,

    CONSTRAINT ck_ticket_categories_capacity_positive
        CHECK (capacity > 0),

    CONSTRAINT ck_ticket_categories_price_non_negative
        CHECK (price_amount >= 0),

    -- L'invariant central du système, gravé dans le schéma.
    CONSTRAINT ck_ticket_categories_seats_within_bounds
        CHECK (available_seats BETWEEN 0 AND capacity)
);

-- Unicité insensible à la casse, pour correspondre à la règle du domaine
-- (« Fosse » et « fosse » sont la même catégorie).
CREATE UNIQUE INDEX uq_ticket_categories_event_name
    ON ticket_categories (event_id, lower(name));

CREATE INDEX ix_ticket_categories_event_id ON ticket_categories (event_id);
CREATE INDEX ix_events_starts_at           ON events (starts_at);
