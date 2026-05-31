package parser;

import java.util.*;

/**
 * LL(1) 语法分析器 —— 实现 FIRST/FOLLOW 集计算与预测分析表构造。
 *
 * <h3>在编译器流程中的位置</h3>
 * <pre>
 *   产生式集合（CFG） → [LL1Analyzer] → FIRST 集合 + FOLLOW 集合 + 预测分析表 M
 * </pre>
 *
 * <h3>LL(1) 文法的理论基础（编译原理教材 §4.3-§4.5）</h3>
 *
 * <p><b>LL(1) 的含义：</b></p>
 * <ul>
 *   <li><b>第一个 L</b> —— Left-to-right scanning of input（从左到右扫描输入）</li>
 *   <li><b>第二个 L</b> —— Leftmost derivation（最左推导）</li>
 *   <li><b>(1)</b> —— One symbol of lookahead（向前看一个输入符号）</li>
 * </ul>
 *
 * <p><b>核心数据结构：</b></p>
 * <dl>
 *   <dt>FIRST(α)</dt>
 *   <dd>从文法符号串 α 出发能推导出的所有<b>终结符首符号</b>的集合。
 *       如果 α 能推导出 ε（空串），则 ε ∈ FIRST(α)。</dd>
 *   <dt>FOLLOW(A)</dt>
 *   <dd>在所有句型中，紧跟在非终结符 A 后面的<b>终结符</b>的集合。
 *       特别地，如果 A 可以出现在句型的最右端，则 # （输入结束符）∈ FOLLOW(A)。</dd>
 *   <dt>预测分析表 M</dt>
 *   <dd>一个二维表 M[A, a]，其中 A 是非终结符，a 是终结符（含 #）。
 *       M[A, a] 存放：当栈顶为 A 且当前输入为 a 时应使用的产生式。
 *       如果 M[A, a] 为空（无产生式），则出现语法错误。</dd>
 * </dl>
 *
 * <h3>算法概述</h3>
 * <ol>
 *   <li>{@link #computeFirst()} —— 不动点迭代法计算 FIRST 集</li>
 *   <li>{@link #computeFollow(String)} —— 不动点迭代法计算 FOLLOW 集</li>
 *   <li>{@link #buildTable()} —— 根据 FIRST/FOLLOW 构造预测分析表</li>
 * </ol>
 *
 * @author 编译原理课程设计
 * @see Production 产生式
 */
public class LL1Analyzer {

    // ================================================================
    // 文法数据
    // ================================================================

    /** 产生式列表 —— 存储本语法的全部产生式 */
    private List<Production> productions = new ArrayList<>();

    /** 终结符集合 —— 小写开头或特殊符号（如 int, id, num, +, ; 等） */
    private Set<String> terminals = new HashSet<>();

    /** 非终结符集合 —— 大写开头的语法变量（如 Program, StmtList, Expr 等） */
    private Set<String> nonTerminals = new HashSet<>();

    // ================================================================
    // LL(1) 核心数据结构
    // ================================================================

    /**
     * FIRST 集合映射 —— 非终结符 → 其 FIRST 集（终结符 + 可能的 ε）。
     * 例如：FIRST(Expr) = {id, num, (}
     */
    public Map<String, Set<String>> firstSets = new HashMap<>();

    /**
     * FOLLOW 集合映射 —— 非终结符 → 其 FOLLOW 集（终结符 + 可能的 #）。
     * 例如：FOLLOW(Expr) = {;, ), #}
     */
    public Map<String, Set<String>> followSets = new HashMap<>();

    /**
     * 预测分析表 M —— 二维映射 [非终结符][终结符] → 产生式。
     * 例如：M["Expr"]["id"] → "Expr → Term ExprP"
     *       M["Expr"]["+"]  → null（无产生式，表示语法错误）
     */
    public Map<String, Map<String, Production>> parsingTable = new HashMap<>();

