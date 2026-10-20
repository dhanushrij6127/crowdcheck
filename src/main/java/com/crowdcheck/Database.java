package com.crowdcheck;

import com.crowdcheck.Models.*;
import java.sql.*;
import java.util.*;

/** All JDBC work, crowd-level calculation and alert generation live here. */
public class Database {

    // ---- EDIT THESE (or set environment variables DB_URL / DB_USER / DB_PASS) ----
    static final String URL  = env("DB_URL",  "jdbc:mysql://localhost:3306/crowdcheck?useSSL=false&allowPublicKeyRetrieval=true");
    static final String USER = env("DB_USER", "root");
    static final String PASS = env("DB_PASS", "");
    // -----------------------------------------------------------------------------

    static final Set<String> TYPES = Set.of("ENTRANCE", "FOOD_COURT", "AUDITORIUM", "PARKING");
    static final Set<String> ROLES = Set.of("ADMIN", "ORGANIZER", "STAFF", "VISITOR");
    static final String[] MANAGERS = {"ADMIN", "ORGANIZER"};
    static final String[] STAFF    = {"ADMIN", "ORGANIZER", "STAFF"};

    static String env(String k, String d) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? d : v;
    }

    static Connection conn() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    // ---------------- Crowd level calculation ----------------
    /** LOW < 40%, MODERATE 40-69%, HIGH 70-89%, CRITICAL >= 90% of capacity. */
    public static String calcLevel(int count, int capacity) {
        if (capacity <= 0) return "LOW";
        double p = count * 100.0 / capacity;
        if (p < 40) return "LOW";
        if (p < 70) return "MODERATE";
        if (p < 90) return "HIGH";
        return "CRITICAL";
    }

    static boolean needsAlert(String level) {
        return level.equals("HIGH") || level.equals("CRITICAL");
    }

    // ---------------- Authorization ----------------
    static void require(Connection c, int userId, String... roles) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT user_role FROM users WHERE user_id=?")) {
            ps.setInt(1, userId);
            try (ResultSet r = ps.executeQuery()) {
                if (!r.next()) throw new SecurityException("Unknown user. Please choose a valid user.");
                String role = r.getString(1);
                if (!Arrays.asList(roles).contains(role))
                    throw new SecurityException("Your role (" + role + ") is not allowed to do this. Needs: " + String.join("/", roles));
            }
        }
    }

    // ---------------- Users ----------------
    public static List<User> users() throws SQLException {
        List<User> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT user_id,user_name,email,contact_info,user_role FROM users ORDER BY user_id")) {
            while (r.next()) list.add(new User(r.getInt(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)));
        }
        return list;
    }

    public static void addUser(String name, String email, String contact, String role, int actor) throws SQLException {
        if (name == null || name.isBlank() || email == null || email.isBlank())
            throw new IllegalArgumentException("Name and email are required.");
        if (!ROLES.contains(role)) throw new IllegalArgumentException("Role must be one of " + ROLES);
        try (Connection c = conn()) {
            require(c, actor, "ADMIN");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(user_name,email,contact_info,user_role) VALUES(?,?,?,?)")) {
                ps.setString(1, name.trim()); ps.setString(2, email.trim()); ps.setString(3, contact); ps.setString(4, role);
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException e) {
                throw new IllegalArgumentException("A user with this email already exists.");
            }
        }
    }

    // ---------------- Locations ----------------
    static final String LOC_SQL = "SELECT location_id,location_name,location_type,capacity,current_crowd_count,crowd_level,"
            + "DATE_FORMAT(last_updated,'%Y-%m-%d %H:%i:%s') lu FROM locations ";

    static Location loc(ResultSet r) throws SQLException {
        int cap = r.getInt("capacity"), cnt = r.getInt("current_crowd_count");
        return new Location(r.getInt("location_id"), r.getString("location_name"), r.getString("location_type"),
                cap, cnt, r.getString("crowd_level"), r.getString("lu"), cap > 0 ? (int) Math.round(cnt * 100.0 / cap) : 0);
    }

    /** Lists all locations, or searches by name/type when q is given. */
    public static List<Location> locations(String q) throws SQLException {
        boolean search = q != null && !q.isBlank();
        List<Location> list = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(LOC_SQL + (search ? "WHERE location_name LIKE ? OR location_type LIKE ? " : "") + "ORDER BY location_id")) {
            if (search) { ps.setString(1, "%" + q.trim() + "%"); ps.setString(2, "%" + q.trim().replace(' ', '_') + "%"); }
            try (ResultSet r = ps.executeQuery()) { while (r.next()) list.add(loc(r)); }
        }
        return list;
    }

    static Location getLocation(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(LOC_SQL + "WHERE location_id=?")) {
            ps.setInt(1, id);
            try (ResultSet r = ps.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Location not found.");
                return loc(r);
            }
        }
    }

    static void validateLocation(String name, String type, int cap) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Location name is required.");
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Type must be one of " + TYPES);
        if (cap <= 0) throw new IllegalArgumentException("Capacity must be greater than 0.");
    }

    public static void addLocation(String name, String type, int cap, int actor) throws SQLException {
        validateLocation(name, type, cap);
        try (Connection c = conn()) {
            require(c, actor, MANAGERS);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO locations(location_name,location_type,capacity) VALUES(?,?,?)")) {
                ps.setString(1, name.trim()); ps.setString(2, type); ps.setInt(3, cap);
                ps.executeUpdate();
            }
        }
    }

    public static void editLocation(int id, String name, String type, int cap, int actor) throws SQLException {
        validateLocation(name, type, cap);
        try (Connection c = conn()) {
            require(c, actor, MANAGERS);
            int count = getLocation(c, id).currentCrowdCount();
            try (PreparedStatement ps = c.prepareStatement("UPDATE locations SET location_name=?,location_type=?,capacity=?,crowd_level=? WHERE location_id=?")) {
                ps.setString(1, name.trim()); ps.setString(2, type); ps.setInt(3, cap);
                ps.setString(4, calcLevel(count, cap)); ps.setInt(5, id);
                ps.executeUpdate();
            }
        }
    }

    public static void deleteLocation(int id, int actor) throws SQLException {
        try (Connection c = conn()) {
            require(c, actor, MANAGERS);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM locations WHERE location_id=?")) {
                ps.setInt(1, id);
                if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Location not found.");
            }
        }
    }

    // ---------------- Update crowd (+ history + alerts) ----------------
    public static Location updateCrowd(int locId, int count, int actor) throws SQLException {
        if (count < 0) throw new IllegalArgumentException("Crowd count cannot be negative.");
        try (Connection c = conn()) {
            require(c, actor, STAFF);
            Location l = getLocation(c, locId);
            String level = calcLevel(count, l.capacity());
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE locations SET current_crowd_count=?,crowd_level=?,last_updated=NOW() WHERE location_id=?")) {
                    ps.setInt(1, count); ps.setString(2, level); ps.setInt(3, locId); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO crowd_updates(location_id,crowd_count,crowd_level,updated_by) VALUES(?,?,?,?)")) {
                    ps.setInt(1, locId); ps.setInt(2, count); ps.setString(3, level); ps.setInt(4, actor); ps.executeUpdate();
                }
                if (needsAlert(level)) createAlertIfNeeded(c, l, count, level);
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
            return getLocation(c, locId);
        }
    }

    /** Creates an alert unless an open alert of the same level already exists for this location. */
    static void createAlertIfNeeded(Connection c, Location l, int count, String level) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM crowd_alerts WHERE location_id=? AND crowd_level=? AND status IN ('ACTIVE','ACKNOWLEDGED')")) {
            ps.setInt(1, l.locationId()); ps.setString(2, level);
            try (ResultSet r = ps.executeQuery()) { r.next(); if (r.getInt(1) > 0) return; }
        }
        int pct = (int) Math.round(count * 100.0 / l.capacity());
        String msg = l.locationName() + " is " + level + ": " + count + "/" + l.capacity() + " (" + pct + "% full)";
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO crowd_alerts(location_id,alert_message,crowd_level) VALUES(?,?,?)")) {
            ps.setInt(1, l.locationId()); ps.setString(2, msg); ps.setString(3, level); ps.executeUpdate();
        }
    }

    // ---------------- Alerts ----------------
    public static List<Alert> alerts(String status) throws SQLException {
        boolean f = status != null && !status.isBlank() && !status.equals("ALL");
        List<Alert> list = new ArrayList<>();
        String sql = "SELECT a.alert_id,a.location_id,l.location_name,a.alert_message,"
                + "DATE_FORMAT(a.alert_time,'%Y-%m-%d %H:%i:%s') t,a.crowd_level,a.status "
                + "FROM crowd_alerts a JOIN locations l ON l.location_id=a.location_id "
                + (f ? "WHERE a.status=? " : "") + "ORDER BY a.alert_time DESC, a.alert_id DESC";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (f) ps.setString(1, status);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) list.add(new Alert(r.getInt(1), r.getInt(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7)));
            }
        }
        return list;
    }

    public static void setAlertStatus(int id, String status, int actor) throws SQLException {
        if (!status.equals("ACKNOWLEDGED") && !status.equals("RESOLVED"))
            throw new IllegalArgumentException("Status must be ACKNOWLEDGED or RESOLVED.");
        try (Connection c = conn()) {
            require(c, actor, STAFF);
            try (PreparedStatement ps = c.prepareStatement("UPDATE crowd_alerts SET status=? WHERE alert_id=?")) {
                ps.setString(1, status); ps.setInt(2, id);
                if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Alert not found.");
            }
        }
    }

    // ---------------- History & analysis ----------------
    public static List<CrowdUpdate> history(int locationId, int limit) throws SQLException {
        List<CrowdUpdate> list = new ArrayList<>();
        String sql = "SELECT u.update_id,u.location_id,l.location_name,u.crowd_count,l.capacity,u.crowd_level,"
                + "COALESCE(us.user_name,'-') who,DATE_FORMAT(u.update_time,'%Y-%m-%d %H:%i:%s') t "
                + "FROM crowd_updates u JOIN locations l ON l.location_id=u.location_id "
                + "LEFT JOIN users us ON us.user_id=u.updated_by "
                + (locationId > 0 ? "WHERE u.location_id=? " : "") + "ORDER BY u.update_time DESC, u.update_id DESC LIMIT ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (locationId > 0) ps.setInt(i++, locationId);
            ps.setInt(i, limit);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    int cnt = r.getInt(4), cap = r.getInt(5);
                    list.add(new CrowdUpdate(r.getInt(1), r.getInt(2), r.getString(3), cnt, cap,
                            cap > 0 ? (int) Math.round(cnt * 100.0 / cap) : 0, r.getString(6), r.getString(7), r.getString(8)));
                }
            }
        }
        return list;
    }

    /** Per-location overcrowding statistics (most frequently overcrowded first). */
    public static List<Map<String, Object>> analysis() throws SQLException {
        String sql = "SELECT l.location_id,l.location_name,l.capacity,COUNT(u.update_id) total,"
                + "COALESCE(SUM(u.crowd_level IN ('HIGH','CRITICAL')),0) over_cnt,"
                + "COALESCE(SUM(u.crowd_level='CRITICAL'),0) crit_cnt,COALESCE(MAX(u.crowd_count),0) peak "
                + "FROM locations l LEFT JOIN crowd_updates u ON u.location_id=l.location_id "
                + "GROUP BY l.location_id,l.location_name,l.capacity ORDER BY over_cnt DESC, total DESC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) {
                int total = r.getInt("total"), over = r.getInt("over_cnt");
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("locationId", r.getInt("location_id"));
                m.put("locationName", r.getString("location_name"));
                m.put("capacity", r.getInt("capacity"));
                m.put("totalUpdates", total);
                m.put("overcrowdedUpdates", over);
                m.put("criticalUpdates", r.getInt("crit_cnt"));
                m.put("peakCount", r.getInt("peak"));
                m.put("overcrowdedPercent", total == 0 ? 0 : (int) Math.round(over * 100.0 / total));
                list.add(m);
            }
        }
        return list;
    }
}
