/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 * <p>
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
     * @throws IOException Thrown if IO errors occurred.
     */
    @Override
    public void close() throws IOException {
        // intentionally ignored;
        // Use forceClose() when needed to close
    }

    /**
     * Closes the stream forcefully.
     * 
     * @throws IOException Thrown if IO errors occurred.
     */
    public void forceClose() throws IOException {
        if (innerStream != null) {
            innerStream.close();
            innerStream = null;
        }
    }
}
