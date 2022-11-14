package application.account;

public class Account {
    private int id;
    private String userName;
    private String password;
    private int gameCounts;
    private int winCounts;


    public Account() {
    }

    public Account(int id, String userName, String password, int gameCounts, int winCounts) {
        this.id = id;
        this.userName = userName;
        this.password = password;
        this.gameCounts = gameCounts;
        this.winCounts = winCounts;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getGameCounts() {
        return gameCounts;
    }

    public void setGameCounts(int gameCounts) {
        this.gameCounts = gameCounts;
    }

    public int getWinCounts() {
        return winCounts;
    }

    public void setWinCounts(int winCounts) {
        this.winCounts = winCounts;
    }
}
