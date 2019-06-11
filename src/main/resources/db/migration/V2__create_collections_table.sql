CREATE TABLE collections(
    id     UUID   NOT NULL CONSTRAINT collections_pkey PRIMARY KEY,
    name   text   NOT NULL,
    tracks UUID[] NOT NULL
);
