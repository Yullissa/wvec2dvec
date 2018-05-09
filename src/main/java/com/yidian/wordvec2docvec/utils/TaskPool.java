package com.yidian.wordvec2docvec.utils;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.List;
import java.util.concurrent.*;

public class TaskPool {
    private ThreadPoolExecutor pool = null;

    public TaskPool(int corePoolSize, int maxPoolSize, int aliveMin, int qSize, String name){
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(qSize);
        pool = new ThreadPoolExecutor(corePoolSize,
                maxPoolSize,
                aliveMin,
                TimeUnit.MINUTES,
                queue,
                new ThreadFactoryBuilder().setNameFormat(name + "-function-pool-%d").build(),
                new ThreadPoolExecutor.AbortPolicy());

    }
    public Future<?> submit(Runnable task){
        return pool.submit(task);
    }

    public <T> Future<T> submit(Callable<T> task){
        return pool.submit(task);
    }

    public static void main(String [] args){
        TaskPool tp = new TaskPool(10, 20, 10, 5000, "test");
        Stopwatch watch = Stopwatch.createStarted();
        List<Future<?>> lst = Lists.newArrayList();
        for(int i = 0; i < 15; i++){
            int taskId = i;
            Future<?> future = tp.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("pid: " + Thread.currentThread().getId() + " task id: " + taskId + " i am over");
                }
            });
            lst.add(future);
        }
        System.out.println("task submit done");
        for(Future<?> f: lst){
            try {
                f.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        System.out.println("over time elapsed " + watch.toString());

        watch.reset();
        watch.start();
        List<Future<String>> nlst = Lists.newArrayList();
        for(int i = 0; i < 15; i++){
            int taskId = i;
            Future<String> nfuture = tp.submit(new Callable<String>(){
                @Override
                public String call() throws Exception {
                    Stopwatch watch = Stopwatch.createStarted();
                    Thread.sleep((int)Math.ceil(20 * 1000 * Math.random()));
                    return  "pid: " + Thread.currentThread().getId() + "task id: " + taskId + " time cost: " + watch.toString();
                }
            });
            nlst.add(nfuture);
        }
        System.out.println("task submit done");
        for(Future<String> f: nlst){
            try {
                String val = f.get();
                System.out.println("one time elapsed " + val);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        System.out.println("over time elapsed " + watch.toString());
        System.exit(0);

    }
}
