package httpkids.server.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;

public class HttpServer {
	private final static Logger LOG = LoggerFactory.getLogger(HttpServer.class);

	private String ip;
	private int port;
	private int ioThreads;
	private int workerThreads;
	private IRequestDispatcher dispatcher;

	public HttpServer(String ip, int port, int ioThreads, int workerThreads, IRequestDispatcher dispatcher) {
		this.ip = ip;
		this.port = port;
		this.ioThreads = ioThreads;
		this.workerThreads = workerThreads;
		this.dispatcher = dispatcher;
	}

	private ServerBootstrap bootstrap;
	private EventLoopGroup group;
	private Channel serverChannel;
	private MessageCollector collector;

	public void start() {
		bootstrap = new ServerBootstrap();
		group = new NioEventLoopGroup(ioThreads);
		bootstrap.group(group);
		collector = new MessageCollector(workerThreads, dispatcher);
		bootstrap.channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) throws Exception {
				var pipe = ch.pipeline();
				/*
				   Netty 超时控制 handler,用于读取数据的时候的超时，如果在设置时间段内都没有数据读取 ，就会引发查实，然后关闭当前 channel,
				 */
				pipe.addLast(new ReadTimeoutHandler(10));
				/*
					HttpServerCodec 解析 http 协议 https://blog.csdn.net/woshizw27/article/details/87999137
				 */
				pipe.addLast(new HttpServerCodec());
				/*
				 HttpServerCodec 只能获取uri 中的参数， 当使用post 方式请求服务器的时候，对应的参数保存在 message body  中，
				 所以需要加上 HttpObjectAggregator ，

				 */
				pipe.addLast(new HttpObjectAggregator(1 << 30)); // max_size = 1g
				/*
				 传送大文件
				 */
				pipe.addLast(new ChunkedWriteHandler());
				pipe.addLast(collector);
			}
		});
		/*
		ChannelOption 的 含义 ：https://www.cnblogs.com/googlemeoften/p/6082785.html
		 */
		bootstrap.option(ChannelOption.SO_BACKLOG, 100).option(ChannelOption.SO_REUSEADDR, true)
				.option(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true);
		serverChannel = bootstrap.bind(this.ip, this.port).channel();
		LOG.warn("server started @ {}:{}", ip, port);
	}

	public void stop() {
		// 先关闭服务端套件字
		serverChannel.close();
		// 再斩断消息来源，停止io线程池
		group.shutdownGracefully();
		// 停止业务线程池
		collector.closeGracefully();
	}

}
