package bcntec.learning.bigdata.kafka.twitter.producer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;

import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Demonstrates, using the low-level Processor APIs, how to implement the WordCount program
 * that computes a simple word occurrence histogram from an input text.
 *
 * In this example, the input stream reads from a topic named "streams-plaintext-input", where the values of messages
 * represent lines of text; and the histogram output is written to topic "streams-wordcount-processor-output" where each record
 * is an updated count of a single word.
 *
 * Before running this example you must create the input topic and the output topic (e.g. via
 * bin/kafka-topics.sh --create ...), and write some data to the input topic (e.g. via
 * bin/kafka-console-producer.sh). Otherwise you won't see any data arriving in the output topic.
 *
 */
public class KafkaTwitterProcessor {

    static class KafkaWordCountProcessorSupplier implements ProcessorSupplier<String, String> {

        @Override
        public Processor<String, String> get() {
            return new Processor<String, String>() {
                private ProcessorContext context;
                private KeyValueStore<String, Integer> kvStore;

                @Override
                @SuppressWarnings("unchecked")
                public void init(final ProcessorContext context) {
                    this.context = context;
                    this.context.schedule(1000, PunctuationType.STREAM_TIME, timestamp -> {
                        try (KeyValueIterator<String, Integer> iter = kvStore.all()) {
                            System.out.println("----------- " + timestamp + " ----------- ");

                            while (iter.hasNext()) {
                                KeyValue<String, Integer> entry = iter.next();

                                System.out.println("[" + entry.key + ", " + entry.value + "]");

                                context.forward(entry.key, entry.value.toString());
                            }
                        }
                    });
                    this.kvStore = (KeyValueStore<String, Integer>) context.getStateStore("Counts");
                }

                @Override
                public void process(String dummy, String line) {
                    String[] words = line.toLowerCase(Locale.getDefault()).split(" ");

                    for (String word : words) {
                        Integer oldValue = this.kvStore.get(word);

                        if (oldValue == null) {
                            this.kvStore.put(word, 1);
                        } else {
                            this.kvStore.put(word, oldValue + 1);
                        }
                    }

                    context.commit();
                }

                @Override
                public void close() {}
            };
        }
    }

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-processor");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,System.getProperty("bootstrap.servers"));
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        // setting offset reset to earliest so that we can re-run the demo code with the same pre-loaded data
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Topology builder = new Topology();

        builder.addSource("Source", "bigdata.twitter.tags.live");

        builder.addProcessor("Process", new KafkaWordCountProcessorSupplier(), "Source");
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore("Counts"),
                Serdes.String(),
                Serdes.Integer()),
                "Process");

        builder.addSink("Sink", "bigdata.twitter.tags.live-agg", "Process");

        final KafkaStreams streams = new KafkaStreams(builder, props);
        final CountDownLatch latch = new CountDownLatch(1);

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("bigdata.twitter.tags.live-agg-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}