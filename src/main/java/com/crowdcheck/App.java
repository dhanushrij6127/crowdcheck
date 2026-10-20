package com.crowdcheck;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Starts the web server (frontend + REST API) and the console menu. */
public class App {
    static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        try (var c = Database.conn()) {
            System.out.println("Connected to MySQL.");
        } catch (Exception e) {
            System.out.println("Cannot connect to MySQL: " + e.getMessage());
            System.out.println("-> Run database/schema.sql in Workbench and check DB_USER / DB_PASS in Database.java");
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/", App::api);
        server.createContext("/", App::page);
        server.start();
        System.out.println("Web app running at  http://localhost:8080");

        try { Console.run(); }
        catch (NoSuchElementException e) { /* no console attached: keep serving the web app */ }
        Thread.currentThread().join();
    }

    // ---------- serve the single-page frontend ----------
    static void page(HttpExchange ex) throws IOException {
        try (InputStream in = App.class.getResourceAsStream("/web/index.html")) {
            byte[] d = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, d.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(d); }
        }
    }

    // ---------- REST API ----------
    static void api(HttpExchange ex) throws IOException {
        int code = 200;
        Object out;
        try {
            String method = ex.getRequestMethod();
            String[] p = ex.getRequestURI().getPath().substring(5).split("/");
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            Map<String, Object> b = new HashMap<>();
            if (!method.equals("GET")) {
                try (Reader r = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
                    @SuppressWarnings("unchecked") Map<String, Object> m = gson.fromJson(r, Map.class);
                    if (m != null) b = m;
                }
            }
            out = route(method, p, q, b, num(b.get("actorId")));
        } catch (SecurityException e) {
            code = 403; out = Map.of("error", String.valueOf(e.getMessage()));
        } catch (IllegalArgumentException e) {
            code = 400; out = Map.of("error", String.valueOf(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            code = 500; out = Map.of("error", "Server error: " + e.getMessage());
        }
        byte[] data = gson.toJson(out).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    static Object route(String m, String[] p, Map<String, String> q, Map<String, Object> b, int actor) throws Exception {
        Map<String, String> ok = Map.of("status", "ok");
        switch (p[0]) {
            case "users" -> {
                if (m.equals("GET")) return Database.users();
                if (m.equals("POST")) { Database.addUser(str(b, "userName"), str(b, "email"), str(b, "contactInfo"), str(b, "userRole"), actor); return ok; }
            }
            case "locations" -> {
                if (p.length == 1 && m.equals("GET")) return Database.locations(q.get("q"));
                if (p.length == 1 && m.equals("POST")) { Database.addLocation(str(b, "locationName"), str(b, "locationType"), num(b.get("capacity")), actor); return ok; }
                if (p.length == 2 && m.equals("PUT")) { Database.editLocation(Integer.parseInt(p[1]), str(b, "locationName"), str(b, "locationType"), num(b.get("capacity")), actor); return ok; }
                if (p.length == 2 && m.equals("DELETE")) { Database.deleteLocation(Integer.parseInt(p[1]), actor); return ok; }
                if (p.length == 3 && p[2].equals("crowd") && m.equals("POST")) return Database.updateCrowd(Integer.parseInt(p[1]), num(b.get("count")), actor);
            }
            case "alerts" -> {
                if (m.equals("GET")) return Database.alerts(q.get("status"));
                if (p.length == 3 && p[2].equals("status") && m.equals("POST")) { Database.setAlertStatus(Integer.parseInt(p[1]), str(b, "status"), actor); return ok; }
            }
            case "history" -> { if (m.equals("GET")) return Database.history(q.containsKey("locationId") ? Integer.parseInt(q.get("locationId")) : 0, 100); }
            case "analysis" -> { if (m.equals("GET")) return Database.analysis(); }
            default -> { }
        }
        throw new IllegalArgumentException("Unknown request.");
    }

    static String str(Map<String, Object> b, String k) { Object v = b.get(k); return v == null ? null : v.toString(); }

    static int num(Object v) {
        if (v == null || v.toString().isBlank()) return 0;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString().trim());
    }

    static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            String[] s = kv.split("=", 2);
            m.put(URLDecoder.decode(s[0], StandardCharsets.UTF_8), s.length > 1 ? URLDecoder.decode(s[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }
}
