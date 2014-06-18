/*
 * The MIT License
 *
 * Copyright 2014 tim.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.mastfrog.acteur.sse;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.util.Checks;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.util.CharsetUtil;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.joda.time.DateTimeUtils;

/**
 * Receives objects representing server sent events, and publishes them to all
 * registered channels. Automatically used with SseActeur - just inject an
 * EventSink and use its publish method to publish events. In the case of per
 * user or per session EventSinks, write an Acteur that looks up (in a cache or
 * similar) the right EventSink, and include that in its state. Then use the
 * next one.
 *
 * @author Tim Boudreau
 */
@Singleton
public class EventSink {

    private final LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    private final AtomicLong count = new AtomicLong();
    private final MessageRenderer ren;
    private final Set<Channel> channels = Sets.newConcurrentHashSet();
    private volatile boolean shutdown;
    private volatile Thread thread;
    private final ByteBufAllocator alloc;
    private final ApplicationControl ctrl;
    private final Runner runner = new Runner();
    private final Shutdown shutdownRun = new Shutdown();

    @Inject
    protected EventSink(MessageRenderer ren, @Named(ServerModule.BACKGROUND_THREAD_POOL_NAME) ExecutorService svc, ByteBufAllocator alloc, ApplicationControl ctrl, ShutdownHookRegistry reg) {
        this.ren = ren;
        this.alloc = alloc;
        this.ctrl = ctrl;
        reg.add(shutdownRun);
        svc.submit(runner);
    }

    public EventSink publish(String eventType, Object message) {
        if (channels.isEmpty()) {
            return this;
        }
        Message msg = new Message(eventType, count.getAndIncrement(), message);
        messages.offer(msg);
        return this;
    }

    public EventSink publish(Object message) {
        if (channels.isEmpty()) {
            return this;
        }
        Checks.notNull("message", message);
        Message msg = new Message(count.getAndIncrement(), message);
        messages.offer(msg);
        return this;
    }

    public EventSink register(Channel channel) {
        if (channel.isOpen()) {
            channels.add(channel);
        }
        return this;
    }

    private ByteBuf toByteBuf(Message msg) {
        StringBuilder builder = new StringBuilder();
        if (msg.eventType != null) {
            builder.append("\nevent: ").append(msg.eventType);
        }
        String stringMessage = ren.toString(msg.message).replace("\n", "\ndata: "); //XXX support multiline
        builder.append("\nid: ").append(msg.id).append("-").append(msg.timestamp)
                .append("\ndata: ").append(stringMessage).append('\n').append('\n');
        return alloc.buffer(builder.length()).writeBytes(builder.toString().getBytes(CharsetUtil.UTF_8));
    }

    private class Runner implements Runnable {

        @Override
        public void run() {
            synchronized (EventSink.class) {
                thread = Thread.currentThread();
            }
            final List<Message> msgs = new LinkedList<>();
            try {
                for (;;) {
                    try {
                        if (shutdown) {
                            break;
                        }
                        msgs.add(messages.take());
                        messages.drainTo(msgs);
                        if (channels.isEmpty()) {
                            msgs.clear();
                            continue;
                        }
                        for (Message msg : msgs) {
                            ByteBuf buf = toByteBuf(msg);
                            for (Iterator<Channel> channelIterator = channels.iterator(); channelIterator.hasNext();) {
                                if (shutdown) {
                                    return;
                                }
                                Channel channel = channelIterator.next();
                                if (!channel.isOpen()) {
                                    channelIterator.remove();
                                } else {
                                    try {
                                        ByteBuf toWrite = buf.duplicate().retain();
                                        channel.writeAndFlush(new DefaultHttpContent(toWrite));
                                    } catch (Exception e) {
                                        ctrl.internalOnError(e);
                                        channelIterator.remove();
                                    }
                                }
                            }
                            buf.release();
                        }
                        msgs.clear();
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
            } finally {
                msgs.clear();
                try {
                    for (Channel c : channels) {
                        c.close();
                    }
                } finally {
                    channels.clear();
                    synchronized (EventSink.this) {
                        thread = null;
                    }
                }
            }
        }
    }

    private class Shutdown implements Runnable {

        @Override
        public void run() {
            shutdown = true;
            Thread t;
            synchronized (EventSink.this) {
                t = thread;
                thread = null;
            }
            if (t != null && t.isAlive()) {
                t.interrupt();
            }
        }
    }

    private static final class Message {

        public final long timestamp = DateTimeUtils.currentTimeMillis();
        public final String eventType;
        public final long id;
        public final Object message;

        public Message(long id, Object message) {
            this(null, id, message);
        }

        public Message(String eventType, long id, Object message) {
            this.eventType = eventType;
            this.id = id;
            this.message = message;
        }
    }
}
