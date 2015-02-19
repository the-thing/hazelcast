package com.hazelcast.topic.impl.reliable;


import com.hazelcast.config.Config;
import com.hazelcast.config.TopicConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;
import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestThread;
import com.hazelcast.test.annotation.NightlyTest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

@RunWith(HazelcastSerialClassRunner.class)
@Category(NightlyTest.class)
@Ignore
public class ReliableTopicStressTest extends HazelcastTestSupport {

    private volatile boolean stop;
    private ITopic<Long> topic;

    @Before
    public void setup() {
        Config config = new Config();

        TopicConfig topicConfig = new TopicConfig("foobar");
        config.addTopicConfig(topicConfig);


        HazelcastInstance hz = createHazelcastInstance(config);
        topic = hz.getTopic(topicConfig.getName());
    }

    @Test
    public void test() throws InterruptedException {
        final StressMessageListener listener1 = new StressMessageListener(1);
        topic.addMessageListener(listener1);
        final StressMessageListener listener2 = new StressMessageListener(2);
        topic.addMessageListener(listener2);

        final ProduceThread produceThread = new ProduceThread();
        produceThread.start();

        System.out.println("Starting test");
        SECONDS.sleep(30);
        System.out.println("Completed");
        stop = true;

        produceThread.join();

        System.out.println("Number of items produced: " + produceThread.send);

        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals(produceThread.send, listener1.received);
                assertEquals(produceThread.send, listener2.received);
                assertEquals(0, listener1.failures);
                assertEquals(0, listener2.failures);
            }
        });
    }

    public class ProduceThread extends TestThread {
        private volatile long send = 0;

        @Override
        public void doRun() throws Throwable {
            while (!stop) {
                topic.publish(send);
                send++;
            }
        }
    }

    public class StressMessageListener implements MessageListener<Long> {
        private final int id;
        private long received = 0;
        private long failures = 0;

        public StressMessageListener(int id) {
            this.id = id;
        }

        @Override
        public void onMessage(Message<Long> message) {
            if (!message.getMessageObject().equals(received)) {
                failures++;
            }

            if (received % 100000 == 0) {
                System.out.println(toString() + " is at: " + received);
            }

            received++;
        }

        @Override
        public String toString() {
            return "StressMessageListener{" +
                    "id=" + id +
                    '}';
        }
    }
}
