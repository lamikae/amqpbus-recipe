package recipe.amqpbus;

import java.io.IOException;
import java.util.Random;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONObject;
import org.json.JSONArray;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;


/* Implements request-response cycle over AMQP.

A topic exchange is used, with durable request queue,
and an exclusive autodelete response queue.
*/
public class AMQPRequestResponseImpl {
    private final Log log = LogFactory.getLog(getClass().getName());

    // taken from properties config
    private String host = null;
    private int port = -1;
    private String username = null;
    private String password = null;
    private String virtualHost = null;

    private Connection amqp_connection = null;

    private String exchangeName = null;
    private String serviceName = null;
    private String topic = null;

    private String requestQueue = null;
    private String requestRoutingKey = null;
    private String responseRoutingKey = null;
    
    private AMQPPublisher publisher = null;


    /** Unique query id. */
    public String newQid() {
        Random random = new Random();
        int q = random.nextInt(1000);
        String qid = "q" + Integer.toString(q);
        return qid;
    }

    /** Throwaway per-request consumer.
     */
    public AMQPConsumer newConsumer(String qid)
    throws IOException {
        return new AMQPConsumer(amqp_connection, exchangeName, responseRoutingKey, qid);
    }
    
    public AMQPPublisher newPublisher()
    throws IOException {
        return new AMQPPublisher(amqp_connection, exchangeName, requestQueue, requestRoutingKey);
    }
    
    /** Persistent AMQP connection.
     */
    private Connection newConnection()
    throws IOException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        factory.setHost(host);
        factory.setPort(port);
        Connection conn = factory.newConnection();
        log.info(String.format("%s connected to RabbitMQ @ %s:%d%s",
            serviceName, host, port, virtualHost));
        return conn;
    }
    
    /** Constructor.
     */
    public AMQPRequestResponseImpl(String exchangeName, String serviceName, String topic)
    throws IOException {
        // get configuration from properties file with ResourceBundle
        ResourceBundle config = ResourceBundle.getBundle("example");
        host = config.getString("rabbitmq.host");
        port = Integer.valueOf(config.getString("rabbitmq.port"));
        username = config.getString("rabbitmq.username");
        password = config.getString("rabbitmq.password");
        virtualHost = config.getString("rabbitmq.virtualHost");

        this.exchangeName = exchangeName;
        this.serviceName = serviceName;
        this.topic = topic;

        waitForConnection();

        // from this point on this should not crash even if RabbitMQ goes down
        try {
            // publisher queue for request
            this.requestQueue = serviceName+"_req";
            this.requestRoutingKey = topic+".request"; // routing key will be append unique id

            // consumer queue for response
            //String responseRoutingKey = topic+".response.#"; // accept all messages
            this.responseRoutingKey = topic+".response";

            publisher = newPublisher();
        }
        catch (Exception e) {
            log.fatal(e.getMessage());
            e.printStackTrace();
        }
    }

    public void finalize()
    throws Throwable {
        try {
            publisher.finalize();
            log.debug(serviceName+" closing connection");
            amqp_connection.close();
        }
        catch (Exception e) {
            log.error(e.getMessage());
            //e.printStackTrace();
        }
        super.finalize();
    }
    
    /** Overload communicate(). By default, wait for response.
     */
    public String communicate(JSONObject inner_json)
    throws java.io.UnsupportedEncodingException, org.json.JSONException, Throwable {
        return communicate(inner_json, true);
    }

    /** Sends a request to the topic exchange with unique routing key.
    The response is read from an exclusive autodelete queue.
    
    Wraps the given JSON into key "q" and generates a (pseudo-)unique ID.
    
    If wait_for_response is false, will return null and not wait for response.
    */
    public String communicate(JSONObject inner_json, boolean wait_for_response)
    throws java.io.UnsupportedEncodingException, org.json.JSONException, Throwable {
        AMQPConsumer consumer = null;
        String response = null;
        try {
            JSONObject json = new JSONObject();
            json.put("q",inner_json);

            /* request - response */
            if (wait_for_response) {
                // insert qid to denote we're waiting for response
                String qid = newQid();
                json.put("qid",qid);
                // create a consumer before publishing
                consumer = newConsumer(qid);
                publisher.send(json.toString(), qid);
                // wait for response!
                response = consumer.receive();
            }
            
            /* request, no response */
            else {
                publisher.send(json.toString());
            }
        }
        
        /* RabbitMQ is down ... */
        catch (com.rabbitmq.client.ShutdownSignalException sse) {
            log.error(sse.getMessage());
            log.debug(amqp_connection.getCloseReason());
            // wait for RabbitMQ
            waitForConnection();
            // create a new publisher
            publisher = newPublisher();
            // retry recursively
            return communicate(inner_json, wait_for_response);
        }
        catch (IOException ioe) {
            log.error(ioe.getMessage());
        }
        
        finally {
            if (consumer != null) {
                consumer.finalize();
            }
        }
        return response;
    }
    
    /** Waits for AMQP connection (forever if have to).
     */
    private void waitForConnection() {
        int time_to_wait = 60000; // one minute
        while((amqp_connection==null) || (!amqp_connection.isOpen())) {
            try {
                amqp_connection = newConnection();
                continue;
            }
            catch (IOException ioe) {
                //log.debug(ioe.getMessage());
            }
            catch (Exception xe) {
                log.error(xe.getMessage());
                //e.printStackTrace();
            }
            // wait
            try {
                log.warn(String.format("%s retrying RabbitMQ connection in %d seconds",
                    serviceName, time_to_wait/1000));
                Thread.currentThread().sleep(time_to_wait);
                continue;
            }
            catch (java.lang.InterruptedException ie) {
                log.debug(ie.getMessage());
            }
            
        } /* end of while */
    }
    
    /** This method is used in test suite. Calls private AMQPPublisher instance.
     */
    public void send(JSONObject json, String qid) 
    throws IOException {
        publisher.send(json.toString(), qid);
    }

}
