CREATE TABLE mitid_erhverv_cache (
   id                         BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   mitid_erhverv_id           BIGINT NOT NULL,
   status                     VARCHAR(64) NOT NULL,
   givenname                  VARCHAR(255) NULL,
   surname                    VARCHAR(255) NULL,
   cpr                        VARCHAR(10) NULL,
   uuid                       VARCHAR(36) NOT NULL,
   email                      VARCHAR(255) NULL,
   rid                        VARCHAR(255) NULL,
   local_credential           BOOLEAN NOT NULL DEFAULT 0,
   mitid_privat_credential    BOOLEAN NOT NULL DEFAULT 0,
   last_updated               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX mitid_erhverv_cache_idx1 ON mitid_erhverv_cache (uuid);
CREATE INDEX mitid_erhverv_cache_idx2 ON mitid_erhverv_cache (cpr);
CREATE INDEX mitid_erhverv_cache_idx3 ON mitid_erhverv_cache (mitid_erhverv_id);
