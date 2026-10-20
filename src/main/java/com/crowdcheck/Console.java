package com.crowdcheck;

import com.crowdcheck.Models.*;
import java.util.*;

/** Menu-driven console interface (runs alongside the web app). */
public class Console {
    static final Scanner in = new Scanner(System.in);

    static String ask(String m) { System.out.print(m); return in.nextLine().trim(); }

    static int askInt(String m) {
        while (true) {
            try { return Integer.parseInt(ask(m)); }
            catch (NumberFormatException e) { System.out.println("  Please enter a number."); }
        }
    }

    static void printLocations(List<Location> list) {
        System.out.printf("%-4s %-22s %-11s %-11s %-6s %-10s %s%n", "ID", "Location", "Type", "Count/Cap", "Full", "Level", "Last updated");
        for (Location l : list)
            System.out.printf("%-4d %-22s %-11s %-11s %-6s %-10s %s%n", l.locationId(), l.locationName(), l.locationType(),
                    l.currentCrowdCount() + "/" + l.capacity(), l.percent() + "%", l.crowdLevel(), l.lastUpdated());
        if (list.isEmpty()) System.out.println("  (nothing found)");
    }

    static void printAlerts(List<Alert> list) {
        System.out.printf("%-4s %-10s %-13s %-20s %s%n", "ID", "Level", "Status", "Time", "Message");
        for (Alert a : list)
            System.out.printf("%-4d %-10s %-13s %-20s %s%n", a.alertId(), a.crowdLevel(), a.status(), a.alertTime(), a.alertMessage());
        if (list.isEmpty()) System.out.println("  (no alerts)");
    }

    public static void run() throws Exception {
        System.out.println("\n=== CrowdCheck Console ===");
        for (User u : Database.users())
            System.out.printf("  %d  %-18s %s%n", u.userId(), u.userName(), u.userRole());
        int me = askInt("Log in - enter your user ID: ");

        while (true) {
            System.out.println("\n1. View live crowd status    6. Acknowledge an alert");
            System.out.println("2. Search locations          7. Resolve an alert");
            System.out.println("3. Add location              8. Crowd history");
            System.out.println("4. Update crowd count        9. Overcrowding analysis");
            System.out.println("5. View alerts              10. Add user      0. Exit");
            int ch = askInt("Choose: ");
            try {
                switch (ch) {
                    case 1 -> printLocations(Database.locations(null));
                    case 2 -> printLocations(Database.locations(ask("Search text: ")));
                    case 3 -> {
                        String name = ask("Name: ");
                        String type = ask("Type (ENTRANCE/FOOD_COURT/AUDITORIUM/PARKING): ").toUpperCase();
                        Database.addLocation(name, type, askInt("Capacity: "), me);
                        System.out.println("Location added.");
                    }
                    case 4 -> {
                        printLocations(Database.locations(null));
                        int id = askInt("Location ID: ");
                        Location l = Database.updateCrowd(id, askInt("Estimated crowd count: "), me);
                        System.out.println("Updated -> " + l.locationName() + " is " + l.crowdLevel() + " (" + l.percent() + "% full)");
                    }
                    case 5 -> printAlerts(Database.alerts(ask("Filter (ACTIVE/ACKNOWLEDGED/RESOLVED/ALL): ").toUpperCase()));
                    case 6 -> { Database.setAlertStatus(askInt("Alert ID: "), "ACKNOWLEDGED", me); System.out.println("Alert acknowledged."); }
                    case 7 -> { Database.setAlertStatus(askInt("Alert ID: "), "RESOLVED", me); System.out.println("Alert resolved."); }
                    case 8 -> {
                        System.out.printf("%-20s %-22s %-8s %-6s %-10s %s%n", "Time", "Location", "Count", "Full", "Level", "By");
                        for (CrowdUpdate u : Database.history(askInt("Location ID (0 = all): "), 30))
                            System.out.printf("%-20s %-22s %-8d %-6s %-10s %s%n", u.updateTime(), u.locationName(), u.crowdCount(), u.percent() + "%", u.crowdLevel(), u.updatedBy());
                    }
                    case 9 -> {
                        System.out.printf("%-22s %-9s %-14s %-9s %s%n", "Location", "Updates", "High/Critical", "Peak", "Overcrowded");
                        for (Map<String, Object> m : Database.analysis())
                            System.out.printf("%-22s %-9s %-14s %-9s %s%%%n", m.get("locationName"), m.get("totalUpdates"), m.get("overcrowdedUpdates"), m.get("peakCount"), m.get("overcrowdedPercent"));
                    }
                    case 10 -> {
                        Database.addUser(ask("Name: "), ask("Email: "), ask("Contact: "), ask("Role (ADMIN/ORGANIZER/STAFF/VISITOR): ").toUpperCase(), me);
                        System.out.println("User added.");
                    }
                    case 0 -> { System.out.println("Bye!"); System.exit(0); }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (SecurityException | IllegalArgumentException | java.sql.SQLException e) {
                System.out.println("! " + e.getMessage());
            }
        }
    }
}
