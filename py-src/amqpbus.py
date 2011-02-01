# -*- coding: utf-8 -*-
import os, sys
from time import sleep
import traceback
import logging
from threading import Thread, Event, Lock, activeCount, enumerate
from socket import error as socket_error
from carrot.connection import BrokerConnection, AMQPConnectionException
from carrot.messaging import Consumer, Publisher
import json
from jsonrpclib import Server as JSONRPCServerCall
from jsonrpclib.SimpleJSONRPCServer import SimpleJSONRPCServer


class IMessageBroker(Consumer, Thread):
    """Interface for communicating JSON over AMQP.
    
    Consumer thread interface that can also publish a response for each request with unique id.
    Consumer and publisher use the same AMQP connection.

    Subclass this to implement a worker thread. See the example in the tests.
    """
    # implemented in subclass
    service_name = None
    amqp_connection = None
    amqp_connection_settings = None
    exchange_name = None
    topic = None

    # dynamic instance variables
    binding_key = None
    response_routing_key = None
    req_queue_name = None

    def __init__(self, *args, **kwargs):
        logging.debug("%s starting up" % self.__unicode__())

        # set instance variables
        self.binding_key = '.'.join([self.topic, 'request', '*'])
        self.response_routing_key = '.'.join([self.topic, 'response', '%s'])
        self.req_queue_name = '%s_req' % self.service_name

        # initialize the thread
        Thread.__init__(self, *args, **kwargs)
        # wait for AMQP connection, declare exchange and request queue
        # and bind the consumer
        self._wait_connection()
        self._declare_channel()
        self._init_consumer()

    def __exit__(self, e_type, e_value, e_trace):
        logging.debug('%s exiting' % self.__unicode__())
        if e_type:
            logging.error(e_type(e_value))

    def __unicode__(self):
        return self.service_name

    def _declare_channel(self):
        """Declares exchange and request queue."""
        backend = self.amqp_connection.create_backend()
        backend.exchange_declare(
            exchange=self.exchange_name,
            type="topic",
            durable=True,
            auto_delete=False,)
        backend.queue_declare(
            queue=self.req_queue_name,
            durable=True,
            exclusive=False,
            auto_delete=False,)
        backend.queue_bind(self.req_queue_name,self.exchange_name,self.binding_key)
        logging.debug("%s queue %s bound to %s" % (
            self.exchange_name,
            self.req_queue_name,
            self.binding_key))

    def _wait_connection(self,time_to_wait=60):
        """Waits for RabbitMQ connection. """
        _connected = False
        while(not _connected):
            try:
                self.amqp_connection = BrokerConnection(**self.amqp_connection_settings)
                if self.amqp_connection.connection:
                    _connected = True
                    logging.debug('%s connected to AMQP @ %s:%i' % (
                        self.service_name,
                        self.amqp_connection_settings['hostname'],
                        self.amqp_connection_settings['port']))
            except socket_error:
                logging.warn("%s waiting %i sec for RabbitMQ connection..." % (
                    self.service_name, time_to_wait))
                sleep(time_to_wait)

    def _init_consumer(self):
        Consumer.__init__(self,
            connection=self.amqp_connection,
            exchange=self.exchange_name,
            exchange_type="topic",
            queue=self.req_queue_name,
            )
        # register the callback method
        self.register_callback(self._request_filter) # calls "dispatch"
        self._stop = Event()
        self._stop.set() # set to stopped state

    def _request_filter(self, message_data, message):
        """Filter for incoming AMQP requests.
        
        Expects JSON input as message payload. It has a metadata wrapper with keys
        "q" and "qid", for actual message data and an identifier, respectively.
        Example:
        
        {"qid": 293742, "q": {"hello my friend . we speak over the RPC now . "}}
        """
        logging.debug("consumer received message: \n%s" % message.delivery_info)
        #routing_key = message.delivery_info['routing_key']
        qid = None
        try:
            data = json.loads(message_data)
            qid = data.get('qid')
            request = data.get('q')
            self.dispatch(message, request, qid)
        except:
            logging.error(sys.exc_info()[1])
            return

    def dispatch(self, message, request, qid):
        """AMQP "request" callback handler.

        - message: carrot Message
        - request: JSON data
        - qid: unique request identifier
        
        Do whatever your worker is supposed to do here.
        You can return a response by calling self.return_response(json_response, qid).
        """
        raise NotImplementedError, "Write your own subclass"

    def return_response(self, response, qid):
        """AMQP "response" handler.
        
        Publishes a response message to a temporary queue.
        - response is json, qid is string
        """
        message = json.dumps(response)
        routing_key = self.response_routing_key % qid
        logging.debug("response to %s with routing_key: %s, message: \n%s" % (self.exchange_name, routing_key, message))
        try:
            publisher = Publisher(
                connection=self.amqp_connection,
                exchange=self.exchange_name,
                exchange_type="topic",
                routing_key=routing_key,
                )
            publisher.send(message)
            publisher.close()
        except:
            """Trying to send with broken connection.

            Handle gracefully by waiting for connection and publish again."""
            logging.error('%s AMQP error: %s' % (self.service_name, sys.exc_info()[1]))
            self._wait_connection(5)
            self.return_response(response, qid)

    @property
    def stopped(self):
        return self._stop.isSet()

    def run(self):
        self._stop.clear() # started
        logging.info('%s entering AMQP consumer loop' % self.__unicode__())
        it = self.iterconsume()
        while not self.stopped:
            try:
                it.next()
            except KeyboardInterrupt:
                logging.warn("KB interrupt")
            except (AMQPConnectionException, IOError, AttributeError):
                """AMQPConnectionException or IOError if the AMQ socket closes.
                AttributeError if channel has been closed.

                Handle gracefully by waiting for connection and re-enter consumer loop,
                unless the service stop flag is set.
                """
                if self.stopped:
                    break

                # else reconnect
                logging.error('%s AMQP error: %s' % (self.service_name, sys.exc_info()[1]))
                self._wait_connection()
                self._init_consumer()
                self._stop.clear() # continue in started mode
                it = self.iterconsume()
                continue
            except StopIteration:
                """Happens at cancel() and on lost connection, 
                if IOError exception handler could not restore consumption loop."""
                logging.debug('%s %s' % (self.__unicode__(), sys.exc_info()[1]))
                logging.info('%s break out of consumer loop' % self.__unicode__())
                self._stop.set()
                break
            except:
                logging.error("BUG")
                logging.error('%s %s' % (self.__unicode__(), sys.exc_info()[1]))
                traceback.print_tb(sys.exc_info()[2],10)

        try:
            self.amqp_connection.close()
        except:
            logging.debug('%s %s' % (self.__unicode__(), sys.exc_info()[1]))

        logging.info('%s thread exiting' % self.__unicode__())
        exit()

    def stop(self,*args):
        """Stop consumer"""
        logging.debug('%s stop' % self.__unicode__())
        try:
            try:
                # set the Event
                self._stop.set()
                # release the consumer from iterconsume
                self.cancel()
            except:
                pass
                #logging.debug('%s %s' % (self.__unicode__(), sys.exc_info()[1]))
        finally:
            try:
                self.amqp_connection.close()
            except IOError:
                logging.debug(sys.exc_info()[1])
            if self.amqp_connection.pool:
                self.amqp_connection.release()


