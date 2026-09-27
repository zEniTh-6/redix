package commandhandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import store.KeyValueStore;
import store.ListStore;

import static resp.RespProtocol.OK;
import static resp.RespProtocol.NO_VALUE;
import static resp.RespProtocol.END;

public class CommandHandler {
    private final KeyValueStore kv;
    private final ListStore listStore;

    public CommandHandler(KeyValueStore kv, ListStore listStore) {
        this.kv = kv;
        this.listStore = listStore;
    }

    public void handle(List<String> commands, OutputStream out) throws IOException {
        String cmd = commands.get(0).toUpperCase();

        switch (cmd) {
            case "PING" -> {
                out.write("+PONG\r\n".getBytes());
                out.flush();
            }
            case "ECHO" -> {
                String message = commands.get(1);
                String resp = "$" + message.length() + END + message + END;
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
                                out.write(("-ERR invalid expire time\r\n").getBytes());
                                out.flush();
                                break;
                            }
                            long exp_time = System.currentTimeMillis() + timer * 1000;
                            kv.setWithExpiry(key, value, exp_time);
                            out.write(OK.getBytes());
                            out.flush();
                        }
                        case "PX" -> {
                            try {
                                timer = Long.parseLong(commands.get(4));
                            } catch (NumberFormatException e) {
                                out.write(("-ERR invalid expire time\r\n").getBytes());
                                out.flush();
                                break;
                            }
                            long exp_time = System.currentTimeMillis() + timer;
                            kv.setWithExpiry(key, value, exp_time);
                            out.write(OK.getBytes());
                            out.flush();
                        }
                        case "NX" -> {
                            if (kv.setIfAbsent(key, value)) {
                                out.write(OK.getBytes());
                            } else {
                                out.write(NO_VALUE.getBytes());
                            }
                            out.flush();
                        }
                        case "XX" -> {
                            if (kv.setIfPresent(key, value)) {
                                out.write(OK.getBytes());
                            } else {
                                out.write(NO_VALUE.getBytes());
                            }
                            out.flush();
                        }
                        default -> {
                            out.write(("-ERR unknown command " + fn + END).getBytes());
                            out.flush();
                        }
                    }
                } else {
                    kv.set(key, value);
                    out.write(OK.getBytes());
                    out.flush();
                }
            }
            case "GET" -> {
                String key = commands.get(1);
                String value = kv.get(key);
                if (value == null) {
                    out.write(NO_VALUE.getBytes());
                } else {
                    String resp = "$" + value.length() + END + value + END;
                    out.write(resp.getBytes());
                }
                out.flush();
            }
            case "RPUSH" -> {
                String l_name = commands.get(1);
                int size = listStore.rpush(l_name, commands.subList(2, commands.size()));
                out.write((":" + size + END).getBytes());
                out.flush();
            }
            case "LPUSH" -> {
                String l_name = commands.get(1);
                int size = listStore.lpush(l_name, commands.subList(2, commands.size()));
                out.write((":" + size + END).getBytes());
                out.flush();
            }
            case "LRANGE" -> {
                String l_name = commands.get(1);
                try {
                    int st = Integer.parseInt(commands.get(2));
                    int end = Integer.parseInt(commands.get(3));

                    List<String> value = listStore.lrange(l_name, st, end);

                    out.write(("*" + value.size() + END).getBytes());
                    for (String el : value) {
                        out.write(("$" + el.length() + END + el + END).getBytes());
                    }
                    out.flush();
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR enter a valid range" + END).getBytes());
                    out.flush();
                    break;
                }
            }
            case "LLEN" -> {
                String l_name = commands.get(1);
                int len = listStore.llen(l_name);
                out.write((":" + len + END).getBytes());
                out.flush();
            }
            case "LPOP" -> {
                String l_name = commands.get(1);

                if (commands.size() == 2) {
                    List<String> popped = listStore.lpop(l_name, 1);
                    if (popped.isEmpty()) {
                        out.write(NO_VALUE.getBytes());
                    } else {
                        String el = popped.get(0);
                        out.write(("$" + el.length() + END + el + END).getBytes());
                    }
                    out.flush();
                } else {
                    int num;
                    try {
                        num = Integer.parseInt(commands.get(2));
                    } catch (NumberFormatException e) {
                        out.write(("-ERR value is not an integer or out of range" + END).getBytes());
                        out.flush();
                        break;
                    }

                    if (num <= 0) {
                        out.write("*0\r\n".getBytes());
                    } else {
                        List<String> popped = listStore.lpop(l_name, num);
                        out.write(("*" + popped.size() + END).getBytes());
                        for (int i = 0; i < popped.size(); i++) {
                            out.write(("$" + popped.get(i).length() + END + popped.get(i) + END).getBytes());
                        }
                    }
                    out.flush();
                }
            }
            case "RPOP" -> {
                String l_name = commands.get(1);

                if (commands.size() == 2) {
                    List<String> popped = listStore.rpop(l_name, 1);
                    if (popped.isEmpty()) {
                        out.write(NO_VALUE.getBytes());
                    } else {
                        String el = popped.get(0);
                        out.write(("$" + el.length() + END + el + END).getBytes());
                    }
                    out.flush();
                } else {
                    int num;
                    try {
                        num = Integer.parseInt(commands.get(2));
                    } catch (NumberFormatException e) {
                        out.write(("-ERR value is not an integer or out of range" + END).getBytes());
                        out.flush();
                        break;
                    }

                    if (num <= 0) {
                        out.write("*0\r\n".getBytes());
                    } else {
                        List<String> popped = listStore.rpop(l_name, num);
                        out.write(("*" + popped.size() + END).getBytes());
                        for (int i = 0; i < popped.size(); i++) {
                            out.write(("$" + popped.get(i).length() + END + popped.get(i) + END).getBytes());
                        }
                    }
                    out.flush();
                }
            }
            default -> {
                out.write(("-ERR unknown command '" + cmd + "'\r\n").getBytes());
                out.flush();
            }
        }
    }
}