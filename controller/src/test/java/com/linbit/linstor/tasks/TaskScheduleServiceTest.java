package com.linbit.linstor.tasks;

import com.linbit.linstor.tasks.TaskScheduleService.Task;
import com.linbit.linstor.testutils.EmptyErrorReporter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the scheduling semantics of {@link TaskScheduleService}.
 *
 * Note: if the internal task map ever runs empty (all tasks returned {@link Task#END_TASK}), the
 * service thread currently dies with a NoSuchElementException from TreeMap.firstKey(). The
 * controller's real tasks never end, so the tests below always keep at least one live task
 * registered instead of asserting that behavior.
 */
public class TaskScheduleServiceTest
{
    private static final long LATCH_TIMEOUT = 10;
    /**
     * Timeout for awaits that are expected to elapse without the latch being counted down -
     * this is always fully waited out, so keep it short.
     */
    private static final long NEGATIVE_LATCH_TIMEOUT_MS = 1_000;

    private TaskScheduleService service;

    @Before
    public void setUp()
    {
        service = new TaskScheduleService(new EmptyErrorReporter());
    }

    @After
    public void tearDown() throws Exception
    {
        service.shutdown(false);
        service.awaitShutdown(TimeUnit.SECONDS.toMillis(LATCH_TIMEOUT));
    }

    /**
     * Task that counts down a latch on every run and reschedules itself with a fixed delay.
     */
    private static class CountingTask implements Task
    {
        private final CountDownLatch latch;
        private final long rescheduleDelay;
        private final AtomicInteger runCount = new AtomicInteger();

        CountingTask(CountDownLatch latchRef, long rescheduleDelayRef)
        {
            latch = latchRef;
            rescheduleDelay = rescheduleDelayRef;
        }

        @Override
        public long run(long scheduledAt)
        {
            runCount.incrementAndGet();
            latch.countDown();
            return rescheduleDelay < 0 ? END_TASK : scheduledAt + rescheduleDelay;
        }
    }

    @Test
    public void taskAddedBeforeStartRunsImmediately() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(1);
        service.addTask(new CountingTask(latch, 60_000));

        service.start();

        assertThat(latch.await(LATCH_TIMEOUT, TimeUnit.SECONDS)).isTrue();
        assertThat(service.isStarted()).isTrue();
    }

    @Test
    public void taskAddedAfterStartRunsImmediately() throws Exception
    {
        // keep-alive task so the internal task map never runs empty
        service.addTask(new CountingTask(new CountDownLatch(0), 60_000));
        service.start();

        CountDownLatch latch = new CountDownLatch(1);
        service.addTask(new CountingTask(latch, 60_000));

        assertThat(latch.await(LATCH_TIMEOUT, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void returnedDelayReschedulesTask() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(3);
        service.addTask(new CountingTask(latch, 20));

        service.start();

        assertThat(latch.await(LATCH_TIMEOUT, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void endTaskIsNotRescheduled() throws Exception
    {
        // keep-alive task so the service thread survives the ending task
        CountDownLatch keepAliveRan = new CountDownLatch(2);
        service.addTask(new CountingTask(keepAliveRan, 20));

        CountDownLatch endTaskRan = new CountDownLatch(1);
        CountingTask endTask = new CountingTask(endTaskRan, -1);
        service.addTask(endTask);

        service.start();

        assertThat(endTaskRan.await(LATCH_TIMEOUT, TimeUnit.SECONDS)).isTrue();
        // wait until the keep-alive task ran at least twice more, giving the scheduler
        // enough turns to prove the ended task is not executed again
        assertThat(keepAliveRan.await(LATCH_TIMEOUT, TimeUnit.SECONDS)).isTrue();
        assertThat(endTask.runCount.get()).isEqualTo(1);
    }

    @Test
    public void scheduledAtIsPassedToNextRun() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicLong firstScheduledAt = new AtomicLong();
        AtomicLong secondScheduledAt = new AtomicLong();
        service.addTask(new Task()
        {
            @Override
            public long run(long scheduledAt)
            {
                long next;
                if (latch.getCount() == 2)
                {
                    firstScheduledAt.set(scheduledAt);
                    next = scheduledAt + 25;
                }
                else
                {
                    secondScheduledAt.compareAndSet(0, scheduledAt);
                    next = scheduledAt + 60_000;
                }
                latch.countDown();
                return next;
            }
        });

        service.start();

        assertThat(latch.await(LATCH_TIMEOUT, TimeUnit.SECONDS)).isTrue();
        // the second execution must be scheduled exactly at firstScheduledAt + 25
        assertThat(secondScheduledAt.get()).isEqualTo(firstScheduledAt.get() + 25);
    }

    @Test
    public void rescheduleAtRunsFutureTaskEarlier() throws Exception
    {
        // keep-alive task so the internal task map never runs empty
        service.addTask(new CountingTask(new CountDownLatch(0), 60_000));

        CountDownLatch ranTwice = new CountDownLatch(2);
        // reschedules itself far in the future after the first run
        CountingTask farFutureTask = new CountingTask(ranTwice, TimeUnit.HOURS.toMillis(1));

        service.addTask(farFutureTask);
        service.start();

        // first execution happens immediately on start
        assertThat(ranTwice.await(NEGATIVE_LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(ranTwice.getCount()).isEqualTo(1);

        // pull the task from one hour in the future to "now"
        service.rescheduleAt(farFutureTask, 0);

        assertThat(ranTwice.await(LATCH_TIMEOUT, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void rescheduleAtWithNegativeDelayCancelsTask() throws Exception
    {
        CountDownLatch keepAliveRan = new CountDownLatch(3);
        service.addTask(new CountingTask(keepAliveRan, 20));

        CountDownLatch ran = new CountDownLatch(1);
        CountingTask task = new CountingTask(ran, TimeUnit.HOURS.toMillis(1));
        service.addTask(task);
        service.start();

        assertThat(ran.await(LATCH_TIMEOUT, TimeUnit.SECONDS)).isTrue();

        // cancel the far-future reschedule entirely
        service.rescheduleAt(task, -1);

        assertThat(keepAliveRan.await(LATCH_TIMEOUT, TimeUnit.SECONDS)).isTrue();
        assertThat(task.runCount.get()).isEqualTo(1);
    }

    @Test
    public void shutdownStopsService() throws Exception
    {
        service.addTask(new CountingTask(new CountDownLatch(0), 20));
        service.start();
        assertThat(service.isStarted()).isTrue();

        service.shutdown(false);
        service.awaitShutdown(TimeUnit.SECONDS.toMillis(LATCH_TIMEOUT));

        assertThat(service.isStarted()).isFalse();
    }

    @Test
    public void initializeIsCalledOnStart() throws Exception
    {
        CountDownLatch initialized = new CountDownLatch(1);
        service.addTask(new Task()
        {
            @Override
            public void initialize()
            {
                initialized.countDown();
            }

            @Override
            public long run(long scheduledAt)
            {
                return scheduledAt + 60_000;
            }
        });

        service.start();

        assertThat(initialized.await(LATCH_TIMEOUT, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void nextFutureRescheduleIsAlignedToInterval()
    {
        Task task = scheduledAt -> Task.END_TASK;

        long interval = 10_000;
        long before = System.currentTimeMillis();
        long scheduledAt = before - 29_000;
        long next = task.getNextFutureReschedule(scheduledAt, interval);
        long after = System.currentTimeMillis();

        // the result stays aligned to scheduledAt + X * interval and lies in the future
        assertThat((next - scheduledAt) % interval).isZero();
        assertThat(next).isGreaterThan(before);
        assertThat(next).isLessThanOrEqualTo(after + interval);
    }
}
