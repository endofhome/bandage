CREATE TABLE tracks(
    id UUID NOT NULL CONSTRAINT tracks_pkey PRIMARY KEY,
    metadata JSONB NOT NULL
);
