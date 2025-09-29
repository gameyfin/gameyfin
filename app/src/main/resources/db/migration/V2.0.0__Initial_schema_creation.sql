-- Flyway Migration: V2.0.0
-- Purpose: Initial schema creation for Gameyfin application.

/******************************************************************************************
 * 1. Sequences (hi/lo allocation size = 50 for performance)
 ******************************************************************************************/
create sequence COMPANY_SEQ
    increment by 50;

create sequence DIRECTORY_MAPPING_SEQ
    increment by 50;

create sequence GAME_FIELD_METADATA_SEQ
    increment by 50;

create sequence GAME_FIELD_SOURCE_SEQ
    increment by 50;

create sequence GAME_SEQ
    increment by 50;

create sequence IMAGE_SEQ
    increment by 50;

create sequence LIBRARY_SEQ
    increment by 50;

create sequence USERS_SEQ
    increment by 50;

/******************************************************************************************
 * 2. Tables
 ******************************************************************************************/
create table APP_CONFIG
(
    "key"   CHARACTER VARYING(255) not null
        primary key,
    "value" CHARACTER VARYING(255)
);

create table COMPANY
(
    ID   BIGINT not null
        primary key,
    NAME CHARACTER VARYING(255),
    TYPE TINYINT,
    constraint UK4UCNYHR8I0URHWDUDFAHKOB9E
        unique (NAME, TYPE),
    check ("TYPE" BETWEEN 0 AND 1)
);

create table DIRECTORY_MAPPING
(
    ID            BIGINT not null
        primary key,
    EXTERNAL_PATH CHARACTER VARYING(255),
    INTERNAL_PATH CHARACTER VARYING(255)
        constraint UKJ3GSATFAHEWFOLSEAJ29O3KYT
            unique
);

create table IMAGE
(
    ID             BIGINT not null
        primary key,
    CONTENT_ID     CHARACTER VARYING(255),
    CONTENT_LENGTH BIGINT,
    MIME_TYPE      CHARACTER VARYING(255),
    ORIGINAL_URL   CHARACTER VARYING(255),
    TYPE           TINYINT,
    check ("TYPE" BETWEEN 0 AND 3)
);

create table LIBRARY
(
    ID         BIGINT                   not null
        primary key,
    CREATED_AT TIMESTAMP WITH TIME ZONE not null,
    NAME       CHARACTER VARYING(255),
    UPDATED_AT TIMESTAMP WITH TIME ZONE not null
);

create table GAME
(
    ID              BIGINT                   not null
        primary key,
    COMMENT         CHARACTER LARGE OBJECT,
    CREATED_AT      TIMESTAMP WITH TIME ZONE not null,
    CRITIC_RATING   INTEGER,
    DOWNLOAD_COUNT  INTEGER,
    FILE_SIZE       BIGINT,
    MATCH_CONFIRMED BOOLEAN,
    PATH            CHARACTER VARYING(255)
        constraint UK4WXN9FPXFQ8QXPSB7FY0O3NOA
            unique,
    RELEASE         TIMESTAMP WITH TIME ZONE,
    SUMMARY         CHARACTER LARGE OBJECT,
    TITLE           CHARACTER VARYING(255),
    UPDATED_AT      TIMESTAMP WITH TIME ZONE not null,
    USER_RATING     INTEGER,
    COVER_IMAGE_ID  BIGINT
        constraint UK52RQ62FLPBNTI77BYKM7UAHKQ
            unique,
    HEADER_IMAGE_ID BIGINT
        constraint UK30B16LLQV54H40XIOGP7T9P35
            unique,
    LIBRARY_ID      BIGINT,
    constraint FK6CVB43REAYSNYPI0XDY6HQTVF
        foreign key (COVER_IMAGE_ID) references IMAGE,
    constraint FK8N86NDPGKMOO7YOLX6HL8N84G
        foreign key (HEADER_IMAGE_ID) references IMAGE,
    constraint FKIUVR8XFB63T1K6T43EYYXVO2C
        foreign key (LIBRARY_ID) references LIBRARY
);

create table GAME_DEVELOPERS
(
    GAME_ID       BIGINT not null,
    DEVELOPERS_ID BIGINT not null,
    constraint FKB12PO9L2B9OJBAIHC82MM2QXB
        foreign key (DEVELOPERS_ID) references COMPANY,
    constraint FKS4IJSVPIJ53DSL143XVRGBS09
        foreign key (GAME_ID) references GAME
);

