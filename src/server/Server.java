package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server {
  private static ServerSocket serverSocket;
  private static final int port = 12345;

  private static ConcurrentHashMap<Player, Socket> socketMap;
  private static ConcurrentLinkedQueue<Player> matchingList;

  private static ObjectOutputStream oos;
  private static ObjectInputStream ois;
  private static final String CURRENT_FILE_PATH = "./src/server/player/";

  // 8 end chessboard:[111000000, 000111000, 000000111, 100100100, 010010010, 001001001, 100010001, 001010100]
  private static final int[] END_STATE = {448, 56, 7, 292, 146, 73, 273, 84};

  public Server() {
    try {
      socketMap = new ConcurrentHashMap<>();
      matchingList = new ConcurrentLinkedQueue<>();
      serverSocket = new ServerSocket(port);
      initialAccountDir();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void initialAccountDir() throws IOException {
    File file = new File(CURRENT_FILE_PATH);
    if (!file.exists()) {
      file.mkdir();
    }
    Player.init();
  }

  protected void start() {
    new Thread(this::acceptConnection).start();
    new Thread(this::match).start();
  }

  private void acceptConnection() {
    while (true) {
      try {
        Socket socket = serverSocket.accept();
        Player player = new Player(Player.tempId--);
        if (!socketMap.containsKey(player)) {
          socketMap.put(player, socket);
          Thread handleThread = new Thread(() -> handleReceivedMsg(player));
          handleThread.start();
        }

      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private void match() {
    while (true) {
      while (matchingList != null && matchingList.size() >= 2) {
        Player p1 = matchingList.poll();
        Player p2 = matchingList.poll();
        Game game = new Game(p1, p2);
        p1.setInGame(true);
        p2.setInGame(true);
        p1.setGame(game);
        p2.setGame(game);
        send(p1, "type:request-resp;content:symbol=1;");
        send(p2, "type:request-resp;content:symbol=2;");
      }

    }

  }

  private void handleReceivedMsg(Player player) {
    try {
      Socket socket = socketMap.get(player);
      if (socket == null) {
        return;
      }
      InputStream inputStream = socket.getInputStream();
      while (true) {
        byte[] bys = new byte[2048];
        int len = inputStream.read(bys);
        String[] msg = new String(bys, 0, len).split(";");
        Integer id = Integer.parseInt(msg[0].split(":")[1]);
        String type = msg[1].split(":")[1];
        switch (type) {
          case "match-request":
            handleMatchRequest(player);
            break;
          case "update-chessboard":
            handleOneStep(player, msg[2].split(":")[1], Integer.parseInt(msg[3].split(":")[1]), Integer.parseInt(msg[4].split(":")[1]));
            break;
          case "login-request":
            handleLoginRequest(player, msg[2].split(":")[1], msg[3].split(":")[1]);
            break;
          case "register-request":
            handleRegisterRequest(player, msg[2].split(":")[1], msg[3].split(":")[1]);
            break;
        }

      }
    } catch (IOException e) {
      //handle client-disconnect
      handleClientDisconnect(player);

    }

  }


  private void handleLoginRequest(Player player, String username, String password) {
    try {
      File accountFile = new File(CURRENT_FILE_PATH + username + ".txt");
      if (!accountFile.exists()) {
        send(player, "type:login-resp;content:id=-1&cnt=-1&win=-1;");
        return;
      }
      ois = new ObjectInputStream(new FileInputStream(accountFile));
      Player readPlayer = (Player) ois.readObject();
      if (!password.equals(readPlayer.getPassword()) || socketMap.containsKey(readPlayer)) {
        send(player, "type:login-resp;content:id=-1&cnt=-1&win=-1;");
        return;
      }

      Socket soc = socketMap.remove(player);
      player.setId(readPlayer.getId());
      player.setUsername(readPlayer.getUsername());
      player.setPassword(readPlayer.getPassword());
      player.setGameCounts(readPlayer.getGameCounts());
      player.setWinCounts(readPlayer.getWinCounts());
      socketMap.put(player, soc);

      String msg = String.format("type:login-resp;content:id=%s&cnt=%d&win=%d;", player.getId(), player.getGameCounts(), player.getWinCounts());
      send(player, msg);


    } catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
    }

  }

  private void handleRegisterRequest(Player player, String username, String password) {
    try {
      File accountFile = new File(CURRENT_FILE_PATH + username + ".txt");
      if (accountFile.exists()) {
        send(player, "type:register-resp;content:-1;");
        return;
      }
      writePlayer(username, password, accountFile);
      send(player, "type:register-resp;content:1;");


    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  private synchronized void writePlayer(String username, String password, File accountFile) throws IOException {
    oos = new ObjectOutputStream(new FileOutputStream(accountFile));
    Player p = new Player(Player.registerId++);
    p.setUsername(username);
    p.setPassword(password);
    oos.writeObject(p);
    BufferedWriter bw = new BufferedWriter(new FileWriter(CURRENT_FILE_PATH + "registerId.txt"));
    bw.write(String.valueOf(Player.registerId));
    bw.close();
  }

  private void handleClientDisconnect(Player player) {
    matchingList.remove(player);
    try {
      Socket soc = socketMap.remove(player);
      if (soc != null) {
        soc.close();
      }
    } catch (IOException ioException) {
      ioException.printStackTrace();
    }
    if (player.isInGame()) {
      Player anotherPlayer = player.getGame().getAnotherPlayer(player);
      player.setInGame(false);
      anotherPlayer.setInGame(false);
      String msg = "type:opponent-disconnect-resp;content:null;";
      send(anotherPlayer, msg);
    }
  }

  private void handleMatchRequest(Player player) {
    matchingList.add(player);
  }

  private void updatePlayersFile(Player player) throws IOException {
    oos = new ObjectOutputStream(new FileOutputStream(CURRENT_FILE_PATH + player.getUsername() + ".txt"));
    oos.writeObject(player);
  }

  private void handleOneStep(Player player, String newBoard, int x, int y) throws IOException {
    int _board;
    if (player.equals(player.getGame().getPlayer1())) {
      //keep p1 = 1,let p2 -> 0
      _board = Integer.parseInt(newBoard.replace('2', '0'), 2);
    } else {
      //let p1 -> 1, p2 -> 1
      _board = Integer.parseInt(newBoard.replace('1', '0').replace('2', '1'), 2);
    }

    if (hasPlayerWon(_board)) {
      //p1 wins
      Player anotherPlayer = player.getGame().getAnotherPlayer(player);
      player.setInGame(false);
      anotherPlayer.setInGame(false);

      player.setGameCounts(player.getGameCounts() + 1);
      anotherPlayer.setGameCounts(anotherPlayer.getGameCounts() + 1);
      player.setWinCounts(player.getWinCounts() + 1);

      updatePlayersFile(player);
      updatePlayersFile(anotherPlayer);

      String msg1 = "type:win-resp;content:null";
      String msg2 = String.format("type:lose-resp;content:x=%d&y=%d;", x, y);
      send(player, msg1);
      send(anotherPlayer, msg2);
    } else if (gameDraw(newBoard)) {
      //draw
      Player anotherPlayer = player.getGame().getAnotherPlayer(player);
      player.setInGame(false);
      anotherPlayer.setInGame(false);

      player.setGameCounts(player.getGameCounts() + 1);
      anotherPlayer.setGameCounts(anotherPlayer.getGameCounts() + 1);

      updatePlayersFile(player);
      updatePlayersFile(anotherPlayer);

      String msg1 = "type:draw-resp;content:null";
      String msg2 = String.format("type:draw-resp;content:x=%d&y=%d;", x, y);
      send(player, msg1);
      send(anotherPlayer, msg2);
    } else {
      //go on
      Player anotherPlayer = player.getGame().getAnotherPlayer(player);
      String msg = String.format("type:step-resp;content:x=%d&y=%d;", x, y);
      send(anotherPlayer, msg);
    }

  }


  private boolean gameDraw(String newBoard) {
    return !newBoard.contains("0");
  }

  private boolean hasPlayerWon(int _board) {
    for (int endState : END_STATE) {
      if ((_board & endState) == endState) {
        return true;
      }
    }
    return false;
  }

  private void send(Player player, String msg) {
    try {
      Socket soc = socketMap.get(player);
      while (soc == null) ;
      OutputStream os = soc.getOutputStream();
      os.write(msg.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      handleClientDisconnect(player);
    }
  }

}
