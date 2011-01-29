package recipe.amqpbus;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONObject;
import org.json.JSONArray;

import recipe.amqpbus.AMQPPublisher;
import recipe.amqpbus.AMQPConsumer;
import recipe.amqpbus.AMQPRequestResponseImpl;


public class AMQPRequestResponseImplTest {
    
    private final Log log = LogFactory.getLog("AMQPRequestResponseImplTest");

    private String exchangeName = "Vivifacile";

    @Test
    public void requestWithoutResponse()
    throws Throwable, java.lang.InterruptedException {
        AMQPRequestResponseImpl amq = new AMQPRequestResponseImpl(exchangeName,"PythonBackendTest","python");
        assertNotNull(amq);
        
        JSONObject json = new JSONObject();
        json.put("name","obelix");
        try {
            String response = amq.communicate(json,false);
            assertNull(response);
        }
        finally {
            amq.finalize();
        }
    }

    @Test
    public void communicateWithPython() 
    throws Throwable, java.lang.InterruptedException {
        AMQPRequestResponseImpl amq = new AMQPRequestResponseImpl(exchangeName,"PythonBackendTest","python");
        assertNotNull(amq);
        
        JSONObject json = new JSONObject();
        json.put("name","idefix");

        log.warn("Make sure Python service acotel-invia is up in test mode (bin/run-vivifacile-test-server.py)");
        try {
            String response = amq.communicate(json);
            JSONObject reply = new JSONObject(response);
            assert(json.equals(reply.get("q")));
            assertEquals("python",reply.get("from"));
        }
        finally {
            amq.finalize();
        }
    }

    @Test
    public void communicateWithRuby() 
    throws Throwable, java.lang.InterruptedException {
        AMQPRequestResponseImpl amq = new AMQPRequestResponseImpl(exchangeName,"RubyBackendTest","ruby");
        assertNotNull(amq);
        
        JSONObject json = new JSONObject();
        json.put("name","vitalstatistix");

        log.warn("Make sure Ruby service acotel-esiti is up in test mode (bin/run-vivifacile-test-server.rb)");
        try {
            String response = amq.communicate(json);
            JSONObject reply = new JSONObject(response);
            assert(json.equals(reply.get("q")));
            assertEquals("ruby",reply.get("from"));
        }
        finally {
            amq.finalize();
        }
    }

    @Test
    public void concurrentMessagingWithPython() 
    throws Throwable {
        String serviceName = "PythonBackendTest";
        AMQPRequestResponseImpl amq1 = new AMQPRequestResponseImpl(exchangeName,serviceName,"python");
        assertNotNull(amq1);
        AMQPRequestResponseImpl amq2 = new AMQPRequestResponseImpl(exchangeName,serviceName,"python");
        assertNotNull(amq2);
        AMQPRequestResponseImpl amq3 = new AMQPRequestResponseImpl(exchangeName,serviceName,"python");
        assertNotNull(amq3);

        try {
            JSONObject json1 = new JSONObject();
            json1.put("number","1");
            JSONObject json2 = new JSONObject();
            json2.put("number","2");
            JSONObject json3 = new JSONObject();
            json3.put("number","3");

            log.warn("Make sure Python service acotel-invia is up in test mode (bin/run-vivifacile-test-server.py)");
            String response1 = amq1.communicate(json1);
            JSONObject reply1 = new JSONObject(response1);
            JSONObject _q1 = (JSONObject)reply1.get("q");
            assertEquals("1",_q1.get("number"));

            String response3 = amq3.communicate(json3);
            JSONObject reply3 = new JSONObject(response3);
            JSONObject _q3 = (JSONObject)reply3.get("q");
            assertEquals("3",_q3.get("number"));

            String response2 = amq2.communicate(json2);
            JSONObject reply2 = new JSONObject(response2);
            JSONObject _q2 = (JSONObject)reply2.get("q");
            assertEquals("2",_q2.get("number"));
        }
        finally {
            amq1.finalize();
            amq2.finalize();
            amq3.finalize();
        }
    }
}


