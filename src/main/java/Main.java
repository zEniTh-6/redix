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
  private static final String OK = "+OK\r\n";
  private static final String NO_VALUE = "$-1\r\n";
  private static final String END = "\r\n";
  public static void main(String[] args) {
    ServerSocket serverSocket = null;
    Socket clientSocket = null;
    int port = 6379;
    ConcurrentHashMap<String, String> mp = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Long> ex_mp = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, LinkedList<String>> list_mp = new ConcurrentHashMap<>();
    try {
      serverSocket = new ServerSocket(port);
      // Since the tester restarts your program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);

      while (true) {
        clientSocket = serverSocket.accept();
        Socket newSocket = clientSocket;
        Thread t1 = new Thread(() -> handleClient(newSocket, mp, ex_mp, list_mp));
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
      ConcurrentHashMap<String, Long> ex_mp, ConcurrentHashMap<String, LinkedList<String>> list_mp) {
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
            String resp = "$" + message.length() + END  + message + END;
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
                  out.write(OK.getBytes());
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
                  out.write(OK.getBytes());
                  out.flush();
                }
                // --------------------------------FIX THE NX AND XX COMMANDS
                // ---------------------------------- //
                case "NX" -> {
                  if (!mp.containsKey(key)) {
                    mp.put(key, value);
                    out.write(OK.getBytes());
                    out.flush();
                  } else {
                    out.write(NO_VALUE.getBytes());
                    out.flush();
                  }
                }
                case "XX" -> {
                  if (mp.containsKey(key)) {
                    mp.put(key, value);
                    out.write(OK.getBytes());
                    out.flush();
                  } else {
                    out.write(NO_VALUE.getBytes());
                    out.flush();
                  }
                }
                default -> {
                  out.write(("-ERR unknown command " + fn + END).getBytes());
                  out.flush();
                }
              }
            } else {
              ex_mp.remove(key);
              mp.put(key, value);
              out.write(OK.getBytes());
              out.flush();
            }
          }
          case "GET" -> {
            String key = commands.get(1);
            if (ex_mp.containsKey(key) && System.currentTimeMillis() > ex_mp.get(key)) {
              mp.remove(key);
              ex_mp.remove(key);
              out.write(NO_VALUE.getBytes());
              out.flush();
            } else if (mp.containsKey(key)) {
              String value = mp.get(key);
              String resp = "$" + value.length() + END + value + END;
              out.write(resp.getBytes());
              out.flush();
            } else {
              out.write(NO_VALUE.getBytes());
              out.flush();
            }
          }
          case "RPUSH" -> {
            String l_name = commands.get(1);
            LinkedList<String> list = list_mp.get(l_name);

            if (list == null){
              list = new LinkedList<>();
              list_mp.put(l_name, list);
            }
            // accept multiple values
            for(int i = 2; i < commands.size(); i++) {
              String value = commands.get(i);
              list.addLast(value);
            }
            out.write((":" + list.size() + END).getBytes());
            out.flush();
          }
          case "LPUSH" -> {
            String l_name = commands.get(1);
            LinkedList<String> list = list_mp.get(l_name);

            if (list == null){
              list = new LinkedList<>();
              list_mp.put(l_name, list);
            }
            // accept multiple values
            for(int i = 2; i < commands.size(); i++) {
              String value = commands.get(i);
              list.addFirst(value);
            }
            out.write((":" + list.size() + END).getBytes());
            out.flush();
          }
          default -> {
            out.write(("-ERR unknown command " + cmd + END).getBytes());
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
      for (int j = 0; j < len; j++) {
        curr = in.read();
        if (curr == -1)
          return new ArrayList<>();
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
