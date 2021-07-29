package kafka.tutorial1;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ConsumerDemoWithThread {
    public static void main(String[] args) {
        new ConsumerDemoWithThread().run();
    }

    private ConsumerDemoWithThread(){
    }

    private void run(){
        Logger logger = LoggerFactory.getLogger(ConsumerDemoWithThread.class.getName());
        String bootstrapServer = "localhost:9092";
        String groupId = "my-fourth-application";
        String topic = "first_topic";

        //latch for dealing with multiple threads
        CountDownLatch latch = new CountDownLatch(1);
        logger.info("Creating the consumer thread");
        Runnable myConsumerRunnable = new ConsumerRunnable(bootstrapServer, groupId, topic, latch);

        //Start a thread
        Thread myThread = new Thread(myConsumerRunnable);
        myThread.start();

        //add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Caught shutdown hook");
            ((ConsumerRunnable) myConsumerRunnable).shutdown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Application has exited");
        }));
        try{
            latch.await();
        }catch (InterruptedException e){
            logger.error("Application got interrupted");
        }finally{
            logger.info("Application is closing");
        }
    }

    public class ConsumerRunnable implements Runnable{
        private Logger logger = LoggerFactory.getLogger(ConsumerRunnable.class.getName());
        private CountDownLatch latch;
        private KafkaConsumer<String, String> consumer;

        private ConsumerRunnable(String bootstrapServer,
                                 String groupId,
                                 String topic,
                                 CountDownLatch latch){
            this.latch = latch;
            // create consumer configs
            Properties properties = new Properties();
            properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
            properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            // create consumer
            consumer = new KafkaConsumer<String, String>(properties);

            // subscribe consumer to our topic(s)
            consumer.subscribe(Arrays.asList(topic));

        }

        @Override
        public void run() {
            // poll for new data
            try{
                while (true){
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for(ConsumerRecord<String, String> record : records){
                        logger.info("Key : " + record.key() + " Value : " + record.value());
                        logger.info("Partition : " + record.partition() + " Offsets : " + record.offset());
                    }
                }
            }catch (WakeupException e){
                logger.info("Received shutdown signal");
            }finally {
                consumer.close();
                // tell our main code, we are done with the consumer.
                latch.countDown();
            }
        }

        public void shutdown(){
            // The wakeup method is a special method to interrupt consumer.poll()
            // It will throw the exception WakeupException
            consumer.wakeup();
        }
    }
}