    // ================================================================
    // 接口：添加产生式
    // ================================================================

    /**
     * 向文法中添加一条产生式。
     *
     * <p>自动识别左部 LHS（第一个参数）和右部 RHS（后续参数），
     * 并更新终结符/非终结符集合。
     *
     * <p>命名约定：
     * <ul>
     *   <li>大写字母开头 → 非终结符（如 "Program", "StmtList", "Expr"）</li>
     *   <li>小写字母/特殊符号 → 终结符（如 "int", "id", "num", "+"）</li>
     *   <li>"ε"（epsilon）→ 空串标记，不是终结符</li>
     * </ul>
     *
     * @param lhs 产生式左部（非终结符名）
     * @param rhs 产生式右部（变长参数，每个元素是一个文法符号）
     */
    public void addProduction(String lhs, String... rhs) {
        productions.add(new Production(lhs, Arrays.asList(rhs)));
        nonTerminals.add(lhs);  // 左部一定是非终结符
        for (String s : rhs) {
            // 空串 ε 和非终结符（大写开头）不加入终结符集合
            if (!s.equals("ε") && !s.matches("[A-Z].*")) {
                terminals.add(s);
            }
        }
    }

    // ================================================================
    // §4.3 FIRST 集合构造算法
    //
    // 算法：不动点迭代（Fixed-Point Iteration）
    //
    // 核心规则：
    //   对于每条产生式 A → X₁X₂...Xₙ：
    //     1. 如果 X₁ 是终结符或 ε → X₁ 加入 FIRST(A)
    //     2. 如果 X₁ 是非终结符 → FIRST(X₁) 全部加入 FIRST(A)
    //
    // 终止条件：当某一轮迭代中没有任何 FIRST 集合发生变化时，算法收敛。
    // ================================================================

