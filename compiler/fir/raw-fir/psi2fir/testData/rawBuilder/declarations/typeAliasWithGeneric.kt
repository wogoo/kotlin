open class A

interface B<S, T : A>

typealias C<T> = B<T, A>

class D : C<A>

//FIR_FRAGMENTS_COUNT 7