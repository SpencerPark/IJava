Updated:

1. fix `CompilerMagics.compile` auto generated package path error
2. remain `JupyterIO.jupyterXXX.env`, keep thread stdout rewrite to jupyter

TODO:

1. reload `CompilerMagics.compile` class? DirectExecutionControl > DefaultLoaderDelegate
2. thread stdout JupyterIO.retractEnv; BaseKernel.replaceOutputStreams
