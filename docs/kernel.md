# Kernel

All code running in IJava flows through the kernel. This makes it the place to register magics, add things to the classpath, and perform many jupyter related operations.

## Notebook functions

IJava injects a function for getting the active kernel instance and additional helpers for making use of the kernel at runtime. These are defined in the runtime [Kernel](/src/main/java/io/github/spencerpark/ijava/runtime/Kernel.java) class.

### `JavaKernel getKernelInstance()`

Get a reference to the current kernel. It may return null if called outside of a kernel context but should be considered `@NonNull` when inside a notebook or similar. The kernel api has lots of goodies, look at the [JavaKernel](/src/main/java/io/github/spencerpark/ijava/runtime/Kernel.java) class for more information. Specifically there is access to adding to the classpath, getting the magics registry and maven resolver, and access to eval.

### `Object eval(String expr) throws Exception`

The `eval` function provides full access to the code evaluation mechanism of the kernel. It evaluates the code in the _same_ scope as the kernel and **returns an object**. This object is an object that lives in the kernel!

The given expression can be anything you would write in a cell, including magics.

```java
(int) eval("1 + 2") + 3
```

