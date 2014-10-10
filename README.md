XmiParser
=========

XmiParser is XMI Model parsing framework for validation and HTML
javadoc generation. Given XMI XML input file it creates simple
structures to enable subclasses to quickly validate, generate
HTML from the model or convert to other forms.

The package is a collection of a few Groovy classes. The base XmiParser class
does all the parsing of the XMI model iterating over each element and package
in the model then adding those to several data structures including lookup
tables to walk through the model.

The HTML generator generates javadoc-like HTML documentation
with javadoc summary pages and detailed class-level pages.

If a class or interface inherits from multiple super classes or interfaces then
multiple views will be created. The default view will list all the attributes
grouped by the class that it inherits those from. The second view will be
a flat sorted list of attributes with hyper links back to the parent classes
or interfaces that they are inherited from.

HTML generator also creates two summary files that list all the classes
and packages of the model (e.g. xxx.xmi-list.txt) as well as a detailed
listing of all attributes for each class or interface (e.g. xxx.xmi-details.txt).

Note that Enterprise Architect can export EAP models as XMI 1.1 or 2.1.
XMI file must be version 2.1 and UML must be 2.1 or 2.2. Other versions
are not supported by the parser.

# How to build

XmiParser uses [Gradle](http://www.gradle.org) as a build tool. Since gradle is awesome you actually don't have to install gradle,
you just need a working JDK installation.

To compile and run all the tests:

    gradlew test

The first time you run this it will download and install gradle. Downloaded files (including the Gradle
distribution itself) will be stored in the Gradle user home directory (typically "<user_home>/.gradle").
Subsequent runs will be much faster.

If you already have gradle installed then you can use the command gradle in place of gradlew as
listed above.

    gradle test

# Running

To run on the sample XMI file try run.bat which runs a main script
that either validates the model or generate html documentation.

The following assumes you already have [Groovy](http://groovy.codehaus.org/) installed.

    run.bat

To generate HTML javadoc documentation add the "-h" argument:

    run.bat -h

# License

Copyright 2014 The MITRE Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.