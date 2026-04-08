package org.example.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class DBConnection {

    private static final String url;
    private static final String user;
    private static final String pass;

    static {
        try (InputStream in = DBConnection.class.getResourceAsStream("/db.properties")) {
            if (in == null) throw new RuntimeException("Nu gasesc /db.properties in resources!");
            Properties p = new Properties();
            p.load(in);

            url = p.getProperty("DB_URL");
            user = p.getProperty("DB_USER");
            pass = p.getProperty("DB_PASS");
        } catch (Exception e) {
            throw new RuntimeException("Eroare la incarcarea configuratiei DB", e);
        }
    }

    private DBConnection() {}

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(url, user, pass);
        } catch (Exception e) {
            throw new RuntimeException("Nu pot face conexiunea la DB: " + e.getMessage(), e);
        }
    }
}