create table GAME_FEATURES
(
    GAME_ID  BIGINT not null,
    FEATURES TINYINT,
    constraint FK63XLTCT60SCIMPM06K8BHBE4A
        foreign key (GAME_ID) references GAME,
    check ("FEATURES" BETWEEN 0 AND 23)
);

create table GAME_GENRES
(
    GAME_ID BIGINT not null,
    GENRES  TINYINT,
    constraint FKDTSX09YOPD98E0LUEWRUSJD9E
        foreign key (GAME_ID) references GAME,
    check ("GENRES" BETWEEN 0 AND 25)
);

create table GAME_IMAGES
(
    GAME_ID   BIGINT not null,
    IMAGES_ID BIGINT not null
        constraint UKBDE7M3TKHIEEYBINM2ED0B6X1
            unique,
    constraint FK5YWV1DMXCM2VSQUEB7RHQ3JK9
        foreign key (IMAGES_ID) references IMAGE,
    constraint FKOWCPUCV45OX8GT28TXGVHF1AA
        foreign key (GAME_ID) references GAME
);

create table GAME_KEYWORDS
(
    GAME_ID  BIGINT not null,
    KEYWORDS CHARACTER VARYING(255),
    constraint FKMVF6HNJ7ROMQQM2EX70A9NVAC
        foreign key (GAME_ID) references GAME
);

create table GAME_PERSPECTIVES
(
    GAME_ID      BIGINT not null,
    PERSPECTIVES TINYINT,
    constraint FKHUEENG29Y1GHBRDI5QHGUXH6E
        foreign key (GAME_ID) references GAME,
    check ("PERSPECTIVES" BETWEEN 0 AND 7)
);

create table GAME_PUBLISHERS
(
    GAME_ID       BIGINT not null,
    PUBLISHERS_ID BIGINT not null,
    constraint FK49R2KB61LIJ54BQB4VNTST97N
        foreign key (GAME_ID) references GAME,
    constraint FKNGLD5ESGRBRH95J5BJF0HEF85
        foreign key (PUBLISHERS_ID) references COMPANY
);

create table GAME_THEMES
(
    GAME_ID BIGINT not null,
    THEMES  TINYINT,
    constraint FKRV351JXLIOY0A17Y5BBJJ6FW4
        foreign key (GAME_ID) references GAME,
    check ("THEMES" BETWEEN 0 AND 22)
);

create table GAME_VIDEO_URLS
(
    GAME_ID    BIGINT not null,
    VIDEO_URLS BINARY VARYING(255),
    constraint FKJKKWO8WDS086AS7B2KSLSVKM6
        foreign key (GAME_ID) references GAME
);

create table LIBRARY_DIRECTORIES
(
    LIBRARY_ID     BIGINT not null,
    DIRECTORIES_ID BIGINT not null
        constraint UKB5UM4CADBNC6UC8DVOMO81N5F
            unique,
    constraint FKFNCKIU58I9L89MLXV388DY13B
        foreign key (LIBRARY_ID) references LIBRARY,
    constraint FKJDXS58Q1IRTU0IDP6DXJHWAPM
        foreign key (DIRECTORIES_ID) references DIRECTORY_MAPPING
);

create table LIBRARY_GAMES
(
    LIBRARY_ID BIGINT not null,
    GAMES_ID   BIGINT not null
        constraint UK3E4VB9NQXPY27VMTA27GU5FY8
            unique,
    constraint FK6C71EEDM0I2N1JXDE9BOBWG5M
        foreign key (LIBRARY_ID) references LIBRARY,
    constraint FKDKKKES3DAY0WJ1QMV42KMMFDK
        foreign key (GAMES_ID) references GAME
);

create table LIBRARY_UNMATCHED_PATHS
(
    LIBRARY_ID      BIGINT not null,
    UNMATCHED_PATHS CHARACTER VARYING(255),
    constraint FKSJ51WC2LBNNXY0LKLWELI6VSB
        foreign key (LIBRARY_ID) references LIBRARY
);

create table PLUGIN_CONFIG
(
    "key"     CHARACTER VARYING(255) not null,
    PLUGIN_ID CHARACTER VARYING(255) not null,
    "value"   CHARACTER VARYING(255),
    primary key ("key", PLUGIN_ID)
);

