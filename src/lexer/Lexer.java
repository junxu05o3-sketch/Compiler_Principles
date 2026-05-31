package lexer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 词法分析器（Lexer / Scanner）核心类 —— 编译器的第一个阶段。
 *
 * <h3>在编译器流程中的位置</h3>
 * <pre>
 *   源程序字符串 (String)  →  [词法分析器 Lexer]  →  Token 序列 (List&lt;Token&gt;)
 * </pre>
 * 词法分析器是整个编译过程的前端入口。它接收一个字符串形式的源程序，
 * 输出一个 Token（二元式）序列，供后续的语法分析器使用。
 *
 * <h3>实现原理：状态转换图（DFA 模拟）</h3>
 *
 * <p>词法分析本质上是在模拟一个<b>确定有限自动机（DFA）</b>。本实现采用
 * <b>状态转换图（Transition Diagram）</b> 方法，核心思想是：
 * <ol>
 *   <li>将识别每种单词的过程抽象为一个"状态"</li>
 *   <li>从初始状态 START 出发，根据当前读入字符决定进入哪个子状态</li>
 *   <li>子状态持续读入字符，直到遇到不属于该单词的字符为止</li>
 *   <li>依据最长匹配原则，识别出一个完整单词并生成 Token</li>
 * </ol>
 *
 * <h3>状态设计（共 9 个状态）</h3>
 * <pre>
 *                    ┌──────────┐
 *              ┌────→│  START   │←────┐
 *              │     └────┬─────┘     │
 *        字母/_│    数字  │  "   '  运算符  界符   /+字母
 *              ↓     ↓    ↓   ↓    ↓     ↓      ↓
 *         IN_ID  IN_INT IN_STRING IN_CHAR IN_OP DELIMITER → 直接输出
 *           │      │                              │
 *           │  遇到'.'?                           │
 *           │      ↓                              │
 *           │  IN_FLOAT                           │
 *           ↓      ↓                              ↓
 *         (返回Token)                         (检查是否为注释)
 *                                                  │
 *                                     // → IN_LINE_COMMENT
 *                                     /* → IN_BLOCK_COMMENT
 * </pre>
 *
 * <h3>最长匹配原则（Maximal Munch）</h3>
 * 当输入 "ifa" 时，虽然 "if" 是关键字，但由于后面跟了 'a' 且 'a' 是合法的
 * 标识符字符，所以整体识别为标识符 "ifa" 而非关键字 "if" + 标识符 "a"。
 *
 * <h3>支持的语言特性</h3>
 * <ul>
 *   <li>15 个 C/Java 风格关键字（int, float, if, while, return 等）</li>
 *   <li>标识符（字母/下划线开头，字母/数字/下划线后续）</li>
 *   <li>整数常量（纯数字序列）和浮点数常量（含小数点）</li>
 *   <li>字符串常量 "..."，支持转义字符 \n \t \" \\</li>
 *   <li>字符常量 '...'，支持转义字符</li>
 *   <li>单行注释 // 和块注释 /&#42; ... &#42;/</li>
 *   <li>14 种运算符（含双字符运算符如 ==, !=, &lt;=, &gt;=, &amp;&amp;, || 等）</li>
 *   <li>7 种界符（( ) { } [ ] ; ,）</li>
 *   <li>非法字符的错误检测与报告</li>
 * </ul>
 *
 * @author 编译原理课程设计
 * @see Token     输出的 Token（二元式）
 * @see TokenType 种别码枚举
 */
public class Lexer {

    // ================================================================
    // 静态常量：关键字查找表
    // 作用：O(1) 时间判断一个标识符是否为保留关键字
    // 扩展方式：直接在此 Set 中添加新的关键字字符串即可
    // ================================================================

