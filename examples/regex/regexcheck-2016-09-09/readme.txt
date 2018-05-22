# RegexCheck

RegexCheck detects regular expressions that may lead to exponential or super-linear matching time.

It currently supports a subset of Java's regular expression syntax.

## Running RegexCheck

The following command will run the checker on each regex (one per line as it would occur in Java source code, enclosed in double quotes) in the specified file:

```
$ java -Xss64M -Xmx1024M -jar regexcheck.jar -f <file name>
```

There are several additional analysis options that can be set using command line options (see description of command line options for more details).
