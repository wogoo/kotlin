# Priority of locks in parallel runs of the JVM IR backend

1. `ClassCodegen.lock`. Used to prevent concurrent generation of inline functions.
1. Locks in `lazy` instances in `compiler/backend`.
1. Global `IrLock`. Used in `SymbolTable`, 
   `lazyVar` instances in `IrLazyDeclaration`, `Fir2IrLazyDeclaration` subclasses,
   `Fir2IrDeclarationStorage`.
1. - `classWriterLock` used in `loadClassBytesByInternalName`.
   - lock on `methodNode` in `InlineCodegen.cloneMethodNode`.
   - Locks protecting `InlineCache` maps.