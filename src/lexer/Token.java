package lexer;

/**
 * Token（二元式）类 —— 词法分析器输出的基本单元。
 *
 * <h3>在编译器流程中的位置</h3>
 * Token 是词法分析阶段（Lexical Analysis）的输出产物，也是语法分析阶段（Syntax Analysis）
 * 的输入基本单位。整个前端流水线为：
 * <pre>
 *   源程序字符串 → [词法分析器] → Token 序列 → [语法分析器] → 语法树 / 四元式
 * </pre>
 *
 * <h3>二元式定义（编译原理教材 §3.1）</h3>
 * 形式语言中，每个单词用一个二元组表示：<b>（种别码, 属性值）</b>
 * <ul>
 *   <li><b>种别码（type）</b> —— 标识单词的语法类别，如 KEYWORD、IDENTIFIER、INTEGER 等</li>
 *   <li><b>属性值（value）</b> —— 单词的具体字符串表示。对于关键字和界符，种别码即足够；
 *       对于标识符和常量，属性值是语义分析所需的关键信息</li>
 *   <li><b>行号/列号</b> —— 记录单词在源程序中的位置，用于编译错误的精确定位</li>
 * </ul>
 *
 * <h3>编码约定</h3>
 * 单词符号通常用整数种别码表示。本实现中使用 TokenType 枚举代替，更易读且类型安全。
 *
 * @author 编译原理课程设计
 * @see TokenType 种别码枚举
 * @see Lexer  词法分析器（Token 的生产者）
 */
public class Token {

    /** 种别码 —— 标识该 Token 的词法类别（关键字、标识符、常量、运算符等） */
    private final TokenType type;

    /** 属性值 —— 单词符号的字符串表示，如标识符名 "myVar"、常量值 "42"、运算符 "+" 等 */
    private final String value;

    /** 所在行号（从 1 开始）—— 用于错误报告时精确定位源代码中的出错位置 */
    private final int line;

    /** 所在列号（从 1 开始）—— 与行号配合，提供二维坐标定位 */
    private final int column;

    /**
     * 构造一个 Token（二元式）。
     *
     * @param type   种别码（TokenType 枚举值）
     * @param value  单词符号的字符串表示
     * @param line   源代码中的行号（1-indexed）
     * @param column 源代码中的列号（1-indexed）
     */
    public Token(TokenType type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    /** 获取种别码 */
    public TokenType getType()  { return type; }

    /** 获取单词符号的字符串值 */
    public String getValue()    { return value; }

    /** 获取行号 */
    public int getLine()        { return line; }

    /** 获取列号 */
    public int getColumn()      { return column; }

    /**
     * 返回 Token 的字符串表示，格式为二元式：{@code token: "xxx", type: YYY [行:列]}
     *
     * <p>输出示例：
     * <pre>
     *   token: "int"          type: KEYWORD       [1:1]
     *   token: "main"         type: IDENTIFIER    [1:5]
     *   token: "5"            type: INTEGER       [4:9]
     * </pre>
     *
     * @return 格式化的 Token 描述字符串
     */
    @Override
    public String toString() {
        return String.format("token: %-15s type: %-12s [%d:%d]",
                "\"" + value + "\"", type, line, column);
    }
}
