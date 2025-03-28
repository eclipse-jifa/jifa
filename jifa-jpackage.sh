./gradlew clean build -x test

# Path of the input directory that contains the files to be packaged (absolute path or relative to the current directory)
# All files in the input directory will be packaged into the application image.
inputPath="server/build/libs"
destinationPath="server/build/distributions"

name="Eclipse Jifa"
osName=$(uname -s)
arch=$(uname -m)
version="1.0.0"
packageName="${name}-${osName}-${arch}"

echo "Packaging ${name}..."

# For "jpackage", please refer to: https://docs.oracle.com/en/java/javase/17/jpackage/packaging-overview.html#GUID-C1027043-587D-418D-8188-EF8F44A4C06A
jpackage --name "${name}" \
        --vendor "Eclipse" \
        --app-version "${version}" \
        --description "Eclipse Jifa (abbreviated as Jifa) stands for \"Java Issues Finding Assistant\". Online Analyzer for Heap Dump, GC Log, Thread Dump and JFR File." \
        --input "${inputPath}" \
        --dest "${destinationPath}" \
        --main-jar jifa.jar \
        --java-options --add-opens=java.base/java.lang=ALL-UNNAMED \
        --java-options --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
        --java-options -Djdk.util.zip.disableZip64ExtraFieldValidation=true \
        --java-options -Djifa.role=standalone-worker \
        --java-options -Djifa.open-browser-when-ready=true \
        --about-url https://eclipse-jifa.github.io/jifa/

mv "${destinationPath}/${name}-${version}.dmg" "${destinationPath}/${packageName}-${version}.dmg"

echo "Complete!"