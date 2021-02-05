package cloud.server;

import cloud.common.FileDir;
import cloud.common.ProtocolDict;
import cloud.common.User;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SqlClient extends Configs {
    public static List<Connection> connectionList = Collections.synchronizedList(new ArrayList<Connection>());
    public static Semaphore semaphore = new Semaphore(dbMaxCountConnect);

    //при старте сервера создается пул открытых коннектов
    // доступ к пулу организован с помощью семафора
    // свободный коннект ожидается 10 секунд, потом отваливается с ошибкой
    public static void connect() throws SQLException {
        try {
            String connString = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName + "?" + dbParam;
            Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
            for (int i = 0; i < dbMaxCountConnect; i++) {
                connectionList.add(DriverManager.getConnection(connString, dbUser, dbPass));
            }
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    public static Connection getConnection() throws InterruptedException, SQLException {
        if (semaphore.tryAcquire(10, TimeUnit.SECONDS)) {
            return connectionList.remove(0);
        } else {
            throw new SQLException("There is no free connect to DB");
        }
    }

    public static void returnConnection(Connection dbConnect) {
        connectionList.add(dbConnect);
        semaphore.release();
    }

    public static void regUser(User user) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            String insert = "INSERT INTO user (login, name, email, pass, salt, size, free_size) VALUES (?,?,?,?,?,?,?)";
            String insertRootDir = "INSERT INTO directory (user_id, path, is_empty) VALUES (?, '/', 1)";
            ArrayList<byte[]> saltHash = PasswordHash.hash(user.getPass());
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
        } catch (SQLException | RuntimeException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static boolean checkLogin(String login) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            String select = "SELECT * FROM user WHERE login = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(select);
            pstmt.setString(1, login);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static User checkLoginPass(String login, String pass) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
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
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static ArrayList<FileDir> getStructureByID(int userid) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            ArrayList<FileDir> arr = new ArrayList<>();
            String selectFiles = "SELECT f.id, f.size, d.path, f.filename FROM file f, directory d " +
                    "WHERE d.id = f.directory_id AND f.user_id = ? AND f.load_state = 1";
            PreparedStatement pstmt = dbConnection.prepareStatement(selectFiles);
            pstmt.setInt(1, userid);
            ResultSet rs = pstmt.executeQuery();

            String selectDir = "SELECT d.id, d.path FROM directory d WHERE d.user_id = ? AND d.is_empty = 0";
            PreparedStatement pstmt2 = dbConnection.prepareStatement(selectDir);
            pstmt2.setInt(1, userid);
            ResultSet rs2 = pstmt2.executeQuery();

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

            while (rs2.next()) {
                arr.add(new FileDir(ProtocolDict.TYPE_DIRECTORY, rs2.getLong(1), -1L, rs2.getString(2)));
            }

            return arr;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static Long createNewDir(int userid, FileDir d) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            String insert = "INSERT INTO directory (user_id, path, is_empty) VALUES (?, ?, 0)";
            PreparedStatement pstmt = dbConnection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
            pstmt.setInt(1, userid);
            pstmt.setString(2, d.getPathStr());
            pstmt.executeUpdate();

            String path = d.getPathStr();
            if (path.contains("/")) {
                String parPath = path.substring(0, path.lastIndexOf("/"));
                String updateParentDir = "UPDATE directory d SET d.is_empty = 1 " +
                        "WHERE d.user_id = ? AND d.path = ?";
                PreparedStatement pstmt2 = dbConnection.prepareStatement(updateParentDir);
                pstmt2.setInt(1, userid);
                pstmt2.setString(2, parPath);
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
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static Long getFreeMemSize(int userid) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            String select = "SELECT free_size FROM user WHERE id = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(select);
            pstmt.setInt(1, userid);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            } else {
                throw new RuntimeException("there is no info about free size");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static Long startUploadFile(int userid, FileDir f) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            String dirPath;
            if (!f.getPathStr().contains("/")) {
                dirPath = "/";
            } else {
                dirPath = f.getPathStr().substring(0, f.getPathStr().lastIndexOf("/"));
            }
            String insert = "INSERT INTO file (user_id, filename, directory_id, size, load_state) \n" +
                    "SELECT ?, ?, d.id, ?, 2 FROM directory d \n" +
                    "WHERE d.user_id = ? AND d.path = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
            pstmt.setInt(1, userid);
            pstmt.setString(2, f.getName());
            pstmt.setLong(3, f.getSize());
            pstmt.setInt(4, userid);
            pstmt.setString(5, dirPath);
            pstmt.executeUpdate();

            String updateUser = "UPDATE user SET free_size = free_size - ? WHERE id = ?";
            PreparedStatement pstmt2 = dbConnection.prepareStatement(updateUser);
            pstmt2.setLong(1, f.getSize());
            pstmt2.setInt(2, userid);
            pstmt2.executeUpdate();

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
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static void endUploadFile(int userid, Long fid) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
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
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static void endUploadFileWithError(int userid, Long fid, Long fSize) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
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
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static void rename(int userid, String newName, FileDir f) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
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
                String updateDir = "UPDATE directory SET path = CONCAT(?, SUBSTRING(path,?)) " +
                        "WHERE user_id = ? AND path LIKE ?";
                PreparedStatement pstmt2 = dbConnection.prepareStatement(updateDir);
                pstmt2.setString(1, newPath);
                pstmt2.setInt(2, oldPath.length() + 1);
                pstmt2.setInt(3, userid);
                pstmt2.setString(4, oldPath + "%");
                pstmt2.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static ArrayList<Long> delete(int userid, FileDir f, Short emptyFlag) {
        Connection dbConnection;
        try {
            dbConnection = getConnection();
        } catch (InterruptedException | SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            ArrayList<Long> arr = new ArrayList<>();
            if (f.getType() == ProtocolDict.TYPE_FILE) {
                if (emptyFlag == 1) {
                    String updateDir = "UPDATE directory d SET d.is_empty = 0 " +
                            "WHERE d.id = (SELECT f.directory_id FROM file f where f.user_id = ? AND f.id = ?)";
                    PreparedStatement pstmt = dbConnection.prepareStatement(updateDir);
                    pstmt.setInt(1, userid);
                    pstmt.setLong(2, f.getId());
                    pstmt.executeUpdate();
                }

                String deleteFile = "DELETE FROM file f where f.user_id = ? AND f.id = ?";
                PreparedStatement pstmt2 = dbConnection.prepareStatement(deleteFile);
                pstmt2.setInt(1, userid);
                pstmt2.setLong(2, f.getId());
                pstmt2.executeUpdate();

                String updateUser = "UPDATE user SET free_size = free_size + ? WHERE id = ?";
                PreparedStatement pstmt3 = dbConnection.prepareStatement(updateUser);
                pstmt3.setLong(1, f.getSize());
                pstmt3.setInt(2, userid);
                pstmt3.executeUpdate();

                arr.add(f.getId());
            } else {
                long sizeOfDeleted = 0L;
                String selectFiles = "SELECT f.id, f.size FROM file f WHERE f.user_id = ? and \n" +
                        "f.directory_id IN (SELECT d.id FROM directory d WHERE d.user_id = ? and d.path like ?)";
                PreparedStatement pstmt = dbConnection.prepareStatement(selectFiles);
                pstmt.setInt(1, userid);
                pstmt.setInt(2, userid);
                pstmt.setString(3, f.getPathStr()+"%");
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    arr.add(rs.getLong(1));
                    sizeOfDeleted = sizeOfDeleted + rs.getLong(2);
                }
                if (arr.size() > 0) {
                    String deleteFiles = "DELETE FROM file f WHERE f.user_id = ? and \n" +
                            "f.directory_id IN (SELECT d.id FROM directory d WHERE d.user_id = ? and d.path like ?)";
                    PreparedStatement pstmt2 = dbConnection.prepareStatement(deleteFiles);
                    pstmt2.setInt(1, userid);
                    pstmt2.setInt(2, userid);
                    pstmt2.setString(3, f.getPathStr()+"%");
                    pstmt2.executeUpdate();
                }
                if (sizeOfDeleted > 0) {
                    String updateUser = "UPDATE user SET free_size = free_size + ? WHERE id = ?";
                    PreparedStatement pstmtU = dbConnection.prepareStatement(updateUser);
                    pstmtU.setLong(1, sizeOfDeleted);
                    pstmtU.setInt(2, userid);
                    pstmtU.executeUpdate();
                }
                String deleteDirs = "DELETE FROM directory d WHERE d.user_id = ? and d.path like ?";
                PreparedStatement pstmtD = dbConnection.prepareStatement(deleteDirs);
                pstmtD.setInt(1, userid);
                pstmtD.setString(2, f.getPathStr()+"%");
                pstmtD.executeUpdate();

                if (f.getPathStr().contains("/") && emptyFlag == 1) {
                    String parPath = f.getPathStr().substring(0,f.getPathStr().lastIndexOf("/"));
                    String updateParentDir = "UPDATE directory d SET d.is_empty = 0 " +
                            "WHERE d.user_id = ? AND d.path = ?";
                    PreparedStatement pstmtP = dbConnection.prepareStatement(updateParentDir);
                    pstmtP.setInt(1, userid);
                    pstmtP.setString(2, parPath);
                    pstmtP.executeUpdate();
                }
            }
            return arr;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            returnConnection(dbConnection);
        }
    }

    public static void disconnect() throws SQLException {
        for (Connection dbConnect : connectionList) {
            dbConnect.close();
        }
    }

}
