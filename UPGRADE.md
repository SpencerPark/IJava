Upgrade Note:

* Fix `print` input parameter extraction error in code blocks that are called multiple times;
* add `RuntimeCompiler` util;
* add `compile` cellMagic (make sure `--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED` in env *
  IJAVA_COMPILER_OPTS*)
  ![compile](docs/img/compile-cell-magic.png)
* add `read/write` cell/body magic
  ![r-w](docs/img/read-write-line-magic.png)
  ![r-w](docs/img/write-cell-magic.png)
* add `cmd` line magic
  ![cmd](docs/img/cmd-line-magic.png)

