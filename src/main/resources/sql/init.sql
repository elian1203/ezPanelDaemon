DELIMITER $
BEGIN
NOT ATOMIC

set
@users_exists =
(SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = "Users");


set
@servers_exists =
(SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = "Servers");

set
@properties_exists =
(SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = "GlobalProperties");

IF
@users_exists = 0 THEN
CREATE TABLE Users
(
    userId       int PRIMARY KEY AUTO_INCREMENT,
    username     varchar(255)  NOT NULL,
    email        varchar(255) NULL,
    password     varchar(1000) NOT NULL,
    passwordDate varchar(10)   NOT NULL,
    permissions  varchar(1000) NULL
);

INSERT INTO Users(username, email, password, passworddate, permissions)
VALUES ('admin', 'admin@local', 'qEUqmzQpHhxmV34hIwaLTA==', '2000-12-03', '*');
END IF;

IF
@servers_exists = 0 THEN
CREATE TABLE Servers
(
    serverId          int PRIMARY KEY AUTO_INCREMENT,
    name              varchar(255) NOT NULL,
    port              int          NOT NULL,
    dateCreated       varchar(10)  NOT NULL,
    jarPath           varchar(1000) NULL,
    jarPathRelativeTo varchar(100),
    maximumMemory     int NULL,
    autostart         BOOLEAN
);

INSERT INTO Servers(name, port, dateCreated, jarPath, jarPathRelativeTo, maximumMemory, autostart)
VALUES ('Default Server', 25565, CURRENT_DATE, 'paper.jar', 'serverBase', '2048', true);
END IF;

IF
@properties_exists = 0 THEN
CREATE TABLE GlobalProperties
(
    property varchar(1000),
    value    varchar(1000)
);
END IF;

END $
DELIMITER ;
