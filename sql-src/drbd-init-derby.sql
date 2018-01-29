-- linstor security subsystem tables
SET ISOLATION SERIALIZABLE;

-- Database schema for linstor
CREATE SCHEMA LINSTOR;
SET SCHEMA LINSTOR;

-- Security configuration
CREATE TABLE SEC_CONFIGURATION
(
    ENTRY_KEY VARCHAR(24) NOT NULL PRIMARY KEY
        CONSTRAINT SEC_CONF_CHKKEY CHECK (UPPER(ENTRY_KEY) = ENTRY_KEY AND LENGTH(ENTRY_KEY) >= 3),
    ENTRY_DSP_KEY VARCHAR(24) NOT NULL,
    ENTRY_VALUE VARCHAR(24) NOT NULL,
        CONSTRAINT SEC_CONF_CHKDSPKEY CHECK (UPPER(ENTRY_DSP_KEY) = ENTRY_KEY)
);

-- Identities / accounts
CREATE TABLE SEC_IDENTITIES
(
    IDENTITY_NAME VARCHAR(24) NOT NULL PRIMARY KEY
        CONSTRAINT SEC_ID_CHKNAME CHECK (UPPER(IDENTITY_NAME) = IDENTITY_NAME AND LENGTH(IDENTITY_NAME) >= 3),
    IDENTITY_DSP_NAME VARCHAR(24) NOT NULL,
    PASS_SALT CHAR(16) FOR BIT DATA,
    PASS_HASH CHAR(64) FOR BIT DATA,
    ID_ENABLED BOOLEAN NOT NULL DEFAULT TRUE,
    ID_LOCKED BOOLEAN NOT NULL DEFAULT TRUE,
        CONSTRAINT SEC_ID_CHKDSPNAME CHECK (UPPER(IDENTITY_DSP_NAME) = IDENTITY_NAME)
);

-- Object types & domains
CREATE TABLE SEC_TYPES
(
    TYPE_NAME VARCHAR(24) NOT NULL PRIMARY KEY
        CONSTRAINT SEC_TYPES_CHKNAME CHECK (UPPER(TYPE_NAME) = TYPE_NAME AND LENGTH(TYPE_NAME) >= 3),
    TYPE_DSP_NAME VARCHAR(24) NOT NULL,
    TYPE_ENABLED BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT SEC_TYPES_CHKDSPNAME CHECK (UPPER(TYPE_DSP_NAME) = TYPE_NAME)
);

-- Roles
CREATE TABLE SEC_ROLES
(
    ROLE_NAME VARCHAR(24) NOT NULL PRIMARY KEY
        CONSTRAINT SEC_ROLES_CHKNAME CHECK (UPPER(ROLE_NAME) = ROLE_NAME AND LENGTH(ROLE_NAME) >= 3),
    ROLE_DSP_NAME VARCHAR(24) NOT NULL,
    DOMAIN_NAME VARCHAR(24) NOT NULL,
    ROLE_ENABLED BOOLEAN NOT NULL DEFAULT TRUE,
    ROLE_PRIVILEGES BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (DOMAIN_NAME) REFERENCES SEC_TYPES(TYPE_NAME),
    CONSTRAINT SEC_ROLES_CHKDSPNAME CHECK (UPPER(ROLE_DSP_NAME) = ROLE_NAME)
);

-- Identities - roles assignments
CREATE TABLE SEC_ID_ROLE_MAP
(
    IDENTITY_NAME VARCHAR(24) NOT NULL,
    ROLE_NAME VARCHAR(24) NOT NULL,
    PRIMARY KEY (IDENTITY_NAME, ROLE_NAME),
    FOREIGN KEY (IDENTITY_NAME) REFERENCES SEC_IDENTITIES(IDENTITY_NAME) ON DELETE CASCADE,
    FOREIGN KEY (ROLE_NAME) REFERENCES SEC_ROLES(ROLE_NAME) ON DELETE CASCADE
);

-- Valid access types
CREATE TABLE SEC_ACCESS_TYPES
(
    ACCESS_TYPE_NAME VARCHAR(24) NOT NULL PRIMARY KEY
        CONSTRAINT SEC_ACCESS_TYPES_CHKNAME CHECK (UPPER(ACCESS_TYPE_NAME) = ACCESS_TYPE_NAME),
    ACCESS_TYPE_VALUE SMALLINT NOT NULL UNIQUE
);

