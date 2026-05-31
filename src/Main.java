import lexer.Lexer;
import lexer.Token;
import parser.*;
import visualizer.TestResult;
import visualizer.WebVisualizer;
import java.util.ArrayList;
import java.util.List;

/**
 * 编译器主入口类 —— 一口气运行全部 5 个测试用例，生成综合可视化报告。
 *
 * <h3>编译流程（5 个阶段）</h3>
 * <pre>
 *   [1] 词法分析    → 字符序列 → Token 序列
 *   [2] LL(1) 理论  → 文法产生式 → FIRST/FOLLOW 集合 + 预测分析表
 *   [3] 语法分析    → Token 序列 → 语法树 + 原始四元式
 *   [4] 代码优化    → 原始四元式 → 优化后四元式（常量合并+传播）
 *   [5] 可视化报告  → 所有结果  → compiler_report.html（含全部 5 个测试）
 * </pre>
 *
 * @author 编译原理课程设计 —— 重庆交通大学
 */
public class Main {

    public static void main(String[] args) {

        // ========================================================
        // 阶段 0：定义全部 5 个测试用例
        // ========================================================
        String[] testNames = {
                "测试 1：常量合并优化（答辩重点展示）",
                "测试 2：复杂表达式 + 运算符优先级",
                "测试 3：if 条件语句",
                "测试 4：while 循环语句",
                "测试 5：词法错误 & 变量未声明检测"
        };

        String[] sourceCodes = {
                // 测试 1：常量合并 —— 5*2→10, 10+10→20
                "int main() {\n" +
                "    int a;\n" +
                "    int b;\n" +
                "    a = 5 * 2 + 10;\n" +
                "    b = a;\n" +
                "}",

                // 测试 2：运算符优先级 + 括号
                "int main() {\n" +
                "    int x;\n" +
                "    int y;\n" +
                "    x = 1 + 2 * 3;\n" +
                "    y = (1 + 2) * 3;\n" +
                "}",

                // 测试 3：if 条件语句
                "int main() {\n" +
                "    int a;\n" +
                "    a = 10;\n" +
                "    if (a > 5) {\n" +
                "        a = a - 1;\n" +
                "    }\n" +
                "}",

                // 测试 4：while 循环语句
                "int main() {\n" +
                "    int i;\n" +
                "    i = 3;\n" +
                "    while (i > 0) {\n" +
                "        i = i - 1;\n" +
                "    }\n" +
                "}",

                // 测试 5：词法错误 + 语义错误
                "int main() {\n" +
                "    int a;\n" +
                "    int b;\n" +
                "    a = 10 @ 5;\n" +
                "    c = a + b;\n" +
                "}"
        };

        // ========================================================
        // 阶段 2：LL(1) 理论计算（只算一次，所有测试共用同一文法）
        // ========================================================
        System.out.println("============================================");
        System.out.println("  编译器全流程 —— 5 个测试用例批量运行");
        System.out.println("============================================");

        System.out.println("\n--- [2] LL(1) 理论计算（所有测试共用文法） ---");
        LL1Analyzer analyzer = new LL1Analyzer();

        analyzer.addProduction("Program", "int", "main", "(", ")", "{", "StmtList", "}");
        analyzer.addProduction("StmtList", "Stmt", "StmtList");
        analyzer.addProduction("StmtList", "ε");
        analyzer.addProduction("Stmt", "Decl");
        analyzer.addProduction("Stmt", "Assign");
        analyzer.addProduction("Decl", "int", "id", ";");
        analyzer.addProduction("Assign", "id", "=", "Expr", ";");
        analyzer.addProduction("Expr", "Term", "ExprP");
        analyzer.addProduction("ExprP", "+", "Term", "ExprP");
        analyzer.addProduction("ExprP", "ε");
        analyzer.addProduction("Term", "id");
        analyzer.addProduction("Term", "num");

        analyzer.computeFirst();
        analyzer.computeFollow("Program");
        analyzer.buildTable();
        analyzer.printTableGrid();

        // ========================================================
        // 阶段 1 + 3 + 4：逐个运行每个测试用例
        // ========================================================
        List<TestResult> allResults = new ArrayList<>();

        for (int t = 0; t < testNames.length; t++) {
            System.out.println("\n============================================");
            System.out.println("  " + testNames[t]);
            System.out.println("============================================");
            System.out.println("\n源代码:");
            System.out.println(sourceCodes[t]);

            // ----- 词法分析 -----
            System.out.println("\n--- [1] 词法分析 ---");
            Lexer lexer = new Lexer(sourceCodes[t]);
            List<Token> tokens = lexer.tokenize();
            System.out.println("共识别 " + tokens.size() + " 个 Token");

            // ----- 收集词法错误 -----
            List<String> lexErrors = lexer.getErrors();
            if (!lexErrors.isEmpty()) {
                for (String err : lexErrors) {
                    System.err.println("  " + err);
                }
            }

            // ----- 语法分析 + 中间代码 -----
            System.out.println("\n--- [3] 语法分析 + 中间代码 ---");
            Parser parser = new Parser(tokens);
            TreeNode root = null;
            List<Quadruple> rawQuads = new ArrayList<>();
            List<Quadruple> optQuads = new ArrayList<>();
            String errorMsg = null;

            try {
                root = parser.parse();

                System.out.println("语法树:");
                System.out.println(root.label);
                for (TreeNode child : root.children) {
                    child.print("    ", false);
                }

                rawQuads = parser.getQuads();
                System.out.println("\n原始四元式 (" + rawQuads.size() + " 条):");
                for (int i = 0; i < rawQuads.size(); i++) {
                    System.out.println("  " + i + ": " + rawQuads.get(i));
                }

                // ----- 优化 -----
                System.out.println("\n--- [4] 优化器 ---");
                optQuads = Optimizer.optimize(rawQuads);
                System.out.println("优化后四元式 (" + optQuads.size() + " 条):");
                for (int i = 0; i < optQuads.size(); i++) {
                    System.out.println("  " + i + ": " + optQuads.get(i));
                }

                // 优化统计
                int rawArith = 0, optArith = 0;
                for (Quadruple q : rawQuads) {
                    if ("+-*/".contains(q.op)) rawArith++;
                }
                for (Quadruple q : optQuads) {
                    if ("+-*/".contains(q.op)) optArith++;
                }
                if (rawArith > 0 || optArith > 0) {
                    System.out.println("  → 运行时算术运算: " + rawArith + " → " + optArith
                            + "（减少 " + (rawArith - optArith) + " 条）");
                }

            } catch (Exception e) {
                errorMsg = e.getMessage();
                System.err.println("  ✗ 解析出错: " + errorMsg);
            }

            // 收集本测试的结果
            allResults.add(new TestResult(
                    testNames[t], sourceCodes[t],
                    tokens, lexErrors,
                    root, rawQuads, optQuads, errorMsg
            ));
        }

        // ========================================================
        // 阶段 5：生成综合可视化报告（包含所有 5 个测试）
        // ========================================================
        System.out.println("\n============================================");
        System.out.println("--- [5] 生成综合可视化报告 ---");
        WebVisualizer.generateMultiReport(analyzer, allResults);
        System.out.println("============================================");
    }
}
