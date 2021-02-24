/*
 * Copyright 2021 OPS4J.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.transx.jms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQXAConnectionFactory;
import org.apache.aries.transaction.internal.AriesPlatformTransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.transx.tm.Transaction;
import org.ops4j.pax.transx.tm.TransactionManager;
import org.ops4j.pax.transx.tm.impl.geronimo.TransactionManagerWrapper;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ActiveMQTest {

    // This test is affected by https://issues.apache.org/jira/browse/AMQ-2659 after upgrading to ActiveMQ 5.16.1
    // but it sounds reasonable to adjust Pax Transx to this new behavior

    public static final String BROKER_URL = "vm://broker?marshal=false&broker.persistent=false";
    public static final String QUEUE = "myqueue";

    static int brokerId;

    AriesPlatformTransactionManager ptm;
    TransactionManager tm;
    List<AutoCloseable> closeables = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        ptm = new AriesPlatformTransactionManager();
        tm = new TransactionManagerWrapper(ptm);
    }

    @After
    public void tearDown() throws Exception {
        closeables.forEach(c -> {
            try {
                c.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    public void testContextWithXaTx() throws Exception {
        ConnectionFactory cf = createCF(BROKER_URL);

        assertEquals(0, consumeMessages(cf, QUEUE).size());

        Transaction tx = tm.begin();
        try (JMSContext context = cf.createContext()) {
            Queue queue = context.createQueue(QUEUE);
            context.createProducer().send(queue, "Hello");
        }
        tx.rollback();
        assertEquals(0, consumeMessages(cf, QUEUE).size());

        tx = tm.begin();
        try (JMSContext context = cf.createContext()) {
            Queue queue = context.createQueue(QUEUE);
            context.createProducer().send(queue, "Hello");
        }
        tx.commit();
        tx = tm.begin();
        assertEquals(1, consumeMessages(cf, QUEUE).size());
        tx.commit();

        tx = tm.begin();
        try (JMSContext context = cf.createContext()) {
            Queue queue = context.createQueue(QUEUE);
            context.createProducer().send(queue, "Hello");
        }
        tx.commit();
        tx = tm.begin();
        assertEquals(1, consumeMessages(cf, QUEUE).size());
        tx.commit();
    }

    @Test
    public void testSessionWithXaTx() throws Exception {
        ConnectionFactory cf = createCF(BROKER_URL);

        assertEquals(0, consumeMessages(cf, QUEUE).size());

        // transaction with rollback

        Transaction tx = tm.begin();
        try (Connection con = cf.createConnection()) {
            try (Session s = con.createSession()) {
                Queue queue = s.createQueue(QUEUE);
                s.createProducer(queue).send(s.createTextMessage("Hello"));
            }
        }
        tx.rollback();
        tx = tm.begin();
        assertEquals(0, consumeMessages(cf, QUEUE).size());
        tx.commit();

        // transaction with commit

        tx = tm.begin();
        try (Connection con = cf.createConnection()) {
            try (Session s = con.createSession()) {
                Queue queue = s.createQueue(QUEUE);
                s.createProducer(queue).send(s.createTextMessage("Hello"));
            }
        }
        tx.commit();
        tx = tm.begin();
        assertEquals(1, consumeMessages(cf, QUEUE).size());
        tx.commit();

        // no transaction at all

        try (Connection con = cf.createConnection()) {
            try (Session s = con.createSession()) {
                Queue queue = s.createQueue(QUEUE);
                try {
                    MessageProducer producer = s.createProducer(queue);
                    TextMessage message = s.createTextMessage("Hello");
                    producer.send(message);
                    fail("Should have thrown an exception");
                } catch (JMSException e) {
                    // expected
                }
            }
        }
        tx = tm.begin();
        assertEquals(0, consumeMessages(cf, QUEUE).size());
        tx.commit();

        // transaction with setRollbackOnly

        tx = tm.begin();
        try (Connection con = cf.createConnection()) {
            try (Session s = con.createSession(true, Session.SESSION_TRANSACTED)) {
                Queue queue = s.createQueue(QUEUE);
                s.createProducer(queue).send(s.createTextMessage("Hello"));
                tx.setRollbackOnly();
            }
        }
        assertEquals(0, consumeMessages(cf, QUEUE).size());
        tx.rollback();

        // transaction with session.rollback() after transaction.commit();

        tx = tm.begin();
        try (Connection con = cf.createConnection()) {
            try (Session s = con.createSession(false, Session.SESSION_TRANSACTED)) {
                Queue queue = s.createQueue(QUEUE);
                s.createProducer(queue).send(s.createTextMessage("Hello"));
                tx.commit();
                try {
                    s.rollback();
                    fail("Should have thrown an exception");
                } catch (JMSException e) {
                    // expected
                }
            }
        }
        tx = tm.begin();
        assertEquals(1, consumeMessages(cf, QUEUE).size());
        tx.commit();
    }

    @Test
    public void testSpring() throws Exception {
        ConnectionFactory cf = createCF(BROKER_URL, false);
        JmsTemplate jms = new JmsTemplate(cf);
        jms.setDefaultDestinationName(QUEUE);
        jms.setReceiveTimeout(100);

        jms.convertAndSend("Hello");
        Object msg = jms.receiveAndConvert();
        assertEquals("Hello", msg);
    }

    @Test
    public void testSpringXa() throws Exception {
        ConnectionFactory cf = createCF(BROKER_URL);
        JmsTemplate jms = new JmsTemplate(cf);
        jms.setDefaultDestinationName(QUEUE);
        jms.setReceiveTimeout(100);
        TransactionTemplate xaTx = new TransactionTemplate(ptm);

        xaTx.execute(ts -> {
            jms.convertAndSend("Hello");
            return null;
        });
        Object msg = xaTx.execute(ts -> jms.receiveAndConvert());
        assertEquals("Hello", msg);

        xaTx = new TransactionTemplate(ptm);
        xaTx.execute(ts -> {
            jms.convertAndSend("Hello");
            ts.setRollbackOnly();
            return null;
        });
        msg = xaTx.execute(ts -> jms.receiveAndConvert());
        assertNull(msg);
    }

    @Test
    public void testSpringReceiverXa() throws Exception {
        ConnectionFactory cf = createCF(BROKER_URL);

        DefaultJmsListenerContainerFactory jlcf = new DefaultJmsListenerContainerFactory();
        jlcf.setTransactionManager(ptm);
        jlcf.setSessionTransacted(true);
        jlcf.setConnectionFactory(cf);

        AtomicReference<Message> holder = new AtomicReference<>();

        SimpleJmsListenerEndpoint jle = new SimpleJmsListenerEndpoint();
        jle.setDestination(QUEUE);
        jle.setMessageListener(message -> {
            synchronized (holder) {
                holder.set(message);
                holder.notifyAll();
            }
        });
        DefaultMessageListenerContainer mlc = jlcf.createListenerContainer(jle);
        mlc.initialize();
        mlc.start();

        JmsTemplate jms = new JmsTemplate(cf);
        jms.setSessionTransacted(true);
        jms.setDefaultDestinationName(QUEUE);

        synchronized (holder) {
            Transaction tx = tm.begin();
            jms.convertAndSend("Hello");
            tx.commit();
            holder.wait();
        }
        assertNotNull(holder.get());
    }

    @Test
    public void testSpringLocalTx() throws Exception {
        ConnectionFactory cf = createCF(BROKER_URL, false);
        JmsTemplate jms = new JmsTemplate(cf);
        jms.setDefaultDestinationName(QUEUE);
        jms.setReceiveTimeout(1000);
        PlatformTransactionManager tm = new JmsTransactionManager(cf);
        TransactionTemplate localTx = new TransactionTemplate(tm);

        localTx.execute(ts -> {
            jms.convertAndSend("Hello");
            return null;
        });
        Object msg = localTx.execute(ts -> jms.receiveAndConvert());
        assertEquals("Hello", msg);

        localTx.execute(ts -> {
            jms.convertAndSend("Hello");
            ts.setRollbackOnly();
            return null;
        });
        msg = localTx.execute(ts -> jms.receiveAndConvert());
        assertNull(msg);
    }

    private List<String> consumeMessages(ConnectionFactory cf, String queue) {
        try (JMSContext ctx = cf.createContext()) {
            return consumeMessages(ctx, ctx.createQueue(queue));
        }
    }

    private List<String> consumeMessages(JMSContext context, Queue queue) {
        List<String> messages = new ArrayList<>();
        try (JMSConsumer consumer = context.createConsumer(queue)) {
            while (true) {
                String msg = consumer.receiveBody(String.class, 100);
                if (msg != null) {
                    messages.add(msg);
                } else {
                    return messages;
                }
            }
        }
    }

    private ConnectionFactory createCF(String brokerUrl) throws Exception {
        return createCF(brokerUrl, true);
    }

    private ConnectionFactory createCF(String brokerUrl, boolean xa) throws Exception {
        ConnectionFactory cf = ManagedConnectionFactoryBuilder.builder()
                .transaction(xa ? TransactionSupportLevel.XATransaction : TransactionSupportLevel.NoTransaction)
                .transactionManager(xa ? tm : null)
                .name("vmbroker" + brokerId++ )
                .connectionFactory(new ActiveMQConnectionFactory(brokerUrl),
                                   xa ? new ActiveMQXAConnectionFactory(brokerUrl) : null)
                .build();
        if (cf instanceof AutoCloseable) {
            closeables.add((AutoCloseable) cf);
        }
        return cf;
    }

}
