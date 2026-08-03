#!/bin/bash
rm strings.kt
rm *.bin
which kotlinc
kotlinc -version
kotlinc preprocess.kt -include-runtime -d preprocess.jar || exit 1
java -jar preprocess.jar
cp *.bin ../shared/src/androidMain/res/raw
cp *.bin ../shared/src/commonMain/resources
cp strings.kt ../shared/src/commonMain/kotlin/strings.kt
