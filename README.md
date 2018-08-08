IntellijEve
===========

What is IntellijEve?
--------------------
This software is meant to be a graphical software development tool to 
create programs that handle event streams using 
[Rust Futures](https://github.com/rust-lang-nursery/futures-rs).
The user creates a graphical representation (in its most literal meaning).
From this graph Rust code can be generated which forms the executable 
program.

The Rust code generated uses the new Futures (version 0.1) crate of Rust to 
enable asynchronous execution of code segments.

Graphs created by the user are stored using the 
[GraphML file format](http://graphml.graphdrawing.org/).
It is enriched with application-specific data but still valid GraphML.


Why use IntellijEve?
--------------------
As this project is part of university research it may not be fitted with 
every feature that is needed to develop a complex real world application.

However is was designed and implemented to be used in usability studies 
where the focus lies on visual vs. textual programming and the 
visualization of code.
Espacially in the field of parallel programming and stream processing 
this method might have a large benefit.


How to install
---------------

IntellijEve is designed as a plugin for the IntelliJ IDEA development 
environment from [JetBrains](https://www.jetbrains.com/).
Therefore a recent version of that is required to run the plugin.
We recommend version 2018.1 as we used it during development and the 
plugin was tested with this version.

### Prerequisites
* [Rust plugin for IntelliJ IDEA](https://intellij-rust.github.io/)
* [Toml plugin for IntelliJ IDEA](https://plugins.jetbrains.com/plugin/8195-toml)
* the IntellijEve plugin

*Maybe further installation steps we discover after releasing the plugin...*


How to build
------------
*Maybe we should document the whole intellij project and gradle build 
system setup here?*


How to use
----------

### IntelliJ IDEA plugin

If the plugin (and the other plugins mentioned in the *How to Install* 
section) are installed it is easy to start:
1. create a new project in IntelliJ, make it a Rust project
2. follow the usual steps until the project is created and opened
3. create a new file inside the project, when naming it make sure the 
file ending is ".eve"
4. the new file will be opened for editing which results in an empty canvas
that is displayed
5. now you can get started (link to documentation?)

See the [Quick Start Page](https://gitlab.tubit.tu-berlin.de/EVEaMCP/IntellijEve/wikis/quick-start) for details.


### StandaloneEditor

This project also features a class thats enables a user to start the 
graphical editor without a running IntelliJ instance *StandaloneEditor*.

But since the editor was designed as an IntelliJ plugin this standalone 
version lacks some features:

* no file saving/loading
* no integrated editing of code

Nonetheless the StandaloneEditor is useful to get a first glance of the 
features without the hassle of setting up IntelliJ IDEA and creating a 
Rust project.


Development
-----------

To further enhance this software one should have a look at the guide to 
[configure the IntelliJ Platform Plugin SDK](http://www.jetbrains.org/intellij/sdk/docs/welcome.html).
In this project [gradle](https://gradle.org/) is used as a build system 
besides IntelliJ.

The software is written in Kotlin with some snippets of Rust (the parts 
of the generated code).
For the visual presentation Java AWT classes are used but are wrapped to 
provide some functionality that was needed.

*Where can the documentation be found, in the wiki of the project?*
