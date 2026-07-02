@ECHO OFF
SET DIRNAME=%~dp0
SET WRAPPER_JAR=%DIRNAME%gradle\wrapper\gradle-wrapper.jar
SET WRAPPER_SHARED_JAR=%DIRNAME%gradle\wrapper\gradle-wrapper-shared.jar
SET WRAPPER_CLI_JAR=%DIRNAME%gradle\wrapper\gradle-cli.jar

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Gradle wrapper jars are missing. Please generate or download the Gradle wrapper binary first.
  EXIT /B 1
)

IF NOT EXIST "%WRAPPER_SHARED_JAR%" (
  ECHO Gradle wrapper jars are missing. Please generate or download the Gradle wrapper binary first.
  EXIT /B 1
)

IF NOT EXIST "%WRAPPER_CLI_JAR%" (
  ECHO Gradle wrapper jars are missing. Please generate or download the Gradle wrapper binary first.
  EXIT /B 1
)

java -classpath "%WRAPPER_JAR%;%WRAPPER_SHARED_JAR%;%WRAPPER_CLI_JAR%" org.gradle.wrapper.GradleWrapperMain %*
