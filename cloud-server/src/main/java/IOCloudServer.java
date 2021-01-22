import cloud.common.ProtocolDict;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class IOCloudServer {
    public static final int PORT = 8189;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                     DataInputStream in = new DataInputStream(clientSocket.getInputStream())) {
                    int c = in.readShort();
                    if (c == ProtocolDict.LOAD) {
                        try {
                            readSmallFile(in);
                            sendStatusForLoad(out, ProtocolDict.LOAD_STATUS_OK);
                        } catch (IOException e) {
                            System.out.println("Exception caught when trying to load file");
                            System.out.println(e.getMessage());
                            sendStatusForLoad(out, ProtocolDict.LOAD_STATUS_ERROR);
                        }
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Exception caught when trying to listen on port " + PORT + " or listening for a connection");
            System.out.println(e.getMessage());
        }
    }

    public static void readSmallFile(DataInputStream in) throws IOException {
        int nameLen = in.readInt();
        byte[] bName = new byte[nameLen];
        in.read(bName);
        String sName = new String(bName, StandardCharsets.UTF_8);
        File f = new File(sName + "_new");
        int dataLen = in.readInt();
        byte[] data = new byte[dataLen];
        in.read(data);
        try (FileOutputStream file = new FileOutputStream(f)) {
            file.write(data);
        }
    }

    public static void sendStatusForLoad(DataOutputStream out, int status) {
        try {
            out.writeShort(ProtocolDict.LOAD_STATUS);
            out.writeShort(status);
        } catch (IOException e) {
            System.out.println("Exception caught when trying to send load status " + status);
            System.out.println(e.getMessage());
        }
    }
}
