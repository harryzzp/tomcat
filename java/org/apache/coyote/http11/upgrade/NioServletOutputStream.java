/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.http11.upgrade;

import java.io.IOException;
import java.nio.channels.Selector;

import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioSelectorPool;
import org.apache.tomcat.util.net.SocketWrapper;

public class NioServletOutputStream extends AbstractServletOutputStream {

    private final NioChannel nioChannel;
    private final NioSelectorPool pool;
    private final int maxWrite;


    public NioServletOutputStream(
            SocketWrapper<NioChannel> wrapper, NioSelectorPool pool) {
        nioChannel = wrapper.getSocket();
        this.pool = pool;
        maxWrite = nioChannel.getBufHandler().getWriteBuffer().capacity();
    }


    @Override
    protected int doWrite(boolean block, byte[] b, int off, int len)
            throws IOException {
        int leftToWrite = len;
        int count = 0;
        int offset = off;

        while (leftToWrite > 0) {
            int writeThisLoop;
            int writtenThisLoop;

            if (leftToWrite > maxWrite) {
                writeThisLoop = maxWrite;
            } else {
                writeThisLoop = leftToWrite;
            }

            writtenThisLoop = doWriteInternal(block, b, offset, writeThisLoop);
            count += writtenThisLoop;
            leftToWrite -= writtenThisLoop;

            if (writtenThisLoop < writeThisLoop) {
                break;
            }
        }

        return count;
    }

    private int doWriteInternal (boolean block, byte[] b, int off, int len)
            throws IOException {
        nioChannel.getBufHandler().getWriteBuffer().clear();
        nioChannel.getBufHandler().getWriteBuffer().put(b, off, len);
        nioChannel.getBufHandler().getWriteBuffer().flip();

        int written = 0;
        NioEndpoint.KeyAttachment att =
                (NioEndpoint.KeyAttachment) nioChannel.getAttachment(false);
        if (att == null) {
            throw new IOException("Key must be cancelled");
        }
        long writeTimeout = att.getTimeout();
        Selector selector = null;
        try {
            selector = pool.get();
        } catch ( IOException x ) {
            //ignore
        }
        try {
            written = pool.write(nioChannel.getBufHandler().getWriteBuffer(),
                    nioChannel, selector, writeTimeout, block);
        } finally {
            if (selector != null) {
                pool.put(selector);
            }
        }
        return written;
    }


    @Override
    protected void doFlush() throws IOException {
        NioEndpoint.KeyAttachment att =
                (NioEndpoint.KeyAttachment) nioChannel.getAttachment(false);
        if (att == null) {
            throw new IOException("Key must be cancelled");
        }
        long writeTimeout = att.getTimeout();
        Selector selector = null;
        try {
            selector = pool.get();
        } catch ( IOException x ) {
            //ignore
        }
        try {
            do {
                if (nioChannel.flush(true, selector, writeTimeout)) {
                    break;
                }
            } while (true);
        } finally {
            if (selector != null) {
                pool.put(selector);
            }
        }
    }
}