create table PLUGIN_MANAGEMENT_ENTRY
(
    PLUGIN_ID   CHARACTER VARYING(255) not null
        primary key,
    ENABLED     BOOLEAN                not null,
    PRIORITY    INTEGER                not null,
    TRUST_LEVEL TINYINT,
    check ("TRUST_LEVEL" BETWEEN 0 AND 4)
);

create table GAME_ORIGINAL_IDS
(
    GAME_ID          BIGINT                 not null,
    ORIGINAL_IDS     CHARACTER VARYING(255),
    ORIGINAL_IDS_KEY CHARACTER VARYING(255) not null,
    primary key (GAME_ID, ORIGINAL_IDS_KEY),
    constraint FK1CSD5QD7VJT7BTTA3G7HGYBUX
        foreign key (GAME_ID) references GAME,
    constraint FKMT0XWLPWPU9NP0Q289JBAHJRY
        foreign key (ORIGINAL_IDS_KEY) references PLUGIN_MANAGEMENT_ENTRY
);

create table USERS
(
    ID               BIGINT  not null
        primary key,
    EMAIL            CHARACTER VARYING(255)
        constraint UK6DOTKOTT2KJSP8VW4D0M25FB7
            unique,
    EMAIL_CONFIRMED  BOOLEAN not null,
    ENABLED          BOOLEAN not null,
    OIDC_PROVIDER_ID CHARACTER VARYING(255),
    PASSWORD         CHARACTER VARYING(255),
    USERNAME         CHARACTER VARYING(255)
        constraint UKR43AF9AP4EDM43MMTQ01ODDJ6
            unique,
    AVATAR_ID        BIGINT
        constraint UKRSULCN2GYNJY3CDDPWMOSV881
            unique,
    constraint FK19LFLPG5SEIS4DWRM2LVJLXFV
        foreign key (AVATAR_ID) references IMAGE
);

create table GAME_FIELD_SOURCE
(
    DTYPE            CHARACTER VARYING(31) not null,
    ID               BIGINT                not null
        primary key,
    PLUGIN_PLUGIN_ID CHARACTER VARYING(255),
    USER_ID          BIGINT,
    constraint FKNJC4QSS5APFHTPWP42OAEAL5G
        foreign key (PLUGIN_PLUGIN_ID) references PLUGIN_MANAGEMENT_ENTRY,
    constraint FKSR1BGTX5XJVMAL7FEFGL982TP
        foreign key (USER_ID) references USERS
);

create table GAME_FIELD_METADATA
(
    ID         BIGINT not null
        primary key,
    UPDATED_AT TIMESTAMP WITH TIME ZONE,
    SOURCE_ID  BIGINT
        constraint UKHW6U2Y9FLWPTI57QB7K0P27BL
            unique,
    constraint FKQ4RC409TP8FUBTTM733PMJD8F
        foreign key (SOURCE_ID) references GAME_FIELD_SOURCE
);

create table GAME_FIELDS
(
    GAME_ID    BIGINT                 not null,
    FIELDS_ID  BIGINT                 not null
        constraint UK1L5OAH0UOOUV4V5A9P0PAK77X
            unique,
    FIELDS_KEY CHARACTER VARYING(255) not null,
    primary key (GAME_ID, FIELDS_KEY),
    constraint FKLNEPI7YWCI86YH21KO9WD9PYF
        foreign key (GAME_ID) references GAME,
    constraint FKT8FLOFDAPX5M746S5LW54C5B3
        foreign key (FIELDS_ID) references GAME_FIELD_METADATA
);

create table TOKEN
(
    SECRET     CHARACTER VARYING(255) not null
        primary key,
    CREATED_ON TIMESTAMP WITH TIME ZONE,
    PAYLOAD    CHARACTER VARYING(255),
    TYPE       CHARACTER VARYING(255),
    CREATOR_ID BIGINT,
    constraint FKGHOIALAPTI5JFEJ506JBB1O8Y
        foreign key (CREATOR_ID) references USERS
            on delete cascade
);

create table USER_PREFERENCE
(
    "key"   CHARACTER VARYING(255) not null,
    USER_ID BIGINT                 not null,
    "value" CHARACTER VARYING(255),
    primary key ("key", USER_ID)
);

create table USER_ROLES
(
    USER_ID BIGINT not null,
    ROLES   ENUM ('ADMIN', 'SUPERADMIN', 'USER'),
    constraint FKHFH9DX7W3UBF1CO1VDEV94G3F
        foreign key (USER_ID) references USERS
);
