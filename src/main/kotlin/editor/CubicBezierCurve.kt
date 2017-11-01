package editor

class CubicBezierCurve(val parent: Edge, var src: Coordinate, var tgt: Coordinate) {
    companion object {
        val STEPS = 25  // controls how many interpolation steps are computed
    }

    lateinit var ctrlSrc: Coordinate
    lateinit var ctrlTgt: Coordinate

    init {
        computeControlPoints()
    }

    /*
        This array holds our curve points. It needs to be STEPS+1 in size and
        has an additional point for the width of the arrow tip.
     */
    var points = Array<Coordinate>(STEPS+1+1){_ -> Coordinate(0, 0)}

    /*
        Before we paint the actual curve we need to update the curve points
        because the source or target port of the parent edge may have been
        moved since the last redraw.
     */
    fun paint(g: GraphicsProxy){
        // the order of these function calls is important because there are dependecies!
        updateEndpoints()
        computeControlPoints()
        updateCurvePoints()
        // now paint the actual curve as an interpolation between the curve points
        for (i in 0 .. points.size-2){
            val p = points[i]
            val p2 = points[i+1]
            g.line(p, p2)
        }
    }

    private fun updateEndpoints(){
        src = parent.source_coord
        tgt = parent.target_coord
    }

    /*
        Because the visual flow in our editor goes from left to right we want the bezier curves
        to follow this direction. Therefore the control points are placed on the same y-level as
        the source and target port but in the middle concerning the x-coordinate.
     */
    private fun computeControlPoints(){
        val deltaX = tgt.x - src.x
        val xCtrlSrc = src.x + deltaX/2
        val xCtrlTgt = tgt.x - deltaX/2
        ctrlSrc = Coordinate(xCtrlSrc, src.y)
        ctrlTgt = Coordinate(xCtrlTgt, tgt.y)
    }

    private fun updateCurvePoints(){
        // compute points of our curve
        // x = p1.x*(1-t)^3 + h1.x*3*(1-t)^2*t + h2.x*3*(1-t)*t^2 + p2.x*t^3
        // y = p1.y*(1-t)^3 + h1.y*3*(1-t)^2*t + h2.y*3*(1-t)*t^2 + p2.y*t^3
        val tgtMinusSpacing = Coordinate(tgt.x-0.5*UNIT, tgt.y)
        val inc = 1.0/STEPS
        var t = 0.0
        for (i in 0..STEPS ){
            val t2 = t*t
            val t3 = t2*t
            val s = 1.0-t
            val s2 = s*s
            val s3 = s2*s
            val x = src.x*s3 + ctrlSrc.x*3*s2*t + ctrlTgt.x*3*s*t2 + tgtMinusSpacing.x*t3
            val y = src.y*s3 + ctrlSrc.y*3*s2*t + ctrlTgt.y*3*s*t2 + tgtMinusSpacing.y*t3
            points[i] = Coordinate(x, y)
            t += inc
        }
        // Add a point that is the connection between the last curve point and
        // the target node. The line gets hidden underneath the arrow tip.
        points[STEPS+1] = tgt
    }
}