Copy-Item "CuTAPI-Core/build/libs/CuTAPI-Core-1.0-all.jar" -Destination "./server/plugins/CuTAPI.jar"
Copy-Item "ExamplePlugin/build/libs/ExamplePlugin-1.0-all.jar" -Destination "./server/plugins/ExamplePlugin.jar"
cd server
java -Xmx3G -Xms256M -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar paper.jar nogui