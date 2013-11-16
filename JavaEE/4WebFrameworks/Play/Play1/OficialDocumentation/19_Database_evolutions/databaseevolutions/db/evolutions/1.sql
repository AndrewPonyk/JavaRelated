# --- !Ups
CREATE TABLE user(
   id int primary key ,
   name varchar(20)
);

# --- !Downs
   DROP TABLE user;
