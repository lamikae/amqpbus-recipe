JARS="lib/junit-4.6.jar:lib/json.jar:lib/rabbitmq-client-1.8.1.jar:lib/commons-logging.jar:lib/commons-io.jar"

compile:
	javac -cp .:$(JARS) -d build recipe/amqpbus/*.java example/*.java

run: compile
	java -cp .:build:$(JARS) example.ExampleRequest

all: run

.PHONY: run
