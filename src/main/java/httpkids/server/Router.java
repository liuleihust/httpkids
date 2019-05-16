package httpkids.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.handler.codec.http.HttpResponseStatus;

public class Router {

	private IRequestHandler wirecardHandler;

	private Map<String, IRequestHandler> subHandlers = new HashMap<>();
	private Map<String, Map<String, IRequestHandler>> subMethodHandlers = new HashMap<>();

	private Map<String, Router> subRouters = new HashMap<>();

	private List<IRequestFilter> filters = new ArrayList<>();

	// Arrays.asList 返回 Arraylist
	private final static List<String> METHODS = Arrays
			.asList(new String[] { "get", "post", "head", "put", "delete", "trace", "options", "patch", "connect" });

	public Router() {
		this(null);
	}


	/*
	    函数式编程，IRequestHandler 只有一个抽象构造方法。可以 直接传入 函数方法。 (ctx,req)->{}
	 */
	/**
	* @Description: 构造函数
	* @Param: [wirecardHandler]
	* @return:
	* @Author: Liulei
	* @Date: 2019/5/15
	*/
	public Router(IRequestHandler wirecardHandler) {
		this.wirecardHandler = wirecardHandler;
	}

	/**
	* @Description:  配置路径与相应的处理方法。
	* @Param: [path, handler]
	* @return: httpkids.server.Router
	* @Author: Liulei
	* @Date: 2019/5/15
	*/
	public Router handler(String path, IRequestHandler handler) {
		path = KidsUtils.normalize(path);

		/*
		    路径中只运行一个斜线
		 */
		if (path.indexOf('/') != path.lastIndexOf('/')) {
			throw new IllegalArgumentException("path at most one slash allowed");
		}
		/*
		  放入 处理器映射 map 中，如果不指定 http 请求的方法，在默认映射所有方法。
		 */
		this.subHandlers.put(path, handler);
		return this;
	}
	
	/** 
	* @Description:  配置路径和映射方法
	* @Param: [path, method, handler] 
	* @return: httpkids.server.Router 
	* @Author: Liulei
	* @Date: 2019/5/15 
	*/ 
	public Router handler(String path, String method, IRequestHandler handler) {
		path = KidsUtils.normalize(path);
		method = method.toLowerCase();
		if (path.indexOf('/') != path.lastIndexOf('/')) {
			throw new IllegalArgumentException("path at most one slash allowed");
		}
		if (!METHODS.contains(method)) {
			// 参数错误
			throw new IllegalArgumentException("illegal http method name");
		}
		/*
		  subMethodHandlers : < path:<method:handle>>  
		 */
		var handlers = subMethodHandlers.get(path);
		if (handlers == null) {
			handlers = new HashMap<>();
			subMethodHandlers.put(path, handlers);
		}
		handlers.put(method, handler);
		return this;
	}
	
	/** 
	* @Description: 路由嵌套 
	* @Param: [path, router] 
	* @return: httpkids.server.Router 
	* @Author: Liulei
	* @Date: 2019/5/15 
	*/ 
	public Router child(String path, Router router) {
		path = KidsUtils.normalize(path);
		if (path.equals("/")) {
			throw new IllegalArgumentException("child path should not be /");
		}
		if (path.indexOf('/') != path.lastIndexOf('/')) {
			throw new IllegalArgumentException("path at most one slash allowed");
		}
		this.subRouters.put(path, router);
		return this;
	}

	public Router child(String path, IRouteable routeable) {
		return child(path, routeable.route());
	}
	
	/** 
	* @Description: 指定静态资源路径 
	* @Param: [path, resourceRoot] 
	* @return: httpkids.server.Router 
	* @Author: Liulei
	* @Date: 2019/5/15 
	*/ 
	public Router resource(String path, String resourceRoot) {
		Router router = new Router(new StaticRequestHandler(resourceRoot));
		return child(path, router);
	}
	
	
	public Router resource(String path, String resourceRoot, boolean classpath) {
		Router router = new Router(new StaticRequestHandler(resourceRoot, classpath));
		return child(path, router);
	}

	public Router resource(String path, String resourceRoot, boolean classpath, int cacheAge) {
		Router router = new Router(new StaticRequestHandler(resourceRoot, classpath, cacheAge));
		return child(path, router);
	}
	
	
	/** 
	* @Description: 配置过滤器 
	* @Param: [filters] 
	* @return: httpkids.server.Router 
	* @Author: Liulei
	* @Date: 2019/5/15 
	*/ 
	public Router filter(IRequestFilter... filters) {
		for (var filter : filters) {
			this.filters.add(filter);
		}
		return this;
	}

	/** 
	* @Description:  
	* @Param: [ctx, req] 
	* @return: void 
	* @Author: Liulei
	* @Date: 2019/5/15 
	*/ 
	public void handle(KidsContext ctx, KidsRequest req) {
		for (var filter : filters) {
			req.filter(filter);
		}
		var prefix = req.peekUriPrefix();
		var method = req.method().toLowerCase();
		var router = subRouters.get(prefix);
		if (router != null) {
			req.popUriPrefix();
			router.handle(ctx, req);
			return;
		}

		if (prefix.equals(req.relativeUri())) {
			var handlers = subMethodHandlers.get(prefix);
			IRequestHandler handler = null;
			if (handlers != null) {
				handler = handlers.get(method);
			}
			if (handler == null) {
				handler = subHandlers.get(prefix);
			}
			if (handler != null) {
				handleImpl(handler, ctx, req);
				return;
			}
		}

		if (this.wirecardHandler != null) {
			handleImpl(wirecardHandler, ctx, req);
			return;
		}

		throw new AbortException(HttpResponseStatus.NOT_FOUND);
	}

	private void handleImpl(IRequestHandler handler, KidsContext ctx, KidsRequest req) {
		for (var filter : req.filters()) {
			if (!filter.filter(ctx, req, true)) {
				return;
			}
		}

		handler.handle(ctx, req);

		for (var filter : req.filters()) {
			if (!filter.filter(ctx, req, false)) {
				return;
			}
		}
	}

}
