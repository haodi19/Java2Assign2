package server;

import java.io.*;
import java.util.Objects;

public class Player implements Serializable {
    private static final long serialVersionUID = 999L;
    public static int tempId = -1;
    public volatile static int registerId;

    private Integer id;
    private String username;
    private String password;
    private int gameCounts;
    private int winCounts;
    private transient boolean inGame;
    private transient Game game;


    public static void init() throws IOException {
        File currentIdFile = new File("./src/server/player/registerId.txt");
        if (!currentIdFile.exists()) {
            currentIdFile.createNewFile();
            BufferedWriter bw = new BufferedWriter(new FileWriter(currentIdFile));
            bw.write("1");
            registerId = 1;
            bw.close();
        }else {
            BufferedReader br = new BufferedReader(new FileReader(currentIdFile));
            registerId = Integer.parseInt(br.readLine());
            br.close();
        }
    }

    public Player() {
        this.inGame = false;
        game = null;
    }

    public Player(Integer id) {
        this.id = id;
        this.inGame = false;
        game = null;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }


    public boolean isInGame() {
        return inGame;
    }

    public void setInGame(boolean inGame) {
        if (!inGame) {
            this.game = null;
        }
        this.inGame = inGame;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Game getGame() {
        if (!isInGame()) {
            return null;
        }
        return game;
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


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return id.equals(player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", gameCounts=" + gameCounts +
                ", winCounts=" + winCounts +
                '}';
    }
}
