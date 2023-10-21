package com.github.arvyy.islisp.functions;

import com.github.arvyy.islisp.ISLISPContext;
import com.github.arvyy.islisp.exceptions.ISLISPError;
import com.github.arvyy.islisp.runtime.*;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Implements `format-char` function, that writes a given character to output stream.
 */
public abstract class ISLISPFormatChar extends RootNode {

    ISLISPFormatChar(TruffleLanguage<?> language) {
        super(language);
    }

    abstract void executeGeneric(Object stream, Object ch);

    @Override
    public final Object execute(VirtualFrame frame) {
        executeGeneric(frame.getArguments()[1], frame.getArguments()[2]);
        return ISLISPContext.get(this).getNil();
    }

    @Specialization
    void executeProper(LispOutputStream stream, LispChar ch) {
        doPrint(stream.outputStream(), ch.codepoint());
    }

    @Fallback
    void executeFallback(Object stream, Object c) {
        throw new ISLISPError("Bad arguments", this);
    }


    @CompilerDirectives.TruffleBoundary
    void doPrint(OutputStream os, int codepoint) {
        try {
            os.write(new String(new int[] {codepoint}, 0, 1).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ISLISPError(e.getMessage(), this);
        }
    }

    /**
     * Construct LispFunction using this root node.
     * @param lang truffle language reference
     * @return lisp function
     */
    public static LispFunction makeLispFunction(TruffleLanguage<?> lang) {
        return new LispFunction(ISLISPFormatCharNodeGen.create(lang).getCallTarget());
    }
}
