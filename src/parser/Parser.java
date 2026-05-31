package parser;

import lexer.Token;
import lexer.TokenType;
import java.util.ArrayList;
import java.util.List;

/**
 * 语法分析器（Parser）—— 编译器的第二个阶段，采用递归下降分析法。
 *
 * <h3>在编译器流程中的位置</h3>
 * <pre>
 *   Token 序列 → [语法分析器 Parser] → 语法树 (TreeNode) + 四元式序列 (List&lt;Quadruple&gt;)
 * </pre>
 *
 * <h3>实现方法：递归下降分析法（Recursive Descent Parsing）</h3>
 *
 * <p>递归下降分析是<b>自顶向下语法分析</b>最直观的实现方式。其核心思想是：
 * <ol>
 *   <li>为文法的<b>每个非终结符</b>编写一个递归函数</li>
 *   <li>每个函数根据当前输入符号（lookahead）选择一条产生式</li>
 *   <li>按产生式右部的顺序依次调用相应的函数（匹配终结符 或 调用非终结符函数）</li>
 *   <li>如果某条产生式推导失败，回溯尝试其他产生式（本项目未实现回溯，
 *       而是采用 LL(1) 的 lookahead 判断替代）</li>
 * </ol>
 *
 * <h3>本分析器处理的文法（简化 C 风格子集）</h3>
 * <pre>
 *   Program  → int main ( ) { StmtList }
 *   StmtList → Stmt StmtList | ε
 *   Stmt     → Decl | Assign | If | While
 *   Decl     → int id ;
 *   Assign   → id = Expr ;
 *   If       → if ( Expr relop Expr ) { StmtList }
 *   While    → while ( Expr relop Expr ) { StmtList }
 *   Expr     → Term ExprP
 *   ExprP    → + Term ExprP | - Term ExprP | ε
 *   Term     → Factor TermP
 *   TermP    → * Factor TermP | / Factor TermP | ε
 *   Factor   → id | num | ( Expr )
 * </pre>
 *
 * <h3>同步生成的两类输出</h3>
 * <ol>
 *   <li><b>语法树（TreeNode）</b> —— 用于可视化展示程序的语法结构</li>
 *   <li><b>四元式序列（Quadruple）</b> —— 中间代码，供后续优化器和目标代码生成使用</li>
 * </ol>
 *
 * <h3>临时变量命名约定</h3>
 * 表达式的每个中间计算结果存入临时变量 t1, t2, t3, ...，
 * 通过 {@link #newTemp()} 方法统一生成，确保名称唯一。
 *
 * @author 编译原理课程设计
 * @see TreeNode   语法树节点
 * @see Quadruple  四元式
 * @see SymbolTable 符号表
 * @see Optimizer  优化器（消费四元式序列）
 */
public class Parser {

    /** Token 序列 —— 词法分析器的输出，语法分析器的输入 */
    private List<Token> tokens;

    /** 当前 Token 指针 —— 指向下一个待解析的 Token */
    private int p = 0;

    /** 当前扫描到的 Token —— 即 lookahead 符号（LL(1) 的前看符号） */
    private Token currentToken;

    /** 四元式序列 —— 语法分析过程中同步生成的中间代码 */
    private List<Quadruple> quads = new ArrayList<>();

    /** 符号表 —— 管理变量声明，用于语义检查（先声明后使用） */
    private SymbolTable symbolTable = new SymbolTable();

    /** 临时变量计数器 —— 每调用一次 newTemp() 自增，生成 t1, t2, t3, ... */
    private int tempCount = 0;

