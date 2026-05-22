import lexer.Lexer;
import lexer.Token;
import parser.*;
import visualizer.WebVisualizer;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        // 源代码：包含可以被优化的部分，如 5 * 2
        String sourceCode =
                "int main() {\n" +
                        "    int a;\n" +
                        "    int b;\n" +
                        "    a = 5 * 2 + 10;\n" + // 这里可以被优化
                        "    b = a;\n" +
                        "}";

        System.out.println("--- [1] 词法分析 ---");
        Lexer lexer = new Lexer(sourceCode);
        List<Token> tokens = lexer.tokenize();

        System.out.println("\n--- [2] LL(1) 理论计算 ---");
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

        System.out.println("\n--- [3] 语法分析与原始中间代码 ---");
        Parser parser = new Parser(tokens);
        TreeNode root = null;
        try {
            root = parser.parse();
            System.out.println("原始四元式序列:");
            parser.printQuads();

            // ==========================================
            // 新增：[阶段 4] 优化器 (Optimization)
            // ==========================================
            System.out.println("\n--- [4] 优化器 (常量合并与传播) ---");
            List<Quadruple> rawQuads = parser.getQuads();
            List<Quadruple> optimizedQuads = Optimizer.optimize(rawQuads);

            System.out.println("优化后的四元式序列:");
            for (int i = 0; i < optimizedQuads.size(); i++) {
                System.out.println(i + ": " + optimizedQuads.get(i));
            }

            System.out.println("\n--- [5] 生成高级可视化报告 ---");
            // 注意：现在传 5 个参数
            WebVisualizer.generateReport(tokens, analyzer, root, rawQuads, optimizedQuads);

        } catch (Exception e) {
            System.err.println("解析出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}