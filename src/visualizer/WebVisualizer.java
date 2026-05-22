package visualizer;

import lexer.Token;
import parser.LL1Analyzer;
import parser.Production;
import parser.Quadruple;
import parser.TreeNode;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class WebVisualizer {
    public static void generateReport(List<Token> tokens, LL1Analyzer analyzer, TreeNode treeRoot,
                                      List<Quadruple> rawQuads, List<Quadruple> optQuads) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>编译器全流程可视化报告</title>");

        // --- 强大的 CSS 样式 ---
        html.append("<style>");
        html.append("body { font-family: 'Segoe UI', system-ui, sans-serif; background: #f0f2f5; margin: 0; display: flex; flex-direction: column; height: 100vh; }");
        html.append("header { background: #1890ff; color: white; padding: 20px 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }");
        html.append(".nav { display: flex; background: #fff; padding: 0 40px; border-bottom: 1px solid #d9d9d9; }");
        html.append(".nav-item { padding: 15px 25px; cursor: pointer; color: #595959; border-bottom: 2px solid transparent; transition: 0.3s; }");
        html.append(".nav-item:hover { color: #1890ff; }");
        html.append(".nav-item.active { color: #1890ff; border-bottom-color: #1890ff; font-weight: bold; }");
        html.append(".content { flex: 1; padding: 40px; overflow-y: auto; }");
        html.append(".tab-pane { display: none; }");
        html.append(".tab-pane.active { display: block; }");
        html.append(".card { background: white; padding: 25px; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); margin-bottom: 20px; }");
        html.append("table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }");
        html.append("th, td { border: 1px solid #f0f0f0; padding: 12px; text-align: center; }");
        html.append("th { background: #fafafa; }");
        html.append(".diff-container { display: flex; gap: 20px; }");
        html.append(".diff-box { flex: 1; background: #fff; border-radius: 8px; border: 1px solid #d9d9d9; }");
        html.append(".diff-header { background: #fafafa; padding: 10px; border-bottom: 1px solid #d9d9d9; font-weight: bold; text-align: center; }");
        // 树状图样式
        html.append(".tree ul { display: flex; justify-content: center; padding-top: 20px; position: relative; }");
        html.append(".tree li { list-style-type: none; position: relative; padding: 20px 5px 0 5px; text-align: center; }");
        html.append(".tree li::before, .tree li::after { content: ''; position: absolute; top: 0; right: 50%; border-top: 2px solid #ccc; width: 50%; height: 20px; }");
        html.append(".tree li::after { right: auto; left: 50%; border-left: 2px solid #ccc; }");
        html.append(".tree li span { border: 2px solid #1890ff; padding: 5px 10px; border-radius: 5px; display: inline-block; background: #fff; font-size: 12px; }");
        html.append("</style></head><body>");

        html.append("<header><h1>编译器全流程可视化报告 - 重庆交通大学</h1></header>");

        // 导航栏
        html.append("<div class='nav'>");
        html.append("<div class='nav-item active' onclick='showTab(0)'>1. 词法分析</div>");
        html.append("<div class='nav-item' onclick='showTab(1)'>2. LL(1) 理论推导</div>");
        html.append("<div class='nav-item' onclick='showTab(2)'>3. 语法树</div>");
        html.append("<div class='nav-item' onclick='showTab(3)'>4. 代码优化</div>");
        html.append("<div class='nav-item' onclick='showTab(4)'>5. 最终中间代码</div>");
        html.append("</div>");

        html.append("<div class='content'>");

        // --- 选项卡 1: 词法 ---
        html.append("<div class='tab-pane active'>");
        html.append("<div class='card'><h2>词法扫描步骤 (Token Stream)</h2><table><tr><th>步骤</th><th>内容</th><th>类型</th><th>位置</th></tr>");
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            html.append(String.format("<tr><td>%d</td><td><b>%s</b></td><td>%s</td><td>%d:%d</td></tr>", i+1, t.getValue(), t.getType(), t.getLine(), t.getColumn()));
        }
        html.append("</table></div></div>");

        // --- 选项卡 2: LL(1) ---
        html.append("<div class='tab-pane'>");
        html.append("<div class='card'><h2>First & Follow 集合</h2><table><tr><th>非终结符</th><th>First</th><th>Follow</th></tr>");
        for(String nt : analyzer.firstSets.keySet()) {
            html.append("<tr><td>").append(nt).append("</td><td>").append(analyzer.firstSets.get(nt)).append("</td><td>").append(analyzer.followSets.get(nt)).append("</td></tr>");
        }
        html.append("</table></div>");
        html.append("<div class='card'><h2>预测分析表 M</h2><div style='overflow-x:auto;'><table><tr><th>NT \\ T</th>");
        List<String> terminalList = new ArrayList<>();
        for(String nt : analyzer.parsingTable.keySet()) {
            for(String t : analyzer.parsingTable.get(nt).keySet()) if(!terminalList.contains(t)) terminalList.add(t);
        }
        for(String t : terminalList) html.append("<th>").append(t).append("</th>");
        html.append("</tr>");
        for(String nt : analyzer.parsingTable.keySet()) {
            html.append("<tr><td><b>").append(nt).append("</b></td>");
            for(String t : terminalList) {
                Production p = analyzer.parsingTable.get(nt).get(t);
                html.append("<td>").append(p == null ? "" : p.toString()).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</table></div></div></div>");

        // --- 选项卡 3: 语法树 ---
        html.append("<div class='tab-pane'><div class='card'><h2>语法树结构 (Syntax Tree)</h2><div class='tree'><ul>");
        buildTreeHtml(treeRoot, html);
        html.append("</ul></div></div></div>");

        // --- 选项卡 4: 优化对比 ---
        html.append("<div class='tab-pane'><div class='card'><h2>中间代码优化对比 (Optimization)</h2><div class='diff-container'>");
        html.append("<div class='diff-box'><div class='diff-header'>原始四元式</div><table>");
        for(Quadruple q : rawQuads) html.append("<tr><td>").append(q.toString()).append("</td></tr>");
        html.append("</table></div>");
        html.append("<div style='font-size:30px; align-self:center;'>➜</div>");
        html.append("<div class='diff-box'><div class='diff-header' style='color:#52c41a'>优化后四元式 (常量合并)</div><table>");
        for(Quadruple q : optQuads) html.append("<tr><td>").append(q.toString()).append("</td></tr>");
        html.append("</table></div>");
        html.append("</div></div></div>");

        // --- 选项卡 5: 最终结果 ---
        html.append("<div class='tab-pane'><div class='card'><h2>最终中间代码序列 (Quadruples)</h2><table>");
        for (int i = 0; i < optQuads.size(); i++) {
            html.append("<tr><td style='width:50px; background:#fafafa;'>").append(i).append("</td><td style='text-align:left; padding-left:30px;'>").append(optQuads.get(i).toString()).append("</td></tr>");
        }
        html.append("</table></div></div>");

        html.append("</div>"); // content end

        // JavaScript：处理选项卡切换
        html.append("<script>");
        html.append("function showTab(index) {");
        html.append("  const panes = document.querySelectorAll('.tab-pane');");
        html.append("  const items = document.querySelectorAll('.nav-item');");
        html.append("  panes.forEach(p => p.classList.remove('active'));");
        html.append("  items.forEach(i => i.classList.remove('active'));");
        html.append("  panes[index].classList.add('active');");
        html.append("  items[index].classList.add('active');");
        html.append("}");
        html.append("</script>");

        html.append("</body></html>");

        try (FileWriter fw = new FileWriter("compiler_report.html")) {
            fw.write(html.toString());
            System.out.println("\n✓ 高级可视化报告已生成: compiler_report.html");
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void buildTreeHtml(TreeNode node, StringBuilder html) {
        html.append("<li><span>").append(node.label).append("</span>");
        if (!node.children.isEmpty()) {
            html.append("<ul>");
            for (TreeNode child : node.children) buildTreeHtml(child, html);
            html.append("</ul>");
        }
        html.append("</li>");
    }
}