package example;
/**
Please make sure the example_broker.py daemon is running while running the main()
*/
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import recipe.amqpbus.*;

public class ExampleRequest
{
    private final Log log = LogFactory.getLog(getClass().getName());
    private AMQPRequestResponseImpl amq = null;

    public ExampleRequest()
    throws java.io.IOException
    {
        String serviceName = "ExampleBroker";
        String exchangeName = "TestExchange";
        String topic = "example";
        amq = new AMQPRequestResponseImpl(exchangeName,serviceName,topic);
    }

    public void finalize() {
        try { amq.finalize(); }
        catch (Throwable t) {}
    }

    public static void main(String args[]) {
        ExampleRequest obj = null;
        try {
            obj = new ExampleRequest();
            // this will send a request without response
            obj.requestNoResponse();
            
            System.out.println("-----------------------------------------");
            // this will wait for the response and print it
            obj.requestWithResponse();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
        finally {
            obj.finalize();
        }
    }

    /*
        Example of requesting over AMQP without response.
     */
    public void requestNoResponse()
    throws org.json.JSONException, java.io.UnsupportedEncodingException, Throwable {
        JSONObject json = new JSONObject();
        json.put("q","hello AMQP backend, I am Java");
        amq.communicate(json, false); // false == no response
    }

    /*
        Example of requesting over AMQP with response.
     */
    public void requestWithResponse()
    throws org.json.JSONException, java.io.UnsupportedEncodingException, Throwable {
        JSONObject json = new JSONObject();
        json.put("q","time can you get me please?");

        String response = amq.communicate(json); // by default returns a response
        JSONObject reply = new JSONObject(response);
        System.out.println("Response from AMQP: "+reply.toString());
    }
}