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
package org.ops4j.pax.transx.jms.impl;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateRuntimeException;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.Serializable;

import static org.ops4j.pax.transx.jms.impl.Utils.unsupported;

public class JMSContextImpl implements JMSContext {

    private final Connection connection;
    private final int sessionMode;
    private boolean autoStart = true;
    private boolean closed;
    private Session session;
    private MessageProducer sharedProducer;
    /**
     * Client ACK needs to hold last acked messages, so context.ack calls will be respected.
     */
    private volatile Message lastMessagesWaitingAck;

    public JMSContextImpl(final Connection connection,
                          final int sessionMode) {
        this.connection = connection;
        this.sessionMode = sessionMode;
    }

    protected Session getSession() {
        if (session == null) {
            synchronized (this) {
                if (closed) {
                    throw new IllegalStateRuntimeException("Context is closed");
                }
                if (session == null) {
                    try {
                        session = connection.createSession(sessionMode);
                    } catch (JMSException e) {
                        throw Utils.convertToRuntimeException(e);
                    }
                }
            }
        }
        return session;
    }

    @Override
    public void start() {
        try {
            connection.start();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public void stop() {
        try {
            connection.stop();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public synchronized void close() {
        try {
            closed = true;
            connection.close();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    //----- Session state management -----------------------------------------//

    @Override
    public void acknowledge() {
        if (lastMessagesWaitingAck != null) {
            try {
                lastMessagesWaitingAck.acknowledge();
            } catch (JMSException jmse) {
                throw Utils.convertToRuntimeException(jmse);
            }
        }
    }

    @Override
    public void commit() {
        try {
            getSession().commit();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public void rollback() {
        try {
            getSession().rollback();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public void recover() {
        try {
            getSession().recover();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public void unsubscribe(String name) {
        try {
            getSession().unsubscribe(name);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    //----- Message Factory methods ------------------------------------------//

    @Override
    public BytesMessage createBytesMessage() {
        try {
            return getSession().createBytesMessage();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public MapMessage createMapMessage() {
        try {
            return getSession().createMapMessage();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public Message createMessage() {
        try {
            return getSession().createMessage();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public ObjectMessage createObjectMessage() {
        try {
            return getSession().createObjectMessage();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) {
        try {
            return getSession().createObjectMessage(object);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public StreamMessage createStreamMessage() {
        try {
            return getSession().createStreamMessage();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public TextMessage createTextMessage() {
        try {
            return getSession().createTextMessage();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public TextMessage createTextMessage(String text) {
        try {
            return getSession().createTextMessage(text);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    //----- Destination Creation ---------------------------------------------//

    @Override
    public Queue createQueue(String queueName) {
        try {
            return getSession().createQueue(queueName);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public Topic createTopic(String topicName) {
        try {
            return getSession().createTopic(topicName);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public TemporaryQueue createTemporaryQueue() {
        try {
            return getSession().createTemporaryQueue();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public TemporaryTopic createTemporaryTopic() {
        try {
            return getSession().createTemporaryTopic();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    //----- JMSContext factory methods --------------------------------------//

    @Override
    public JMSContext createContext(int sessionMode) {
        return unsupported("createContext");
    }

    //----- JMSProducer factory methods --------------------------------------//

    @Override
    public JMSProducer createProducer() {
        try {
            if (sharedProducer == null) {
                synchronized (this) {
                    if (sharedProducer == null) {
                        sharedProducer = getSession().createProducer(null);
                    }
                }
            }

            return new JMSProducerImpl(this, sharedProducer);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    //----- JMSConsumer factory methods --------------------------------------//

    @Override
    public JMSConsumer createConsumer(Destination destination) {
        try {
            return startIfNeeded(new JMSConsumerImpl(this, getSession().createConsumer(destination)));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public JMSConsumer createConsumer(Destination destination, String selector) {
        try {
            return startIfNeeded(new JMSConsumerImpl(this, getSession().createConsumer(destination, selector)));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public JMSConsumer createConsumer(Destination destination, String selector, boolean noLocal) {
        try {
            return startIfNeeded(new JMSConsumerImpl(this, getSession().createConsumer(destination, selector, noLocal)));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public JMSConsumer createDurableConsumer(Topic topic, String name) {
        try {
            return startIfNeeded(new JMSConsumerImpl(this, getSession().createDurableConsumer(topic, name)));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public JMSConsumer createDurableConsumer(Topic topic, String name, String selector, boolean noLocal) {
        try {
            return startIfNeeded(new JMSConsumerImpl(this, getSession().createDurableConsumer(topic, name, selector, noLocal)));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public JMSConsumer createSharedConsumer(Topic topic, String name) {
        try {
            return startIfNeeded(new JMSConsumerImpl(this, getSession().createSharedConsumer(topic, name)));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public JMSConsumer createSharedConsumer(Topic topic, String name, String selector) {
        try {
            return startIfNeeded(new JMSConsumerImpl(this, getSession().createSharedConsumer(topic, name, selector)));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public JMSConsumer createSharedDurableConsumer(Topic topic, String name) {
        try {
            return startIfNeeded(new JMSConsumerImpl(this, getSession().createSharedDurableConsumer(topic, name)));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public JMSConsumer createSharedDurableConsumer(Topic topic, String name, String selector) {
        try {
            return startIfNeeded(new JMSConsumerImpl(this, getSession().createSharedDurableConsumer(topic, name, selector)));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    //----- QueueBrowser Factory Methods -------------------------------------//

    @Override
    public QueueBrowser createBrowser(Queue queue) {
        try {
            return startIfNeeded(getSession().createBrowser(queue));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String selector) {
        try {
            return startIfNeeded(getSession().createBrowser(queue, selector));
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    //----- Get or Set Context and Session values ----------------------------//

    @Override
    public boolean getAutoStart() {
        return autoStart;
    }

    @Override
    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    @Override
    public String getClientID() {
        try {
            return connection.getClientID();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public void setClientID(String clientID) {
        try {
            connection.setClientID(clientID);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public ExceptionListener getExceptionListener() {
        try {
            return connection.getExceptionListener();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public void setExceptionListener(ExceptionListener listener) {
        try {
            connection.setExceptionListener(listener);
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public ConnectionMetaData getMetaData() {
        try {
            return connection.getMetaData();
        } catch (JMSException jmse) {
            throw Utils.convertToRuntimeException(jmse);
        }
    }

    @Override
    public int getSessionMode() {
        return sessionMode;
    }

    @Override
    public boolean getTransacted() {
        return sessionMode == JMSContext.SESSION_TRANSACTED;
    }

    //----- Internal implementation methods ----------------------------------//

    private <T> T startIfNeeded(T consumer) throws JMSException {
        if (getAutoStart()) {
            connection.start();
        }
        return consumer;
    }

    /**
     * this is to ensure Context.acknowledge would work on ClientACK
     */
    Message setLastMessage(final Message lastMessageReceived) {
        if (sessionMode == CLIENT_ACKNOWLEDGE) {
            lastMessagesWaitingAck = lastMessageReceived;
        }
        return lastMessageReceived;
    }

}
