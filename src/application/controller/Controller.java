package application.controller;

import application.account.Account;
import client.Client;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * client thread:
 * [(first-time connection thread) started in (initializeClient), stopped];
 * [(receiveMsg) started in (initializeClient.first-time-connection), unstopped]
 * [(receiveStartGameMsg-thread) started in startGame, may be unstopped]
 * [(handleGameMsg) started in startGame, unstopped]
 * socket: only 1 socket overall, created when starts the process
 */

public class Controller implements Initializable {
  private int self;
  private int opponent;

  private static final int EMPTY = 0;
  private static final int CIRCLE = 1;
  private static final int CROSS = 2;

  private static final int BOUND = 90;
  private static final int OFFSET = 15;

  private int state;
  private static final int READY = 0;
  private static final int MY_TURN = 1;
  private static final int OPPONENT_TURN = 2;
  private static final int SERVER_ERROR = 3;
  private static final int MATCHING = 4;
  private static final int CONNECTING = 5;
  private static final int WIN = 6;
  private static final int LOSE = 7;
  private static final int DRAW = 8;
  private static final int OPPONENT_DISCONNECT = 9;
  private static final int NOT_LOGIN = 10;


  private Client client;

  private ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> msgList;

  private Account account;

  @FXML
  private Pane baseSquare;

  @FXML
  private Rectangle gamePanel;

  private int[][] chessBoard;
  private List<Node> chessList;

  private Button startButton;
  private Button loginButton;
  private Button registerButton;

  private VBox stateBoard;

  private VBox accountBoard;

  private Label stateText = new Label();
  private Label stateValue = new Label();

  private Label accountLabel = new Label();
  private Label gameCountsLabel = new Label();
  private Label winCountsLabel = new Label();

  private MessageWindow msgWindow;


  class MessageWindow {
    private Stage stage;

    private void displayAccountFunctionWindow(String mode) {
      stage = new Stage();
      stage.initModality(Modality.APPLICATION_MODAL);
      if ("login".equals(mode)) {
        stage.setTitle("Login");
      } else {
        stage.setTitle("Register");
      }


      VBox vBox = new VBox();
      vBox.setStyle("-fx-padding: 100; -fx-spacing: 15");

      Label loginMsg = new Label();
      loginMsg.setText(String.format("Please %s to continue", mode));

      Label usernameLabel = new Label();
      usernameLabel.setText("Username:  ");
      TextField usernameField = new TextField();
      HBox usernameBox = new HBox(usernameLabel, usernameField);

      Label passwordLabel = new Label();
      passwordLabel.setText("Password:   ");
      PasswordField passwordField = new PasswordField();
      HBox passwordBox = new HBox(passwordLabel, passwordField);

      Label loginFeedBackLabel = new Label();
      Label registerFeedBackLabel = new Label();

      Button submitButton = new Button("submit");
      if ("login".equals(mode)) {
        submitButton.setOnAction(event -> {
          submitButton.setDisable(true);
          try {
            String username = usernameField.getText();
            String password = passwordField.getText();
            if (!preCheckMsg(username, password)) {
              if (!vBox.getChildren().contains(loginFeedBackLabel)) {
                loginFeedBackLabel.setText("Login failed!");
                vBox.getChildren().add(loginFeedBackLabel);
              }
              submitButton.setDisable(false);
              return;
            }
            String msg = String.format("id:-1;type:login-request;username:%s;password:%s", username, password);
            client.send(msg);

            Thread handleRespThread = new Thread(() -> {
              int[] ints = handleLoginResp();
              int id = ints[0], gameCounts = ints[1], winCounts = ints[2];
              if (id == -1) {
                // login failed
                handleLoginFailure(vBox, loginFeedBackLabel, submitButton);
              } else {
                // login successfully
                handleLoginSuccess(username, password, id, gameCounts, winCounts);

              }
            });
            handleRespThread.setDaemon(true);
            handleRespThread.start();

          } catch (IOException e) {
            stage.close();
          }

        });

      } else {
        submitButton.setOnAction(event -> {
          submitButton.setDisable(true);
          try {
            String username = usernameField.getText();
            String password = passwordField.getText();
            if (!preCheckMsg(username, password)) {
              if (!vBox.getChildren().contains(registerFeedBackLabel)) {
                registerFeedBackLabel.setText("Register failed!");
                vBox.getChildren().add(registerFeedBackLabel);
              }
              submitButton.setDisable(false);
              return;
            }
            String msg = String.format("id:-1;type:register-request;username:%s;password:%s", username, password);
            client.send(msg);

            Thread handleRespThread = new Thread(() -> {
              int result = handleRegisterResp();
              if (result == -1) {
                // register failed
                handleRegisterFailure(vBox, registerFeedBackLabel, submitButton);

              } else {
                // register successfully
                handleRegisterSuccess();

              }
            });
            handleRespThread.setDaemon(true);
            handleRespThread.start();

          } catch (IOException e) {
            stage.close();
          }

        });


      }


      vBox.getChildren().addAll(loginMsg, usernameBox, passwordBox, submitButton);
      vBox.setAlignment(Pos.CENTER);
      Scene scene = new Scene(vBox, 500, 300);
      stage.setScene(scene);

      stage.showAndWait();
    }

