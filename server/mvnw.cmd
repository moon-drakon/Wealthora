@ECHO OFF
SETLOCAL
SET "MAVEN_PROJECTBASEDIR=%~dp0."
IF "%JAVA_HOME%"=="" (
  ECHO JAVA_HOME must point to JDK 25. 1>&2
  EXIT /B 1
)
SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
IF NOT EXIST "%JAVA_EXE%" (
  ECHO JAVA_HOME does not contain bin\java.exe. 1>&2
  EXIT /B 1
)
"%JAVA_EXE%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -classpath "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
SET MAVEN_EXIT_CODE=%ERRORLEVEL%
ENDLOCAL & EXIT /B %MAVEN_EXIT_CODE%
