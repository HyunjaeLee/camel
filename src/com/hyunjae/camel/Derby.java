package com.hyunjae.camel;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

class Derby {

    private Connection connection;

    Derby() throws Exception {
        Class.forName("org.apache.derby.jdbc.ClientDriver").newInstance();
        connection = DriverManager.getConnection("jdbc:derby://localhost:1527//camel");
    }

    void insertSeries(int id, String title, String thumbnailUrl, int timestamp, String day) {
        String sql = "INSERT INTO series VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE ";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, title);
            statement.setString(3, thumbnailUrl);
            statement.setInt(4, timestamp);
            statement.setString(5, day);
            statement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void insertEpisode(int id, String title, String thumbnailUrl, String videoUrl, int timestamp, int seriesId) {
        String sql = "INSERT INTO episode VALUES (?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, title);
            statement.setString(3, thumbnailUrl);
            statement.setString(4, videoUrl);
            statement.setInt(5, timestamp);
            statement.setInt(6, seriesId);
            statement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    List<Integer> selectSeriesId() {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT id FROM series";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next())
                list.add(resultSet.getInt(1));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    List<Integer> selectEpisodeId() {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT id FROM episode";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next())
                list.add(resultSet.getInt(1));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    void updateVideoUrl(String videoUrl, int episodeId) {
        String sql = "UPDATE episode SET video_url=? WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, videoUrl);
            statement.setInt(2, episodeId);
            statement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
