#!/bin/bash
set -x
nohup /bu/bin/bitcoind &
nohup /abc/bin/bitcoind --datadir=/root/.bitcoinabc &
sleep 90  # how long to load an empty wallet?
ps -efwww

/bu/bin/bitcoin-cli generate 1
sleep 1
/abc/bin/bitcoin-cli --datadir=/root/.bitcoinabc generate 1
sleep 1
/bu/bin/bitcoin-cli generate 100
sleep 5
/abc/bin/bitcoin-cli --datadir=/root/.bitcoinabc generate 50
sleep 5

# Let's make sure it was set up properly
/bu/bin/bitcoin-cli getpeerinfo
/bu/bin/bitcoin-cli getinfo

# shutdown
/abc/bin/bitcoin-cli --datadir=/root/.bitcoinabc stop
/bu/bin/bitcoin-cli stop
