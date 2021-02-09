interface A
interface B

class C<T> where T : A, T : B {

}

//FIR_FRAGMENTS_COUNT 4