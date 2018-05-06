package com.yidian.wordvec2docvec.task;


import com.yidian.wordvec2docvec.core.FidSet2news;
import com.yidian.wordvec2docvec.data.DocumentFeature;
import com.yidian.wordvec2docvec.filter.DocumentFilter;
import com.yidian.wordvec2docvec.filter.FastTextFilter;
import com.yidian.wordvec2docvec.utils.KafkaConsumer;
import com.yidian.serving.metrics.MetricsFactoryUtil;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.message.MessageAndMetadata;
import kafka.utils.ZkUtils;
import lombok.extern.log4j.Log4j;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Optional;
import java.util.Properties;

@Log4j
public class DocumentCollect extends Thread {
    private DocumentFilter documentFilter = null;
    private String topic = "null";

    public DocumentCollect(String topic, DocumentFilter documentFilter) {
        this.topic = topic;

        this.documentFilter = documentFilter;
    }

    @Override
    public void run() {
        super.run();
        this.setName("ExploreDocCollect-thread-" + topic);
        Properties props = new Properties();
        try {
            FileInputStream fs = new FileInputStream("kafka.properties");
            props.load(fs);
            log.info("reading kafka.properties");
        } catch (FileNotFoundException e) {
            log.error("kafka.properties is missing!");
            log.error(e);
            System.exit(-1);
        } catch (IOException e) {
            log.error("read kafka.properties failed!");
            log.error(e);
            System.exit(-1);
        }
        String metricPrefix = "usercluster-ee.docCollect." + topic;
        String hostName = "usercluster-ee";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.error("The hostname cannot be resolved", e);
            throw new RuntimeException(e);
        }
        props.setProperty("group.id", hostName);
        String zkUrl = props.getProperty("zookeeper.connect").replace("/violet", "");
        ZkUtils.maybeDeletePath(zkUrl, "/violet/consumers/" + hostName + "/offsets/" + topic);
        FidSet2news fidSet2news = FidSet2news.getInstance();
        ConsumerConfig config = new ConsumerConfig(props);
        KafkaConsumer consumer = new KafkaConsumer(config);

        KafkaStream<byte[], byte[]> stream = consumer.createMessageStream(topic);
        Iterator<MessageAndMetadata<byte[], byte[]>> iter = stream.iterator();
        while (iter.hasNext()) {
            MessageAndMetadata<byte[], byte[]> msg = iter.next();
            String data = new String(msg.message());
            MetricsFactoryUtil.getRegisteredFactory().getMeter(metricPrefix + ".log.process.qps").mark();
            Optional<DocumentFeature> documentFeatureOpt = documentFilter.filter(data);
            documentFeatureOpt.ifPresent(fidSet2news::addDocument);
        }
    }

    public static void main(String[] args) {
        DocumentCollect edc = new DocumentCollect("indata_str_documents_info", FastTextFilter.getInstance());
        edc.run();
    }
}
