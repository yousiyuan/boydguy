springboot rabbitmq消息同步特性用作接口调用
http://www.manongjc.com/detail/13-lbmgrqtxerzpxrg.html

参考资料
https://blog.csdn.net/art_code/article/details/90499839

Redis雪崩效应解决方案：

①、分布式锁(Redis集群);本地锁(单台Redis服务器)

②、使用消息中间件，消息中间件可以解决高并发；

③、使用一级缓存和二级缓存(Redis + Ehcache)；

④、均摊分配Redis Key的失效时间，例如按照不同的业务场景设置不同的Redis Key失效时间。

对比：
①与②相比，②更靠谱
③与④相比，④最最靠谱

