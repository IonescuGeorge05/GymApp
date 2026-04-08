package org.example.dao;

import org.example.db.DBConnection;
import org.example.model.User;

import java.sql.*;

public class UserDao {

    public User findByEmail(String email) throws SQLException {
        String sql = "SELECT id, email, full_name, password_hash, membership_type, sessions_left FROM users WHERE email=?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                // parola o citim separat în AuthService, nu o punem în User
                return new User(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("full_name"),
                        rs.getString("membership_type"),
                        rs.getInt("sessions_left")
                );
            }
        }
    }

    public String getPasswordHashByEmail(String email) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE email=?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        }
    }

    public User create(String email, String fullName, String passwordHash) throws SQLException {
        String sql = """
            INSERT INTO users(email, full_name, password_hash, membership_type, sessions_left)
            VALUES(?, ?, ?, 'Basic', 0)
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, fullName);
            ps.setString(3, passwordHash);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return new User(id, email, fullName, "Basic", 0);
            }
        }
    }
}
