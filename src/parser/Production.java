package parser;

import java.util.List;

public class Production {
    public String lhs;         // 左部，如 "Expression"
    public List<String> rhs;  // 右部，如 ["Term", "ExpressionPrime"]

    public Production(String lhs, List<String> rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public String toString() {
        return lhs + " -> " + String.join(" ", rhs);
    }
}