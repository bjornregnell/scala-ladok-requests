# scala-ladok-requests
A simple Scala 3 library for getting stuff from Ladok if you are a teacher and have a cookie.

## How to use this lib

Add these directives to your scala-cli script:
```scala
//> using scala 3.8.3
//> using dep se.bjornregnell::scala-ladok-requests:0.1.0
```

## How to copy your session cookie from Firefox after Ladok Login

1. Log in to Ladok at https://start.ladok.se in Firefox.

2. Once logged in, open Firefox DevTools by pressing `F12` (or `Ctrl+Shift+I`).

3. Click the **Network** tab in DevTools.

4. In the Ladok page, click on something (e.g. search for a student or navigate to a course) to trigger a network request.

5. In the Network tab, look for a request to `start.ladok.se` (you can type `ladok` in the filter box to narrow it down). Click on one of the XHR requests (type "xhr").

6. In the right panel, click the **Headers** tab.

7. Scroll down to **Request Headers** and find the line that starts with `Cookie:`.

8. Right-click the cookie value and choose **Copy Value**. It will be a long string containing things like `XSRF-TOKEN=...;JSESSIONID=...;...`.

9. Open a terminal and run:
   ```
   cat > ~/.ladok-cookie
   ```
   Then paste the cookie value (`Ctrl+Shift+V` in most terminals), press `Enter`, then press `Ctrl+D` to save the file.

10. You can now run the tool. The cookie expires after some time, so you will need to repeat these steps when your session runs out.

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


