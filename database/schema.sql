-- ============================================================
--  CrowdCheck database setup  (run this ONCE in MySQL Workbench)
--  WARNING: it drops and re-creates the tables.
-- ============================================================
CREATE DATABASE IF NOT EXISTS crowdcheck;
USE crowdcheck;

DROP TABLE IF EXISTS crowd_updates;
DROP TABLE IF EXISTS crowd_alerts;
DROP TABLE IF EXISTS locations;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    user_id      INT AUTO_INCREMENT PRIMARY KEY,
    user_name    VARCHAR(100) NOT NULL,
    email        VARCHAR(120) NOT NULL UNIQUE,
    contact_info VARCHAR(30),
    user_role    ENUM('ADMIN','ORGANIZER','STAFF','VISITOR') NOT NULL DEFAULT 'VISITOR'
);

CREATE TABLE locations (
    location_id         INT AUTO_INCREMENT PRIMARY KEY,
    location_name       VARCHAR(100) NOT NULL,
    location_type       ENUM('ENTRANCE','FOOD_COURT','AUDITORIUM','PARKING') NOT NULL,
    capacity            INT NOT NULL CHECK (capacity > 0),
    current_crowd_count INT NOT NULL DEFAULT 0,
    crowd_level         ENUM('LOW','MODERATE','HIGH','CRITICAL') NOT NULL DEFAULT 'LOW',
    last_updated        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE crowd_alerts (
    alert_id      INT AUTO_INCREMENT PRIMARY KEY,
    location_id   INT NOT NULL,
    alert_message VARCHAR(255) NOT NULL,
    alert_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    crowd_level   ENUM('LOW','MODERATE','HIGH','CRITICAL') NOT NULL,
    status        ENUM('ACTIVE','ACKNOWLEDGED','RESOLVED') NOT NULL DEFAULT 'ACTIVE',
    FOREIGN KEY (location_id) REFERENCES locations(location_id) ON DELETE CASCADE
);

-- History table (needed for "Crowd History and Analysis")
CREATE TABLE crowd_updates (
    update_id    INT AUTO_INCREMENT PRIMARY KEY,
    location_id  INT NOT NULL,
    crowd_count  INT NOT NULL,
    crowd_level  ENUM('LOW','MODERATE','HIGH','CRITICAL') NOT NULL,
    updated_by   INT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (location_id) REFERENCES locations(location_id) ON DELETE CASCADE,
    FOREIGN KEY (updated_by)  REFERENCES users(user_id) ON DELETE SET NULL
);

-- ---------------- Sample data ----------------
INSERT INTO users (user_name, email, contact_info, user_role) VALUES
 ('Asha Admin',      'admin@college.edu',     '9000000001', 'ADMIN'),
 ('Omar Organizer',  'organizer@college.edu', '9000000002', 'ORGANIZER'),
 ('Sam Staff',       'staff@college.edu',     '9000000003', 'STAFF'),
 ('Vera Visitor',    'visitor@college.edu',   '9000000004', 'VISITOR');

INSERT INTO locations (location_name, location_type, capacity, current_crowd_count, crowd_level) VALUES
 ('Main Gate',         'ENTRANCE',   500, 120, 'LOW'),
 ('Back Gate',         'ENTRANCE',   300, 150, 'MODERATE'),
 ('Central Food Court','FOOD_COURT', 400, 370, 'CRITICAL'),
 ('Snack Corner',      'FOOD_COURT', 150, 110, 'HIGH'),
 ('Main Auditorium',   'AUDITORIUM', 1000,650, 'MODERATE'),
 ('Mini Auditorium',   'AUDITORIUM', 250,  60, 'LOW'),
 ('Student Parking',   'PARKING',    600, 480, 'HIGH'),
 ('Visitor Parking',   'PARKING',    200, 190, 'CRITICAL');

INSERT INTO crowd_alerts (location_id, alert_message, alert_time, crowd_level, status) VALUES
 (3, 'Central Food Court is CRITICAL: 370/400 (93% full)', NOW(),                          'CRITICAL', 'ACTIVE'),
 (8, 'Visitor Parking is CRITICAL: 190/200 (95% full)',    NOW(),                          'CRITICAL', 'ACTIVE'),
 (7, 'Student Parking is HIGH: 480/600 (80% full)',        NOW(),                          'HIGH',     'ACTIVE'),
 (4, 'Snack Corner is HIGH: 110/150 (73% full)',           DATE_SUB(NOW(), INTERVAL 1 HOUR),'HIGH',    'ACKNOWLEDGED'),
 (3, 'Central Food Court is HIGH: 340/400 (85% full)',     DATE_SUB(NOW(), INTERVAL 5 HOUR),'HIGH',    'RESOLVED');

INSERT INTO crowd_updates (location_id, crowd_count, crowd_level, updated_by, update_time) VALUES
 (3, 150, 'LOW',      3, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
 (3, 260, 'MODERATE', 3, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
 (3, 340, 'HIGH',     3, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
 (3, 380, 'CRITICAL', 3, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
 (3, 300, 'HIGH',     3, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
 (3, 370, 'CRITICAL', 3, NOW()),
 (8, 100, 'MODERATE', 3, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
 (8, 150, 'HIGH',     3, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
 (8, 185, 'CRITICAL', 3, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
 (8, 190, 'CRITICAL', 3, NOW()),
 (7, 300, 'MODERATE', 3, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
 (7, 420, 'HIGH',     3, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
 (7, 480, 'HIGH',     3, NOW()),
 (1,  80, 'LOW',      3, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
 (1, 200, 'MODERATE', 3, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
 (1, 120, 'LOW',      3, NOW()),
 (5, 400, 'MODERATE', 3, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
 (5, 650, 'MODERATE', 3, NOW());
