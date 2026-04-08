package org.example.dao;

import org.example.db.DBConnection;
import org.example.model.ClassSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClassDao {

    public List<ClassRow> getAllClassesWithBookingForUser(int userId) throws SQLException {
        String sql = """
            SELECT c.id, c.name, c.instructor, c.class_time, c.spots_left,
                   (SELECT 1 FROM class_bookings b WHERE b.user_id=? AND b.class_id=c.id) AS booked
            FROM classes c
            ORDER BY c.class_time
        """;

        List<ClassRow> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    String instructor = rs.getString("instructor");
                    String time = rs.getString("class_time");
                    int spots = rs.getInt("spots_left");
                    boolean booked = rs.getObject("booked") != null;

                    out.add(new ClassRow(id, new ClassSession(name, instructor, time, spots), booked));
                }
            }
        }
        return out;
    }

    public void addClass(String name, String instructor, String time, int spots) throws SQLException {
        String sql = "INSERT INTO classes(name, instructor, class_time, spots_left) VALUES(?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, instructor);
            ps.setString(3, time);
            ps.setInt(4, spots);
            ps.executeUpdate();
        }
    }

    public void bookClass(int userId, int classId) throws SQLException {
        // rezervare + scade locuri daca mai sunt (transaction)
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // lock row
                int spots;
                try (PreparedStatement ps = con.prepareStatement("SELECT spots_left FROM classes WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, classId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Clasa nu exista.");
                        spots = rs.getInt(1);
                    }
                }

                if (spots <= 0) throw new SQLException("Nu mai sunt locuri.");

                // insert booking
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO class_bookings(user_id, class_id) VALUES(?,?)")) {
                    ps.setInt(1, userId);
                    ps.setInt(2, classId);
                    ps.executeUpdate();
                }

                // decrement spots
                try (PreparedStatement ps = con.prepareStatement("UPDATE classes SET spots_left=spots_left-1 WHERE id=?")) {
                    ps.setInt(1, classId);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public void cancelBooking(int userId, int classId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int deleted;
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM class_bookings WHERE user_id=? AND class_id=?")) {
                    ps.setInt(1, userId);
                    ps.setInt(2, classId);
                    deleted = ps.executeUpdate();
                }
                if (deleted > 0) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE classes SET spots_left=spots_left+1 WHERE id=?")) {
                        ps.setInt(1, classId);
                        ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public static class ClassRow {
        public final int classId;
        public final ClassSession session;
        public final boolean booked;

        public ClassRow(int classId, ClassSession session, boolean booked) {
            this.classId = classId;
            this.session = session;
            this.booked = booked;
        }
    }
}
