package cloud.server;

import cloud.common.FileDir;
import cloud.common.ProtocolDict;
import cloud.common.User;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.ArrayList;

public class SqlClient extends Configs {
    private Connection dbConnection;

    public void connect() {
        try {
            String connString = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName + "?" + dbParam;
            Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
            dbConnection = DriverManager.getConnection(connString, dbUser, dbPass);
        } catch (Exception e) {
            System.out.println("Connection failed...");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void regUser(User user) {

        String insert = "INSERT INTO user (login, name, email, pass, salt, size, free_size) VALUES (?,?,?,?,?,?,?)";
        try {
            ArrayList<byte[]> saltHash = PasswordHash.hash(user.getPass());
            try {
                PreparedStatement pstmt = dbConnection.prepareStatement(insert);
                pstmt.setString(1, user.getLogin());
                pstmt.setString(2, user.getName());
                pstmt.setString(3, user.getEmail());
                pstmt.setBytes(4, saltHash.get(1));
                pstmt.setBytes(5, saltHash.get(0));
                pstmt.setLong(6, 1024 * 1024 * 1024);
                pstmt.setLong(7, 1024 * 1024 * 1024);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public boolean checkLogin(String login) {
        try {
            String select = "SELECT * FROM user WHERE login = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(select);
            pstmt.setString(1, login);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public boolean checkLoginPass(String login, String pass, User user) {
        try {
            String select = "SELECT pass, salt, id, name, email, size, free_size FROM user WHERE login = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(select);
            pstmt.setString(1, login);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                return false;
            }
            byte[] trueHash = rs.getBytes(1);
            byte[] salt = rs.getBytes(2);
            boolean res = PasswordHash.validatePass(pass, salt, trueHash);
            if (res) {
                user.setLogin(login);
                user.setId(rs.getInt(3));
                user.setName(rs.getString(4));
                user.setEmail(rs.getString(5));
                user.setMemSize(rs.getLong(6));
                user.setFreeMemSize(rs.getLong(7));
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public ArrayList<FileDir> getStructureByID(int userid) {
        try {
            ArrayList<FileDir> arr = new ArrayList<>();
            String selectFiles = "SELECT f.id, f.size, d.path, f.filename FROM file f, directory d " +
                    "WHERE d.id = f.directory_id AND f.user_id = ? AND f.load_state = 1";
            PreparedStatement pstmt = dbConnection.prepareStatement(selectFiles);
            pstmt.setInt(1, userid);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String dirPath = rs.getString(3);
                String fileName = rs.getString(4);
                String path;
                if (dirPath.equals("/")) {
                    path = fileName;
                } else {
                    path = dirPath + "/" + fileName;
                }
                arr.add(new FileDir(ProtocolDict.TYPE_FILE, rs.getLong(1), rs.getLong(2), path));
            }

            String selectDir = "SELECT d.id, d.path FROM directory d WHERE d.user_id = ? AND d.is_empty = 0";
            PreparedStatement pstmt2 = dbConnection.prepareStatement(selectDir);
            pstmt2.setInt(1, userid);
            ResultSet rs2 = pstmt2.executeQuery();
            while (rs2.next()) {
                arr.add(new FileDir(ProtocolDict.TYPE_DIRECTORY, rs2.getLong(1), -1L, rs2.getString(2)));
            }

            return arr;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
        try {
            dbConnection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}