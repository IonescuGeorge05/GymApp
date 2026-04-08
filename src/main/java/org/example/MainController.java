package org.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.example.db.DBConnection;
import org.example.model.ClassSession;
import org.mindrot.jbcrypt.BCrypt;

import java.io.InputStream;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainController {

    @FXML private AnchorPane profileAnchor, classesAnchor, statsAnchor, aiAnchor;
    @FXML private StackPane contentStack;
    @FXML private Button btnProfile, btnClasses, btnStats, btnAI;

    // === CONFIG ===
    // va fi citita din src/main/resources/config.properties: OPENAI_API_KEY=...
    private String apiKey;

    private Member currentMember;

    // === STATS ===
    private int totalCalories = 0;
    private int workoutsThisMonth = 0;

    private Label caloriesLabel;
    private Label workoutsLabel;
    private Label avgCalLbl;
    private Label avgDurLbl;


    // === WORKOUTS ===
    private List<WorkoutRow> workoutHistory = new ArrayList<>();

    // === CLASSES ===
    private VBox classesContainer;

    // === AI COACH CHAT ===
    private final List<JsonObject> coachMessages = new ArrayList<>();

    // ======================= INIT =======================

    @FXML
    public void initialize() {
        loadApiKeyFromConfig();

        // ascundem tab-ul AI din UI (nu-l mai folosim)
        if (btnAI != null) {
            btnAI.setVisible(false);
            btnAI.setManaged(false);
        }
        if (aiAnchor != null) {
            aiAnchor.setVisible(false);
            aiAnchor.setManaged(false);
        }

        Member logged = requireAuth();
        if (logged == null) {
            Platform.exit();
            return;
        }
        currentMember = logged;

        setupProfile();
        setupClasses();
        setupStats();

        showTab(profileAnchor);

        btnProfile.setOnAction(e -> showTab(profileAnchor));
        btnClasses.setOnAction(e -> showTab(classesAnchor));
        btnStats.setOnAction(e -> showTab(statsAnchor));
    }

    private void showTab(AnchorPane tab) {
        profileAnchor.setVisible(false);
        classesAnchor.setVisible(false);
        statsAnchor.setVisible(false);
        if (aiAnchor != null) aiAnchor.setVisible(false);
        tab.setVisible(true);
    }

    // ======================= CONFIG =======================

    private void loadApiKeyFromConfig() {
        Properties p = new Properties();
        try (InputStream in = MainController.class.getResourceAsStream("/config.properties")) {
            if (in == null) {
                apiKey = null;
                return;
            }
            p.load(in);
            apiKey = p.getProperty("OPENAI_API_KEY");
            if (apiKey != null) apiKey = apiKey.trim();
            if (apiKey != null && apiKey.isBlank()) apiKey = null;
        } catch (Exception e) {
            apiKey = null;
        }
    }

    // ======================= AUTH =======================

    private Member requireAuth() {
        while (true) {
            ChoiceDialog<String> modeDialog = new ChoiceDialog<>("Login", "Login", "Register");
            modeDialog.setTitle("Autentificare");
            modeDialog.setHeaderText("Alege o optiune");
            modeDialog.setContentText("Mod:");

            Optional<String> modeOpt = modeDialog.showAndWait();
            if (modeOpt.isEmpty()) return null;

            String mode = modeOpt.get();
            if ("Login".equals(mode)) {
                Member m = showLoginDialog();
                if (m != null) return m;
            } else {
                Member m = showRegisterDialog();
                if (m != null) return m;
            }
        }
    }

    private Member showLoginDialog() {
        Dialog<Member> dialog = new Dialog<>();
        dialog.setTitle("Login");
        dialog.setHeaderText("Introdu email si parola");

        TextField tfEmail = new TextField();
        tfEmail.setPromptText("email");

        PasswordField tfPass = new PasswordField();
        tfPass.setPromptText("parola");

        VBox box = new VBox(10, new Label("Email:"), tfEmail, new Label("Parola:"), tfPass);
        box.setStyle("-fx-padding: 10;");
        dialog.getDialogPane().setContent(box);

        ButtonType btnLogin = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnLogin, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> {
            if (bt == btnLogin) {
                try {
                    return login(tfEmail.getText(), tfPass.getText());
                } catch (Exception ex) {
                    showError("Login esuat", ex.getMessage());
                    return null;
                }
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    private Member showRegisterDialog() {
        Dialog<Member> dialog = new Dialog<>();
        dialog.setTitle("Register");
        dialog.setHeaderText("Creeaza cont nou");

        TextField tfName = new TextField();
        tfName.setPromptText("nume complet");

        TextField tfEmail = new TextField();
        tfEmail.setPromptText("email");

        PasswordField tfPass = new PasswordField();
        tfPass.setPromptText("parola");

        PasswordField tfPass2 = new PasswordField();
        tfPass2.setPromptText("repeta parola");

        VBox box = new VBox(
                10,
                new Label("Nume:"), tfName,
                new Label("Email:"), tfEmail,
                new Label("Parola:"), tfPass,
                new Label("Repeta parola:"), tfPass2
        );
        box.setStyle("-fx-padding: 10;");
        dialog.getDialogPane().setContent(box);

        ButtonType btnRegister = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnRegister, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> {
            if (bt == btnRegister) {
                try {
                    if (!tfPass.getText().equals(tfPass2.getText())) {
                        showError("Register esuat", "Parolele nu coincid.");
                        return null;
                    }
                    return register(tfName.getText(), tfEmail.getText(), tfPass.getText());
                } catch (Exception ex) {
                    showError("Register esuat", ex.getMessage());
                    return null;
                }
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    // IMPORTANT: Member trebuie sa aiba constructor:
    // new Member(int id, String name, String membershipType, LocalDate membershipExpiresAt)
    private Member login(String email, String plainPassword) throws Exception {
        email = (email == null ? "" : email.trim().toLowerCase());
        plainPassword = (plainPassword == null ? "" : plainPassword);

        if (email.isBlank() || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Completeaza email si parola.");
        }

        String sql = """
            SELECT
                id,
                full_name,
                password_hash,
                membership_type,
                membership_expires_at
            FROM users
            WHERE email=?
        """;

        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Email sau parola gresita.");

                String hash = rs.getString("password_hash");
                if (!BCrypt.checkpw(plainPassword, hash)) {
                    throw new IllegalArgumentException("Email sau parola gresita.");
                }

                int id = rs.getInt("id");
                String name = rs.getString("full_name");
                String membershipType = rs.getString("membership_type");

                Date exp = rs.getDate("membership_expires_at");
                LocalDate expiresAt = (exp == null) ? null : exp.toLocalDate();

                return new Member(id, name, membershipType, expiresAt);
            }
        }
    }

    private Member register(String fullName, String email, String plainPassword) throws Exception {
        fullName = (fullName == null ? "" : fullName.trim());
        email = (email == null ? "" : email.trim().toLowerCase());
        plainPassword = (plainPassword == null ? "" : plainPassword);

        if (fullName.isBlank() || email.isBlank() || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Completeaza toate campurile.");
        }
        if (!email.contains("@") || !email.contains(".")) {
            throw new IllegalArgumentException("Email invalid.");
        }
        if (plainPassword.length() < 6) {
            throw new IllegalArgumentException("Parola trebuie sa aiba minim 6 caractere.");
        }

        // check existent
        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE email=?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) throw new IllegalArgumentException("Email deja folosit.");
            }
        }

        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));

        String insert = """
            INSERT INTO users(
                email,
                full_name,
                password_hash,
                membership_type,
                membership_expires_at
            )
            VALUES(?,?,?,?, DATE_ADD(CURDATE(), INTERVAL 30 DAY))
        """;

        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, email);
            ps.setString(2, fullName);
            ps.setString(3, hash);
            ps.setString(4, "Basic");

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("Nu am primit ID dupa insert.");
                int id = keys.getInt(1);

                LocalDate expiresAt = LocalDate.now().plusDays(30);
                return new Member(id, fullName, "Basic", expiresAt);
            }
        }
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.initModality(Modality.APPLICATION_MODAL);
        a.showAndWait();
    }

    // ======================= PROFILE =======================

    private void setupProfile() {
        profileAnchor.getChildren().clear();

        VBox container = new VBox(15);
        container.setStyle("-fx-alignment: center;");

        Label title = new Label("👤 profilul tau");
        title.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");

        Label id = new Label("id: " + currentMember.getId());
        Label name = new Label("nume: " + currentMember.getName());
        Label type = new Label("abonament: " + currentMember.getMembershipType());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String expText = (currentMember.getMembershipExpiresAt() == null)
                ? "—"
                : currentMember.getMembershipExpiresAt().format(fmt);
        Label exp = new Label("expira la: " + expText);

        if (currentMember.getMembershipExpiresAt() != null &&
                currentMember.getMembershipExpiresAt().isBefore(LocalDate.now())) {
            exp.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold;");
        }

        container.getChildren().addAll(title, id, name, type, exp);

        AnchorPane.setTopAnchor(container, 50.0);
        AnchorPane.setLeftAnchor(container, 0.0);
        AnchorPane.setRightAnchor(container, 0.0);

        profileAnchor.getChildren().add(container);
    }

    // ======================= CLASSES (NO ADD BUTTON) =======================

    private void setupClasses() {
        classesAnchor.getChildren().clear();

        classesContainer = new VBox(15);
        classesContainer.setStyle("-fx-alignment: center;");

        Label title = new Label("📋 clase disponibile");
        title.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");

        VBox wrapper = new VBox(20, title, classesContainer);
        wrapper.setStyle("-fx-alignment: center;");

        AnchorPane.setTopAnchor(wrapper, 50.0);
        AnchorPane.setLeftAnchor(wrapper, 0.0);
        AnchorPane.setRightAnchor(wrapper, 0.0);

        classesAnchor.getChildren().add(wrapper);

        refreshClassesFromDb();
    }

    private void refreshClassesFromDb() {
        classesContainer.getChildren().clear();

        String sql = """
            SELECT c.id, c.name, c.instructor, c.class_time, c.spots_left,
                   (SELECT 1 FROM class_bookings b WHERE b.user_id=? AND b.class_id=c.id) AS booked
            FROM classes c
            ORDER BY c.class_time
        """;

        try (java.sql.Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, currentMember.getId());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int classId = rs.getInt("id");
                    String name = rs.getString("name");
                    String instructor = rs.getString("instructor");
                    String time = rs.getString("class_time");
                    int spots = rs.getInt("spots_left");
                    boolean booked = rs.getObject("booked") != null;

                    ClassSession cs = new ClassSession(name, instructor, time, spots);
                    addClassToUIFromDb(classId, cs, booked);
                }
            }
        } catch (Exception e) {
            showError("DB error", e.getMessage());
        }
    }

    private void addClassToUIFromDb(int classId, ClassSession c, boolean booked) {
        Label text = new Label(
                c.getName() + " | " + c.getInstructor() + " | " + c.getTime() + " | locuri: " + c.getSpotsLeft()
        );

        Button action = new Button(booked ? "anuleaza" : "rezerva");
        action.setOnAction(e -> {
            try {
                // optional: daca abonamentul e expirat, blocam rezervarile
                if (!booked && currentMember.getMembershipExpiresAt() != null &&
                        currentMember.getMembershipExpiresAt().isBefore(LocalDate.now())) {
                    showError("Abonament expirat", "Nu poti rezerva clase cu abonamentul expirat.");
                    return;
                }

                if (booked) cancelBooking(currentMember.getId(), classId);
                else bookClass(currentMember.getId(), classId);

                refreshClassesFromDb();
            } catch (Exception ex) {
                showError("Eroare", ex.getMessage());
            }
        });

        HBox row = new HBox(15, text, action);
        row.setStyle("-fx-alignment: center;");

        classesContainer.getChildren().add(row);
    }

    private void bookClass(int userId, int classId) throws Exception {
        try (java.sql.Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int spots;

                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT spots_left FROM classes WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, classId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new java.sql.SQLException("Clasa nu exista.");
                        spots = rs.getInt(1);
                    }
                }

                if (spots <= 0) throw new java.sql.SQLException("Nu mai sunt locuri.");

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO class_bookings(user_id, class_id) VALUES(?,?)")) {
                    ps.setInt(1, userId);
                    ps.setInt(2, classId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE classes SET spots_left=spots_left-1 WHERE id=?")) {
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

    private void cancelBooking(int userId, int classId) throws Exception {
        try (java.sql.Connection con = DBConnection.getConnection()) {
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

    // ======================= STATS + CALENDAR + WORKOUTS CRUD =======================

    private void setupStats() {
        statsAnchor.getChildren().clear();

        reloadStatsFromDb();     // totalCalories, workoutsThisMonth, workoutHistory
           // activeDaysSet

        VBox container = new VBox(12);
        container.setStyle("-fx-alignment: center;");

        Label title = new Label("📊 progresul tau");
        title.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");

        caloriesLabel = new Label("🔥 calorii arse: " + totalCalories);
        workoutsLabel = new Label("💪 antrenamente luna asta: " + workoutsThisMonth);

        double avgCal = 0;
        double avgDur = 0;
        try {
            avgCal = getAvgCalories(currentMember.getId());
            avgDur = getAvgDuration(currentMember.getId());
        } catch (Exception e) {
            showError("DB error", e.getMessage());
        }

        avgCalLbl = new Label("📈 medie calorii/antrenament: " + String.format(Locale.US, "%.1f", avgCal));
        avgDurLbl = new Label("⏱ medie minute/antrenament: " + String.format(Locale.US, "%.1f", avgDur));


        Button addWorkoutBtn = new Button("+ adauga antrenament");
        addWorkoutBtn.setOnAction(e -> showAddWorkoutPopup());

        Button historyBtn = new Button("📜 istoric antrenamente (edit/sterge)");
        historyBtn.setOnAction(e -> showWorkoutHistory());

        Button aiCoachBtn = new Button("🤖 Sfaturi antrenor AI");
        aiCoachBtn.setOnAction(e -> openCoachChat());

        container.getChildren().addAll(
                title,
                caloriesLabel,
                workoutsLabel,
                avgCalLbl,
                avgDurLbl,
                addWorkoutBtn,
                historyBtn,
                aiCoachBtn
        );

        AnchorPane.setTopAnchor(container, 50.0);
        AnchorPane.setLeftAnchor(container, 0.0);
        AnchorPane.setRightAnchor(container, 0.0);

        statsAnchor.getChildren().add(container);
    }

    private void reloadStatsFromDb() {
        try {
            totalCalories = getTotalCalories(currentMember.getId());
            workoutsThisMonth = getWorkoutsThisMonth(currentMember.getId());
            workoutHistory = getWorkoutHistory(currentMember.getId());
        } catch (Exception e) {
            showError("DB error", e.getMessage());
            totalCalories = 0;
            workoutsThisMonth = 0;
            workoutHistory = new ArrayList<>();
        }
    }


    private int getTotalCalories(int userId) throws Exception {
        String sql = "SELECT COALESCE(SUM(calories),0) FROM workouts WHERE user_id=?";
        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int getWorkoutsThisMonth(int userId) throws Exception {
        String sql = """
            SELECT COUNT(*)
            FROM workouts
            WHERE user_id=?
              AND YEAR(performed_at)=YEAR(CURRENT_DATE())
              AND MONTH(performed_at)=MONTH(CURRENT_DATE())
        """;
        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private double getAvgCalories(int userId) throws Exception {
        String sql = "SELECT COALESCE(AVG(calories),0) FROM workouts WHERE user_id=?";
        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private double getAvgDuration(int userId) throws Exception {
        String sql = "SELECT COALESCE(AVG(duration_minutes),0) FROM workouts WHERE user_id=?";
        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private List<LocalDate> getActiveDays(int userId) throws Exception {
        String sql = """
            SELECT DISTINCT DATE(performed_at) AS day
            FROM workouts
            WHERE user_id=?
            ORDER BY day
        """;
        List<LocalDate> days = new ArrayList<>();
        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("day");
                    if (d != null) days.add(d.toLocalDate());
                }
            }
        }
        return days;
    }

    // ---- WORKOUT ROW MODEL ----
    private static class WorkoutRow {
        int id;
        String type;
        int duration;
        int calories;
        Timestamp performedAt;

        WorkoutRow(int id, String type, int duration, int calories, Timestamp performedAt) {
            this.id = id;
            this.type = type;
            this.duration = duration;
            this.calories = calories;
            this.performedAt = performedAt;
        }

        @Override
        public String toString() {
            LocalDate d = performedAt.toLocalDateTime().toLocalDate();
            return type + " | " + duration + " min | " + calories + " kcal | " + d;
        }
    }

    private List<WorkoutRow> getWorkoutHistory(int userId) throws Exception {
        String sql = """
            SELECT id, workout_type, duration_minutes, calories, performed_at
            FROM workouts
            WHERE user_id=?
            ORDER BY performed_at DESC
        """;
        List<WorkoutRow> out = new ArrayList<>();
        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new WorkoutRow(
                            rs.getInt("id"),
                            rs.getString("workout_type"),
                            rs.getInt("duration_minutes"),
                            rs.getInt("calories"),
                            rs.getTimestamp("performed_at")
                    ));
                }
            }
        }
        return out;
    }

    private void addWorkoutToDb(int userId, String type, int duration, int calories) throws Exception {
        String sql = "INSERT INTO workouts(user_id, workout_type, duration_minutes, calories) VALUES(?,?,?,?)";
        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            ps.setInt(3, duration);
            ps.setInt(4, calories);
            ps.executeUpdate();
        }
    }

    private void deleteWorkout(int workoutId) throws Exception {
        String sql = "DELETE FROM workouts WHERE id=? AND user_id=?";
        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, workoutId);
            ps.setInt(2, currentMember.getId());
            ps.executeUpdate();
        }
    }

    private void updateWorkout(int workoutId, String type, int duration, int calories) throws Exception {
        String sql = """
            UPDATE workouts
            SET workout_type=?, duration_minutes=?, calories=?
            WHERE id=? AND user_id=?
        """;
        try (java.sql.Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setInt(2, duration);
            ps.setInt(3, calories);
            ps.setInt(4, workoutId);
            ps.setInt(5, currentMember.getId());
            ps.executeUpdate();
        }
    }

    private void showAddWorkoutPopup() {
        Stage popup = new Stage();
        popup.setTitle("adauga antrenament");

        VBox root = new VBox(10);
        root.setStyle("-fx-padding: 20; -fx-alignment: center; -fx-background-color: #121212;");

        TextField tfType = new TextField();
        tfType.setPromptText("tip antrenament");

        TextField tfDuration = new TextField();
        tfDuration.setPromptText("durata (minute)");

        TextField tfCalories = new TextField();
        tfCalories.setPromptText("calorii arse");

        Button btnAdd = new Button("adauga");
        btnAdd.setStyle("-fx-background-color: #1db954; -fx-text-fill: black;");

        btnAdd.setOnAction(e -> {
            try {
                String type = tfType.getText().trim();
                int duration = Integer.parseInt(tfDuration.getText().trim());
                int calories = Integer.parseInt(tfCalories.getText().trim());

                if (type.isBlank()) throw new IllegalArgumentException("Scrie tipul antrenamentului.");

                addWorkoutToDb(currentMember.getId(), type, duration, calories);

                // refacem tot stats tab (recalcule + calendar highlight)
                setupStats();
                showTab(statsAnchor);

                popup.close();
            } catch (Exception ex) {
                showError("Input invalid", ex.getMessage());
            }
        });

        root.getChildren().addAll(tfType, tfDuration, tfCalories, btnAdd);
        popup.setScene(new Scene(root, 300, 270));
        popup.show();
    }

    private void showWorkoutHistory() {
        Stage stage = new Stage();
        stage.setTitle("istoric antrenamente");

        VBox root = new VBox(10);
        root.setStyle("-fx-padding: 15; -fx-background-color: #1f1f2f;");

        Label title = new Label("📜 istoricul tau");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 16; -fx-font-weight: bold;");

        ListView<WorkoutRow> listView = new ListView<>();
        listView.getItems().addAll(workoutHistory);

        Button btnEdit = new Button("✏️ modifica");
        Button btnDelete = new Button("🗑 sterge");

        btnEdit.setOnAction(e -> {
            WorkoutRow sel = listView.getSelectionModel().getSelectedItem();
            if (sel == null) return;

            openEditWorkoutDialog(sel, () -> {
                setupStats();
                listView.getItems().setAll(workoutHistory);
            });
        });

        btnDelete.setOnAction(e -> {
            WorkoutRow sel = listView.getSelectionModel().getSelectedItem();
            if (sel == null) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmare");
            confirm.setHeaderText(null);
            confirm.setContentText("Sigur stergi antrenamentul selectat?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            try {
                deleteWorkout(sel.id);
                setupStats();
                listView.getItems().setAll(workoutHistory);
            } catch (Exception ex) {
                showError("DB error", ex.getMessage());
            }
        });

        HBox actions = new HBox(10, btnEdit, btnDelete);
        actions.setStyle("-fx-alignment: center;");

        root.getChildren().addAll(title, listView, actions);
        stage.setScene(new Scene(root, 460, 560));
        stage.show();
    }

    private void openEditWorkoutDialog(WorkoutRow w, Runnable onSaved) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("modifica antrenament");

        TextField tfType = new TextField(w.type);
        TextField tfDur = new TextField(String.valueOf(w.duration));
        TextField tfCal = new TextField(String.valueOf(w.calories));

        VBox box = new VBox(10,
                new Label("tip:"), tfType,
                new Label("durata (minute):"), tfDur,
                new Label("calorii:"), tfCal
        );
        box.setStyle("-fx-padding: 10;");
        dialog.getDialogPane().setContent(box);

        ButtonType save = new ButtonType("salveaza", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> {
            if (bt == save) {
                try {
                    String type = tfType.getText().trim();
                    int dur = Integer.parseInt(tfDur.getText().trim());
                    int cal = Integer.parseInt(tfCal.getText().trim());
                    if (type.isBlank()) throw new IllegalArgumentException("Tipul nu poate fi gol.");

                    updateWorkout(w.id, type, dur, cal);
                    onSaved.run();
                } catch (Exception ex) {
                    showError("Eroare", ex.getMessage());
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    // ======================= AI COACH (POPUP CHAT) =======================

    private void openCoachChat() {
        if (apiKey == null) {
            showError("AI", "Nu exista OPENAI_API_KEY in config.properties.");
            return;
        }

        Stage stage = new Stage();
        stage.setTitle("Sfaturi antrenor AI");

        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setStyle("-fx-control-inner-background: #1f1f2f; -fx-text-fill: #e6e6e6; -fx-font-size: 14;");

        TextField input = new TextField();
        input.setPromptText("scrie mesajul tau...");

        Button send = new Button("trimite");

        send.setOnAction(e -> {
            String msg = input.getText().trim();
            if (msg.isBlank()) return;
            input.clear();

            area.appendText("tu: " + msg + "\n");

            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", msg);
            coachMessages.add(user);

            new Thread(() -> {
                String resp = callCoachAI();
                Platform.runLater(() -> area.appendText("ai: " + resp + "\n\n"));
            }).start();
        });

        input.setOnAction(e -> send.fire());

        HBox bottom = new HBox(10, input, send);
        bottom.setStyle("-fx-padding: 10; -fx-alignment: center;");
        HBox.setHgrow(input, Priority.ALWAYS);

        VBox root = new VBox(10, area, bottom);
        root.setStyle("-fx-padding: 10; -fx-background-color: #121212;");
        VBox.setVgrow(area, Priority.ALWAYS);

        stage.setScene(new Scene(root, 540, 620));
        stage.show();

        if (coachMessages.isEmpty()) {
            new Thread(() -> {
                String first = firstCoachMessage();
                Platform.runLater(() -> area.appendText("ai: " + first + "\n\n"));
            }).start();
        }
    }

    private String firstCoachMessage() {
        try {
            // refresh ca sa fie “la zi”
              reloadStatsFromDb();


            double avgCal = getAvgCalories(currentMember.getId());
            double avgDur = getAvgDuration(currentMember.getId());

            coachMessages.clear();

            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content",
                    "Esti un antrenor personal. Raspunzi doar in romana. " +
                            "Incepi conversatia proactiv: 3 recomandari concrete + 2 intrebari scurte. " +
                            "Fii practic, fara texte lungi."
            );
            coachMessages.add(system);

            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content",
                    "Date utilizator:\n" +
                            "- nume: " + currentMember.getName() + "\n" +
                            "- antrenamente luna asta: " + workoutsThisMonth + "\n" +
                            "- calorii totale: " + totalCalories + "\n" +
                            "- medie calorii/antrenament: " + String.format(Locale.US, "%.1f", avgCal) + "\n" +
                            "- medie minute/antrenament: " + String.format(Locale.US, "%.1f", avgDur) + "\n" +
                            "- medie minute/antrenament: " + String.format(Locale.US, "%.1f", avgDur) + "\n" +
                            "Ofera sfaturi pentru urmatoarele 2 saptamani."
            );
            coachMessages.add(user);

            return callCoachAI();
        } catch (Exception e) {
            e.printStackTrace();
            return "Nu pot genera sfaturi acum (eroare DB).";
        }
    }

    private String callCoachAI() {
        try {
            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("application/json");

            JsonObject body = new JsonObject();
            body.addProperty("model", "gpt-4.1-mini");

            JsonArray messages = new JsonArray();
            for (JsonObject m : coachMessages) messages.add(m);
            body.add("messages", messages);

            RequestBody requestBody = RequestBody.create(body.toString(), mediaType);

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            Response response = client.newCall(request).execute();
            String json = (response.body() == null) ? "" : response.body().string();

            // 1) daca HTTP nu e 2xx, afisam eroarea reala
            if (!response.isSuccessful()) {
                // incearca sa extragi mesajul din JSON
                try {
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    if (root.has("error")) {
                        JsonObject err = root.getAsJsonObject("error");
                        String msg = err.has("message") ? err.get("message").getAsString() : "Eroare necunoscuta";
                        return "Eroare AI (" + response.code() + "): " + msg;
                    }
                } catch (Exception ignore) {}
                return "Eroare AI (" + response.code() + "): " + json;
            }

            // 2) parse normal
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // 3) daca API a intors totusi un obiect error
            if (root.has("error")) {
                JsonObject err = root.getAsJsonObject("error");
                String msg = err.has("message") ? err.get("message").getAsString() : "Eroare necunoscuta";
                return "Eroare AI: " + msg;
            }

            // 4) verifica choices
            if (!root.has("choices") || root.getAsJsonArray("choices") == null || root.getAsJsonArray("choices").size() == 0) {
                return "Eroare AI: raspuns invalid (lipseste 'choices').\n" + json;
            }

            String content = root.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            JsonObject assistant = new JsonObject();
            assistant.addProperty("role", "assistant");
            assistant.addProperty("content", content);
            coachMessages.add(assistant);

            return content;

        } catch (Exception e) {
            e.printStackTrace();
            return "eroare ai: " + e.getMessage();
        }
    }

}