-- Type enforcement rules
CREATE TABLE SEC_TYPE_RULES
(
    DOMAIN_NAME VARCHAR(24) NOT NULL,
    TYPE_NAME VARCHAR(24) NOT NULL,
    ACCESS_TYPE SMALLINT NOT NULL,
    PRIMARY KEY (DOMAIN_NAME, TYPE_NAME),
    FOREIGN KEY (DOMAIN_NAME) REFERENCES SEC_TYPES(TYPE_NAME) ON DELETE CASCADE,
    FOREIGN KEY (TYPE_NAME) REFERENCES SEC_TYPES(TYPE_NAME) ON DELETE CASCADE,
    FOREIGN KEY (ACCESS_TYPE) REFERENCES SEC_ACCESS_TYPES(ACCESS_TYPE_VALUE) ON DELETE RESTRICT
);

-- Identities - default role assignments
CREATE TABLE SEC_DFLT_ROLES
(
    IDENTITY_NAME VARCHAR(24) NOT NULL PRIMARY KEY,
    ROLE_NAME VARCHAR(24) NOT NULL,
    FOREIGN KEY (IDENTITY_NAME, ROLE_NAME) REFERENCES SEC_ID_ROLE_MAP(IDENTITY_NAME, ROLE_NAME)
        ON DELETE CASCADE
);

-- Object proection
CREATE TABLE SEC_OBJECT_PROTECTION
(
    OBJECT_PATH VARCHAR(512) NOT NULL PRIMARY KEY,
    CREATOR_IDENTITY_NAME VARCHAR(24) NOT NULL,
    OWNER_ROLE_NAME VARCHAR(24) NOT NULL,
    SECURITY_TYPE_NAME VARCHAR(24) NOT NULL,
    CONSTRAINT FK_IDENTITY
        FOREIGN KEY (CREATOR_IDENTITY_NAME) REFERENCES SEC_IDENTITIES(IDENTITY_NAME) ON DELETE RESTRICT,
    CONSTRAINT FK_ROLE
        FOREIGN KEY (OWNER_ROLE_NAME) REFERENCES SEC_ROLES(ROLE_NAME) ON DELETE RESTRICT,
    CONSTRAINT FK_SEC_TYPE
        FOREIGN KEY (SECURITY_TYPE_NAME) REFERENCES SEC_TYPES(TYPE_NAME) ON DELETE RESTRICT
);

-- Access control lists
CREATE TABLE SEC_ACL_MAP
(
    OBJECT_PATH VARCHAR(512) NOT NULL,
    ROLE_NAME VARCHAR(24) NOT NULL,
    ACCESS_TYPE SMALLINT NOT NULL,
    PRIMARY KEY (OBJECT_PATH, ROLE_NAME),
    FOREIGN KEY (OBJECT_PATH) REFERENCES SEC_OBJECT_PROTECTION(OBJECT_PATH) ON DELETE CASCADE,
    FOREIGN KEY (ROLE_NAME) REFERENCES SEC_ROLES(ROLE_NAME) ON DELETE RESTRICT,
    FOREIGN KEY (ACCESS_TYPE) REFERENCES SEC_ACCESS_TYPES(ACCESS_TYPE_VALUE) ON DELETE RESTRICT
);

-- linstor objects

CREATE TABLE NODES
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    NODE_NAME VARCHAR(255) NOT NULL PRIMARY KEY
        CONSTRAINT NODES_CHKNAME CHECK (UPPER(NODE_NAME) = NODE_NAME AND LENGTH(NODE_NAME) >= 2),
    NODE_DSP_NAME VARCHAR(255) NOT NULL,
    NODE_FLAGS BIGINT NOT NULL,
    NODE_TYPE INT NOT NULL,
    CONSTRAINT NODES_CHKDSPNAME CHECK (UPPER(NODE_DSP_NAME) = NODE_NAME)
);

CREATE TABLE NODE_NET_INTERFACES
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    NODE_NAME VARCHAR(255) NOT NULL,
    NODE_NET_NAME VARCHAR(255) NOT NULL,
    NODE_NET_DSP_NAME VARCHAR(255) NOT NULL,
    INET_ADDRESS VARCHAR(45) NOT NULL,
    PRIMARY KEY (NODE_NAME, NODE_NET_NAME),
    FOREIGN KEY (NODE_NAME) REFERENCES NODES(NODE_NAME) ON DELETE CASCADE
);

