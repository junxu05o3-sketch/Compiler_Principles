package parser;

import java.util.List;

/**
 * 产生式（Production）类 —— 上下文无关文法（CFG）的基本组成单元。
 *
 * <h3>在编译器流程中的位置</h3>
 * 产生式用于描述程序设计语言的语法规则，是 LL(1) 语法分析的理论基础。
 * <pre>
 *   产生式集合 → [LL1Analyzer] → FIRST/FOLLOW 集合 → 预测分析表 M
 * </pre>
 *
 * <h3>产生式的形式化定义（编译原理教材 §4.1）</h3>
 * <p>一个产生式记为 A → α，其中：
 * <ul>
 *   <li><b>A（左部 / LHS）</b> —— 一个非终结符（语法变量），如 "Program", "Expr"</li>
 *   <li><b>α（右部 / RHS）</b> —— 由终结符和/或非终结符组成的符号串</li>
 * </ul>
 *
 * <h3>本项目中使用的产生式示例</h3>
 * <pre>
 *   Program  → int main ( ) { StmtList }
 *   Expr     → Term ExprP
 *   ExprP    → + Term ExprP | ε      （ε 表示空串）
 * </pre>
 *
 * <h3>终结符与非终结符的命名约定</h3>
 * 本项目中约定：大写字母开头的符号为非终结符（如 "Program", "Expr"），
 * 其他符号为终结符（如 "int", "id", "num", "+"）。
 * 空串用希腊字母 ε（epsilon）表示。
 *
 * @author 编译原理课程设计
 * @see LL1Analyzer LL(1) 分析器（产生式的消费者）
 */
public class Production {

    /** 产生式左部（LHS, Left-Hand Side）—— 一个非终结符，如 "Program", "Expr" */
    public String lhs;

    /** 产生式右部（RHS, Right-Hand Side）—— 文法符号序列，如 ["int","main","(",")","{","StmtList","}"] */
    public List<String> rhs;

    /**
     * 构造一个产生式。
     *
     * @param lhs 产生式左部（非终结符名）
     * @param rhs 产生式右部（文法符号列表）；若为空列表则表示 ε-产生式
     */
    public Production(String lhs, List<String> rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    /**
     * 返回产生式的字符串表示（用于预测分析表的展示）。
     *
     * <p>示例输出：
     * <pre>
     *   Program -> int main ( ) { StmtList }
     *   ExprP -> ε
     * </pre>
     *
     * @return 格式为 "左部 -> 右部符号1 右部符号2 ..." 的字符串
     */
    @Override
    public String toString() {
        return lhs + " -> " + String.join(" ", rhs);
    }
}
