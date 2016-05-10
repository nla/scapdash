CREATE TABLE host (
  id   INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE advisory (
  id           INT PRIMARY KEY AUTO_INCREMENT,
  xml_ref      VARCHAR(255) NOT NULL UNIQUE,
  title        VARCHAR(255) NOT NULL,
  description  TEXT,
  severity     VARCHAR(255) NOT NULL,
  issued_date  DATETIME,
  updated_date DATETIME
);

CREATE TABLE advisory_reference (
  id          INT PRIMARY KEY AUTO_INCREMENT,
  advisory_id INT          NOT NULL,
  system      VARCHAR(255) NOT NULL,
  identifier  VARCHAR(255) NOT NULL,

  FOREIGN KEY (advisory_id) REFERENCES advisory (id),
  UNIQUE (advisory_id, system, identifier)
);

CREATE TABLE checkin (
  id           INT PRIMARY KEY AUTO_INCREMENT,
  host_id      INT      NOT NULL,
  checkin_date DATETIME NOT NULL,

  FOREIGN KEY (host_id) REFERENCES host (id)
);

CREATE TABLE checkin_result (
  checkin_id  INT NOT NULL,
  advisory_id INT NOT NULL,
  value       INT NOT NULL,

  PRIMARY KEY (checkin_id, advisory_id),
  FOREIGN KEY (checkin_id) REFERENCES checkin (id),
  FOREIGN KEY (advisory_id) REFERENCES advisory (id)
);