CREATE TABLE SATELLITE_CONNECTIONS
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    NODE_NAME VARCHAR(255) NOT NULL,
    NODE_NET_NAME VARCHAR(255) NOT NULL,
    TCP_PORT SMALLINT NOT NULL,
    INET_TYPE VARCHAR(5) NOT NULL,
    PRIMARY KEY (NODE_NAME),
    FOREIGN KEY (NODE_NAME, NODE_NET_NAME) REFERENCES NODE_NET_INTERFACES(NODE_NAME, NODE_NET_NAME)
        ON DELETE CASCADE,
    CONSTRAINT STLT_CHK_PORT_RANGE CHECK (TCP_PORT > 0 AND TCP_PORT < 65536),
    CONSTRAINT STLT_CHK_TYPE CHECK (INET_TYPE = 'PLAIN' OR INET_TYPE = 'SSL')
);

CREATE TABLE RESOURCE_DEFINITIONS
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    RESOURCE_NAME VARCHAR(48) NOT NULL,
    RESOURCE_DSP_NAME VARCHAR(48) NOT NULL,
    TCP_PORT INTEGER NOT NULL,
    RESOURCE_FLAGS BIGINT NOT NULL,
    SECRET VARCHAR(20) NOT NULL,
    TRANSPORT_TYPE VARCHAR(40) NOT NULL,
    PRIMARY KEY (RESOURCE_NAME),
    CONSTRAINT RSC_DFN_CHKNAME CHECK (UPPER(RESOURCE_NAME) = RESOURCE_NAME AND LENGTH(RESOURCE_NAME) >= 3),
    CONSTRAINT RSC_DFN_CHKDSPNAME CHECK (UPPER(RESOURCE_DSP_NAME) = RESOURCE_NAME),
    CONSTRAINT RSC_DFN_CHK_PORT_RANGE CHECK (TCP_PORT > 0 AND TCP_PORT < 65536),
    CONSTRAINT RSC_DFN_CHK_TRANSPORT_TYPE CHECK (TRANSPORT_TYPE = 'IP' OR TRANSPORT_TYPE = 'RDMA'
        OR TRANSPORT_TYPE = 'RoCE')
);

CREATE TABLE RESOURCES
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    NODE_NAME VARCHAR(255) NOT NULL,
    RESOURCE_NAME VARCHAR(48) NOT NULL,
    NODE_ID INT NOT NULL,
    RESOURCE_FLAGS BIGINT NOT NULL,
    PRIMARY KEY (NODE_NAME, RESOURCE_NAME),
    FOREIGN KEY (NODE_NAME) REFERENCES NODES(NODE_NAME) ON DELETE CASCADE,
    FOREIGN KEY (RESOURCE_NAME) REFERENCES RESOURCE_DEFINITIONS(RESOURCE_NAME) ON DELETE CASCADE
);

CREATE TABLE STOR_POOL_DEFINITIONS
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    POOL_NAME VARCHAR(32) NOT NULL PRIMARY KEY
        CONSTRAINT STOR_POOL_CHKNAME CHECK (UPPER(POOL_NAME) = POOL_NAME AND LENGTH(POOL_NAME) >= 3),
    POOL_DSP_NAME VARCHAR(32) NOT NULL,
    CONSTRAINT STOR_POOL_CHKDSPNAME CHECK (UPPER(POOL_DSP_NAME) = POOL_NAME)
);

CREATE TABLE NODE_STOR_POOL
(
    UUID CHAR(16) FOR BIT DATA NOT NULL UNIQUE,
    NODE_NAME VARCHAR(255) NOT NULL,
    POOL_NAME VARCHAR(32) NOT NULL,
    DRIVER_NAME VARCHAR(256) NOT NULL,
    PRIMARY KEY (NODE_NAME, POOL_NAME),
    FOREIGN KEY (NODE_NAME) REFERENCES NODES(NODE_NAME) ON DELETE CASCADE,
    FOREIGN KEY (POOL_NAME) REFERENCES STOR_POOL_DEFINITIONS(POOL_NAME) ON DELETE CASCADE
);

