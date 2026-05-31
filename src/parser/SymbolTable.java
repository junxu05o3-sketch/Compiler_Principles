package parser;

import java.util.HashSet;
import java.util.Set;

/**
 * 简易符号表（Symbol Table）—— 管理程序中声明的变量名。
 *
 * <h3>在编译器流程中的位置</h3>
 * 符号表是编译器的核心数据结构之一，贯穿词法分析之后的几乎所有阶段。
 * <pre>
 *   声明语句解析 → 符号表.add(变量名)        （填入符号）
 *   表达式解析   → 符号表.contains(变量名)   （查符号——语义检查）
 * </pre>
 *
 * <h3>符号表的作用（编译原理教材 §6.2）</h3>
 * <ol>
 *   <li><b>收集标识符信息</b> —— 记录程序中出现的所有变量名、函数名</li>
 *   <li><b>语义检查</b> —— 验证变量是否"先声明后使用"。如果使用的变量
 *       不在符号表中，则报"变量未声明"错误</li>
 *   <li><b>存储属性信息</b> —— 在实际编译器中，符号表还存储类型、作用域、
 *       存储分配地址等。本实现中仅存储变量名，保持简洁</li>
 * </ol>
 *
 * <h3>实现方式</h3>
 * 使用 HashSet 存储变量名，提供 O(1) 的插入和查询效率。
 * 高级编译器中的符号表通常用哈希表 + 栈式作用域链实现（支持嵌套作用域）。
 *
 * <h3>本项目中符号表的使用位置</h3>
 * <ul>
 *   <li>{@link Parser#declaration()} —— 解析变量声明时，将变量名加入符号表</li>
 *   <li>{@link Parser#assignment()} —— 解析赋值语句时，检查左值变量是否已声明</li>
 * </ul>
 *
 * @author 编译原理课程设计
 * @see Parser 语法分析器（符号表的主要使用者）
 */
public class SymbolTable {

    /** 变量名集合 —— 使用 HashSet 实现 O(1) 插入与查询 */
    private Set<String> variables = new HashSet<>();

    /**
     * 向符号表中添加一个新变量名（对应声明语句的处理）。
     *
     * @param name 变量名（标识符字符串）
     */
    public void add(String name) {
        variables.add(name);
    }

    /**
     * 检查某变量名是否已在符号表中（即是否已声明）。
     *
     * <p>用于语义分析中的"先声明后使用"检查。
     * 如果返回 false，说明在使用一个未声明的变量，应报错。
     *
     * @param name 待检查的变量名
     * @return true 如果该变量已经声明过
     */
    public boolean contains(String name) {
        return variables.contains(name);
    }
}
