package parser;

import java.util.ArrayList;
import java.util.List;

/**
 * 语法树（Syntax Tree / Parse Tree）节点类 —— 表示程序中语法结构的树形组织。
 *
 * <h3>在编译器流程中的位置</h3>
 * <pre>
 *   Token 序列 → [语法分析器] → 语法树 (TreeNode 根节点)
 *                            ↘ 四元式序列（中间代码）
 * </pre>
 * 语法树和四元式都是语法分析器的输出。语法树用于：
 * <ol>
 *   <li><b>可视化</b> —— 直观展示程序的语法结构</li>
 *   <li><b>语义分析</b> —— 遍历语法树进行类型检查等</li>
 *   <li><b>中间代码生成</b> —— 从语法树翻译为四元式（本项目实际采用
 *        语法分析过程中同步生成四元式的方案）</li>
 * </ol>
 *
 * <h3>树结构</h3>
 * 语法树是一棵<b>有序树</b>，满足：
 * <ul>
 *   <li>根节点代表整个程序（如 "Program"）</li>
 *   <li>内部节点代表语法结构（如 "Decl", "Assign", "Expr"）</li>
 *   <li>叶节点代表具体的 Token 值（如 "int", "a", "5", ";"）</li>
 *   <li>子节点顺序反映它们在源程序中的出现顺序</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>
 * 源代码: int a;
 * 语法树:
 *   Program
 *   └── Decl
 *       ├── int
 *       ├── id: a
 *       └── ;
 * </pre>
 *
 * @author 编译原理课程设计
 * @see Parser 语法分析器（语法树的构造者）
 */
public class TreeNode {

    /** 节点标签 —— 非终结符名（如 "Decl", "Assign"）或具体值（如 "int", "id: a", "5"） */
    public String label;

    /** 子节点列表 —— 有序列表，反映源程序中各部分的出现顺序 */
    public List<TreeNode> children = new ArrayList<>();

    /**
     * 构造一个语法树节点。
     *
     * @param label 节点的标签（非终结符名或 Token 值）
     */
    public TreeNode(String label) {
        this.label = label;
    }

    /**
     * 向当前节点添加一个子节点。
     *
     * <p>空节点（null）不会被添加，这简化了语法分析器中对可选成份的处理。
     *
     * @param child 子节点（为 null 时忽略）
     */
    public void addChild(TreeNode child) {
        if (child != null) {
            children.add(child);
        }
    }

    /**
     * 递归打印语法树结构（使用 Unicode 树形连接线）。
     *
     * <p>输出风格：
     * <pre>
     *   Program
     *   ├── Decl
     *   │   ├── int
     *   │   ├── id: a
     *   │   └── ;
     *   └── Assign
     *       ├── id: b
     *       ├── =
     *       ├── Expr(+)
     *       │   ├── 5
     *       │   └── 10
     *       └── ;
     * </pre>
     *
     * <p>算法：深度优先遍历（DFS），递归打印每个子树。
     * 每个节点通过 prefix 参数记住祖先节点的缩进和连接线样式。
     *
     * @param prefix 当前行的前缀字符串（由祖先节点传递下来，包含 │   等连接线）
     * @param isLast 当前节点是否为父节点的最后一个子节点（决定用 └── 还是 ├──）
     */
    public void print(String prefix, boolean isLast) {
        // 打印当前节点：├── 或 └── 取决于是否为最后一个子节点
        System.out.println(prefix + (isLast ? "└── " : "├── ") + label);

        // 计算子节点的前缀
        // 如果是最后一个子节点，下一级用空格缩进；否则用竖线 │ 连接
        for (int i = 0; i < children.size(); i++) {
            children.get(i).print(
                prefix + (isLast ? "    " : "│   "),
                i == children.size() - 1
            );
        }
    }
}