CREATE TABLE VOLUME_DEFINITIONS
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    RESOURCE_NAME VARCHAR(48) NOT NULL,
    VLM_NR INT NOT NULL,
    VLM_SIZE BIGINT NOT NULL,
    VLM_MINOR_NR INT NOT NULL UNIQUE,
    VLM_FLAGS BIGINT NOT NULL,
    PRIMARY KEY (RESOURCE_NAME, VLM_NR),
    FOREIGN KEY (RESOURCE_NAME) REFERENCES RESOURCE_DEFINITIONS(RESOURCE_NAME) ON DELETE CASCADE
);

CREATE TABLE VOLUMES
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    NODE_NAME VARCHAR(255) NOT NULL,
    RESOURCE_NAME VARCHAR(48) NOT NULL,
    VLM_NR INT NOT NULL,
    STOR_POOL_NAME VARCHAR(32) NOT NULL,
    BLOCK_DEVICE_PATH VARCHAR(255), -- null == diskless
    META_DISK_PATH VARCHAR(255),  -- null == internal
    VLM_FLAGS BIGINT NOT NULL,
    PRIMARY KEY (NODE_NAME, RESOURCE_NAME, VLM_NR),
    FOREIGN KEY (NODE_NAME, RESOURCE_NAME) REFERENCES RESOURCES(NODE_NAME, RESOURCE_NAME) ON DELETE CASCADE,
    FOREIGN KEY (RESOURCE_NAME, VLM_NR) REFERENCES VOLUME_DEFINITIONS(RESOURCE_NAME, VLM_NR) ON DELETE CASCADE,
    FOREIGN KEY (STOR_POOL_NAME) REFERENCES STOR_POOL_DEFINITIONS(POOL_NAME) ON DELETE CASCADE
);


CREATE TABLE NODE_CONNECTIONS
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    NODE_NAME_SRC VARCHAR(255) NOT NULL,
    NODE_NAME_DST VARCHAR(255) NOT NULL,
    PRIMARY KEY (NODE_NAME_SRC, NODE_NAME_DST),
    FOREIGN KEY (NODE_NAME_SRC) REFERENCES NODES(NODE_NAME) ON DELETE CASCADE,
    FOREIGN KEY (NODE_NAME_DST) REFERENCES NODES(NODE_NAME) ON DELETE CASCADE
);

CREATE TABLE RESOURCE_CONNECTIONS
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    NODE_NAME_SRC VARCHAR(255) NOT NULL,
    NODE_NAME_DST VARCHAR(255) NOT NULL,
    RESOURCE_NAME VARCHAR(48) NOT NULL,
    PRIMARY KEY (NODE_NAME_SRC, NODE_NAME_DST, RESOURCE_NAME),
    FOREIGN KEY (NODE_NAME_SRC, RESOURCE_NAME) REFERENCES RESOURCES(NODE_NAME, RESOURCE_NAME) ON DELETE CASCADE,
    FOREIGN KEY (NODE_NAME_DST, RESOURCE_NAME) REFERENCES RESOURCES(NODE_NAME, RESOURCE_NAME) ON DELETE CASCADE
);

CREATE TABLE VOLUME_CONNECTIONS
(
    UUID CHAR(16) FOR BIT DATA NOT NULL,
    NODE_NAME_SRC VARCHAR(255) NOT NULL,
    NODE_NAME_DST VARCHAR(255) NOT NULL,
    RESOURCE_NAME VARCHAR(48) NOT NULL,
    VLM_NR INT NOT NULL,
    PRIMARY KEY (NODE_NAME_SRC, NODE_NAME_DST, RESOURCE_NAME, VLM_NR),
    FOREIGN KEY (NODE_NAME_SRC, RESOURCE_NAME, VLM_NR) REFERENCES VOLUMES(NODE_NAME, RESOURCE_NAME, VLM_NR)
        ON DELETE CASCADE,
    FOREIGN KEY (NODE_NAME_DST, RESOURCE_NAME, VLM_NR) REFERENCES VOLUMES(NODE_NAME, RESOURCE_NAME, VLM_NR)
        ON DELETE CASCADE
);

