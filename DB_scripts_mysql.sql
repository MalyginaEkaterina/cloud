CREATE DATABASE `cloud_storage` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

CREATE TABLE `directory` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int unsigned NOT NULL,
  `path` varchar(4096) NOT NULL,
  `path_hash` binary(32) DEFAULT NULL,
  `is_empty` tinyint DEFAULT NULL COMMENT '0 - пустой , 1 - не пустой',
  PRIMARY KEY (`id`),
  KEY `fk_directory_user_id_idx` (`user_id`),
  KEY `idx_user_id_path_hash` (`user_id`,`path_hash`),
  CONSTRAINT `fk_directory_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int unsigned NOT NULL,
  `filename` varchar(256) NOT NULL,
  `directory_id` bigint unsigned NOT NULL,
  `size` bigint NOT NULL COMMENT 'размер файла в байтах',
  `load_state` tinyint NOT NULL COMMENT 'статус загрузки: 1 - полностью загружен, 2 - в процессе загрузки',
  PRIMARY KEY (`id`),
  KEY `fk_file_user_id_idx` (`user_id`),
  KEY `fk_file_directory_id_idx` (`directory_id`),
  KEY `idx_filename_directory_id` (`filename`,`directory_id`),
  CONSTRAINT `fk_file_directory_id` FOREIGN KEY (`directory_id`) REFERENCES `directory` (`id`),
  CONSTRAINT `fk_file_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `login` varchar(128) NOT NULL,
  `name` varchar(128) DEFAULT NULL,
  `email` varchar(128) DEFAULT NULL,
  `pass` varbinary(255) DEFAULT NULL,
  `salt` varbinary(255) DEFAULT NULL,
  `size` bigint DEFAULT NULL,
  `free_size` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `login_UNIQUE` (`login`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
