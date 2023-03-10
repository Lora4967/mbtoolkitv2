package me.earthme.mbtoolkit.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import me.earthme.mbtoolkit.network.codec.MessageDecoder;
import me.earthme.mbtoolkit.network.codec.MessageEncoder;
import me.earthme.mbtoolkit.network.handler.NettyClientHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.concurrent.locks.LockSupport;

public class ClientInstance {
    private static final Logger logger = LogManager.getLogger();
    private final NioEventLoopGroup loopGroup = new NioEventLoopGroup();
    private final Bootstrap bootstrap = new Bootstrap();
    private ChannelFuture future;
    private InetSocketAddress lastAddress;
    private boolean flag = false;
    private volatile boolean shouldRun = true;

    public void connect(InetSocketAddress socketAddress) {
        this.lastAddress = socketAddress;
        if (!this.flag){
            this.bootstrap.group(this.loopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY,true)
                    .option(ChannelOption.SO_KEEPALIVE,true)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(@NotNull Channel ch) {
                            ch.pipeline()
                                    .addLast(new LengthFieldBasedFrameDecoder(2077721600,0,4,0,4))
                                    .addLast(new LengthFieldPrepender(4))
                                    .addLast(new MessageDecoder())
                                    .addLast(new MessageEncoder())
                                    .addLast(new NettyClientHandler());
                        }
                    });
        }

        try {
            this.future = this.bootstrap.connect(socketAddress).sync();
            logger.info("Connected to server");
        }catch (Exception e){
            e.printStackTrace();
        }

        if (!flag){
            final Thread reconnectThread = new Thread(()->{
                while (this.shouldRun){
                    try {
                        this.blockUntilDisconnected();
                        this.future.channel().close();
                        Thread.sleep(3000);
                        logger.info("Connection lost!Reconnecting to server");
                        if (this.shouldRun){
                            this.connect(this.lastAddress);
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            });
            reconnectThread.setPriority(3);
            reconnectThread.setDaemon(true);
            reconnectThread.start();
            flag = true;
        }
    }

    public void shutdown(){
        this.shouldRun = false;
        this.future.channel().close();
        this.loopGroup.shutdownGracefully();
    }

    public void blockUntilDisconnected(){
        while (this.future.channel().isOpen() && this.shouldRun){
            LockSupport.parkNanos(100_000_000);
        }
    }
}