CREATE TABLE PROPS_CONTAINERS
(
    PROPS_INSTANCE VARCHAR(512) NOT NULL
        CONSTRAINT PRP_INST_CHKNAME CHECK(UPPER(PROPS_INSTANCE) = PROPS_INSTANCE AND LENGTH(PROPS_INSTANCE) >= 2),
    PROP_KEY VARCHAR(512) NOT NULL,
    PROP_VALUE VARCHAR(4096) NOT NULL,
    PRIMARY KEY (PROPS_INSTANCE, PROP_KEY)
);

CREATE INDEX IDX_PROPS_CONTAINERS ON PROPS_CONTAINERS (PROPS_INSTANCE ASC);

-- Identities load
CREATE VIEW SEC_IDENTITIES_LOAD AS
    SELECT IDENTITY_DSP_NAME, ID_ENABLED
    FROM SEC_IDENTITIES;

-- Roles load
CREATE VIEW SEC_ROLES_LOAD AS
    SELECT ROLE_DSP_NAME, ROLE_ENABLED
    FROM SEC_ROLES;

-- Security types load
CREATE VIEW SEC_TYPES_LOAD AS
    SELECT TYPE_DSP_NAME, TYPE_ENABLED
    FROM SEC_TYPES;

-- Type enforcement rules load
CREATE VIEW SEC_TYPE_RULES_LOAD AS
    SELECT DOMAIN_NAME, TYPE_NAME, SEC_ACCESS_TYPES.ACCESS_TYPE_NAME AS ACCESS_TYPE
    FROM SEC_TYPE_RULES
    LEFT JOIN SEC_ACCESS_TYPES ON SEC_TYPE_RULES.ACCESS_TYPE = SEC_ACCESS_TYPES.ACCESS_TYPE_VALUE
    ORDER BY DOMAIN_NAME, TYPE_NAME ASC;

-- Security subsystem initialization
INSERT INTO SEC_CONFIGURATION (ENTRY_KEY, ENTRY_DSP_KEY, ENTRY_VALUE)
    VALUES ('SECURITYLEVEL', 'SecurityLevel', 'MAC');

INSERT INTO SEC_ACCESS_TYPES (ACCESS_TYPE_NAME, ACCESS_TYPE_VALUE)
    VALUES ('CONTROL', 15);
INSERT INTO SEC_ACCESS_TYPES (ACCESS_TYPE_NAME, ACCESS_TYPE_VALUE)
    VALUES ('CHANGE', 7);
INSERT INTO SEC_ACCESS_TYPES (ACCESS_TYPE_NAME, ACCESS_TYPE_VALUE)
    VALUES ('USE', 3);
INSERT INTO SEC_ACCESS_TYPES (ACCESS_TYPE_NAME, ACCESS_TYPE_VALUE)
    VALUES ('VIEW', 1);

INSERT INTO SEC_IDENTITIES (IDENTITY_NAME, IDENTITY_DSP_NAME, ID_ENABLED, ID_LOCKED)
    VALUES ('SYSTEM', 'SYSTEM', TRUE, TRUE);
INSERT INTO SEC_IDENTITIES (IDENTITY_NAME, IDENTITY_DSP_NAME, ID_ENABLED, ID_LOCKED)
    VALUES ('PUBLIC', 'PUBLIC', TRUE, TRUE);

-- Domains / Types
INSERT INTO SEC_TYPES (TYPE_NAME, TYPE_DSP_NAME, TYPE_ENABLED)
    VALUES ('SYSTEM', 'SYSTEM', TRUE);
INSERT INTO SEC_TYPES (TYPE_NAME, TYPE_DSP_NAME, TYPE_ENABLED)
    VALUES ('PUBLIC', 'PUBLIC', TRUE);
INSERT INTO SEC_TYPES (TYPE_NAME, TYPE_DSP_NAME, TYPE_ENABLED)
    VALUES ('SHARED', 'SHARED', TRUE);
INSERT INTO SEC_TYPES (TYPE_NAME, TYPE_DSP_NAME, TYPE_ENABLED)
    VALUES ('SYSADM', 'SysAdm', TRUE);
INSERT INTO SEC_TYPES (TYPE_NAME, TYPE_DSP_NAME, TYPE_ENABLED)
    VALUES ('USER', 'User', TRUE);

