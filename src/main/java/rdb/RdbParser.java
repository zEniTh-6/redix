package rdb;

import store.KeyValueStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class RdbParser {
    public static void load(File rdb, KeyValueStore kv) {
        try (FileInputStream in = new FileInputStream(rdb)) {
            byte[] header = new byte[9];
            readarr(in, header);

            String heading = new String(header);
            if (!heading.startsWith("REDIS")) {
                throw new IOException("Not a valid RDB file");
            }

            Long pendingExpiry = null;

            int opcode;
            while ((opcode = in.read()) != -1) {
                switch (opcode) {
                    case 0xFA -> {
                        readString(in);
                        readString(in);
                    }
                    case 0xFE -> readLength(in);
                    case 0xFB -> {
                        readLength(in);
                        readLength(in);
                    }
                    case 0xFC -> pendingExpiry = readLongLE(in);
                    case 0xFD -> pendingExpiry = readIntLE(in) * 1000L;
                    case 0x00 -> {
                        String key = readString(in);
                        String value = readString(in);
                        if (pendingExpiry != null) {
                            kv.setWithExpiry(key, value, pendingExpiry);
                            pendingExpiry = null;
                        } else {
                            kv.set(key, value);
                        }
                    }
                    case 0xFF -> {
                        return;
                    }
                    default -> throw new IOException("Unsupported opcode: " + opcode);
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("RDB file not found, starting with empty store: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Failed to load RDB file: " + e.getMessage());
        }
    }

    private static int readLength(InputStream in) throws IOException {
        int first = in.read();
        if (first == -1)
            throw new IOException("Unexpected EOF reading length");

        int type = (first & 0xC0) >> 6; // top 2 bits
        return switch (type) {
            case 0 -> first & 0x3F; // 6-bit length
            case 1 -> {
                int next = in.read();
                if (next == -1)
                    throw new IOException("Unexpected EOF reading length");
                yield ((first & 0x3F) << 8) | next; // 14-bit length
            }
            case 2 -> {
                byte[] b = new byte[4];
                readarr(in, b);
                yield ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16)
                        | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF); // 32-bit, big-endian
            }
            default -> -1 - first; // signal "special int", encode which sub-type in the value
        };
    }

    private static String readString(InputStream in) throws IOException {
        int len = readLength(in);
        if (len < 0) {
            int subtype = -1 - len;
            return switch (subtype) {
                case 0xC0 -> {
                    byte[] arr = new byte[1];
                    readarr(in, arr);
                    yield String.valueOf(arr[0]); // 8-bit signed int
                }
                case 0xC1 -> {
                    byte[] arr = new byte[2];
                    readarr(in, arr);
                    int value = (arr[0] & 0xFF) | (arr[1] << 8); // little-endian, sign-extend high byte
                    yield String.valueOf(value);
                }
                case 0xC2 -> {
                    byte[] arr = new byte[4];
                    readarr(in, arr);
                    int value = (arr[0] & 0xFF) | ((arr[1] & 0xFF) << 8)
                            | ((arr[2] & 0xFF) << 16) | (arr[3] << 24); // little-endian, sign-extend high byte
                    yield String.valueOf(value);
                }
                default -> throw new IOException("Unsupported string encoding: " + subtype);
            };
        }
        byte[] arr = new byte[len];
        readarr(in, arr);
        return new String(arr);
    }

    private static long readLongLE(InputStream in) throws IOException {
        byte[] b = new byte[8];
        readarr(in, b);
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= ((long) (b[i] & 0xFF)) << (8 * i);
        }
        return result;
    }

    private static int readIntLE(InputStream in) throws IOException {
        byte[] b = new byte[4];
        readarr(in, b);
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result |= (b[i] & 0xFF) << (8 * i);
        }
        return result;
    }

    private static void readarr(InputStream in, byte[] arr) throws IOException {
        for (int i = 0; i < arr.length; i++) {
            int b = in.read();
            if (b == -1)
                throw new IOException("RDB file is shorter than expected");
            arr[i] = (byte) b;
        }
    }
}