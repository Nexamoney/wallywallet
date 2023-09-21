#!/bin/bash
rm strings.kt
rm *.bin
which kotlinc
kotlinc -version
kotlinc preprocess.kt -include-runtime -d preprocess.jar || exit 1
java -jar preprocess.jar
cp *.bin ../src/src/androidMain/res/raw
cp *.bin ../src/src/commonMain/resources
cp strings.kt ../src/src/commonMain/kotlin/strings.kt
