# scala-ladok-requests
A simple Scala 3 library for getting stuff from Ladok if you are a teacher and have a cookie.

## How to download the app and run it

Download the latest `ladok.jar` file from here: 
https://github.com/bjornregnell/scala-ladok-requests/releases/latest/download/ladok.jar 

Or use curl:
```
curl -fLO https://github.com/bjornregnell/scala-ladok-requests/releases/latest/download/ladok.jar
```

Run it with:
```
./ladok.jar --help
```

Before you can extract data from ladok you need to copy the cookie, as explained in the next sections.

## How to copy your session cookie

Instructions below are for the [Firefox](https://www.firefox.com/sv-SE/) browser. A chatbot might help you how to do this in other browsers.

1. Log in to Ladok at https://start.ladok.se with Firefox

2. Once logged in, open Firefox DevTools by pressing `F12` (or `Ctrl+Shift+I`).

3. Click the **Network** tab in DevTools.

4. In the Ladok page, click on something (e.g. search for a student or navigate to a course) to trigger a network request.

5. In the Network tab, look in the list to the left for the `File` column and scroll to an entry called "inloggadanvandare" and click on it.  

6. Then click the XHR tab in the right panel.

6. In the right panel, click the **Headers** tab.

7. Scroll further down to **Request Headers** and find the line that starts with `Cookie:`.

8. Right-click the cookie value and choose **Copy Value**. It will be a long string containing things like `XLADOK_LANG=sv; XSRF-TOKEN=...`.

9. Create a file in your home directory called `.ladok-cookie` and paste the text into that file. If you are on linux or macos you can do it by opening a terminal and run:
   ```
   cat > ~/.ladok-cookie
   ```
   Then paste the cookie value (`Ctrl+Shift+V` in most terminals), press `Enter`, then press `Ctrl+D` to save the file.

10. You can now run the tool. The cookie expires after some time, so you will need to repeat these steps when your session runs out.


## How to package the app as a fat jar and run it

If you clone this repo you can:
```
scala-cli --power package --assembly -o ladok.jar .
```
Then you can run the app like so:
```
./ladok.jar --help
```

## How to use this code as a lib in your own apps

Add these directives to your scala-cli script:
```scala
//> using scala 3.8.3
//> using dep se.bjornregnell::scala-ladok-requests:0.2.0
```

## How to publish using scala-cli

After you have setup everything you can, if you have access, publish to Maven Central:
```
scala-cli --power publish .
```

### Setting up publish

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


