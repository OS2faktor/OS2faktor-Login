package dk.digitalidentity.common.task;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SchedulingTaskRescheduler {

	@Autowired
	private ScheduledTaskHolder scheduledTaskHolder;

    @Autowired
    private TaskScheduler taskScheduler;

	@Autowired
	private BeanFactory beanFactory;

	@Autowired
	private Environment environment;

	@EventListener(ApplicationReadyEvent.class)
    public void addFuzz() {
        for (ScheduledTask scheduledTask : scheduledTaskHolder.getScheduledTasks()) {
            Task task = scheduledTask.getTask();
            if (!(task instanceof CronTask)) {
                continue;
            }

            Runnable runnable = task.getRunnable();
            ScheduledMethodRunnable smr = unwrapRunnable(runnable);
            if (smr == null) {
            	continue;
            }

            Method method = smr.getMethod();
            if (!method.isAnnotationPresent(Scheduled.class)) {
            	continue;
            }

            Scheduled info = method.getAnnotation(Scheduled.class);
            String newCron = resolveExpression(info.cron());

            log.debug("Rescheduling {} to cron: {}", method.getName(), newCron);

            scheduledTask.cancel(false);

            CronTask newCronTask = new CronTask(runnable, newCron);
            ScheduledFuture<?> newFuture = taskScheduler.schedule(newCronTask.getRunnable(), newCronTask.getTrigger());

            try {
                Field futureField = ScheduledTask.class.getDeclaredField("future");
                futureField.setAccessible(true);
                futureField.set(scheduledTask, newFuture);
            }
            catch (NoSuchFieldException | IllegalAccessException ex) {
                log.warn("Could not replace future on ScheduledTask - new schedule is active but old handle is stale", ex);
            }
        }
    }

	// spring wraps our runnable in some auto-generated wrapper, so we need to
	// unwrap till we hit the ScheduledMethodRunnable
	private ScheduledMethodRunnable unwrapRunnable(Runnable runnable) {
		if (runnable instanceof ScheduledMethodRunnable smr) {
			return smr;
		}

		try {
			Field delegateField = runnable.getClass().getDeclaredField("runnable");
			delegateField.setAccessible(true);
			Object delegate = delegateField.get(runnable);

			if (delegate instanceof ScheduledMethodRunnable smr) {
				return smr;
			}

			if (delegate instanceof Runnable nestedRunnable) {
				return unwrapRunnable(nestedRunnable);
			}
		}
		catch (NoSuchFieldException | IllegalAccessException ex) {
			log.warn("Could not unwrap runnable of type {}", runnable.getClass().getName(), ex);
		}

		return null;
	}

	// this method allows us to expand #{new java.util.Random().nextInt(60)} into
	// something meaningful (it also supports placeholders)
	private String resolveExpression(String cron) {
		String resolved = environment.resolvePlaceholders(cron);

		StandardBeanExpressionResolver resolver = new StandardBeanExpressionResolver();
		BeanExpressionContext context = new BeanExpressionContext((ConfigurableBeanFactory) beanFactory, null);
		Object evaluated = resolver.evaluate(resolved, context);

		return evaluated != null ? evaluated.toString() : resolved;
	}
}