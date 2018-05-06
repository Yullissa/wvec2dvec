package com.yidian.wordvec2docvec.utils;

/**
 * Created by cuteapi on 2017/7/19.
 */

import com.google.common.collect.Maps;
import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.consumer.TopicFilter;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;
import kafka.serializer.Decoder;
import lombok.extern.log4j.Log4j;
import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author weijian Date : 2013-09-24 16:18
 *
 *         Properties props = new Properties(); props.put("zookeeper.connect", "10.127.10.48:2181,10.127.10.49:2181,10.127.10.50:2181"); props.put("group.id", "w");
 *         props.put("fetch.message.max.bytes", String.valueOf(1024l * 1024l * 100l)); props.put("auto.offset.reset", "smallest"); props.put("zookeeper.session.timeout.ms", "2000");
 *         props.put("zookeeper.sync.time.ms", "200"); props.put("auto.commit.interval.ms", "10000"); ConsumerConfig config = new ConsumerConfig(props); KafkaConsumer consumer = new
 *         KafkaConsumer(config);
 *
 */
@Log4j
public final class KafkaConsumer {

    private final ConsumerConnector consumerConnector;

    public KafkaConsumer(final ConsumerConfig config) {
        checkNotNull(config, "config");
        consumerConnector = Consumer.createJavaConsumerConnector(config);
    }

    public <K, V> Map<String, List<KafkaStream<K, V>>> createMessageStreams(Map<String, Integer> stringIntegerMap, Decoder<K> kDecoder, Decoder<V> vDecoder) {
        return consumerConnector.createMessageStreams(stringIntegerMap, kDecoder, vDecoder);
    }

    public <K, V> List<KafkaStream<K, V>> createMessageStreamsByFilter(TopicFilter topicFilter, int i, Decoder<K> kDecoder, Decoder<V> vDecoder) {
        return consumerConnector.createMessageStreamsByFilter(topicFilter, i, kDecoder, vDecoder);
    }

    public List<KafkaStream<byte[], byte[]>> createMessageStreamsByFilter(TopicFilter topicFilter, int i) {
        return consumerConnector.createMessageStreamsByFilter(topicFilter, i);
    }

    public List<KafkaStream<byte[], byte[]>> createMessageStreamsByFilter(TopicFilter topicFilter) {
        return consumerConnector.createMessageStreamsByFilter(topicFilter);
    }

    public Map<String, List<KafkaStream<byte[], byte[]>>> createMessageStreams(Map<String, Integer> topicCountMap) {
        return consumerConnector.createMessageStreams(topicCountMap);
    }

    public List<KafkaStream<byte[], byte[]>> createMessageStreams(final String topic, final int numStreams) {
        checkNotNull(topic, "topic");
        checkArgument(numStreams > 0, "numStreams <= 0");

        Map<String, Integer> topicCountMap = Maps.newHashMap();
        topicCountMap.put(topic, numStreams);

        Map<String, List<KafkaStream<byte[], byte[]>>> map = consumerConnector.createMessageStreams(topicCountMap);

        return map.get(topic);
    }

    public KafkaStream<byte[], byte[]> createMessageStream(final String topic) {
        checkNotNull(topic, "topic");

        Map<String, Integer> topicCountMap = Maps.newHashMap();
        topicCountMap.put(topic, 1);

        Map<String, List<KafkaStream<byte[], byte[]>>> map = consumerConnector.createMessageStreams(topicCountMap);

        return map.get(topic).get(0);
    }

    public void commitOffsets() {
        consumerConnector.commitOffsets();
    }

    public void shutdown() {
        consumerConnector.shutdown();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Properties props = new Properties();
        File logConfig = new File("log4j.properties");
        if (logConfig.exists()) {
            System.out.println("User config " + logConfig.toString());
            PropertyConfigurator.configure(logConfig.toString());
        }
        else
            System.out.println("No user config!");
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
        ConsumerConfig config = new ConsumerConfig(props);
        KafkaConsumer consumer = new KafkaConsumer(config);
        KafkaStream<byte[], byte[]> stream = consumer.createMessageStream("indata_str_usercluster_explore_log");
        Iterator<MessageAndMetadata<byte[], byte[]>> iter = stream.iterator();
        while (iter.hasNext()) {
            MessageAndMetadata<byte[], byte[]> msg = iter.next();
            String rawlog = new String(msg.message());
            System.out.println(rawlog);
            log.error(rawlog);
            //Thread.sleep(100);
        }
    }
}
//10.111.1.13:2181
//hadoop2-13.lg-4-e10.yidian.com:2181 hadoop2-14.lg-4-e10.yidian.com:2181 had
//bin/kafka-console-consumer.sh --zookeeper 10.111.1.13:2181  --from-beginning --topic rawlog_str_pollen_website
//Website/user/binding-location
////0.8.1.1
