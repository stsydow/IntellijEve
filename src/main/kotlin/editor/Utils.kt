package editor

fun profile(name: String, op: () -> Unit) {
    val start_time = System.nanoTime()
    op.invoke()
    val dt = (System.nanoTime() - start_time + 500) / 1000
    if (dt > 1000) println("$name ${dt / 1000}.${dt % 1000}ms")
}
/* usage:
profile("a_tag", {
    do_stuff()
})
*/