    private void handleRegisterSuccess() {
      Platform.runLater(() -> {
        updateState(NOT_LOGIN);
        stage.close();
      });
    }

    private void handleRegisterFailure(VBox vBox, Label registerFeedBackLabel, Button submitButton) {
      Platform.runLater(() -> {
        if (!vBox.getChildren().contains(registerFeedBackLabel)) {
          registerFeedBackLabel.setText("Register failed!");
          vBox.getChildren().add(registerFeedBackLabel);
        }
        submitButton.setDisable(false);
      });
    }

    private void handleLoginSuccess(String username, String password, int id, int gameCounts, int winCounts) {
      account = new Account(id, username, password, gameCounts, winCounts);
      Platform.runLater(() -> {
        updateState(READY);
        startButton.setDisable(false);
        startButton.setVisible(true);
        loginButton.setVisible(false);
        registerButton.setVisible(false);
        accountLabel.setText("Player: " + username);
        gameCountsLabel.setText("Total games: " + gameCounts);
        winCountsLabel.setText("Win games: " + winCounts);
        accountBoard.setVisible(true);
        stage.close();
      });
    }

    private void handleLoginFailure(VBox vBox, Label loginFeedBackLabel, Button submitButton) {
      Platform.runLater(() -> {
        if (!vBox.getChildren().contains(loginFeedBackLabel)) {
          loginFeedBackLabel.setText("Login failed!");
          vBox.getChildren().add(loginFeedBackLabel);
        }
        submitButton.setDisable(false);
      });
    }

    private boolean preCheckMsg(String username, String password) {
      return username.length() > 0 && username.length() <= 10 && password.length() > 0 && !username.contains("&");
    }

    private int[] handleLoginResp() {
      while (msgList.get("login-resp") == null || msgList.get("login-resp").isEmpty()) {
        if (state == SERVER_ERROR) {
          return new int[]{-1, -1, -1};
        }
      }
      String[] content = msgList.get("login-resp").poll().split("&");
      int id = Integer.parseInt(content[0].split("=")[1]);
      int gameCounts = Integer.parseInt(content[1].split("=")[1]);
      int winCounts = Integer.parseInt(content[2].split("=")[1]);
      return new int[]{id, gameCounts, winCounts};
    }

    private int handleRegisterResp() {
      while (msgList.get("register-resp") == null || msgList.get("register-resp").isEmpty()) {
        if (state == SERVER_ERROR) {
          return -1;
        }
      }
      int result = Integer.parseInt(msgList.get("register-resp").poll());
      return result;
    }

  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    initializeGamePanel();
    initializeState();
    initializeMsgWindow();
    initializeLoginFunction();
    initializeRegisterFunction();
    initializeStartButton();
    initializeClient();
    initializeStateBoard();
    initializeAccountBoard();
  }

  private void initializeMsgWindow() {
    msgWindow = new MessageWindow();
  }

  private void initializeGamePanel() {
    gamePanel.setOnMouseClicked(this::clickChessBoard);
  }

  private void refreshChessBoard() {
    chessBoard = new int[3][3];
    if (chessList != null) {
      baseSquare.getChildren().removeAll(chessList);
    }
    chessList = new ArrayList<>();
  }

  private void initializeState() {
    updateState(CONNECTING);
  }

  private void initializeLoginFunction() {
    loginButton = new Button();
    loginButton.setLayoutX(55);
    loginButton.setLayoutY(330);
    loginButton.setText("Login");
    loginButton.setOnAction(event -> msgWindow.displayAccountFunctionWindow("login"));
    loginButton.setDisable(true);
    loginButton.setVisible(true);
    baseSquare.getChildren().add(loginButton);
  }

