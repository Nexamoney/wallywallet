FROM ubuntu:18.04
MAINTAINER Andrew Stone

ENV VERSION_SDK_TOOLS "4333796"

ENV ANDROID_HOME "/opt/adk"
ENV PATH "$PATH:${ANDROID_HOME}/tools"
ENV DEBIAN_FRONTEND noninteractive
ENV BOOST_VERSION 1.70.0

RUN apt-get update

# useful tools
RUN apt-get install -y bzip2 curl wget git-core html2text 

# C++ build stuff
RUN apt-get install -y build-essential python automake autoconf autogen libtool pkg-config

# Java build stuff
RUN apt-get install -y openjdk-8-jdk-headless

# random libs that android or the emulator actually needs to run
RUN apt-get install -y libc6-i386 lib32ncurses5 lib32stdc++6 lib32gcc1 lib32z1 lib32gcc1 libpulse-dev unzip
# get the emulator to run by driving its X windows screen to a fake screen using xvfb
RUN apt install -y qemu libxcomposite-dev libxcursor-dev libgl1-mesa-glx x11vnc xvfb

# removes apt package data and temporary files
RUN rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

RUN rm -f /etc/ssl/certs/java/cacerts; /var/lib/dpkg/info/ca-certificates-java.postinst configure

RUN curl -s https://dl.google.com/android/repository/sdk-tools-linux-${VERSION_SDK_TOOLS}.zip > /sdk.zip && unzip /sdk.zip -d /opt/adk && rm -v /sdk.zip
RUN wget -q https://dl.google.com/android/repository/platform-tools-latest-linux.zip && unzip platform-tools-latest-linux.zip -d /opt/adk && rm platform-tools-latest-linux.zip
    
RUN yes | /opt/adk/tools/bin/sdkmanager --licenses > /dev/null

ADD packages.txt /opt/adk
RUN mkdir -p /root/.android && touch /root/.android/repositories.cfg && ${ANDROID_HOME}/tools/bin/sdkmanager --update
RUN while read -r package; do PACKAGES="${PACKAGES}${package} "; done < /opt/adk/packages.txt && ${ANDROID_HOME}/tools/bin/sdkmanager ${PACKAGES}

RUN mkdir /root/.vnc; x11vnc -storepasswd test /root/.vnc/passwd

# create a basic android image
RUN /opt/adk/tools/bin/avdmanager create avd -n a28_1 -d 1 -k "system-images;android-28;default;x86_64"