    /** 关键字集合 —— 用于快速判断标识符是否为保留字。
     *  包含 C/Java 中的常见类型关键字、控制流关键字和常量关键字。 */
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            // ---- 类型关键字 ----
            "int", "float", "double", "char", "void", "bool",
            // ---- 控制流关键字 ----
            "if", "else", "while", "for", "do",
            "return", "break", "continue",
            // ---- 常量关键字 ----
            "true", "false", "null"
    ));

    // ================================================================
    // 内部枚举：词法分析器状态（模拟 DFA 的状态集）
    //
    // 设计思路：
    //   - 将 "识别某种单词的过程" 抽象为一个状态
    //   - 每个状态内部循环读入字符，直到遇到终止条件
    //   - 状态之间没有显式转换（转换逻辑内嵌在 tokenize() 的 if-else 分支中）
    // ================================================================

    /** 词法分析器内部状态枚举 —— 模拟 DFA 的状态转换图 */
    private enum State {
        /** 初始/空白跳过状态 —— 每个新 Token 识别的起点，在此状态下跳过空白字符 */
        START,

        /** 正在识别标识符或关键字 —— 已读到首字母/下划线，继续读字母/数字/下划线 */
        IN_ID,

        /** 正在识别整数 —— 已读到数字，继续读数字序列 */
        IN_INT,

        /** 正在识别浮点数 —— 已读到整数部分 + 小数点，继续读小数部分 */
        IN_FLOAT,

        /** 正在识别字符串常量 —— 已读到开头的双引号 "，持续读到下一个未转义的双引号 */
        IN_STRING,

        /** 正在识别字符常量 —— 已读到开头的单引号 '，读取一个字符（可能含转义），再读结尾的单引号 */
        IN_CHAR,

        /** 正在识别运算符 —— 已读到运算符首字符，可能需要再读一个字符组成双字符运算符 */
        IN_OP,

        /** 正在跳过单行注释 —— 已匹配 // ，持续读到换行符为止 */
        IN_LINE_COMMENT,

        /** 正在跳过块注释 —— 已匹配 /* ，持续读到 *​/ 为止 */
        IN_BLOCK_COMMENT
    }

    // ================================================================
    // 实例字段
    // ================================================================

    /** 源程序字符串 —— 整个待编译的源代码文本 */
    private final String source;

    /** 当前扫描位置（字符下标，从 0 开始）—— 指向下一个待读取的字符 */
    private int pos;

    /** 当前行号（从 1 开始）—— 遇到换行符时自增，用于错误报告 */
    private int line;

    /** 当前列号（从 1 开始）—— 遇到换行符时重置为 1 */
    private int column;

    /** Token 输出列表 —— 按扫描顺序存储所有识别出的 Token，是词法分析的最终产物 */
    private final List<Token> tokens;

    /** 词法错误列表 —— 收集所有扫描过程中遇到的非法字符和未闭合注释/字符串错误 */
    private final List<String> errors;

    // ================================================================
    // 构造函数
    // ================================================================

    /**
     * 构造词法分析器。
     * 初始化扫描指针到源程序开头，行列号均为 1，Token 列表和错误列表为空。
     *
     * @param source 待编译的源程序字符串（完整的源代码文本）
     */
    public Lexer(String source) {
        this.source  = source;
        this.pos     = 0;      // 从第一个字符开始扫描
        this.line    = 1;      // 行号从 1 开始
        this.column  = 1;      // 列号从 1 开始
        this.tokens  = new ArrayList<>();
        this.errors  = new ArrayList<>();
    }

    // ================================================================
    // 公共接口
    // ================================================================

    /**
     * 【核心接口】执行词法分析，将源程序字符串转换为 Token 序列。
     *
     * <h3>算法流程（词法分析主循环）</h3>
     * <ol>
     *   <li><b>跳过空白</b> —— 忽略空格、制表符、换行符</li>
     *   <li><b>判断首字符类型</b> —— 根据首字符决定进入哪个识别子程序：
     *     <ul>
     *       <li>字母/下划线 → 识别标识符或关键字</li>
     *       <li>数字 → 识别整数或浮点数常量</li>
     *       <li>双引号 " → 识别字符串常量</li>
     *       <li>单引号 ' → 识别字符常量</li>
     *       <li>运算符首字符 → 识别单/双字符运算符</li>
     *       <li>界符字符 → 直接生成界符 Token</li>
     *       <li>/ + / → 单行注释（跳过）</li>
     *       <li>/ + * → 块注释（跳过）</li>
     *       <li>其他 → 非法字符错误</li>
     *     </ul>
     *   </li>
     *   <li><b>循环</b> —— 重复步骤 1-2 直到源程序全部扫描完毕</li>
     *   <li><b>追加 EOF</b> —— 在 Token 序列末尾添加文件结束标记</li>
     * </ol>
     *
     * @return 完整的 Token 序列（包含末尾的 EOF Token）
     */
    public List<Token> tokenize() {
        // 主循环：逐个字符扫描，直到源程序末尾
        while (pos < source.length()) {
            skipWhitespace();           // ① 跳过所有空白字符（空格、制表符、换行）
            if (pos >= source.length()) break;  // 防止跳过空白后到达文件末尾

            char c = peek();            // ② 获取当前字符（不消耗）

            // ----------------------------------------------------
            // ③ 注释处理（优先级最高，因为以 / 开头）
            //    / 有两种可能：注释开头 或 除法运算符
            //    需要 lookahead 一个字符来区分
            // ----------------------------------------------------
            if (c == '/' && pos + 1 < source.length()) {
                char next = source.charAt(pos + 1);
                if (next == '/') {
                    skipLineComment();  // 单行注释：// ... → 直接跳过
                    continue;
                } else if (next == '*') {
                    skipBlockComment(); // 块注释：/* ... */ → 直接跳过
                    continue;
                }
                // 否则就是除法运算符 /，留给后面的运算符识别处理
            }

            // ----------------------------------------------------
            // ④ 标识符 / 关键字识别
            //    规则：以字母或下划线开头
            // ----------------------------------------------------
            if (Character.isLetter(c) || c == '_') {
                tokens.add(readIdentifierOrKeyword());
                continue;
            }

            // ----------------------------------------------------
            // ⑤ 数字常量识别（整数 / 浮点数）
            //    规则：以数字开头
            // ----------------------------------------------------
            if (Character.isDigit(c)) {
                tokens.add(readNumber());
                continue;
            }

            // ----------------------------------------------------
            // ⑥ 字符串常量识别
            //    规则：以双引号 " 开头
            // ----------------------------------------------------
            if (c == '"') {
                tokens.add(readString());
                continue;
            }

            // ----------------------------------------------------
            // ⑦ 字符常量识别
            //    规则：以单引号 ' 开头
            // ----------------------------------------------------
            if (c == '\'') {
                tokens.add(readChar());
                continue;
            }

            // ----------------------------------------------------
            // ⑧ 运算符识别（含多字符运算符）
            //    规则：首字符属于运算符字符集
            // ----------------------------------------------------
            if (isOperatorStart(c)) {
                tokens.add(readOperator());
                continue;
            }

            // ----------------------------------------------------
            // ⑨ 界符识别（单字符，直接输出）
            //    规则：字符属于界符字符集
            // ----------------------------------------------------
            if (isDelimiter(c)) {
                int tokLine = line, tokCol = column;
                advance();
                tokens.add(new Token(TokenType.DELIMITER, String.valueOf(c), tokLine, tokCol));
                continue;
            }

            // ----------------------------------------------------
            // ⑩ 非法字符 —— 不属于任何合法类的字符
            //    记录错误但不中断分析（错误恢复策略）
            // ----------------------------------------------------
            int errLine = line, errCol = column;
            String errMsg = String.format("[错误] 非法字符 '%c' 在 %d:%d", c, errLine, errCol);
            errors.add(errMsg);
            tokens.add(new Token(TokenType.UNKNOWN, String.valueOf(c), errLine, errCol));
            advance();  // 跳过非法字符，继续分析后续内容
        }

        // ⑪ 在 Token 序列末尾追加文件结束符
        //    语法分析器依靠 EOF 来判断输入是否读完
        tokens.add(new Token(TokenType.EOF, "EOF", line, column));
        return tokens;
    }

    /**
     * 获取词法分析过程中收集的所有错误信息。
     * 调用时机：在 tokenize() 之后调用，用于编译错误报告。
     *
     * @return 词法错误信息列表（每个元素是一条错误描述）
     */
    public List<String> getErrors() {
        return errors;
    }

    // ================================================================
    // 识别子程序：标识符 / 关键字
    //
    // 编程语言中的标识符规则（C/Java 风格）：
    //   标识符 → [a-zA-Z_][a-zA-Z0-9_]*
    //
    // 识别策略：
    //   1. 从 START 状态进入后，持续读取字母/数字/下划线
    //   2. 遇到非标识符字符时停止
    //   3. 检查识别到的字符串是否在关键字集合中
    //   4. 在关键字集合中 → TokenType.KEYWORD
    //      不在关键字集合中 → TokenType.IDENTIFIER
    // ================================================================

    /**
     * 识别标识符或关键字。
     *
     * <p>算法：
     * <ol>
     *   <li>循环读取字母/数字/下划线字符，直到遇到非标识符字符</li>
     *   <li>将读到的字符串在关键字集合中查找</li>
     *   <li>命中 → KEYWORD；未命中 → IDENTIFIER</li>
     * </ol>
     *
     * @return 识别出的 Token（种别码为 KEYWORD 或 IDENTIFIER）
     */
    private Token readIdentifierOrKeyword() {
        int startLine = line, startCol = column;  // 保存起始位置（用于错误报告）
        StringBuilder sb = new StringBuilder();

        // 循环读取：只要当前字符是字母/数字/下划线，就继续读取
        while (pos < source.length()) {
            char c = peek();
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
                advance();
            } else {
                break;  // 遇到非标识符字符 → 识别结束
            }
        }

        String word = sb.toString();
        // 关键字判定：在预定义关键字集合中查找
        // 例如读到 "int" → KEYWORD；读到 "myVar" → IDENTIFIER
        TokenType type = KEYWORDS.contains(word) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
        return new Token(type, word, startLine, startCol);
    }

    // ================================================================
    // 识别子程序：整数 / 浮点数
    //
    // 数字常量的正则定义（简化版）：
    //   整数   → digit+
    //   浮点数 → digit+ . digit+
    //
    // 注意：本实现不支持科学计数法（如 1.5e10）和前导正负号
    //      负号如 -5 在语法分析阶段处理为：一元负运算符 + 整数常量
    //
    // 输入示例与对应输出：
    //   "123"   → (INTEGER, "123")
    //   "3.14"  → (FLOAT, "3.14")
    //   "12."   → (INTEGER, "12") + (DELIMITER, ".")  —— 不视为浮点数
    // （因为 "." 后面不是数字，不符合浮点数格式）
    // ================================================================

    /**
     * 识别整数或浮点数常量。
     *
     * <p>算法：
     * <ol>
     *   <li>读取整数部分：循环读取连续的数字字符</li>
     *   <li>检查是否有小数点：若当前字符为 '.' 且下一个字符是数字，
     *       则进入浮点数模式，读取小数部分</li>
     *   <li>根据是否遇到小数点决定种别码为 INTEGER 还是 FLOAT</li>
     * </ol>
     *
     * @return 识别出的 Token（种别码为 INTEGER 或 FLOAT）
     */
    private Token readNumber() {
        int startLine = line, startCol = column;
        StringBuilder sb = new StringBuilder();
        boolean isFloat = false;

        // ① 读取整数部分：连续读取数字字符
        while (pos < source.length() && Character.isDigit(peek())) {
            sb.append(peek());
            advance();
        }

        // ② 判断是否有小数点：需要前看（lookahead）一个字符
        //    条件：当前字符是 '.' 且下一个字符是数字（避免把 "12." 误判为浮点数）
        if (pos < source.length() && peek() == '.'
                && pos + 1 < source.length() && Character.isDigit(source.charAt(pos + 1))) {
            isFloat = true;
            sb.append('.');           // 记录小数点
            advance();                // 消耗 '.' 字符

            // ③ 读取小数部分
            while (pos < source.length() && Character.isDigit(peek())) {
                sb.append(peek());
                advance();
            }
        }

        // ④ 根据是否有小数点决定种别码
        TokenType type = isFloat ? TokenType.FLOAT : TokenType.INTEGER;
        return new Token(type, sb.toString(), startLine, startCol);
    }

    // ================================================================
    // 识别子程序：字符串常量
    //
    // 字符串常量的形式为： "任意字符序列（可含转义）"
    //
    // 转义字符支持（共 4 个标准转义）：
    //   \n → 换行符 (ASCII 10)
    //   \t → 制表符 (ASCII 9)
    //   \" → 双引号（避免与字符串边界混淆）
    //   \\ → 反斜杠本身
    //
    // 错误处理：
    //   - 字符串中遇到换行符 → 报错"字符串未闭合"，视为字符串结束
    //   - 到达文件末尾仍未遇到闭合引号 → 自然结束（不在本方法中处理）
    // ================================================================

    /**
     * 识别字符串常量 {@code "..."}。
     *
     * <p>算法：
     * <ol>
     *   <li>消耗开头的双引号 "</li>
     *   <li>循环读取字符：
     *     <ul>
     *       <li>遇到反斜杠 \ → 进入转义字符处理（\n, \t, \", \\）</li>
     *       <li>遇到双引号 " → 字符串结束，消耗引号并跳出循环</li>
     *       <li>遇到换行符 → 报错（字符串不能跨行），跳出循环</li>
     *       <li>其他字符 → 直接追加到结果</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @return 识别出的 Token（种别码为 STRING，属性值为去掉引号和转义后的实际字符串内容）
     */
    private Token readString() {
        int startLine = line, startCol = column;
        StringBuilder sb = new StringBuilder();
        advance(); // 消耗开头的双引号 "

        while (pos < source.length()) {
            char c = peek();

            if (c == '\\') {
                // ---- 处理转义字符 ----
                advance();  // 消耗反斜杠
                if (pos < source.length()) {
                    char esc = peek();
                    advance();  // 消耗转义后的字符
                    switch (esc) {
                        case 'n':  sb.append('\n'); break;   // \n → 换行
                        case 't':  sb.append('\t'); break;   // \t → 制表
                        case '"':  sb.append('"');  break;   // \" → 双引号
                        case '\\': sb.append('\\'); break;   // \\ → 反斜杠
                        default:   sb.append('\\'); sb.append(esc); break; // 未知转义→保持原样
                    }
                }
            } else if (c == '"') {
                // ---- 遇到闭合双引号：字符串结束 ----
                advance();  // 消耗结尾的双引号
                break;
            } else if (c == '\n') {
                // ---- 字符串中不能直接出现换行符：报错 ----
                errors.add(String.format("[错误] 字符串未闭合（遇到换行），在 %d:%d", line, column));
                break;
            } else {
                // ---- 普通字符：直接追加 ----
                sb.append(c);
                advance();
            }
        }

        return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    // ================================================================
    // 识别子程序：字符常量
    //
    // 字符常量的形式为： '单个字符（可含转义）'
    //
    // 合法示例：
    //   'a'   → (CHAR, "a")
    //   '\n'  → (CHAR, "\n")      // 转义字符被视为一个字符
    //   '\\'  → (CHAR, "\\")
    // ================================================================

    /**
     * 识别字符常量 {@code '...'}。
     *
     * <p>算法：
     * <ol>
     *   <li>消耗开头的单引号 '</li>
     *   <li>读取一个字符（可能是普通字符或转义序列）</li>
     *   <li>检查并消耗结尾的单引号 '</li>
     *   <li>若缺少结尾引号，记录错误</li>
     * </ol>
     *
     * @return 识别出的 Token（种别码为 CHAR，属性值为字符内容）
     */
    private Token readChar() {
        int startLine = line, startCol = column;
        advance(); // 消耗开头的单引号 '

        StringBuilder sb = new StringBuilder();
        if (pos < source.length()) {
            char c = peek();
            if (c == '\\') {
                // 转义字符：保留 \ 和后面的字符
                advance();
                if (pos < source.length()) {
                    sb.append('\\');
                    sb.append(peek());
                    advance();
                }
            } else {
                // 普通字符：直接追加
                sb.append(c);
                advance();
            }
        }

        // 期望结尾的单引号 '
        if (pos < source.length() && peek() == '\'') {
            advance();  // 消耗结尾引号
        } else {
            // 缺少闭合引号 → 报错
            errors.add(String.format("[错误] 字符常量未闭合，在 %d:%d", startLine, startCol));
        }

        return new Token(TokenType.CHAR, sb.toString(), startLine, startCol);
    }

    // ================================================================
    // 识别子程序：运算符（含多字符运算符前瞻）
    //
    // 编程语言中存在大量由两个字符组成的运算符，如：
    //   关系运算符：==  !=  <=  >=
    //   逻辑运算符：&&  ||
    //   自增自减：  ++  --
    //   复合赋值：  +=  -=  *=  /=
    //
    // 识别策略（前瞻一个字符 lookahead）：
    //   1. 读入第一个运算符字符
    //   2. 看下一个字符，判断它们组合起来是否构成合法的双字符运算符
    //   3. 是 → 消耗两个字符，生成一个双字符运算符 Token
    //      否 → 只消耗第一个字符，生成单字符运算符 Token
    // ================================================================

    /**
     * 识别运算符（含单字符和双字符运算符）。
     *
     * <p>算法（前瞻一个字符）：
     * <ol>
     *   <li>读取并消耗运算符的第一个字符</li>
     *   <li>检查当前字符与下一个字符的组合是否为合法的双字符运算符</li>
     *   <li>是双字符运算符 → 消耗第二个字符，返回双字符运算符 Token</li>
     *   <li>不是 → 返回单字符运算符 Token</li>
     * </ol>
     *
     * <p>支持的双字符运算符（共 12 种）：
     * ==, !=, &lt;=, &gt;=, &amp;&amp;, ||, ++, --, +=, -=, *=, /=
     *
     * @return 识别出的 Token（种别码为 OPERATOR）
     */
    private Token readOperator() {
        int startLine = line, startCol = column;
        char c = peek();
        advance();  // 消耗第一个运算符字符

        // 尝试组成双字符运算符（前瞻一个字符）
        if (pos < source.length()) {
            char next = peek();
            String two = "" + c + next;
            switch (two) {
                // 关系运算符
                case "==": case "!=": case "<=": case ">=":
                // 逻辑运算符
                case "&&": case "||":
                // 自增自减
                case "++": case "--":
                // 复合赋值运算符
                case "+=": case "-=": case "*=": case "/=":
                    advance(); // 消耗第二个字符
                    return new Token(TokenType.OPERATOR, two, startLine, startCol);
                default:
                    break;  // 不是合法的双字符运算符，回退为单字符
            }
        }

        // 单字符运算符：+ - * / % = ! < > & |
        return new Token(TokenType.OPERATOR, String.valueOf(c), startLine, startCol);
    }

    // ================================================================
    // 注释跳过子程序
    //
    // 注释不是 Token，而是应该被完全忽略的文本。
    // 编译器在词法分析阶段直接跳过注释内容，不产生任何输出。
    //
    // 两种注释风格（继承自 C 语言）：
    //   单行注释：// 从此处到行尾的所有字符都被忽略
    //   块注释：  /* 从此处到 *​/ 的所有字符都被忽略（可跨多行）
    // ================================================================

    /**
     * 跳过单行注释 {@code // ...}。
     *
     * <p>算法：从 // 之后开始，持续向后扫描，直到遇到换行符或文件末尾。
     * 注意：换行符不被消耗（由 skipWhitespace 处理），所以注释结束后
     * 下一行内容仍能正常分析。
     */
    private void skipLineComment() {
        // 消耗 "//" 两个字符
        advance(); advance();
        // 持续跳过直到遇到换行符（\n）或文件末尾
        while (pos < source.length() && peek() != '\n') {
            advance();
        }
    }

    /**
     * 跳过块注释 {@code /* ... *​/}。
     *
     * <p>算法：
     * <ol>
     *   <li>消耗开头的 /* 两个字符</li>
     *   <li>持续扫描，寻找结尾标记 *​/</li>
     *   <li>遇到 *​/ → 消耗两个字符，正常返回</li>
     *   <li>遇到文件末尾仍未找到 *​/ → 记录"块注释未闭合"错误</li>
     * </ol>
     *
     * <p>注意：本实现不支持嵌套块注释（C 语言也不支持）。
     */
    private void skipBlockComment() {
        // 消耗 "/*" 两个字符
        advance(); advance();

        // 持续扫描，寻找 "*/"
        while (pos < source.length()) {
            // 检查当前字符和下一个字符是否构成 "*/"
            if (peek() == '*' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                advance(); advance(); // 消耗 "*/" 两个字符，正常退出
                return;
            }
            advance();  // 普通注释内容字符，跳过
        }

        // 到达文件末尾仍未找到 "*/" → 注释未闭合错误
        errors.add(String.format("[错误] 块注释未闭合，在 %d:%d", line, column));
    }

    // ================================================================
    // 工具方法（辅助函数）
    // ================================================================

    /**
     * 跳过空白字符（空格、制表符 \t、换行符 \n、回车符 \r 等）。
     *
     * <p>空白字符在词法上仅起分隔作用，不产生任何 Token。
     * 在扫描每个新 Token 之前调用此方法，确保当前指针指向一个有意义的字符。
     *
     * <p>注意：换行符在此被跳过，行列号的更新由 {@link #advance()} 统一处理。
     */
    private void skipWhitespace() {
        while (pos < source.length() && Character.isWhitespace(peek())) {
            advance();
        }
    }

    /**
     * 查看当前字符（不消耗，不移动扫描指针）。
     *
     * <p>这是实现前瞻（lookahead）的基本操作。在需要根据当前字符
     * 做分支判断时使用，避免错误地消耗不属于当前 Token 的字符。
     *
     * @return 扫描指针当前指向的字符
     */
    private char peek() {
        return source.charAt(pos);
    }

    /**
     * 消耗当前字符（移动扫描指针），并更新行列号。
     *
     * <p>这是词法分析器中最基本的操作，每读入一个字符都必须调用此方法。
     *
     * <p>行列号更新规则：
     * <ul>
     *   <li>当前字符为 \n → 行号 +1，列号重置为 1</li>
     *   <li>其他字符 → 列号 +1</li>
     * </ul>
     */
    private void advance() {
        if (pos < source.length()) {
            if (source.charAt(pos) == '\n') {
                line++;      // 遇到换行：行号加 1
                column = 1;  // 列号回到第 1 列
            } else {
                column++;    // 普通字符：列号加 1
            }
            pos++;           // 移动扫描指针
        }
    }

    /**
     * 判断字符是否可以作为运算符的起始字符。
     *
     * <p>运算符首字符集合 = { +, -, *, /, %, =, !, &lt;, &gt;, &amp;, | }
     *
     * <p>注意：'/' 同时也是注释的起始字符，需要在 tokenize() 主循环中
     * 先检查注释，注释不匹配时才进入运算符识别。
     *
     * @param c 待检查的字符
     * @return true 如果该字符可以作为运算符的起始字符
     */
    private boolean isOperatorStart(char c) {
        return "+-*/%=!<>&|".indexOf(c) >= 0;
    }

    /**
     * 判断字符是否为界符（分隔符）。
     *
     * <p>界符字符集合 = { (, ), {, }, [, ], ;, , }
     *
     * <p>界符的特点：
     * <ul>
     *   <li>都是单字符 Token（不存在多字符界符）</li>
     *   <li>每个界符在语法上有明确的含义（如分号表示语句结束）</li>
     *   <li>识别最简单：直接生成 Token，无需前瞻</li>
     * </ul>
     *
     * @param c 待检查的字符
     * @return true 如果该字符是界符
     */
    private boolean isDelimiter(char c) {
        return "(){}[];,".indexOf(c) >= 0;
    }
}
