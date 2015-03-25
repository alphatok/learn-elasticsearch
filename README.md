###INSTALL(WINDOWS)###
***
1. download [Elasticsearch](https://www.elastic.co/downloads/elasticsearch) from official site
2. unzip 
3. install service(recommended) 
###RUN ELASTIC SEARCH(WINDOWS)###
***
**MAKE SURE JAVA_HOME EXIST**

1. Create es-service.bat as follows
	>pushd %ES_HOME%\bin    
	>call service.bat start   
	>popd   
1. double click es-service.bat

