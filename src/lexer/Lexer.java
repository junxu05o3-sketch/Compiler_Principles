package lexer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 词法分析器（Lexer）核心类
 *
 * 实现思路：基于状态转换图（DFA），逐字符扫描源程序，
 * 识别并生成各类 Token（二元式）。
 *
 * 支持特性：
 *   - 关键字 / 标识符识别
 *   - 整数 / 浮点数识别
 *   - 字符串常量 "..." 识别
 *   - 字符常量 '.' 识别
 *   - 单行注释 // 和块注释 /* ... *\/ 跳过
 *   - 多字符运算符（==, !=, <=, >=, &&, ||）识别
 *   - 非法字符报错
 */
public class Lexer {

    // ----------------------------------------------------------------
    // 关键字集合（查找表）
    // ----------------------------------------------------------------
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "int", "float", "double", "char", "void", "bool",
            "if", "else", "while", "for", "do",
            "return", "break", "continue",
            "true", "false", "null"
    ));

    // ----------------------------------------------------------------
    // 内部状态枚举（状态转换图的各状态）
    // ----------------------------------------------------------------
    private enum State {
        START,          // 初始/空白跳过状态
        IN_ID,          // 正在识别标识符或关键字
        IN_INT,         // 正在识别整数
        IN_FLOAT,       // 正在识别浮点数（遇到小数点后）
        IN_STRING,      // 正在识别字符串常量
        IN_CHAR,        // 正在识别字符常量
        IN_OP,          // 正在识别（可能的多字符）运算符
        IN_LINE_COMMENT,// 正在跳过单行注释
        IN_BLOCK_COMMENT// 正在跳过块注释
    }

    // ----------------------------------------------------------------
    // 字段
    // ----------------------------------------------------------------
    private final String source;        // 源程序字符串
    private int pos;                    // 当前扫描位置（字符下标）
    private int line;                   // 当前行号（从 1 开始）
    private int column;                 // 当前列号（从 1 开始）
    private final List<Token> tokens;   // 输出的 Token 列表（有序）
    private final List<String> errors;  // 词法错误列表

    // ----------------------------------------------------------------
    // 构造器
    // ----------------------------------------------------------------
    public Lexer(String source) {
        this.source  = source;
        this.pos     = 0;
        this.line    = 1;
        this.column  = 1;
        this.tokens  = new ArrayList<>();
        this.errors  = new ArrayList<>();
    }

    // ================================================================
    // 公共接口：执行词法分析，返回 Token 列表
    // ================================================================
    public List<Token> tokenize() {
        while (pos < source.length()) {
            skipWhitespace();           // 跳过空白字符
            if (pos >= source.length()) break;

            char c = peek();

            // ---------- 注释处理 ----------
            if (c == '/' && pos + 1 < source.length()) {
                char next = source.charAt(pos + 1);
                if (next == '/') {
                    skipLineComment();
                    continue;
                } else if (next == '*') {
                    skipBlockComment();
                    continue;
                }
            }

            // ---------- 标识符 / 关键字 ----------
            if (Character.isLetter(c) || c == '_') {
                tokens.add(readIdentifierOrKeyword());
                continue;
            }

            // ---------- 数字常量（整数 / 浮点数）----------
            if (Character.isDigit(c)) {
                tokens.add(readNumber());
                continue;
            }

            // ---------- 字符串常量 ----------
            if (c == '"') {
                tokens.add(readString());
                continue;
            }

            // ---------- 字符常量 ----------
            if (c == '\'') {
                tokens.add(readChar());
                continue;
            }

            // ---------- 运算符 ----------
            if (isOperatorStart(c)) {
                tokens.add(readOperator());
                continue;
            }

            // ---------- 界符 ----------
            if (isDelimiter(c)) {
                int tokLine = line, tokCol = column;
                advance();
                tokens.add(new Token(TokenType.DELIMITER, String.valueOf(c), tokLine, tokCol));
                continue;
            }

            // ---------- 非法字符 ----------
            int errLine = line, errCol = column;
            String errMsg = String.format("[错误] 非法字符 '%c' 在 %d:%d", c, errLine, errCol);
            errors.add(errMsg);
            tokens.add(new Token(TokenType.UNKNOWN, String.valueOf(c), errLine, errCol));
            advance();
        }

        // 追加 EOF
        tokens.add(new Token(TokenType.EOF, "EOF", line, column));
        return tokens;
    }

    /** 获取词法错误列表 */
    public List<String> getErrors() {
        return errors;
    }

    // ================================================================
    // 识别：标识符 / 关键字
    // 规则：以字母或下划线开头，后续可跟字母、数字、下划线
    // 状态：START -> IN_ID -> (结束)
    // ================================================================
    private Token readIdentifierOrKeyword() {
        int startLine = line, startCol = column;
        StringBuilder sb = new StringBuilder();

        while (pos < source.length()) {
            char c = peek();
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
                advance();
            } else {
                break;
            }
        }

        String word = sb.toString();
        TokenType type = KEYWORDS.contains(word) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
        return new Token(type, word, startLine, startCol);
    }

    // ================================================================
    // 识别：整数 / 浮点数
    // 规则：
    //   整数  -> [0-9]+
    //   浮点数 -> [0-9]+ '.' [0-9]+
    // 状态：START -> IN_INT -> (遇到'.'则转) IN_FLOAT -> (结束)
    // ================================================================
    private Token readNumber() {
        int startLine = line, startCol = column;
        StringBuilder sb = new StringBuilder();
        boolean isFloat = false;

        // 整数部分
        while (pos < source.length() && Character.isDigit(peek())) {
            sb.append(peek());
            advance();
        }

        // 检查是否有小数点（浮点数部分）
        if (pos < source.length() && peek() == '.'
                && pos + 1 < source.length() && Character.isDigit(source.charAt(pos + 1))) {
            isFloat = true;
            sb.append('.');
            advance(); // 消耗 '.'
            while (pos < source.length() && Character.isDigit(peek())) {
                sb.append(peek());
                advance();
            }
        }

        TokenType type = isFloat ? TokenType.FLOAT : TokenType.INTEGER;
        return new Token(type, sb.toString(), startLine, startCol);
    }

    // ================================================================
    // 识别：字符串常量
    // 规则："..." 中间可含转义字符 \" \n \t \\
    // ================================================================
    private Token readString() {
        int startLine = line, startCol = column;
        StringBuilder sb = new StringBuilder();
        advance(); // 跳过开头 "

        while (pos < source.length()) {
            char c = peek();
            if (c == '\\') {
                // 处理转义字符
                advance();
                if (pos < source.length()) {
                    char esc = peek();
                    advance();
                    switch (esc) {
                        case 'n':  sb.append('\n'); break;
                        case 't':  sb.append('\t'); break;
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        default:   sb.append('\\'); sb.append(esc); break;
                    }
                }
            } else if (c == '"') {
                advance(); // 跳过结尾 "
                break;
            } else if (c == '\n') {
                // 字符串不能跨行（报错但继续）
                errors.add(String.format("[错误] 字符串未闭合（换行），在 %d:%d", line, column));
                break;
            } else {
                sb.append(c);
                advance();
            }
        }

        return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    // ================================================================
    // 识别：字符常量
    // 规则：'c' 或 '\n' 等单个字符
    // ================================================================
    private Token readChar() {
        int startLine = line, startCol = column;
        advance(); // 跳过开头 '

        StringBuilder sb = new StringBuilder();
        if (pos < source.length()) {
            char c = peek();
            if (c == '\\') {
                advance();
                if (pos < source.length()) {
                    sb.append('\\');
                    sb.append(peek());
                    advance();
                }
            } else {
                sb.append(c);
                advance();
            }
        }

        // 期望结尾 '
        if (pos < source.length() && peek() == '\'') {
            advance();
        } else {
            errors.add(String.format("[错误] 字符常量未闭合，在 %d:%d", startLine, startCol));
        }

        return new Token(TokenType.CHAR, sb.toString(), startLine, startCol);
    }

    // ================================================================
    // 识别：运算符（含多字符运算符）
    // 支持：+ - * / % = == != < <= > >= && || !
    // 状态：START -> IN_OP -> 判断是否需要再读一个字符
    // ================================================================
    private Token readOperator() {
        int startLine = line, startCol = column;
        char c = peek();
        advance();

        // 尝试组成双字符运算符
        if (pos < source.length()) {
            char next = peek();
            String two = "" + c + next;
            switch (two) {
                case "==": case "!=": case "<=": case ">=":
                case "&&": case "||": case "++": case "--":
                case "+=": case "-=": case "*=": case "/=":
                    advance(); // 消耗第二个字符
                    return new Token(TokenType.OPERATOR, two, startLine, startCol);
                default:
                    break;
            }
        }

        return new Token(TokenType.OPERATOR, String.valueOf(c), startLine, startCol);
    }

    // ================================================================
    // 跳过：单行注释 //
    // ================================================================
    private void skipLineComment() {
        // 跳过 "//"
        advance(); advance();
        while (pos < source.length() && peek() != '\n') {
            advance();
        }
    }

    // ================================================================
    // 跳过：块注释 /* ... */
    // ================================================================
    private void skipBlockComment() {
        // 跳过 "/*"
        advance(); advance();
        while (pos < source.length()) {
            if (peek() == '*' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                advance(); advance(); // 跳过 "*/"
                return;
            }
            advance();
        }
        // 注释未闭合
        errors.add(String.format("[错误] 块注释未闭合，在 %d:%d", line, column));
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /** 跳过空白字符（空格、制表符、换行） */
    private void skipWhitespace() {
        while (pos < source.length() && Character.isWhitespace(peek())) {
            advance();
        }
    }

    /** 查看当前字符（不消耗） */
    private char peek() {
        return source.charAt(pos);
    }

    /** 消耗当前字符，更新行列号 */
    private void advance() {
        if (pos < source.length()) {
            if (source.charAt(pos) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            pos++;
        }
    }

    /** 判断字符是否为运算符起始字符 */
    private boolean isOperatorStart(char c) {
        return "+-*/%=!<>&|".indexOf(c) >= 0;
    }

    /** 判断字符是否为界符 */
    private boolean isDelimiter(char c) {
        return "(){}[];,".indexOf(c) >= 0;
    }
}
