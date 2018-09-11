package editor

class CubicBezierCurve(val parent: Edge) {
    companion object {
        const val STEPS = 25  // controls how many interpolation steps are computed
    }

    lateinit var ctrlSrc: Coordinate
    lateinit var ctrlTgt: Coordinate

    init {
        computeControlPoints()
    }

    var points = Array(STEPS+1){_ -> Coordinate(0, 0)}

    /*
        Before we paint the actual curve we need to update the curve points
        because the source or target port of the parent edge may have been
        moved since the last redraw.
     */
    fun paint(g: GraphicsProxy){
        // the order of these function calls is important because there are dependecies!
        computeControlPoints()
        updateCurvePoints()
        // now paint the actual curve as an interpolation between the curve points
        for (i in 0 .. points.size-2){
            val p = points[i]
            val p2 = points[i+1]
            g.line(p, p2)
        }

        // paint end and control points as well as lines between them
//        var diamondShape = Transform(0.0, 0.0, UNIT) * listOf(
//                Coordinate(-0.05, 0.0),
//                Coordinate(0.0, 0.05),
//                Coordinate(0.05, 0.0),
//                Coordinate(0.0, -0.05))
//        // endpoints
//        g.polygon(Color.BLUE, Transform(parent.sourceCoord.x, parent.sourceCoord.y, 1.0) * diamondShape, true)
//        g.polygon(Color.BLUE, Transform(parent.targetCoord.x, parent.targetCoord.y, 1.0) * diamondShape, true)
//        // control points
//        g.polygon(Color.RED, Transform(ctrlSrc.x, ctrlSrc.y, 1.0) * diamondShape, true)
//        g.polygon(Color.RED, Transform(ctrlTgt.x, ctrlTgt.y, 1.0) * diamondShape, true)
//        // control lines
//        g.line(parent.sourceCoord, ctrlSrc)
//        g.line(parent.targetCoord, ctrlTgt)
    }

    /*
        Because the visual flow in our editor goes from left to right we want the bezier curves
        to follow this direction. Therefore the control points are placed on the same y-level as
        the source and target port but in the middle concerning the x-coordinate.
     */
    private fun computeControlPoints(){
        val deltaX = parent.targetCoord.x - parent.sourceCoord.x
        val xCtrlSrc = parent.sourceCoord.x + deltaX/2
        val xCtrlTgt = parent.targetCoord.x - deltaX/2
        ctrlSrc = Coordinate(xCtrlSrc, parent.sourceCoord.y)
        ctrlTgt = Coordinate(xCtrlTgt, parent.targetCoord.y)
    }

    private fun updateCurvePoints(){
        // compute points of our curve
        // x = p1.x*(1-t)^3 + h1.x*3*(1-t)^2*t + h2.x*3*(1-t)*t^2 + p2.x*t^3
        // y = p1.y*(1-t)^3 + h1.y*3*(1-t)^2*t + h2.y*3*(1-t)*t^2 + p2.y*t^3
        val inc = 1.0/STEPS
        var t = 0.0
        for (i in 0..STEPS ){
            val t2 = t*t
            val t3 = t2*t
            val s = 1.0-t
            val s2 = s*s
            val s3 = s2*s
            val x = parent.sourceCoord.x*s3 + ctrlSrc.x*3*s2*t + ctrlTgt.x*3*s*t2 + parent.targetCoord.x*t3
            val y = parent.sourceCoord.y*s3 + ctrlSrc.y*3*s2*t + ctrlTgt.y*3*s*t2 + parent.targetCoord.y*t3
            points[i] = Coordinate(x, y)
            t += inc
        }
    }

    /*
        Calculate the shortest distance between the given point (x, y) and this curve.
        The function relies on the linear interpolation of the curve just like the paint
        function.
     */
    fun shortestDistancePointToCurve(x: Double, y: Double): Double {
        var distMin = Double.MAX_VALUE

        for (i in 0 .. points.size-2){
            val dist = shortestDistancePointToLine(points[i].x, points[i].y, points[i+1].x, points[i+1].y, x, y)
            if (dist < distMin)
                distMin = dist
        }

        return distMin
    }
}