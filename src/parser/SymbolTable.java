package parser;
import java.util.HashSet;
import java.util.Set;

/**
 * 简易符号表，管理变量声明
 */
public class SymbolTable {
    private Set<String> variables = new HashSet<>();

    public void add(String name) { variables.add(name); }
    public boolean contains(String name) { return variables.contains(name); }
}