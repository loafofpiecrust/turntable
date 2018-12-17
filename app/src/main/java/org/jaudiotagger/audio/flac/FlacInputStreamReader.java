package org.jaudiotagger.audio.flac;

import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.logging.ErrorMessage;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;

public class FlacInputStreamReader
{
    public static Logger logger = Logger.getLogger("org.jaudiotagger.audio.flac");

    public static final int FLAC_STREAM_IDENTIFIER_LENGTH = 4;
    public static final String FLAC_STREAM_IDENTIFIER = "fLaC";

    private FileChannel raf;
    private int startOfFlacInFile;
    public long currentPosition = 0;

    /**
     * Create instance for holding stream info
     * @param raf
     */
    public FlacInputStreamReader(FileChannel raf)
    {
        this.raf = raf;
    }

    /**
     * Reads the stream block to ensure it is a flac file
     *
     * @throws IOException
     * @throws CannotReadException
     */
    public void findStream() throws IOException, CannotReadException
    {
        //Begins tag parsing
        if (raf.size() == 0)
        {
            //Empty File
            throw new CannotReadException("Error: File empty");
        }
//        raf.reset();
//        currentPosition = 0;
        raf.position(0);

        //FLAC Stream at start
        if (isFlacHeader())
        {
            startOfFlacInFile = 0;
            return;
        }

        //Ok maybe there is an ID3v24tag first
        if (isId3v2Tag())
        {
            startOfFlacInFile = (int) (raf.position() - FLAC_STREAM_IDENTIFIER_LENGTH);
            return;
        }
        throw new CannotReadException(ErrorMessage.FLAC_NO_FLAC_HEADER_FOUND.getMsg());
    }

    private boolean isId3v2Tag() throws IOException
    {
        raf.position(0);
        if(AbstractID3v2Tag.isId3Tag(raf))
        {
            logger.warning(ErrorMessage.FLAC_CONTAINS_ID3TAG.getMsg(currentPosition));
            //FLAC Stream immediately after end of id3 tag
            if (isFlacHeader())
            {
                return true;
            }
        }
        return false;
    }

    private boolean isFlacHeader() throws IOException
    {
        //FLAC Stream at start
        byte[] b = new byte[FLAC_STREAM_IDENTIFIER_LENGTH];
        ByteBuffer bb = ByteBuffer.wrap(b);
        raf.read(bb);
        String flac = new String(b);
        return flac.equals(FLAC_STREAM_IDENTIFIER);
    }

    /**
     * Usually flac header is at start of file, but unofficially and ID3 tag is allowed at the start of the file.
     *
     * @return the start of the Flac within file
     */
    public int getStartOfFlacInFile()
    {
        return startOfFlacInFile;
    }
}
