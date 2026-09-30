package commandhandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import store.KeyValueStore;
import store.ListStore;

import static resp.RespProtocol.OK;
import static resp.RespProtocol.NO_VALUE;
import static resp.RespProtocol.END;

public class CommandHandler {
    private final KeyValueStore kv;
    private final ListStore listStore;
    final File RDB;

    public CommandHandler(KeyValueStore kv, ListStore listStore, File RDB) {
        this.kv = kv;
        this.listStore = listStore;
        this.RDB = RDB;
    }

    public void handle(List<String> commands, OutputStream out) throws IOException {
        String cmd = commands.get(0).toUpperCase();

        switch (cmd) {
            case "PING" -> {
                out.write("+PONG\r\n".getBytes());
                out.flush();
            }
            case "ECHO" -> {
                try {
                    String message = commands.get(1);
                    String resp = "$" + message.length() + END + message + END;
                    out.write(resp.getBytes());
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'echo' command" + END).getBytes());
                }
                out.flush();
            }
            case "SET" -> {
                try {
                    String key = commands.get(1);
                    String value = commands.get(2);

                    listStore.del(key);
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
                } catch (IndexOutOfBoundsException | NumberFormatException e) {
                    out.write(("-ERR wrong number of arguments for 'set' command" + END).getBytes());
                }
                out.flush();
            }
            case "GET" -> {
                try {
                    String key = commands.get(1);
                    String value = kv.GET(key);
                    if (value == null) {
                        out.write(NO_VALUE.getBytes());
                    } else {
                        String resp = "$" + value.length() + END + value + END;
                        out.write(resp.getBytes());
                    }
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'get' command" + END).getBytes());
                }
                out.flush();
            }
            case "RPUSH" -> {
                try {
                    String l_name = commands.get(1);

                    int exist = kv.exists(l_name);
                    if (exist == 0) {
                        int size = listStore.rpush(l_name, commands.subList(2, commands.size()));
                        out.write((":" + size + END).getBytes());
                    } else {
                        out.write(("-WRONGTYPE Operation against a key holding the wrong kind of value" + END)
                                .getBytes());
                    }
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'rpush' command" + END).getBytes());
                }
                out.flush();
            }
            case "LPUSH" -> {
                try {
                    String l_name = commands.get(1);

                    int exist = kv.exists(l_name);
                    if (exist == 0) {
                        int size = listStore.lpush(l_name, commands.subList(2, commands.size()));
                        out.write((":" + size + END).getBytes());
                    } else {
                        out.write(("-WRONGTYPE Operation against a key holding the wrong kind of value" + END)
                                .getBytes());
                    }

                } catch (IndexOutOfBoundsException | NumberFormatException e) {
                    out.write(("-ERR wrong number of arguments for 'echo' command" + END).getBytes());
                }
                out.flush();
            }
            case "LRANGE" -> {
                try {
                    String l_name = commands.get(1);
                    int st = Integer.parseInt(commands.get(2));
                    int end = Integer.parseInt(commands.get(3));

                    List<String> value = listStore.lrange(l_name, st, end);

                    out.write(("*" + value.size() + END).getBytes());
                    for (String el : value) {
                        out.write(("$" + el.length() + END + el + END).getBytes());
                    }
                } catch (NumberFormatException e) {
                    out.write(("-ERR enter a valid range" + END).getBytes());
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'echo' command" + END).getBytes());
                }
                out.flush();
            }
            case "LLEN" -> {
                try {
                    String l_name = commands.get(1);
                    int len = listStore.llen(l_name);
                    out.write((":" + len + END).getBytes());
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'llen' command" + END).getBytes());
                }
                out.flush();
            }
            case "LPOP" -> {
                try {
                    String l_name = commands.get(1);

                    if (commands.size() == 2) {
                        List<String> popped = listStore.lpop(l_name, 1);
                        if (popped.isEmpty()) {
                            out.write(NO_VALUE.getBytes());
                        } else {
                            String el = popped.get(0);
                            out.write(("$" + el.length() + END + el + END).getBytes());
                        }
                    } else {
                        int num;
                        try {
                            num = Integer.parseInt(commands.get(2));
                        } catch (NumberFormatException e) {
                            out.write(("-ERR value is not an integer or out of range" + END).getBytes());
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
                    }
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'lpop' command" + END).getBytes());
                }
                out.flush();
            }
            case "RPOP" -> {
                try {
                    String l_name = commands.get(1);

                    if (commands.size() == 2) {
                        List<String> popped = listStore.rpop(l_name, 1);
                        if (popped.isEmpty()) {
                            out.write(NO_VALUE.getBytes());
                        } else {
                            String el = popped.get(0);
                            out.write(("$" + el.length() + END + el + END).getBytes());
                        }
                    } else {
                        int num;
                        try {
                            num = Integer.parseInt(commands.get(2));
                        } catch (NumberFormatException e) {
                            out.write(("-ERR value is not an integer or out of range" + END).getBytes());
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
                    }
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'rpop' command" + END).getBytes());
                }
                out.flush();
            }
            case "DEL" -> {
                try {
                    List<String> keys = commands.subList(1, commands.size());
                    int removed = 0;
                    for (String k : keys) {
                        removed += kv.del(k);
                        removed += listStore.del(k);
                    }
                    out.write((":" + removed + END).getBytes());
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'del' command" + END).getBytes());
                }
                out.flush();
            }
            case "EXISTS" -> {
                try {
                    List<String> keys = commands.subList(1, commands.size());
                    int exist = 0;
                    for (String k : keys) {
                        exist += kv.exists(k);
                        exist += listStore.exists(k);
                    }
                    out.write((":" + exist + END).getBytes());
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'exists' command" + END).getBytes());
                }
                out.flush();
            }
            case "TYPE" -> {
                try {
                    String key = commands.get(1);
                    String type;
                    boolean if_kv = kv.type(key);
                    boolean if_list = listStore.type(key);

                    if (if_kv)
                        type = "string";
                    else if (if_list)
                        type = "list";
                    else
                        type = "none";

                    out.write(("+" + type + END).getBytes());
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'type' command" + END).getBytes());
                }
                out.flush();
            }
            case "KEYS" -> {
                try {
                    String comd = commands.get(1);
                    if (comd.equals("*")) {
                        Set<String> allKeys = new java.util.HashSet<>();
                        allKeys.addAll(kv.keys());
                        allKeys.addAll(listStore.keys());

                        out.write(("*" + allKeys.size() + END).getBytes());
                        for (String key : allKeys) {
                            out.write(("$" + key.length() + END + key + END).getBytes());
                        }
                    } else {
                        out.write(("-ERR unknown command" + END).getBytes());
                    }
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'ttl' command" + END).getBytes());
                }
                out.flush();
            }
            case "TTL" -> {
                try {
                    String key = commands.get(1);
                    int kv_exist = kv.exists(key);
                    if (kv_exist == 1) {
                        long result = kv.getTime(key);
                        out.write((":" + result + END).getBytes());
                    } else {
                        out.write((":-2" + END).getBytes());
                    }
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'ttl' command" + END).getBytes());
                }
                out.flush();
            }
            case "PERSIST" -> {
                try {
                    String key = commands.get(1);
                    int result = kv.persist(key);
                    out.write((":" + result + END).getBytes());
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'persist' command" + END).getBytes());
                }
                out.flush();
            }
            case "EXPIRE" -> {
                try {
                    String key = commands.get(1);
                    long sec = Long.parseLong(commands.get(2));
                    int result = kv.setExpiry(key, sec);
                    out.write((":" + result + END).getBytes());
                } catch (IndexOutOfBoundsException | NumberFormatException e) {
                    out.write(("-ERR wrong number of arguments for 'expire' command" + END).getBytes());
                }
                out.flush();
            }
            case "CONFIG" -> {
                try {
                    String command = commands.get(1);
                    String para = commands.get(2);
                    if (!command.equalsIgnoreCase("GET")) {
                        out.write(("-ERR unsupported CONFIG subcommand" + END).getBytes());
                    } else if (para.equalsIgnoreCase("dir")) {
                        String dirpath = RDB.getParentFile().getAbsolutePath();
                        out.write(("*2" + END + "$3" + END + "dir" + END + "$" + dirpath.length() + END + dirpath + END)
                                .getBytes());
                    } else if (para.equalsIgnoreCase("dbfilename")) {
                        String dbfile = RDB.getName();
                        out.write(("*2" + END + "$10" + END + "dbfilename" + END + "$" + dbfile.length() + END + dbfile
                                + END)
                                .getBytes());
                    } else
                        out.write("*0\r\n".getBytes());
                } catch (IndexOutOfBoundsException e) {
                    out.write(("-ERR wrong number of arguments for 'config' command" + END).getBytes());
                }
                out.flush();
            }
            default -> {
                out.write(("-ERR unknown command '" + cmd + "'\r\n").getBytes());
                out.flush();
            }
        }
    }
}