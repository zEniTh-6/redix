import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

  public static void main(String[] args) {
    ServerSocket serverSocket = null;
    Socket clientSocket = null;
    int port = 6379;
    ConcurrentHashMap<String, String> mp = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Long> ex_mp = new ConcurrentHashMap<>();
    LinkedList<String> list = new LinkedList<>();
    try {
      serverSocket = new ServerSocket(port);
      // Since the tester restarts your program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);

      while (true) {
        clientSocket = serverSocket.accept();
        Socket newSocket = clientSocket;
        Thread t1 = new Thread(() -> handleClient(newSocket, mp, ex_mp));
        t1.start();
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    } finally {
      try {
        if (clientSocket != null) {
          clientSocket.close();
        }
      } catch (IOException e) {
        System.out.println("IOException: " + e.getMessage());
      }
    }
  }

  public static void handleClient(Socket clientSocket, ConcurrentHashMap<String, String> mp,
      ConcurrentHashMap<String, Long> ex_mp) {
    try (InputStream in = clientSocket.getInputStream();
        OutputStream out = clientSocket.getOutputStream()) {
      while (true) {
        List<String> commands = parser(in);
        // client closed connection
        if (commands == null)
          break;
        if (commands.isEmpty())
          continue;

        String cmd = commands.get(0).toUpperCase();
        switch (cmd) {
          case "PING" -> {
            out.write("+PONG\r\n".getBytes());
            out.flush();
          }
          case "ECHO" -> {
            String message = commands.get(1);
            String resp = "$" + message.length() + "\r\n" + message + "\r\n";
            out.write(resp.getBytes());
            out.flush();
          }
          case "SET" -> {
            String key = commands.get(1);
            String value = commands.get(2);

            if (commands.size() > 3) {
              String fn = commands.get(3).toUpperCase();
              long timer = 0;


              switch (fn) {
                case "EX" -> {
                  try {
                    timer = Long.parseLong(commands.get(4));
                  } catch (NumberFormatException e) {
                    out.write(("-ERR invalid expire time\r\n".getBytes()));
                    out.flush();
                    break;
                  }
                  long exp_time = System.currentTimeMillis() + timer * 1000;
                  mp.put(key, value);
                  ex_mp.put(key, exp_time);
                  String resp = "+OK\r\n";
                  out.write(resp.getBytes());
                  out.flush();
                }
                case "PX" -> {
                  try {
                    timer = Long.parseLong(commands.get(4));
                  } catch (NumberFormatException e) {
                    out.write(("-ERR invalid expire time\r\n".getBytes()));
                    out.flush();
                    break;
                  }
                  long exp_time = System.currentTimeMillis() + timer;
                  mp.put(key, value);
                  ex_mp.put(key, exp_time);
                  String resp = "+OK\r\n";
                  out.write(resp.getBytes());
                  out.flush();
                }
                case "NX" -> {
                  if (!mp.containsKey(key)) {
                    mp.put(key, value);
                    String resp = "+OK\r\n";
                    out.write(resp.getBytes());
                    out.flush();
                  } else {
                    String resp = "$-1\r\n";
                    out.write(resp.getBytes());
                    out.flush();
                  }
                }
                case "XX" -> {
                  if (mp.containsKey(key)) {
                    mp.put(key, value);
                    String resp = "+OK\r\n";
                    out.write(resp.getBytes());
                    out.flush();
                  } else {
                    String resp = "$-1\r\n";
                    out.write(resp.getBytes());
                    out.flush();
                  }
                }
                default -> {
                  out.write(("-ERR unknown command '" + fn + "'\r\n").getBytes());
                  out.flush();
                }
              }
            } else {
              ex_mp.remove(key);
              mp.put(key, value);
              String resp = "+OK\r\n";
              out.write(resp.getBytes());
              out.flush();
            }
          }
          case "GET" -> {
            String key = commands.get(1);
            if (ex_mp.containsKey(key) && System.currentTimeMillis() > ex_mp.get(key)) {
              mp.remove(key);
              ex_mp.remove(key);
              String resp = "$-1\r\n";
              out.write(resp.getBytes());
              out.flush();
            } else if (mp.containsKey(key)) {
              String value = mp.get(key);
              String resp = "$" + value.length() + "\r\n" + value + "\r\n";
              out.write(resp.getBytes());
              out.flush();
            } else {
              String resp = "$-1\r\n";
              out.write(resp.getBytes());
              out.flush();
            }
          }
          default -> {
            out.write(("-ERR unknown command '" + cmd + "'\r\n").getBytes());
            out.flush();
          }
        }
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    } finally {
      try {
        clientSocket.close();
      } catch (IOException ignored) {
      }
    }
  }

  // Reads one full RESP array command (*N\r\n $len\r\n data\r\n ... ) and
  // returns its elements as tokens.
  private static List<String> parser(InputStream in) throws IOException {
    String header = readLine(in);
    if (header == null)
      return null;
    if (!header.startsWith("*"))
      return new ArrayList<>();

    int num_of_args = Integer.parseInt(header.substring(1));
    List<String> cmds = new ArrayList<>(num_of_args);
    for (int i = 0; i < num_of_args; i++) {

      // read the length of command 1st as per the RESP format.
      String len_of_cmd = readLine(in);
      if (len_of_cmd == null || !len_of_cmd.startsWith("$"))
        return new ArrayList<>();

      // now the actual command.
      int len = Integer.parseInt(len_of_cmd.substring(1));
      StringBuilder sb = new StringBuilder();
      int curr;
      for(int j = 0; j < len; j++){
        curr = in.read();
        if(curr == -1) return new ArrayList<>();
        sb.append((char) curr);
      }
      String resp_end = readLine(in);
      String value = sb.toString();
      cmds.add(value);
    }
    return cmds;
  }

  private static String readLine(InputStream in) throws IOException {
    StringBuilder sb = new StringBuilder();
    int prev = -1, curr;
    while ((curr = in.read()) != -1) {
      if (prev == '\r' && curr == '\n') {
        sb.setLength(sb.length() - 1);
        return sb.toString();
      }
      sb.append((char) curr);
      prev = curr;
    }
    return sb.length() == 0 ? null : sb.toString();
  }
}
