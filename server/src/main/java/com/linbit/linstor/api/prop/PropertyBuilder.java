package com.linbit.linstor.api.prop;

import com.linbit.ImplementationError;
import com.linbit.linstor.LinStorRuntimeException;
import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.api.prop.Property.PropertyType;
import com.linbit.linstor.propscon.ReadOnlyProps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PropertyBuilder
{
    private @Nullable String name;
    private @Nullable String key;
    private @Nullable PropertyType type;
    private @Nullable String value;
    private boolean internal;
    private @Nullable String info;
    private @Nullable String[] values;
    private @Nullable Long max = null;
    private @Nullable Long min = null;
    private @Nullable Double minFloat = null;
    private @Nullable Double maxFloat = null;
    private @Nullable String dflt;
    private @Nullable String unit;

    public PropertyBuilder()
    {
    }

    public Property build()
    {
        Property prop = null;

        if (type == null)
        {
            throw new ImplementationError("property type has not been set before building");
        }
        if (name == null)
        {
            throw new ImplementationError("property name has not been set before building");
        }
        if (key == null)
        {
            throw new ImplementationError("property key has not been set before building");
        }
        switch (type)
        {
            case REGEX:
                if (value == null)
                {
                    throw new ImplementationError(
                        "trying to build a property of type " + type + " but the value was not set before building"
                    );
                }
                prop = new RegexProperty(name, key, value, internal, info, unit, dflt);
                break;
            case SYMBOL:
                prop = new RegexProperty(name, key, buildValuesEnumRegex(), internal, info, unit, dflt);
                break;
            case BOOLEAN:
                prop = new BooleanProperty(name, key, internal, info, unit, dflt);
                break;
            case BOOLEAN_TRUE_FALSE:
                prop = new BooleanTrueFalseProperty(name, key, internal, info, unit, dflt);
                break;
            case RANGE:
                if (min == null)
                {
                    throw new ImplementationError(
                        "trying to build a property of type " + type + " but the min value was not set before building"
                    );
                }
                if (max == null)
                {
                    throw new ImplementationError(
                        "trying to build a property of type " + type + " but the max value was not set before building"
                    );
                }
                prop = new RangeProperty(name, key, min, max, internal, info, unit, dflt);
                break;
            case RANGE_FLOAT:
                if (minFloat == null)
                {
                    throw new ImplementationError(
                        "trying to build a property of type " + type +
                            " but the minFloat value was not set before building"
                    );
                }
                if (maxFloat == null)
                {
                    throw new ImplementationError(
                        "trying to build a property of type " + type +
                            " but the maxFloat value was not set before building"
                    );
                }
                prop = new RangeFloatProperty(name, key, minFloat, maxFloat, internal, info, unit, dflt);
                break;
            case STRING:
                prop = new StringProperty(name, key, internal, info, unit, dflt);
                break;
            case NUMERIC_OR_SYMBOL:
                if (min == null)
                {
                    throw new ImplementationError(
                        "trying to build a property of type " + type + " but the min value was not set before building"
                    );
                }
                if (max == null)
                {
                    throw new ImplementationError(
                        "trying to build a property of type " + type + " but the max value was not set before building"
                    );
                }
                prop = new NumericOrSymbolProperty(
                    name, key, min, max, buildValuesEnumRegex(), internal, info, unit, dflt
                );
                break;
            case LONG:
                prop = new LongProperty(name, key, internal, info, unit, dflt);
                break;
            default:
                throw new ImplementationError("Unknown property type: " + type, null);
        }
        return prop;
    }

    private String buildValuesEnumRegex()
    {
        if (values == null)
        {
            throw new ImplementationError(
                "trying to build a property of type " + type + " but values was not set before building"
            );
        }
        StringBuilder symbolValue = new StringBuilder("(?:");
        for (String val : values)
        {
            symbolValue.append(val).append("|");
        }
        symbolValue.setLength(symbolValue.length() - 1);
        symbolValue.append(")");
        return symbolValue.toString();
    }

    public PropertyBuilder name(String nameRef)
    {
        name = nameRef;
        return this;
    }

    public PropertyBuilder keyRef(String... keyRef)
    {
        List<String> keyList = new ArrayList<>();
        for (String keyPart : keyRef)
        {
            try
            {
                keyList.add(
                    (String) ApiConsts.class.getField(keyPart).get(null)
                );
            }
            catch (Exception exc)
            {
                throw new LinStorRuntimeException(
                    "Failed to generate whitelist for properties.",
                    "An error occurred while resolving the API property key: '" + keyPart,
                    null, // cause
                    null, // correction
                    "Rule name: " + name + ".\nOrig keys: " + Arrays.toString(keyRef),
                    exc
                );
            }
        }
        key = keyList.stream().collect(Collectors.joining(ReadOnlyProps.PATH_SEPARATOR));
        return this;
    }

    public PropertyBuilder keyStr(String keyStr)
    {
        key = keyStr;
        return this;
    }

    public PropertyBuilder type(String typeRef)
    {
        type = PropertyType.valueOfIgnoreCase(typeRef);
        if (type == null)
        {
            throw new NullPointerException("Unknown type '" + typeRef + "'");
        }
        return this;
    }

    public PropertyBuilder value(String valueRef)
    {
        value = valueRef;
        return this;
    }

    public PropertyBuilder internal(String internalRef)
    {
        internal = "true".equalsIgnoreCase(internalRef);
        return this;
    }

    public PropertyBuilder info(String infoRef)
    {
        info = infoRef;
        return this;
    }

    public PropertyBuilder values(String... valuesRef)
    {
        values = valuesRef;
        return this;
    }

    public PropertyBuilder max(String maxStr)
    {
        max = Long.parseLong(maxStr);
        return this;
    }

    public PropertyBuilder min(String minStr)
    {
        min = Long.parseLong(minStr);
        return this;
    }

    public PropertyBuilder max_float(String maxStr)
    {
        maxFloat = Double.parseDouble(maxStr);
        return this;
    }

    public PropertyBuilder min_float(String minStr)
    {
        minFloat = Double.parseDouble(minStr);
        return this;
    }

    public PropertyBuilder dflt(String dfltStr)
    {
        dflt = dfltStr;
        return this;
    }

    public PropertyBuilder unit(String unitRef)
    {
        unit = unitRef;
        return this;
    }

    public PropertyBuilder drbd_res_file_section(String section)
    {
        // TODO ignore for now, rework of properties needed: see #610
        return this;
    }
}
