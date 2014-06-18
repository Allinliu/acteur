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

import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.Connection;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * Use for HTTP requests that get a text/event-stream response which remains
 * open indefinitely
 *
 * @author Tim Boudreau
 */
public final class SseActeur extends Acteur {

    private static final MediaType TYPE = MediaType.parse("text/event-stream; charset=UTF-8");

    @Inject
    SseActeur(EventSink sink) {
        add(Headers.CONTENT_TYPE, TYPE);
        add(Headers.CACHE_CONTROL, CacheControl.PRIVATE_NO_CACHE_NO_STORE);
        add(Headers.CONNECTION, Connection.keep_alive);
        setState(new RespondWith(OK));
        setResponseBodyWriter(new L(sink));
        setChunked(true);
    }

    private static class L implements ChannelFutureListener {

        private final EventSink sink;

        public L(EventSink sink) {
            this.sink = sink;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            sink.register(future.channel());
        }
    }
}
