package org.mockito.internal.invocation;

import org.hamcrest.Matcher;
import org.mockito.internal.matchers.ArrayEquals;
import org.mockito.internal.matchers.Equals;

import java.util.ArrayList;
import java.util.List;

/**
 * by Szczepan Faber, created at: 3/31/12
 */
public class ArgumentsProcessor {
    // expands array varArgs that are given by runtime (1, [a, b]) into true
    // varArgs (1, a, b);
    static Object[] expandVarArgs(final boolean isVarArgs, final Object[] args) {
        if (!isVarArgs || args[args.length - 1] != null && !args[args.length - 1].getClass().isArray()) {
            return args == null ? new Object[0] : args;
        }

        final int nonVarArgsCount = args.length - 1;
        Object[] varArgs;
        if (args[nonVarArgsCount] == null) {
            // in case someone deliberately passed null varArg array
            varArgs = new Object[] { null };
        } else {
            varArgs = ArrayEquals.createObjectArray(args[nonVarArgsCount]);
        }
        final int varArgsCount = varArgs.length;
        Object[] newArgs = new Object[nonVarArgsCount + varArgsCount];
        System.arraycopy(args, 0, newArgs, 0, nonVarArgsCount);
        System.arraycopy(varArgs, 0, newArgs, nonVarArgsCount, varArgsCount);
        return newArgs;
    }

    public static List<Matcher> argumentsToMatchers(Object[] arguments) {
        List<Matcher> matchers = new ArrayList<Matcher>(arguments.length);
        for (Object arg : arguments) {
            if (arg != null && arg.getClass().isArray()) {
                matchers.add(new ArrayEquals(arg));
            } else {
                matchers.add(new Equals(arg));
            }
        }
        return matchers;
    }
}
