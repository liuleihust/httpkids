package httpkids.server;


/*
     函数接口：首先是一个接口，然后在这个接口里面只能有一个抽象方法。
     这种类型的接口称为 SAM 接口，即Single Abstract Method interfaces

     特点：
     1. 接口有且仅有一个抽象方法。
     2. 允许定义静态方法
     3. 允许定义默认方法
     4， 允许java.lang.Object 中的public 方法
     该注解不是必须的，加上该注解可以更好的让编译器进行检查。
 */
@FunctionalInterface
public interface IRequestHandler {

	public void handle(KidsContext ctx, KidsRequest req);

}
