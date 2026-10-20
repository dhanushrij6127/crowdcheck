-- ============================================================
--  CrowdCheck - queries to VIEW the tables in MySQL Workbench
--  Run one block at a time (select it, then press Ctrl+Enter)
-- ============================================================
USE crowdcheck;

-- 1. List all tables
SHOW TABLES;

-- 2. Table structures
DESCRIBE users;
DESCRIBE locations;
DESCRIBE crowd_alerts;
DESCRIBE crowd_updates;

-- 3. Table data
SELECT * FROM users;
SELECT * FROM locations;
SELECT * FROM crowd_alerts;
SELECT * FROM crowd_updates;

-- 4. Live crowd status with percentage full
SELECT location_id, location_name, location_type, current_crowd_count, capacity,
       ROUND(current_crowd_count * 100 / capacity) AS percent_full, crowd_level, last_updated
FROM locations
ORDER BY percent_full DESC;

-- 5. Alerts together with location names
SELECT a.alert_id, l.location_name, a.alert_message, a.crowd_level, a.status, a.alert_time
FROM crowd_alerts a JOIN locations l ON l.location_id = a.location_id
ORDER BY a.alert_time DESC;

-- 6. Crowd history with location and staff names
SELECT u.update_id, l.location_name, u.crowd_count, u.crowd_level, us.user_name AS updated_by, u.update_time
FROM crowd_updates u
JOIN locations l ON l.location_id = u.location_id
LEFT JOIN users us ON us.user_id = u.updated_by
ORDER BY u.update_time DESC;

-- 7. Locations that are frequently overcrowded (HIGH / CRITICAL updates)
SELECT l.location_name,
       COUNT(*) AS total_updates,
       SUM(u.crowd_level IN ('HIGH','CRITICAL')) AS overcrowded_updates,
       ROUND(100 * SUM(u.crowd_level IN ('HIGH','CRITICAL')) / COUNT(*)) AS overcrowded_percent
FROM crowd_updates u JOIN locations l ON l.location_id = u.location_id
GROUP BY l.location_id, l.location_name
ORDER BY overcrowded_percent DESC;

-- 8. Number of locations per crowd level
SELECT crowd_level, COUNT(*) AS locations FROM locations GROUP BY crowd_level;

-- 9. Only open alerts
SELECT * FROM crowd_alerts WHERE status IN ('ACTIVE','ACKNOWLEDGED');
