package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
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

    // 8 end chessboard:[111000000, 000111000, 000000111, 100100100, 010010010, 001001001, 100010001, 001010100]
    private static final int[] END_STATE = {448, 56, 7, 292, 146, 73, 273, 84};

    public Server() {
        try {
            socketMap = new ConcurrentHashMap<>();
            matchingList = new ConcurrentLinkedQueue<>();
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void start() {
        new Thread(this::acceptConnection).start();
        new Thread(this::match).start();
    }

    private void acceptConnection() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                InetAddress clientIP = socket.getInetAddress();
                Integer clientPort = socket.getPort();
                Player player = new Player(clientIP.getHostAddress(), clientPort);
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
                        handleRequest(player);
                        break;
                    case "update-chessboard":
                        handleOneStep(player, msg[2].split(":")[1], Integer.parseInt(msg[3].split(":")[1]), Integer.parseInt(msg[4].split(":")[1]));
                        break;
                }

            }
        } catch (IOException e) {
            //handle client-disconnect
            handleClientDisconnect(player);

        }

    }

    private void handleClientDisconnect(Player player) {
        matchingList.remove(player);
        try {
            socketMap.remove(player).close();
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

    private void handleRequest(Player player) {
        matchingList.add(player);
    }

    private void handleOneStep(Player player, String newBoard, int x, int y) {
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
            String msg1 = "type:win-resp;content:null";
            String msg2 = String.format("type:lose-resp;content:x=%d&y=%d;", x, y);
            send(player, msg1);
            send(anotherPlayer, msg2);
        } else if (gameDraw(newBoard)) {
            //draw
            Player anotherPlayer = player.getGame().getAnotherPlayer(player);
            player.setInGame(false);
            anotherPlayer.setInGame(false);
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
            OutputStream os = socketMap.get(player).getOutputStream();
            os.write(msg.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            handleClientDisconnect(player);
        }
    }

}
