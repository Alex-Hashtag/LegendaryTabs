package sfiomn.legendarytabs.api.tabs_menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tiny recursive-descent math expression evaluator used to compute data-driven
 * screen sizes from named variables. Supports arithmetic, comparisons, boolean
 * logic, a ternary operator, and a handful of helper functions.
 *
 * Grammar (highest to lowest precedence):
 *   primary    := NUMBER | IDENT ('(' args ')')? | '(' expr ')'
 *   unary      := ('-' | '!') unary | primary
 *   mul        := unary (('*' | '/' | '%') unary)*
 *   add        := mul (('+' | '-') mul)*
 *   relational := add (('<' | '<=' | '>' | '>=') add)*
 *   equality   := relational (('==' | '!=') relational)*
 *   and        := equality ('&&' equality)*
 *   or         := and ('||' and)*
 *   ternary    := or ('?' expr ':' expr)?
 *   expr       := ternary
 *
 * Booleans are represented as 1.0 (true) / 0.0 (false); any nonzero value is
 * treated as true when used as a condition.
 */
public class FormulaEvaluator {

    public static class FormulaException extends RuntimeException {
        public FormulaException(String message) {
            super(message);
        }
    }

    private enum TokenType {
        NUMBER, IDENT, PLUS, MINUS, STAR, SLASH, PERCENT,
        LPAREN, RPAREN, COMMA, QUESTION, COLON,
        LT, LE, GT, GE, EQ, NE, AND, OR, NOT, EOF
    }

    private static class Token {
        final TokenType type;
        final String text;
        final double number;

        Token(TokenType type, String text) {
            this(type, text, 0);
        }

        Token(TokenType type, String text, double number) {
            this.type = type;
            this.text = text;
            this.number = number;
        }
    }

    private final List<Token> tokens;
    private int pos = 0;
    private final Map<String, Double> variables;

    private FormulaEvaluator(String expression, Map<String, Double> variables) {
        this.tokens = tokenize(expression);
        this.variables = variables;
    }

    public static double evaluate(String expression, Map<String, Double> variables) {
        if (expression == null || expression.isBlank()) {
            throw new FormulaException("Empty formula");
        }
        FormulaEvaluator evaluator = new FormulaEvaluator(expression, variables);
        double result = evaluator.parseExpr();
        evaluator.expect(TokenType.EOF);
        return result;
    }

