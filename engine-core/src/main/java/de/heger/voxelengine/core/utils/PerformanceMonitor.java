package de.heger.voxelengine.core.utils;

import de.heger.voxelengine.core.logging.LoggerFacade;

/**
 * Utility class for monitoring performance metrics and memory usage.
 * Provides efficient memory monitoring without causing additional allocations.
 */
public class PerformanceMonitor {
    
    private static final LoggerFacade logger = LoggerFacade.get(PerformanceMonitor.class);
    
    private static final Runtime runtime = Runtime.getRuntime();
    private static final long MB = 1024 * 1024;
    
    // Singleton instance
    private static final PerformanceMonitor INSTANCE = new PerformanceMonitor();
    
    // Performance tracking fields
    private long lastGcTime = 0;
    private long lastMemoryCheck = 0;
    private long memoryCheckInterval = 5000; // 5 seconds
    
    // Memory statistics
    private long maxMemoryUsed = 0;
    private long totalAllocations = 0;
    private long lastTotalMemory = 0;
    
    private PerformanceMonitor() {
        // Private constructor for singleton
    }
    
    public static PerformanceMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Checks current memory usage and logs if significant changes occurred.
     * This method is designed to be called frequently without significant overhead.
     */
    public void checkMemoryUsage() {
        long currentTime = System.currentTimeMillis();
        
        // Only check memory periodically to avoid overhead
        if (currentTime - lastMemoryCheck < memoryCheckInterval) {
            return;
        }
        
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        // Track peak memory usage
        if (usedMemory > maxMemoryUsed) {
            maxMemoryUsed = usedMemory;
        }
        
        // Estimate allocations (rough approximation)
        if (lastTotalMemory > 0) {
            long memoryDiff = totalMemory - lastTotalMemory;
            if (memoryDiff > 0) {
                totalAllocations += memoryDiff;
            }
        }
        
        // Log memory statistics
        logger.debug("Memory - Used: {}MB, Free: {}MB, Total: {}MB, Max: {}MB, Peak: {}MB", 
                    usedMemory / MB, freeMemory / MB, totalMemory / MB, 
                    maxMemory / MB, maxMemoryUsed / MB);
        
        // Warning if memory usage is high
        double memoryUsagePercentage = (double) usedMemory / maxMemory * 100;
        if (memoryUsagePercentage > 80) {
            logger.warn("High memory usage detected: {:.1f}% ({} MB / {} MB)", 
                       memoryUsagePercentage, usedMemory / MB, maxMemory / MB);
        }
        
        lastMemoryCheck = currentTime;
        lastTotalMemory = totalMemory;
    }
    
    /**
     * Suggests garbage collection if memory usage is high.
     * Should be used sparingly as it may impact performance.
     */
    public void suggestGC() {
        long currentTime = System.currentTimeMillis();
        
        // Don't suggest GC too frequently
        if (currentTime - lastGcTime < 10000) { // 10 seconds minimum
            return;
        }
        
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        double memoryUsagePercentage = (double) usedMemory / maxMemory * 100;
        
        if (memoryUsagePercentage > 75) {
            logger.info("Suggesting garbage collection due to high memory usage: {:.1f}%", memoryUsagePercentage);
            System.gc();
            lastGcTime = currentTime;
            
            // Check memory again after GC
            totalMemory = runtime.totalMemory();
            freeMemory = runtime.freeMemory();
            usedMemory = totalMemory - freeMemory;
            double newUsagePercentage = (double) usedMemory / maxMemory * 100;
            logger.info("Memory usage after GC: {:.1f}% (freed {:.1f}%)", 
                       newUsagePercentage, memoryUsagePercentage - newUsagePercentage);
        }
    }
    
    /**
     * Gets current memory usage as a percentage of maximum available memory.
     */
    public double getMemoryUsagePercentage() {
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        return (double) usedMemory / maxMemory * 100;
    }
    
    /**
     * Gets current used memory in MB.
     */
    public long getUsedMemoryMB() {
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return (totalMemory - freeMemory) / MB;
    }
    
    /**
     * Gets peak memory usage in MB.
     */
    public long getPeakMemoryUsageMB() {
        return maxMemoryUsed / MB;
    }
    
    /**
     * Gets estimated total allocations in MB (rough approximation).
     */
    public long getTotalAllocationsMB() {
        return totalAllocations / MB;
    }
    
    /**
     * Resets performance statistics.
     */
    public void reset() {
        maxMemoryUsed = 0;
        totalAllocations = 0;
        lastTotalMemory = 0;
        lastGcTime = 0;
        lastMemoryCheck = 0;
    }
    
    /**
     * Sets the interval for memory checks in milliseconds.
     */
    public void setMemoryCheckInterval(long intervalMs) {
        this.memoryCheckInterval = intervalMs;
    }
} 