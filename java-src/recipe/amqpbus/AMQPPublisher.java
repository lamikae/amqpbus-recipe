package recipe.amqpbus;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;


class AMQPPublisher {
    private final Log log = LogFactory.getLog(getClass().getName());

    private Channel channel = null;
    private String exchangeName = null;
    private String queueName = null;
    private String routingKey = null;

    /**
    Declares the exchange and request queue.
    
    NOTE: if existing already on RabbitMQ and parameters don't
    match, this will throw an error.
    */
    public AMQPPublisher(Connection amqp_connection, String exchangeName, String queueName, String routingKey)
    throws IOException {
        this.channel = amqp_connection.createChannel();
        this.exchangeName = exchangeName;
        this.queueName = queueName;
        this.routingKey = routingKey;

        boolean durable = true;
        channel.exchangeDeclare(exchangeName, "topic", durable);
        channel.queueDeclare(queueName, durable, false, false, null);
        //channel.queueBind(queueName, exchangeName, routingKey);
        log.debug("Publisher using queue "+queueName);
    }

    public void finalize()
    throws Throwable {
        try {
            log.debug("Publisher "+queueName+" closing channel");
            channel.close();
        }
        catch (Exception e) {
            log.error(e.getMessage());
            //e.printStackTrace();
        }
        super.finalize();
    }

    /** Overload send(). By default, publish without qid (=no response).
     */
    public void send(String message)
    throws IOException {
        send(message, null);
    }

    /** Sends the message to the channel, 
        and appends the given query id to the routing key.
     */
    public void send(String message, String qid)
    throws IOException {
        String bindingKey = routingKey;
        if (qid != null) {
            bindingKey += "."+qid;
        }
        log.info("publishing query to "+queueName+" with binding key: "+bindingKey+"\n"+message);
        byte[] messageBodyBytes = message.getBytes();
        channel.basicPublish(
            exchangeName,
            bindingKey,
            MessageProperties.PERSISTENT_TEXT_PLAIN,
            messageBodyBytes);
    }
}