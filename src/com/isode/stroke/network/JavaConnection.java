/*
 * Copyright (c) 2010 Remko Tronçon
 * All rights reserved.
 */
/*
 * Copyright (c) 2010-2013, Isode Limited, London, England.
 * All rights reserved.
 */
package com.isode.stroke.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.isode.stroke.base.ByteArray;
import com.isode.stroke.eventloop.Event.Callback;
import com.isode.stroke.eventloop.EventLoop;
import com.isode.stroke.eventloop.EventOwner;

public class JavaConnection extends Connection implements EventOwner {

    private class Worker implements Runnable {

        private final HostAddressPort address_;
        private final List<ByteArray> writeBuffer_ = Collections.synchronizedList(new ArrayList<ByteArray>());

        public Worker(HostAddressPort address) {
            address_ = address;
        }
        
        private boolean isWriteNeeded() {
            return (!writeBuffer_.isEmpty());
        }

        public void run() {
            try {
                try {
                    socketChannel_ = SocketChannel.open(
                            new InetSocketAddress(address_.getAddress().getInetAddress(),address_.getPort()));                
                    /* By default, SocketChannels start off in blocking mode, which
                     * isn't what we want
                     */
                    socketChannel_.configureBlocking(false);
                    selector_ = Selector.open();
                    selectionKey_ = socketChannel_.register(selector_,  SelectionKey.OP_READ);
                } catch (IOException ex) {
                    handleConnected(true);
                    return;
                }
                handleConnected(false);
                while (!disconnecting_) {

                    /* This will block until something is ready on the selector,
                     * including someone calling selector.wakeup(), or until the
                     * thread is interrupted
                     */
                    try {
                        selector_.select();
                    } catch (IOException e) {
                        disconnected_ = true;
                        handleDisconnected(null);
                        break;
                    }

                    /* Something(s) happened.  See what needs doing */
                    if (disconnecting_) {
                        handleDisconnected(null);
                        /* No point doing anything else */
                        break;
                    }
                    boolean writeNeeded = isWriteNeeded();
                    boolean readNeeded = selectionKey_.isReadable();
                    
                    { /* Handle any writing */
                        if (writeNeeded) {
                            try {
                                doWrite();
                            }
                            catch (IOException e) {
                                disconnecting_ = true;
                                handleDisconnected(Error.WriteError);                                                
                            }
                        }
                    }

                    { /* Handle any reading */
                        ByteArray dataRead;

                        if (readNeeded) {
                            try {                            
                                dataRead = doRead();
                                if (!dataRead.isEmpty()) {
                                    handleDataRead(dataRead);
                                }
                            } catch (IOException ex) {
                                handleDisconnected(Error.ReadError);
                                return;
                            }
                        }
                    }
                    
                    if (isWriteNeeded() && !disconnected_) {
                        /* There is something that's not been written yet.
                         * This might happen because the "doWrite()" didn't
                         * write the complete buffer, or because our "write()" 
                         * method was called (perhaps more than once) since 
                         * this thread was woken.
                         * 
                         * Give the buffer a chance to empty
                         */
                        try {
                            Thread.sleep(100);
                        }
                        catch (InterruptedException e) {
                            /* */
                        }
                        /* Force the next iteration of the loop to wake up
                         * straight away, and check all conditions again
                         */
                        selector_.wakeup();
                    }
                }            
                handleDisconnected(null);
            } finally {
                if(socketChannel_ != null) {
                    try {
                        socketChannel_.close();                        
                    } catch (IOException ex) {
                        /* Do we need to return an error if we're already trying to close? */
                    }
                    if(selector_ != null) {
                        try {
                            selector_.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }

        /**
         * Called when there's something in the writeBuffer to be written.
         * Will remove from writeBuffer_ anything that got written.
         * @throws IOException if an error occurs when trying to write to the
         * socket
         */
        private void doWrite() throws IOException {
            if (!isWriteNeeded()) {
                return;
            }

            ByteArray data = writeBuffer_.get(0);
            byte[] bytes = data.getData();
            int bytesToWrite = bytes.length;

            if (bytesToWrite == 0) {
                /*
                 * Not sure if this can happen, but does no harm to check
                 */
                writeBuffer_.remove(0);
                return;
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

            /*
             * Because the SocketChannel is non-blocking, we have to
             * be prepared to cope with the write operation not
             * consuming all (or any!) of the data
             */
            boolean finishedWriting = false;
            int bytesWritten = socketChannel_.write(byteBuffer);
            finishedWriting = (byteBuffer.remaining() == 0);
            if (finishedWriting) {
                writeBuffer_.remove(0);
                return;
            }
            /* Was anything written at all? */
            if (bytesWritten == 0) {
                /* Leave the buffer in the array so that it'll get tried
                 * again later
                 */
                return;
            }

            /* The buffer was *partly* written.  This means we have to
             * remove that part.  We do this by creating a new ByteArray
             * with the remaining bytes in, and replacing the first 
             * element in the list with that.
             */
            byte[] remainingBytes = new byte[bytesToWrite - bytesWritten];
            System.arraycopy(bytes, bytesWritten,remainingBytes,0, remainingBytes.length);
            ByteArray leftOver = new ByteArray(remainingBytes);

            writeBuffer_.set(0, leftOver);
            return;
        }
        
        /**
         * Called when there's something that's come in on the socket
         * @return a ByteBuffer containing bytes read (may be empty, won't be null)
         * @throws IOException if the socket got closed
         */
        private ByteArray doRead() throws IOException {

            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            ByteArray data = new ByteArray();

            int count = socketChannel_.read(byteBuffer);
            if (count == 0) {
                return data;
            }
            while (count > 0) {
                byteBuffer.flip();
                byte[] result = new byte[byteBuffer.remaining()];
                byteBuffer.get(result);
                byteBuffer.compact();
                for (int i=0; i<result.length; i++) {
                    data.append(result[i]);
                }

                count = socketChannel_.read(byteBuffer);
            }
            if (count == -1) {
                /* socketChannel input has reached "end-of-stream", which
                 * we regard as meaning that the socket has been closed 
                 */
                throw new IOException("socketChannel_.read returned -1");
            }
            return data;
        }
        
        private void handleConnected(final boolean error) {

            eventLoop_.postEvent(new Callback() {
                public void run() {
                    onConnectFinished.emit(Boolean.valueOf(error));
                }
            });
        }

        private void handleDisconnected(final Error error) {
            if (!disconnected_) {
                disconnected_ = true;
                eventLoop_.postEvent(new Callback() {
                    public void run() {
                        onDisconnected.emit(error);
                    }
                });
            }
        }

        private void handleDataRead(final ByteArray data) {
            eventLoop_.postEvent(new Callback() {
                public void run() {
                    onDataRead.emit(data);
                }
            });
        }

    }

    private JavaConnection(EventLoop eventLoop) {
        eventLoop_ = eventLoop;
    }

    public static JavaConnection create(EventLoop eventLoop) {
        return new JavaConnection(eventLoop);
    }

    @Override
    public void listen() {
        //TODO: needed for server, not for client.
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void connect(HostAddressPort address) {
        worker_ = new Worker(address);
        Thread workerThread = new Thread(worker_);
        workerThread.setDaemon(true);
        workerThread.setName("JavaConnection "+ address.toString());
        workerThread.start();
    }

    @Override
    public void disconnect() {
        disconnecting_ = true;
        if (selector_ != null) {
            selector_.wakeup();
        }
    }

    @Override
    public void write(ByteArray data) {
        worker_.writeBuffer_.add(data);
        if (selector_ != null) {
            selector_.wakeup();
        }

    }

    @Override
    public HostAddressPort getLocalAddress() {
        if (socketChannel_ == null) {
            return null;
        }
        Socket socket = socketChannel_.socket();
        if (socket == null) {
            return null;
        }
        return new HostAddressPort(new HostAddress(socket.getLocalAddress()), socket.getLocalPort());        
    }
    
    @Override
    public String toString()
    {
        return "JavaConnection " + 
        (socketChannel_ == null ? "with no socket configured" : "for " + getLocalAddress()) +
        (disconnecting_ ? " (disconnecting)" : "");
    }
    
    private final EventLoop eventLoop_;
    private boolean disconnecting_ = false;
    private boolean disconnected_ = false;
    private SocketChannel socketChannel_;
    private Selector selector_;
    private SelectionKey selectionKey_;
    private Worker worker_;

}
