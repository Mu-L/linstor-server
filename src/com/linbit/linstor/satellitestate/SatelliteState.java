package com.linbit.linstor.satellitestate;

import com.linbit.linstor.ResourceName;
import com.linbit.linstor.VolumeNumber;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SatelliteState
{
    private final Map<ResourceName, SatelliteResourceState> resourceStates = new HashMap<>();

    public Map<ResourceName, SatelliteResourceState> getResourceStates()
    {
        return resourceStates;
    }

    public <T> T getFromResource(ResourceName resourceName, Function<SatelliteResourceState, T> getter)
    {
        return resourceStates.containsKey(resourceName) ?
            getter.apply(resourceStates.get(resourceName)) :
            null;
    }

    public <T> void setOnResource(ResourceName resourceName, BiConsumer<SatelliteResourceState, T> setter, T value)
    {
        resourceStates.computeIfAbsent(resourceName, ignored -> new SatelliteResourceState());
        setter.accept(resourceStates.get(resourceName), value);
    }

    public <T> void unsetOnResource(
        ResourceName resourceName,
        BiConsumer<SatelliteResourceState, T> setter
    )
    {
        SatelliteResourceState resourceState = resourceStates.get(resourceName);
        if (resourceState != null)
        {
            setter.accept(resourceState, null);

            if (resourceState.isEmpty())
            {
                resourceStates.remove(resourceName);
            }
        }
    }

    public <T> T getFromVolume(
        ResourceName resourceName,
        VolumeNumber volumeNumber,
        Function<SatelliteVolumeState, T> getter
    )
    {
        T value;

        SatelliteResourceState resourceState = resourceStates.get(resourceName);
        if (resourceState == null)
        {
            value = null;
        }
        else
        {
            value = resourceState.getFromVolume(volumeNumber, getter);
        }

        return value;
    }

    public <T> void setOnVolume(
        ResourceName resourceName,
        VolumeNumber volumeNumber,
        BiConsumer<SatelliteVolumeState, T> setter,
        T value
    )
    {
        resourceStates.computeIfAbsent(resourceName, ignored -> new SatelliteResourceState());
        resourceStates.get(resourceName).setOnVolume(volumeNumber, setter, value);
    }

    public <T> void unsetOnVolume(
        ResourceName resourceName,
        VolumeNumber volumeNumber,
        BiConsumer<SatelliteVolumeState, T> setter
    )
    {
        SatelliteResourceState resourceState = resourceStates.get(resourceName);
        if (resourceState != null)
        {
            resourceState.unsetOnVolume(volumeNumber, setter);

            if (resourceState.isEmpty())
            {
                resourceStates.remove(resourceName);
            }
        }
    }
}
