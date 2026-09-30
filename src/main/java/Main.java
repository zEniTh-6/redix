import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import commandhandler.CommandHandler;
import resp.RespProtocol;
import store.KeyValueStore;
import store.ListStore;

public class Main {

  public static void main(String[] args) {
    ServerSocket serverSocket = null;
    Socket clientSocket = null;
    int port = 6379;
    
    String dir = ".";
    String dbfilename = "dump.rdb";
        
    for (int i = 0; i < args.length - 1; i++) {
      if (args[i].equals("--dir")) {
        dir = args[i + 1];
      } else if (args[i].equals("--dbfilename")) {
        dbfilename = args[i + 1];
      }
    }
    
    File RDB = new File(dir, dbfilename);
    RDB.getParentFile().mkdirs();
    
    KeyValueStore kv = new KeyValueStore();
    ListStore listStore = new ListStore();
    CommandHandler commandHandler = new CommandHandler(kv, listStore, RDB);

    try {
      serverSocket = new ServerSocket(port);
      // Since the tester restarts your program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);

      while (true) {
        clientSocket = serverSocket.accept();
        Socket newSocket = clientSocket;
        Thread t1 = new Thread(() -> handleClient(newSocket, commandHandler));
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

  public static void handleClient(Socket clientSocket, CommandHandler commandHandler) {
    try (InputStream in = clientSocket.getInputStream();
        OutputStream out = clientSocket.getOutputStream()) {
      while (true) {
        List<String> commands = RespProtocol.parser(in);
        // client closed connection
        if (commands == null)
          break;
        if (commands.isEmpty())
          continue;

        commandHandler.handle(commands, out);
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
}