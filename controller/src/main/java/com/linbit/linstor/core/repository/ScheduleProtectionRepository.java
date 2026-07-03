package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.CoreModule.ScheduleMap;
import com.linbit.linstor.core.identifier.ScheduleName;
import com.linbit.linstor.core.objects.Schedule;

import javax.inject.Inject;
import javax.inject.Singleton;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Singleton
public class ScheduleProtectionRepository implements ScheduleRepository
{
    private final CoreModule.ScheduleMap scheduleMap;

    // can't initialize objProt in constructor because of chicken-egg-problem
    @SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Inject
    public ScheduleProtectionRepository(CoreModule.ScheduleMap scheduleMapRef)
    {
        scheduleMap = scheduleMapRef;
    }

    public void setObjectProtection()
    {
        if (scheduleMapObjProt != null)
        {
            throw new IllegalStateException("Object protection already set");
        }
    }

    @SuppressFBWarnings("UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Override
    public void requireAccess(AccessType requested)
    {
        // suppressed spotbugs-warning because checkProtSet() does exactly what is needed
        checkProtSet();
    }

    @Override
    public @Nullable Schedule get(ScheduleName scheduleNameRef)
    {
        checkProtSet();
        return scheduleMap.get(scheduleNameRef);
    }

    @Override
    public void put(Schedule scheduleRef)
    {
        checkProtSet();
        scheduleMap.put(scheduleRef.getName(), scheduleRef);
    }

    @Override
    public void remove(ScheduleName scheduleNameRef)
    {
        checkProtSet();
        scheduleMap.remove(scheduleNameRef);
    }

    @Override
    public ScheduleMap getMapForView()
    {
        checkProtSet();
        return scheduleMap;
    }

    private void checkProtSet()
    {
        if (scheduleMapObjProt == null)
        {
            throw new IllegalStateException("Object protection not yet set");
        }
    }
}
