TARGET=amqpbus-recipe.zip

zip:
	rm -rf .tmp
	mkdir -p .tmp/recipe
	cp -a *-src .tmp/recipe
	find .tmp -type l -exec rm {} \;
	find .tmp -name *.pyc -exec rm {} \;
	cd .tmp/recipe/java-src && make compile && rm Makefile
	links amqpbus-blog.html -dump > .tmp/recipe/README
	cd .tmp && zip -r $(TARGET) .
	mv .tmp/$(TARGET) .
	unzip -l $(TARGET)
	#rm -rf .tmp