  private void initializeRegisterFunction() {
    registerButton = new Button();
    registerButton.setLayoutX(165);
    registerButton.setLayoutY(330);
    registerButton.setText("Register");
    registerButton.setOnAction(event -> msgWindow.displayAccountFunctionWindow("register"));
    registerButton.setDisable(true);
    registerButton.setVisible(true);
    baseSquare.getChildren().add(registerButton);
  }

  private void initializeStartButton() {
    startButton = new Button();
    startButton.setLayoutX(95);
    startButton.setLayoutY(330);
    startButton.setText("Start a Game");
    startButton.setOnAction(this::startGame);
    startButton.setDisable(true);
    startButton.setVisible(false);
    baseSquare.getChildren().add(startButton);
  }

  private void initializeStateBoard() {
    stateBoard = new VBox();
    stateBoard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #B2DFEE; -fx-pref-height: 100; -fx-pref-width: 280");

    stateText.setText("Current State");
    stateText.setFont(Font.font(30));

    stateBoard.getChildren().add(stateText);
    stateBoard.getChildren().add(stateValue);
    stateBoard.setAlignment(Pos.CENTER);
    stateBoard.setLayoutX(350);
    stateBoard.setLayoutY(15);
    stateBoard.setVisible(true);

    baseSquare.getChildren().add(stateBoard);
  }

  private void initializeAccountBoard() {
    accountBoard = new VBox();
    accountBoard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #B2DFEE; -fx-pref-height: 150; -fx-pref-width: 280");

    accountLabel.setFont(Font.font(25));
    gameCountsLabel.setFont(Font.font(25));
    winCountsLabel.setFont(Font.font(25));
    gameCountsLabel.setTextFill(Color.FIREBRICK);
    winCountsLabel.setTextFill(Color.FIREBRICK);

    accountBoard.getChildren().add(accountLabel);
    accountBoard.getChildren().add(gameCountsLabel);
    accountBoard.getChildren().add(winCountsLabel);
    accountBoard.setAlignment(Pos.CENTER);
    accountBoard.setLayoutX(350);
    accountBoard.setLayoutY(134);
    accountBoard.setVisible(false);

    baseSquare.getChildren().add(accountBoard);
  }


