/*
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur.server;

import com.google.inject.Provider;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION;
import static com.mastfrog.acteur.server.ServerModule.MAX_CONTENT_LENGTH;
import com.mastfrog.acteur.spi.ApplicationControl;
import com.mastfrog.settings.Settings;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Sharable
class PipelineFactoryImpl extends ChannelInitializer<SocketChannel> {

    static final boolean DEFAULT_AGGREGATE_CHUNKS = true;

    private final Provider<ChannelHandler> handler;
    private final boolean aggregateChunks;
    private final int maxContentLength;
    private final boolean httpCompression;
    private final Provider<ApplicationControl> app;
    private final PipelineDecorator decorator;

    @Inject
    PipelineFactoryImpl(Provider<ChannelHandler> handler, Provider<ApplicationControl> app, Settings settings, PipelineDecorator decorator) {
        this.decorator = decorator;
        this.handler = handler;
        this.app = app;
        aggregateChunks = settings.getBoolean("aggregateChunks", DEFAULT_AGGREGATE_CHUNKS);
        httpCompression = settings.getBoolean(HTTP_COMPRESSION, true);
        maxContentLength = settings.getInt(MAX_CONTENT_LENGTH, 1048576);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        app.get().internalOnError(cause);
    }

    private final MessageBufEncoder messageBufEncoder = new MessageBufEncoder();
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = ch.pipeline();
        decorator.onCreatePipeline(pipeline);
        ChannelHandler decoder = new HttpRequestDecoder();
        ChannelHandler encoder = new HttpResponseEncoder();
//        SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
//        engine.setUseClientMode(false);
//        pipeline.addLast("ssl", new SslHandler(engine));

        pipeline.addLast(PipelineDecorator.DECODER, decoder);
        pipeline.addLast(PipelineDecorator.ENCODER, encoder);
        if (aggregateChunks) {
            ChannelHandler aggregator = new HttpObjectAggregator(maxContentLength);
            pipeline.addLast(PipelineDecorator.AGGREGATOR, aggregator);
        }

//        pipeline.addLast(PipelineDecorator.BYTES, messageBufEncoder);

        // Remove the following line if you don't want automatic content compression.
        if (httpCompression) {
            ChannelHandler compressor = new SelectiveCompressor();
            pipeline.addLast(PipelineDecorator.COMPRESSOR, compressor);
        }
        pipeline.addLast(PipelineDecorator.HANDLER, handler.get());
        decorator.onPipelineInitialized(pipeline);
    }

    @Sharable
    private static class MessageBufEncoder extends MessageToByteEncoder<ByteBuf> {

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
            out.writeBytes(msg);
        }
    }

    private static class SelectiveCompressor extends HttpContentCompressor {

        @Override
        protected Result beginEncode(HttpResponse headers, String acceptEncoding) throws Exception {
            if (headers.headers().contains("X-Internal-Compress")) {
                headers.headers().remove("X-Internal-Compress");
                return null;
            }
            return super.beginEncode(headers, acceptEncoding);
        }
    }
}
