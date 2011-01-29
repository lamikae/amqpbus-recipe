#!/usr/bin/env python
# -*- coding: utf-8 -*-
import unittest
import logging
from time import sleep
from random import randrange
from carrot.connection import BrokerConnection, AMQPConnectionException
from carrot.messaging import Consumer, Publisher
import json
import settings
from amqpbus import IMessageBroker
from test_helpers import MockRPCServiceProvider

# RPC server for testing.
# Needs to be accessed outside the scope of the test class.
# Started at test setUp, and closed after all tests in this module have ran.
global test_rpc_service
test_rpc_service = None

class AmqpbusTests(unittest.TestCase):
    """Tests the AMQP communication.
    """
    test_connection = None

    def setUp(self):
        # single global RPC server thread, ok in test framework
        global test_rpc_service
        if not test_rpc_service:
            test_rpc_service = MockRPCServiceProvider()
        if not self.test_connection:
            self.test_connection = BrokerConnection(**settings.AMQP_CONNECTION)

    def tearDown(self):
        test_rpc_service.stop()
        self.test_connection.close()

    def test_consumer(self):
        """AMQP->RPC->AMQP
        
        Send a AMQP message, and test RPCConsumer response.
        Uses a mock RPC server that the consumer will call.
        Check the consumer AMQP response with test consumer.
        """
        class TestConsumer(IMessageBroker):
            service_name = 'TestConsumer'
            exchange_name = 'Test'
            topic = 'test'
            amqp_connection_settings = settings.AMQP_CONNECTION

            def dispatch(self, message, request, qid):
                """ AMQP -> RPC dispatcher.
                """
                logging.info('dispatching AMQP -> RPC')
                response = {}
                if qid:
                    # call remote RPC
                    # this message has an qid so it expects a response
                    response['msg'] = test_rpc_service.push(request)
                    message.ack()
                    # return the response to AMQP, the caller should be listening ..
                    self.return_response(response,qid)
                else:
                    # no qid, so do something stateless .. 
                    print request
                    message.ack()

        try:
            consumer = TestConsumer()
            self.assertEquals('test.request.*',consumer.binding_key)
            self.assertEquals('test.response.%s',consumer.response_routing_key)

            consumer.start()
            test_rpc_service.start()
            test_rpc_service.messages = []
            # allow consumer to start
            sleep(0.2)
            self.assert_(not consumer.stopped)
            self.assert_(consumer.isAlive())

            # test variables
            qid = str(randrange(0,999999))
            jsondata = {'msg':'hello rpc'}

            _publisher = Publisher(
                connection=self.test_connection,
                exchange='Test',
                exchange_type="topic",
                routing_key='test.request.'+qid,
                )

            # test channel
            backend = self.test_connection.create_backend()
            backend.queue_declare(
                queue="test",
                durable=False,
                exclusive=False,
                auto_delete=True,)
            backend.queue_bind("test",'Test','test.response.'+qid)
            _consumer = Consumer(
                connection=self.test_connection,
                exchange='Test',
                exchange_type="topic",
                queue="test",
                )
            _consumer.discard_all()

            logging.debug('publishing JSON message to RPC')
            data_on_the_wire = json.dumps({'q': jsondata, 'qid': qid})
            _publisher.send(data_on_the_wire)

            # allow data to pass the wire
            sleep(0.2)

            # retrieve dispatcher response
            response = _consumer.fetch()
            self.assert_(response, 'No response')
            data = json.loads(response.payload)
            self.assert_(len(data['msg']) > 0) # assert non-empty response
            self.assertEquals('ok',data['msg'])

            # check dispatcher RPC function
            self.assert_(len(test_rpc_service.messages) > 0, 'Message did not arrive')
            self.assertEquals(test_rpc_service.messages[0], jsondata)
        finally:
            try:
                consumer.stop()
            except:
                pass
            try:
                _consumer.close()
            except:
                pass
            test_rpc_service.stop()
            self.assert_(consumer.stopped)

if __name__ == '__main__':
    unittest.main()