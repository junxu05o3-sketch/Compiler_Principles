package parser;

import lexer.Token;
import lexer.TokenType;
import java.util.ArrayList;
import java.util.List;

public class Parser {
    private List<Token> tokens;
    private int p = 0;
    private Token currentToken;

    private List<Quadruple> quads = new ArrayList<>();
    private SymbolTable symbolTable = new SymbolTable();
    private int tempCount = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.currentToken = tokens.get(p);
    }

    // 给外部获取四元式的接口
    public List<Quadruple> getQuads() { return this.quads; }

    // ----------------------------------------------------------------
    // 核心工具方法
    // ----------------------------------------------------------------
    private void matchValue(String val) {
        if (currentToken.getValue().equals(val)) {
            advance();
        } else {
            error("期望符号: " + val + "，实际获得: " + currentToken.getValue());
        }
    }

    private void advance() {
        if (p < tokens.size() - 1) {
            p++;
            currentToken = tokens.get(p);
        }
    }

    private String newTemp() { return "t" + (++tempCount); }

    private void error(String msg) {
        throw new RuntimeException("[语法错误] 行 " + currentToken.getLine() + ": " + msg);
    }

    // ----------------------------------------------------------------
    // 统一解析入口：同时生成语法树和四元式
    // ----------------------------------------------------------------
    public TreeNode parse() {
        TreeNode root = new TreeNode("Program");
        matchValue("int"); root.addChild(new TreeNode("int"));
        matchValue("main"); root.addChild(new TreeNode("main"));
        matchValue("("); root.addChild(new TreeNode("("));
        matchValue(")"); root.addChild(new TreeNode(")"));
        matchValue("{"); root.addChild(new TreeNode("{"));

        root.addChild(statementList());

        matchValue("}"); root.addChild(new TreeNode("}"));
        return root;
    }

    private TreeNode statementList() {
        TreeNode node = new TreeNode("StmtList");
        while (!currentToken.getValue().equals("}") && currentToken.getType() != TokenType.EOF) {
            node.addChild(statement());
        }
        return node;
    }

    private TreeNode statement() {
        TreeNode node = new TreeNode("Statement");
        String val = currentToken.getValue();
        if (val.equals("int")) {
            node.addChild(declaration());
        } else if (val.equals("if")) {
            node.addChild(ifStatement());
        } else if (val.equals("while")) {
            node.addChild(whileStatement());
        } else if (currentToken.getType() == TokenType.IDENTIFIER) {
            node.addChild(assignment());
        } else {
            advance();
        }
        return node;
    }

    private TreeNode declaration() {
        TreeNode node = new TreeNode("Decl");
        matchValue("int"); node.addChild(new TreeNode("int"));
        String varName = currentToken.getValue();
        symbolTable.add(varName);
        node.addChild(new TreeNode("id: " + varName));
        advance();
        matchValue(";"); node.addChild(new TreeNode(";"));
        return node;
    }

    private TreeNode assignment() {
        TreeNode node = new TreeNode("Assign");
        String target = currentToken.getValue();
        if (!symbolTable.contains(target)) error("变量未声明: " + target);
        node.addChild(new TreeNode("id: " + target));
        advance();
        matchValue("="); node.addChild(new TreeNode("="));

        // 解析表达式并获取返回值用于生成四元式
        NodeValue res = expression();
        node.addChild(res.node);

        matchValue(";"); node.addChild(new TreeNode(";"));
        quads.add(new Quadruple("=", res.val, "_", target));
        return node;
    }

    private TreeNode whileStatement() {
        TreeNode node = new TreeNode("While");
        matchValue("while"); node.addChild(new TreeNode("while"));
        int beginAddr = quads.size();

        matchValue("("); node.addChild(new TreeNode("("));
        NodeValue condLeft = expression();
        String op = currentToken.getValue();
        advance();
        NodeValue condRight = expression();
        matchValue(")"); node.addChild(new TreeNode(")"));

        int jumpToEndAddr = quads.size();
        quads.add(new Quadruple("j" + reverseOp(op), condLeft.val, condRight.val, "0"));

        matchValue("{"); node.addChild(new TreeNode("{"));
        node.addChild(statementList());
        matchValue("}"); node.addChild(new TreeNode("}"));

        quads.add(new Quadruple("j", "_", "_", String.valueOf(beginAddr)));
        quads.get(jumpToEndAddr).result = String.valueOf(quads.size());
        return node;
    }

    private TreeNode ifStatement() {
        TreeNode node = new TreeNode("If");
        matchValue("if"); node.addChild(new TreeNode("if"));
        matchValue("(");
        NodeValue condLeft = expression();
        String op = currentToken.getValue(); advance();
        NodeValue condRight = expression();
        matchValue(")");

        int jumpIfFalse = quads.size();
        quads.add(new Quadruple("j" + reverseOp(op), condLeft.val, condRight.val, "0"));

        matchValue("{");
        node.addChild(statementList());
        matchValue("}");

        quads.get(jumpIfFalse).result = String.valueOf(quads.size());
        return node;
    }

    // ----------------------------------------------------------------
    // 表达式解析逻辑 (统一生成树节点和返回值)
    // ----------------------------------------------------------------
    private NodeValue expression() {
        NodeValue left = term();
        while (currentToken.getValue().equals("+") || currentToken.getValue().equals("-")) {
            String op = currentToken.getValue(); advance();
            NodeValue right = term();
            String temp = newTemp();
            quads.add(new Quadruple(op, left.val, right.val, temp));

            TreeNode newNode = new TreeNode("Expr(" + op + ")");
            newNode.addChild(left.node);
            newNode.addChild(right.node);
            left = new NodeValue(temp, newNode);
        }
        return left;
    }

    private NodeValue term() {
        NodeValue left = factor();
        while (currentToken.getValue().equals("*") || currentToken.getValue().equals("/")) {
            String op = currentToken.getValue(); advance();
            NodeValue right = factor();
            String temp = newTemp();
            quads.add(new Quadruple(op, left.val, right.val, temp));

            TreeNode newNode = new TreeNode("Term(" + op + ")");
            newNode.addChild(left.node);
            newNode.addChild(right.node);
            left = new NodeValue(temp, newNode);
        }
        return left;
    }

    private NodeValue factor() {
        Token t = currentToken;
        if (t.getType() == TokenType.INTEGER || t.getType() == TokenType.FLOAT || t.getType() == TokenType.IDENTIFIER) {
            advance();
            return new NodeValue(t.getValue(), new TreeNode(t.getValue()));
        } else if (t.getValue().equals("(")) {
            advance();
            NodeValue res = expression();
            matchValue(")");
            return res;
        }
        error("无效因子: " + t.getValue());
        return null;
    }

    private String reverseOp(String op) {
        switch (op) {
            case ">": return "<="; case "<": return ">="; case ">=": return "<";
            case "<=": return ">"; case "==": return "!="; case "!=": return "==";
            default: return op;
        }
    }

    // 内部类：用于同时传递解析出的值和树节点
    private static class NodeValue {
        String val; TreeNode node;
        NodeValue(String v, TreeNode n) { this.val = v; this.node = n; }
    }

    public void printQuads() {
        for (int i = 0; i < quads.size(); i++) System.out.println(i + ": " + quads.get(i));
    }
}