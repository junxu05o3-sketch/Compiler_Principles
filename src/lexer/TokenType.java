package lexer;

/**
 * 单词符号的种别码枚举
 * 每种 TokenType 对应词法分析中的一类单词
 */
public enum TokenType {
    KEYWORD,        // 关键字，如 int if else while return
    IDENTIFIER,     // 标识符，如变量名、函数名
    INTEGER,        // 整数常量，如 123
    FLOAT,          // 浮点数常量，如 3.14
    STRING,         // 字符串常量，如 "hello"
    CHAR,           // 字符常量，如 'a'
    OPERATOR,       // 运算符，如 + - * / == != < <= > >= && || !
    DELIMITER,      // 界符，如 ( ) { } ; ,
    EOF,            // 文件结束符
    UNKNOWN         // 非法/未知字符（词法错误）
}
