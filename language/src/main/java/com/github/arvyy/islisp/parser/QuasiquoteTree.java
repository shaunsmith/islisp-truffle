package com.github.arvyy.islisp.parser;

import com.github.arvyy.islisp.ISLISPContext;
import com.github.arvyy.islisp.exceptions.ISLISPError;
import com.github.arvyy.islisp.runtime.Pair;
import com.github.arvyy.islisp.runtime.Symbol;
import com.oracle.truffle.api.nodes.Node;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Representation of a quasiquote, specifying holes and ways to substitute them during evaluation.
 */
//TODO preserve source location
//TODO calcify non-atoms into Atom if there are no internal substitutions
public sealed interface QuasiquoteTree {

    record Atom(Object value) implements QuasiquoteTree { }
    record List(QuasiquoteTree[] children) implements QuasiquoteTree { }
    record Quasiquote(QuasiquoteTree value) implements QuasiquoteTree { }
    record Unquote(QuasiquoteTree value) implements QuasiquoteTree { }
    record UnquoteSplicing(QuasiquoteTree value) implements QuasiquoteTree { }
    record Hole(int index) implements QuasiquoteTree { }

    record QuasiquoteTreeAndExpressions(QuasiquoteTree tree, Object[] expressions) { }

    /**
     * Parse sexpr into quasiquote tree.
     *
     * @param expr sexpr
     * @return quasiquote tree and expressions
     */
    static QuasiquoteTreeAndExpressions parseQuasiquoteTree(Object expr) {
        return parseQuasiquoteTree(expr, 0, 0);
    }

    private static QuasiquoteTreeAndExpressions parseQuasiquoteTree(Object expr, int level, int holeIndex) {
        if (expr instanceof Pair p) {
            if (p.car() instanceof Symbol s) {
                Object rest;
                boolean isSplicing = false;
                switch (s.name()) {
                    case "quasiquote":
                        rest = ((Pair) p.cdr()).car();
                        /*
                        var res = parseQuasiquoteTree(rest, level + 1, holeIndex);
                        return new QuasiquoteTreeAndExpressions(new Quasiquote(res.tree), res.expressions);
                         */
                        return parseQuasiquoteTree(rest, level + 1, holeIndex);
                    case "unquote-splicing":
                        isSplicing = true;
                        // fallthrough
                    case "unquote":
                        rest = ((Pair) p.cdr()).car();
                        if (level < 1) {
                            throw new RuntimeException("Unquote outside of quasiquote");
                        }
                        if (level == 1) {
                            var hole = new Hole(holeIndex);
                            if (isSplicing) {
                                return new QuasiquoteTreeAndExpressions(new UnquoteSplicing(hole), new Object[]{rest});
                            } else {
                                return new QuasiquoteTreeAndExpressions(new Unquote(hole), new Object[]{rest});
                            }
                        } else {
                            return parseQuasiquoteTree(rest, level - 1, holeIndex);
                        }
                     default:
                }
            }
            // regular list
            var expressions = new ArrayList<Object>();
            var children = new ArrayList<QuasiquoteTree>();
            for (var v: p) {
                var parsedChildResult = parseQuasiquoteTree(v, level, holeIndex + expressions.size());
                expressions.addAll(Arrays.asList(parsedChildResult.expressions));
                children.add(parsedChildResult.tree);
            }
            return new QuasiquoteTreeAndExpressions(
                    new List(children.toArray(QuasiquoteTree[]::new)),
                    expressions.toArray(Object[]::new));
        }
        if (expr instanceof Integer || expr instanceof Symbol) {
            return new QuasiquoteTreeAndExpressions(new Atom(expr), new Object[]{});
        }
        throw new RuntimeException();
    }

    /**
     * Evaluate quasiquote tree, substituting holes with given values.
     *
     * @param tree quasiquote tree
     * @param substitutionValues values to be substituted
     * @param node node from which this execution is done; used in case of an error
     * @return evaluated value
     */
    static Object evalQuasiquoteTree(QuasiquoteTree tree, Object[] substitutionValues, Node node) {
        if (tree instanceof Atom a) {
            return a.value;
        }
        if (tree instanceof Unquote u) {
            if (u.value instanceof Hole h) {
                return substitutionValues[h.index];
            } else {
                var value = evalQuasiquoteTree(u.value, substitutionValues, node);
                return new Pair(ISLISPContext.get(null).namedSymbol("unquote"), value);
            }
        }
        if (tree instanceof UnquoteSplicing) {
            throw new ISLISPError("Bad unquotesplicing use", node);
        }
        if (tree instanceof List l) {
            var values = new ArrayList<Object>();
            for (var child: l.children) {
                if (child instanceof UnquoteSplicing us) {
                    if (us.value instanceof Hole h) {
                        if (substitutionValues[h.index] instanceof Pair p) {
                            for (var v: p) {
                                values.add(v);
                            }
                        } else if (substitutionValues[h.index] instanceof Symbol s) {
                            if (s != ISLISPContext.get(null).getNil()) {
                                throw new ISLISPError("Unquote splicing not list", node);
                            }
                        } else {
                            throw new ISLISPError("Unquote splicing not list", node);
                        }
                    } else {
                        var value = evalQuasiquoteTree(child, substitutionValues, node);
                        values.add(new Pair(ISLISPContext.get(null).namedSymbol("unquote-splicing"), value));
                    }
                } else {
                    values.add(evalQuasiquoteTree(child, substitutionValues, node));
                }
            }
            Object lispList = ISLISPContext.get(null).getNil();
            for (int i = values.size() - 1; i >= 0; i--) {
                lispList = new Pair(values.get(i), lispList);
            }
            return lispList;
        }
        throw new ISLISPError("?", node);
    }

}
