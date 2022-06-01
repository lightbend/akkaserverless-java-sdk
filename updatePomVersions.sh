# this script updates all maven projects versions (maven-java and samples) to align with the current sdk version
# if SDK_VERSION env var is defined, it will use it, otherwise it will take the version from sbt
# after running this script, you may run local tests or simply send a PR with the updates. 

# you can pass the path to a sample to only change the version for single sample, 
# eg: ./updatePomVersions.sh samples/java-eventsourced-counter
# useful when testing out new functionality locally.


if [ -z ${SDK_VERSION+x} ]; then 
  SDK_VERSION=$(sbt "print sdkJava/version" | tail -1)
fi


if [ $1 ]; then
  PROJ=$1
else
  PROJ="samples/*java-*"
fi

sbt publishM2;publishLocal
(
  cd maven-java
  mvn versions:set -DnewVersion=$SDK_VERSION
  mvn install

  # cleanup
  rm pom.xml.versionsBackup
  rm */pom.xml.versionsBackup

  # revert
  git checkout pom.xml
  git checkout */pom.xml
)

for i in $PROJ
do
  sed  -i .versionsBackup "s/<kalix-sdk.version>\(.*\)<\/kalix-sdk.version>/<kalix-sdk.version>$SDK_VERSION<\/kalix-sdk.version>/" $i/pom.xml
  rm $i/pom.xml.versionsBackup
done
