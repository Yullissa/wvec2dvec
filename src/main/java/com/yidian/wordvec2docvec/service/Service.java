package com.yidian.wordvec2docvec.service;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.yidian.wordvec2docvec.data.DocsPool;
import com.yidian.wordvec2docvec.utils.DocEmbedding;
import lombok.extern.log4j.Log4j;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.File;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Log4j
public class Service implements Runnable {
    @Parameter(names = "-port")
    private int port = 8000;
    @Parameter(names = "-config")
    private String beanConfig;
    @Parameter(names = "-docNum")
    private int docNum = 14858382;
    @Parameter(names = "-avgle")
    private float avgle = 302.3f;

    private ScheduledThreadPoolExecutor scheduledPool = new ScheduledThreadPoolExecutor(5,
            new ThreadFactoryBuilder().setNameFormat("fidset2news-function-scheduler-%d").build(),
            new ThreadPoolExecutor.AbortPolicy());

    @Override
    public void run() {
        log.info("Service Started");
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        initService();
//        DocumentFilter filter = FastTextFilter.getInstance();
//        DocumentCollect newsCollect = new DocumentCollect("indata_str_documents_info", filter);
//        DocumentCollect vertCollectt = new DocumentCollect("indata_str_vertical_documents", filter);
//        newsCollect.start();
//        vertCollectt.start();
//        scheduledPool.scheduleAtFixedRate(() -> FidSet2news.getInstance().explire(), 0, 3, TimeUnit.MINUTES);
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMinThreads(threadPool.getMinThreads() * 4);
        threadPool.setMaxThreads(threadPool.getMaxThreads() * 4);
        Server server = new Server(threadPool);
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.setConnectors(new Connector[]{connector});

        log.warn("Use port = " + port);
        ServletContextHandler service = new ServletContextHandler(ServletContextHandler.SESSIONS);
        {
            service.setContextPath("/docvec");
            service.addServlet(new ServletHolder(new Word2DocVecServlet(docNum,avgle)), "/recommend/*");
        }
        ServletContextHandler root = new ServletContextHandler(ServletContextHandler.SESSIONS);
        {
            root.setContextPath("/");
        }
        ContextHandler pages = new ContextHandler();
        {
            pages.setContextPath("/static");
            ResourceHandler handler = new ResourceHandler();
            handler.setDirectoriesListed(true);
            handler.setBaseResource(JarResource.newClassPathResource("."));
            pages.setHandler(handler);
        }
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        {
            contexts.addHandler(service);
            contexts.addHandler(pages);
            contexts.addHandler(root);
        }
        HandlerCollection handlers = new HandlerCollection();
        handlers.setHandlers(new Handler[]{contexts, new DefaultHandler()});
        server.setHandler(handlers);
        try {
            server.start();
        } catch (Exception e) {
            log.error("", e);
        }
        log.info("Service Started");
        try {
            server.join();
        } catch (InterruptedException e) {
            log.error("", e);
        }
        log.info("Service End");
    }

    private void initService() {
        DocEmbedding.defaultInstance();
        DocsPool.defaultInstance();
    }

    public static void main(String[] args) {
        File logConfig = new File("log4j.properties");
        if (logConfig.exists()) {
            System.out.println("User config " + logConfig.toString());
            PropertyConfigurator.configure(logConfig.toString());
        }
        Service service = new Service();
        JCommander jcommander = new JCommander(service);
        jcommander.parse(args);
        service.run();
        System.out.println("hello world");
    }
}
