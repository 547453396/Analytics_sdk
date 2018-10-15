##SDK使用说明

参考[数据协议](protocol.md)及上传的数据模型。另外本sdk源码上参考[神策开源代码](https://github.com/sensorsdata/sa-sdk-android).

##集成SDK

支持module和aar两种形式导入。  
####步骤1
在 **project** 级别的 build.gradle 文件中添加依赖：

    
    //aop全埋点需要
    classpath 'org.aspectj:aspectjtools:1.8.9'
    classpath 'org.aspectj:aspectjweaver:1.8.9'
    
如下图：  
![图1](http://img2033.static.suishenyun.net/e79c3b8f01a693b60e8a69616a4f35fb/390aeec83ecdc67b39f439cdca6acfd3.png!w480.jpg)
####步骤2 
导入analyticssdk module或aar文件，在主APP module的 build.gradle 文件中添加依赖：  


    //aop全埋点需要
    implementation 'org.aspectj:aspectjrt:1.8.9'
    //aar导入或module导入
    //implementation 'cn.weli.analytics:sdk:1.0@aar'
    implementation project(':analyticssdk')
    
添加导入aspectj所需的编译代码，放于文件尾部即可：    

    
    import org.aspectj.bridge.IMessage
    import org.aspectj.bridge.MessageHandler
    import org.aspectj.tools.ajc.Main
    final def log = project.logger
    final def variants = project.android.applicationVariants

    variants.all { variant ->
    if (!variant.buildType.isDebuggable()) {
        log.debug("Skipping non-debuggable build type '${variant.buildType.name}'.")
        return;
    }

    JavaCompile javaCompile = variant.javaCompile
    javaCompile.doLast {
        String[] args = ["-showWeaveInfo",
                         "-1.8",
                         "-inpath", javaCompile.destinationDir.toString(),
                         "-aspectpath", javaCompile.classpath.asPath,
                         "-d", javaCompile.destinationDir.toString(),
                         "-classpath", javaCompile.classpath.asPath,
                         "-bootclasspath", project.android.bootClasspath.join(File.pathSeparator)]
        log.debug "ajc args: " + Arrays.toString(args)

        MessageHandler handler = new MessageHandler(true);
        new Main().run(args, handler);
        for (IMessage message : handler.getMessages(null, true)) {
            switch (message.getKind()) {
                case IMessage.ABORT:
                case IMessage.ERROR:
                case IMessage.FAIL:
                    log.error message.message, message.thrown
                    break;
                case IMessage.WARNING:
                    log.warn message.message, message.thrown
                    break;
                case IMessage.INFO:
                    log.info message.message, message.thrown
                    break;
                case IMessage.DEBUG:
                    log.debug message.message, message.thrown
                    break;
            }
        }
      }
    }
如图：     
![图2](http://img2033.static.suishenyun.net/e79c3b8f01a693b60e8a69616a4f35fb/6e38d99af2f9409ac8f429d8c1c5be8d.png!w480.jpg)

以上基本上集成完毕。  
使用方法参加[链接](使用方法文档.md)。  
参考文档：  
[项目埋点的演进](https://www.jianshu.com/p/abdaf64ad553)
