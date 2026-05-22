package parser;

import java.util.*;

public class LL1Analyzer {
    private List<Production> productions = new ArrayList<>();
    private Set<String> terminals = new HashSet<>();
    private Set<String> nonTerminals = new HashSet<>();

    public Map<String, Set<String>> firstSets = new HashMap<>();
    public Map<String, Set<String>> followSets = new HashMap<>();
    public Map<String, Map<String, Production>> parsingTable = new HashMap<>();

    public void addProduction(String lhs, String... rhs) {
        productions.add(new Production(lhs, Arrays.asList(rhs)));
        nonTerminals.add(lhs);
        for (String s : rhs) {
            if (!s.equals("ε") && !s.matches("[A-Z].*")) { // 假设大写开头是非终结符
                terminals.add(s);
            }
        }
    }

    // --- 4.3 First 集合构造实现 ---
    public void computeFirst() {
        for (String nt : nonTerminals) firstSets.put(nt, new HashSet<>());

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Production p : productions) {
                Set<String> firstLhs = firstSets.get(p.lhs);
                int beforeSize = firstLhs.size();

                String firstSymbol = p.rhs.get(0);
                if (firstSymbol.equals("ε") || terminals.contains(firstSymbol)) {
                    firstLhs.add(firstSymbol);
                } else {
                    firstLhs.addAll(firstSets.get(firstSymbol));
                }

                if (firstLhs.size() > beforeSize) changed = true;
            }
        }
    }

    // --- 4.4 Follow 集合构造实现 ---
    public void computeFollow(String startSymbol) {
        for (String nt : nonTerminals) followSets.put(nt, new HashSet<>());
        followSets.get(startSymbol).add("#"); // 结束符

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Production p : productions) {
                for (int i = 0; i < p.rhs.size(); i++) {
                    String symbol = p.rhs.get(i);
                    if (nonTerminals.contains(symbol)) {
                        Set<String> followSymbol = followSets.get(symbol);
                        int beforeSize = followSymbol.size();

                        // 如果后面还有符号 A -> αBβ
                        if (i + 1 < p.rhs.size()) {
                            String next = p.rhs.get(i + 1);
                            if (terminals.contains(next)) followSymbol.add(next);
                            else {
                                Set<String> nextFirst = new HashSet<>(firstSets.get(next));
                                nextFirst.remove("ε");
                                followSymbol.addAll(nextFirst);
                            }
                        } else { // A -> αB
                            followSymbol.addAll(followSets.get(p.lhs));
                        }

                        if (followSymbol.size() > beforeSize) changed = true;
                    }
                }
            }
        }
    }

    // --- 4.5 预测分析表的创建 ---
    public void buildTable() {
        for (String nt : nonTerminals) parsingTable.put(nt, new HashMap<>());

        for (Production p : productions) {
            String firstSymbol = p.rhs.get(0);
            Set<String> lookaheads = new HashSet<>();

            if (firstSymbol.equals("ε")) {
                lookaheads.addAll(followSets.get(p.lhs));
            } else if (terminals.contains(firstSymbol)) {
                lookaheads.add(firstSymbol);
            } else {
                lookaheads.addAll(firstSets.get(firstSymbol));
            }

            for (String terminal : lookaheads) {
                parsingTable.get(p.lhs).put(terminal, p);
            }
        }
    }

    public void printInfo() {
        System.out.println("\n[First 集合]: " + firstSets);
        System.out.println("[Follow 集合]: " + followSets);
        System.out.println("[预测分析表 M]:");
        parsingTable.forEach((nt, map) -> {
            System.out.println("  " + nt + ": " + map);
        });
    }

    public void printTableGrid() {
        System.out.println("\n[4.5 预测分析表 M (矩阵展示)]:");
        List<String> terminalList = new ArrayList<>(terminals);
        terminalList.add("#"); // 包含结束符

        // 打印表头
        System.out.printf("%-15s", "");
        for (String t : terminalList) {
            System.out.printf("%-20s", t);
        }
        System.out.println();

        // 打印每一行
        for (String nt : nonTerminals) {
            System.out.printf("%-15s", nt);
            Map<String, Production> row = parsingTable.get(nt);
            for (String t : terminalList) {
                Production p = row.get(t);
                System.out.printf("%-20s", (p == null ? "" : p.toString()));
            }
            System.out.println();
        }
    }
}