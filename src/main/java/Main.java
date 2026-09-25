import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
  public static void main(String[] args) {
    ServerSocket serverSocket = null;
    Socket clientSocket = null;
    int port = 6379;
    ConcurrentHashMap<String, String> mp = new ConcurrentHashMap<>();
    try {
      serverSocket = new ServerSocket(port);
      // Since the tester restarts your program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);

      while(true){
        clientSocket = serverSocket.accept();
        Socket newSocket = clientSocket;
        Thread t1 = new Thread(() -> handleClient(newSocket, mp));
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
  
  public static void handleClient(Socket clientSocket, ConcurrentHashMap<String, String> mp) {
    try (InputStream in = clientSocket.getInputStream();
         OutputStream out = clientSocket.getOutputStream()) {
      while (true) {
        List<String> commands = parser(in);
        // client closed connection
        if (commands == null) break; 
        if (commands.isEmpty()) continue; 

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
            mp.put(key, value);
            String resp = "+OK\r\n";
            out.write(resp.getBytes());
            out.flush();
          }
          case "GET" -> {
            String key = commands.get(1);
            if(mp.containsKey(key)){
              String value = mp.get(key);
              String resp = "$" + value.length() + "\r\n" + value + "\r\n";
              out.write(resp.getBytes());
              out.flush();
            }
            else{
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
      try { clientSocket.close(); } catch (IOException ignored) {}
    }
  }

  // Reads one full RESP array command (*N\r\n $len\r\n data\r\n ... ) and
  // returns its elements as tokens. 
  private static List<String> parser(InputStream in) throws IOException {
    String header = readLine(in);
    if (header == null) return null;
    if (!header.startsWith("*")) return new ArrayList<>();

    int num_of_args = Integer.parseInt(header.substring(1));
    List<String> cmds = new ArrayList<>(num_of_args);
    for (int i = 0; i < num_of_args; i++) {
      // read the length of command 1st as per the RESP format.
      String len_of_cmd = readLine(in); 
      if (len_of_cmd == null || !len_of_cmd.startsWith("$")) return new ArrayList<>();
      // now the actual command.
      String value = readLine(in);         
      if (value == null) return new ArrayList<>();
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
