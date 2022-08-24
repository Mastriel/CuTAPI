Copy-Item "CuTAPI-Core/build/libs/CuTAPI-Core-1.0-all.jar" -Destination "./server/plugins/CuTAPI.jar"
Copy-Item "ExamplePlugin/build/libs/ExamplePlugin-1.0-all.jar" -Destination "./server/plugins/ExamplePlugin.jar"
cd server
java -Xmx3G -Xms256M -jar paper.jar nogui