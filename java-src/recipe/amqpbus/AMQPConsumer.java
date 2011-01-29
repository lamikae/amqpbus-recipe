package recipe.amqpbus;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.QueueingConsumer;


class AMQPConsumer {
    private final Log log = LogFactory.getLog(getClass().getName());

    private Channel channel = null;
    private String exchangeName = null;
    private String routingKey = null;
    private String qid = null;
    
    private String queueName = null;
    
    public String getQid() {
        return this.qid;
    }


    /* A throwaway consumer, declares a new queue for each request.
    
        Creates a non-durable, exclusive, autodelete queue with an auto-generated name,
        and binds a unique routing key to it.
     */
    public AMQPConsumer(Connection amqp_connection, String exchangeName, String routingKey, String qid)
    throws IOException {
        this.channel = amqp_connection.createChannel();
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.qid = qid;

        String bindingKey = routingKey+"."+qid;

        queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, exchangeName, bindingKey);
        // purge old messages?
        //channel.queuePurge(queueName);
        this.queueName = queueName;

        log.debug("Consumer "+queueName+" bound to "+bindingKey);
    }

    public void finalize()
    throws Throwable {
        try {
            log.debug("Consumer "+queueName+" closing channel");
            channel.close();
        }
        catch (Exception e) {
            log.error(e.getMessage());
            //e.printStackTrace();
        }
        super.finalize();
    }

    public String receive()
    throws IOException {
        boolean noAck = false;
        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume(queueName, noAck, consumer);
        QueueingConsumer.Delivery delivery;

        // wait for next delivery..
        while (true /* decide whether to continue reading */) {
            try {
                delivery = consumer.nextDelivery();
            }
            catch (InterruptedException ie) {
                continue;
            }

            String message = new String(delivery.getBody(), "UTF-8");
            //message = message.replaceAll( " ", "" ); // ???
            log.info(queueName+" received message: \n" + message);
            
            // ack message
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            
            // return bytes
            return message;
        }
    }
}