# CrowdCheck – Live Crowd Monitoring

Java 17 + Maven + JDBC + MySQL, with a web frontend and a console menu.

## 1. Set up the database (MySQL Workbench)
1. Open `database/schema.sql` in Workbench and run it (lightning icon). It creates the `crowdcheck` database, the tables and sample data.
2. To look at the tables any time, open `database/workbench_queries.sql` and run the queries one at a time.

## 2. Set your MySQL password
Open `src/main/java/com/crowdcheck/Database.java` and change `DB_USER` / `DB_PASS` (defaults: root / root).

## 3. Run (VS Code)
- Install the "Extension Pack for Java" in VS Code, then open this folder.
- Open `App.java` and click **Run**, or in the terminal: `mvn compile exec:java`
- Open **http://localhost:8080** in your browser.
- The console menu also works in the same terminal.

## Roles
Admin: everything · Organizer: locations + crowd + alerts · Staff: crowd + alerts · Visitor: view only.
Use the "Viewing as" dropdown (top right) to switch users.

## Crowd levels (percent of capacity)
LOW < 40% · MODERATE 40-69% · HIGH 70-89% · CRITICAL 90%+ . Alerts are created for HIGH and CRITICAL.
