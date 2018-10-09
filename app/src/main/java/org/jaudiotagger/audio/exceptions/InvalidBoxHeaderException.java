package org.jaudiotagger.audio.exceptions;

/**
 * Thrown if when trying to read box uuid the length doesn't make any sense
 */
public class InvalidBoxHeaderException extends RuntimeException
{
    public InvalidBoxHeaderException(String message)
    {
        super(message);
    }
}
