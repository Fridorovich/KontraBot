package org.altmir.db;

import org.altmir.dao.PendingGame;
import org.altmir.dao.Admin;
import org.altmir.dao.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:paintball_bot.db";
    private boolean initialized = false;

    public DatabaseManager() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        if (!initialized) {
            LiquibaseMigration.runMigrations();
            initialized = true;
        }
    }

    //проверка инициализации бд
    public User getUser(Long chatId) {
        initializeDatabase();
        String sql = "SELECT * FROM users WHERE chat_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new User(
                        rs.getLong("chat_id"),
                        rs.getString("username"),
                        rs.getInt("games_played"),
                        rs.getInt("bonus_points"),
                        LocalDateTime.parse(rs.getString("registration_date")),
                        rs.getBoolean("terms_accepted"),
                        rs.getBoolean("has_pending_request")
                );
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при получении пользователя: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public void saveUser(User user) {
        initializeDatabase();
        String sql = "INSERT OR REPLACE INTO users (chat_id, username, games_played, bonus_points, registration_date, terms_accepted) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, user.getChatId());
            pstmt.setString(2, user.getUsername());
            pstmt.setInt(3, user.getGamesPlayed());
            pstmt.setInt(4, user.getBonusPoints());
            pstmt.setString(5, user.getRegistrationDate().toString());
            pstmt.setBoolean(6, user.isTermsAccepted());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при сохранении пользователя: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateUserTermsAccepted(Long chatId, boolean accepted) {
        String sql = "UPDATE users SET terms_accepted = ? WHERE chat_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setBoolean(1, accepted);
            pstmt.setLong(2, chatId);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addGame(Long chatId) {
        String sql = "UPDATE users SET games_played = games_played + 1, bonus_points = bonus_points + 10 WHERE chat_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, chatId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addBonusPoints(Long chatId, int points) {
        String sql = "UPDATE users SET bonus_points = bonus_points + ? WHERE chat_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, points);
            pstmt.setLong(2, chatId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isAdmin(Long userId) {
        initializeDatabase();
        String sql = "SELECT COUNT(*) FROM admins WHERE user_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();

            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при проверке администратора: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public List<Admin> getAllAdmins() {
        initializeDatabase();
        List<Admin> admins = new ArrayList<>();
        String sql = "SELECT * FROM admins";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                admins.add(new Admin(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getLong("added_by"),
                        LocalDateTime.parse(rs.getString("added_date"))
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при получении администраторов: " + e.getMessage());
            e.printStackTrace();
        }
        return admins;
    }

    public void addAdmin(Long userId, String username, Long addedBy) {
        initializeDatabase();
        String sql = "INSERT OR REPLACE INTO admins (user_id, username, added_by, added_date) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.setString(2, username);
            pstmt.setLong(3, addedBy);
            pstmt.setString(4, LocalDateTime.now().toString());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при добавлении администратора: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void removeAdmin(Long userId) {
        initializeDatabase();
        String sql = "DELETE FROM admins WHERE user_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при удалении администратора: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Long addPendingGame(Long userChatId, String username) {
        initializeDatabase();
        String sql = "INSERT INTO pending_games (user_chat_id, username, request_date, status) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setLong(1, userChatId);
            pstmt.setString(2, username);
            pstmt.setString(3, LocalDateTime.now().toString());
            pstmt.setString(4, "PENDING");

            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при добавлении pending game: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public void updatePendingGameStatus(Long requestId, String status, Long processedBy) {
        initializeDatabase();
        String sql = "UPDATE pending_games SET status = ?, processed_by = ?, processed_date = ? WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setLong(2, processedBy);
            pstmt.setString(3, LocalDateTime.now().toString());
            pstmt.setLong(4, requestId);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при обновлении статуса игры: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public PendingGame getPendingGame(Long requestId) {
        initializeDatabase();
        String sql = "SELECT * FROM pending_games WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, requestId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                PendingGame game = new PendingGame();
                game.setId(rs.getLong("id"));
                game.setUserChatId(rs.getLong("user_chat_id"));
                game.setUsername(rs.getString("username"));
                game.setRequestDate(LocalDateTime.parse(rs.getString("request_date")));
                game.setStatus(rs.getString("status"));
                game.setProcessedBy(rs.getLong("processed_by"));
                if (rs.getString("processed_date") != null) {
                    game.setProcessedDate(LocalDateTime.parse(rs.getString("processed_date")));
                }
                return game;
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при получении pending game: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public List<Long> getAllAdminIds() {
        initializeDatabase();
        List<Long> adminIds = new ArrayList<>();
        String sql = "SELECT user_id FROM admins";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                adminIds.add(rs.getLong("user_id"));
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при получении ID администраторов: " + e.getMessage());
            e.printStackTrace();
        }
        return adminIds;
    }

    public List<String> getAdminList() {
        initializeDatabase();
        List<String> admins = new ArrayList<>();
        String sql = "SELECT user_id, username FROM admins";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                admins.add(String.format("@%s (ID: %d)",
                        rs.getString("username"),
                        rs.getLong("user_id")));
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при получении списка администраторов: " + e.getMessage());
            e.printStackTrace();
        }
        return admins;
    }

    public Long getUserChatIdFromRequest(Long requestId) {
        initializeDatabase();
        String sql = "SELECT user_chat_id FROM pending_games WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, requestId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getLong("user_chat_id");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при получении user_chat_id: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public boolean hasPendingRequest(Long chatId) {
        initializeDatabase();
        String sql = "SELECT has_pending_request FROM users WHERE chat_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getBoolean("has_pending_request");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при проверке pending request: " + e.getMessage());
        }
        return false;
    }

    public void setPendingRequestStatus(Long chatId, boolean status) {
        initializeDatabase();
        String sql = "UPDATE users SET has_pending_request = ? WHERE chat_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setBoolean(1, status);
            pstmt.setLong(2, chatId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("❌ Ошибка при обновлении статуса запроса: " + e.getMessage());
        }
    }
}