    /**
     * 构造语法分析器。
     *
     * @param tokens 词法分析器输出的 Token 序列
     */
    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.currentToken = tokens.get(p);  // 初始化 lookahead 为第一个 Token
    }

    /**
     * 获取分析过程中生成的四元式序列（供外部优化器使用）。
     *
     * @return 四元式列表
     */
    public List<Quadruple> getQuads() {
        return this.quads;
    }

    // ================================================================
    // 核心工具方法（语法分析器的底层基础设施）
    // ================================================================

    /**
     * 匹配一个特定值的终结符（关键词/界符/运算符）。
     *
     * <p>这是递归下降分析中最基本的操作。当语法规则要求一个特定的
     * 终结符（如 "int", ";", "="）时，调用此方法：
     * <ul>
     *   <li><b>匹配成功</b> → 消耗该 Token，lookahead 前进到下一个 Token</li>
     *   <li><b>匹配失败</b> → 抛出异常，报告语法错误</li>
     * </ul>
     *
     * @param val 期望的终结符字符串值（如 "int", ";", "="）
     * @throws RuntimeException 如果当前 Token 的值与期望不符
     */
    private void matchValue(String val) {
        if (currentToken.getValue().equals(val)) {
            advance();  // 匹配成功：消耗此 Token
        } else {
            error("期望符号: " + val + "，实际获得: " + currentToken.getValue());
        }
    }

    /**
     * 将 lookahead 向前移动一个 Token（消耗当前 Token）。
     *
     * <p>这不是 LR 分析中从栈顶弹出符号的操作，而是相当于读入下一个输入符号。
     * 在递归下降分析中，调用 advance() 意味着当前 Token 已被成功解析。
     */
    private void advance() {
        if (p < tokens.size() - 1) {
            p++;
            currentToken = tokens.get(p);
        }
    }

    /**
     * 生成一个新的临时变量名。
     *
     * <p>命名格式：t1, t2, t3, ...
     *
     * <p>临时变量用于存储表达式计算过程中的中间结果。
     * 例如对于 a = b + c * d，会生成：
     * <pre>
     *   t1 = c * d
     *   t2 = b + t1
     *   a  = t2
     * </pre>
     *
     * @return 新临时变量名（如 "t1", "t2", ...）
     */
    private String newTemp() {
        return "t" + (++tempCount);
    }

    /**
     * 报告语法错误并抛出异常。
     *
     * <p>在完整的编译器中，错误处理应包含错误恢复（如应急模式 panic mode），
     * 以便在一次分析中报告多个错误。本实现采用简单的"遇错即停"策略。
     *
     * @param msg 错误描述信息
     * @throws RuntimeException 总是抛出异常
     */
    private void error(String msg) {
        throw new RuntimeException("[语法错误] 行 " + currentToken.getLine() + ": " + msg);
    }

    // ================================================================
    // 统一解析入口
    //
    // 调用 parse() 启动整个语法分析流程，返回语法树的根节点。
    // 同时四元式序列 quads 也被同步填充。
    // ================================================================

    /**
     * 【核心入口】启动语法分析，返回语法树的根节点，同时生成四元式序列。
     *
     * <h3>解析流程</h3>
     * <ol>
     *   <li>匹配函数签名 int main()</li>
     *   <li>匹配函数体开括号 {</li>
     *   <li>递归解析语句列表（StmtList）</li>
     *   <li>匹配函数体闭括号 }</li>
     * </ol>
     *
     * <p>调用此方法后，可通过 {@link #getQuads()} 获取四元式序列。
     *
     * @return 语法树的根节点（标签为 "Program"）
     * @throws RuntimeException 如果发生语法错误
     */
    public TreeNode parse() {
        // 创建语法树根节点
        TreeNode root = new TreeNode("Program");

        // 匹配函数头：int main()
        matchValue("int");    root.addChild(new TreeNode("int"));
        matchValue("main");   root.addChild(new TreeNode("main"));
        matchValue("(");      root.addChild(new TreeNode("("));
        matchValue(")");      root.addChild(new TreeNode(")"));
        matchValue("{");      root.addChild(new TreeNode("{"));

        // 递归解析函数体：语句列表
        root.addChild(statementList());

        // 匹配函数尾
        matchValue("}");      root.addChild(new TreeNode("}"));

        return root;
    }

    // ================================================================
    // 非终结符解析函数：StmtList → Stmt StmtList | ε
    //
    // 这是递归下降分析的核心模式——每个非终结符对应一个解析函数。
    // 本方法处理语句列表，当遇到 }（函数结束）或 EOF（文件结束）时
    // 选择 ε-产生式（不消费任何 Token，直接返回空节点）。
    // ================================================================

    /**
     * 解析语句列表（StmtList → Stmt StmtList | ε）。
     *
     * <p>采用 while 循环而非真正的递归调用，以避免过深的递归栈。
     * 本质上等价于尾递归优化后的递归下降分析。
     *
     * <p>ε-产生式的选择条件（何时停止解析语句）：
     * <ul>
     *   <li>当前 Token 是 "}"（函数体结束）</li>
     *   <li>当前 Token 是 EOF（文件结束）</li>
     * </ul>
     *
     * @return StmtList 对应的语法树节点
     */
    private TreeNode statementList() {
        TreeNode node = new TreeNode("StmtList");

        // 持续解析语句，直到遇到函数体闭括号或文件结束
        // （这是 ε-产生式 StmtList → ε 的触发条件）
        while (!currentToken.getValue().equals("}")
                && currentToken.getType() != TokenType.EOF) {
            node.addChild(statement());  // 解析一条语句
        }
        return node;
    }

    // ================================================================
    // 非终结符解析函数：Stmt → Decl | Assign | If | While
    //
    // 通过 lookahead（当前 Token 的值）判断选择哪条产生式：
    //   "int"   → Decl（变量声明）
    //   "if"    → If（条件语句）
    //   "while" → While（循环语句）
    //   标识符   → Assign（赋值语句）
    //   其他     → 跳过（空语句，兼容性处理）
    // ================================================================

    /**
     * 解析一条语句（Stmt → Decl | Assign | If | While）。
     *
     * <p>通过 lookahead 符号选择产生式（LL(1) 判断）：
     * <ul>
     *   <li>{@code "int"}   → 调用 {@link #declaration()}</li>
     *   <li>{@code "if"}    → 调用 {@link #ifStatement()}</li>
     *   <li>{@code "while"} → 调用 {@link #whileStatement()}</li>
     *   <li>{@code IDENTIFIER} → 调用 {@link #assignment()}</li>
     *   <li>其他 → 跳过（容错处理，消费一个 Token 后继续）</li>
     * </ul>
     *
     * @return Statement 对应的语法树节点
     */
    private TreeNode statement() {
        TreeNode node = new TreeNode("Statement");
        String val = currentToken.getValue();

        if (val.equals("int")) {
            // 产生式：Stmt → Decl
            node.addChild(declaration());
        } else if (val.equals("if")) {
            // 产生式：Stmt → If
            node.addChild(ifStatement());
        } else if (val.equals("while")) {
            // 产生式：Stmt → While
            node.addChild(whileStatement());
        } else if (currentToken.getType() == TokenType.IDENTIFIER) {
            // 产生式：Stmt → Assign（以标识符开头）
            node.addChild(assignment());
        } else {
            // 无法匹配任何产生式 → 容错处理：跳过当前 Token
            advance();
        }
        return node;
    }

    // ================================================================
    // 非终结符解析函数：Decl → int id ;
    //
    // 变量声明的处理：
    //   1. 匹配关键字 int
    //   2. 读取标识符（变量名）
    //   3. 将变量名加入符号表（语义动作）
    //   4. 匹配分号 ;
    // ================================================================

    /**
     * 解析变量声明语句（Decl → int id ;）。
     *
     * <h3>语义动作</h3>
     * 在识别到变量名时，将其加入符号表：
     * <pre>
     *   symbolTable.add(varName);
     * </pre>
     * 这样在后续的赋值语句中可以通过符号表检查变量是否已声明。
     *
     * @return Decl 对应的语法树节点
     */
    private TreeNode declaration() {
        TreeNode node = new TreeNode("Decl");

        matchValue("int");                           // 匹配类型关键字
        node.addChild(new TreeNode("int"));

        String varName = currentToken.getValue();    // 获取变量名
        symbolTable.add(varName);                    // 【语义动作】将变量加入符号表
        node.addChild(new TreeNode("id: " + varName));
        advance();                                   // 消耗标识符

        matchValue(";");                             // 匹配分号
        node.addChild(new TreeNode(";"));

        return node;
    }

    // ================================================================
    // 非终结符解析函数：Assign → id = Expr ;
    //
    // 赋值语句的处理流程：
    //   1. 读取左值变量名
    //   2. 语义检查：验证变量是否已声明
    //   3. 匹配 = 号
    //   4. 解析右值表达式（递归调用 expression()）
    //   5. 生成赋值四元式：(=, 表达式结果, _, 目标变量)
    //   6. 匹配分号 ;
    // ================================================================

    /**
     * 解析赋值语句（Assign → id = Expr ;）。
     *
     * <h3>语义检查</h3>
     * 在解析右值之前，首先检查左值变量是否已在符号表中声明。
     * 如果未声明则抛出"变量未声明"错误。这是"先声明后使用"规则的实现。
     *
     * <h3>中间代码生成</h3>
     * 解析完右值表达式后，生成一条赋值四元式：
     * <pre>
     *   (=, 表达式结果值, _, 目标变量名)
     * </pre>
     *
     * @return Assign 对应的语法树节点
     */
    private TreeNode assignment() {
        TreeNode node = new TreeNode("Assign");

        // ① 获取左值（目标变量）
        String target = currentToken.getValue();

        // ② 语义检查：变量使用前必须已声明
        if (!symbolTable.contains(target)) {
            error("变量未声明: " + target);
        }
        node.addChild(new TreeNode("id: " + target));
        advance();  // 消耗标识符

        // ③ 匹配赋值号
        matchValue("=");
        node.addChild(new TreeNode("="));

        // ④ 解析右值表达式（递归调用，返回表达式的结果值和对应的语法子树）
        NodeValue res = expression();
        node.addChild(res.node);

        // ⑤ 匹配分号
        matchValue(";");
        node.addChild(new TreeNode(";"));

        // ⑥ 生成四元式：(=, 表达式结果, _, 目标变量)
        quads.add(new Quadruple("=", res.val, "_", target));

        return node;
    }

    // ================================================================
    // 非终结符解析函数：While → while ( Expr relop Expr ) { StmtList }
    //
    // While 循环的中间代码（四元式）结构：
    //   beginAddr:  (条件判断四元式1)
    //               ...
    //               (j<relop>, left, right, ?)     ← 条件跳转，目标待回填
    //               (循环体四元式...)
    //               (j, _, _, beginAddr)           ← 无条件跳回循环头
    //   endAddr:    ← 条件跳转的目标地址
    // ================================================================

    /**
     * 解析 while 循环语句（While → while ( Expr relop Expr ) { StmtList }）。
     *
     * <h3>中间代码（四元式）结构</h3>
     * <pre>
     *   beginAddr:  条件判断表达式
     *               (j&lt;=relop&gt;, left, right, ?)   ← 条件为假时跳出，目标待回填
     *               循环体四元式...
     *               (j, _, _, beginAddr)              ← 无条件跳回循环头
     *   endAddr:    ← 回填条件跳转的目标地址
     * </pre>
     *
     * <h3>回填（Backpatching）技术</h3>
     * 生成条件跳转指令时，其目标地址暂时未知（因为循环体还没解析），
     * 先填入占位符（如 "0"）。等循环体解析完毕、endAddr 确定后，
     * 再回填正确的跳转地址。
     *
     * @return While 对应的语法树节点
     */
    private TreeNode whileStatement() {
        TreeNode node = new TreeNode("While");

        matchValue("while");
        node.addChild(new TreeNode("while"));

        // 记录循环头的四元式地址（用于生成无条件跳回指令）
        int beginAddr = quads.size();

        // 解析条件表达式 ( ... )
        matchValue("(");
        node.addChild(new TreeNode("("));
        NodeValue condLeft = expression();       // 左操作数
        String op = currentToken.getValue();      // 关系运算符
        advance();
        NodeValue condRight = expression();      // 右操作数
        matchValue(")");
        node.addChild(new TreeNode(")"));

        // 生成条件跳转指令（目标地址暂时填 0，等循环体解析完后回填）
        int jumpToEndAddr = quads.size();
        quads.add(new Quadruple("j" + reverseOp(op), condLeft.val, condRight.val, "0"));

        // 解析循环体 { ... }
        matchValue("{");
        node.addChild(new TreeNode("{"));
        node.addChild(statementList());
        matchValue("}");
        node.addChild(new TreeNode("}"));

        // 生成无条件跳回循环头的指令
        quads.add(new Quadruple("j", "_", "_", String.valueOf(beginAddr)));

        // 回填：将条件跳转的目标地址设为循环出口（即 j 指令的下一条）
        quads.get(jumpToEndAddr).result = String.valueOf(quads.size());

        return node;
    }

    // ================================================================
    // 非终结符解析函数：If → if ( Expr relop Expr ) { StmtList }
    //
    // If 语句的中间代码结构：
    //   (条件判断四元式)
    //   (j<反relop>, left, right, ?)  ← 条件为假时跳转，目标待回填
    //   (if-true 体四元式...)
    //   endAddr: ← 回填跳转目标
    // ================================================================

    /**
     * 解析 if 条件语句（If → if ( Expr relop Expr ) { StmtList }）。
     *
     * <h3>中间代码结构</h3>
     * <pre>
     *   条件判断表达式
     *   (j&lt;=反relop&gt;, left, right, ?)    ← 条件为假时跳转到 endAddr
     *   if-true 体四元式...
     *   endAddr:  ← 回填跳转目标
     * </pre>
     *
     * <h3>反向运算符（reverseOp）</h3>
     * 在 if 语句中，生成的是<b>条件为假时跳转</b>的指令。
     * 因此需要将关系运算符取反，例如：
     * <ul>
     *   <li>{@code >} 的反运算符是 {@code <=}</li>
     *   <li>{@code ==} 的反运算符是 {@code !=}</li>
     * </ul>
     *
     * @return If 对应的语法树节点
     */
    private TreeNode ifStatement() {
        TreeNode node = new TreeNode("If");

        matchValue("if");
        node.addChild(new TreeNode("if"));

        // 解析条件表达式 ( ... )
        matchValue("(");
        NodeValue condLeft = expression();
        String op = currentToken.getValue();
        advance();
        NodeValue condRight = expression();
        matchValue(")");

        // 生成条件跳转（条件为假时跳转到 if 语句末尾）
        // 目标地址暂填 0，等 if 体解析完后回填
        int jumpIfFalse = quads.size();
        quads.add(new Quadruple("j" + reverseOp(op), condLeft.val, condRight.val, "0"));

        // 解析 if 体 { ... }
        matchValue("{");
        node.addChild(statementList());
        matchValue("}");

        // 回填：条件为假时的跳转目标 = 当前四元式末尾（即 if 语句之后的第一条指令）
        quads.get(jumpIfFalse).result = String.valueOf(quads.size());

        return node;
    }

    // ================================================================
    // 表达式解析（Expr → Term ExprP）
    //
    // 采用"运算符优先级分析法"的思想，将表达式文法改写为：
    //   Expr  → Term { (+|-) Term }       （消去左递归）
    //   Term  → Factor { (*|/) Factor }
    //   Factor → id | num | ( Expr )
    //
    // 运算符优先级通过"先解析高优先级子表达式"实现：
    //   expression() 调用 term() 先解析 * /
    //   term()       调用 factor() 先解析基本单元
    // ================================================================

    /**
     * 解析加减表达式（Expr → Term { (+|-) Term }）。
     *
     * <h3>运算符优先级</h3>
     * 通过先调用 term() 再处理 + - 的方式，<b>隐式</b>实现了运算符优先级：
     * term() 内部的 * / 会比这里的 + - 先结合。
     *
     * <h3>中间代码生成</h3>
     * 每遇到一个 + 或 - 运算符，生成一条四元式：
     * <pre>
     *   (op, left.val, right.val, newTemp)
     * </pre>
     *
     * @return 表达式的结果值（临时变量名或常量）及其语法子树
     */
    private NodeValue expression() {
        // 先解析高优先级的项（* / 的运算在 term() 内部完成）
        NodeValue left = term();

        // 循环处理 + 和 -（左结合）
        while (currentToken.getValue().equals("+") || currentToken.getValue().equals("-")) {
            String op = currentToken.getValue();
            advance();  // 消耗运算符

            NodeValue right = term();  // 解析右操作数（同样先做 * /）
            String temp = newTemp();   // 分配临时变量存结果

            // 生成四元式：(op, left, right, temp)
            quads.add(new Quadruple(op, left.val, right.val, temp));

            // 构建语法子树
            TreeNode newNode = new TreeNode("Expr(" + op + ")");
            newNode.addChild(left.node);
            newNode.addChild(right.node);

            // 当前结果成为新的 left，继续循环处理后续运算符
            left = new NodeValue(temp, newNode);
        }
        return left;
    }

    /**
     * 解析乘除表达式（Term → Factor { (*|/) Factor }）。
     *
     * <h3>优先级</h3>
     * * 和 / 的优先级高于 + 和 -，这由 expression() 调用 term()
     * 的结构隐式保证。
     *
     * <h3>中间代码生成</h3>
     * 每遇到一个 * 或 / 运算符，生成一条四元式。
     *
     * @return 项的结果值及语法子树
     */
    private NodeValue term() {
        // 先解析基本因子
        NodeValue left = factor();

        // 循环处理 * 和 /（左结合）
        while (currentToken.getValue().equals("*") || currentToken.getValue().equals("/")) {
            String op = currentToken.getValue();
            advance();  // 消耗运算符

            NodeValue right = factor();
            String temp = newTemp();

            // 生成四元式：(op, left, right, temp)
            quads.add(new Quadruple(op, left.val, right.val, temp));

            // 构建语法子树
            TreeNode newNode = new TreeNode("Term(" + op + ")");
            newNode.addChild(left.node);
            newNode.addChild(right.node);

            left = new NodeValue(temp, newNode);
        }
        return left;
    }

    /**
     * 解析基本因子（Factor → id | num | ( Expr )）。
     *
     * <h3>因子的三种情况</h3>
     * <ul>
     *   <li><b>标识符</b> —— 变量引用，返回其变量名</li>
     *   <li><b>整数/浮点数</b> —— 字面常量，返回其字符串表示</li>
     *   <li><b>( Expr )</b> —— 括号表达式，递归解析内部表达式</li>
     * </ul>
     *
     * @return 因子的值（变量名/常量/临时变量）及语法子树
     */
    private NodeValue factor() {
        Token t = currentToken;

        // 情况 1 & 2：标识符或字面常量
        if (t.getType() == TokenType.INTEGER
                || t.getType() == TokenType.FLOAT
                || t.getType() == TokenType.IDENTIFIER) {
            advance();
            return new NodeValue(t.getValue(), new TreeNode(t.getValue()));
        }

        // 情况 3：括号表达式 ( Expr )
        if (t.getValue().equals("(")) {
            advance();               // 消耗 (
            NodeValue res = expression();  // 递归解析内部表达式
            matchValue(")");         // 消耗 )
            return res;
        }

        // 无法匹配任何因子 → 语法错误
        error("无效因子: " + t.getValue());
        return null;
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /**
     * 获取关系运算符的"反向"运算符。
     *
     * <p>用于条件跳转指令的生成。例如对于 if (a &gt; b)：
     * 生成的指令是 "j&lt;= a b target"，即条件为假时跳转。
     * 所以需要将 &gt; 反转成 &lt;=。
     *
     * <p>反转规则：
     * <pre>
     *   &gt;  ↔ &lt;=       &lt;  ↔ &gt;=
     *   &gt;= ↔ &lt;        &lt;= ↔ &gt;
     *   ==  ↔ !=        != ↔ ==
     * </pre>
     *
     * @param op 原始关系运算符（如 "&gt;", "==", "&lt;="）
     * @return 反转后的关系运算符（如 "&lt;=", "!=", "&gt;"）
     */
    private String reverseOp(String op) {
        switch (op) {
            case ">":  return "<=";
            case "<":  return ">=";
            case ">=": return "<";
            case "<=": return ">";
            case "==": return "!=";
            case "!=": return "==";
            default:   return op;
        }
    }

    // ================================================================
    // 内部辅助类：NodeValue
    //
    // 递归下降分析中，每个解析函数需要同时返回两样东西：
    //   1. val  —— 表达式的计算值（变量名、常量或临时变量名），用于生成四元式
    //   2. node —— 对应的语法子树根节点，用于最终展示语法树
    //
    // Java 方法只能返回一个值，因此使用此内部类将两者打包返回。
    // ================================================================

    /**
     * 内部辅助类 —— 打包表达式的"值"和"语法树节点"。
     *
     * <p>编译器中的表达式解析通常需要同时获得：
     * <ul>
     *   <li><b>语义值</b> —— 用于后续代码生成（如变量名 "a"、临时变量 "t1"、常量 "5"）</li>
     *   <li><b>语法树</b> —— 用于可视化和可能的后续分析</li>
     * </ul>
     * 这两个信息通过本内部类一次性返回，避免全局变量或多次遍历。
     */
    private static class NodeValue {
        /** 表达式的计算值（变量名、常量值或临时变量名） */
        String val;

        /** 表达式对应的语法子树根节点 */
        TreeNode node;

        /**
         * 构造一个 NodeValue。
         * @param v 表达式的结果值
         * @param n 对应的语法树节点
         */
        NodeValue(String v, TreeNode n) {
            this.val = v;
            this.node = n;
        }
    }

    // ================================================================
    // 输出接口
    // ================================================================

    /**
     * 打印所有生成的四元式（中间代码）。
     *
     * <p>输出格式：序号: (op, arg1, arg2, result)
     */
    public void printQuads() {
        for (int i = 0; i < quads.size(); i++) {
            System.out.println(i + ": " + quads.get(i));
        }
    }
}
