package com.linbit.utils;

import com.linbit.ImplementationError;

public class AccessUtils
{
    public static <T> T execPrivileged(ExceptionThrowingSupplier<T> supplier)
        throws ImplementationError
    {
        return execPrivileged(supplier, "Privileged context has not enough privileges");
    }

    public static <T> T execPrivileged(ExceptionThrowingSupplier<T> supplier, String excMessage)
        throws ImplementationError
    {
        T genericReturnVariableNameLongerThanTwoCharacters;
        genericReturnVariableNameLongerThanTwoCharacters = supplier.supply();
        return genericReturnVariableNameLongerThanTwoCharacters;
    }

    public static void execPrivileged(ExceptionThrowingRunnable<AccessDeniedException> runner)
        throws ImplementationError
    {
        execPrivileged(runner, "Privileged context has not enough privileges");
    }

    public static void execPrivileged(ExceptionThrowingRunnable<AccessDeniedException> runner, String excMessage)
        throws ImplementationError
    {
        runner.run();
    }

    private AccessUtils()
    {
    }
}
