package com.linbit.linstor.propscon;


/**
 * Manages access to properties containers
 *
 * @author Robert Altnoeder &lt;robert.altnoeder@linbit.com&gt;
 */
public final class PropsAccess
{
    public static Props secureGetProps(Props propsRef)
    {
        // Always require at least VIEW access

        // If CHANGE or CONTROL access is permitted, return a modifiable instance of the
        // properties container, otherwise wrap the properties container in a read-only
        // container and return this read-only container instead
        Props securedProps;
        AccessType allowedAccess = objProt.queryAccess();
        if (allowedAccess.hasAccess(AccessType.CHANGE))
        {
            securedProps = propsRef;
        }
        else
        {
            securedProps = new ReadOnlyPropsImpl(propsRef);
        }
        return securedProps;
    }

    private PropsAccess()
    {
    }

    public static Props secureGetProps(
        Props propsRef
    )
    {
        // Always require at least VIEW access

        Props securedProps;
        if (propsRef instanceof ReadOnlyPropsImpl)
        {
            securedProps = propsRef;
        }
        else
        {
            // If CHANGE or CONTROL access is permitted, return a modifiable instance of the
            // properties container, otherwise wrap the properties container in a read-only
            // container and return this read-only container instead
            AccessType allowedAccess1 = objProt1.queryAccess();
            AccessType allowedAccess2 = objProt2.queryAccess();
            if (allowedAccess1.hasAccess(AccessType.CHANGE) &&
                allowedAccess2.hasAccess(AccessType.CHANGE))
            {
                securedProps = propsRef;
            }
            else
            {
                securedProps = new ReadOnlyPropsImpl(propsRef);
            }
        }
        return securedProps;
    }
}
