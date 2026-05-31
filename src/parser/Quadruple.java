package parser;

/**
 * 四元式（Quadruple）类 —— 中间代码的基本单元。
 *
 * <h3>在编译器流程中的位置</h3>
 * <pre>
 *   语法树 → [语义分析/中间代码生成] → 四元式序列 → [优化器] → [目标代码生成]
 * </pre>
 * 四元式是编译器前端和后端之间的桥梁，是一种广泛使用的中间表示（IR）形式。
 *
 * <h3>四元式的定义（编译原理教材 §7.2）</h3>
 * 一个四元式由四个部分组成：<b>(运算符, 操作数1, 操作数2, 结果)</b>
 *
 * <p>各字段的含义：
 * <ul>
 *   <li><b>op（运算符）</b> —— 如 +, -, *, /, =, j（无条件跳转）, j&lt;=（条件跳转）等</li>
 *   <li><b>arg1（第一操作数）</b> —— 常量值、变量名或临时变量名（t1, t2, ...）</li>
 *   <li><b>arg2（第二操作数）</b> —— 同上；对于单目操作，用 "_" 表示不使用</li>
 *   <li><b>result（结果）</b> —— 存放结果的变量名或临时变量名；对于跳转指令，存放目标地址</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>
 *   源代码: a = 5 + 3 * 2
 *   四元式:
 *     ( * , 3 , 2 , t1 )    // t1 = 3 * 2
 *     ( + , 5 , t1, t2 )    // t2 = 5 + t1
 *     ( = , t2, _ , a )     // a = t2
 * </pre>
 *
 * <h3>设计说明</h3>
 * 字段使用 public 而非 private + getter/setter，是为了方便 {@link Optimizer}
 * 中直接修改四元式的操作数（常量传播时替换 arg1/arg2）。在完整的编译器工程中，
 * 建议使用 getter/setter 以封装内部表示。
 *
 * @author 编译原理课程设计
 * @see Parser    语法分析器（四元式的主要生产者）
 * @see Optimizer 优化器（四元式的消费者兼生产者）
 */
public class Quadruple {

    /** 运算符 —— 如 +, -, *, /, =, j（跳转）, j&lt;= 等 */
    public String op;

    /** 第一操作数 —— 常量值、变量名或临时变量；不使用时为 "_" */
    public String arg1;

    /** 第二操作数 —— 常量值、变量名或临时变量；不使用时为 "_" */
    public String arg2;

    /** 结果 —— 存放结果的变量/临时变量名；对于跳转指令则存放跳转目标地址（四元式序号） */
    public String result;

    /**
     * 构造一个四元式。
     *
     * @param op     运算符
     * @param arg1   第一操作数
     * @param arg2   第二操作数（单目操作时用 "_" 表示空）
     * @param result 结果变量名或跳转目标地址
     */
    public Quadruple(String op, String arg1, String arg2, String result) {
        this.op = op;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.result = result;
    }

    /**
     * 返回四元式的字符串表示，格式为：{@code (op  , arg1, arg2, result)}
     *
     * <p>示例输出：
     * <pre>
     *   (*   , 5   , 2   , t1  )
     *   (+   , t1  , 10  , t2  )
     *   (=   , t2  , _   , a   )
     * </pre>
     *
     * @return 格式化的四元式字符串
     */
    @Override
    public String toString() {
        return String.format("(%-4s, %-4s, %-4s, %-4s)", op, arg1, arg2, result);
    }
}
