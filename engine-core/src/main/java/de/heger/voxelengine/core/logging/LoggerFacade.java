package de.heger.voxelengine.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple logging facade wrapping SLF4J.
 */
public class LoggerFacade {
    private final Logger logger;

    private LoggerFacade(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    /**
     * Obtain a LoggerFacade for the given class.
     * @param clazz the class requesting a logger
     * @return a LoggerFacade instance
     */
    public static LoggerFacade get(Class<?> clazz) {
        return new LoggerFacade(clazz);
    }

    public void trace(String msg, Object... args) {
        logger.trace(msg, args);
    }

    public void debug(String msg, Object... args) {
        logger.debug(msg, args);
    }

    public void info(String msg, Object... args) {
        logger.info(msg, args);
    }

    public void warn(String msg, Object... args) {
        logger.warn(msg, args);
    }

    public void error(String msg, Object... args) {
        logger.error(msg, args);
    }

    public void error(String msg, Throwable t) {
        logger.error(msg, t);
    }
}
