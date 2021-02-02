import cloud.common.FileDir;
import cloud.common.ProtocolDict;

import java.util.Arrays;
import java.util.HashMap;

public class TreeDirectory {
    private TreeNode root;

    public TreeDirectory() {
        FileDir f = new FileDir(ProtocolDict.TYPE_DIRECTORY);
        this.root = new TreeNode(f);
    }

    public void insert(FileDir f) {
        String[] path = f.getPath();
        TreeNode parent = root;
        if (path.length > 1) {
            parent = insertPath(Arrays.copyOfRange(path, 0, path.length - 1));
        }
        TreeNode t = new TreeNode(f);
        t.setParent(parent);
        HashMap<String, TreeNode> child = parent.getSetChild();
        if (child.containsKey(f.getName())) {
            return;
        }
        child.put(f.getName(), t);
    }

    public TreeNode insertPath(String[] path) {
        TreeNode cur = root;
        for (int i = 0; i < path.length; i++) {
            HashMap<String, TreeNode> child = cur.getSetChild();
            if (child.containsKey(path[i])) {
                cur = child.get(path[i]);
            } else {
                FileDir f = new FileDir(ProtocolDict.TYPE_DIRECTORY,
                        Arrays.copyOfRange(path, 0, i + 1));
                TreeNode t = new TreeNode(f);
                t.setParent(cur);
                child.put(path[i], t);
                cur = t;
            }
        }
        return cur;
    }

    public TreeNode get(String path) {
        if (path.isEmpty()) {
            return root;
        }
        TreeNode cur = root;
        String[] arr = path.split("/");
        for (int i = 0; i < arr.length; i++) {
            HashMap<String, TreeNode> child = cur.getSetChild();
            if (child.containsKey(arr[i])) {
                cur = child.get(arr[i]);
            } else {
                //TODO log
                return null;
            }
        }
        return cur;
    }

//    public static void main(String[] args) {
//        TreeDirectory t = new TreeDirectory();
//        //System.out.println(Arrays.toString("ftft/".split("/")));
//    }
}
