package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {
  private static Socket socket;
  private static OutputStream outputStream;
  private static InputStream inputStream;
  private static byte[] bys;

  private static final String serverIP = "127.0.0.1";
  private static final int serverPort = 12345;

  public void initialize() throws IOException {
    socket = new Socket(serverIP, serverPort);
    bys = new byte[2048];
    outputStream = socket.getOutputStream();
    inputStream = socket.getInputStream();
  }

  public void send(String content) throws IOException {
    outputStream.write(content.getBytes(StandardCharsets.UTF_8));
  }

  public String receive() throws IOException {
    int len = inputStream.read(bys);
    return new String(bys, 0, len);
  }

  public void close() throws IOException {
    socket.close();
  }
}
