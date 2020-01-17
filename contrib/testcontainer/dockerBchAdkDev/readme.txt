Builds a docker container with the C++, Android, and Android Native development environments.
Relies on the container defined in ../dockerAdk.

Android is located at /opt/adk.
BU is located at /bu.  BU data dir is the default.
ABC is located at /abc.  ABC data dir is /root/.bitcoinabc.

A regtest blockchain is created with coins in both wallets and the nodes are connected.

/startAndroid.sh will start up an emulator.

Boost source tarball is placed in /boost_1.70.0.tar.bz2.

Modify build.sh to push the container up under your own username.
