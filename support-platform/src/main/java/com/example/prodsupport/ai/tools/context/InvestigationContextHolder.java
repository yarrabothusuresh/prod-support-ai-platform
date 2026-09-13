package com.example.prodsupport.ai.tools.context;

public final class InvestigationContextHolder {

    private static final ThreadLocal<InvestigationContext> CURRENT_CONTEXT = new ThreadLocal<>();

    private InvestigationContextHolder() {}

    public static void setContext(InvestigationContext context) {
        CURRENT_CONTEXT.set(context);
    }

    public static InvestigationContext getContext() {
        return CURRENT_CONTEXT.get();
    }

    public static void clearContext() {
        CURRENT_CONTEXT.remove();
    }
}
