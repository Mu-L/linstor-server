package com.linbit.drbdmanage;

/**
 * Valid name of a drbdmanageNG resource
 *
 * @author Robert Altnoeder &lt;robert.altnoeder@linbit.com&gt;
 */
public class ResourceName extends GenericName
{
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 48;

    public static final byte[] VALID_CHARS = { '_' };
    public static final byte[] VALID_INNER_CHARS = { '-' };

    private ResourceName(String resName)
    {
        super(resName);
    }

    public ResourceName fromString(String resName)
        throws InvalidNameException
    {
        Checks.nameCheck(resName, MIN_LENGTH, MAX_LENGTH, VALID_CHARS, VALID_INNER_CHARS);
        return new ResourceName(resName);
    }
}
