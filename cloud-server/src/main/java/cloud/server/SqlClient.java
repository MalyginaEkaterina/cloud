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
            throw new RuntimeException(e);
        }
    }

    public void regUser(User user) {
        try {
            String insert = "INSERT INTO user (login, name, email, pass, salt, size, free_size) VALUES (?,?,?,?,?,?,?)";
            String insertRootDir = "INSERT INTO directory (user_id, path, path_hash, is_empty) VALUES (?, '/', UNHEX(SHA2('/',256)),1)";
            ArrayList<byte[]> saltHash = PasswordHash.hash(user.getPass());
            try {
                dbConnection.setAutoCommit(false);
                PreparedStatement pstmt = dbConnection.prepareStatement(insert);
                pstmt.setString(1, user.getLogin());
                pstmt.setString(2, user.getName());
                pstmt.setString(3, user.getEmail());
                pstmt.setBytes(4, saltHash.get(1));
                pstmt.setBytes(5, saltHash.get(0));
                pstmt.setLong(6, user.getMemSize());
                pstmt.setLong(7, user.getFreeMemSize());
                pstmt.executeUpdate();

                String select = "SELECT id FROM user WHERE login = ?";
                PreparedStatement pstmt2 = dbConnection.prepareStatement(select);
                pstmt2.setString(1, user.getLogin());
                ResultSet rs = pstmt2.executeQuery();
                if (rs.next()) {
                    PreparedStatement pstmt3 = dbConnection.prepareStatement(insertRootDir);
                    pstmt3.setInt(1, rs.getInt(1));
                    pstmt3.executeUpdate();
                    dbConnection.commit();
                } else {
                    dbConnection.rollback();
                }
                dbConnection.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
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
            throw new RuntimeException(e);
        }
    }

    public User checkLoginPass(String login, String pass) {
        try {
            String select = "SELECT pass, salt, id, name, email, size, free_size FROM user WHERE login = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(select);
            pstmt.setString(1, login);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                return null;
            }
            byte[] trueHash = rs.getBytes(1);
            byte[] salt = rs.getBytes(2);
            if (!PasswordHash.validatePass(pass, salt, trueHash)) {
                return null;
            }
            User user = new User(rs.getString(4), rs.getString(5), login);
            user.setId(rs.getInt(3));
            user.setMemSize(rs.getLong(6));
            user.setFreeMemSize(rs.getLong(7));
            return user;
        } catch (Exception e) {
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
            throw new RuntimeException(e);
        }
    }

    public Long createNewDir(int userid, FileDir d) {
        try {
            String insert = "INSERT INTO directory (user_id, path, path_hash, is_empty) VALUES (?, ?, UNHEX(SHA2(?,256)),0)";
            PreparedStatement pstmt = dbConnection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
            pstmt.setInt(1, userid);
            pstmt.setString(2, d.getPathStr());
            pstmt.setString(3, d.getPathStr());
            pstmt.executeUpdate();

            String path = d.getPathStr();
            if (path.contains("/")) {
                String parPath = path.substring(0, path.lastIndexOf("/"));
                String updateParentDir = "UPDATE directory d SET d.is_empty = 1 " +
                        "WHERE d.user_id = ? AND d.path_hash = UNHEX(SHA2(?, 256)) AND d.path = ?";
                PreparedStatement pstmt2 = dbConnection.prepareStatement(updateParentDir);
                pstmt2.setInt(1, userid);
                pstmt2.setString(2, parPath);
                pstmt2.setString(3, parPath);
                pstmt2.executeUpdate();
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                } else {
                    throw new RuntimeException("There is no new directory " + d.getPath());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Long startUploadFile(int userid, FileDir f) {
        try {
            String dirPath;
            if (!f.getPathStr().contains("/")) {
                dirPath = "/";
            } else {
                dirPath = f.getPathStr().substring(0, f.getPathStr().lastIndexOf("/"));
            }
            System.out.println("start 1st insert");
            String insert = "INSERT INTO file (user_id, filename, directory_id, size, load_state) \n" +
                    "SELECT ?, ?, d.id, ?, 2 FROM directory d \n" +
                    "WHERE d.user_id = ? AND d.path_hash = UNHEX(SHA2(?, 256)) AND d.path = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
            pstmt.setInt(1, userid);
            pstmt.setString(2, f.getName());
            pstmt.setLong(3, f.getSize());
            pstmt.setInt(4, userid);
            pstmt.setString(5, dirPath);
            pstmt.setString(6, dirPath);
            pstmt.executeUpdate();
            System.out.println("end 1st insert");
            String updateUser = "UPDATE user SET free_size = free_size - ? WHERE id = ?";
            PreparedStatement pstmt2 = dbConnection.prepareStatement(updateUser);
            pstmt2.setLong(1, f.getSize());
            pstmt2.setInt(2, userid);
            pstmt2.executeUpdate();
            System.out.println("end 2d insert");

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {

                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                } else {
                    throw new RuntimeException("insert into file was failed");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void endUploadFile(int userid, Long fid) {
        try {
            String update = "UPDATE file SET load_state = 1 WHERE user_id = ? AND id = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(update);
            pstmt.setInt(1, userid);
            pstmt.setLong(2, fid);
            pstmt.executeUpdate();

            String updateDir = "UPDATE directory d SET d.is_empty = 1 " +
                    "WHERE d.id = (SELECT f.directory_id FROM file f where f.user_id = ? AND f.id = ?)";
            PreparedStatement pstmt2 = dbConnection.prepareStatement(updateDir);
            pstmt2.setInt(1, userid);
            pstmt2.setLong(2, fid);
            pstmt2.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void endUploadFileWithError(int userid, Long fid, Long fSize) {
        try {
            String update = "UPDATE file SET load_state = 3 WHERE user_id = ? AND id = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(update);
            pstmt.setInt(1, userid);
            pstmt.setLong(2, fid);
            pstmt.executeUpdate();

            String updateUser = "UPDATE user SET free_size = free_size + ? WHERE id = ?";
            PreparedStatement pstmt2 = dbConnection.prepareStatement(updateUser);
            pstmt2.setLong(1, fSize);
            pstmt2.setInt(2, userid);
            pstmt2.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void rename(int userid, String newName, FileDir f) {
        try {
            if (f.getType() == ProtocolDict.TYPE_FILE) {
                String updateFile = "UPDATE file SET filename = ? WHERE user_id = ? AND id = ?";
                PreparedStatement pstmt = dbConnection.prepareStatement(updateFile);
                pstmt.setString(1, newName);
                pstmt.setInt(2, userid);
                pstmt.setLong(3, f.getId());
                pstmt.executeUpdate();
            } else {
                String newPath;
                String oldPath = f.getPathStr();
                if (oldPath.contains("/")) {
                    newPath = oldPath.substring(0, oldPath.lastIndexOf("/") + 1) + newName;
                } else {
                    newPath = newName;
                }
                String updateDir = "UPDATE directory SET path = CONCAT(?, SUBSTRING(path,?)), " +
                        "path_hash = UNHEX(SHA2(CONCAT(?, SUBSTRING(path,?)), 256)) " +
                        "WHERE user_id = ? AND path LIKE ?";
                PreparedStatement pstmt2 = dbConnection.prepareStatement(updateDir);
                pstmt2.setString(1, newPath);
                pstmt2.setInt(2, oldPath.length() + 1);
                pstmt2.setString(3, newPath);
                pstmt2.setInt(4, oldPath.length() + 1);
                pstmt2.setInt(5, userid);
                pstmt2.setString(6, oldPath+"%");
                pstmt2.executeUpdate();
            }
        } catch (Exception e) {
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
