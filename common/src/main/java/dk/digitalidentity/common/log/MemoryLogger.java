package dk.digitalidentity.common.log;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MemoryLogger {

	public void logMemoryMetrics() {
		MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

		StringBuilder sb = new StringBuilder();
		sb.append("\n========== Memory Report ==========\n");

		// heap memory
		MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
		sb.append("HEAP: Used=").append(formatBytes(heapUsage.getUsed()));
		sb.append(", Committed=").append(formatBytes(heapUsage.getCommitted()));
		sb.append(", Max=").append(formatBytes(heapUsage.getMax()));
		sb.append(" (").append(getPercentage(heapUsage.getUsed(), heapUsage.getMax())).append("% used)\n");

		// non-heap memory
		MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
		sb.append("NON-HEAP: Used=").append(formatBytes(nonHeapUsage.getUsed()));
		sb.append(", Committed=").append(formatBytes(nonHeapUsage.getCommitted()));
		sb.append(", Max=").append(formatBytes(nonHeapUsage.getMax())).append("\n");

		log.info("Memory usage: " + sb.toString());
	}

	private static String formatBytes(long bytes) {
		if (bytes == -1) {
			return "undefined";
		}

		return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
	}

	private static String getPercentage(long used, long max) {
		if (max <= 0) {
			return "N/A";
		}

		return String.format("%.1f", (100.0 * used) / max);
	}
}
