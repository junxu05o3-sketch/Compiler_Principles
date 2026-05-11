import lexer.Lexer;
import lexer.Token;
import lexer.TokenType;

import java.util.List;

/**
 * 词法分析器入口类（Main）
 *
 * 包含一段示例源程序，调用 Lexer 进行分析并打印结果。
 */
public class Main {

    public static void main(String[] args) {

        // ============================================================
        // 示例源程序（模拟一段 C 风格代码）
        // ============================================================
        String sourceCode =
                "// 计算阶乘的函数\n" +
                "int factorial(int n) {\n" +
                "    if (n <= 1) {\n" +
                "        return 1;\n" +
                "    } else {\n" +
                "        return n * factorial(n - 1);\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "/* 主函数 */\n" +
                "int main() {\n" +
                "    int result = factorial(5);\n" +
                "    float pi = 3.14;\n" +
                "    char grade = 'A';\n" +
                "    bool flag = true;\n" +
                "    // 输出结果\n" +
                "    int x = 10;\n" +
                "    int y = x + 2;\n" +
                "    if (x >= y || flag != false) {\n" +
                "        x++;\n" +
                "    }\n" +
                "    return 0;\n" +
                "}\n";

        // ============================================================
        // 执行词法分析
        // ============================================================
        System.out.println("========================================");
        System.out.println("         词法分析器 输出结果             ");
        System.out.println("========================================");
        System.out.println();

        Lexer lexer = new Lexer(sourceCode);
        List<Token> tokens = lexer.tokenize();

        // ============================================================
        // 打印所有 Token（二元式）
        // ============================================================
        int index = 1;
        for (Token token : tokens) {
            // EOF 单独输出
            if (token.getType() == TokenType.EOF) {
                System.out.println("----------------------------------------");
                System.out.printf("[%3d] %s%n", index++, token);
                break;
            }
            System.out.printf("[%3d] %s%n", index++, token);
        }

        // ============================================================
        // 打印统计信息
        // ============================================================
        System.out.println();
        System.out.println("========================================");
        System.out.println("              统计信息                  ");
        System.out.println("========================================");
        printStatistics(tokens);

        // ============================================================
        // 打印词法错误（如有）
        // ============================================================
        List<String> errors = lexer.getErrors();
        if (!errors.isEmpty()) {
            System.out.println();
            System.out.println("========================================");
            System.out.println("              词法错误                  ");
            System.out.println("========================================");
            errors.forEach(System.out::println);
        } else {
            System.out.println();
            System.out.println("✓ 词法分析完成，无错误。");
        }
    }

    /**
     * 统计各类 Token 数量并打印
     */
    private static void printStatistics(List<Token> tokens) {
        int[] counts = new int[TokenType.values().length];
        for (Token t : tokens) {
            counts[t.getType().ordinal()]++;
        }
        for (TokenType type : TokenType.values()) {
            int cnt = counts[type.ordinal()];
            if (cnt > 0) {
                System.out.printf("  %-12s : %d 个%n", type, cnt);
            }
        }
        System.out.printf("  %-12s : %d 个%n", "合计(含EOF)", tokens.size());
    }
}
