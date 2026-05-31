package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 中间代码优化器 —— 对四元式序列进行机器无关的优化。
 *
 * <h3>在编译器流程中的位置</h3>
 * <pre>
 *   原始四元式序列 → [优化器 Optimizer] → 优化后的四元式序列 → [目标代码生成]
 * </pre>
 * 优化器位于中间代码生成之后、目标代码生成之前，是编译器中<b>可选的但极其重要</b>的阶段。
 *
 * <h3>本优化器实现的两种经典优化技术</h3>
 *
 * <p><b>1. 常量合并（Constant Folding）—— 编译原理教材 §9.1</b></p>
 * <p>在编译时计算常量表达式的值，而非在运行时计算。</p>
 * <pre>
 *   优化前: t1 = 5 * 2      → 两条指令
 *          t2 = t1 + 10
 *   优化后: t1 = 10         → 一条指令（5*2 在编译时算出）
 *          t2 = 20           → 一条指令（10+10 在编译时算出）
 * </pre>
 *
 * <p><b>2. 常量传播（Constant Propagation）—— 编译原理教材 §9.2</b></p>
 * <p>如果一个变量在某个程序点是常数值，则将该值替换到使用处。</p>
 * <pre>
 *   优化前: t1 = 10
 *          t2 = t1 + 5       → t1 的值已知为 10
 *   优化后: t1 = 10
 *          t2 = 10 + 5       → t1 被替换为其常量值 10
 *          (进一步触发常量合并) t2 = 15
 * </pre>
 *
 * <h3>算法设计</h3>
 * <ol>
 *   <li>遍历四元式序列，维护一个常量映射表（constMap）：临时变量 → 常量值</li>
 *   <li>对每条四元式，首先尝试用常量表中的值替换操作数（常量传播）</li>
 *   <li>如果两个操作数都是常数值，直接在编译时计算（常量合并）</li>
 *   <li>如果是常量赋值语句，记录到常量映射表中供后续传播使用</li>
 * </ol>
 *
 * <h3>局限性（本简化实现的约束）</h3>
 * <ul>
 *   <li>仅处理基本块内的常量传播（不跨基本块、不跨控制流）</li>
 *   <li>仅对临时变量（t 开头）做传播，不对用户变量做传播</li>
 *   <li>不执行死代码消除、公共子表达式消除等更高级的优化</li>
 *   <li>cleanUp 方法为预留接口，当前仅返回原序列</li>
 * </ul>
 *
 * @author 编译原理课程设计
 * @see Quadruple 四元式
 * @see Parser    语法分析器（四元式的生产者）
 */
public class Optimizer {

