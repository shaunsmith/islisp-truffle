package com.github.arvyy.islisp.functions;

import com.github.arvyy.islisp.ISLISPContext;
import com.github.arvyy.islisp.nodes.ISLISPErrorSignalerNode;
import com.github.arvyy.islisp.nodes.ISLISPTypes;
import com.github.arvyy.islisp.runtime.LispFunction;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.CountingConditionProfile;

@TypeSystemReference(ISLISPTypes.class)
public abstract class ISLISPNumericEqual extends RootNode {

    @Child
    private ISLISPErrorSignalerNode errorSignalerNode;
    private final CountingConditionProfile profile;

    protected ISLISPNumericEqual(TruffleLanguage<?> language) {
        super(language);
        errorSignalerNode = new ISLISPErrorSignalerNode();
        profile = CountingConditionProfile.create();
    }

    abstract Object executeGeneric(Object a, Object b);

    @Override
    public final Object execute(VirtualFrame frame) {
        return executeGeneric(frame.getArguments()[1], frame.getArguments()[2]);
    }

    @Specialization
    Object doInts(int a, int b) {
        if (profile.profile(a == b)) {
            return ISLISPContext.get(this).getT();
        }
        return ISLISPContext.get(this).getNil();
    }

    @Specialization
    Object doDoubles(double a, double b) {
        if (profile.profile(a == b)) {
            return ISLISPContext.get(this).getT();
        }
        return ISLISPContext.get(this).getNil();
    }

    @Fallback
    Object notNumbers(Object a, Object b) {
        var ctx = ISLISPContext.get(this);
        var numberClass = ctx.lookupClass(ctx.namedSymbol("<number>").identityReference());
        return errorSignalerNode.signalWrongType(b, numberClass);
    }

    public static LispFunction makeLispFunction(TruffleLanguage<?> lang) {
        return new LispFunction(ISLISPNumericEqualNodeGen.create(lang).getCallTarget());
    }

}
