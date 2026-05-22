package parser;

/**
 * 四元式类 (op, arg1, arg2, result)
 */
public class Quadruple {
    public String op, arg1, arg2, result;

    public Quadruple(String op, String arg1, String arg2, String result) {
        this.op = op;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.result = result;
    }

    @Override
    public String toString() {
        return String.format("(%-4s, %-4s, %-4s, %-4s)", op, arg1, arg2, result);
    }
}