package parser;

import java.util.ArrayList;
import java.util.List;

public class TreeNode {
    public String label;
    public List<TreeNode> children = new ArrayList<>();

    public TreeNode(String label) {
        this.label = label;
    }

    public void addChild(TreeNode child) {
        if (child != null) children.add(child);
    }

    // 递归打印树状结构
    public void print(String prefix, boolean isLast) {
        System.out.println(prefix + (isLast ? "└── " : "├── ") + label);
        for (int i = 0; i < children.size(); i++) {
            children.get(i).print(prefix + (isLast ? "    " : "│   "), i == children.size() - 1);
        }
    }
}