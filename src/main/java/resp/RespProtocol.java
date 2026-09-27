package resp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class RespProtocol {
    public static final String OK = "+OK\r\n";
    public static final String NO_VALUE = "$-1\r\n";
    public static final String END = "\r\n";

    // Reads one full RESP array command (*N\r\n $len\r\n data\r\n ... ) and
    // returns its elements as tokens.
    public static List<String> parser(InputStream in) throws IOException {
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
            readLine(in);
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