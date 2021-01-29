package cloud.common;

import java.util.Arrays;

public class FileDir {
    private short type;
    private long id;
    private long size;
    private String[] path;
    private String name;
    private String pathStr;


    public FileDir(short type) {
        this.type = type;
    }

    public FileDir(short type, String[] path) {
        this.type = type;
        this.path = path;
        this.name = path[path.length - 1];
        if (type == ProtocolDict.TYPE_DIRECTORY) {
            this.size = -1L;
        }
    }
    public FileDir(short type, long size, String[] path) {
        this(type, path);
        this.size = size;
    }

    public FileDir(short type, long id, long size, String[] path) {
        this(type, size, path);
        this.id = id;
    }

    public FileDir(short type, long id, long size, String pathStr) {
        this.type = type;
        this.id = id;
        this.size = size;
        this.pathStr = pathStr;
    }

    public String[] getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public short getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public long getId() {
        return id;
    }

    public String getPathStr() {
        return pathStr;
    }

    @Override
    public String toString() {
        return "FileDir{" +
                "type=" + type +
                ", id=" + id +
                ", size=" + size +
                ", path=" + Arrays.toString(path) +
                ", name='" + name + '\'' +
                ", pathStr='" + pathStr + '\'' +
                '}';
    }
}
