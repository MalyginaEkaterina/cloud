import cloud.common.FileDir;

import java.util.HashMap;

public class TreeNode {
    private String name;
    private FileDir fileDir;
    private TreeNode parent;
    private HashMap<String, TreeNode> setChild;

    public TreeNode(FileDir fileDir) {
        this.fileDir = fileDir;
        this.name = fileDir.getName();
        this.parent = null;
        this.setChild = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public FileDir getFileDir() {
        return fileDir;
    }

    public TreeNode getParent() {
        return parent;
    }

    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    public HashMap<String, TreeNode> getSetChild() {
        return setChild;
    }
}
