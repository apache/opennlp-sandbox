package opennlp.tools.dl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

/**
 * This class offers a wrapper for {@link InputStream};
 * The only sole purpose of this wrapper is to bypass the close calls that are usually
 * propagated from the readers.
 * A use case of this wrapper is for reading multiple files from the {@link java.util.zip.ZipInputStream},
 * especially because the tools like {@link org.apache.commons.io.IOUtils#copy(Reader, Writer)}
 * and {@link org.nd4j.linalg.factory.Nd4j#read(InputStream)} automatically close the input stream.
 *
 * Note:
 *  1. this tool ignores the call to {@link #close()} method
 *  2. Remember to call {@link #forceClose()} when the stream when the inner stream needs to be closed
 *  3. This wrapper doesn't hold any resources. If you close the innerStream, you can safely ignore closing this wrapper
 *
 * @author Thamme Gowda (thammegowda@apache.org)
 */
public class UnclosableInputStream extends InputStream {

    private InputStream innerStream;

    public UnclosableInputStream(InputStream stream){
        this.innerStream = stream;
    }

    @Override
    public int read() throws IOException {
        return innerStream.read();
    }

    /**
     * NOP - Does not close the stream - intentional
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        // intentionally ignored;
        // Use forceClose() when needed to close
    }

    /**
     * Closes the stream
     * @throws IOException
     */
    public void forceClose() throws IOException {
        if (innerStream != null) {
            innerStream.close();
            innerStream = null;
        }
    }
}
