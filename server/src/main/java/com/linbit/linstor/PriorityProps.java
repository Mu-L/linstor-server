package com.linbit.linstor;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.api.prop.LinStorObject;
import com.linbit.linstor.core.objects.NodeConnection;
import com.linbit.linstor.core.objects.ResourceConnection;
import com.linbit.linstor.core.objects.VolumeConnection;
import com.linbit.linstor.propscon.InvalidKeyException;
import com.linbit.linstor.propscon.PropsContainer;
import com.linbit.linstor.propscon.ReadOnlyProps;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.security.AccessDeniedException;
import com.linbit.utils.Pair;
import com.linbit.utils.PairNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class PriorityProps
{
    public static final String FALLBACKMAP_NAME = "Fallback";
    public static final String DEFAULT_DESCR = "default value";

    private final List<PairNonNull<ReadOnlyProps, String>> propList = new ArrayList<>();
    private final HashMap<String, String> fallbackMap = new HashMap<>();

    public PriorityProps(
        AccessContext accCtx,
        NodeConnection nodeConnection,
        ResourceConnection resourceConnection,
        VolumeConnection volumeConnection
    )
        throws AccessDeniedException
    {
        if (volumeConnection != null)
        {
            addProps(volumeConnection.getProps(accCtx));
        }
        if (resourceConnection != null)
        {
            addProps(resourceConnection.getProps(accCtx));
        }
        if (nodeConnection != null)
        {
            addProps(nodeConnection.getProps(accCtx));
        }
    }

    /**
     * First Props is queried first in case of getProp(String, String)
     *
     * @param props
     */
    public PriorityProps(ReadOnlyProps... props)
    {
        addProps(props);
    }

    public PriorityProps addProps(ReadOnlyProps... props)
    {
        for (ReadOnlyProps prop : props)
        {
            addProps(prop, "");
        }
        return this;
    }

    public PriorityProps addProps(ReadOnlyProps props, String descr)
    {
        if (props != null)
        {
            propList.add(new PairNonNull<>(props, descr));
        }
        return this;
    }

    public PriorityProps addProps(ReadOnlyProps props, String descr, String type)
    {
        if (props != null)
        {
            propList.add(new PairNonNull<>(props, descr));
        }
        return this;
    }

    /**
     * Get the prio property and the container it is included.
     * @param key Property key
     * @param namespace namespace the property is in.
     * @return A Pair containing the property value and the container it is contained.
     * @throws InvalidKeyException
     */
    public Pair<String, ReadOnlyProps> getPropAndContainer(String key, @Nullable String namespace)
        throws InvalidKeyException
    {
        String value = null;
        @Nullable ReadOnlyProps container = null;
        for (PairNonNull<ReadOnlyProps, String> pair : propList)
        {
            value = pair.objA.getProp(key, namespace);
            if (value != null)
            {
                container = pair.objA;
                break;
            }
        }
        if (value == null)
        {
            final String fullKey = namespace != null ? namespace + ReadOnlyProps.PATH_SEPARATOR + key : key;
            value = fallbackMap.get(prepStoreKey(fullKey));
        }
        return new Pair<>(value, container);
    }

    public String getProp(String key, String namespace) throws InvalidKeyException
    {
        return getPropAndContainer(key, namespace).objA;
    }

    public String getProp(String key, @Nullable String namespace, String defaultValue) throws InvalidKeyException
    {
        final String val = getProp(key, namespace);
        return val == null ? defaultValue : val;
    }

    public @Nullable String getProp(String key) throws InvalidKeyException
    {
        return getProp(key, null);
    }

    /**
     * Get the prio property and the container it is included if not found defaultValue is returned.
     * @param key Property key
     * @param namespace namespace the property is in.
     * @param defaultValue Default value if property not found.
     * @return A Pair containing the property value and the container it is contained, if not found and the default
     *         value is returned, the container is null.
     * @throws InvalidKeyException
     */
    public Pair<String, ReadOnlyProps> getPropAndContainer(String key, String namespace, String defaultValue)
        throws InvalidKeyException
    {
        var result = getPropAndContainer(key, namespace);
        return result.objA == null ? new Pair<>(defaultValue, null) : result;
    }

    private String prepStoreKey(String key)
    {
        return key
            .replaceAll(ReadOnlyProps.PATH_SEPARATOR + "+", ReadOnlyProps.PATH_SEPARATOR)
            .replaceAll("^" + ReadOnlyProps.PATH_SEPARATOR + "*", "");
    }

    public void setFallbackProp(String key, String value)
    {
        fallbackMap.put(prepStoreKey(key), value);
    }

    public void setFallbackProp(String key, String value, String namespace)
    {
        final String fullKey = namespace.endsWith(ReadOnlyProps.PATH_SEPARATOR) ?
            namespace + key :
            namespace + ReadOnlyProps.PATH_SEPARATOR + key;
        fallbackMap.put(prepStoreKey(fullKey), value);
    }

    public Map<String, String> renderRelativeMap(@Nullable String namespace)
    {
        Map<String, String> ret = new HashMap<>();

        int nsLen;

        if (namespace != null)
        {
            nsLen = namespace.length();
            if (nsLen > 0 && !namespace.endsWith(ReadOnlyProps.PATH_SEPARATOR))
            {
                nsLen++; // also cut the trailing "/"
            }
        }
        else
        {
            nsLen = 0;
        }

        for (PairNonNull<ReadOnlyProps, String> prop : propList)
        {
            @Nullable ReadOnlyProps optNs = prop.objA.getNamespace(namespace);
            if (optNs != null)
            {
                for (Entry<String, String> entry : optNs.map().entrySet())
                {
                    ret.putIfAbsent(
                        entry.getKey().substring(nsLen),
                        entry.getValue()
                    );
                }
            }
        }
        for (Entry<String, String> entry : fallbackMap.entrySet())
        {
            if (namespace != null && entry.getKey().startsWith(namespace))
            {
                ret.putIfAbsent(entry.getKey().substring(nsLen), entry.getValue());
            }
        }
        return ret;
    }

    /**
     * @param namespcDrbdHandlerOptionsRef
     * @return true if any of the given {@link PropsContainer} has the given namespace, i.e. at least
     * one key/value within the given namespace
     */
    public boolean anyPropsHasNamespace(String namespcDrbdHandlerOptionsRef)
    {
        boolean ret = false;
        for (PairNonNull<ReadOnlyProps, String> entry : propList)
        {
            if (entry.objA.getNamespace(namespcDrbdHandlerOptionsRef) != null)
            {
                ret = true;
                break;
            }
        }
        for (Entry<String, String> entry : fallbackMap.entrySet())
        {
            if (entry.getKey().startsWith(namespcDrbdHandlerOptionsRef))
            {
                ret = true;
                break;
            }
        }
        return ret;
    }

    /**
     * Returns a list of <Value, PropsContainer-Description> of all property containers containing
     * the key. If a property container does not contain the requested key, it will be skipped from the
     * result.
     */
    public MultiResult getConflictingProp(String key, String namespace)
        throws InvalidKeyException
    {
        MultiResult ret = null;

        for (PairNonNull<ReadOnlyProps, String> pair : propList)
        {
            String value = pair.objA.getProp(key, namespace);
            if (value != null)
            {
                if (ret == null)
                {
                    ret = new MultiResult(
                        value,
                        pair.objB,
                        pair.objA.getDescription(),
                        pair.objA.getType()
                    );
                }
                else
                {
                    ret.addResult(
                        value,
                        pair.objB,
                        pair.objA.getDescription(),
                        pair.objA.getType()
                    );
                }
            }
        }
        final String fullKey = namespace != null ? namespace + ReadOnlyProps.PATH_SEPARATOR + key : key;
        String value = fallbackMap.get(prepStoreKey(fullKey));
        if (value != null)
        {
            if (ret == null)
            {
                ret = new MultiResult(value, FALLBACKMAP_NAME, DEFAULT_DESCR, null);
            }
            else
            {
                ret.addResult(value, FALLBACKMAP_NAME, DEFAULT_DESCR, null);
            }
        }

        return ret;
    }

    public Map<String, MultiResult> renderConflictingMap(String namespace, boolean absoluteKey)
    {
        Map<String, MultiResult> ret = new TreeMap<>();

        int nsLen = namespace.length();
        if (nsLen > 0)
        {
            nsLen++; // also cut the trailing "/"
        }

        for (PairNonNull<ReadOnlyProps, String> propWithDescr : propList)
        {
            @Nullable ReadOnlyProps optNs = propWithDescr.objA.getNamespace(namespace);
            if (optNs != null)
            {
                for (Entry<String, String> entry : optNs.map().entrySet())
                {
                    final String absKey = entry.getKey();
                    final String key = absoluteKey ? absKey : absKey.substring(nsLen);
                    MultiResult result = ret.get(key);
                    if (result == null)
                    {
                        result = new MultiResult(
                            entry.getValue(),
                            propWithDescr.objB,
                            propWithDescr.objA.getDescription(),
                            propWithDescr.objA.getType()
                        );
                        ret.put(key, result);
                    }
                    else
                    {
                        result.addResult(
                            entry.getValue(),
                            propWithDescr.objB,
                            propWithDescr.objA.getDescription(),
                            propWithDescr.objA.getType()
                        );
                    }
                }
            }
        }
        for (Entry<String, String> entry : fallbackMap.entrySet())
        {
            if (entry.getKey().startsWith(namespace))
            {
                final String absKey = entry.getKey();
                final String key = absoluteKey ? absKey : absKey.substring(nsLen);
                MultiResult result = ret.get(key);
                if (result == null)
                {
                    result = new MultiResult(entry.getValue(), FALLBACKMAP_NAME, DEFAULT_DESCR, null);
                    ret.put(key, result);
                }
                else
                {
                    result.addResult(entry.getValue(), FALLBACKMAP_NAME, DEFAULT_DESCR, null);
                }
            }
        }
        return ret;
    }

    public static class MultiResult
    {
        public final ValueWithDescription first;
        public final List<ValueWithDescription> conflictingList;
        private final List<ValueWithDescription> modifiableList = new ArrayList<>();

        public MultiResult(String valueRef, String propDescr, String objDescr, @Nullable LinStorObject objType)
        {
            first = new ValueWithDescription(valueRef, propDescr, objDescr, objType);
            conflictingList = Collections.unmodifiableList(modifiableList);
        }

        private void addResult(String valueRef, String propDescr, String objDescr, @Nullable LinStorObject objType)
        {
            modifiableList.add(new ValueWithDescription(valueRef, propDescr, objDescr, objType));
        }
    }

    public static class ValueWithDescription
    {
        public final String value;
        public final String propsDescription;
        public final String objDescription;
        public final @Nullable LinStorObject objType;

        private ValueWithDescription(
            String valueRef,
            String propsDescriptionRef,
            String objDescrRef,
            @Nullable LinStorObject objTypeRef
        )
        {
            value = valueRef;
            propsDescription = propsDescriptionRef;
            objDescription = objDescrRef;
            objType = objTypeRef;
        }
    }
}
