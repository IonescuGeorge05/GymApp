package org.example.dao;

import org.example.db.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorkoutDao {

    public void addWorkout(int userId, String type, int durationMinutes, int calories) throws SQLException {
        String sql = "INSERT INTO workouts(user_id, workout_type, duration_minutes, calories) VALUES(?,?,?,?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            ps.setInt(3, durationMinutes);
            ps.setInt(4, calories);
            ps.executeUpdate();
        }
    }

    public int getTotalCalories(int userId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(calories),0) FROM workouts WHERE user_id=?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int getWorkoutsThisMonth(int userId) throws SQLException {
        String sql = """
            SELECT COUNT(*)
            FROM workouts
            WHERE user_id=?
              AND YEAR(performed_at)=YEAR(CURRENT_DATE())
              AND MONTH(performed_at)=MONTH(CURRENT_DATE())
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<String> getWorkoutHistory(int userId) throws SQLException {
        String sql = """
            SELECT workout_type, duration_minutes, calories, performed_at
            FROM workouts
            WHERE user_id=?
            ORDER BY performed_at DESC
        """;
        List<String> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("workout_type");
                    int dur = rs.getInt("duration_minutes");
                    int cal = rs.getInt("calories");
                    Timestamp ts = rs.getTimestamp("performed_at");
                    LocalDateTime dt = ts.toLocalDateTime();
                    out.add(type + " | " + dur + " min | " + cal + " kcal | " + dt.toLocalDate());
                }
            }
        }
        return out;
    }
}
