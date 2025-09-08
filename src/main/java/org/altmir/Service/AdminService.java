package org.altmir.Service;

import org.altmir.db.DatabaseManager;

import java.util.ArrayList;
import java.util.List;

public class AdminService {
    private final DatabaseManager databaseManager;

    public AdminService() {
        this.databaseManager = new DatabaseManager();
    }

    public boolean isAdmin(Long userId) {
        return databaseManager.isAdmin(userId);
    }

    public List<Long> getAllAdminIds() {
        return databaseManager.getAllAdminIds();
    }

    public List<String> getAdminList() {
        return databaseManager.getAdminList();
    }

    public void addAdmin(Long userId, String username, Long addedBy) {
        databaseManager.addAdmin(userId, username, addedBy);
    }

    public void removeAdmin(Long userId) {
        databaseManager.removeAdmin(userId);
    }

    public Long createGameRequest(Long userChatId, String username) {
        return databaseManager.addPendingGame(userChatId, username);
    }

    public void approveGameRequest(Long requestId, Long adminId) {
        databaseManager.updatePendingGameStatus(requestId, "APPROVED", adminId);

        // Добавляем игру пользователю
        Long userChatId = databaseManager.getUserChatIdFromRequest(requestId);
        if (userChatId != null) {
            databaseManager.addGame(userChatId);
        }
    }

    public void rejectGameRequest(Long requestId, Long adminId) {
        databaseManager.updatePendingGameStatus(requestId, "REJECTED", adminId);
    }

    public Long getUserChatIdFromRequest(Long requestId) {
        return databaseManager.getUserChatIdFromRequest(requestId);
    }

    public void addBonusPoints(Long userChatId, int points, Long adminId) {
        databaseManager.addBonusPoints(userChatId, points);
    }

    public void removeBonusPoints(Long userChatId, int points, Long adminId) {
        databaseManager.addBonusPoints(userChatId, -points);
    }
}