package de.heger.voxelengine.core.utils;

import de.heger.voxelengine.core.logging.LoggerFacade;

/**
 * Argument- and state-checking helpers for failing fast with clear messages.
 *
 * <p>These static methods let a method validate its inputs in one line and
 * throw a descriptive exception when an input is wrong, instead of failing
 * with a confusing null-pointer exception further down the call stack.</p>
 */
public class Validate {

    private static final LoggerFacade logger = LoggerFacade.get(Validate.class);
    /**
     * Checks if the given object is null and throws an exception if it is.
     *
     * @param obj The object to check.
     * @param message The error message to include in the exception.
     * @throws NullPointerException if the object is null.
     */
    public static void notNull(Object obj, String message) {
        if (obj == null) {
            logger.error(message);
            throw new NullPointerException(message);
        }
    }   

    /**
     * Checks if the given string is null or empty and throws an exception if it is.
     *
     * @param str The string to check.
     * @param message The error message to include in the exception.
     * @throws IllegalArgumentException if the string is null or empty.
     */
    public static void notEmpty(String str, String message) {
        if (str == null || str.isEmpty()) {
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }
}