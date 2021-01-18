package nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

public class NioServer {

    private final ServerSocketChannel serverChannel = ServerSocketChannel.open();
    private final Selector selector = Selector.open();
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    private Path serverPath = Paths.get("serverDir").toAbsolutePath();

    public NioServer() throws IOException {
        serverChannel.bind(new InetSocketAddress(8189));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (serverChannel.isOpen()) {
            selector.select(); // block
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    handleAccept(key);
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new NioServer();
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        int read = 0;
        StringBuilder msg = new StringBuilder();
        while ((read = channel.read(buffer)) > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                msg.append((char) buffer.get());
            }
            buffer.clear();
        }
        String smsg = msg.toString().replaceAll("[\n|\r]", "").trim();
        if (!smsg.isEmpty()) {
            String command = smsg.split(" ")[0];
            if (command.equals("ls")) {
                System.out.println(smsg);
                String files = Files.list(serverPath)
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.joining(", "));
                //files += "\n";
                channel.write(ByteBuffer.wrap(files.getBytes(StandardCharsets.UTF_8)));
            } else if (command.equals("cd")) {
                System.out.println(smsg);
                Path pathTemp = getPath(smsg.substring(2).trim());
                if (pathTemp.toFile().exists()) {
                    serverPath = pathTemp;
                } else {
                    channel.write(ByteBuffer.wrap("Не найден указанный путь".getBytes(StandardCharsets.UTF_8)));
                }
            } else if (command.equals("cat")) {
                System.out.println(smsg);
                Path pathTemp = getPath(smsg.substring(3).trim());
                if (pathTemp.toFile().exists()) {
                    channel.write(ByteBuffer.wrap(Files.readAllBytes(pathTemp)));
                } else {
                    channel.write(ByteBuffer.wrap("Не найден указанный файл".getBytes(StandardCharsets.UTF_8)));
                }
            } else if (command.equals("touch")) {
                System.out.println(smsg);
                Path pathTemp = getPath(smsg.substring(5).trim());
                if (pathTemp.toFile().exists()) {
                    channel.write(ByteBuffer.wrap("Указанный файл уже существует".getBytes(StandardCharsets.UTF_8)));
                } else {
                    Files.createFile(pathTemp);
                }
            } else if (command.equals("mkdir")) {
                System.out.println(smsg);
                Path pathTemp = getPath(smsg.substring(5).trim());
                if (pathTemp.toFile().exists()) {
                    channel.write(ByteBuffer.wrap("Указанная директория уже существует".getBytes(StandardCharsets.UTF_8)));
                } else {
                    Files.createDirectory(pathTemp);
                }
            }
        }
    }

    private Path getPath(String p) {
        Path pa;
        if (p.startsWith("\"") && p.endsWith("\"")) {
            pa = Paths.get(p.substring(1, p.length() - 1));
        } else {
            pa = Paths.get(p);
        }
        //System.out.println(pa);
        Path pathTemp;
        if (pa.isAbsolute()) {
            pathTemp = pa;
        } else {
            pathTemp = Paths.get(serverPath.toString(), pa.toString()).normalize();
        }
        //System.out.println(pathTemp);
        return pathTemp;
    }

    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        System.out.println("Socket was accepted");
    }
}