# scala-ladok-requests
A simple Scala 3 library for getting stuff from Ladok if you are a teacher and have a cookie.

## How to use this lib

Add these directives to your scala-cli script:
```scala
//> using dep se.bjornregnell::scala-ladok-requests:0.1.0
```

## How to publish using scala-cli

Publish to Maven Central:
```
scala-cli --power publish .
```


Before that you need too these tricky steps:

```
scala-cli --power config publish.credentials ossrh-staging-api.central.sonatype.com value:<youruser> value:<yourpwd>
```

And also setup a gpg PGP key:
```
scala-cli --power config --create-pgp-key --pgp-password random
```
Save the password printed in a safe place.

Then print the key:
```
scala-cli --power config pgp.public-key
```
And put it in `cat >tmp/pubkey.asc` by copy-pasting it there

Then you need to publish the public key like so:
```
gpg --import tmp/pubkey.asc
gpg --list-keys --keyid-format long   # copy the hexstring id
gpg --keyserver keyserver.ubuntu.com --send-keys <the hexstring id>
```
No you should be able to:
```
scala-cli --power publish .
```

Login to https://central.sonatype.com and check https://central.sonatype.com/publishing/deployments

It will take a while...


