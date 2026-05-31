package visualizer;

import lexer.Token;
import parser.Quadruple;
import parser.TreeNode;
import java.util.List;

/**
 * 单个测试用例的编译全流程结果 —— 用于将多个测试用例汇总到同一个可视化报告中。
 *
 * <p>每个 TestResult 包含一个完整测试用例的：名称、源代码、Token 序列、词法错误、
 * 语法树根节点、原始四元式、优化后四元式、以及可能的语法/语义错误信息。
 */
public class TestResult {

    /** 测试用例名称（如"测试 1：常量合并优化"） */
    public final String name;

    /** 测试用例的源代码文本 */
    public final String sourceCode;

    /** 词法分析产出的 Token 序列 */
    public final List<Token> tokens;

    /** 词法错误列表（非法字符等） */
    public final List<String> lexErrors;

    /** 语法树的根节点（如果解析失败则为 null） */
    public final TreeNode astRoot;

    /** 优化前的原始四元式序列 */
    public final List<Quadruple> rawQuads;

    /** 优化后的四元式序列 */
    public final List<Quadruple> optQuads;

    /** 语法/语义错误信息（如果解析成功则为 null） */
    public final String errorMsg;

    /**
     * 构造一个测试用例结果。
     */
    public TestResult(String name, String sourceCode,
                      List<Token> tokens, List<String> lexErrors,
                      TreeNode astRoot,
                      List<Quadruple> rawQuads, List<Quadruple> optQuads,
                      String errorMsg) {
        this.name = name;
        this.sourceCode = sourceCode;
        this.tokens = tokens;
        this.lexErrors = lexErrors;
        this.astRoot = astRoot;
        this.rawQuads = rawQuads;
        this.optQuads = optQuads;
        this.errorMsg = errorMsg;
    }

    /** 该测试是否完全成功（无词法错误、无语法/语义错误） */
    public boolean isSuccess() {
        return errorMsg == null && lexErrors.isEmpty();
    }
}