-- Type enforcement rules
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('SYSTEM', 'SYSTEM', 15);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('SYSTEM', 'PUBLIC', 15);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('SYSTEM', 'SHARED', 15);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('SYSTEM', 'SYSADM', 15);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('SYSTEM', 'USER', 15);

INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('PUBLIC', 'SYSTEM', 3);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('PUBLIC', 'PUBLIC', 15);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('PUBLIC', 'SHARED', 7);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('PUBLIC', 'SYSADM', 3);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('PUBLIC', 'USER', 3);

INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('SYSADM', 'SYSTEM', 15);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('SYSADM', 'PUBLIC', 15);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('SYSADM', 'SHARED', 15);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('SYSADM', 'SYSADM', 15);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('SYSADM', 'USER', 15);

INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('USER', 'SYSTEM', 3);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('USER', 'PUBLIC', 7);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('USER', 'SHARED', 7);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('USER', 'SYSADM', 3);
INSERT INTO SEC_TYPE_RULES (DOMAIN_NAME, TYPE_NAME, ACCESS_TYPE)
    VALUES ('USER', 'USER', 15);

-- Default security roles
INSERT INTO SEC_ROLES (ROLE_NAME, ROLE_DSP_NAME, DOMAIN_NAME, ROLE_ENABLED, ROLE_PRIVILEGES)
    VALUES ('SYSTEM', 'SYSTEM', 'SYSTEM', TRUE, -1);
INSERT INTO SEC_ROLES (ROLE_NAME, ROLE_DSP_NAME, DOMAIN_NAME, ROLE_ENABLED, ROLE_PRIVILEGES)
    VALUES ('PUBLIC', 'PUBLIC', 'PUBLIC', TRUE, 0);
INSERT INTO SEC_ROLES (ROLE_NAME, ROLE_DSP_NAME, DOMAIN_NAME, ROLE_ENABLED, ROLE_PRIVILEGES)
    VALUES ('SYSADM', 'SysAdm', 'SYSADM', TRUE, -1);
INSERT INTO SEC_ROLES (ROLE_NAME, ROLE_DSP_NAME, DOMAIN_NAME, ROLE_ENABLED, ROLE_PRIVILEGES)
    VALUES ('USER', 'User', 'USER', TRUE, 0);

-- Identity / Role assignment
INSERT INTO SEC_ID_ROLE_MAP (IDENTITY_NAME, ROLE_NAME)
    VALUES ('SYSTEM', 'SYSTEM');
INSERT INTO SEC_ID_ROLE_MAP (IDENTITY_NAME, ROLE_NAME)
    VALUES ('PUBLIC', 'PUBLIC');

-- Default role assignment
INSERT INTO SEC_DFLT_ROLES (IDENTITY_NAME, ROLE_NAME)
    VALUES ('SYSTEM', 'SYSTEM');
INSERT INTO SEC_DFLT_ROLES (IDENTITY_NAME, ROLE_NAME)
    VALUES ('PUBLIC', 'PUBLIC');


-- Default NetCom services

-- Default PlainConnector
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/PlainConnector/type', 'plain');
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/PlainConnector/bindaddress', '::0');
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/PlainConnector/port', '3376');

-- Default SSLConnector
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/SslConnector/type', 'ssl');
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/SslConnector/bindaddress', '::0');
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/SslConnector/port', '3377');
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/SslConnector/keyPasswd', 'dmngdemo');
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/SslConnector/keyStorePasswd', 'dmngdemo');
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/SslConnector/trustStorePasswd', 'dmngdemo');
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/SslConnector/trustStore', 'ssl/certificates.jks');
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/SslConnector/sslProtocol', 'TLSv1');
INSERT INTO PROPS_CONTAINERS VALUES ('CTRLCFG', 'netcom/SslConnector/keyStore', 'ssl/keystore.jks');


-- Access control - System objects

-- Owner - Nodes map
INSERT INTO SEC_OBJECT_PROTECTION (OBJECT_PATH, CREATOR_IDENTITY_NAME, OWNER_ROLE_NAME, SECURITY_TYPE_NAME)
    VALUES ('/sys/controller/nodesMap', 'SYSTEM', 'SYSADM', 'SHARED');
-- ACL: Nodes map
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/nodesMap', 'SYSTEM', 15);
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/nodesMap', 'USER', 7);

