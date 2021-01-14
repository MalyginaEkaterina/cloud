import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CloudClient {
    public static final int PORT = 8189;

    public static void main(String[] args) {
        File f = new File("file_to_load.txt");

        try (Socket socket = new Socket("127.0.0.1", PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            if (f.length() <= Integer.MAX_VALUE) {
                writeSmallFile(out, f);
                int c = in.readShort();
                if (c == ProtocolDict.LOAD_STATUS) {
                    int status = in.readShort();
                    if (status == ProtocolDict.LOAD_STATUS_OK) {
                        System.out.println("File uploaded successfully");
                    } else if (status == ProtocolDict.LOAD_STATUS_ERROR) {
                        System.out.println("Loading error");
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Exception caught when trying to connect to port " + PORT);
            System.out.println(e.getMessage());
        }
    }

    public static void writeSmallFile (DataOutputStream out, File f) throws IOException {
        byte[] data;
        try {
            data = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            System.out.println("Exception caught when reading the file " + f.getName());
            System.out.println(e.getMessage());
            return;
        }
        out.writeShort(ProtocolDict.LOAD);
        byte[] name = f.getName().getBytes(StandardCharsets.UTF_8);
        out.writeInt(name.length);
        out.write(name);
        out.writeInt(data.length);
        out.write(data);
    }
}
