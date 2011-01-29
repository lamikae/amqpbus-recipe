# -*- coding: utf-8 -*-
import unittest
import logging
from threading import Thread, Lock, activeCount, enumerate
import json
from jsonrpclib.SimpleJSONRPCServer import SimpleJSONRPCServer

class MockRPCServiceProvider(Thread):
    """Implement a mock of a remote 3rd party JSON-RPC service provider."""

    url = ('localhost', 8484)
    messages = []
    name = ''
    ident = 0

    def __init__(self, *args, **kwargs):
        Thread.__init__(self)
        self.server = SimpleJSONRPCServer(self.url)
        self.server.register_function(self.push, 'push')
        self.server.verbose = False
        self.messages = []

    def run(self):
        logging.debug("MockRPCServiceProvider thread %i start" % self.ident)
        try:
            self.server.serve_forever()
        except:
            pass
        logging.debug("MockRPCServiceProvider thread %i exit" % self.ident)
        exit()

    def stop(self):
        logging.debug("Stop MockRPCServiceProvider")
        self.server.shutdown()
        self.server.server_close()

    def register_function(self,*args,**kwargs):
        return self.server.register_function(*args,**kwargs)

    def push(self, msg, *args):
        logging.debug("saving message: %s" % msg)
        self.messages.append(msg)
        return "ok"

    def get_messages(self):
        return self.messages

