package me.alfred.messagelogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DataBaseManager {

    private final String url;
    private final String username;
    private final String password;

    public DataBaseManager(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public void initializeDatabase() {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            String createTableQuery = "CREATE TABLE IF NOT EXISTS message_logs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_name VARCHAR(255)," +
                    "message_type VARCHAR(255)," +
                    "message VARCHAR(255)," +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            executeUpdate(connection, createTableQuery);

            String createIndexQuery = "CREATE INDEX IF NOT EXISTS idx_player_name ON message_logs (player_name)";
            executeUpdate(connection, createIndexQuery);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insertLog(String playerName, String messageType, String message) {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            String insertQuery = "INSERT INTO message_logs (player_name, message_type, message) VALUES (?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
                statement.setString(1, playerName);
                statement.setString(2, messageType);
                statement.setString(3, message);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getLogMessages(String playerName) {
        List<String> logLines = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            String selectQuery = "SELECT timestamp, player_name, message FROM message_logs WHERE player_name = ? ORDER BY timestamp ASC";
            try (PreparedStatement statement = connection.prepareStatement(selectQuery)) {
                statement.setString(1, playerName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String timestamp = resultSet.getTimestamp("timestamp").toString();
                        String messagePlayerName = resultSet.getString("player_name");
                        String message = resultSet.getString("message");
                        String logMessage = timestamp + " " + messagePlayerName + " " + message;
                        logLines.add(logMessage);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return logLines;
    }

    public void close() {
        // No specific close logic required for the database connection
    }

    private void executeUpdate(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(query);
        }
    }
}
