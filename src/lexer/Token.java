package lexer;

/**
 * Token（二元式）类
 * 词法分析的基本输出单元，格式为：(种别码, 单词符号)
 */
public class Token {

    private final TokenType type;   // 种别码
    private final String value;     // 单词符号的字符串表示
    private final int line;         // 所在行号（用于错误报告）
    private final int column;       // 所在列号

    public Token(TokenType type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    public TokenType getType()  { return type; }
    public String getValue()    { return value; }
    public int getLine()        { return line; }
    public int getColumn()      { return column; }

    /**
     * 输出二元式格式：token: xxx, type: YYY  [line:col]
     */
    @Override
    public String toString() {
        return String.format("token: %-15s type: %-12s [%d:%d]",
                "\"" + value + "\"", type, line, column);
    }
}
