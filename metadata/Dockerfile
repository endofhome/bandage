FROM postgres:11.3
ENV POSTGRES_USER bandage_user
ENV POSTGRES_PASSWORD bandage_user_pwd
ENV POSTGRES_DB bandage
COPY ./init-postgres-metadata.sql /docker-entrypoint-initdb.d/
