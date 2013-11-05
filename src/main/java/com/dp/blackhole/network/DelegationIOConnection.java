package com.dp.blackhole.network;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DelegationIOConnection implements NonblockingConnection<TransferWrap> {

    public static class DelegationIOConnectionFactory implements ConnectionFactory<DelegationIOConnection> {

        @Override
        public DelegationIOConnection makeConnection(SocketChannel channel, Selector selector, TypedFactory wrappedFactory) {
            return new DelegationIOConnection(channel, selector, wrappedFactory);
        }
    }
    
    public static final Log LOG = LogFactory.getLog(DelegationIOConnection.class);
    
    private Selector selector;
    private SocketChannel channel;
    private AtomicBoolean active;
    
    private ConcurrentLinkedQueue<TransferWrap> writeQueue;
    private TransferWrap readBuf;
    private boolean readComplete;
    private boolean writeComplete;
  
    private TypedFactory wrappedFactory;

    public DelegationIOConnection(SocketChannel channel, Selector selector, TypedFactory wrappedFactory) {
        this.channel = channel;
        writeQueue = new ConcurrentLinkedQueue<TransferWrap>();
        active = new AtomicBoolean(true);
        this.selector = selector;
        this.wrappedFactory = wrappedFactory;
    }
    
    @Override
    public void send(TransferWrap entity) {
        if (!isActive()) {
            LOG.error("connection closed, message sending abort");
            return;
        }
        
        SelectionKey key = keyFor(selector);
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        offer(entity);
        selector.wakeup();
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    @Override
    public SocketChannel getChannel() {
        return channel;
    }

    @Override
    public SelectionKey keyFor(Selector selector) {
        return channel.keyFor(selector);
    }

    private void offer(TransferWrap buffer) {
        writeQueue.offer(buffer);
    }

    @Override
    public void readyforRead() {
        readComplete = false;
        readBuf = null;
    }    
    
    @Override
    public int read() throws IOException {
        if (readBuf == null) {
            readBuf = new TransferWrap(wrappedFactory);
        }
        int read = readBuf.read(channel);
        if (readBuf.complete()) {
            readComplete = true;
        }
        return read;
    }

    @Override
    public boolean readComplete() {
        return readComplete;
    }

    @Override
    public int write() throws IOException {
        int written = 0;
        writeComplete = false;
        while (true) {
            TransferWrap messageTosend = writeQueue.peek();
            // end of queue
            if (messageTosend == null) {
                writeComplete = true;
                break;
            }
            // finish one buffer
            if (messageTosend.complete()) {
                writeQueue.poll();
                continue;
            }
            // start to write one buffer until socket writebuffer full
            written += messageTosend.write(channel);
        }
        return written;
    }

    @Override
    public boolean writeComplete() {
        return writeComplete;
    }

    @Override
    public TransferWrap getEntity() {
        return readBuf;
    }

    @Override
    public void close() {
        active.getAndSet(false);
        if (!channel.isOpen()) {
            return;
        }
        try {
            channel.socket().shutdownOutput();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            channel.socket().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
