package com.deathfrog.warehouseworkshop.api.util;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Provides runtime-selectable diagnostic tracing without changing the configured logger level. */
public final class TraceUtils
{
    public static final String TRACE_RESEARCH_DELIVERY = "researchdelivery";
    private static final Map<String, Boolean> TRACE_SETTINGS = new ConcurrentHashMap<>();

    /** Prevents utility-class construction. */
    private TraceUtils()
    {
    }

    /** Executes a logging statement only when its trace category is enabled. */
    public static void dynamicTrace(final String traceKey, final Runnable loggingStatement)
    {
        if (isTracing(traceKey))
        {
            try
            {
                loggingStatement.run();
            }
            catch (final Throwable throwable)
            {
                WarehouseWorkshopMod.LOGGER.warn("Trace '{}' threw while logging; swallowing.", traceKey, throwable);
            }
        }
    }

    /** Returns whether a trace category is currently enabled. */
    public static boolean isTracing(final String traceKey)
    {
        return Boolean.TRUE.equals(TRACE_SETTINGS.get(traceKey));
    }

    /** Enables or disables a trace category for the current server process. */
    public static void setTrace(final String traceKey, final boolean enabled)
    {
        TRACE_SETTINGS.put(traceKey, enabled);
    }

    /** Returns the trace categories accepted by the command. */
    public static List<String> getTraceKeys()
    {
        return List.of(TRACE_RESEARCH_DELIVERY);
    }
}
