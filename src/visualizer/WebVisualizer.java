package visualizer;

import lexer.Token;
import parser.LL1Analyzer;
import parser.Production;
import parser.Quadruple;
import parser.TreeNode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 可视化报告生成器 —— 将编译全流程数据渲染为交互式 HTML 网页报告。
 *
 * <h3>两种模式</h3>
 * <ul>
 *   <li>{@link #generateReport} —— 传统单测试模式（向后兼容）</li>
 *   <li>{@link #generateMultiReport} —— 多测试批量模式：一个 HTML 包含全部测试用例</li>
 * </ul>
 *
 * @author 编译原理课程设计
 */
public class WebVisualizer {

    // ================================================================
    // 非终结符中英对照表（仅影响 HTML 显示，不改动底层逻辑）
    // ================================================================
    private static final Map<String, String> NT_CN = new LinkedHashMap<>();
    static {
        NT_CN.put("Program",  "程序");
        NT_CN.put("StmtList", "语句列表");
        NT_CN.put("Stmt",     "语句");
        NT_CN.put("Decl",     "声明语句");
        NT_CN.put("Assign",   "赋值语句");
        NT_CN.put("If",       "条件语句");
        NT_CN.put("While",    "循环语句");
        NT_CN.put("Expr",     "表达式");
        NT_CN.put("ExprP",    "表达式'");
        NT_CN.put("Term",     "项");
        NT_CN.put("TermP",    "项'");
        NT_CN.put("Factor",   "因子");
    }

    /** 获取非终结符的中文显示名（找不到则返回原名） */
    private static String ntName(String en) {
        return NT_CN.getOrDefault(en, en);
    }

    /** 将产生式中的非终结符替换为中文名 */
    private static String toChineseProduction(Production p) {
        String cnLhs = ntName(p.lhs);
        StringBuilder rhs = new StringBuilder();
        for (int i = 0; i < p.rhs.size(); i++) {
            if (i > 0) rhs.append(" ");
            rhs.append(ntName(p.rhs.get(i)));
        }
        return cnLhs + " → " + rhs.toString();
    }

    // ================================================================
    // 多测试批量模式（答辩用）
    // ================================================================

    /**
     * 生成包含多个测试用例的综合可视化报告。
     *
     * @param analyzer  LL(1) 分析器（所有测试共用同一文法）
     * @param allResults 所有测试用例的编译结果列表
     */
    public static void generateMultiReport(LL1Analyzer analyzer, List<TestResult> allResults) {
        StringBuilder html = new StringBuilder();

        // ---- HTML 头部 ----
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>编译器全流程可视化报告</title>");

        // ---- CSS ----
        html.append("<style>");
        html.append(":root { --primary: #1890ff; --success: #52c41a; --error: #f5222d; --bg: #f0f2f5; }");
        html.append("* { box-sizing: border-box; margin: 0; padding: 0; }");
        html.append("body { font-family: 'Segoe UI', system-ui, 'Microsoft YaHei', sans-serif; background: var(--bg); }");
        html.append("header { background: linear-gradient(135deg, #141E30, #243B55); color: white; padding: 18px 40px; display: flex; justify-content: space-between; align-items: center; }");
        html.append("header h1 { font-size: 20px; }");
        html.append(".summary-bar { display: flex; gap: 20px; font-size: 13px; color: #ccc; }");
        html.append(".summary-bar span { background: rgba(255,255,255,0.1); padding: 6px 14px; border-radius: 20px; }");
        html.append(".summary-bar .ok { color: var(--success); }");
        html.append(".summary-bar .fail { color: var(--error); }");

        // 顶层导航
        html.append(".top-nav { display: flex; background: #fff; padding: 0 40px; border-bottom: 2px solid #e8e8e8; overflow-x: auto; }");
        html.append(".top-nav-item { padding: 12px 20px; cursor: pointer; color: #595959; border-bottom: 2px solid transparent; white-space: nowrap; transition: 0.2s; font-size: 14px; }");
        html.append(".top-nav-item:hover { color: var(--primary); }");
        html.append(".top-nav-item.active { color: var(--primary); border-bottom-color: var(--primary); font-weight: bold; }");

        // 内容区
        html.append(".content { padding: 24px 40px; max-width: 1400px; margin: 0 auto; }");
        html.append(".top-pane { display: none; }");
        html.append(".top-pane.active { display: block; }");

        // 卡片
        html.append(".card { background: white; padding: 20px 24px; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); margin-bottom: 20px; }");
        html.append(".card h2 { font-size: 16px; color: #262626; margin-bottom: 14px; padding-bottom: 10px; border-bottom: 1px solid #f0f0f0; }");
        html.append(".card h3 { font-size: 14px; color: #595959; margin: 16px 0 8px; }");

        // 源代码
        html.append(".source-code { background: #1e1e1e; color: #d4d4d4; padding: 16px; border-radius: 6px; font-family: 'Cascadia Code','Fira Code',monospace; font-size: 13px; line-height: 1.7; overflow-x: auto; white-space: pre; }");

        // 表格
        html.append("table { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 13px; }");
        html.append("th, td { border: 1px solid #f0f0f0; padding: 8px 10px; text-align: center; }");
        html.append("th { background: #fafafa; font-weight: 600; color: #595959; }");
        html.append("tr:hover { background: #fafafa; }");

        // diff 对比
        html.append(".diff-container { display: flex; gap: 20px; margin-top: 12px; }");
        html.append(".diff-box { flex: 1; border: 1px solid #e8e8e8; border-radius: 6px; overflow: hidden; }");
        html.append(".diff-header { background: #fafafa; padding: 8px 14px; border-bottom: 1px solid #e8e8e8; font-weight: bold; font-size: 13px; text-align: center; }");
        html.append(".diff-arrow { display: flex; align-items: center; font-size: 24px; color: var(--primary); }");

        // 优化统计徽章
        html.append(".opt-stat { display: inline-block; background: #f6ffed; color: var(--success); border: 1px solid #b7eb8f; padding: 4px 12px; border-radius: 4px; font-size: 13px; margin: 8px 0; }");

        // 语法树
        html.append(".tree { padding: 16px; overflow-x: auto; }");
        html.append(".tree ul { display: flex; justify-content: center; padding-top: 16px; position: relative; }");
        html.append(".tree li { list-style-type: none; position: relative; padding: 16px 4px 0 4px; text-align: center; }");
        html.append(".tree li::before, .tree li::after { content: ''; position: absolute; top: 0; right: 50%; border-top: 2px solid #d9d9d9; width: 50%; height: 16px; }");
        html.append(".tree li::after { right: auto; left: 50%; border-left: 2px solid #d9d9d9; }");
        html.append(".tree li:only-child::after, .tree li:only-child::before { display: none; }");
        html.append(".tree li:only-child { padding-top: 0; }");
        html.append(".tree li span { border: 2px solid var(--primary); padding: 3px 8px; border-radius: 4px; display: inline-block; background: #fff; font-size: 11px; white-space: nowrap; }");
        html.append(".tree li span.leaf { border-color: #d9d9d9; color: #8c8c8c; }");

        // 错误信息
        html.append(".error-box { background: #fff2f0; border: 1px solid #ffccc7; border-radius: 6px; padding: 14px; margin: 12px 0; color: var(--error); font-size: 13px; }");
        html.append(".success-badge { display: inline-block; background: #f6ffed; color: var(--success); border: 1px solid #b7eb8f; padding: 3px 10px; border-radius: 4px; font-size: 12px; }");
        html.append(".error-badge { display: inline-block; background: #fff2f0; color: var(--error); border: 1px solid #ffccc7; padding: 3px 10px; border-radius: 4px; font-size: 12px; }");

        html.append("</style></head><body>");

        // ---- 头部 + 统计摘要 ----
        int successCount = 0, failCount = 0;
        for (TestResult r : allResults) {
            if (r.isSuccess()) successCount++; else failCount++;
        }
        html.append("<header>");
        html.append("<h1>🖥 编译器全流程可视化报告 —— 重庆交通大学</h1>");
        html.append("<div class='summary-bar'>");
        html.append("<span>共 " + allResults.size() + " 个测试</span>");
        html.append("<span class='ok'>✓ " + successCount + " 通过</span>");
        if (failCount > 0) html.append("<span class='fail'>✗ " + failCount + " 失败</span>");
        html.append("</div></header>");

        // ---- 顶层导航：LL(1) 理论 + 各测试用例 ----
        html.append("<div class='top-nav'>");
        html.append("<div class='top-nav-item active' onclick='switchTopPane(0)'>📐 LL(1) 理论推导</div>");
        for (int i = 0; i < allResults.size(); i++) {
            TestResult r = allResults.get(i);
            String icon = r.isSuccess() ? "✅" : "❌";
            html.append("<div class='top-nav-item' onclick='switchTopPane(" + (i + 1) + ")'>")
                .append(icon).append(" ").append(escapeHtml(r.name)).append("</div>");
        }
        html.append("</div>");

        html.append("<div class='content'>");

        // ============================================================
        // 面板 0：LL(1) 理论推导（独立于测试用例）
        // ============================================================
        html.append("<div class='top-pane active'>");

        // FIRST & FOLLOW 表
        html.append("<div class='card'><h2>📐 First & Follow 集合</h2>");
        html.append("<table><tr><th>非终结符</th><th>First 集合</th><th>Follow 集合</th></tr>");
        for (String nt : analyzer.firstSets.keySet()) {
            html.append("<tr><td><b>").append(ntName(nt)).append("</b></td>")
                .append("<td>").append(analyzer.firstSets.get(nt)).append("</td>")
                .append("<td>").append(analyzer.followSets.get(nt)).append("</td></tr>");
        }
        html.append("</table></div>");

        // 预测分析表 M
        html.append("<div class='card'><h2>📐 预测分析表 M（矩阵展示）</h2>");
        html.append("<div style='overflow-x:auto;'><table><tr><th>NT \\ T</th>");

        List<String> terminalList = new ArrayList<>();
        for (String nt : analyzer.parsingTable.keySet()) {
            for (String t : analyzer.parsingTable.get(nt).keySet()) {
                if (!terminalList.contains(t)) terminalList.add(t);
            }
        }
        for (String t : terminalList) html.append("<th>").append(t).append("</th>");
        html.append("</tr>");

        for (String nt : analyzer.parsingTable.keySet()) {
            html.append("<tr><td><b>").append(ntName(nt)).append("</b></td>");
            for (String t : terminalList) {
                Production p = analyzer.parsingTable.get(nt).get(t);
                html.append("<td>").append(p == null ? "" : toChineseProduction(p)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</table></div></div></div>"); // 结束 LL(1) 面板

        // ============================================================
        // 面板 1~N：每个测试用例的详细结果
        // ============================================================
        for (int idx = 0; idx < allResults.size(); idx++) {
            TestResult r = allResults.get(idx);
            html.append("<div class='top-pane'>");

            // ---- 状态徽章 ----
            String badgeHtml = r.isSuccess()
                    ? "<span class='success-badge'>✓ 编译成功</span>"
                    : "<span class='error-badge'>✗ 编译出错</span>";

            // ---- 源代码 ----
            html.append("<div class='card'><h2>📝 源代码 ").append(badgeHtml).append("</h2>");
            html.append("<div class='source-code'>").append(escapeHtml(r.sourceCode)).append("</div></div>");

            // ---- 词法错误 ----
            if (!r.lexErrors.isEmpty()) {
                html.append("<div class='card'><h2>⚠ 词法错误</h2>");
                for (String err : r.lexErrors) {
                    html.append("<div class='error-box'>").append(escapeHtml(err)).append("</div>");
                }
                html.append("</div>");
            }

            // ---- Token 表格 ----
            if (r.tokens != null && !r.tokens.isEmpty()) {
                html.append("<div class='card'><h2>🔤 Token 序列（共 " + r.tokens.size() + " 个）</h2>");
                html.append("<table><tr><th>序号</th><th>内容</th><th>类型</th><th>位置</th></tr>");
                for (int i = 0; i < r.tokens.size(); i++) {
                    Token t = r.tokens.get(i);
                    html.append("<tr><td>").append(i + 1).append("</td>")
                        .append("<td><b>").append(escapeHtml(t.getValue())).append("</b></td>")
                        .append("<td>").append(t.getType()).append("</td>")
                        .append("<td>").append(t.getLine()).append(":").append(t.getColumn()).append("</td></tr>");
                }
                html.append("</table></div>");
            }

            // ---- 语法/语义错误 ----
            if (r.errorMsg != null) {
                html.append("<div class='card'><h2>❌ 语法/语义错误</h2>");
                html.append("<div class='error-box'>").append(escapeHtml(r.errorMsg)).append("</div>");
                html.append("<p style='color:#8c8c8c; font-size:13px; margin-top:8px;'>说明：编译器正确地检测到了源代码中的错误，这正是词法/语法分析器的功能。</p>");
                html.append("</div>");
            }

            // ---- 语法树 ----
            if (r.astRoot != null) {
                html.append("<div class='card'><h2>🌳 语法树</h2>");
                html.append("<div class='tree'><ul>");
                buildTreeHtml(r.astRoot, html);
                html.append("</ul></div></div>");
            }

            // ---- 优化对比（仅当解析成功且有四元式时） ----
            if (r.errorMsg == null && !r.rawQuads.isEmpty()) {
                // 统计
                int rawArith = 0, optArith = 0;
                for (Quadruple q : r.rawQuads) { if ("+-*/".contains(q.op)) rawArith++; }
                for (Quadruple q : r.optQuads) { if ("+-*/".contains(q.op)) optArith++; }

                html.append("<div class='card'><h2>⚡ 中间代码优化对比</h2>");
                if (rawArith > 0 || optArith > 0) {
                    String statText = (rawArith == optArith)
                            ? "运行时算术运算: " + rawArith + " 条（此测试无优化空间）"
                            : "运行时算术运算: " + rawArith + " 条 → " + optArith + " 条 ｜ 减少 " + (rawArith - optArith) + " 条 🎯";
                    html.append("<div class='opt-stat'>").append(statText).append("</div>");
                }

                html.append("<div class='diff-container'>");

                // 左：原始四元式
                html.append("<div class='diff-box'><div class='diff-header'>📋 原始四元式（");
                html.append(String.valueOf(r.rawQuads.size())).append(" 条）</div><table>");
                for (int i = 0; i < r.rawQuads.size(); i++) {
                    html.append("<tr><td style='width:36px;background:#fafafa;'>").append(i)
                        .append("</td><td style='text-align:left;font-family:monospace;'>")
                        .append(escapeHtml(r.rawQuads.get(i).toString())).append("</td></tr>");
                }
                html.append("</table></div>");

                // 箭头
                html.append("<div class='diff-arrow'>➜</div>");

                // 右：优化后四元式
                html.append("<div class='diff-box'><div class='diff-header' style='color:var(--success);'>✨ 优化后四元式（");
                html.append(String.valueOf(r.optQuads.size())).append(" 条）</div><table>");
                for (int i = 0; i < r.optQuads.size(); i++) {
                    html.append("<tr><td style='width:36px;background:#fafafa;'>").append(i)
                        .append("</td><td style='text-align:left;font-family:monospace;'>")
                        .append(escapeHtml(r.optQuads.get(i).toString())).append("</td></tr>");
                }
                html.append("</table></div>");

                html.append("</div></div>");  // diff-container + card
            }

            html.append("</div>"); // top-pane 结束
        }

        html.append("</div>"); // content 结束

        // ---- JavaScript：顶层面板切换 ----
        html.append("<script>");
        html.append("function switchTopPane(idx) {");
        html.append("  document.querySelectorAll('.top-pane').forEach(p => p.classList.remove('active'));");
        html.append("  document.querySelectorAll('.top-nav-item').forEach(i => i.classList.remove('active'));");
        html.append("  document.querySelectorAll('.top-pane')[idx].classList.add('active');");
        html.append("  document.querySelectorAll('.top-nav-item')[idx].classList.add('active');");
        html.append("}");
        html.append("</script>");

        html.append("</body></html>");

        // ---- 写入文件（强制 UTF-8 编码，避免 Windows GBK 乱码） ----
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream("compiler_report.html"), StandardCharsets.UTF_8)) {
            writer.write(html.toString());
            System.out.println("✓ 综合可视化报告已生成: compiler_report.html");
            System.out.println("  包含: LL(1) 理论推导 + " + allResults.size() + " 个测试用例的完整结果");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ================================================================
    // 传统单测试模式（向后兼容）
    // ================================================================

    /**
     * 传统单测试报告（向后兼容旧代码）。
     */
    public static void generateReport(List<Token> tokens, LL1Analyzer analyzer, TreeNode treeRoot,
                                      List<Quadruple> rawQuads, List<Quadruple> optQuads) {
        List<TestResult> single = new ArrayList<>();
        single.add(new TestResult("测试用例", "", tokens, new ArrayList<>(),
                treeRoot, rawQuads, optQuads, null));
        generateMultiReport(analyzer, single);
    }

    // ================================================================
    // 语法树 HTML 构建（递归）
    // ================================================================

    /**
     * 递归构建语法树的 HTML 表示。
     */
    private static void buildTreeHtml(TreeNode node, StringBuilder html) {
        // 判断是否为叶节点（值为纯终结符）
        String label = node.label;
        boolean isLeaf = !label.contains("(") && (label.equals(label.toLowerCase())
                || label.startsWith("id:") || label.matches("\\d+"));
        String cls = isLeaf ? " class='leaf'" : "";

        html.append("<li><span").append(cls).append(">").append(escapeHtml(label)).append("</span>");
        if (!node.children.isEmpty()) {
            html.append("<ul>");
            for (TreeNode child : node.children) {
                buildTreeHtml(child, html);
            }
            html.append("</ul>");
        }
        html.append("</li>");
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /** HTML 转义，防止 XSS 和渲染问题 */
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
