package server;

import java.util.Objects;

public class Player {
    private Integer id;
    private String IP;
    private Integer port;
    private boolean inGame;
    private Game game;

    public Player() {
        this.inGame = false;
        game = null;
    }

    public Player(Integer id, String IP, Integer port) {
        this.id = id;
        this.IP = IP;
        this.port = port;
        this.inGame = false;
        game = null;
    }

    public Player(String IP, Integer port) {
        this.IP = IP;
        this.port = port;
        this.inGame = false;
        game = null;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getIP() {
        return IP;
    }

    public void setIP(String IP) {
        this.IP = IP;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public boolean isInGame() {
        return inGame;
    }

    public void setInGame(boolean inGame) {
        if(!inGame){
            this.game = null;
        }
        this.inGame = inGame;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Game getGame(){
        if(!isInGame()){
            return null;
        }
        return game;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(IP, player.IP) && Objects.equals(port, player.port);
    }

    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", IP='" + IP + '\'' +
                ", port=" + port +
                ", inGame=" + inGame +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(IP, port);
    }
}
