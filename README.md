# shadow-copy

`shadow-copy` help you to understand changes in a filesystem directory tree between two shadow copies.
The primary goal is not to do a backup, but to understand what has been changed in a directory after
a program execution or a package installation. To be fast, a filtering mechanism prevents analysis of
some sub-folders and files, and the logic assumes that a file has not been changed if its last modified time
has not been changed. 

The main use-case is to monitor a user home directory or the `/etc` directory.

## Usage

### Installation

Download latest `shadow-copy` from [shadow-copy/releases](https://github.com/alban-auzeill/shadow-copy/releases)

### Creating a shadow copy

`./shadow-copy [<directory path 1>] [<directory path 2>] ...`

Without arguments, `shadow-copy` does a shadow copy of the current directory.

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

### Syntax
```
$ shadow-copy --help
```
```
Shadow Copy
Syntax: shadow-copy <action> <arguments>

Available actions:
- create [ <target-directory> ]
    # Create a shadow copy of the current directory into a new sub-directory of '.shadow-copy':
    shadow-copy create
    # Copy the '/home/paul' directory into a new sub-directory of '/home/paul/.shadow-copy':
    shadow-copy create /home/paul
    # Copy the '/home/paul' directory into a new sub-directory of '/tmp/test':
    shadow-copy create /home/paul --shadow-directory /tmp/test
- history [ <target-directory> ]
    # Show the sorted list of shadow copy index and path, index 1 is the latest:
    shadow-copy history
    # Show only the latest shadow copy path:
    shadow-copy history -n 1 --no-index
    # Show size of each shadow copies:
    shadow-copy history --no-index | xargs du -hs
- diff [ <target-directory> ] [ <index> ]  [ <index> ]
    # Compare the current directory with the last shadow copy:
    shadow-copy diff
    # Compare the current directory with the given shadow copy index:
    shadow-copy diff 2
    # Compare two shadow copies:
    shadow-copy diff 2 3
- purge [ <target-directory> ]
    # Only keep the 10 latest shadow copies:
    shadow-copy purge
    # Only keep the 5 latest shadow copies:
    shadow-copy purge -n 5

Available options:
  --version
    Display the shadow-copy version.
  --help
    Show this help.
  --shadow-directory <directory-path>
    Replace usage of a '.shadow-copy' sub-directory by the given directory path.
  --shadow-index <index>
    Force the index of last shadow copy to use. index >=1, default: 1
  --no-index
    Do not prefix shadow history by index.
  -n <size>
    Limit the history list or the purge list to the given number.
```

### Filtering the shadow copy

If a file `.shadow-copy/ignore` exists, it is used to filter the shadow copy.
See some filter examples in [filter-examples](filter-examples).