  private void initializeClient() {
    client = new Client();
    msgList = new ConcurrentHashMap<>();
    loginButton.setVisible(true);
    registerButton.setVisible(true);
    Thread connectServerThread = new Thread(() -> {
      boolean successConnected;
      do {
        try {
          client.initialize();
          successConnected = true;
          Thread receiveThread = new Thread(this::receiveMsg);
          receiveThread.setDaemon(true);
          receiveThread.start();

          Platform.runLater(() -> {
            loginButton.setDisable(false);
            registerButton.setDisable(false);
            updateState(NOT_LOGIN);
          });

        } catch (IOException e) {
          successConnected = false;

          Platform.runLater(() -> {
            startButton.setDisable(true);
            loginButton.setDisable(true);
            registerButton.setDisable(true);
            updateState(SERVER_ERROR);
          });

          try {
            Thread.sleep(5000);
          } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
          }
        }
      } while (!successConnected);
    });
    connectServerThread.setDaemon(true);
    connectServerThread.start();
  }

  private void drawChess(int x, int y) {
    if (chessBoard[x][y] == CIRCLE) {
      drawCircle(x, y);
    } else if (chessBoard[x][y] == CROSS) {
      drawCross(x, y);
    }
  }

  private void drawCircle(int i, int j) {
    Circle circle = new Circle();
    baseSquare.getChildren().add(circle);
    chessList.add(circle);
    circle.setCenterX(i * BOUND + BOUND / 2.0 + OFFSET);
    circle.setCenterY(j * BOUND + BOUND / 2.0 + OFFSET);
    circle.setRadius(BOUND / 2.0 - OFFSET / 2.0);
    circle.setStroke(Color.RED);
    circle.setFill(Color.TRANSPARENT);
  }

  private void drawCross(int i, int j) {
    Line line_a = new Line();
    Line line_b = new Line();
    baseSquare.getChildren().add(line_a);
    baseSquare.getChildren().add(line_b);
    chessList.add(line_a);
    chessList.add(line_b);
    line_a.setStartX(i * BOUND + OFFSET * 1.5);
    line_a.setStartY(j * BOUND + OFFSET * 1.5);
    line_a.setEndX((i + 1) * BOUND + OFFSET * 0.5);
    line_a.setEndY((j + 1) * BOUND + OFFSET * 0.5);
    line_a.setStroke(Color.BLUE);

    line_b.setStartX((i + 1) * BOUND + OFFSET * 0.5);
    line_b.setStartY(j * BOUND + OFFSET * 1.5);
    line_b.setEndX(i * BOUND + OFFSET * 1.5);
    line_b.setEndY((j + 1) * BOUND + OFFSET * 0.5);
    line_b.setStroke(Color.BLUE);
  }

  private void clickChessBoard(MouseEvent event) {
    try {
      if (state != MY_TURN) {
        return;
      }
      int x = (int) (event.getX() / BOUND);
      int y = (int) (event.getY() / BOUND);

      if (chessBoard[x][y] != EMPTY) {
        return;
      }

      updateState(OPPONENT_TURN);
      chessBoard[x][y] = self;
      drawChess(x, y);

      String gameMsg = String.format("id:%d;type:update-chessboard;new-board:%s;x-ordinate:%d;y-ordinate:%d", account.getId(), encodeChessBoard(), x, y);
      client.send(gameMsg);


    } catch (IOException e) {
      handleServerError();
    }
  }

  private String encodeChessBoard() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < chessBoard.length; i++) {
      for (int j = 0; j < chessBoard[0].length; j++) {
        sb.append(chessBoard[j][i]);
      }
    }
    return sb.toString();
  }

  private void handleGameMsg() {
    while (state != SERVER_ERROR) {
      while ((msgList.get("step-resp") == null || msgList.get("step-resp").isEmpty())
          && (msgList.get("win-resp") == null || msgList.get("win-resp").isEmpty())
          && (msgList.get("lose-resp") == null || msgList.get("lose-resp").isEmpty())
          && (msgList.get("draw-resp") == null || msgList.get("draw-resp").isEmpty())
          && (msgList.get("opponent-disconnect-resp") == null || msgList.get("opponent-disconnect-resp").isEmpty())) {
        if (state == SERVER_ERROR) {
          return;
        }
      }

      if ((!(msgList.get("step-resp") == null)) && !msgList.get("step-resp").isEmpty()) {
        String content = msgList.get("step-resp").poll();
        String[] ordinates = content.split("&");
        int x_ordinate = Integer.parseInt(ordinates[0].split("=")[1]);
        int y_ordinate = Integer.parseInt(ordinates[1].split("=")[1]);
        Platform.runLater(() -> {
          chessBoard[x_ordinate][y_ordinate] = opponent;
          drawChess(x_ordinate, y_ordinate);
          updateState(MY_TURN);
        });

      } else if ((!(msgList.get("win-resp") == null)) && !msgList.get("win-resp").isEmpty()) {
        String content = msgList.get("win-resp").poll();
        Platform.runLater(() -> endGame(WIN));
        break;
      } else if ((!(msgList.get("lose-resp") == null)) && !msgList.get("lose-resp").isEmpty()) {
        String content = msgList.get("lose-resp").poll();
        String[] ordinates = content.split("&");
        int x_ordinate = Integer.parseInt(ordinates[0].split("=")[1]);
        int y_ordinate = Integer.parseInt(ordinates[1].split("=")[1]);
        Platform.runLater(() -> {
          chessBoard[x_ordinate][y_ordinate] = opponent;
          drawChess(x_ordinate, y_ordinate);
          endGame(LOSE);
        });
        break;

      } else if ((!(msgList.get("draw-resp") == null)) && !msgList.get("draw-resp").isEmpty()) {
        String content = msgList.get("draw-resp").poll();
        if ("null".equals(content)) {
          Platform.runLater(() -> endGame(DRAW));
        } else {
          String[] ordinates = content.split("&");
          int x_ordinate = Integer.parseInt(ordinates[0].split("=")[1]);
          int y_ordinate = Integer.parseInt(ordinates[1].split("=")[1]);
          Platform.runLater(() -> {
            chessBoard[x_ordinate][y_ordinate] = opponent;
            drawChess(x_ordinate, y_ordinate);
            endGame(DRAW);
          });
        }
        break;
      } else if ((!(msgList.get("opponent-disconnect-resp") == null)) && !msgList.get("opponent-disconnect-resp").isEmpty()) {
        String content = msgList.get("opponent-disconnect-resp").poll();
        Platform.runLater(() -> {
          endGame(OPPONENT_DISCONNECT);
        });
        break;
      }

    }
  }

  private void startGame(ActionEvent event) {
    try {
      refreshChessBoard();
      String requestMsg = String.format("id:%d;type:match-request;content:request;", account.getId());
      client.send(requestMsg);
      updateState(MATCHING);
      startButton.setVisible(false);
      Thread requestThread = new Thread(() -> {
        while (msgList.get("request-resp") == null || msgList.get("request-resp").isEmpty()) {
          if (state == SERVER_ERROR) {
            return;
          }
        }
        String content = msgList.get("request-resp").poll();
        int symbol = Integer.parseInt(content.split("=")[1]);
        if (symbol == CIRCLE) {
          self = CIRCLE;
          opponent = CROSS;
          Platform.runLater(() -> updateState(MY_TURN));
        } else {
          self = CROSS;
          opponent = CIRCLE;
          Platform.runLater(() -> updateState(OPPONENT_TURN));
        }
        Thread handleThread = new Thread(this::handleGameMsg);
        handleThread.setDaemon(true);
        handleThread.start();
      });
      requestThread.setDaemon(true);
      requestThread.start();

    } catch (IOException e) {
      handleServerError();
    }

  }


  private void endGame(int endState) {
    updateState(endState);
    msgList.clear();
    if (endState == SERVER_ERROR) {
      startButton.setDisable(true);
      loginButton.setDisable(true);
      registerButton.setDisable(true);
      startButton.setText("Start Game!");
      startButton.setVisible(false);
      accountBoard.setVisible(false);
    } else {
      startButton.setText("One More Game!");
      account.setGameCounts(account.getGameCounts() + 1);
      if (endState == WIN) {
        account.setWinCounts(account.getWinCounts() + 1);
      }
      gameCountsLabel.setText("Total games: " + account.getGameCounts());
      winCountsLabel.setText("Win games: " + account.getWinCounts());
      startButton.setVisible(true);
    }

  }

  private void setStateValue(Pair<String, Paint> stateMsg, int fontSize) {
    stateValue.setText(stateMsg.getKey());
    stateValue.setFont(Font.font(fontSize));
    stateValue.setTextFill(stateMsg.getValue());
  }

  private void updateState(int newState) {
    state = newState;
    setStateValue(getStateString(), 30);
  }

  private Pair<String, Paint> getStateString() {
    switch (state) {
      case READY:
        return new Pair<>("Ready", Color.GREEN);
      case MY_TURN:
        return new Pair<>("Your Turn!", Color.TURQUOISE);
      case OPPONENT_TURN:
        return new Pair<>("Waiting...", Color.DARKCYAN);
      case SERVER_ERROR:
        return new Pair<>("Server Error!", Color.ORANGE);
      case MATCHING:
        return new Pair<>("Matching...", Color.DEEPSKYBLUE);
      case CONNECTING:
        return new Pair<>("Connecting...", Color.GRAY);
      case WIN:
        return new Pair<>("You Win!", Color.FIREBRICK);
      case LOSE:
        return new Pair<>("You Lose!", Color.MAROON);
      case DRAW:
        return new Pair<>("Draw Game!", Color.GOLDENROD);
      case OPPONENT_DISCONNECT:
        return new Pair<>("Opponent Escape!", Color.MEDIUMVIOLETRED);
      case NOT_LOGIN:
        return new Pair<>("Not Login", Color.LIGHTSEAGREEN);
      default:
        return new Pair<>("Unknown", Color.BLACK);
    }
  }

  private void receiveMsg() {
    while (true) {
      try {
        String[] resp = client.receive().split(";");
        String type = resp[0].split(":")[1];
        addMsgToList(type, resp[1].split(":")[1]);

      } catch (IOException e) {
        //server error
        handleServerError();
        break;
      }
    }
  }

  private void handleServerError() {
    Platform.runLater(() -> {
      endGame(SERVER_ERROR);
      if (!(msgWindow == null) && !(msgWindow.stage == null) && msgWindow.stage.isShowing()) {
        msgWindow.stage.close();
      }
    });
    try {
      client.close();
    } catch (IOException ee) {
      ee.printStackTrace();
    }
    initializeClient();
  }

  private void addMsgToList(String type, String content) {
    if (!msgList.containsKey(type)) {
      ConcurrentLinkedQueue<String> msgQueue = new ConcurrentLinkedQueue<>();
      msgQueue.add(content);
      msgList.put(type, msgQueue);
    } else {
      msgList.get(type).add(content);
    }
  }

}