    private static List<Token> tokenize(String expression) {
        List<Token> result = new ArrayList<>();
        int i = 0;
        int len = expression.length();
        while (i < len) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < len && Character.isDigit(expression.charAt(i + 1)))) {
                int start = i;
                while (i < len && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) i++;
                String text = expression.substring(start, i);
                result.add(new Token(TokenType.NUMBER, text, Double.parseDouble(text)));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(expression.charAt(i)) || expression.charAt(i) == '_')) i++;
                result.add(new Token(TokenType.IDENT, expression.substring(start, i)));
                continue;
            }
            switch (c) {
                case '+' -> { result.add(new Token(TokenType.PLUS, "+")); i++; }
                case '-' -> { result.add(new Token(TokenType.MINUS, "-")); i++; }
                case '*' -> { result.add(new Token(TokenType.STAR, "*")); i++; }
                case '/' -> { result.add(new Token(TokenType.SLASH, "/")); i++; }
                case '%' -> { result.add(new Token(TokenType.PERCENT, "%")); i++; }
                case '(' -> { result.add(new Token(TokenType.LPAREN, "(")); i++; }
                case ')' -> { result.add(new Token(TokenType.RPAREN, ")")); i++; }
                case ',' -> { result.add(new Token(TokenType.COMMA, ",")); i++; }
                case '?' -> { result.add(new Token(TokenType.QUESTION, "?")); i++; }
                case ':' -> { result.add(new Token(TokenType.COLON, ":")); i++; }
                case '<' -> {
                    if (i + 1 < len && expression.charAt(i + 1) == '=') { result.add(new Token(TokenType.LE, "<=")); i += 2; }
                    else { result.add(new Token(TokenType.LT, "<")); i++; }
                }
                case '>' -> {
                    if (i + 1 < len && expression.charAt(i + 1) == '=') { result.add(new Token(TokenType.GE, ">=")); i += 2; }
                    else { result.add(new Token(TokenType.GT, ">")); i++; }
                }
                case '=' -> {
                    if (i + 1 < len && expression.charAt(i + 1) == '=') { result.add(new Token(TokenType.EQ, "==")); i += 2; }
                    else throw new FormulaException("Unexpected '=' at position " + i + " (did you mean '=='?)");
                }
                case '!' -> {
                    if (i + 1 < len && expression.charAt(i + 1) == '=') { result.add(new Token(TokenType.NE, "!=")); i += 2; }
                    else { result.add(new Token(TokenType.NOT, "!")); i++; }
                }
                case '&' -> {
                    if (i + 1 < len && expression.charAt(i + 1) == '&') { result.add(new Token(TokenType.AND, "&&")); i += 2; }
                    else throw new FormulaException("Unexpected '&' at position " + i + " (did you mean '&&'?)");
                }
                case '|' -> {
                    if (i + 1 < len && expression.charAt(i + 1) == '|') { result.add(new Token(TokenType.OR, "||")); i += 2; }
                    else throw new FormulaException("Unexpected '|' at position " + i + " (did you mean '||'?)");
                }
                default -> throw new FormulaException("Unexpected character '" + c + "' at position " + i);
            }
        }
        result.add(new Token(TokenType.EOF, ""));
        return result;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private boolean check(TokenType type) {
        return peek().type == type;
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(TokenType type) {
        if (!match(type)) {
            throw new FormulaException("Expected " + type + " but found '" + peek().text + "'");
        }
    }

    private double parseExpr() {
        return parseTernary();
    }

    private double parseTernary() {
        double condition = parseOr();
        if (match(TokenType.QUESTION)) {
            double whenTrue = parseExpr();
            expect(TokenType.COLON);
            double whenFalse = parseExpr();
            return condition != 0 ? whenTrue : whenFalse;
        }
        return condition;
    }

    private double parseOr() {
        double left = parseAnd();
        while (match(TokenType.OR)) {
            double right = parseAnd();
            left = (left != 0 || right != 0) ? 1 : 0;
        }
        return left;
    }

    private double parseAnd() {
        double left = parseEquality();
        while (match(TokenType.AND)) {
            double right = parseEquality();
            left = (left != 0 && right != 0) ? 1 : 0;
        }
        return left;
    }

    private double parseEquality() {
        double left = parseRelational();
        while (true) {
            if (match(TokenType.EQ)) left = (left == parseRelational()) ? 1 : 0;
            else if (match(TokenType.NE)) left = (left != parseRelational()) ? 1 : 0;
            else break;
        }
        return left;
    }

    private double parseRelational() {
        double left = parseAdditive();
        while (true) {
            if (match(TokenType.LT)) left = (left < parseAdditive()) ? 1 : 0;
            else if (match(TokenType.LE)) left = (left <= parseAdditive()) ? 1 : 0;
            else if (match(TokenType.GT)) left = (left > parseAdditive()) ? 1 : 0;
            else if (match(TokenType.GE)) left = (left >= parseAdditive()) ? 1 : 0;
            else break;
        }
        return left;
    }

    private double parseAdditive() {
        double left = parseMultiplicative();
        while (true) {
            if (match(TokenType.PLUS)) left += parseMultiplicative();
            else if (match(TokenType.MINUS)) left -= parseMultiplicative();
            else break;
        }
        return left;
    }

    private double parseMultiplicative() {
        double left = parseUnary();
        while (true) {
            if (match(TokenType.STAR)) left *= parseUnary();
            else if (match(TokenType.SLASH)) left /= parseUnary();
            else if (match(TokenType.PERCENT)) left %= parseUnary();
            else break;
        }
        return left;
    }

    private double parseUnary() {
        if (match(TokenType.MINUS)) return -parseUnary();
        if (match(TokenType.NOT)) return parseUnary() == 0 ? 1 : 0;
        return parsePrimary();
    }

    private double parsePrimary() {
        Token token = peek();
        if (match(TokenType.NUMBER)) {
            return token.number;
        }
        if (match(TokenType.LPAREN)) {
            double value = parseExpr();
            expect(TokenType.RPAREN);
            return value;
        }
        if (match(TokenType.IDENT)) {
            if (match(TokenType.LPAREN)) {
                List<Double> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    args.add(parseExpr());
                    while (match(TokenType.COMMA)) {
                        args.add(parseExpr());
                    }
                }
                expect(TokenType.RPAREN);
                return callFunction(token.text, args);
            }
            Double value = variables.get(token.text);
            if (value == null) {
                throw new FormulaException("Unknown variable '" + token.text + "'");
            }
            return value;
        }
        throw new FormulaException("Unexpected token '" + token.text + "'");
    }

    private double callFunction(String name, List<Double> args) {
        return switch (name) {
            case "min" -> {
                if (args.size() != 2) throw new FormulaException("min() expects 2 arguments");
                yield Math.min(args.get(0), args.get(1));
            }
            case "max" -> {
                if (args.size() != 2) throw new FormulaException("max() expects 2 arguments");
                yield Math.max(args.get(0), args.get(1));
            }
            case "floor" -> {
                if (args.size() != 1) throw new FormulaException("floor() expects 1 argument");
                yield Math.floor(args.get(0));
            }
            case "ceil" -> {
                if (args.size() != 1) throw new FormulaException("ceil() expects 1 argument");
                yield Math.ceil(args.get(0));
            }
            case "round" -> {
                if (args.size() != 1) throw new FormulaException("round() expects 1 argument");
                yield (double) Math.round(args.get(0));
            }
            case "abs" -> {
                if (args.size() != 1) throw new FormulaException("abs() expects 1 argument");
                yield Math.abs(args.get(0));
            }
            default -> throw new FormulaException("Unknown function '" + name + "'");
        };
    }
}
