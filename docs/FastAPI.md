# FastAPI

These API try to find a faster implementation of existing Java APIs

It can be used to optimise your plugins when `KibblePatcher` is used

When plugin rewrite is installed (MC 1.13+) all calls to Java APIs are redirected to their
faster counterpart without requiring any modification to the installed plugins

The actual fast APIs available are:

- `FastMath` that use the faster Minecraft Implementation of `sin` and `cos`
- `FastReplace` that use faster Apache-Lang implementation of `replace`

Note: `FastReplace` can fallback to other places where Apache-Lang is located,
so it's safer to use `FastReplace` than to use Apache `replace` function directly
