# Display

One of the many great things about the Jupyter front ends is the support for [`display_data`](http://jupyter-client.readthedocs.io/en/stable/messaging.html#display-data). IJava interfaces with the [base kernel](https://github.com/SpencerPark/jupyter-jvm-basekernel)'s high level rendering API.

## Notebook functions

IJava injects 2 functions into the user space for displaying data: `display` and `render`. Most use cases should prefer the former but there is a necessary case for `render` that is outline below. In addition the `updateDisplay` function can be used to update a previously displayed object. All are defined in the runtime [Display](/src/main/java/io/github/spencerpark/ijava/runtime/Display.java) class.

All display/render functions include a `text/plain` representation in their output. By default this is the `String.valueOf(Object)` value but it can be overridden.

### `String display(Object o)`

Display an object as it's **preferred** types. If you don't want a specific type it is best to let the object decide how it is best represented.

The object is rendered and published on the display stream. An id is returned which can be used to `updateDisplay` if desired.

### `String display(Object o, String... as)`

Display an object as the **requested** types. In this case the object attempts to be rendered as the desired mime types given in `as`. No promises though, if a type is unsupported it will simply not appear in the output.

The object is rendered and published on the display stream. An id is returned which can be used to `updateDisplay` if desired.

This is useful when a type has many potential representations but not all are preferred. For example a `CharSequence` has many representations but only the `text/plain` is preferred. To display it as executable javascript we can use the following:

```java
display("alert('Hello from IJava!');", "application/javascript");
```

Since there is the potential that some front ends don't support a given format many can be given and the front end chooses the best. For example, to display as html and markdown:

```java
display("<b>Bold</b>", "text/html", "text/markdown");
```

This will trigger a display message with values for `text/html`, `text/markdown`, and the implicit `text/plain`.


### `DisplayData render(Object o)`

Renders an object as it's **preferred** types and returns it's rendered format. Similar to `display(Object o)` but without publishing the result.

### `DisplayData render(Object o, String... as)`

Renders an object as the **requested** types and returns it's rendered format. Similar to `display(Object o, String... as)` but without publishing the result.

When expressions are the last code unit in a cell they are rendered with the `render(Object o)` semantics. If this is not desired it can be hijacked by wrapping it in a call to this function.

```java
String md = "Hello from **IJava**";

render(md, "text/markdown")
```

This will result in the `Out[_]` result to be the pretty `text/markdown` representation rather than the boring `text/plain` representation.

### `void updateDisplay(String id, Object o)`

Renders an object as it's **preferred** types and updates an existing display with the given id to contain the new rendered object. Similar to `display(Object o)` but updates an existing displayed object instead of appending a new one.

### `void updateDisplay(String id, Object o, String... as)`

Renders an object as it's **requested** types and updates an existing display with the given id to contain the new rendered object. Similar to `display(Object o, String... as)` but updates an existing displayed object instead of appending a new one.

```java
String id = display("<b>Countdown:</b> 3", "text/html");
for (int i = 3; i >= 0; i--) {
    updateDisplay(id, "<b>Countdown:</b> " + i, "text/html");
    Thread.sleep(1000L);
}
render("<b>Liftoff!</b>", "text/html")
```