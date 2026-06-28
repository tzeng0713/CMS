CREATE DATABASE IF NOT EXISTS cms
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

DROP USER IF EXISTS 'cms_app'@'localhost';
CREATE USER 'cms_app'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON cms.* TO 'cms_app'@'localhost';
FLUSH PRIVILEGES;
