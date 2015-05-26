easyDownload
============


##说明##

下载包, 目前只支持HTTP下载, 包括断点, 多线程, 下载限速, 下载优先级, 一个地址从多个服务器下载等功能.

使用前调用 DownloadPool.init(context); 进行初始化, 之后将可以开始下载.



##gradle使用说明##

compile 'com.github.lisicnu:easyDownload:0.1.2'
// 内部已经自动引用  
// compile 'com.github.lisicnu:droidUtil:0.1.3'
// compile 'com.github.lisicnu:log4android:1.0.1'


##Future##
所涉及到的项目 libDroid, log4android, 
具体的使用方式参见 http://www.cnblogs.com/checkway/, 相关文章正在完善中.
