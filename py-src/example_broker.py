#!/usr/bin/env python
# -*- coding: utf-8 -*-
import sys
from signal import signal, SIGTERM, SIGINT, SIGABRT, SIGQUIT
from time import sleep, ctime
import logging
import json
import settings
from amqpbus import IMessageBroker

class ExampleBroker(IMessageBroker):
    service_name = 'ExampleBroker'
    exchange_name = 'TestExchange'
    topic = 'example'
    amqp_connection_settings = settings.AMQP_CONNECTION

    def dispatch(self, message, request, qid):
        """ AMQP -> RPC dispatcher.
        """
        if qid:
            logging.info('-------------------\nreceived request %s:\n%s\n-------------------' % (qid,request))
            # call remote RPC
            # this message has an qid so it expects a response
            response = {'msg': ctime()}
            message.ack()
            # return the response to AMQP, the caller should be listening ..
            self.return_response(response,qid)
        else:
            logging.info('-------------------\nreceived request:\n%s\n-------------------' % request)
            # no qid, so do something stateless .. 
            print ctime()
            message.ack()


if __name__ == '__main__':
    """Starts the AMQP consumer loop."""
    # setup logger
    console = logging.StreamHandler()
    log = logging.getLogger()
    log.addHandler(console)
    log.setLevel(logging.DEBUG)

    # initialize broker and bind stop signals
    broker = ExampleBroker()
    for sgnl in [SIGABRT, SIGINT, SIGQUIT, SIGTERM]:
        signal(sgnl, broker.stop)

    # start broker thread while main thread sleeps waiting for broker to stop
    broker.start()
    sleep(1)
    while not broker.stopped:
        sleep(10)

    # clean exit
    sys.exit(0)
