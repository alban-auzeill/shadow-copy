# `shadow-copy.sh` bash script

`shadow-copy.sh` help you to understand changes in a filesystem directory tree between two shadow copies.
The primary goal is not to do a backup, but to understand what has been changed in a directory after
a program execution or a package installation. To be fast, a filtering mechanism prevents analysis of
some sub-folders and files, and the logic assumes that a file has not been changed if its last modified time
has not been changed. 

The main use-case is to monitor a user home directory or the `/etc` directory.

## Usage

### Installation

```
$ curl --location --silent --show-error --output shadow-copy.sh https://raw.githubusercontent.com/alban-auzeill/shadow-copy/master/shadow-copy.sh
$ chmod +x shadow-copy.sh 
```

### Creating a shadow copy

`./shadow-copy.sh [<directory path 1>] [<directory path 2>] ...`

Without arguments, the script will do a shadow copy of the current directory.

The shadow copy of a directory is created in a subfolder of the directory itself.
The name of the shadow directory contains the date and a counter. 
e.g.: `.shadow-copy/2020.10.27-10h29-1`

The copy logic:

* To speedup copies and reduce the noise when comparing two shadow copies, a filter mechanism
  prevent analyses of some files and directories. See some filter examples in [filter-examples](filter-examples). 
* Symbolic links are copied as symbolic links (never traverse).
* Unsupported files like block special, character special, named pipe, socket, are replaced with
  a regular text file containing the file type, and the last modified time.
* If a file already exists in the last shadow copy with the exact same last modified time,
  then a hard link of the file is created between the last shadow copy and the new one.
  Otherwise, the file is copied using a lightweight copy in the new shadow copy and the last modified time is preserved.

### Filtering the shadow copy

If a bash script file `.shadow-copy-filter.sh` exists next to `.shadow-copy`, it is used to filter the shadow copy.
See some filter examples in [filter-examples](filter-examples).

Note: the executable flags on this script is not required.