    /**
     * 【核心接口】对四元式序列执行常量合并与常量传播优化。
     *
     * <h3>算法流程（单遍扫描）</h3>
     * <ol>
     *   <li><b>常量传播</b>：检查 arg1/arg2 是否在 constMap 中，若在则替换为常量值</li>
     *   <li><b>常量合并</b>：若 arg1 和 arg2 都是数值常量，编译时计算 op(arg1, arg2)
     *       <ul>
     *         <li>能算出 → 将结果记入 constMap，将该指令替换为赋值指令 (=, 结果, _, result)</li>
     *         <li>不能算（如除 0）→ 保持原样</li>
     *       </ul>
     *   </li>
     *   <li><b>常量赋值记录</b>：若指令为 (=, 常量, _, 临时变量)，将 临时变量→常量 记入 constMap</li>
     *   <li><b>保留原指令</b>：不需要合并的指令保持原样（但操作数可能已被替换）</li>
     * </ol>
     *
     * <h3>示例：优化 5 * 2 + 10</h3>
     * <pre>
     *   输入四元式:                    输出四元式:
     *   0: (*, 5, 2, t1)              0: (=, 10, _, t1)      ← 5*2 合并为 10
     *   1: (+, t1, 10, t2)            1: (+, 10, 10, t2)    ← t1 被传播为 10
     *   2: (=, t2, _, a)              2: (=, 20, _, a)      ← 10+10 合并为 20
     * </pre>
     *
     * @param inputQuads 原始四元式序列（由语法分析器生成）
     * @return 优化后的四元式序列
     */
    public static List<Quadruple> optimize(List<Quadruple> inputQuads) {
        List<Quadruple> resultQuads = new ArrayList<>();

        // 常量映射表：临时变量名 → 常量值
        // 例如：{"t1" → "10", "t2" → "5"} 表示 t1 已知为常量 10，t2 已知为常量 5
        Map<String, String> constMap = new HashMap<>();

        for (Quadruple q : inputQuads) {
            // 从原四元式中取出操作数
            String arg1 = q.arg1;
            String arg2 = q.arg2;

            // ==========================================================
            // 第 1 步：常量传播
            // 如果 arg1 或 arg2 对应已知的常量值，则替换为常量值
            // 例如：constMap = {"t1" → "10"}，遇到 (+, t1, 5, t2)
            //       → arg1 从 "t1" 替换为 "10"，变成 (+, 10, 5, t2)
            // ==========================================================
            if (constMap.containsKey(arg1)) {
                arg1 = constMap.get(arg1);
            }
            if (constMap.containsKey(arg2)) {
                arg2 = constMap.get(arg2);
            }

            // ==========================================================
            // 第 2 步：常量合并
            // 如果两个操作数都是数值常量，在编译时直接计算
            // 例如：(+, 10, 5, t2) → 编译时算出 10+5=15
            //       → 生成 (=, 15, _, t2) 并记录 {"t2" → "15"}
            // ==========================================================
            if (isNumber(arg1) && isNumber(arg2)) {
                String val = calculate(q.op, arg1, arg2);
                if (val != null) {
                    // 常量合并成功：
                    //   - 将结果记入 constMap（供后续传播使用）
                    //   - 将原运算指令替换为赋值指令
                    constMap.put(q.result, val);
                    resultQuads.add(new Quadruple("=", val, "_", q.result));
                    continue;  // 跳过后续处理（已替换为赋值指令）
                }
                // 合并失败（如除零）→ 保留原指令继续处理
            }

            // ==========================================================
            // 第 3 步：常量赋值记录
            // 如果指令是 (=, 常量, _, 临时变量)，
            // 将该临时变量加入常量表中供后续传播
            // 例如：(=, 10, _, t1) → constMap 记录 {"t1" → "10"}
            // ==========================================================
            if (q.op.equals("=") && isNumber(arg1) && q.result.startsWith("t")) {
                constMap.put(q.result, arg1);
            }

            // ==========================================================
            // 第 4 步：保留原指令
            // 不需要合并的指令（如涉及非临时变量、涉及未知值的操作数等）
            // 保持原样添加到输出队列（但操作数已经过常量传播替换）
            // ==========================================================
            resultQuads.add(new Quadruple(q.op, arg1, arg2, q.result));
        }

        // 清理阶段（当前为空操作，预留扩展接口）
        return cleanUp(resultQuads);
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /**
     * 清理优化后的四元式序列（预留扩展接口）。
     *
     * <p>可以在此实现更高级的清理优化，例如：
     * <ul>
     *   <li>死代码消除 —— 删除计算后从未被使用的临时变量赋值</li>
     *   <li>冗余赋值消除 —— 删除对同一变量的连续赋值中前面的赋值</li>
     *   <li>无用临时变量消除 —— 如果临时变量只被使用一次，将其内联</li>
     * </ul>
     *
     * <p>当前为简化实现，直接返回原始序列不做修改。
     *
     * @param quads 待清理的四元式序列
     * @return 清理后的四元式序列（当前直接返回原序列）
     */
    private static List<Quadruple> cleanUp(List<Quadruple> quads) {
        // TODO: 可在此实现死代码消除等高级优化
        // 例如：如果某个 t1 只被定义但从未被使用（不在任何 arg1/arg2 中出现），
        //       则可以安全删除定义 t1 的指令。
        return quads;
    }

    /**
     * 判断字符串是否表示一个数值常量。
     *
     * <p>识别格式：
     * <ul>
     *   <li>整数：如 "0", "42", "-7"</li>
     *   <li>浮点数：如 "3.14", "0.5", "1.0"</li>
     * </ul>
     *
     * <p>排除的值：
     * <ul>
     *   <li>null —— 空引用</li>
     *   <li>"_" —— 操作数占位符（表示不使用的操作数）</li>
     * </ul>
     *
     * @param s 待检查的字符串
     * @return true 如果 s 是数值常量
     */
    private static boolean isNumber(String s) {
        if (s == null || s.equals("_")) {
            return false;
        }
        // 匹配整数（可含前导负号）或浮点数
        return s.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * 在编译时计算两个常量操作数的运算结果。
     *
     * <p>支持的运算符：+ - * /
     *
     * <p>特殊处理：
     * <ul>
     *   <li>除数为 0 → 返回 null（避免除零错误，保留原指令由运行时处理）</li>
     *   <li>结果为整数（如 10.0）→ 去除小数点，返回 "10"</li>
     *   <li>其他异常 → 返回 null（保留原指令）</li>
     * </ul>
     *
     * @param op 运算符（+ - * /）
     * @param a1 左操作数字符串表示
     * @param a2 右操作数字符串表示
     * @return 计算结果字符串，若无法计算则返回 null
     */
    private static String calculate(String op, String a1, String a2) {
        try {
            double v1 = Double.parseDouble(a1);
            double v2 = Double.parseDouble(a2);
            double res = 0;

            switch (op) {
                case "+": res = v1 + v2; break;
                case "-": res = v1 - v2; break;
                case "*": res = v1 * v2; break;
                case "/":
                    if (v2 != 0) {
                        res = v1 / v2;
                    } else {
                        return null;  // 除零错误 → 不优化，保留原指令
                    }
                    break;
                default:
                    return null;  // 不支持的运算符
            }

            // 格式化结果：整数去掉小数点（"10.0" → "10"）
            if (res == (long) res) {
                return String.valueOf((long) res);
            }
            return String.valueOf(res);
        } catch (Exception e) {
            // 任何异常（如字符串无法解析为数字）→ 无法优化，保留原指令
            return null;
        }
    }
}
