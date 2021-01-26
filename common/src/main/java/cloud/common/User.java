package cloud.common;

public class User {
    private int id;
    private String name;
    private String email;
    private String login;
    private String pass;
    private long memSize;
    private long freeMemSize;

    public User(String name, String email, String login) {
        this.name = name;
        this.email = email;
        this.login = login;
    }

    public User(String name, String email, String login, String pass) {
        this(name, email, login);
        this.pass = pass;
    }

    public User() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getLogin() {
        return login;
    }

    public String getPass() {
        return pass;
    }

    public long getMemSize() {
        return memSize;
    }

    public long getFreeMemSize() {
        return freeMemSize;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setMemSize(long memSize) {
        this.memSize = memSize;
    }

    public void setFreeMemSize(long freeMemSize) {
        this.freeMemSize = freeMemSize;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", login='" + login + '\'' +
                ", pass='" + pass + '\'' +
                ", memSize=" + memSize +
                ", freeMemSize=" + freeMemSize +
                '}';
    }
}