    /**
     * 计算所有非终结符的 FIRST 集合（§4.3）。
     *
     * <h3>不动点迭代算法</h3>
     * <ol>
     *   <li>初始化：每个非终结符的 FIRST 集为空</li>
     *   <li>重复遍历所有产生式，对每条产生式 A → X₁X₂...Xₙ：
     *     <ul>
     *       <li>若 X₁ 是终结符或 ε → 将 X₁ 加入 FIRST(A)</li>
     *       <li>若 X₁ 是非终结符 → 将 FIRST(X₁) 全部合并到 FIRST(A)</li>
     *     </ul>
     *   </li>
     *   <li>直到没有新的符号加入任何 FIRST 集为止（不动点）</li>
     * </ol>
     *
     * <p><b>注意：</b>本简化实现假设所有文法符号的 FIRST 集只取决于右部第一个符号。
     * 对于完整的 LL(1) 文法，需要考虑 FIRST 集包含 ε 时的级联传播（即
     * FIRST(A → X₁X₂...) = (FIRST(X₁) - {ε}) ∪ FIRST(X₂) ∪ ...）。
     * 本项目的文法不含这类复杂情况，简化处理即可。
     */
    public void computeFirst() {
        // ① 初始化：每个非终结符对应一个空的 FIRST 集
        for (String nt : nonTerminals) {
            firstSets.put(nt, new HashSet<>());
        }

        // ② 不动点迭代
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Production p : productions) {
                Set<String> firstLhs = firstSets.get(p.lhs);
                int beforeSize = firstLhs.size();

                // 取右部第一个符号
                String firstSymbol = p.rhs.get(0);

                if (firstSymbol.equals("ε") || terminals.contains(firstSymbol)) {
                    // 规则 1：右部首符号是终结符或 ε → 直接加入
                    firstLhs.add(firstSymbol);
                } else {
                    // 规则 2：右部首符号是非终结符 → 合并其 FIRST 集
                    firstLhs.addAll(firstSets.get(firstSymbol));
                }

                // 如果有新的符号加入，标记 changed = true 继续迭代
                if (firstLhs.size() > beforeSize) {
                    changed = true;
                }
            }
        }
    }

    // ================================================================
    // §4.4 FOLLOW 集合构造算法
    //
    // 算法：不动点迭代（Fixed-Point Iteration）
    //
    // 核心规则（教材算法 4.2）：
    //   1. 对于开始符号 S，将 # 加入 FOLLOW(S)
    //   2. 对于每条产生式 A → αBβ：
    //      - 若 β 的第一个符号是终结符 → 加入 FOLLOW(B)
    //      - 若 β 的第一个符号是非终结符 → FIRST(β) - {ε} 加入 FOLLOW(B)
    //   3. 对于每条产生式 A → αB（B 在末尾）：
    //      - FOLLOW(A) 全部加入 FOLLOW(B)
    //   4. 重复 2-3 直到所有 FOLLOW 集不再变化
    // ================================================================

    /**
     * 计算所有非终结符的 FOLLOW 集合（§4.4）。
     *
     * <h3>算法流程</h3>
     * <ol>
     *   <li>初始化 FOLLOW(S) = {#}（S 为开始符号），其余为空</li>
     *   <li>反复扫描所有产生式，对每个非终结符 B：
     *     <ul>
     *       <li>若 B 右部有后继符号 β（即 A → αBβ）：
     *           若 β 是终结符 → 加入 FOLLOW(B)；
     *           若 β 是非终结符 → FIRST(β)-{ε} 加入 FOLLOW(B)</li>
     *       <li>若 B 在右部末尾（即 A → αB）：
     *           FOLLOW(A) 全部加入 FOLLOW(B)</li>
     *     </ul>
     *   </li>
     *   <li>直到所有 FOLLOW 集不再扩大为止</li>
     * </ol>
     *
     * @param startSymbol 文法的开始符号（如 "Program"），其 FOLLOW 集中将加入输入结束符 "#"
     */
    public void computeFollow(String startSymbol) {
        // ① 初始化：每个非终结符对应一个空的 FOLLOW 集
        for (String nt : nonTerminals) {
            followSets.put(nt, new HashSet<>());
        }
        // 规则 1：开始符号的 FOLLOW 集包含输入结束符 #
        followSets.get(startSymbol).add("#");

        // ② 不动点迭代
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Production p : productions) {
                // 遍历产生式右部的每个符号
                for (int i = 0; i < p.rhs.size(); i++) {
                    String symbol = p.rhs.get(i);
                    // 只关心非终结符的 FOLLOW 集
                    if (nonTerminals.contains(symbol)) {
                        Set<String> followSymbol = followSets.get(symbol);
                        int beforeSize = followSymbol.size();

                        // 情况 1：产生式 A → αBβ（B 后面还有符号）
                        if (i + 1 < p.rhs.size()) {
                            String next = p.rhs.get(i + 1);
                            if (terminals.contains(next)) {
                                // 后继是终结符 → 直接加入 FOLLOW(B)
                                followSymbol.add(next);
                            } else {
                                // 后继是非终结符 → FIRST(next) - {ε} 加入 FOLLOW(B)
                                Set<String> nextFirst = new HashSet<>(firstSets.get(next));
                                nextFirst.remove("ε");
                                followSymbol.addAll(nextFirst);
                            }
                        } else {
                            // 情况 2：产生式 A → αB（B 在末尾）
                            //         FOLLOW(A) 全部加入 FOLLOW(B)
                            followSymbol.addAll(followSets.get(p.lhs));
                        }

                        if (followSymbol.size() > beforeSize) {
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    // ================================================================
    // §4.5 预测分析表构造算法
    //
    // 算法（教材算法 4.3）：
    //   对于每条产生式 A → α：
    //     对于每个 a ∈ FIRST(α)：
    //       将 A → α 填入 M[A, a]
    //     如果 ε ∈ FIRST(α)：
    //       对于每个 b ∈ FOLLOW(A)：
    //         将 A → α 填入 M[A, b]
    // ================================================================

    /**
     * 根据已计算好的 FIRST/FOLLOW 集合构造预测分析表 M（§4.5）。
     *
     * <h3>算法流程</h3>
     * <ol>
     *   <li>初始化：为每个非终结符创建一行（空映射）</li>
     *   <li>对每条产生式 A → α：
     *     <ul>
     *       <li>如果 α 的首符号是终结符 a → M[A, a] = A → α</li>
     *       <li>如果 α 的首符号是 ε → 对每个 b ∈ FOLLOW(A)，M[A, b] = A → ε</li>
     *       <li>如果 α 的首符号是非终结符 B → 对每个 c ∈ FIRST(B)，M[A, c] = A → α</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p><b>注意：</b>如果 M[A, a] 已有产生式（即对同一表项填入两条产生式），
     * 则说明该文法不是 LL(1) 文法（存在冲突）。本简化实现会直接覆盖旧值。
     */
    public void buildTable() {
        // ① 初始化：为每个非终结符创建一行
        for (String nt : nonTerminals) {
            parsingTable.put(nt, new HashMap<>());
        }

        // ② 填入产生式
        for (Production p : productions) {
            String firstSymbol = p.rhs.get(0);
            Set<String> lookaheads = new HashSet<>();

            // 确定该产生式的 lookahead 符号集合
            if (firstSymbol.equals("ε")) {
                // ε-产生式 → lookahead = FOLLOW(A)
                lookaheads.addAll(followSets.get(p.lhs));
            } else if (terminals.contains(firstSymbol)) {
                // 首符号是终结符 → lookahead = {firstSymbol}
                lookaheads.add(firstSymbol);
            } else {
                // 首符号是非终结符 → lookahead = FIRST(firstSymbol)
                lookaheads.addAll(firstSets.get(firstSymbol));
            }

            // 将该产生式填入表中对应的每个表项
            for (String terminal : lookaheads) {
                parsingTable.get(p.lhs).put(terminal, p);
            }
        }
    }

    // ================================================================
    // 输出方法（用于控制台展示和调试）
    // ================================================================

    /**
     * 打印 FIRST 集合、FOLLOW 集合和预测分析表（简洁格式）。
     */
    public void printInfo() {
        System.out.println("\n[First 集合]: " + firstSets);
        System.out.println("[Follow 集合]: " + followSets);
        System.out.println("[预测分析表 M]:");
        parsingTable.forEach((nt, map) -> {
            System.out.println("  " + nt + ": " + map);
        });
    }

    /**
     * 以矩阵（表格）形式打印预测分析表 M（§4.5 标准展示格式）。
     *
     * <p>表格的行是非终结符，列是终结符（含 #），每个单元格存放对应产生式。
     *
     * <p>示例输出：
     * <pre>
     * [4.5 预测分析表 M (矩阵展示)]
     *               int               id                ;                 #
     * Program       Program -> int... (空)              (空)              (空)
     * StmtList      (空)              StmtList -> Stmt. (空)              StmtList -> ε
     * </pre>
     */
    public void printTableGrid() {
        System.out.println("\n[4.5 预测分析表 M (矩阵展示)]:");

        // 收集所有终结符（包括 #）
        List<String> terminalList = new ArrayList<>(terminals);
        terminalList.add("#");

        // 打印表头行
        System.out.printf("%-15s", "");  // 左上角空白单元格
        for (String t : terminalList) {
            System.out.printf("%-20s", t);
        }
        System.out.println();

        // 打印每一行（每个非终结符一行）
        for (String nt : nonTerminals) {
            System.out.printf("%-15s", nt);
            Map<String, Production> row = parsingTable.get(nt);
            for (String t : terminalList) {
                Production p = row.get(t);
                // 有产生式则打印产生式，无可用的产生式则留空（表示语法错误）
                System.out.printf("%-20s", (p == null ? "" : p.toString()));
            }
            System.out.println();
        }
    }
}
