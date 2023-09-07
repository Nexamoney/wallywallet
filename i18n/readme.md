# Internationalization

This directory contains a script run.sh and a kotlin program that reads the strings.xml files defined in androidMain/res/values*.  These scripts create a set of binary files, one for each locale, and a multiplatform strings.kt file that provides kotlin variable names that can be used to index into the local specific binary file.

These created files are commited to the repository, so you only need to rerun run.sh if you have added or removed some string translations.

