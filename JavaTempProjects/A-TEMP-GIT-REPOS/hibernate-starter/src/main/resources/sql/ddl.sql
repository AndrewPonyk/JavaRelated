create table users
(
    id bigserial primary key,
    username varchar(128) unique,
    firstname  varchar(128),
    lastname   varchar(128),
    birthday   date,
    role       varchar(32),
    info       jsonb,
    company_id integer references company
);

create table company
(
    id   serial primary key,
    name varchar(65) not null unique
);

create table profile
(
    id       bigserial
        primary key,
    user_id  bigint not null
        unique
        references users,
    street   varchar(128),
    language char(2)
);

create table all_seq
(
    table_name varchar(32) not null
        primary key,
    pk_value   bigint      not null
);


CREATE TABLE chat (
    id BIGSERIAL PRIMARY KEY ,
    name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE users_chat (
    id BIGSERIAL PRIMARY KEY ,
    user_id BIGINT REFERENCES users(id),
    chat_id BIGINT REFERENCES chat(id),
    created_at TIMESTAMP NOT NULL ,
    created_by VARCHAR(128) NOT NULL,
    UNIQUE (user_id, chat_id)
);

DROP TABLE users_chat;


CREATE TABLE company_locale (
    company_id INT NOT NULL REFERENCES company (id),
    lang CHAR(2) NOT NULL ,
    description VARCHAR(128) NOT NULL ,
    PRIMARY KEY (company_id, lang)
)