-- Owner - Resource definition map
INSERT INTO SEC_OBJECT_PROTECTION (OBJECT_PATH, CREATOR_IDENTITY_NAME, OWNER_ROLE_NAME, SECURITY_TYPE_NAME)
    VALUES ('/sys/controller/rscDfnMap', 'SYSTEM', 'SYSADM', 'SHARED');
-- ACL: Resource definition map
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/rscDfnMap', 'SYSTEM', 15);
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/rscDfnMap', 'USER', 7);

-- Owner - Storage pool map
INSERT INTO SEC_OBJECT_PROTECTION (OBJECT_PATH, CREATOR_IDENTITY_NAME, OWNER_ROLE_NAME, SECURITY_TYPE_NAME)
    VALUES ('/sys/controller/storPoolMap', 'SYSTEM', 'SYSADM', 'SHARED');
-- ACL: Storage pool map
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/storPoolMap', 'SYSTEM', 15);
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/storPoolMap', 'USER', 7);

-- Owner - System configuration properties
INSERT INTO SEC_OBJECT_PROTECTION (OBJECT_PATH, CREATOR_IDENTITY_NAME, OWNER_ROLE_NAME, SECURITY_TYPE_NAME)
    VALUES ('/sys/controller/conf', 'SYSTEM', 'SYSADM', 'SYSTEM');
-- ACL: System configuration properties
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/conf', 'SYSTEM', 15);

-- Owner - System shutdown request
INSERT INTO SEC_OBJECT_PROTECTION (OBJECT_PATH, CREATOR_IDENTITY_NAME, OWNER_ROLE_NAME, SECURITY_TYPE_NAME)
    VALUES ('/sys/controller/shutdown', 'SYSTEM', 'SYSADM', 'SYSTEM');
-- ACL: System shutdown request
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/shutdown', 'SYSTEM', 15);


-- Default storage pool definition
INSERT INTO STOR_POOL_DEFINITIONS VALUES (x'f51611c6528f4793a87a866d09e6733a', 'DFLTSTORPOOL', 'DfltStorPool');
-- Owner - Default storage pool definition
INSERT INTO SEC_OBJECT_PROTECTION (OBJECT_PATH, CREATOR_IDENTITY_NAME, OWNER_ROLE_NAME, SECURITY_TYPE_NAME)
    VALUES ('/storpooldefinitions/DFLTSTORPOOL', 'SYSTEM', 'SYSADM', 'SHARED');
-- ACL - Default storage pool definition
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
    VALUES ('/storpooldefinitions/DFLTSTORPOOL', 'PUBLIC', 7);
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
    VALUES ('/storpooldefinitions/DFLTSTORPOOL', 'USER', 7);


-- Default diskless storage pool definition
INSERT INTO STOR_POOL_DEFINITIONS VALUES (x'f51611c6528f4793a87a866d09e6733a', 'DFLTDISKLESSSTORPOOL', 'DfltDisklessStorPool');
-- Owner - Default storage pool definition
INSERT INTO SEC_OBJECT_PROTECTION (OBJECT_PATH, CREATOR_IDENTITY_NAME, OWNER_ROLE_NAME, SECURITY_TYPE_NAME)
    VALUES ('/storpooldefinitions/DFLTDISKLESSSTORPOOL', 'SYSTEM', 'SYSADM', 'SHARED');
-- ACL - Default storage pool definition
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
    VALUES ('/storpooldefinitions/DFLTDISKLESSSTORPOOL', 'PUBLIC', 7);
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
    VALUES ('/storpooldefinitions/DFLTDISKLESSSTORPOOL', 'USER', 7);


-- TEST ENTRIES FOR TESTING WITH ANONYMOUS CONNECTIONS (ID: PUBLIC / ROLE: PUBLIC / DOMAIN: PUBLIC)
-- Allow inserting and deleting nodes
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/nodesMap', 'PUBLIC', 7);

-- Allow inserting and deleting resource definitions
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/rscDfnMap', 'PUBLIC', 7);

-- Allow inserting and deleting storage pool definitions
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/storPoolMap', 'PUBLIC', 7);

-- Allow viewing the system configuration
INSERT INTO SEC_ACL_MAP (OBJECT_PATH, ROLE_NAME, ACCESS_TYPE)
	VALUES ('/sys/controller/conf', 'PUBLIC', 1);
