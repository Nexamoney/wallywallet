FROM andrewstone/adk:latest
MAINTAINER Andrew Stone

ENV ANDROID_HOME "/opt/adk"
ENV PATH "$PATH:${ANDROID_HOME}/tools"
ENV DEBIAN_FRONTEND noninteractive
ENV BOOST_VERSION 1.70.0

RUN wget -q -O /boost_1.70_0.tar.bz2 https://dl.bintray.com/boostorg/release/1.70.0/source/boost_1_70_0.tar.bz2

# RUN rm -f /bucash.tar.gz; curl -s --location https://github.com/BitcoinUnlimited/BitcoinUnlimitedWebDownloads/raw/master/BUcash-1.7.0.0-linux64.tar.gz > /bucash.tar.gz
# RUN tar xvf /bucash.tar.gz; mv /BUcash-1.7.0.0 /bu

RUN apt update; apt-get install -y software-properties-common; add-apt-repository -y ppa:bitcoin-unlimited/bu-ppa; apt-get update; apt-get -y install bsdmainutils libssl-dev libevent-dev libdb4.8-dev libdb4.8++-dev libboost-system-dev libboost-filesystem-dev libboost-chrono-dev libboost-program-options-dev libboost-test-dev libboost-thread-dev
RUN git clone https://github.com/BitcoinUnlimited/BitcoinUnlimited.git /busrc
RUN (mkdir /bu; cd /busrc; git checkout dev; mkdir release; ./autogen.sh; cd release; ../configure --with-gui=no --prefix=/bu; make -j8 install)
RUN apt-get -y install python3-git clang cmake
RUN (cd /; curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs > sh.rustup.rs; sh /sh.rustup.rs -y -q)
RUN (export PATH=/root/.cargo/bin:$PATH; cd /busrc/release; make electrscash; cp /busrc/src/electrscash /bu/bin/)

RUN rm -f /abc.tar.gz; curl -s --location https://download.bitcoinabc.org/0.20.10/linux/bitcoin-abc-0.20.10-x86_64-linux-gnu.tar.gz > /abc.tar.gz
RUN tar xvf /abc.tar.gz; mv /bitcoin-abc-* /abc

RUN mkdir -p /root/.bitcoin
ADD bitcoin.conf /root/.bitcoin

RUN mkdir -p /root/.bitcoinabc
ADD bitcoinabc.conf /root/.bitcoinabc/bitcoin.conf

RUN rm -f /bucash.tar.gz /abc.tar.gz; rm -rf /root/.bitcoinabc/regtest; rm -rf /root/.bitcoin/regtest

# Make a regtest blockchain
ADD initBlockchain.sh /initBlockchain.sh
RUN chmod a+x /initBlockchain.sh; /initBlockchain.sh

RUN apt-get update
RUN apt install -y automake autoconf autogen libtool pkg-config
ADD bitcoin-config.h /bitcoin-config.h

ADD startAndroid.sh /startAndroid.sh
RUN chmod a+x /startAndroid.sh
