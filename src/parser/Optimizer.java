package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Optimizer {

    /**
     * 执行优化：常量合并与简单传播
     */
    public static List<Quadruple> optimize(List<Quadruple> inputQuads) {
        List<Quadruple> resultQuads = new ArrayList<>();
        // 记录临时变量与其对应的常数值，如 t1 -> "10"
        Map<String, String> constMap = new HashMap<>();

        for (Quadruple q : inputQuads) {
            String arg1 = q.arg1;
            String arg2 = q.arg2;

            // 1. 替换已经变为常数的操作数 (常量传播)
            if (constMap.containsKey(arg1)) arg1 = constMap.get(arg1);
            if (constMap.containsKey(arg2)) arg2 = constMap.get(arg2);

            // 2. 尝试常量合并
            if (isNumber(arg1) && isNumber(arg2)) {
                String val = calculate(q.op, arg1, arg2);
                if (val != null) {
                    // 如果能算出来（如 5*2），则记录结果并将其转化为赋值语句
                    constMap.put(q.result, val);
                    resultQuads.add(new Quadruple("=", val, "_", q.result));
                    continue;
                }
            }

            // 3. 处理赋值语句的传播: (=, 10, _, t1) -> 记录 t1 为 10
            if (q.op.equals("=") && isNumber(arg1) && q.result.startsWith("t")) {
                constMap.put(q.result, arg1);
            }

            // 保持原样添加（但使用替换后的操作数）
            resultQuads.add(new Quadruple(q.op, arg1, arg2, q.result));
        }

        return cleanUp(resultQuads);
    }

    // 清理那些已经没用的临时变量赋值语句
    private static List<Quadruple> cleanUp(List<Quadruple> quads) {
        // 在这个简单实现中，我们暂时保留所有指令，或者你可以根据需求过滤
        return quads;
    }

    private static boolean isNumber(String s) {
        if (s == null || s.equals("_")) return false;
        return s.matches("-?\\d+(\\.\\d+)?");
    }

    private static String calculate(String op, String a1, String a2) {
        try {
            double v1 = Double.parseDouble(a1);
            double v2 = Double.parseDouble(a2);
            double res = 0;
            switch (op) {
                case "+": res = v1 + v2; break;
                case "-": res = v1 - v2; break;
                case "*": res = v1 * v2; break;
                case "/": if(v2 != 0) res = v1 / v2; else return null; break;
                default: return null;
            }
            // 如果是整数则去掉小数点
            if (res == (long) res) return String.valueOf((long) res);
            return String.valueOf(res);
        } catch (Exception e) {
            return null;
        }
    }
}